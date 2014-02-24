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
import java.util.Iterator;
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
import org.dspace.mxres.MetadataView;
import org.dspace.mxres.ResourceMap;
import org.dspace.webapi.content.domain.BitstreamEntity;
import org.dspace.webapi.content.domain.CommunityEntity;
import org.dspace.webapi.content.domain.CollectionEntity;
import org.dspace.webapi.content.domain.ContentEntity;
import org.dspace.webapi.content.domain.ItemEntity;
import org.dspace.webapi.content.domain.MetadataEntity;
import org.dspace.webapi.content.domain.ViewEntity;
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
        Context ctx = new Context();
        DSpaceObject dso = resolveDso(ctx, prefix, id);
        ContentEntity entity = resolveEntity(dso);
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
        ContentEntity entity = resolveEntity(dso);
        ctx.complete();
        return entity;
    }

    public void removeEntity(String prefix, String id) throws AuthorizeException, IOException, SQLException {
        Context ctx = new Context();
        DSpaceObject dso = resolveDso(ctx, prefix, id);
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
    }

    public MetadataEntity getMetadataSet(String prefix, String id, String name) throws SQLException {
        Context ctx = new Context();
        DSpaceObject dso = resolveDso(ctx, prefix, id);
        MetadataEntity entity = new MetadataEntity(dso, name);
        ctx.complete();
        return entity;
    }

    public ViewEntity getMetadataView(String prefix, String id, String name) throws SQLException {
        Context ctx = new Context();
        DSpaceObject dso = resolveDso(ctx, prefix, id);
        // now look up view for this name
        ViewEntity entity = new ViewEntity(ctx, dso, name);
        ctx.complete();
        return entity;
    }

     public MetadataEntity updateMetadata(String prefix, String id, String name, MetadataEntity updEntity) throws AuthorizeException, SQLException {
        Context ctx = new Context();
        DSpaceObject dso = resolveDso(ctx, prefix, id);
        // clear all metadata in the set, then add back entity content
        dso.clearMetadata(name, MDValue.ANY, MDValue.ANY, MDValue.ANY);
        for (Statement stmt : updEntity.getStatements()) {
            dso.addMetadata(name, stmt.getElement(), stmt.getQualifier(), stmt.getLanguage(), stmt.getValue());
        }
        dso.update();
        MetadataEntity entity = new MetadataEntity(dso, name);
        ctx.complete();
        return entity;
    }

    public MediaReader getMediaReader(String prefix, String id) throws AuthorizeException, IOException, SQLException {
        Context ctx = new Context();
        Bitstream bitstream = (Bitstream)resolveDso(ctx, prefix, id);
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
            ctx.abort();
            throw new IllegalArgumentException("no such entity");
        }
        // if there is a sequence id, descend to bitstream
        if (parts.length > 1) {
            dso = findBitstream(ctx, dso, parts[1]);
            if (dso == null) {
                ctx.abort();
                throw new IllegalArgumentException("no such entity");
            }          
        }
        return dso;
    }

    private ContentEntity resolveEntity(DSpaceObject dso) throws SQLException {
        switch (dso.getType()) {
            case Constants.COMMUNITY : return new CommunityEntity((Community)dso);
            case Constants.COLLECTION : return new CollectionEntity((Collection)dso);
            case Constants.ITEM : return new ItemEntity((Item)dso);
            case Constants.BITSTREAM : return new BitstreamEntity((Bitstream)dso);
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
        ResourceMap<MetadataView> viewMap = new ResourceMap(MetadataView.class, ctx);
        // determine what kind of object handle belongs to
        String id = handle.substring(handle.indexOf("/") + 1);
        int objType = (id.indexOf(".") > 0) ? Constants.BITSTREAM : HandleManager.resolveToType(ctx, handle);
        String keyPat = Constants.typeText[objType].toLowerCase() + "-mdv-"; 
        Iterator<String> viewIter = viewMap.ruleKeysLike(keyPat).iterator();
        // Typically, the only views offered are 'brief' and 'full', but it's user-configurable
        while (viewIter.hasNext()) {
            String view = viewIter.next().substring(keyPat.length());
            refList.add(new EntityRef(view, handle + "/mdview/" + view, view));
        }
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
