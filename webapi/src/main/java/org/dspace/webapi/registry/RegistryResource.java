/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.webapi.registry;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.POST;
import javax.ws.rs.Produces;
import javax.ws.rs.PUT;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
//import javax.ws.rs.core.Response.ResponseBuilder;
import javax.ws.rs.core.SecurityContext;
import javax.ws.rs.core.UriBuilder;
import javax.ws.rs.core.UriInfo;

import static javax.ws.rs.core.MediaType.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.primitives.Ints;

import org.dspace.authorize.AuthorizeException;
import org.dspace.content.NonUniqueMetadataException;

import org.dspace.webapi.Injectable;
import org.dspace.webapi.EntityRef;
import org.dspace.webapi.registry.domain.SchemaEntity;
import org.dspace.webapi.registry.domain.FieldEntity;
import org.dspace.webapi.registry.domain.FormatEntity;

/**
 * RegistryResource is a JAX-RS root resource providing a REST API for management of registry data.
 * Through the API, one can perform CRUD operations on metadata schema, fields, and bitstream formats.
 * (other registry data TBD)
 * 
 * @author richardrodgers
 */

@Path("registry")
@Produces({APPLICATION_XML, APPLICATION_JSON})
@Consumes({APPLICATION_XML, APPLICATION_JSON})
public class RegistryResource {

    private static Logger log = LoggerFactory.getLogger(RegistryResource.class);
    private final RegistryDao regDao = new RegistryDao();

    @Context UriInfo uriInfo;

    @GET @Path("schemas")
    public List<EntityRef> getSchemas() {
        List<EntityRef> refList = null;
        try (org.dspace.core.Context context = new org.dspace.core.Context()) {
            refList = regDao.getSchemas(context);
        } catch (SQLException sqlE) {
            throw new WebApplicationException(Response.Status.INTERNAL_SERVER_ERROR);
        }
        // inject URIs for each reference
        for (EntityRef ref : refList) {
            inject(ref);
        }
        return refList;
    }

    // look up a metadata schema by ID or unique name
    @GET @Path("schema/{key}")
    public SchemaEntity getSchema(@PathParam("key") String key) {
        SchemaEntity entity = null;
        try (org.dspace.core.Context context = new org.dspace.core.Context()) {
            Integer intKey = Ints.tryParse(key);
            if (intKey != null) {
                entity = regDao.getSchema(context, intKey);
            } else {
                entity = regDao.findSchema(context, key);
            }
        } catch (IllegalArgumentException iaE) {
            throw new WebApplicationException(Response.Status.NOT_FOUND);
        } catch (AuthorizeException authE) {
            throw new WebApplicationException(Response.Status.UNAUTHORIZED);
        } catch (SQLException sqlE) {
            throw new WebApplicationException(Response.Status.INTERNAL_SERVER_ERROR);
        }
        inject(entity);
        return entity;
    }

    // return a reference list of Schemas from query
    @GET @Path("schemas/{query}")
    public List<EntityRef> findSchemas(@PathParam("query") String query) {
        List<EntityRef> refList = new ArrayList<>();
        try (org.dspace.core.Context context = new org.dspace.core.Context()) {
            refList = regDao.findSchemas(context, query);
        } catch (SQLException sqlE) {
            throw new WebApplicationException(Response.Status.INTERNAL_SERVER_ERROR);
        }
        // inject URIs for each reference
        for (EntityRef ref : refList) {
            inject(ref);
        }
        return refList;
    }

    // return a list of fields belonging to schema
    @GET @Path("schema/{id}/fields")
    public List<EntityRef> getSchemaFields(@PathParam("id") int id) {
        return getRefList(id, "schema", "field");
    }

    // create a new metadata schema
    @POST @Path("schemas")
    public Response createSchema(SchemaEntity entity) {
        SchemaEntity newEntity = null;
        try (org.dspace.core.Context context = new org.dspace.core.Context()) {
            newEntity =  regDao.createSchema(context, entity);
        } catch (AuthorizeException authE) {
            throw new WebApplicationException(Response.Status.UNAUTHORIZED);
        } catch (SQLException sqlE) {
            throw new WebApplicationException(Response.Status.INTERNAL_SERVER_ERROR);
        }
        inject(newEntity);
        return Response.created(newEntity.getURI()).entity(newEntity).build();
    }

    // update a metadata schema
    @PUT @Path("schema/{id}")
    public SchemaEntity updateSchema(@PathParam("id") int id, SchemaEntity entity) {
        SchemaEntity updEntity = null;
        try (org.dspace.core.Context context = new org.dspace.core.Context()) {
            updEntity = regDao.updateSchema(context, id, entity);
        } catch (AuthorizeException authE) {
            throw new WebApplicationException(Response.Status.UNAUTHORIZED);
        } catch (IllegalArgumentException iaE) {
            throw new WebApplicationException(Response.Status.NOT_FOUND);
        } catch (SQLException sqlE) {
            throw new WebApplicationException(Response.Status.INTERNAL_SERVER_ERROR);
        }
        inject(updEntity);
        return updEntity;
    }

    // remove a metadata schema
    @DELETE @Path("schema/{id}")
    public Response removeSchema(@PathParam("id") int id) {
        try (org.dspace.core.Context context = new org.dspace.core.Context()) {
            regDao.removeSchema(context, id);
            return Response.noContent().build();
        } catch (AuthorizeException authE) {
            throw new WebApplicationException(Response.Status.UNAUTHORIZED);
        } catch (SQLException sqlE) {
            throw new WebApplicationException(Response.Status.INTERNAL_SERVER_ERROR);
        }
    }
    
    @GET @Path("fields")
    public List<EntityRef> getFields() {
        List<EntityRef> refList = null;
        try (org.dspace.core.Context context = new org.dspace.core.Context()) {
            refList = regDao.getFields(context);
        } catch (SQLException sqlE) {
            throw new WebApplicationException(Response.Status.INTERNAL_SERVER_ERROR);
        }
        // inject URIs for each reference
        for (EntityRef ref : refList) {
            inject(ref);
        }
        return refList;
    }

    // look up a group by ID
    @GET @Path("field/{id}")
    public FieldEntity getField(@PathParam("id") int id) {
        FieldEntity entity = null;
        try (org.dspace.core.Context context = new org.dspace.core.Context()) {
            entity = regDao.getField(context, id);
        } catch (IllegalArgumentException iaE) {
            throw new WebApplicationException(Response.Status.NOT_FOUND);
        } catch (SQLException sqlE) {
            throw new WebApplicationException(Response.Status.INTERNAL_SERVER_ERROR);
        }
        // Inject URIs into this entity
        inject(entity);
        return entity;
    }

    // create a new group
    @POST @Path("fields")
    public Response createField(FieldEntity entity) {
        FieldEntity newEntity = null;
         try {
            newEntity = regDao.createField(entity);
        } catch (AuthorizeException authE) {
            throw new WebApplicationException(Response.Status.UNAUTHORIZED);
        } catch (NonUniqueMetadataException nuE) {
            throw new WebApplicationException(Response.Status.UNAUTHORIZED);
        } catch (IOException | SQLException sqlE) {
            throw new WebApplicationException(Response.Status.INTERNAL_SERVER_ERROR);
        }
        // Inject URIs into this entity
        inject(newEntity);
        return Response.created(newEntity.getURI()).entity(newEntity).build();
    }
    
    // update a field
    @PUT @Path("field/{id}")
    public FieldEntity updateField(@PathParam("id") int id, FieldEntity fldEntity) {
        FieldEntity updEntity = null;
        try (org.dspace.core.Context context = new org.dspace.core.Context()) {
            updEntity = regDao.updateField(context, id, fldEntity);
        } catch (AuthorizeException authE) {
            throw new WebApplicationException(Response.Status.UNAUTHORIZED);
        } catch (NonUniqueMetadataException nuE) {
            throw new WebApplicationException(Response.Status.UNAUTHORIZED);
        } catch (IOException | SQLException sqlE) {
            throw new WebApplicationException(Response.Status.INTERNAL_SERVER_ERROR);
        }
        inject(updEntity);
        return updEntity;
    }

    // remove a field
    @DELETE @Path("field/{id}")
    public Response removeField(@PathParam("id") int id) {
        try (org.dspace.core.Context context = new org.dspace.core.Context()) {
            regDao.removeField(context, id);
            return Response.noContent().build();
        } catch (AuthorizeException authE) {
            throw new WebApplicationException(Response.Status.UNAUTHORIZED);
        } catch (SQLException sqlE) {
            throw new WebApplicationException(Response.Status.INTERNAL_SERVER_ERROR);
        }
    }

    // look up a format by ID
    @GET @Path("format/{id}")
    public FormatEntity getFormat(@PathParam("id") int id) {
        FormatEntity entity = null;
        try (org.dspace.core.Context context = new org.dspace.core.Context()) {
            entity = regDao.getFormat(context, id);
        } catch (IllegalArgumentException iaE) {
            throw new WebApplicationException(Response.Status.NOT_FOUND);
        } catch (SQLException sqlE) {
            throw new WebApplicationException(Response.Status.INTERNAL_SERVER_ERROR);
        }
         // Inject URIs into this entity
        inject(entity);
        return entity;
    }

    // create a new format
    @POST @Path("formats")
    public Response createFormat(FormatEntity entity) {
        FormatEntity newEntity = null;
         try {
            newEntity = regDao.createFormat(entity);
        } catch (AuthorizeException authE) {
            throw new WebApplicationException(Response.Status.UNAUTHORIZED);
        } catch (SQLException sqlE) {
            throw new WebApplicationException(Response.Status.INTERNAL_SERVER_ERROR);
        }
        // Inject URIs into this entity
        inject(newEntity);
        return Response.created(newEntity.getURI()).entity(newEntity).build();
    }

    // update a format
    @PUT @Path("format/{id}")
    public FormatEntity updateFormat(@PathParam("id") int id, FormatEntity fmEntity) {
        FormatEntity updEntity = null;
        try (org.dspace.core.Context context = new org.dspace.core.Context()) {
            updEntity = regDao.updateFormat(context, id, fmEntity);
        } catch (AuthorizeException authE) {
            throw new WebApplicationException(Response.Status.UNAUTHORIZED);
        } catch (SQLException sqlE) {
            throw new WebApplicationException(Response.Status.INTERNAL_SERVER_ERROR);
        }
        inject(updEntity);
        return updEntity;
    }

    // remove a format
    @DELETE @Path("format/{id}")
    public Response removeFormat(@PathParam("id") int id) {
        try (org.dspace.core.Context context = new org.dspace.core.Context()) {
            regDao.removeFormat(context, id);
            return Response.noContent().build();
        } catch (AuthorizeException authE) {
            throw new WebApplicationException(Response.Status.UNAUTHORIZED);
        } catch (SQLException sqlE) {
            throw new WebApplicationException(Response.Status.INTERNAL_SERVER_ERROR);
        }
    }

    private List<EntityRef> getRefList(int id, String refType, String targType) {
        List<EntityRef> refList = null;
        try {
            refList = regDao.getReferences(id, refType, targType);
        } catch (IllegalArgumentException iaE) {
            throw new WebApplicationException(Response.Status.NOT_FOUND);
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

    private void inject(Injectable injectable) {
        Map<String, String> sites = injectable.getUriInjections();
        for (String key : sites.keySet()) {
            UriBuilder ub = uriInfo.getBaseUriBuilder();
            ub = ub.path("registry");
            for (String part: sites.get(key).split(":")) {
                ub = ub.path(part);
            }
            injectable.injectUri(key, ub.build());
        }
    }
}
