/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.webapi.authz;

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
import org.dspace.webapi.Injectable;

import org.dspace.webapi.authz.domain.EPersonEntity;
import org.dspace.webapi.authz.domain.EntityRef;
import org.dspace.webapi.authz.domain.GroupEntity;
import org.dspace.webapi.authz.domain.LinkEntity;
import org.dspace.webapi.authz.domain.PolicyEntity;

/**
 * AuthorizationResource is a JAX-RS root resource providing a REST API for authorization.
 * Through the API, one can perform CRUD operations on epersons, groups, and resource policies,
 * attach or remove policies to content objects, etc.
 * 
 * @author richardrodgers
 */

@Path("authz")
@Produces({APPLICATION_XML, APPLICATION_JSON})
@Consumes({APPLICATION_XML, APPLICATION_JSON})
public class AuthorizationResource {

    private static Logger log = LoggerFactory.getLogger(AuthorizationResource.class);
    private final AuthorizationDao authzDao = new AuthorizationDao();

    @Context UriInfo uriInfo;

    @GET @Path("epeople")
    public List<EntityRef> getEPeople() {
        List<EntityRef> refList = null;
        try (org.dspace.core.Context context = new org.dspace.core.Context()) {
            refList = authzDao.getEPeople(context);
        } catch (SQLException sqlE) {
            throw new WebApplicationException(Response.Status.INTERNAL_SERVER_ERROR);
        }
        // inject URIs for each reference
        for (EntityRef ref : refList) {
            inject(ref);
        }
        return refList;
    }

    // find an EPerson by unique key: DB id, email (netid - TODO)
    @GET @Path("eperson/{key}")
    public EPersonEntity findEPerson(@PathParam("key") String key) {
        EPersonEntity epEntity = null;
        try (org.dspace.core.Context context = new org.dspace.core.Context()) {
            Integer intKey = Ints.tryParse(key);
            if (intKey != null) {
                epEntity =  authzDao.getEPerson(context, intKey);
            } else {
                epEntity =  authzDao.findEPerson(context, key);
            }
        } catch (IllegalArgumentException iaE) {
            throw new WebApplicationException(Response.Status.NOT_FOUND);
        } catch (AuthorizeException authE) {
            throw new WebApplicationException(Response.Status.UNAUTHORIZED);
        } catch (SQLException sqlE) {
            throw new WebApplicationException(Response.Status.INTERNAL_SERVER_ERROR);
        }
        inject(epEntity);
        return epEntity;
    }

    // return a reference list or EPersons from query
    @GET @Path("epeople/{query}")
    public List<EntityRef> findEPeople(@PathParam("query") String query) {
        List<EntityRef> refList = new ArrayList<>();
        try (org.dspace.core.Context context = new org.dspace.core.Context()) {
            refList = authzDao.findEPeople(context, query);
        } catch (SQLException sqlE) {
            throw new WebApplicationException(Response.Status.INTERNAL_SERVER_ERROR);
        }
        // inject URIs for each reference
        for (EntityRef ref : refList) {
            inject(ref);
        }
        return refList;
    }

    // return an link entity list of groups eperson is a member of
    @GET @Path("eperson/{id}/groups")
    public List<LinkEntity> getEPersonGroups(@PathParam("id") int id) {
        return getLinkList(id, "eperson", "group");
    }

    // create a new EPerson
    @POST @Path("epeople")
    public EPersonEntity createEPerson(EPersonEntity epEntity) {
        EPersonEntity newEntity = null;
        try (org.dspace.core.Context context = new org.dspace.core.Context()) {
            newEntity =  authzDao.createEPerson(context, epEntity);
        } catch (AuthorizeException authE) {
            throw new WebApplicationException(Response.Status.UNAUTHORIZED);
        } catch (SQLException sqlE) {
            throw new WebApplicationException(Response.Status.INTERNAL_SERVER_ERROR);
        }
        inject(newEntity);
        return newEntity;
    }

    // update an EPerson
    @PUT @Path("eperson/{id}")
    public EPersonEntity updateEPerson(@PathParam("id") int id, EPersonEntity epEntity) {
        EPersonEntity updEntity = null;
        try (org.dspace.core.Context context = new org.dspace.core.Context()) {
            updEntity =  authzDao.updateEPerson(context, id, epEntity);
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

    // remove an EPerson
    @DELETE @Path("eperson/{id}")
    public EPersonEntity removeEPerson(@PathParam("id") int id) {
        EPersonEntity entity = null;
        try (org.dspace.core.Context context = new org.dspace.core.Context()) {
            entity = authzDao.removeEPerson(context, id);
            context.complete();
        } catch (AuthorizeException authE) {
            throw new WebApplicationException(Response.Status.UNAUTHORIZED);
        } catch (IllegalArgumentException iaE) {
            throw new WebApplicationException(Response.Status.NOT_FOUND);
        } catch (SQLException sqlE) {
            throw new WebApplicationException(Response.Status.INTERNAL_SERVER_ERROR);
        }
        inject(entity);
        return entity;
    }
    
    @GET @Path("groups")
    public List<EntityRef> getGroups() {
        List<EntityRef> refList = null;
        try (org.dspace.core.Context context = new org.dspace.core.Context()) {
            refList = authzDao.getGroups(context);
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
    @GET @Path("group/{id}")
    public GroupEntity getGroup(@PathParam("id") int id) {
        GroupEntity entity = null;
        try (org.dspace.core.Context context = new org.dspace.core.Context()) {
            entity = authzDao.getGroup(context, id);
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
    @POST @Path("groups")
    public GroupEntity createGroup(GroupEntity entity) {
        GroupEntity newEntity = null;
         try {
            newEntity = authzDao.createGroup(entity);
        } catch (AuthorizeException authE) {
            throw new WebApplicationException(Response.Status.UNAUTHORIZED);
        } catch (SQLException sqlE) {
            throw new WebApplicationException(Response.Status.INTERNAL_SERVER_ERROR);
        }
        // Inject URIs into this entity
        inject(newEntity);
        return newEntity;
    }

    // update a group
    @PUT @Path("group/{id}")
    public GroupEntity updateGroup(@PathParam("id") int id, GroupEntity gpEntity) {
        GroupEntity updEntity = null;
        try (org.dspace.core.Context context = new org.dspace.core.Context()) {
            updEntity =  authzDao.updateGroup(context, id, gpEntity);
        } catch (AuthorizeException authE) {
            throw new WebApplicationException(Response.Status.UNAUTHORIZED);
        } catch (SQLException sqlE) {
            throw new WebApplicationException(Response.Status.INTERNAL_SERVER_ERROR);
        }
        inject(updEntity);
        return updEntity;
    }

    // get a group membership link
    @GET @Path("group/{gid}/{mtype}/{mid}")
    public LinkEntity getMemberLink(@PathParam("gid") int gid, @PathParam("mtype") String mtype, @PathParam("mid") int mid) {
        LinkEntity linkEntity = null;
        try (org.dspace.core.Context context = new org.dspace.core.Context()) {
            linkEntity =  authzDao.getMemberLink(context, gid, mtype, mid);
        } catch (AuthorizeException authE) {
            throw new WebApplicationException(Response.Status.UNAUTHORIZED);
        } catch (IllegalArgumentException iaE) {
            throw new WebApplicationException(Response.Status.NOT_FOUND);
        } catch (SQLException sqlE) {
            throw new WebApplicationException(Response.Status.INTERNAL_SERVER_ERROR);
        }
        inject(linkEntity);
        return linkEntity;
    }

    // add a member to a group
    @PUT @Path("group/{gid}/{mtype}/{mid}")
    public LinkEntity addGroupMember(@PathParam("gid") int gid, @PathParam("mtype") String mtype, @PathParam("mid") int mid) {
        LinkEntity entity = null;
        try (org.dspace.core.Context context = new org.dspace.core.Context()) {
            entity =  authzDao.addGroupMember(context, gid, mtype, mid);
        } catch (AuthorizeException authE) {
            throw new WebApplicationException(Response.Status.UNAUTHORIZED);
        } catch (IllegalArgumentException iaE) {
            throw new WebApplicationException(Response.Status.NOT_FOUND);
        } catch (SQLException sqlE) {
            throw new WebApplicationException(Response.Status.INTERNAL_SERVER_ERROR);
        }
        inject(entity);
        return entity;
    }

    // remove a member from a group
    @DELETE @Path("group/{gid}/{mtype}/{mid}")
    public LinkEntity removeGroupMember(@PathParam("gid") int gid, @PathParam("mtype") String mtype, @PathParam("mid") int mid) {
        LinkEntity entity = null;
        try (org.dspace.core.Context context = new org.dspace.core.Context()) {
            entity = authzDao.removeGroupMember(context, gid, mtype, mid);
            context.complete();
        } catch (AuthorizeException authE) {
            throw new WebApplicationException(Response.Status.UNAUTHORIZED);
        } catch (IllegalArgumentException iaE) {
            throw new WebApplicationException(Response.Status.NOT_FOUND);
        } catch (SQLException sqlE) {
            throw new WebApplicationException(Response.Status.INTERNAL_SERVER_ERROR);
        }
        inject(entity);
        return entity;
    }

    // remove a group
    @DELETE @Path("group/{id}")
    public GroupEntity removeGroup(@PathParam("id") int id) {
        GroupEntity entity = null;
        try (org.dspace.core.Context context = new org.dspace.core.Context()) {
            entity = authzDao.removeGroup(context, id);
            context.complete();
        } catch (AuthorizeException authE) {
            throw new WebApplicationException(Response.Status.UNAUTHORIZED);
        } catch (IllegalArgumentException iaE) {
            throw new WebApplicationException(Response.Status.NOT_FOUND);
        } catch (SQLException sqlE) {
            throw new WebApplicationException(Response.Status.INTERNAL_SERVER_ERROR);
        }
        inject(entity);
        return entity;
    }

    // return an entity-list of group members and groups
    @GET @Path("group/{id}/{subres}")
    public List<LinkEntity> getGroupMembers(@PathParam("id") int id, @PathParam("subres") String subres) {
        return getLinkList(id, "group", subres);
    }

    // look up a policy by ID
    @GET @Path("policy/{id}")
    public PolicyEntity getPolicy(@PathParam("id") int id) {
        PolicyEntity entity = null;
        try (org.dspace.core.Context context = new org.dspace.core.Context()) {
            entity =  authzDao.getPolicy(context, id);
        } catch (IllegalArgumentException iaE) {
            throw new WebApplicationException(Response.Status.NOT_FOUND);
        } catch (SQLException sqlE) {
            throw new WebApplicationException(Response.Status.INTERNAL_SERVER_ERROR);
        }
         // Inject URIs into this entity
        inject(entity);
        return entity;
    }

    // create a new policy
    @POST @Path("policies")
    public PolicyEntity createPolicy(PolicyEntity entity) {
        PolicyEntity newEntity = null;
         try {
            newEntity = authzDao.createPolicy(entity);
        } catch (AuthorizeException authE) {
            throw new WebApplicationException(Response.Status.UNAUTHORIZED);
        } catch (SQLException sqlE) {
            throw new WebApplicationException(Response.Status.INTERNAL_SERVER_ERROR);
        }
        // Inject URIs into this entity
        inject(newEntity);
        return newEntity;
    }

    // update a policy
    @PUT @Path("policy/{id}")
    public PolicyEntity updatePolicy(@PathParam("id") int id, PolicyEntity plEntity) {
        PolicyEntity updEntity = null;
        try (org.dspace.core.Context context = new org.dspace.core.Context()) {
            updEntity =  authzDao.updatePolicy(context, id, plEntity);
        } catch (AuthorizeException authE) {
            throw new WebApplicationException(Response.Status.UNAUTHORIZED);
        } catch (SQLException sqlE) {
            throw new WebApplicationException(Response.Status.INTERNAL_SERVER_ERROR);
        }
        inject(updEntity);
        return updEntity;
    }

    // remove a policy
    @DELETE @Path("policy/{id}")
    public PolicyEntity removePolicy(@PathParam("id") int id) {
        PolicyEntity entity = null;
        try (org.dspace.core.Context context = new org.dspace.core.Context()) {
            entity = authzDao.removePolicy(context, id);
            context.complete();
        } catch (AuthorizeException authE) {
            throw new WebApplicationException(Response.Status.UNAUTHORIZED);
        } catch (IllegalArgumentException iaE) {
            throw new WebApplicationException(Response.Status.NOT_FOUND);
        } catch (SQLException sqlE) {
            throw new WebApplicationException(Response.Status.INTERNAL_SERVER_ERROR);
        }
        inject(entity);
        return entity;
    }

    // get a resource's policies
    @GET @Path("{prefix}/{id}/policies")
    public List<EntityRef> getResourcePolicies(@PathParam("prefix") String prefix, @PathParam("id") String id) {
        List<EntityRef> refList = null;
        try {
            refList = authzDao.getPolicyReferences(prefix, id);
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

    private List<LinkEntity> getLinkList(int id, String refType, String targType) {
        List<LinkEntity> linkList = null;
        try {
            linkList = authzDao.getLinks(id, refType, targType);
        } catch (IllegalArgumentException iaE) {
            throw new WebApplicationException(Response.Status.NOT_FOUND);
        } catch (SQLException sqlE) {
            throw new WebApplicationException(Response.Status.INTERNAL_SERVER_ERROR);
        }
        if (linkList.size() == 0) {
            throw new WebApplicationException(Response.Status.NO_CONTENT);
        }
        // inject URIs for each reference
        for (LinkEntity link : linkList) {
            inject(link);
        }
        return linkList;
    }

    private void inject(Injectable injectable) {
        Map<String, String> sites = injectable.getUriInjections();
        for (String key : sites.keySet()) {
            UriBuilder ub = uriInfo.getBaseUriBuilder();
            ub = ub.path("authz");
            for (String part: sites.get(key).split(":")) {
                ub = ub.path(part);
            }
            injectable.injectUri(key, ub.build());
        }
    }
}
