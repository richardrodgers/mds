/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.curate.queue;

import java.util.Arrays;
import java.util.List;

/**
 * TaskQueueEntry defines the record or entry in the named task queues.
 * Regular immutable value object class.
 * 
 * @author richardrodgers
 */
public final class TaskQueueEntry
{
    private final String epersonId;
    private final String submitTime;
    private final String tasks;
    private final String target;
    private final String jrnFilter;
    
    /**
     * TaskQueueEntry constructor with enumerated field values.
     * 
     * @param epersonId
     * @param submitTime
     * @param taskNames
     * @param target
     * @param jrnFilter
     */
    public TaskQueueEntry(String epersonId, long submitTime,
                          List<String> taskNames, String target, String jrnFilter) {
        this.epersonId = epersonId;
        this.submitTime = Long.toString(submitTime);
        StringBuilder sb = new StringBuilder();
        for (String tName : taskNames) {
            sb.append(tName).append(",");
        }
        this.tasks = sb.substring(0, sb.length() - 1);
        this.target = target;
        this.jrnFilter = jrnFilter;
    }

    /**
     * TaskQueueEntry constructor with enumerated field values.
     * 
     * @param epersonId
     * @param submitTime
     * @param taskNames
     * @param target
     * @param jrnFilter
     */
    public TaskQueueEntry(String epersonId, long submitTime,
                          String taskNames, String target, String jrnFilter) {
        this.epersonId = epersonId;
        this.submitTime = Long.toString(submitTime);
        tasks = taskNames;
        this.target = target;
        this.jrnFilter = jrnFilter;
    }
    
    /**
     * Constructor with a pipe-separated list of field values.
     * 
     * @param entry
     *        list of field values separated by '|'s
     */
    public TaskQueueEntry(String entry) {
        String[] tokens = entry.split("\\|");
        epersonId = tokens[0];
        submitTime = tokens[1];
        tasks = tokens[2];
        target = tokens[3];
        if (tokens.length > 4) {
            jrnFilter = tokens[4];
        } else {
            jrnFilter = "n";
        }
    }
    
    /**
     * Returns the epersonId (email) of the agent who enqueued this task entry.
     *  
     * @return epersonId
     *         name of EPerson (email) or 'unknown' if none recorded.
     */
    public String getEpersonId() {
        return epersonId;
    }
    
    /**
     * Returns the timestamp of when this entry was enqueued.
     * 
     * @return time
     *         Submission timestamp
     */
    public long getSubmitTime() {
        return Long.valueOf(submitTime);
    }
    
    /**
     * Return the list of tasks associated with this entry.
     * 
     * @return tasks
     *         the list of logical task names
     */
    public List<String> getTaskNames() {
        return Arrays.asList(tasks.split(","));
    }
    
    /**
     * Return the list of tasks associated with this entry as a string
     * 
     * @return tasks
     *         the csv String of logical task names
     */
    public String getTaskList() {
        return tasks;
    }

    /**
     * Returns the curation target.
     * @return target
     *         usually a handle or workflow id, or the name of a selector
     */
    public String getTarget() {
        return target;
    }

    /**
     * Returns the journal filter.
     * @return filter
     *         "n" for none, "a" for all or set of status codes ("sfke")
     */
    public String getJournalFilter() {
        return target;
    }

    /**
     * Returns true if this entry has the same task list
     * and target as the passed entry. I.e. they can
     * differ only in submitter or time.
     *
     * @return true if same effect, else false
     */
     public boolean isoFunctional(TaskQueueEntry that) {
         return tasks.equals(that.tasks) && target.equals(that.target);
     }

    /**
     * Returns a string representation of the entry
     * @return string
     *         pipe-separated field values
     */
    @Override
    public String toString() {
        return epersonId + "|" + submitTime + "|" + tasks + "|" + target + "|" + jrnFilter;
    }
}
