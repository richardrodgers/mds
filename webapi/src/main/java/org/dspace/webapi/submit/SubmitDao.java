/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */

package org.dspace.webapi.submit;

import java.io.InputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
import org.dspace.content.InstallItem;
import org.dspace.content.MDValue;
import org.dspace.content.MetadataSchema;
import org.dspace.content.WorkspaceItem;
import org.dspace.eperson.EPerson;
import org.dspace.handle.HandleManager;
import org.dspace.mxres.MetadataSpec;
import org.dspace.mxres.ResourceMap;

import org.dspace.webapi.EntityRef;
import org.dspace.webapi.submit.domain.BitstreamEntity;
import org.dspace.webapi.submit.domain.SubmitEntity;
import org.dspace.webapi.submit.domain.ItemEntity;
import org.dspace.webapi.submit.domain.MetadataEntity;
import org.dspace.webapi.submit.domain.SpecEntity;
import org.dspace.webapi.submit.domain.Statement;

/**
 * SubmitDao provides domain objects, known as 'entities'
 * to the service. Here the entities represent item submissions
 * and their parts (bitstreams, and metadata)
 *  
 * @author richardrodgers
 */

public class SubmitDao {

    private static Logger log = LoggerFactory.getLogger(SubmitDao.class);

    public List<EntityRef> getSubmissionReferences(String handle, String contentType, String filter) throws SQLException {
        List<EntityRef> refList = new ArrayList<>();
        try (Context ctx = new Context()) {
            switch (contentType) {
                case "submissions" : getSubmissionRefs(refList, handle, ctx); break;
                default: break;
            }
            ctx.complete();
        }
        return refList;
    }

    public List<EntityRef> getSubReferences(int id, String contentType, String filter) throws SQLException {
        List<EntityRef> refList = new ArrayList<>();
        try (Context ctx = new Context()) {
            switch (contentType) {
                case "bitstreams" : getBitstreamRefs(refList, id, filter, ctx); break;
                case "mdspecs" : getMetadataSpecRefs(refList, id, -1, filter, ctx); break;
                default: break;
            }
            ctx.complete();
        }
        return refList;
    }

    public List<EntityRef> getBitstreamReferences(int id, int seqNo, String contentType, String filter) throws SQLException {
        List<EntityRef> refList = new ArrayList<>();
        try (Context ctx = new Context()) {
            switch (contentType) {
                case "mdspecs" : getMetadataSpecRefs(refList, id, seqNo, filter, ctx); break;
                default: break;
            }
            ctx.complete();
        }
        return refList;
    }

    public SubmitEntity getEntity(int id) throws SQLException {
        SubmitEntity entity;
        try (Context ctx = new Context()) {
            WorkspaceItem wsi = resolveSubmission(ctx, id);
            entity = new ItemEntity(wsi);
            ctx.complete();
        }
        return entity;
    }

    public SubmitEntity getEntity(int id, int seqNo) throws SQLException {
        SubmitEntity entity;
        try (Context ctx = new Context()) {
            WorkspaceItem wsi = resolveSubmission(ctx, id);
            Bitstream bs = wsi.getItem().getBitstreamBySequenceID(seqNo);
            if (bs == null) {
                throw new IllegalArgumentException("No such bitstream: " + id + ":" + seqNo);
            }
            entity = new BitstreamEntity(wsi, bs);
            ctx.complete();
        }
        return entity;
    }

    public SubmitEntity createEntity(String prefix, String id, String subres, EntityRef entityRef) throws AuthorizeException, IOException, SQLException {
        SubmitEntity entity;
        try (Context ctx = new Context()) {
            ctx.turnOffAuthorisationSystem();
            DSpaceObject dso = HandleManager.resolveToObject(ctx, prefix + "/" + id);
            if (dso == null || dso.getType() != Constants.COLLECTION) {
                throw new IllegalArgumentException("No collection found for: " + prefix + "/" + id);
            }
            if ("submission".equals(subres)) {
                // RLR fix this - Workspace Item expects a user - choose first created
                // which will be an admin
                EPerson submitter = EPerson.findAll(ctx, 3).get(0);
                ctx.setCurrentUser(submitter);
                WorkspaceItem wsi = WorkspaceItem.create(ctx, (Collection)dso, false);
                wsi.update();
                entity = new ItemEntity(wsi);
                ctx.complete();
            } else {
                throw new IllegalArgumentException("Unknown resource type: " + subres);
            }
        }
        return entity;
    }

    public SubmitEntity entityFromMediaWriter(MediaWriter writer, int id)  throws AuthorizeException, IOException, SQLException {
        SubmitEntity entity;
        try (Context ctx = new Context()) {
            ctx.turnOffAuthorisationSystem();
            WorkspaceItem wsi = resolveSubmission(ctx, id);
            // At the moment, all bitstreams are placed in ORIGINAL bundle,
            // because traditional submission works that way.
            // TODO: expose bundle and allow user-selection/creation
            Bundle origBundle = null;
            List<Bundle> origBundles = wsi.getItem().getBundles("ORIGINAL");
            if (origBundles.isEmpty()) {
                origBundle = wsi.getItem().createBundle("ORIGINAL");
            } else {
                origBundle = origBundles.get(0);
            }
            Bitstream bs = origBundle.createBitstream(writer.getStream());
            bs.update();
            wsi.update();
            entity = new BitstreamEntity(wsi, bs);
            ctx.complete();
        }
        return entity;
    }

    public void removeEntity(int id, boolean discard) throws AuthorizeException, IOException, SQLException {
        try (Context ctx = new Context()) {
            WorkspaceItem wsi = resolveSubmission(ctx, id);
            // either remove wrapper - if 'discard' is false, or blow the whole thing away
            if (! discard) {
                //wsi.deleteWrapper();
                // TODO - put into workflow, instead of immediate installation
                InstallItem.installItem(ctx, wsi, null);
            } else {
                wsi.deleteAll();
            }
            ctx.complete();
        }
    }

    public void removeEntity(int id, int seqNo) throws AuthorizeException, IOException, SQLException {
        try (Context ctx = new Context()) {
            WorkspaceItem wsi = resolveSubmission(ctx, id);
            Bitstream bs = wsi.getItem().getBitstreamBySequenceID(seqNo);
            if (bs == null) {
                throw new IllegalArgumentException("No such bitstream: " + id + ":" + seqNo);
            }
            wsi.getItem().getBundles("ORIGINAL").get(0).removeBitstream(bs);
            wsi.update();
            ctx.complete();
        }
    }

    public SpecEntity getMetadataSpec(int id, String name) throws SQLException {
        SpecEntity entity;
        try (Context ctx = new Context()) {
            WorkspaceItem wsi = resolveSubmission(ctx, id);
            // verify that name is known
            if (! metadataSpecNames(ctx, Constants.ITEM).contains(name)) {
                ctx.abort();
                throw new IllegalArgumentException("no such metadata spec: " + name);
            }
            entity = new SpecEntity(ctx, wsi, null, name);
            ctx.complete();
        }
        return entity;
    }

    public SpecEntity getMetadataSpec(int id, int seqNo, String name) throws IOException, SQLException {
        SpecEntity entity;
        try (Context ctx = new Context()) {
            WorkspaceItem wsi = resolveSubmission(ctx, id);
            Bitstream bs = wsi.getItem().getBitstreamBySequenceID(seqNo);
            if (bs == null) {
                throw new IllegalArgumentException("No such bitstream: " + id + ":" + seqNo);
            }
            // verify that name is known
            if (! metadataSpecNames(ctx, Constants.BITSTREAM).contains(name)) {
                ctx.abort();
                throw new IllegalArgumentException("no such metadata spec: " + name);
            }
            entity = new SpecEntity(ctx, wsi, bs, name);
            ctx.complete();
        }
        return entity;
    }

    public MetadataEntity updateMetadata(int id, String name, MetadataEntity updEntity) throws AuthorizeException, IOException, SQLException {
        Context ctx = new Context();
        WorkspaceItem wsi = resolveSubmission(ctx, id);
        DSpaceObject dso = wsi.getItem();
        // clear all metadata in the set, then add back entity content
        dso.clearMetadata(name, MDValue.ANY, MDValue.ANY, MDValue.ANY);
        for (Statement stmt : updEntity.getStatements()) {
            dso.addMetadata(name, stmt.getElement(), stmt.getQualifier(), stmt.getLanguage(), stmt.getValue());
        }
        wsi.update();
        MetadataEntity entity = new MetadataEntity(wsi, null, name);
        ctx.complete();
        return entity;
    }

    public MetadataEntity updateMetadata(int id, int seqNo, String name, MetadataEntity updEntity) throws AuthorizeException, SQLException {
        Context ctx = new Context();
        WorkspaceItem wsi = resolveSubmission(ctx, id);
        Bitstream bs = wsi.getItem().getBitstreamBySequenceID(seqNo);
        if (bs == null) {
            throw new IllegalArgumentException("No such bitstream: " + id + ":" + seqNo);
        }
        // clear all metadata in the set, then add back entity content
        bs.clearMetadata(name, MDValue.ANY, MDValue.ANY, MDValue.ANY);
        for (Statement stmt : updEntity.getStatements()) {
            bs.addMetadata(name, stmt.getElement(), stmt.getQualifier(), stmt.getLanguage(), stmt.getValue());
        }
        bs.update();
        MetadataEntity entity = new MetadataEntity(wsi, bs, name);
        ctx.complete();
        return entity;
    }

    private WorkspaceItem resolveSubmission(Context ctx, int id) throws SQLException {
        WorkspaceItem wsi = WorkspaceItem.find(ctx, id);
        if (wsi == null) {
            throw new IllegalArgumentException("no such entity: " + id);
        }
        return wsi;
    }

    private void getSubmissionRefs(List<EntityRef> refList, String handle, Context ctx) throws SQLException {
        DSpaceObject dso = HandleManager.resolveToObject(ctx, handle);
        if (dso != null && dso.getType() == Constants.COLLECTION) {
            for (WorkspaceItem wsi : WorkspaceItem.findByCollection(ctx, (Collection)dso)) {
                refList.add(new EntityRef("submission", String.valueOf(wsi.getID()), "submission"));
            }
        } else {
            throw new IllegalArgumentException("Handle: " + handle + " is not a known collection");
        }
    }

    private void getBitstreamRefs(List<EntityRef> refList, int id, String filter, Context ctx) throws SQLException {
        WorkspaceItem wsi = resolveSubmission(ctx, id); // throws exception if not found
        List<Bundle> bundles = wsi.getItem().getBundles(filter);
        // no bitstreams may exist, in which case the bundle won't yet either
        if (bundles.size() > 0) {
            for (Bitstream bs : bundles.get(0).getBitstreams()) {
                String bsId = wsi.getID() + "/bitstream/" + bs.getSequenceID();
                refList.add(new EntityRef(bs.getName(), bsId, "bitstream"));
            }
        }
    }

    private void getMetadataSpecRefs(List<EntityRef> refList, int id, int seqNo, String filter, Context ctx) throws SQLException {
        WorkspaceItem wsi = resolveSubmission(ctx, id); // throws exception if not found
        List<String> names;
        if (seqNo == -1) {
            // we want the submission (item) specs
            for (String name : metadataSpecNames(ctx, Constants.ITEM)) {
               refList.add(new EntityRef(name, id + "/mdspec/" + name, name));
            }
        } else {
            // we want the submission bitstream specs
            for (String name : metadataSpecNames(ctx, Constants.BITSTREAM)) {
               refList.add(new EntityRef(name, id + "/bitstream/" + seqNo + "/mdspec/" + name, name));
            }
        }
    }

    private List<String> metadataSpecNames(Context ctx, int objType) throws SQLException {
        ResourceMap<MetadataSpec> specMap = new ResourceMap(MetadataSpec.class, ctx);
        String keyPat = Constants.typeText[objType].toLowerCase() + "-mds-"; 
        Iterator<String> specIter = specMap.ruleKeysLike(keyPat).iterator();
        List<String> names = new ArrayList<>();
        while (specIter.hasNext()) {
            names.add(specIter.next().substring(keyPat.length()));
        }
        return names;
    }
}
