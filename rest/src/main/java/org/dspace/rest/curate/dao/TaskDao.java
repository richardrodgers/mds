/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.rest.curate.dao;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.dspace.core.ConfigurationManager;
import org.dspace.rest.curate.domain.Task;
import org.dspace.rest.curate.domain.TaskGroup;

/**
 * TaskDao provides domain objects to the service.
 * Currently, their definitions reside in DSpace configuration
 * files, but this is not optimal.
 *  
 * @author richardrodgers
 *
 */

public class TaskDao {
	
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
		Map<String, Task> taskMap = new HashMap<String, Task>();
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
		List<TaskGroup> groupList = new ArrayList<TaskGroup>();
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
		Map<String, String> statusMap = new HashMap<String, String>();
		String labels = ConfigurationManager.getProperty("curate", "ui.statusmessges");
		if (labels != null)
		{
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
}
