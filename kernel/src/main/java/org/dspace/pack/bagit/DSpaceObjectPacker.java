/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.pack.bagit;

import java.io.FileFilter;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import edu.mit.lib.bagit.Bag;
import edu.mit.lib.bagit.Filler;
import edu.mit.lib.bagit.Loader;

import org.dspace.authorize.AuthorizeException;
import org.dspace.content.Bitstream;
import org.dspace.content.BoundedIterator;
import org.dspace.content.Bundle;
import org.dspace.content.Collection;
import org.dspace.content.Community;
import org.dspace.content.DSpaceObject;
import org.dspace.content.FormatIdentifier;
import org.dspace.content.Item;
import org.dspace.core.Constants;
import org.dspace.core.Context;

import org.dspace.pack.Packer;
import org.dspace.pack.PackingFilter;
import org.dspace.pack.PackingSpec;
import static org.dspace.pack.bagit.BagUtils.*;

/**
 * DSpaceObjectPacker packs DSOs to BagIt compressed archives,
 * or creates DSOs from such BagIt archives.
 *
 * @author richardrodgers
 */
public class DSpaceObjectPacker implements Packer {

    private PackingSpec spec;
    private PackingFilter filter;

    public DSpaceObjectPacker() {}

    @Override
    public void setPackingSpec(PackingSpec spec) {
        this.spec = spec;
        filter = new PackingFilter(spec);
    }

    @Override
    public Path pack(DSpaceObject dso, Path packDir) throws AuthorizeException, IOException, SQLException {
        Filler filler = new Filler(packDir);
        // set base object properties
        filler.metadata(BAG_TYPE, "AIP");

        filler.property("data/object", OBJECT_TYPE, Constants.typeText[dso.getType()].toLowerCase());
        filler.property("data/object", OBJECT_ID, dso.getHandle());
        DSpaceObject parent = dso.getParentObject();
        if (parent != null) {
            filler.property("data/object", OWNER_ID, parent.getHandle());
        }
       
        if (dso.getType() == Constants.ITEM) {
            StringBuilder linked = new StringBuilder();
            Item item = (Item)dso;
            for (Collection coll : item.getCollections()) {
                if (! item.isOwningCollection(coll)) {
                    linked.append(coll.getHandle()).append(",");
                }
            }
            String linkedStr = linked.toString();
            if (linkedStr.length() > 0) {
                filler.property("data/object", OTHER_IDS, linkedStr.substring(0, linkedStr.length() - 2));
            }
            if (item.isWithdrawn()) {
                filler.property("data/object", WITHDRAWN, "true");
            }
        }

        // next metadata
        BagUtils.writeMetadata(dso, filter, filler.payloadStream("metadata.xml"));

        if (dso.getType() == Constants.COMMUNITY) {
            // check for logo
            Bitstream logo = ((Community)dso).getLogo();
            if (logo != null) {
                filler.payload("logo", logo.retrieve());
            }
        } else if (dso.getType() == Constants.COLLECTION) {
            // check for logo
            Bitstream logo = ((Collection)dso).getLogo();
            if (logo != null) {
                filler.payload("logo", logo.retrieve());
            }
        } else {
            Item item = (Item)dso;
            // proceed to bundles, in sub-directories, filtering
            for (Bundle bundle : item.getBundles()) {
                if (filter.acceptBundle(bundle.getName())) {
                    // only bundle metadata is the primary bitstream - remember it
                    // and place in bitstream metadata if defined
                    int primaryId = bundle.getPrimaryBitstreamID();
                    for (Bitstream bs : bundle.getBitstreams()) {
                        // write metadata to xml file
                        String seqId = String.valueOf(bs.getSequenceID());
                        String relPath = bundle.getName() + "/";
                        BagUtils.XmlWriter writer = BagUtils.xmlWriter(filler.payloadStream(relPath + seqId + "-metadata.xml"));
                        writer.startStanza("metadata");
                        // field access is hard-coded in Bitstream class, ugh!
                        writer.writeValue("name", bs.getName());
                        writer.writeValue("source", bs.getSource());
                        writer.writeValue("description", bs.getDescription());
                        writer.writeValue("sequence_id", seqId);
                        if (bs.getID() == primaryId) {
                           writer.writeValue("bundle_primary", "true"); 
                        }
                        writer.endStanza();
                        writer.close();
                        // write the bitstream itself, unless reference filter applies
                        String url = filter.byReference(bundle, bs);
                        if (url != null) {
                            // add reference to bag
                            filler.payloadRef(relPath + seqId, bs.getSize(), url);
                        } else {
                            // add bytes to bag
                            filler.payload(relPath + seqId, bs.retrieve());
                        }
                    }
                }
            }
        }
        return filler.toPackage(spec.getFormat());
    }

    @Override
    public void unpack(DSpaceObject dso, Path archive) throws AuthorizeException, IOException, SQLException {
        if (archive == null || Files.notExists(archive)) {
            //throw new IOException("Missing archive for object: " + dso.getHandle());
        }
        Bag bag = new Loader(archive).load();
        unpackBag(dso, bag);
    }

    @Override
    public void unpack(DSpaceObject dso, InputStream archStream) throws AuthorizeException, IOException, SQLException {
        if (archStream == null) {
            //throw new IOException("Missing archive for object: " + dso.getHandle());
        }
        Bag bag = new Loader(archStream, spec.getFormat()).load();
        unpackBag(dso, bag);
    }

    private void unpackBag(DSpaceObject dso, Bag bag) throws AuthorizeException, IOException, SQLException {
        // add the metadata first
        BagUtils.readMetadata(dso, bag.payloadStream("metadata.xml"));

        if (dso.getType() == Constants.COMMUNITY) {
            // also install logo or set to null
            ((Community)dso).setLogo(bag.payloadStream("logo"));
            // now write data back to DB
            dso.update();
        } else if (dso.getType() == Constants.COLLECTION) {
            // also install logo or set to null
            ((Collection)dso).setLogo(bag.payloadStream("logo"));
            // now write data back to DB
            dso.update();
        } else {
            Item item = (Item)dso;
            // proceed to bundle data & metadata
            Map<String, String> directory = bag.payloadManifest();
            Map<String, Bundle> bundles = new HashMap<>();
            List<String> seqNoList = new ArrayList<>();
            for (String path : directory.keySet()) {
                // format of path is: 'data/BUNDLENAME/seqno[-metadata.xml]'
                String[] parts = path.split("/");
                // skip metadata.xml or any other file not in a bundle directory
                if (parts.length == 2) {
                    continue;
                }
                // directory is bundle name
                String bundleName = parts[1];
                Bundle bundle = null;
                if (bundles.containsKey(bundleName)) {
                    bundle = bundles.get(bundleName);
                } else {
                    bundle = item.createBundle(bundleName);
                    bundles.put(bundleName, bundle);
                }
                String[] fnParts = parts[2].split("-");
                if (! seqNoList.contains(fnParts[0])) {
                    // read both bitstream file and its associated metadata file
                    String bsPath = bundleName + "/" + fnParts[0];
                    Bitstream bs = bundle.createBitstream(bag.payloadStream(bsPath));
                    // now set bitstream metadata
                    BagUtils.XmlReader reader = BagUtils.xmlReader(bag.payloadStream(bsPath + "-metadata.xml"));
                    if (reader != null && reader.findStanza("metadata")) {
                        BagUtils.Value value = null;
                        // field access is hard-coded in Bitstream class
                        while((value = reader.nextValue()) != null) {
                            switch (value.name) {
                                case "name" : bs.setName(value.val); break;
                                case "source" : bs.setSource(value.val); break;
                                case "sequence_id" : bs.setSequenceID(Integer.valueOf(value.val)); break;
                                // special case - bundle metadata in bitstream
                                case "bundle_primary" : bundle.setPrimaryBitstreamID(bs.getID()); break;
                                default : break;
                            }
                        }
                        reader.close();
                    } else {
                        throw new IOException("Cannot locate bitstream metadata file: " + bsPath + "-metadata.xml");
                    }
                    // RLR - FIXME - stub here to do crude Format Identification - replace with service call
                    Context context = new Context();
                    bs.delegate(context);
                    bs.setFormat(FormatIdentifier.guessFormat(context, bs));
                    context.complete();
                    seqNoList.add(fnParts[0]);
                    bs.update();
                }
            }
        }
    }
}
