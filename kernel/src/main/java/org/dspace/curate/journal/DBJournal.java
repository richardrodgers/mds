/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.curate.journal;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.skife.jdbi.v2.Handle;
import org.skife.jdbi.v2.BeanMapper;

import org.dspace.authorize.AuthorizeException;
import org.dspace.core.Context;
import org.dspace.curate.CurationJournal;

/**
 * DBJournal is a CurationJournal to capture task execution
 * data to a database table.  Entries represent curation task 
 * executions and their outcomes.
 *
 * @author richardrodgers
 */

public class DBJournal implements CurationJournal {

    /**
     * Writes a single entry to the journal.
     * 
     * @param context
     *        the DSpace execution context
     * @param timestamp
     *        when the curation occurred
     * @param task
     *        the task name
     * @throws IOException
     */
    @Override
    public void write(Context context, long timestamp, String task, String objectId, int status, String result)
            throws AuthorizeException, IOException {
        String userId = context.getCurrentUser().getName();
        context.getHandle().execute("INSERT INTO cjournal (cjournal_id, curation_date, user_id, task, object_id, status, result) " +
                  "VALUES (nextval('cjournal_seq'), ?, ?, ?, ?, ?, ?)",
                  timestamp, userId, task, objectId, status, result);
    }

    public static void deleteTask(Context context, long timestamp, String task) throws AuthorizeException, IOException {
        context.getHandle().execute("DELETE FROM cjournal WHERE curation_date = ? AND task = ?", timestamp, task);
    }

    public static void deleteBefore(Context context, long timestamp) throws AuthorizeException, IOException {
        context.getHandle().execute("DELETE FROM cjournal WHERE curation_date < ?", timestamp);
    }

    public static List<JournalEntry> allEntries(Context context, long timestamp, String task) throws AuthorizeException, IOException {
        return context.getHandle().createQuery("SELECT * FROM cjournal WHERE curation_date = ? AND task = ?").
        bind(0, timestamp).bind(1, task).map(new BeanMapper<JournalEntry>(JournalEntry.class)).list();
    }

    public static List<JournalEntry> entriesWithStatus(Context context, long timestamp, String task, int status) throws AuthorizeException, IOException {
        return context.getHandle().createQuery("SELECT * FROM cjournal WHERE curation_date = ? AND task = ? AND status = ?").
        bind(0, timestamp).bind(1, task).bind(2, status).map(new BeanMapper<JournalEntry>(JournalEntry.class)).list();
    }

    public static TaskSummary taskSummary(Context context, long timestamp, String task) throws AuthorizeException, IOException {
        return context.getHandle().createQuery("SELECT COUNT(*) numObjects, SUM(case WHEN status = 0 then 1 else 0 end) numSuccess, SUM(case WHEN status = 1 then 1 else 0 end) numFail, SUM(case WHEN status = 2 then 1 else 0 end) numSkip, SUM(case WHEN status = -1 then 1 else 0 end) numError, FROM cjournal WHERE curation_date = ? AND task = ?").
        bind(0, timestamp).bind(1, task).map(new BeanMapper<TaskSummary>(TaskSummary.class)).first();
    }

    public static List<JournalEntry> curationsSince(Context context, long timestamp) throws AuthorizeException, IOException {
        return context.getHandle().createQuery("SELECT DISTINCT curation_date, user_id, task FROM cjournal WHERE curation_date > ? LIMIT 100").
        bind(0, timestamp).map(new BeanMapper<JournalEntry>(JournalEntry.class)).list();
    }

    public static List<TaskSummary> summariesSince(Context context, long timestamp) throws AuthorizeException, IOException {
        List<TaskSummary> summaries = new ArrayList<>();
        for (JournalEntry jEntry : curationsSince(context, timestamp)) {
            TaskSummary summ = taskSummary(context, jEntry.curationDate, jEntry.task);
            summ.setCurationDate(jEntry.curationDate);
            summ.setUserId(jEntry.userId);
            summ.setTask(jEntry.task);
            summaries.add(summ);
        }
        return summaries;
    }

    class TaskSummary {
        public long curationDate;
        public String userId;
        public String task;
        public int numObjects;
        public int numSuccess;
        public int numFail;
        public int numSkip;
        public int numError;

        public TaskSummary(long curationDate, String userId, String task, int numObjects, int numSuccess, int numFail, int numSkip, int numError) {
            this.curationDate = curationDate;
            this.userId = userId;
            this.task = task;
            this.numObjects = numObjects;
            this.numSuccess = numSuccess;
            this.numFail = numFail;
            this.numSkip = numSkip;
            this.numError = numError;
        }

        // Bean methods for mapper
        public void setCurationDate(long date) { this.curationDate = date; }
        public void setUserId(String userId) { this.userId = userId; }
        public void setTask(String task) { this.task = task; }
        public void setNumObjects(int numObjects) { this.numObjects = numObjects; }
        public void setNumSuccess(int numSuccess) { this.numSuccess = numSuccess; }
        public void setNumFail(int numFail) { this.numFail = numFail; }
        public void setNumSkip(int numSkip) { this.numSkip = numSkip; }
        public void setNumError(int numError) { this.numError = numError; }
    }

    class JournalEntry {
        public long curationDate;
        public String userId;
        public String task;
        public String objectId;
        public int status;
        public String result;

        public JournalEntry (long curationDate, String userId, String task, String objectid, int status, String result) {
            this.curationDate = curationDate;
            this.userId = userId;
            this.task = task;
            this.objectId = objectId;
            this.status = status;
            this.result = result;
        }

        // Bean methods for mapper
        public void setCurationDate(long date) { this.curationDate = date; }
        public void setUserId(String userId) { this.userId = userId; }
        public void setTask(String task) { this.task = task; }
        public void setObjectId(String objectId) { this.objectId = objectId; }
        public void setStatus(int status) { this.status = status; }
        public void setResult(String result) { this.result = result; }
    }
}
