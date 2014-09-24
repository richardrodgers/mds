/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.webapi.submit;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

import javax.ws.rs.Consumes;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.QueryParam;
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

import org.dspace.webapi.EntityRef;
import org.dspace.webapi.Injectable;
import org.dspace.webapi.submit.domain.SubmitEntity;
import org.dspace.webapi.submit.domain.MetadataEntity;
import org.dspace.webapi.submit.domain.SpecEntity;

/**
 * SubmitResource is a JAX-RS root resource providing a RESTful API for 
 * managing Item submissions. Service interface provides basic CRUD operations,
 * adding bitstreams, editing metadata, etc
 * 
 * @author richardrodgers
 */

@Path("submit")
@Produces({APPLICATION_XML, APPLICATION_JSON})
public class SubmitResource {

    private static Logger log = LoggerFactory.getLogger(SubmitResource.class);
    private final SubmitDao submitDao = new SubmitDao();

    @Context UriInfo uriInfo;

    // get an entityRef list of submissions for a collection
    @GET @Path("{prefix}/{id}/submissions")
    public List<EntityRef> getSubmissionList(
            @PathParam("prefix") String prefix,
            @PathParam("id") String id) {
        return getRefList(prefix + "/" + id, "submissions", null);
    } 

    // create a new submission to a collection
    @POST @Path("{prefix}/{id}/submissions")
    public Response createSubmission(
            @PathParam("prefix") String prefix,
            @PathParam("id") String id) {
        SubmitEntity entity = null;
        try {
            entity = submitDao.createEntity(prefix, id, "submission", null);
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

    // get a submission entity
    @GET @Path("{id}")
    public SubmitEntity getSubmission(@PathParam("id") int id) {
        SubmitEntity entity = null;
        try {
            entity = submitDao.getEntity(id);
        } catch (SQLException sqlE) {
            throw new WebApplicationException(Response.Status.INTERNAL_SERVER_ERROR);
        } catch (IllegalArgumentException iaE) {
            throw new WebApplicationException(Response.Status.NOT_FOUND);
        }
        // Inject URIs into this entity
        inject(entity);
        return entity;
    }

    // remove a submission entity, either advancing it to workflow/installation, or just purging it
    @DELETE @Path("{id}")
    public Response removeSubmission(
            @PathParam("id") int id,
            @DefaultValue("false") @QueryParam("discard") boolean discard) {
        try {
            submitDao.removeEntity(id, discard);
            return Response.noContent().build();
        } catch (AuthorizeException authE) {
            throw new WebApplicationException(Response.Status.UNAUTHORIZED);
        } catch (IllegalArgumentException iaE) {
            throw new WebApplicationException(Response.Status.NOT_FOUND);
        } catch (IOException | SQLException sqlE) {
            throw new WebApplicationException(Response.Status.INTERNAL_SERVER_ERROR);
        }
    }

    // get a submission bitstream entity list
    @GET @Path("{id}/bitstreams")
    public List<EntityRef> getBitstreamList(@PathParam("id") int id) {
        return getSubRefList(id, "bitstreams", "ORIGINAL");
    } 

    // get a submission bitstream entity
    @GET @Path("{id}/bitstream/{seqNo}")
    public SubmitEntity getBitstream(
            @PathParam("id") int id,
            @PathParam("seqNo") int seqNo) {
        SubmitEntity entity = null;
        try {
            entity = submitDao.getEntity(id, seqNo);
        } catch (SQLException sqlE) {
            throw new WebApplicationException(Response.Status.INTERNAL_SERVER_ERROR);
        } catch (IllegalArgumentException iaE) {
            throw new WebApplicationException(Response.Status.NOT_FOUND);
        }
        // Inject URIs into this entity
        inject(entity);
        return entity;
    }

    // create a new submission bitstream entity by uploading file
    @POST @Path("{id}/bitstreams")
    public Response addBitstream(
            @PathParam("id") int id,
            @DefaultValue("") @HeaderParam("fileName") String name,
            InputStream in) {
        SubmitEntity entity = null;
        MediaWriter writer = null;
        try {
            writer = new MediaWriter(name, in);
            entity = submitDao.entityFromMediaWriter(writer, id);
        } catch (IllegalArgumentException iaE) {
            throw new WebApplicationException(Response.Status.NOT_FOUND);
        } catch (IOException | SQLException exp) {
            throw new WebApplicationException(Response.Status.INTERNAL_SERVER_ERROR);
        } catch (AuthorizeException authE) {
            throw new WebApplicationException(Response.Status.UNAUTHORIZED);
        }
        return Response.created(entity.getURI()).build();
    }

    // remove a submission bitstream entity
    @DELETE @Path("{id}/bitstream/{seqNo}")
    public Response removeBitstream(
            @PathParam("id") int id,
            @PathParam("seqNo") int seqNo) {
        try {
            submitDao.removeEntity(id, seqNo);
            return Response.noContent().build();
        } catch (AuthorizeException authE) {
            throw new WebApplicationException(Response.Status.UNAUTHORIZED);
        } catch (IllegalArgumentException iaE) {
            throw new WebApplicationException(Response.Status.NOT_FOUND);
        } catch (IOException | SQLException sqlE) {
            throw new WebApplicationException(Response.Status.INTERNAL_SERVER_ERROR);
        }
    }

    // get a submission mdspec entity list
    @GET @Path("{id}/mdspecs")
    public List<EntityRef> getMdSpecList(@PathParam("id") int id) {
        return getSubRefList(id, "mdspecs", null);
    } 

    // get a submission metadata spec
    @GET @Path("{id}/mdspec/{name}")
    public SubmitEntity getMdSpec(
            @PathParam("id") int id,
            @PathParam("name") String name) {
        SpecEntity entity = null;
        try {
            entity = submitDao.getMetadataSpec(id, name);
        } catch (SQLException sqlE) {
            throw new WebApplicationException(Response.Status.INTERNAL_SERVER_ERROR);
        } catch (IllegalArgumentException iaE) {
            throw new WebApplicationException(Response.Status.NOT_FOUND);
        }
        // Inject URIs into this entity
        inject(entity);
        return entity;
    }

    // update a submission entity metadata spec
    @PUT @Path("{id}/mdspec/{name}")
    @Consumes({APPLICATION_XML, APPLICATION_JSON})
    public SubmitEntity updateMetadata(
            @PathParam("id") int id,
            @PathParam("name") String name,
            MetadataEntity updEntity) {
        MetadataEntity mdEntity = null;
        try {
            mdEntity = submitDao.updateMetadata(id, name, updEntity);
        } catch (AuthorizeException authE) {
            throw new WebApplicationException(Response.Status.UNAUTHORIZED);
        } catch (IOException | SQLException sqlE) {
            throw new WebApplicationException(Response.Status.INTERNAL_SERVER_ERROR);
        } catch (IllegalArgumentException iaE) {
            throw new WebApplicationException(Response.Status.NOT_FOUND);
        }
        // Inject URIs into this entity
        inject(mdEntity);
        return mdEntity;
    }

    // get a submission bitstream entity's metadata specs
    @GET @Path("{id}/bitstream/{seqNo}/mdspecs")
    public List<EntityRef> getBitstreamMdSpecs(
            @PathParam("id") int id,
            @PathParam("seqNo") int seqNo) {
        return getBitstreamRefList(id, seqNo, "mdspec", null);
    }

    // get an bitstream entity metadata spec
    @GET @Path("{id}/bitstream/{seqNo}/mdspec/{name}")
    public SubmitEntity getBitstreamMdSpec(
            @PathParam("id") int id,
            @PathParam("seqNo") int seqNo,
            @PathParam("name") String name) {
        SpecEntity specEntity = null;
        try {
            specEntity = submitDao.getMetadataSpec(id, seqNo, name);
        } catch (IOException | SQLException sqlE) {
            log.error("SQL exception: " + sqlE.getMessage());
            sqlE.printStackTrace();
            throw new WebApplicationException(Response.Status.INTERNAL_SERVER_ERROR);
        } catch (IllegalArgumentException iaE) {
            throw new WebApplicationException(Response.Status.NOT_FOUND);
        }
        // Inject URIs into this entity
        inject(specEntity);
        return specEntity;
    }

    // update a bitstream entity metadata spec
    @PUT @Path("{id}/bitstream/{seqNo}/mdspec/{name}")
    @Consumes({APPLICATION_XML, APPLICATION_JSON})
    public SubmitEntity updateBitstreamMetadata(
            @PathParam("id") int id,
            @PathParam("seqNo") int seqNo,
            @PathParam("name") String name,
            MetadataEntity updEntity) {
        MetadataEntity mdEntity = null;
        try {
            mdEntity = submitDao.updateMetadata(id, seqNo, name, updEntity);
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

    private void inject(Injectable injectable) {
        Map<String, String> sites = injectable.getUriInjections();
        for (String key : sites.keySet()) {
            UriBuilder ub = uriInfo.getBaseUriBuilder();
            ub = ub.path("submit");
            for (String part: sites.get(key).split(":")) {
                ub = ub.path(part);
            }
            injectable.injectUri(key, ub.build());
        }
    }

    private List<EntityRef> getRefList(String handle, String refType, String filter) {
        List<EntityRef> refList = null;
        try {
            refList = submitDao.getSubmissionReferences(handle, refType, filter);
        } catch (SQLException sqlE) {
            throw new WebApplicationException(Response.Status.INTERNAL_SERVER_ERROR);
        }
        //if (refList.size() == 0) {
        //   throw new WebApplicationException(Response.Status.NO_CONTENT);
        //}
        return injectedEntityList(refList);
    }

    private List<EntityRef> getSubRefList(int id, String refType, String filter) {
        List<EntityRef> refList = null;
        try {
            refList = submitDao.getSubReferences(id, refType, filter);
        } catch (SQLException sqlE) {
            throw new WebApplicationException(Response.Status.INTERNAL_SERVER_ERROR);
        }
        return injectedEntityList(refList);
    }

    private List<EntityRef> getBitstreamRefList(int id, int seqNo, String refType, String filter) {
        List<EntityRef> refList = null;
        try {
            refList = submitDao.getBitstreamReferences(id, seqNo, refType, filter);
        } catch (SQLException sqlE) {
            throw new WebApplicationException(Response.Status.INTERNAL_SERVER_ERROR);
        }
        return injectedEntityList(refList);
    }

    private List<EntityRef> injectedEntityList(List<EntityRef> refList) {
        // inject URIs for each reference
        for (EntityRef ref : refList) {
            inject(ref);
        }
        return refList;
    }
}
