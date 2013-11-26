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

import org.dspace.webapi.content.domain.ContentEntity;
import org.dspace.webapi.content.domain.MetadataEntity;
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
    @GET @Path("/")
    public List<EntityRef> getRoots() {
        return getRefList(null, "community", null);
    }

    // get a content entity (community, collection, item, bitstream)
    @GET @Path("{prefix}/{id}")
    public ContentEntity getContent(@PathParam("prefix") String prefix, @PathParam("id") String id) {
        ContentEntity entity = null;
        try {
            entity = contentDao.getEntity(prefix, id);
        } catch (SQLException sqlE) {
            throw new WebApplicationException(Response.Status.INTERNAL_SERVER_ERROR);
        } catch (IllegalArgumentException iaE) {
            throw new WebApplicationException(Response.Status.NOT_FOUND);
        }
        // Inject URIs into this entity
        inject(entity);
        return entity;
    }

    // get an entity sub-resource list
    @GET @Path("{prefix}/{id}/{subres}")
    public List<EntityRef> getResourceList(@PathParam("prefix") String prefix, @PathParam("id") String id, @PathParam("subres") String subres) {
        return getRefList(prefix + "/" + id, subres, null);
    } 

    // get an item's filtered bitstreams
    @GET @Path("{prefix}/{id}/filter/{filter}")
    public List<EntityRef> getItemBitstreams(@PathParam("prefix") String prefix, @PathParam("id") String id, @PathParam("filter") String filter) {
        return getRefList(prefix + "/" + id, "bitstream", filter);
    }

    // get an entity metadata set
    @GET @Path("{prefix}/{id}/mdset/{name}")
    public MetadataEntity getMetadata(@PathParam("prefix") String prefix, @PathParam("id") String id, @PathParam("name") String name) {
        MetadataEntity mdEntity = null;
        try {
            mdEntity = contentDao.getMetadata(prefix, id, name);
        } catch (SQLException sqlE) {
            throw new WebApplicationException(Response.Status.INTERNAL_SERVER_ERROR);
        } catch (IllegalArgumentException iaE) {
            throw new WebApplicationException(Response.Status.NOT_FOUND);
        }
        // Inject URIs into this entity
        inject(mdEntity);
        return mdEntity;
    }

    // get an entity media object (= the bitstream bytes)
    @GET @Path("{prefix}/{id}/media/{name}")
    public Response getMedia(@PathParam("prefix") String prefix, @PathParam("id") String id) {
        try {
            MediaReader reader = contentDao.getMediaReader(prefix, id);
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
            ub = ub.path("content");
            for (String part: sites.get(key).split(":")) {
                ub = ub.path(part);
            }
            injectable.injectUri(key, ub.build());
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
