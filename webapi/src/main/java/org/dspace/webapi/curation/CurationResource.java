/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.webapi.curation;

import java.io.IOException;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.POST;
import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.ResponseBuilder;
import javax.ws.rs.core.SecurityContext;

import static javax.ws.rs.core.MediaType.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.dspace.authorize.AuthorizeException;
import org.dspace.curate.Curator;
import org.dspace.curate.TaskResolver;
import org.dspace.curate.ObjectSelector;
import org.dspace.eperson.EPerson;

import org.dspace.webapi.curation.domain.Action;
import org.dspace.webapi.curation.domain.Curation;
import org.dspace.webapi.curation.domain.CurationOrder;
import org.dspace.webapi.curation.domain.GroupRef;
import org.dspace.webapi.curation.domain.Selector;
import org.dspace.webapi.curation.domain.SelectorGroup;
import org.dspace.webapi.curation.domain.Task;
import org.dspace.webapi.curation.domain.TaskGroup;

/**
 * CurationResource is a JAX-RS root resource providing a REST API for curation.
 * Through the API, one can enumerate tasks and task groups, (named) selectors
 * and selector groups, and invoke or queue Dspace Objects against them.
 * 
 * @author richardrodgers
 */

@Path("curation")
@Produces({APPLICATION_XML, APPLICATION_JSON})
@Consumes({APPLICATION_XML, APPLICATION_JSON})
public class CurationResource {

    private static Logger log = LoggerFactory.getLogger(CurationResource.class);

    private final CurationDao curationDao = new CurationDao();
    private final Map<String, String> statusMap = curationDao.getStatusMap();

    @GET @Path("taskgroups")
    public List<GroupRef> getTaskGroups() {
        try (org.dspace.core.Context context = new org.dspace.core.Context()) {
            return curationDao.getTaskGroups(context);
        } catch (SQLException sqlE) {
            throw new WebApplicationException(Response.Status.INTERNAL_SERVER_ERROR);
        }
    }

    @GET @Path("taskgroup/{groupId}")
    public TaskGroup getTaskGroup(@PathParam("groupId") String groupId) {
        try (org.dspace.core.Context context = new org.dspace.core.Context()) {
            return curationDao.getTaskGroup(context, groupId);
        } catch (SQLException sqlE) {
            throw new WebApplicationException(Response.Status.INTERNAL_SERVER_ERROR);
        }
    }

    @GET @Path("selectorgroups")
    public List<GroupRef> getSelectorGroups() {
        try (org.dspace.core.Context context = new org.dspace.core.Context()) {
            return curationDao.getSelectorGroups(context);
        } catch (SQLException sqlE) {
            throw new WebApplicationException(Response.Status.INTERNAL_SERVER_ERROR);
        }
    }

    @GET @Path("selectorgroup/{groupId}")
    public SelectorGroup getSelectorGroup(@PathParam("groupId") String groupId) {
        try (org.dspace.core.Context context = new org.dspace.core.Context()) {
            return curationDao.getSelectorGroup(context, groupId);
        } catch (SQLException sqlE) {
            throw new WebApplicationException(Response.Status.INTERNAL_SERVER_ERROR);
        }
    }

    @POST @Path("content/{prefix}/{id}")
    public Curation curateDso(@Context SecurityContext sec, @PathParam("prefix") String prefix,
                              @PathParam("id") String id, CurationOrder order) {
        try (org.dspace.core.Context context = new org.dspace.core.Context()) {
            String dsoId = prefix + "/" + id;
            Curation curation = initCuration(context, dsoId, "dso", sec, order);
            return curate(curation);
        } catch (SQLException sqlE) {
            throw new WebApplicationException(Response.Status.INTERNAL_SERVER_ERROR);
        }
    }

    @POST @Path("set/{selector}")
    public Curation curateSelector(@Context SecurityContext sec, @PathParam("selector") String selector,
                                   CurationOrder order) {
        try (org.dspace.core.Context context = new org.dspace.core.Context()) {
            Curation curation = initCuration(context, selector, "set", sec, order);
            return curate(curation);
        } catch (SQLException sqlE) {
            throw new WebApplicationException(Response.Status.INTERNAL_SERVER_ERROR);
        }
    }

    private Curation curate(Curation curation) {
        org.dspace.core.Context ctx = null;
        EPerson invoker = null;
        Curator curator = null;
        try {
            ctx = new org.dspace.core.Context();
            invoker = EPerson.findByEmail(ctx, curation.getInvoker());
            if (invoker != null) {
                ctx.setCurrentUser(invoker);
                curator = initCurator(ctx, curation);
                String queue = curation.getQueue();
                if ("dso".equals(curation.getIdType())) {
                    if (queue == null) {
                        curator.curate(ctx, curation.getId());
                    } else {
                        curator.queue(ctx, curation.getId(), queue);
                    }
                } else if ("set".equals(curation.getIdType())) {
                    ObjectSelector selector = TaskResolver.resolveSelector(ctx, curation.getId());
                    if (selector != null) {
                        selector.setContext(ctx);
                        if (queue == null) {
                            curator.curate(selector);
                        } else {
                            curator.queue(selector, queue);
                        }
                    } else {
                        throw new WebApplicationException(Response.Status.NOT_FOUND);
                    }
                }
                updateCuration(curation, curator);
            } else {
                throw new WebApplicationException(Response.Status.UNAUTHORIZED);
            }
        } catch (AuthorizeException | IOException | SQLException e) {
            log.error("Error", e);
            throw new WebApplicationException();
        } finally {
            try {
                if (curator != null) {
                    curator.complete();
                }
                if (ctx != null) {
                    ctx.complete();
                }
            } catch (IOException | SQLException e) {
                log.error("Exception completing curator", e);
            }
        }
        return curation;
    }

    private Curation initCuration(org.dspace.core.Context context, String id, String idType, 
                                  SecurityContext sec, CurationOrder order) throws SQLException {
        Curation curation = new Curation();
        curation.setId(id);
        curation.setIdType(idType);
        //curation.setInvoker(sec.getUserPrincipal().toString());
        curation.setInvoker("rrodgers@mit.edu");
        Task task = curationDao.getTask(context, order.getTaskName());
        if (task != null) {
            Action action = new Action();
            action.setTaskName(task.getName());
            action.setTaskDescription(task.getDescription());
            curation.setAction(action);
        } else {
            log.warn("Unknown task: '" + task + "' - skipped");
            // no point in continuing - no tasks to work with
            throw new WebApplicationException(Response.Status.BAD_REQUEST);
        }
        String invoked = order.getInvoked();
        String queue = order.getQueue();
        if (queue != null) {
            curation.setQueue(queue);
            // override any invoked instructions - queued operations always batch
            invoked = "batch";
        }
        String scope = order.getTransactionScope();
        if (scope != null) {
            Curator.TxScope txScope = Curator.TxScope.valueOf(scope.toUpperCase());
            if (txScope != null) {
                curation.setTransactionScope(scope);
            } else {
                log.warn("Invalid transaction scope: '" + scope + "' - ignored");
            }
        }
        if (invoked != null) {
            curation.setInvoked(invoked);
        }
        if (order.getCacheLimit() > 0) {
            curation.setCacheLimit(order.getCacheLimit());
        }
        if (order.getJournalFilter() != null) {
            curation.setJournalFilter(order.getJournalFilter());
        }
        return curation;
    }

    private void updateCuration(Curation curation, Curator curator) {
        Action action = curation.getAction();
        int code = curator.getStatus(action.getTaskName());
        action.setStatusCode(code);
        String key = statusMap.containsKey(String.valueOf(code)) ? String.valueOf(code) : "other";
        action.setStatusLabel(statusMap.get(key));
        action.setResult(curator.getResult(action.getTaskName()));
    }

    private Curator initCurator(org.dspace.core.Context context, Curation curation) {
        Curator curator = new Curator();
        String scope = curation.getTransactionScope();
        if (scope != null) {
            curator.setTransactionScope(Curator.TxScope.valueOf(scope.toUpperCase()));
        }
        int cacheLimit = curation.getCacheLimit();
        if (cacheLimit > 0) {
            curator.setCacheLimit(cacheLimit);
        }
        String filter = curation.getJournalFilter();
        if (filter != null) {
            curator.setJournalFilter(filter);
        }
        String invoked = curation.getInvoked();
        if (invoked != null) {
            curator.setInvoked(Curator.Invoked.valueOf(invoked.toUpperCase()));
        }
        Action action = curation.getAction();
        curator.addTask(context, action.getTaskName());
        return curator;
    }
}
