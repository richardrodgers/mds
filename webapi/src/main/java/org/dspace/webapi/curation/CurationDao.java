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

    public List<Task> getTasks(Context context) {
        return context.getHandle().createQuery("SELECT name, description FROM ctask_data WHERE type = 'task'")
                                  .map(new TaskMapper()).list();
    }

    public Task getTask(Context context, String taskName) {
        return context.getHandle().createQuery("SELECT name, description FROM ctask_data WHERE name = :name")
                                  .bind("name", taskName).map(new TaskMapper()).first();
    }

    public List<TaskGroup> getTaskGroups(Context context) {
        List<TaskGroup> groupList = context.getHandle().createQuery("SELECT group_name, description FROM ctask_group WHERE type = 'task' AND api_access = TRUE")
                                                       .map(new TaskGroupMapper()).list();
        // For now, fill in members - should redo with entity list and separate call per group
        for (TaskGroup group : groupList) {
            // lookup memmbers
            List<Task> members = context.getHandle().createQuery("SELECT name, ctask_data.description FROM ctask_data, group2ctask, ctask_group WHERE ctask_group.group_name = :gname AND group2ctask.group_id = ctask_group.ctask_group_id AND ctask_data.ctask_id = group2ctask.ctask_id")
                                .bind("gname", group.getName()).map(new TaskMapper()).list();
            group.setMembers(members);
        }
        return groupList;
    }

    public List<Selector> getSelectors(Context context) {
        return context.getHandle().createQuery("SELECT name, description FROM ctask_data WHERE type = 'selector'")
                                  .map(new SelectorMapper()).list();
    }

    public List<SelectorGroup> getSelectorGroups(Context context) {
        List<SelectorGroup> groupList = context.getHandle().createQuery("SELECT group_name, description FROM ctask_group WHERE type = 'selector' AND api_access = TRUE")
                                                       .map(new SelectorGroupMapper()).list();
        // For now, fill in members - should redo with entity list and separate call per group
        for (SelectorGroup group : groupList) {
            // lookup memmbers
            List<Selector> members = context.getHandle().createQuery("SELECT name, ctask_data.description FROM ctask_data, group2ctask, ctask_group WHERE ctask_group.group_name = :gname AND group2ctask.group_id = ctask_group.ctask_group_id AND ctask_data.ctask_id = group2ctask.ctask_id")
                                .bind("gname", group.getName()).map(new SelectorMapper()).list();
            group.setMembers(members);
        }
        return groupList;
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
