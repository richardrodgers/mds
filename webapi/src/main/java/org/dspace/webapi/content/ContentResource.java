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

import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
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

import org.dspace.webapi.Injectable;
import org.dspace.webapi.content.domain.ContentEntity;
import org.dspace.webapi.content.domain.MetadataEntity;
import org.dspace.webapi.content.domain.ViewEntity;
import org.dspace.webapi.content.domain.EntityRef;

/**
 * ContentResource is a JAX-RS root resource providing a RESTful API for DSpaceObjects.
 * Service interface provides basic CRUD operations.
 * 
 * @author richardrodgers
 */

@Path("content")
@Produces({APPLICATION_XML, APPLICATION_JSON})
public class ContentResource {

    private static Logger log = LoggerFactory.getLogger(ContentResource.class);
    private final ContentDao contentDao = new ContentDao();

    @Context UriInfo uriInfo;

    // get list of roots of the content hierarchy - i.e. top-level communities
    @GET @Path("/")
    public List<EntityRef> getRoots() {
        return getRefList(null, "community", null);
    }

    // get list of supported package types for roots - i.e. top-level communities
    @GET @Path("packages")
    public List<EntityRef> getRootPackageSpecs() {
        return getRefList(null, "packages", null);
    }

    // create a new root in the content hierarchy - i.e a top-level community
    @POST @Path("/")
    @Consumes({APPLICATION_XML, APPLICATION_JSON})
    public Response createRoot(EntityRef entityRef) {
        ContentEntity entity = null;
         try {
            entity = contentDao.createEntity(null, null, null, entityRef);
        } catch (AuthorizeException authE) {
            throw new WebApplicationException(Response.Status.UNAUTHORIZED);
        } catch (IOException | SQLException sqlE) {
            throw new WebApplicationException(Response.Status.INTERNAL_SERVER_ERROR);
        }
        // Inject URIs into this entity
        inject(entity);
        return Response.created(entity.getURI()).build();
    }

    // create a new root in the content hierarchy - i.e a top-level community from a package
    @POST @Path("package/{name}")
    @Consumes("application/zip")
    public Response rootfromPackage(@PathParam("name") String name, InputStream in) {
        ContentEntity entity = null;
        try {
            entity = contentDao.entityFromPackage(null, null, name, in);
        } catch (AuthorizeException authE) {
            throw new WebApplicationException(Response.Status.UNAUTHORIZED);
        } catch (IllegalArgumentException iaE) {
            throw new WebApplicationException(Response.Status.NOT_FOUND);
        } catch (IOException | SQLException sqlE) {
            throw new WebApplicationException(Response.Status.INTERNAL_SERVER_ERROR);
        }
        inject(entity);
        return Response.created(entity.getURI()).build();
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

    // remove a content entity
    @DELETE @Path("{prefix}/{id}")
    public Response removeContent(@PathParam("prefix") String prefix, @PathParam("id") String id) {
        try {
            contentDao.removeEntity(prefix, id);
            return Response.noContent().build();
        } catch (AuthorizeException authE) {
            throw new WebApplicationException(Response.Status.UNAUTHORIZED);
        } catch (IllegalArgumentException iaE) {
            throw new WebApplicationException(Response.Status.NOT_FOUND);
        } catch (IOException | SQLException sqlE) {
            throw new WebApplicationException(Response.Status.INTERNAL_SERVER_ERROR);
        }
    }

    // get an entity sub-resource list
    @GET @Path("{prefix}/{id}/{subres}")
    public List<EntityRef> getResourceList(@PathParam("prefix") String prefix, @PathParam("id") String id, @PathParam("subres") String subres) {
        return getRefList(prefix + "/" + id, subres, null);
    } 

    // create an entity sub-resource
    @POST @Path("{prefix}/{id}/{subres}")
    @Consumes({APPLICATION_XML, APPLICATION_JSON})
    public Response createContent(@PathParam("prefix") String prefix, @PathParam("id") String id, @PathParam("subres") String subres, EntityRef entityRef) {
        ContentEntity entity = null;
        try {
            entity = contentDao.createEntity(prefix, id, subres, entityRef);
        } catch (AuthorizeException authE) {
            throw new WebApplicationException(Response.Status.UNAUTHORIZED);
        } catch (IllegalArgumentException iaE) {
            throw new WebApplicationException(Response.Status.NOT_FOUND);
        } catch (IOException | SQLException sqlE) {
            throw new WebApplicationException(Response.Status.INTERNAL_SERVER_ERROR);
        }
        inject(entity);
        return Response.created(entity.getURI()).build();
    }

    // get an item's filtered bitstreams
    @GET @Path("{prefix}/{id}/filter/{name}")
    public List<EntityRef> getItemBitstreams(@PathParam("prefix") String prefix, @PathParam("id") String id, @PathParam("name") String name) {
        return getRefList(prefix + "/" + id, "bitstream", name);
    }

    // get an entity metadata set
    @GET @Path("{prefix}/{id}/mdset/{name}")
    public ContentEntity getMetadataSet(@PathParam("prefix") String prefix, @PathParam("id") String id, @PathParam("name") String name) {
        MetadataEntity mdEntity = null;
        try {
            mdEntity = contentDao.getMetadataSet(prefix, id, name);
        } catch (SQLException sqlE) {
            log.error("SQL exception: " + sqlE.getMessage());
            sqlE.printStackTrace();
            throw new WebApplicationException(Response.Status.INTERNAL_SERVER_ERROR);
        } catch (IllegalArgumentException iaE) {
            throw new WebApplicationException(Response.Status.NOT_FOUND);
        }
        // Inject URIs into this entity
        inject(mdEntity);
        return mdEntity;
    }

    // get an entity metadata view
    @GET @Path("{prefix}/{id}/mdview/{name}")
    public ContentEntity getMetadataView(@PathParam("prefix") String prefix, @PathParam("id") String id, @PathParam("name") String name) {
        ViewEntity mdEntity = null;
        try {
            mdEntity = contentDao.getMetadataView(prefix, id, name);
        } catch (SQLException sqlE) {
            throw new WebApplicationException(Response.Status.INTERNAL_SERVER_ERROR);
        } catch (IllegalArgumentException iaE) {
            throw new WebApplicationException(Response.Status.NOT_FOUND);
        }
        // Inject URIs into this entity
        inject(mdEntity);
        return mdEntity;
    }

    // update an entity metadata set
    @PUT @Path("{prefix}/{id}/mdset/{name}")
    @Consumes({APPLICATION_XML, APPLICATION_JSON})
    public ContentEntity updateMetadata(@PathParam("prefix") String prefix, @PathParam("id") String id, @PathParam("name") String name, MetadataEntity updEntity) {
        MetadataEntity mdEntity = null;
        try {
            mdEntity = contentDao.updateMetadata(prefix, id, name, updEntity);
        } catch (AuthorizeException authE) {
            throw new WebApplicationException(Response.Status.UNAUTHORIZED);
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

    // get an entity package object
    @GET @Path("{prefix}/{id}/package/{name}")
    public Response getPackage(@PathParam("prefix") String prefix, @PathParam("id") String id, @PathParam("name") String name) {
        try {
            PackageReader reader = contentDao.getPackageReader(prefix, id, name);
            return Response.ok(reader.getStream()).type(reader.getMimeType()).build();
         } catch (IllegalArgumentException iaE) {
            throw new WebApplicationException(Response.Status.NOT_FOUND);
        } catch (IOException | SQLException exp) {
            log.error("SQL exception: " + exp.getMessage());
            exp.printStackTrace();
            throw new WebApplicationException(Response.Status.INTERNAL_SERVER_ERROR);
        } catch (AuthorizeException authE) {
            log.error("Auth exception: " + authE.getMessage());
            authE.printStackTrace();
            throw new WebApplicationException(Response.Status.UNAUTHORIZED);
        }
    }

    // create an entity from a packaged representation (SIP)
    @POST @Path("{prefix}/{id}/package/{name}")
    @Consumes("application/zip")
    public Response contentFromPackage(@PathParam("prefix") String prefix, @PathParam("id") String id, @PathParam("name") String name, InputStream in) {
        ContentEntity entity = null;
        try {
            entity = contentDao.entityFromPackage(prefix, id, name, in);
        } catch (AuthorizeException authE) {
            throw new WebApplicationException(Response.Status.UNAUTHORIZED);
        } catch (IllegalArgumentException iaE) {
            throw new WebApplicationException(Response.Status.NOT_FOUND);
        } catch (IOException | SQLException sqlE) {
            throw new WebApplicationException(Response.Status.INTERNAL_SERVER_ERROR);
        }
        inject(entity);
        return Response.created(entity.getURI()).build();
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
