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
import org.dspace.content.MDValue;
import org.dspace.content.MetadataSchema;
import org.dspace.handle.HandleManager;
import org.dspace.webapi.content.domain.BitstreamEntity;
import org.dspace.webapi.content.domain.CommunityEntity;
import org.dspace.webapi.content.domain.CollectionEntity;
import org.dspace.webapi.content.domain.ContentEntity;
import org.dspace.webapi.content.domain.ItemEntity;
import org.dspace.webapi.content.domain.MetadataEntity;
import org.dspace.webapi.content.domain.EntityRef;
import org.dspace.webapi.content.domain.Statement;

/**
 * ContentDao provides domain objects, known as 'entities'
 * to the service.
 *  
 * @author richardrodgers
 */

public class ContentDao {

    public List<EntityRef> getContentReferences(String handle, String contentType, String filter) throws SQLException {
        List<EntityRef> refList = new ArrayList<>();
        Context ctx = new Context();
        switch (contentType) {
            case "community" : getCommunityRefs(refList, ctx); break;
            case "collections" : getCollectionRefs(refList, handle, ctx); break;
            case "subcommunities" : getSubcommunityRefs(refList, handle, ctx); break;
            case "items" : getItemRefs(refList, handle, ctx); break;
            case "bitstream" : getBitstreamRefs(refList, handle, filter, ctx); break;
            case "filters" : getFilterRefs(refList, handle, ctx); break;
            case "mdsets" : getMetadataSetRefs(refList, handle, ctx); break;
            case "mdviews" : getMetadataViewRefs(refList, handle, ctx); break;
            default: break;
        }
        ctx.complete();
        return refList;
    }

    public ContentEntity getEntity(String prefix, String id) throws SQLException {
        String[] idParts = id.split("\\.");
        Context ctx = new Context();
        DSpaceObject dso = resolveDso(ctx, prefix, id);
        ContentEntity entity = resolveEntity(ctx, dso, idParts[1]);
        ctx.complete();
        return entity;
    }

    public ContentEntity createEntity(String prefix, String id, String subres, EntityRef entityRef) throws AuthorizeException, IOException, SQLException {
        Context ctx = new Context();
        ctx.turnOffAuthorisationSystem();
        DSpaceObject dso = null;
        if (prefix == null) {
            // no parent - create a top-level community
            Community comm = Community.create(null, ctx);
            comm.setName(entityRef.getName());
            comm.update();
            dso = comm;
        } else {
            DSpaceObject parent = resolveDso(ctx, prefix, id);
            switch (subres) {
                case "subcommunities" : Community subComm = ((Community)parent).createSubcommunity();
                                        subComm.setName(entityRef.getName());
                                        subComm.update();
                                        dso = subComm; break;
                case "collections": Collection coll = ((Community)parent).createCollection();
                                    coll.setName(entityRef.getName());
                                    coll.update();
                                    dso = coll; break;
                default: break;
            }
        }
        ContentEntity entity = resolveEntity(ctx, dso, null);
        ctx.complete();
        return entity;
    }

    public ContentEntity removeEntity(String prefix, String id) throws AuthorizeException, IOException, SQLException {
        String[] idParts = id.split("\\.");
        Context ctx = new Context();
        DSpaceObject dso = resolveDso(ctx, prefix, id);
        ContentEntity entity = resolveEntity(ctx, dso, idParts[1]);
        // removal means that the parent removes the child, except if no parent, then direct delete
        DSpaceObject parent = dso.getParentObject();
        if (parent == null) {
            // top-level community
            ((Community)dso).delete();
        } else {
            // remove is non-generic - need to type objects
            switch (parent.getType()) {
                case Constants.COMMUNITY: Community comm = (Community)parent; 
                                          if (dso.getType() == Constants.COMMUNITY) comm.removeSubcommunity((Community)dso);
                                          else comm.removeCollection((Collection)dso); break;
                case Constants.COLLECTION: ((Collection)parent).removeItem((Item)dso); break;
                case Constants.ITEM: ((Item)parent).getBundles().get(0).removeBitstream((Bitstream)dso); break;
                default: break;
            }
        }
        ctx.complete();
        return entity;
    }

    public MetadataEntity getMetadataSet(String prefix, String id, String name) throws SQLException {
        String handle = prefix + "/" + id;
        MetadataEntity entity = null;
        Context ctx = new Context();
        DSpaceObject dso = HandleManager.resolveToObject(ctx, handle);
        if (dso == null) {
            ctx.complete();
            throw new IllegalArgumentException("no such entity");
        }
        entity = new MetadataEntity(dso, name);
        ctx.complete();
        return entity;
    }

    public MetadataEntity getMetadataView(String prefix, String id, String name) throws SQLException {
        String handle = prefix + "/" + id;
        MetadataEntity entity = null;
        Context ctx = new Context();
        DSpaceObject dso = HandleManager.resolveToObject(ctx, handle);
        if (dso == null) {
            ctx.complete();
            throw new IllegalArgumentException("no such entity");
        }
        entity = new MetadataEntity(dso, name);
        ctx.complete();
        return entity;
    }

     public MetadataEntity updateMetadata(String prefix, String id, String name, MetadataEntity updEntity) throws AuthorizeException, SQLException {
        String handle = prefix + "/" + id;
        MetadataEntity entity = null;
        Context ctx = new Context();
        DSpaceObject dso = HandleManager.resolveToObject(ctx, handle);
        if (dso == null) {
            ctx.complete();
            throw new IllegalArgumentException("no such entity");
        }
        // clear all metadata in the set, then add back entity content
        dso.clearMetadata(name, MDValue.ANY, MDValue.ANY, MDValue.ANY);
        for (Statement stmt : updEntity.getStatements()) {
            dso.addMetadata(name, stmt.getElement(), stmt.getQualifier(), stmt.getLanguage(), stmt.getValue());
        }
        dso.update();
        entity = new MetadataEntity(dso, name);
        ctx.complete();
        return entity;
    }

    public MediaReader getMediaReader(String prefix, String id) throws AuthorizeException, IOException, SQLException {
        String[] parts = id.split("\\."); 
        Context ctx = new Context();
        DSpaceObject dso = resolveDso(ctx, prefix, id);
        Bitstream bitstream = findBitstream(ctx, dso, parts[1]);
        MediaReader reader = new MediaReader(bitstream.retrieve(), bitstream.getFormat().getMIMEType(), bitstream.getSize());
        ctx.complete();
        return reader;
    }

    private DSpaceObject resolveDso(Context ctx, String prefix, String id) throws SQLException {
        String[] parts = id.split("\\.");
        String lid = id;
        if (parts.length > 1) {
            lid = parts[0];
        } 
        DSpaceObject dso = HandleManager.resolveToObject(ctx, prefix + "/" + lid);
        if (dso == null) {
            ctx.complete();
            throw new IllegalArgumentException("no such entity");
        }
        return dso;
    }

    private ContentEntity resolveEntity(Context ctx, DSpaceObject dso, String seqId) throws SQLException {
        switch (dso.getType()) {
            case Constants.COMMUNITY : return new CommunityEntity((Community)dso);
            case Constants.COLLECTION : return new CollectionEntity((Collection)dso);
            case Constants.ITEM : if (seqId == null) return new ItemEntity((Item)dso);
                                  else return new BitstreamEntity(findBitstream(ctx, dso, seqId));
            default: return null;
        }
    }
    
    private Bitstream findBitstream(Context ctx, DSpaceObject dso, String seq) throws SQLException {
        int seqInt = Integer.parseInt(seq);
        if (dso.getType() == Constants.ITEM) {
            // clumsy way for now
            Item item = (Item)dso;
            for (Bundle bundle : item.getBundles()) {
               for (Bitstream bs: bundle.getBitstreams()) {
                   if (bs.getSequenceID() == seqInt) {
                       return bs;
                   }
               }
            }
        } else if (dso.getType() == Constants.COMMUNITY) {
            // could be a community logo
            Bitstream cbs = ((Community)dso).getLogo();
            if (cbs.getSequenceID() == seqInt) {
                return cbs;
            }
        } else if (dso.getType() == Constants.COLLECTION) {
            // or a collection logo
            Bitstream clbs = ((Collection)dso).getLogo();
            if (clbs.getSequenceID() == seqInt) {
                return clbs;
            }
        }
        ctx.complete();
        throw new IllegalArgumentException("no such bitstream");
    }

    private void getMetadataSetRefs(List<EntityRef> refList, String handle, Context ctx) throws SQLException {
        // Currently, the only sets offered are schema partitions of the metadata - others would require new content API support
        for (MetadataSchema schema: MetadataSchema.findAll(ctx)) {
            refList.add(new EntityRef(schema.getName(), handle + "/mdset/" + schema.getName(), schema.getNamespace()));
        }
    }

    private void getMetadataViewRefs(List<EntityRef> refList, String handle, Context ctx) throws SQLException {
        // Currently, the only views offered are 'short' and 'full'
        refList.add(new EntityRef("short", handle + "/mdview/short", "short"));
        refList.add(new EntityRef("full", handle + "/mdview/full", "full"));
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
                String bsHandle = item.getHandle() + "." + bs.getSequenceID();
                refList.add(new EntityRef(bs.getName(), bsHandle, "bitstream"));
            }
        }
    }
}
