/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.webapi.authz;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.skife.jdbi.v2.Handle;
import org.skife.jdbi.v2.StatementContext;
import org.skife.jdbi.v2.tweak.ResultSetMapper;
import org.skife.jdbi.v2.util.StringMapper;

import org.dspace.authorize.AuthorizeException;
import org.dspace.authorize.AuthorizeManager;
import org.dspace.authorize.ResourcePolicy;
import org.dspace.eperson.EPerson;
import org.dspace.eperson.EPersonDeletionException;
import org.dspace.eperson.Group;
import org.dspace.content.Bitstream;
import org.dspace.content.Bundle;
import org.dspace.content.DSpaceObject;
import org.dspace.content.Item;
import org.dspace.core.ConfigurationManager;
import org.dspace.core.Context;
import org.dspace.handle.HandleManager;

import org.dspace.webapi.EntityRef;
import org.dspace.webapi.authz.domain.EPersonEntity;
import org.dspace.webapi.authz.domain.GroupEntity;
import org.dspace.webapi.authz.domain.LinkEntity;
import org.dspace.webapi.authz.domain.PolicyEntity;

/**
 * AuthorizationDao provides authZ domain objects to the service.
 *  
 * @author richardrodgers
 */

public class AuthorizationDao {

    public EPersonEntity getEPerson(Context context, int id) throws SQLException {
        EPerson eperson = EPerson.find(context, id);
        if (eperson == null) {
            throw new IllegalArgumentException("No such EPerson: " + id);
        }
        return new EPersonEntity(eperson);
    }

    public EPersonEntity findEPerson(Context context, String key) throws AuthorizeException, SQLException {
        EPerson eperson = EPerson.findByEmail(context, key);
        if (eperson == null) {
            throw new IllegalArgumentException("No EPerson for key: " + key);
        }
        return new EPersonEntity(eperson);
    }

    public List<EntityRef> findEPeople(Context context, String query) throws SQLException {
        List<EPerson> people = EPerson.findAll(context, 3);
        List<EntityRef> refs = new ArrayList<>();
        for (EPerson person: people) {
            refs.add(new EntityRef(person.getEmail(), String.valueOf(person.getID()), "eperson"));
        }
        return refs;
    }

    public List<EntityRef> getEPeople(Context context) throws SQLException {
        List<EPerson> people = EPerson.findAll(context, 3);
        List<EntityRef> refs = new ArrayList<>();
        for (EPerson person: people) {
            refs.add(new EntityRef(person.getEmail(), String.valueOf(person.getID()), "eperson"));
        }
        return refs;
    }

    public EPersonEntity createEPerson(Context context, EPersonEntity epEntity) throws AuthorizeException, SQLException {
        EPerson ep = EPerson.create(context);
        epEntity.sync(ep);
        ep.update();
        return new EPersonEntity(ep);
    }

    public EPersonEntity updateEPerson(Context context, int id, EPersonEntity epEntity) throws AuthorizeException, SQLException {
        EPerson eperson = EPerson.find(context, id);
        if (eperson == null) {
            throw new IllegalArgumentException("No such EPerson: " + id);
        }
        epEntity.sync(eperson);
        eperson.update();
        return new EPersonEntity(eperson);
    }

    public void removeEPerson(Context context, int id) throws AuthorizeException, SQLException {
        EPerson eperson = EPerson.find(context, id);
        if (eperson != null) {
            try {
                eperson.delete();
                context.complete();
            } catch (EPersonDeletionException epdEx) {
                throw new AuthorizeException("wrong - change this");
            }
        }
    }

    public GroupEntity getGroup(Context context, int id) throws SQLException {
        Group group = Group.find(context, id);
        if (group == null) {
            throw new IllegalArgumentException("No such Group: " + id);
        }
        return new GroupEntity(group);
    }

    public List<EntityRef> getGroups(Context context) throws SQLException {
        List<Group> groups = Group.findAll(context, 3);
        List<EntityRef> refs = new ArrayList<>();
        for (Group group: groups) {
            refs.add(new EntityRef(group.getName(), String.valueOf(group.getID()), "group"));
        }
        return refs;
    }

    public GroupEntity createGroup(GroupEntity entity) throws AuthorizeException, SQLException {
        Context ctx = new Context();
        ctx.turnOffAuthorisationSystem();
        Group group = Group.create(ctx);
        entity.sync(group);
        group.update();
        ctx.complete();
        return new GroupEntity(group);
    }

    public GroupEntity updateGroup(Context context, int id, GroupEntity gpEntity) throws AuthorizeException, SQLException {
        Group group = Group.find(context, id);
        if (group == null) {
            throw new IllegalArgumentException("No such Group: " + id);
        }
        gpEntity.sync(group);
        group.update();
        return new GroupEntity(group);
    }

    public LinkEntity getMemberLink(Context context, int gid, String mtype, int mid) throws AuthorizeException, SQLException {
        LinkEntity link = null;
        Group group = Group.find(context, gid);
        if (group == null) {
            throw new IllegalArgumentException("No such Group: " + gid);
        }
        if ("eperson".equals(mtype)) {
            EPerson eperson = EPerson.find(context, mid);
            if (eperson == null) {
                throw new IllegalArgumentException("No such member EPerson: " + mid);
            }
            link = new LinkEntity(group, eperson);
        } else if ("group".equals(mtype)) {
            Group memgroup = Group.find(context, mid);
            if (memgroup == null) {
                throw new IllegalArgumentException("No such member Group: " + mid);
            }
            link = new LinkEntity(group, memgroup);
        } else {
            throw new IllegalArgumentException("No such member type: " + mtype);
        }
        return link;
    }

    public LinkEntity addGroupMember(Context context, int gid, String mtype, int mid) throws AuthorizeException, SQLException {
        Group group = Group.find(context, gid);
        LinkEntity link = null;
        if (group == null) {
            throw new IllegalArgumentException("No such Group: " + gid);
        }
        if ("eperson".equals(mtype)) {
            EPerson eperson = EPerson.find(context, mid);
            if (eperson == null) {
                throw new IllegalArgumentException("No such member EPerson: " + mid);
            }
            group.addMember(eperson);
            group.update();
            link = new LinkEntity(group, eperson);
        } else if ("group".equals(mtype)) {
            Group memgroup = Group.find(context, mid);
            if (memgroup == null) {
                throw new IllegalArgumentException("No such member Group: " + mid);
            }
            group.addMember(memgroup);
            group.update();
            link = new LinkEntity(group, memgroup);
        } else {
            throw new IllegalArgumentException("No such member type: " + mtype);
        }
        return link;
    }

    public void removeGroupMember(Context context, int gid, String mtype, int mid) throws AuthorizeException, SQLException {
        Group group = Group.find(context, gid);
        if (group != null) {
            if ("eperson".equals(mtype)) {
                EPerson eperson = EPerson.find(context, mid);
                if (eperson != null) {
                    group.removeMember(eperson);
                    group.update();
                    context.complete();
                }
            } else if ("group".equals(mtype)) {
                Group memgroup = Group.find(context, mid);
                if (memgroup != null) {
                    group.removeMember(memgroup);
                    group.update();
                    context.complete();
                }
            }
        }
    }

    public void removeGroup(Context context, int id) throws AuthorizeException, SQLException {
        Group group = Group.find(context, id);
        if (group != null) {
            group.delete();
            context.complete();
        }
    }

    public PolicyEntity getPolicy(Context context, int id) throws SQLException {
        ResourcePolicy policy = ResourcePolicy.find(context, id);
        if (policy == null) {
            throw new IllegalArgumentException("No such policy: " + id);
        }
        return new PolicyEntity(policy);
    }

    public PolicyEntity createPolicy(PolicyEntity entity) throws AuthorizeException, SQLException {
        Context ctx = new Context();
        ctx.turnOffAuthorisationSystem();
        ResourcePolicy policy = ResourcePolicy.create(ctx);
        entity.sync(policy);
        policy.update();
        ctx.complete();
        return new PolicyEntity(policy);
    }

    public PolicyEntity updatePolicy(Context context, int id, PolicyEntity plEntity) throws AuthorizeException, SQLException {
        ResourcePolicy policy = ResourcePolicy.find(context, id);
        if (policy == null) {
            throw new IllegalArgumentException("No such policy: " + id);
        }
        plEntity.sync(policy);
        policy.update();
        return new PolicyEntity(policy);
    }

    public void removePolicy(Context context, int id) throws AuthorizeException, SQLException {
        ResourcePolicy policy = ResourcePolicy.find(context, id);
        if (policy != null) {
            policy.delete();
            context.complete();
        }
    }

    public List<EntityRef> getPolicyReferences(String prefix, String id) throws SQLException {
        List<EntityRef> refList = new ArrayList<>();
        Context ctx = new Context();
        String[] parts = id.split("\\.");
        String lid = id;
        if (parts.length > 1) {
            lid = parts[0];
        } 
        DSpaceObject dso = HandleManager.resolveToObject(ctx, prefix + "/" + lid);
        if (dso == null) {
            ctx.abort();
            throw new IllegalArgumentException("No such entity: " + prefix + id);
        }
        // if there is a sequence id, descend to bitstream
        if (parts.length > 1) {
            dso = findBitstream(dso, parts[1]);
            if (dso == null) {
                ctx.abort();
                throw new IllegalArgumentException("No such entity: " + prefix + id);
            }          
        }
        for (ResourcePolicy rp : AuthorizeManager.getPolicies(ctx, dso)) {
            refList.add(new EntityRef(rp.getActionText(), String.valueOf(rp.getID()), "policy"));
        }
        ctx.complete();
        return refList;
    }

    private DSpaceObject findBitstream(DSpaceObject dso, String seqStr) throws SQLException {
        int seqInt = Integer.parseInt(seqStr);
        // clumsy way for now
        Item item = (Item)dso;
        for (Bundle bundle : item.getBundles()) {
            for (Bitstream bs: bundle.getBitstreams()) {
                if (bs.getSequenceID() == seqInt) {
                    return bs;
                }
            }
        }
        return null;
    }

    public List<LinkEntity> getLinks(int id, String sourceType, String targetType) throws SQLException {
        List<LinkEntity> linkList = new ArrayList<>();
        Context ctx = new Context();
        switch (sourceType) {
            case "eperson" : getMemberOfLinks(linkList, id, ctx); break;
            case "group" : getMemberLinks(linkList, id, ctx, targetType); break;
            default: break;
        }
        ctx.complete();
        return linkList;
    }

    private void getMemberOfLinks(List<LinkEntity> linkList, int id, Context ctx) throws SQLException {
        EPerson eperson = EPerson.find(ctx, id);
        if (eperson == null) {
            throw new IllegalArgumentException("No such EPerson: " + id);
        }
        for (int gid: Group.allMemberGroupIDs(ctx, eperson)) {
            linkList.add(new LinkEntity(gid, eperson.getID(), "eperson"));
        }
    }

    private void getMemberLinks(List<LinkEntity> linkList, int id, Context ctx, String targetType) throws SQLException {
        Group group = Group.find(ctx, id);
        if (group == null) {
            throw new IllegalArgumentException("No such Group: " + id);
        }
        if ("members".equals(targetType)) {
            Iterator<Integer> memIter = Group.allMemberIDs(ctx, group).iterator();
            while ( memIter.hasNext() ) {
                linkList.add(new LinkEntity(id, memIter.next(), "eperson"));
            }
        } else if ("groupmembers".equals(targetType)) {
            for (Group gr : group.getMemberGroups()) {
                linkList.add(new LinkEntity(group, gr));
            }
        }
    }
}
