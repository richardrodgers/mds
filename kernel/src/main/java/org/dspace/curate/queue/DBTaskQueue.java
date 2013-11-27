/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */

package org.dspace.curate.queue;

import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.skife.jdbi.v2.Handle;
import org.skife.jdbi.v2.StatementContext;
import org.skife.jdbi.v2.tweak.ResultSetMapper;
import org.skife.jdbi.v2.util.StringMapper;

import org.dspace.core.Context;

/**
 * DBTaskQueue provides a TaskQueue implementation based on
 * the relational database for queue management. Supercedes
 * FileTaskQueue, which is now deprecated.
 *
 * @author richardrodgers
 */
public class DBTaskQueue implements TaskQueue {

    private static Logger log = LoggerFactory.getLogger(DBTaskQueue.class);   

    public DBTaskQueue() {}
    
    @Override
    public List<String> queueNames(Context context) throws SQLException {
        return context.getHandle().createQuery("SELECT DISTINCT queue_name FROM task_queue").
               map(StringMapper.FIRST).list();
    }
    
    @Override
    public void enqueue(Context context, String queueName, TaskQueueEntry entry) throws IOException, SQLException {
        context.getHandle().execute("INSERT into task_queue (queue_name, task_list, eperson_id, enqueue_time, target, jrn_filter) values (?, ?, ?, ?, ?, ?)",
            queueName, entry.getTaskList(), entry.getEpersonId(), entry.getSubmitTime(), entry.getTarget(), entry.getJournalFilter());
    }

    @Override
    public synchronized void enqueue(Context context, String queueName, Set<TaskQueueEntry> entrySet) throws IOException, SQLException {
        Iterator<TaskQueueEntry> iter = entrySet.iterator();
        while (iter.hasNext()) {
            enqueue(context, queueName, iter.next());
        }
    }

    @Override
    public Set<TaskQueueEntry> dequeue(Context context, String queueName, long ticket) throws IOException, SQLException {
        // first write ticket into all entries on queue, then return them
        context.getHandle().execute("UPDATE task_queue SET ticket = :tkt WHERE queue_name = :qname", ticket, queueName);
        List<TaskQueueEntry> tqList = context.getHandle().createQuery("SELECT * FROM task_queue WHERE queue_name = :qname").
                                      bind(":qname", queueName).map(new QueueEntryMapper()).list();
        return new HashSet<TaskQueueEntry>(tqList);
    }

    @Override
    public Set<TaskQueueEntry> peek(Context context, String queueName) throws SQLException {
        List<TaskQueueEntry> tqList = context.getHandle().createQuery("SELECT * FROM task_queue WHERE queue_name = :qname").
                                      bind(":qname", queueName).map(new QueueEntryMapper()).list();
        return new HashSet<TaskQueueEntry>(tqList);
    }
    
    @Override
    public void release(Context context, String queueName, long ticket, boolean remove) throws SQLException {
        if (remove) {
            context.getHandle().execute("DELETE FROM task_queue WHERE queue_name = :qname AND ticket = :tkt", queueName, ticket);
        } else {
            // simply purge ticket from entries
            context.getHandle().execute("UPDATE task_queue SET ticket = NULL WHERE queue_name = :qname AND ticket = :tkt", queueName, ticket);
        }
    }

    private class QueueEntryMapper implements ResultSetMapper<TaskQueueEntry> {
        public TaskQueueEntry map(int index, ResultSet r, StatementContext ctx) throws SQLException {
            return new TaskQueueEntry(r.getString("eperson_id"), r.getLong("enqueue_time"), r.getString("task_list"), r.getString("target"), r.getString("jrn_filter"));
        }
    }
}
