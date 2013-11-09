/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.webapi.content;

import java.io.InputStream;
import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.dspace.authorize.AuthorizeException;
import org.dspace.core.Context;
import org.dspace.core.Constants;
import org.dspace.content.BoundedIterator;
import org.dspace.content.Bitstream;
import org.dspace.content.Bundle;
import org.dspace.content.Collection;
import org.dspace.content.Community;
import org.dspace.content.Item;
import org.dspace.content.DSpaceObject;
import org.dspace.handle.HandleManager;
import org.dspace.webapi.content.domain.BitstreamEntity;
import org.dspace.webapi.content.domain.CommunityEntity;
import org.dspace.webapi.content.domain.CollectionEntity;
import org.dspace.webapi.content.domain.ItemEntity;
import org.dspace.webapi.content.domain.EntityRef;

/**
 * ContentDao provides domain objects, known as 'entities'
 * to the service.
 *  
 * @author richardrodgers
 *
 */

public class ContentDao {

    public List<EntityRef> getContentReferences(String handle, String contentType, String filter) throws SQLException {
        List<EntityRef> refList = new ArrayList<>();
        Context ctx = new Context();
        switch (contentType) {
            case "community" : getCommunityRefs(refList, ctx); break;
            case "collection" : getCollectionRefs(refList, handle, ctx); break;
            case "subcommunity" : getSubcommunityRefs(refList, handle, ctx); break;
            case "item" : getItemRefs(refList, handle, ctx); break;
            case "bitstream" : getBitstreamRefs(refList, handle, filter, ctx); break;
            case "filter" : getFilterRefs(refList, handle, ctx); break;
            default: break;
        }
        ctx.complete();
        return refList;
    }

    public CommunityEntity getCommunity(String prefix, String id) throws SQLException {
        String handle = prefix + "/" + id;
        CommunityEntity entity = null;
        Context ctx = new Context();
        DSpaceObject dso = HandleManager.resolveToObject(ctx, handle);
        if (dso == null) {
            ctx.complete();
            throw new IllegalArgumentException("no such community");
        }
        if (dso.getType() == Constants.COMMUNITY) {
            entity = new CommunityEntity((Community)dso);
        }
        ctx.complete();
        return entity;
    }

    public CollectionEntity getCollection(String prefix, String id) throws SQLException {
        String handle = prefix + "/" + id;
        CollectionEntity entity = null;
        Context ctx = new Context();
        DSpaceObject dso = HandleManager.resolveToObject(ctx, handle);
        if (dso == null) {
            ctx.complete();
            throw new IllegalArgumentException("no such collection");
        }
        if (dso.getType() == Constants.COLLECTION) {
            entity = new CollectionEntity((Collection)dso);
        }
        ctx.complete();
        return entity;
    }

    public ItemEntity getItem(String prefix, String id) throws SQLException {
        String handle = prefix + "/" + id;
        ItemEntity entity = null;
        Context ctx = new Context();
        DSpaceObject dso = HandleManager.resolveToObject(ctx, handle);
        if (dso == null) {
            ctx.complete();
            throw new IllegalArgumentException("no such item");
        }
        if (dso.getType() == Constants.ITEM) {
            entity = new ItemEntity((Item)dso);
        }
        ctx.complete();
        return entity;
    }

    public BitstreamEntity getBitstream(String prefix, String id, String seq) throws SQLException {
        return new BitstreamEntity(findBitstream(prefix + "/" + id, seq));
    }

    public MediaReader getBitstreamReader(String prefix, String id, String seq) throws AuthorizeException, IOException, SQLException {
        Bitstream bitstream = findBitstream(prefix + "/" + id, seq);
        return new MediaReader(bitstream.retrieve(), bitstream.getFormat().getMIMEType(), bitstream.getSize());
    }

    private Bitstream findBitstream(String handle, String seq) throws SQLException {
        Context ctx = new Context();
        DSpaceObject dso = HandleManager.resolveToObject(ctx, handle);
        if (dso == null) {
            ctx.complete();
            throw new IllegalArgumentException("no such item");
        }
        if (dso.getType() == Constants.ITEM) {
            // clumsy way for now
            Item item = (Item)dso;
            int seqInt = Integer.parseInt(seq);
            for (Bundle bundle : item.getBundles()) {
               for (Bitstream bs: bundle.getBitstreams()) {
                   if (bs.getSequenceID() == seqInt) {
                       ctx.complete();
                       return bs;
                   }
               }
            }
        }
        ctx.complete();
        throw new IllegalArgumentException("no such bitstream");
    }

    private void getCommunityRefs(List<EntityRef> refList, Context ctx) throws SQLException {
        BoundedIterator<Community> comIter = Community.findAllTop(ctx);
        while (comIter.hasNext()) {
            Community comm = comIter.next();
            refList.add(new EntityRef(comm.getName(), comm.getHandle(), "community"));
        }
    }

    private void getCollectionRefs(List<EntityRef> refList, String handle, Context ctx) throws SQLException {
        DSpaceObject dso = HandleManager.resolveToObject(ctx, handle);
        if (dso != null && dso.getType() == Constants.COMMUNITY) {
            Community comm = (Community)dso;
            BoundedIterator<Collection> collIter = comm.getCollections();
            while (collIter.hasNext()) {
                Collection coll = collIter.next();
                refList.add(new EntityRef(coll.getName(), coll.getHandle(), "collection"));
            }
        }
    }

    private void getSubcommunityRefs(List<EntityRef> refList, String handle, Context ctx) throws SQLException {
        DSpaceObject dso = HandleManager.resolveToObject(ctx, handle);
        if (dso != null && dso.getType() == Constants.COMMUNITY) {
            Community comm = (Community)dso;
            BoundedIterator<Community> commIter = comm.getSubcommunities();
            while (commIter.hasNext()) {
                Community subcomm = commIter.next();
                refList.add(new EntityRef(subcomm.getName(), subcomm.getHandle(), "community"));
            }
        }
    }

    private void getItemRefs(List<EntityRef> refList, String handle, Context ctx) throws SQLException {
        DSpaceObject dso = HandleManager.resolveToObject(ctx, handle);
        if (dso != null && dso.getType() == Constants.COLLECTION) {
            Collection coll = (Collection)dso;
            BoundedIterator<Item> itemIter = coll.getItems();
            while (itemIter.hasNext()) {
                Item item = itemIter.next();
                refList.add(new EntityRef(item.getName(), item.getHandle(), "item"));
            }
        }
    }

    private void getFilterRefs(List<EntityRef> refList, String handle, Context ctx) throws SQLException {
        DSpaceObject dso = HandleManager.resolveToObject(ctx, handle);
        if (dso != null && dso.getType() == Constants.ITEM) {
            Item item = (Item)dso;
            // Only currently defined filters are Bundles
            for (Bundle bundle : item.getBundles()) {
                String path = item.getHandle() + "/filter/" + bundle.getName();
                refList.add(new EntityRef(bundle.getName(), path, "item"));
            }
        }
    }

    private void getBitstreamRefs(List<EntityRef> refList, String handle, String filter, Context ctx) throws SQLException {
        DSpaceObject dso = HandleManager.resolveToObject(ctx, handle);
        if (dso != null && dso.getType() == Constants.ITEM) {
            Item item = (Item)dso;
            for (Bitstream bs : item.getBundles(filter).get(0).getBitstreams()) {
                String bsHandle = item.getHandle() + "/" + bs.getSequenceID();
                refList.add(new EntityRef(bs.getName(), bsHandle, "bitstream"));
            }
        }
    }
}
