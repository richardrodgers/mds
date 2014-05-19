/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */

package org.dspace.ctask.replicate;

import java.io.IOException;
import java.sql.SQLException;

import org.dspace.authorize.AuthorizeException;
import org.dspace.content.Bitstream;
import org.dspace.content.BoundedIterator;
import org.dspace.content.Bundle;
import org.dspace.content.Collection;
import org.dspace.content.Community;
import org.dspace.content.Item;
import org.dspace.content.DSpaceObject;
import org.dspace.core.Constants;
import org.dspace.core.Context;
import org.dspace.curate.AbstractCurationTask;
import org.dspace.curate.Curator;
import org.dspace.curate.Distributive;
import org.dspace.pack.PackingSpec;
import org.dspace.pack.PackingFilter;

/**
 * EstimateAIPSize task computes the total number of bytes in all the content
 * files (viz Bitstreams, including logos for containers) of the passed object.
 * If a container, it includes all it's members or children. Note that this
 * is quite inexact (and always too small), since real AIPs will include
 * metadata, etc, but should be adequate for a gross approximation. Also note
 * that the size estimates reflect constraints introduced by the packing
 * spec defined for the object (e.g. excluding filtered content bundles).
 * 
 * @author richardrodgers
 * @see TransmitAIP
 */
@Distributive
public class EstimateAIPSize extends AbstractCurationTask {

    @Override
    public int perform(DSpaceObject dso) throws AuthorizeException, IOException, SQLException {

        if (dso == null) {
            setResult("DSpace object is null");
            return Curator.CURATE_ERROR;
        } 
        long size = 0L;
        ReplicaManager repMan = ReplicaManager.instance();
        PackingSpec spec = repMan.packingSpec(dso);
        PackingFilter filter = new PackingFilter(spec);
        if (dso.getType() == Constants.COMMUNITY) {
            size = communitySize((Community)dso, "", filter);
        } else if (dso.getType() == Constants.COLLECTION) {
            size = collectionSize((Collection)dso, "", filter);
        } else {
            size = itemSize((Item)dso, "", filter);
        }
        String msg = "ID: " + dso.getHandle() + " (" + dso.getName() +
                     ") estimated AIP size: " + scaledSize(size, 0);
        report(msg);
        setResult(scaledSize(size, 0));
        return Curator.CURATE_SUCCESS;
    }
    
    String[] prefixes = { "", "kilo", "mega", "giga", "tera", "peta", "exa" };
    private String scaledSize(long size, int idx) {
        return (size < 1000L) ? size + " " + prefixes[idx] + "bytes" :
               scaledSize(size / 1000L, idx + 1);
    }

    private long communitySize(Community community, String method, PackingFilter filter) throws SQLException {
        long size = 0L;
        // logo size, if present
        Bitstream logo = community.getLogo();
        if (logo != null) {
            size += logo.getSize();
        }
        // proceed to children, unless 'norecurse' set
        if (! "norecurse".equals(method)) {
            BoundedIterator<Community> commIter = community.getSubcommunities();
            while (commIter.hasNext()) {
                size += communitySize(commIter.next(), "", filter);
            }
            BoundedIterator<Collection> collIter = community.getCollections();
            while (collIter.hasNext()) {
                size += collectionSize(collIter.next(), "", filter);
            }
        }
        return size;
    }

    private long collectionSize(Collection collection, String method, PackingFilter filter) throws SQLException  {
        long size = 0L;
        // start with logo size, if present
        Bitstream logo = collection.getLogo();
        if (logo != null) {
            size += logo.getSize();
        }
        // proceed to items, unless 'norecurse' set
        if (! "norecurse".equals(method)) {
            BoundedIterator<Item> itemIter = collection.getItems();
            while (itemIter.hasNext()) {
                size += itemSize(itemIter.next(), "", filter);
            }
        }
        return size;
    }

    private long itemSize(Item item, String method, PackingFilter filter) throws SQLException {
        // just total bitstream sizes, respecting filters
        long size = 0L;
        for (Bundle bundle : item.getBundles())  {
            if (filter.acceptBundle(bundle.getName())) {
                for (Bitstream bs : bundle.getBitstreams()) {
                    size += bs.getSize();
                }
            }
        }
        return size;
    }
}
