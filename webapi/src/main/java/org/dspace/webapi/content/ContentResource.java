/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.webapi.content;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
//import javax.ws.rs.POST;
import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.ResponseBuilder;
import javax.ws.rs.core.UriBuilder;
import javax.ws.rs.core.UriInfo;

import static javax.ws.rs.core.MediaType.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.dspace.authorize.AuthorizeException;
import org.dspace.core.Constants;

import org.dspace.webapi.content.ContentDao;
import org.dspace.webapi.content.domain.CommunityEntity;
import org.dspace.webapi.content.domain.CollectionEntity;
import org.dspace.webapi.content.domain.ItemEntity;
import org.dspace.webapi.content.domain.BitstreamEntity;
import org.dspace.webapi.content.domain.EntityRef;

/**
 * ContentResource is a JAX-RS root resource providing a REST API for DSpaceObjects.
 * Current service interface is read-only.
 * 
 * @author richardrodgers
 */

@Path("content")
@Produces({APPLICATION_XML, APPLICATION_JSON})
public class ContentResource {

    private static Logger log = LoggerFactory.getLogger(ContentResource.class);

    private final ContentDao contentDao = new ContentDao();

    @Context UriInfo uriInfo;

    // get roots of the content hierarchy - i.e. top-level communities
    @GET @Path("roots")
    public List<EntityRef> getTopCommunities() {
        return getRefList(null, "community", null);
    }

    // get a community resource
    @GET @Path("community/{prefix}/{id}")
    public CommunityEntity getCommunity(@PathParam("prefix") String prefix, @PathParam("id") String id) {
        CommunityEntity entity = null;
        try {
            entity = contentDao.getCommunity(prefix, id);
        } catch (SQLException sqlE) {
            throw new WebApplicationException(Response.Status.INTERNAL_SERVER_ERROR);
        } catch (IllegalArgumentException iaE) {
            throw new WebApplicationException(Response.Status.NOT_FOUND);
        }
        // Inject URIs into this entity
        inject(entity);
        return entity;
    }

    // get a community's collections
    @GET @Path("community/{prefix}/{id}/collections")
    public List<EntityRef> getCommunityCollections(@PathParam("prefix") String prefix, @PathParam("id") String id) {
        return getRefList(prefix + "/" + id, "collection", null);
    }

    // get a community's subcommunities
    @GET @Path("community/{prefix}/{id}/subcommunities")
    public List<EntityRef> getSubcommunities(@PathParam("prefix") String prefix, @PathParam("id") String id) {
        return getRefList(prefix + "/" + id, "subcommunity", null);
    }

    // get a collection
    @GET @Path("collection/{prefix}/{id}")
    public CollectionEntity getCollection(@PathParam("prefix") String prefix, @PathParam("id") String id) {
        CollectionEntity entity = null;
        try {
            entity = contentDao.getCollection(prefix, id);
        } catch (SQLException sqlE) {
            throw new WebApplicationException(Response.Status.INTERNAL_SERVER_ERROR);
        } catch (IllegalArgumentException iaE) {
            throw new WebApplicationException(Response.Status.NOT_FOUND);
        }
        // Inject URIs into this entity
        inject(entity);
        return entity;
    }

    // get a collections's items
    @GET @Path("collection/{prefix}/{id}/items")
    public List<EntityRef> getCollectionItems(@PathParam("prefix") String prefix, @PathParam("id") String id) {
        return getRefList(prefix + "/" + id, "item", null);
    }

    // get an item
    @GET @Path("item/{prefix}/{id}")
    public ItemEntity getItem(@PathParam("prefix") String prefix, @PathParam("id") String id) {
        ItemEntity entity = null;
        try {
            entity = contentDao.getItem(prefix, id);
        } catch (SQLException sqlE) {
            throw new WebApplicationException(Response.Status.INTERNAL_SERVER_ERROR);
        } catch (IllegalArgumentException iaE) {
            throw new WebApplicationException(Response.Status.NOT_FOUND);
        }
        // Inject URIs into this entity
        inject(entity);
        return entity;
    }

    // get an item's bitstream filters - AKA bundles
    @GET @Path("item/{prefix}/{id}/filters")
    public List<EntityRef> getItemFilters(@PathParam("prefix") String prefix, @PathParam("id") String id) {
        return getRefList(prefix + "/" + id, "filter", null);
    }

    // get an item's bitstreams (ORIGINAL bundle)
    @GET @Path("item/{prefix}/{id}/bitstreams")
    public List<EntityRef> getItemBitstreams(@PathParam("prefix") String prefix, @PathParam("id") String id) {
        return getRefList(prefix + "/" + id, "bitstream", "ORIGINAL");
    }

    // get an item's filtered bitstreams
    @GET @Path("item/{prefix}/{id}/filter/{filter}")
    public List<EntityRef> getItemBitstreams(@PathParam("prefix") String prefix, @PathParam("id") String id, @PathParam("filter") String filter) {
        return getRefList(prefix + "/" + id, "bitstream", filter);
    }

    // get a bitstream resource
    @GET @Path("bitstream/{prefix}/{id}/{seq}")
    public BitstreamEntity getBitstream(@PathParam("prefix") String prefix, @PathParam("id") String id, @PathParam("seq") String seq) {
        BitstreamEntity entity = null;
        try {
            entity = contentDao.getBitstream(prefix, id, seq);
        } catch (SQLException sqlE) {
            throw new WebApplicationException(Response.Status.INTERNAL_SERVER_ERROR);
        } catch (IllegalArgumentException iaE) {
            throw new WebApplicationException(Response.Status.NOT_FOUND);
        }
        // Inject URIs into this entity
        inject(entity);
        return entity;
    }

     // get a bitstream's bytes
    @GET @Path("bitstream/{prefix}/{id}/{seq}/{name}")
    public Response getBitstreamBytes(@PathParam("prefix") String prefix, @PathParam("id") String id, @PathParam("seq") String seq) {
        try {
            MediaReader reader = contentDao.getBitstreamReader(prefix, id, seq);
            return Response.ok(reader.getStream()).type(reader.getMimeType()).build();
        } catch (IllegalArgumentException iaE) {
            throw new WebApplicationException(Response.Status.NOT_FOUND);
        } catch (IOException | SQLException exp) {
            throw new WebApplicationException(Response.Status.INTERNAL_SERVER_ERROR);
        } catch (AuthorizeException authE) {
            throw new WebApplicationException(Response.Status.UNAUTHORIZED);
        }
    }

    private void inject(Injectable injectable) {
        Map<String, String> sites = injectable.getUriInjections();
        for (String key : sites.keySet()) {
            UriBuilder ub = uriInfo.getBaseUriBuilder();
            String[] parts = sites.get(key).split(":");
            injectable.injectUri(key, ub.path("content").path(parts[0]).path(parts[1]).build());
        }
        Map<String, List<EntityRef>> refSites = injectable.getRefInjections();
        for (String refKey: refSites.keySet()) {
            List<EntityRef> refs = refSites.get(refKey);
            for (EntityRef ref: refs) {
                inject(ref);
            }
            injectable.injectRefs(refKey, refs);
        }
    }

    private List<EntityRef> getRefList(String handle, String refType, String filter) {
        List<EntityRef> refList = null;
        try {
            refList = contentDao.getContentReferences(handle, refType, filter);
        } catch (SQLException sqlE) {
            throw new WebApplicationException(Response.Status.INTERNAL_SERVER_ERROR);
        }
        if (refList.size() == 0) {
            throw new WebApplicationException(Response.Status.NO_CONTENT);
        }
        // inject URIs for each reference
        for (EntityRef ref : refList) {
            inject(ref);
        }
        return refList;
    }
}
