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
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import edu.mit.lib.bagit.Bag;
import edu.mit.lib.bagit.Filler;
import edu.mit.lib.bagit.Loader;

import org.dspace.authorize.AuthorizeException;
import org.dspace.content.Bitstream;
import org.dspace.content.Bundle;
import org.dspace.content.Collection;
import org.dspace.content.Item;

import org.dspace.pack.Packer;
import static org.dspace.pack.PackerFactory.*;

/**
 * ItemPacker packs and unpacks Item AIPs in BagIt bag compressed archives
 *
 * @author richardrodgers
 */
public class ItemPacker implements Packer {

    private Item item = null;
    private String archFmt = null;
    private List<String> filterBundles = new ArrayList<String>();
    private boolean exclude = true;
    private List<RefFilter> refFilters = new ArrayList<RefFilter>();

    public ItemPacker(Item item, String archFmt) {
        this.item = item;
        this.archFmt = archFmt;
    }

    public Item getItem() {
        return item;
    }

    public void setItem(Item item) {
        this.item = item;
    }

    @Override
    public File pack(File packDir) throws AuthorizeException, IOException, SQLException {
        Filler filler = new Filler(packDir.toPath());
        // set base object properties
        filler.metadata(BAG_TYPE, "AIP");

        filler.property("data/object", OBJECT_TYPE, "item");
        filler.property("data/object", OBJECT_ID, item.getHandle());
       
        // get collections
        StringBuilder linked = new StringBuilder();
        for (Collection coll : item.getCollections()) {
            if (item.isOwningCollection(coll)) {
                filler.property("data/object", OWNER_ID, coll.getHandle());
            } else {
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

        // start with metadata
        BagUtils.writeMetadata(item, filler.payloadStream("metadata.xml"));
        // proceed to bundles, in sub-directories, filtering
        for (Bundle bundle : item.getBundles()) {
            if (accept(bundle.getName())) {
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
                    String url = byReference(bundle, bs);
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
        //return archive;
        return filler.toPackage(archFmt).toFile();
    }

    @Override
    public void unpack(File archive) throws AuthorizeException, IOException, SQLException {
        if (archive == null || ! archive.exists()) {
            throw new IOException("Missing archive for item: " + item.getHandle());
        }
        Bag bag = new Loader(archive.toPath()).load();
        // add the metadata first
        BagUtils.readMetadata(item, bag.payloadStream("metadata.xml"));
        // proceed to bundle data & metadata
        Map<String, String> directory = bag.payloadManifest();
        for (String relPath : directory.keySet()) {
            File bfile = null;
            try { 
                bfile = bag.payloadFile(relPath).toFile();
            } catch (IllegalAccessException iee) {}
            // only bundles are directories
            //if (! new File(relpAhbfile.isDirectory()) {
            //    continue;
            //}
            Bundle bundle = item.createBundle(bfile.getName());
            for (File file : bfile.listFiles(new FileFilter() {
                    public boolean accept(File file) {
                        return ! file.getName().endsWith(".xml");
                    }
            })) {
                String relPath2 = bundle.getName() + File.separator + file.getName();
                InputStream in = bag.payloadStream(relPath2);
                if (in != null) {
                    Bitstream bs = bundle.createBitstream(in);
                    // now set bitstream metadata
                    BagUtils.XmlReader reader = BagUtils.xmlReader(bag.payloadStream(relPath2 + "-metadata.xml"));
                    if (reader != null && reader.findStanza("metadata")) {
                        BagUtils.Value value = null;
                        // field access is hard-coded in Bitstream class
                        while((value = reader.nextValue()) != null) {
                            String name = value.name;
                            if ("name".equals(name)) {
                                bs.setName(value.val);
                            } else if ("source".equals(name)) {
                                bs.setSource(value.val);
                            } else if ("sequence_id".equals(name)) {
                                bs.setSequenceID(Integer.valueOf(value.val));
                            } else if ("bundle_primary".equals(name)) {
                                // special case - bundle metadata in bitstream
                                bundle.setPrimaryBitstreamID(bs.getID());
                            }
                        }
                        reader.close();
                    } else {
                        String missing = relPath + "-metadata.xml";
                        throw new IOException("Cannot locate bitstream metadata file: " + missing);
                    }
                    bs.update();
                }
            }
        }
    }

    @Override
    public long size(String method) throws SQLException {
        long size = 0L;
        // just total bitstream sizes, respecting filters
        for (Bundle bundle : item.getBundles())  {
            if (accept(bundle.getName())) {
                for (Bitstream bs : bundle.getBitstreams()) {
                    size += bs.getSize();
                }
            }
        }
        return size;
    }
    
    @Override
    public void setContentFilter(String filter) {
        //If our filter list of bundles begins with a '+', then this list
        // specifies all the bundles to *include*. Otherwise all 
        // bundles *except* the listed ones are included
        if (filter.startsWith("+")) {
            exclude = false;
            //remove the preceding '+' from our bundle list
            filter = filter.substring(1);
        }
        
        filterBundles = Arrays.asList(filter.split(","));
    }

    private boolean accept(String name) {
        boolean onList = filterBundles.contains(name);
        return exclude ? ! onList : onList;
    }

    @Override
    public void setReferenceFilter(String filterSet) {
        // parse ref filter list
        for (String filter : filterSet.split(",")) {
            refFilters.add(new RefFilter(filter));
        }
    }

    private String byReference(Bundle bundle, Bitstream bs) {
        for (RefFilter filter : refFilters) {
            if (filter.bundle.equals(bundle.getName()) &&
                filter.size == bs.getSize()) {
                return filter.url;
            }
        }
        return null;
    }

    private class RefFilter {
        public String bundle;
        public long size;
        public String url;

        public RefFilter(String filter)  {
            String[] parts = filter.split(" ");
            bundle = parts[0];
            size = Long.valueOf(parts[1]);
            url = parts[2];
        }
    }
}
