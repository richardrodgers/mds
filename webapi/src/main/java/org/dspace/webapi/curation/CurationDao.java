/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.webapi.curation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.dspace.core.ConfigurationManager;
import org.dspace.webapi.curation.domain.Selector;
import org.dspace.webapi.curation.domain.SelectorGroup;
import org.dspace.webapi.curation.domain.Task;
import org.dspace.webapi.curation.domain.TaskGroup;

/**
 * CurationDao provides domain objects to the service.
 * Currently, their definitions reside in DSpace configuration
 * files, but this is not optimal.
 *  
 * @author richardrodgers
 *
 */

public class CurationDao {

    public List<Task> getTasks() {
        List<Task> taskList = new ArrayList<Task>();
        String taskProp = ConfigurationManager.getProperty("curate", "ui.tasknames");
        if (taskProp != null) {
            for (String desc : taskProp.split(",")) {
                String[] parts = desc.split("=");
                Task task = new Task();
                task.setName(parts[0].trim());
                task.setDescription(parts[1].trim());
                taskList.add(task);
            }
        }
        return taskList;
    }

    public Map<String, Task> getTaskMap() {
        Map<String, Task> taskMap = new HashMap<>();
        String taskProp = ConfigurationManager.getProperty("curate", "ui.tasknames");
        if (taskProp != null) {
            for (String desc : taskProp.split(",")) {
                String[] parts = desc.split("=");
                Task task = new Task();
                task.setName(parts[0].trim());
                task.setDescription(parts[1].trim());
                taskMap.put(task.getName(), task);
            }
        }
        return taskMap;
    }

    public List<TaskGroup> getTaskGroups() {
        List<TaskGroup> groupList = new ArrayList<>();
        String tgProp = ConfigurationManager.getProperty("curate", "ui.taskgroups");
        if (tgProp != null) {
            List<Task> allTasks = getTasks();
            List<Task> members = new ArrayList<Task>();
            for (String desc : tgProp.split(",")) {
                String[] parts = desc.split("=");
                TaskGroup group = new TaskGroup();
                group.setName(parts[0].trim());
                group.setDescription(parts[1].trim());
                String memProp = ConfigurationManager.getProperty("curate", "ui.taskgroup." + group.getName());
                for (String mem : memProp.split(",")) {
                    Task memTask = containsTask(allTasks, mem.trim());
                    if (memTask != null) {
                        members.add(memTask);
                    }
                }
                group.setMembers(members);
                groupList.add(group);
            }
        }
        return groupList;
    }

    public Map<String, String> getStatusMap() {
        Map<String, String> statusMap = new HashMap<>();
        String labels = ConfigurationManager.getProperty("curate", "ui.statusmessges");
        if (labels != null) {
            for (String code : labels.split(",")) {
                String[] parts = code.split("="); 
                statusMap.put(parts[0].trim(), parts[1].trim());
            }
        }
        return statusMap;
    }

    private Task containsTask(List<Task> taskList, String taskName) {
        for (Task task : taskList) {
            if (task.getName().equals(taskName)) {
                return task;
            }
        }
        return null;
    }

    public List<Selector> getSelectors() {
        // Currently read from curate.cfg - belong elsewhere
        String selProp = ConfigurationManager.getProperty("curate", "ui.selectors");
        List<Selector> selList = new ArrayList<>();
        if (selProp != null) {
            for (String desc : selProp.split(",")) {
                String[] parts = desc.split("=");
                Selector selector = new Selector();
                selector.setName(parts[0].trim());
                selector.setDescription(parts[1].trim());
                selList.add(selector);
            }
        }
        return selList;
    }

    public List<SelectorGroup> getSelectorGroups() {
        // Currently read from curate.cfg - belong elsewhere
        String sgProp = ConfigurationManager.getProperty("curate", "ui.selectorgroups");
        List<SelectorGroup> groupList = new ArrayList<SelectorGroup>();
        if (sgProp != null) {
            List<Selector> allSelectors = getSelectors();
            List<Selector> members = new ArrayList<Selector>();
            for (String desc : sgProp.split(",")) {
                String[] parts = desc.split("=");
                SelectorGroup group = new SelectorGroup();
                group.setName(parts[0].trim());
                group.setDescription(parts[1].trim());
                String memProp = ConfigurationManager.getProperty("curate", "ui.selectorgroup." + group.getName());
                for (String mem : memProp.split(",")) {
                    Selector memSelector = containsSelector(allSelectors, mem.trim());
                    if (memSelector != null) {
                        members.add(memSelector);
                    }
                }
                group.setMembers(members);
                groupList.add(group);
            }
        }
        return groupList;
    }

    private Selector containsSelector(List<Selector> selectorList, String selectorName) {
        for (Selector selector : selectorList) {
            if (selector.getName().equals(selectorName)) {
                return selector;
            }
        }
        return null;
    }
}
