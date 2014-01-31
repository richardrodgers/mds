/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.webapi.curation;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.skife.jdbi.v2.Handle;
import org.skife.jdbi.v2.StatementContext;
import org.skife.jdbi.v2.tweak.ResultSetMapper;
import org.skife.jdbi.v2.util.StringMapper;


import org.dspace.core.ConfigurationManager;
import org.dspace.core.Context;
import org.dspace.webapi.curation.domain.GroupRef;
import org.dspace.webapi.curation.domain.Selector;
import org.dspace.webapi.curation.domain.SelectorGroup;
import org.dspace.webapi.curation.domain.Task;
import org.dspace.webapi.curation.domain.TaskGroup;

/**
 * CurationDao provides domain objects to the service.
 *  
 * @author richardrodgers
 */

public class CurationDao {

    public Task getTask(Context context, String taskName) {
        return context.getHandle().createQuery("SELECT name, description FROM ctask_data WHERE type = 'task' AND name = :name")
                                  .bind("name", taskName).map(new TaskMapper()).first();
    }

    public List<GroupRef> getTaskGroups(Context context) {
        return context.getHandle().createQuery("SELECT group_name, description, type FROM ctask_group WHERE type = 'task' AND api_access = TRUE")
                                  .map(new GroupRefMapper()).list();
    }

    public TaskGroup getTaskGroup(Context context, String groupId) {
        TaskGroup group = context.getHandle().createQuery("SELECT group_name, description FROM ctask_group WHERE type = 'task' AND group_name = :gid")
                                             .bind("gid", groupId).map(new TaskGroupMapper()).first();
        if (group != null) {
            // lookup members
            List<Task> members = context.getHandle().createQuery("SELECT name, ctask_data.description FROM ctask_data, group2ctask, ctask_group WHERE ctask_group.group_name = :gname AND group2ctask.group_id = ctask_group.ctask_group_id AND ctask_data.ctask_id = group2ctask.ctask_id")
                                                    .bind("gname", group.getName()).map(new TaskMapper()).list();
            group.setMembers(members);
        }
        return group;
    }

    public List<GroupRef> getSelectorGroups(Context context) {
        return context.getHandle().createQuery("SELECT group_name, description, type FROM ctask_group WHERE type = 'selector' AND api_access = TRUE")
                                  .map(new GroupRefMapper()).list();
    }

    public SelectorGroup getSelectorGroup(Context context, String groupId) {
        SelectorGroup group = context.getHandle().createQuery("SELECT group_name, description, type FROM ctask_group WHERE type = 'selector' AND group_name = :gid")
                                                 .bind("gid", groupId).map(new SelectorGroupMapper()).first();
        if (group != null) {
            // lookup memmbers
            List<Selector> members = context.getHandle().createQuery("SELECT name, ctask_data.description FROM ctask_data, group2ctask, ctask_group WHERE ctask_group.group_name = :gname AND group2ctask.group_id = ctask_group.ctask_group_id AND ctask_data.ctask_id = group2ctask.ctask_id")
                                                        .bind("gname", group.getName()).map(new SelectorMapper()).list();
            group.setMembers(members);
        }
        return group;
    }

    public Map<String, String> getStatusMap() {
        Map<String, String> statusMap = new HashMap<>();
        String labels = ConfigurationManager.getProperty("curate", "ui.statusmessages");
        if (labels != null) {
            for (String code : labels.split(",")) {
                String[] parts = code.split("="); 
                statusMap.put(parts[0].trim(), parts[1].trim());
            }
        }
        return statusMap;
    }

    private Selector containsSelector(List<Selector> selectorList, String selectorName) {
        for (Selector selector : selectorList) {
            if (selector.getName().equals(selectorName)) {
                return selector;
            }
        }
        return null;
    }

    private class GroupRefMapper implements ResultSetMapper<GroupRef> {
        public GroupRef map(int index, ResultSet r, StatementContext ctx) throws SQLException {
            return new GroupRef(r.getString("group_name"), r.getString("description"), r.getString("type"));
        }
    }

    private class TaskMapper implements ResultSetMapper<Task> {
        public Task map(int index, ResultSet r, StatementContext ctx) throws SQLException {
            return new Task(r.getString("name"), r.getString("description"));
        }
    }

    private class TaskGroupMapper implements ResultSetMapper<TaskGroup> {
        public TaskGroup map(int index, ResultSet r, StatementContext ctx) throws SQLException {
            return new TaskGroup(r.getString("group_name"), r.getString("description"));
        }
    }

    private class SelectorMapper implements ResultSetMapper<Selector> {
        public Selector map(int index, ResultSet r, StatementContext ctx) throws SQLException {
            return new Selector(r.getString("name"), r.getString("description"));
        }
    }

    private class SelectorGroupMapper implements ResultSetMapper<SelectorGroup> {
        public SelectorGroup map(int index, ResultSet r, StatementContext ctx) throws SQLException {
            return new SelectorGroup(r.getString("group_name"), r.getString("description"));
        }
    }
}
