/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.webapi.info;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
//import javax.ws.rs.core.Response.ResponseBuilder;
import javax.ws.rs.core.SecurityContext;
import javax.ws.rs.core.UriBuilder;
import javax.ws.rs.core.UriInfo;
import javax.servlet.ServletContext;

import static javax.ws.rs.core.MediaType.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.dspace.authorize.AuthorizeException;
import org.dspace.content.NonUniqueMetadataException;

import org.dspace.webapi.Injectable;
import org.dspace.webapi.EntityRef;
import org.dspace.webapi.info.domain.FieldsEntity;
import org.dspace.webapi.info.domain.FormatsEntity;
import org.dspace.webapi.info.domain.InfoEntity;
import org.dspace.webapi.info.domain.ServerEntity;
import org.dspace.webapi.info.domain.SystemEntity;
import org.dspace.webapi.info.domain.AssetsEntity;
import org.dspace.webapi.info.domain.UsersEntity;
import org.dspace.webapi.info.domain.WorkflowEntity;

/**
 * InfoResource is a JAX-RS root resource providing a REST API for the display of system data,
 * which includes server and environmental information, repository content profiling data,
 * software configuration, etc. All resources read-only.
 * 
 * @author richardrodgers
 */

@Path("info")
@Produces({APPLICATION_JSON, APPLICATION_XML})
public class InfoResource {

    private static Logger log = LoggerFactory.getLogger(InfoResource.class);
    private final InfoDao infoDao = new InfoDao();

    @Context UriInfo uriInfo;
    @Context ServletContext srvCtx;

    @GET @Path("/")
    public List<EntityRef> infoEndpoints() {
        // just the catalog of info endpoints
        List<EntityRef> refList = new ArrayList<>();
        refList.add(new EntityRef("Server Environment", "server", "serverInfo"));
        refList.add(new EntityRef("Software Installation", "system", "systemInfo"));
        refList.add(new EntityRef("Assets", "assets", "assetInfo"));
        refList.add(new EntityRef("Metadata", "metadata", "metadataInfo"));
        refList.add(new EntityRef("Bitstream Formats", "formats", "formatsInfo"));
        refList.add(new EntityRef("Users", "users", "userInfo"));
        refList.add(new EntityRef("Workflow", "workflow", "workflowInfo"));
        // inject URIs for each reference
        for (EntityRef ref : refList) {
            inject(ref);
        }
        return refList;
    }

    @GET @Path("server")
    public ServerEntity serverInfo() {
        try (org.dspace.core.Context context = new org.dspace.core.Context()) {
            ServerEntity ent = infoDao.getServer(context, srvCtx.getServerInfo());
            context.complete();
            return ent;
        } catch (SQLException sqlE) {
            throw new WebApplicationException(Response.Status.INTERNAL_SERVER_ERROR);
        }
       //return new ServerEntity(srvCtx.getServerInfo());
    }

    @GET @Path("system")
    public InfoEntity systemInfo() {
        try (org.dspace.core.Context context = new org.dspace.core.Context()) {
            SystemEntity ent = infoDao.getSystem(context);
            context.complete();
            return ent;
        } catch (SQLException sqlE) {
            throw new WebApplicationException(Response.Status.INTERNAL_SERVER_ERROR);
        }
    }

    @GET @Path("assets")
    public AssetsEntity assetsInfo() {
        try (org.dspace.core.Context context = new org.dspace.core.Context()) {
            AssetsEntity ent = infoDao.getAssets(context);
            context.complete();
            return ent;
        } catch (SQLException sqlE) {
            throw new WebApplicationException(Response.Status.INTERNAL_SERVER_ERROR);
        }
    }

    @GET @Path("metadata")
    public List<EntityRef> metadataInfo() {
        List<EntityRef> refList = null;
        try (org.dspace.core.Context context = new org.dspace.core.Context()) {
            refList = infoDao.getSchemas(context);
            context.complete();
        } catch (SQLException sqlE) {
            throw new WebApplicationException(Response.Status.INTERNAL_SERVER_ERROR);
        }
        // inject URIs for each reference
        for (EntityRef ref : refList) {
            inject(ref);
        }
        return refList;
    }

    // look up metadata schema fields by ID or unique name
    @GET @Path("metadata/{key}")
    public InfoEntity schemaInfo(@PathParam("key") String key) {
        FieldsEntity entity = null;
        try (org.dspace.core.Context context = new org.dspace.core.Context()) {
            entity = infoDao.getFields(context, key);
            context.complete();
        } catch (IllegalArgumentException iaE) {
            throw new WebApplicationException(Response.Status.NOT_FOUND);
        //} catch (AuthorizeException authE) {
        //    throw new WebApplicationException(Response.Status.UNAUTHORIZED);
        } catch (SQLException sqlE) {
            throw new WebApplicationException(Response.Status.INTERNAL_SERVER_ERROR);
        }
        return entity;
    }

    // return a list of bitstream formats and their occurence counts
    @GET @Path("formats")
    public InfoEntity formatsInfo() {
        try (org.dspace.core.Context context = new org.dspace.core.Context()) {
            FormatsEntity ent = infoDao.getFormats(context);
            context.complete();
            return ent;
        } catch (SQLException sqlE) {
            throw new WebApplicationException(Response.Status.INTERNAL_SERVER_ERROR);
        }
    }
    
    @GET @Path("users")
    public UsersEntity userInfo() {
        try (org.dspace.core.Context context = new org.dspace.core.Context()) {
            UsersEntity ent = infoDao.getUsers(context);
            context.complete();
            return ent;
        } catch (SQLException sqlE) {
            throw new WebApplicationException(Response.Status.INTERNAL_SERVER_ERROR);
        }
    }

    @GET @Path("workflow")
    public WorkflowEntity workflowInfo() {
        try (org.dspace.core.Context context = new org.dspace.core.Context()) {
            WorkflowEntity ent = infoDao.getWorkflow(context);
            context.complete();
            return ent;
        } catch (SQLException sqlE) {
            throw new WebApplicationException(Response.Status.INTERNAL_SERVER_ERROR);
        }
    }

    private void inject(Injectable injectable) {
        Map<String, String> sites = injectable.getUriInjections();
        for (String key : sites.keySet()) {
            UriBuilder ub = uriInfo.getBaseUriBuilder();
            ub = ub.path("info");
            for (String part: sites.get(key).split(":")) {
                ub = ub.path(part);
            }
            injectable.injectUri(key, ub.build());
        }
    }
}
