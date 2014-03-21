/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.pack.bagit;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;

import edu.mit.lib.bagit.Bag;
import edu.mit.lib.bagit.Filler;
import edu.mit.lib.bagit.Loader;

import org.dspace.authorize.AuthorizeException;
import org.dspace.content.Bitstream;
import org.dspace.content.BoundedIterator;
import org.dspace.content.Collection;
import org.dspace.content.Community;
import org.dspace.content.Item;

import org.dspace.pack.Packer;
import org.dspace.pack.PackerFactory;

import static org.dspace.pack.PackerFactory.*;

/**
 * CollectionPacker packs and unpacks Collection AIPs in BagIt bags
 *
 * @author richardrodgers
 */
public class CollectionPacker implements Packer {

    private Collection collection = null;
    private String archFmt = null;

    public CollectionPacker(Collection collection, String archFmt)  {
        this.collection = collection;
        this.archFmt = archFmt;
    }

    public Collection getCollection() {
        return collection;
    }

    public void setCollection(Collection collection) {
        this.collection = collection;
    }

    @Override
    public File pack(File packDir) throws AuthorizeException, IOException, SQLException {
        Filler filler = new Filler(packDir.toPath());
        // categorize bag
        filler.metadata(BAG_TYPE, "AIP");

        filler.property("data/object", OBJECT_TYPE, "collection");
        filler.property("data/object", OBJECT_ID, collection.getHandle());
        
        Community parent = collection.getCommunities().get(0);
        if (parent != null) {
            filler.property("data/object", OWNER_ID, parent.getHandle());
        }
        
        // then metadata
        BagUtils.writeMetadata(collection, filler.payloadStream("metadata.xml"));
        
        // also add logo if it exists
        Bitstream logo = collection.getLogo();
        if (logo != null) {
            filler.payload("logo", logo.retrieve());
        }
        return filler.toPackage(archFmt).toFile();
    }

    @Override
    public void unpack(File archive) throws AuthorizeException, IOException, SQLException {
        if (archive == null) {
            throw new IOException("Missing archive for collection: " + collection.getHandle());
        }
        Bag bag = new Loader(archive.toPath()).load();
        // add the metadata
        BagUtils.readMetadata(collection, bag.payloadStream("metadata.xml"));
          // also install logo or set to null
        collection.setLogo(bag.payloadStream("logo"));
        // now write data back to DB
        collection.update();
    }

    @Override
    public long size(String method) throws SQLException  {
        long size = 0L;
        // start with logo size, if present
        Bitstream logo = collection.getLogo();
        if (logo != null) {
            size += logo.getSize();
        }
        // proceed to items, unless 'norecurse' set
        if (! "norecurse".equals(method)) {
            BoundedIterator<Item> itemIter = collection.getItems();
            ItemPacker iPup = null;
            while (itemIter.hasNext()) {
                if (iPup == null) {
                    iPup = (ItemPacker)PackerFactory.instance(itemIter.next());
                } else {
                    iPup.setItem(itemIter.next());
                }
                size += iPup.size(method);
            }
        }
        return size;
    }

    @Override
    public void setContentFilter(String filter) {
        // no-op
    }

    @Override
    public void setReferenceFilter(String filter) {
        throw new UnsupportedOperationException("Not supported yet.");
    }
}
