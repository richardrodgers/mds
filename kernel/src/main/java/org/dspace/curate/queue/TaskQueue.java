/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.curate.queue;

import java.io.IOException;
import java.sql.SQLException;
import java.util.List;
import java.util.Set;

import org.dspace.core.Context;

/**
 * TaskQueue objects manage access to named queues of task entries.
 * Entries represent curation task requests that have been deferred.
 * The queue supports concurrent non-blocking writers, but controls
 * read access to a single reader possessing a ticket (first come,
 * first serve). After the read, the queue remains locked until
 * released by the reader, after which it is typically purged.
 * Examining the state of the queue without acquiring a read lock
 * is effected with a 'peek'.
 *
 * @author richardrodgers
 */
public interface TaskQueue {
    
    /**
     * Returns list of queue names.
     * 
     * @param context
     *        the DSpace context
     * @return queues
     *         the list of names of active queues
     */
    List<String> queueNames(Context context) throws SQLException;

    /**
     * Queues a single entry to a named queue.
     * 
     * @param context
     *        the DSpace context
     * @param queueName
     *        the name of the queue on which to write
     * @param entry
     *        the task entry
     * @throws IOException
     */
    void enqueue(Context context, String queueName, TaskQueueEntry entry) throws IOException, SQLException;

    /**
     * Queues a set of task entries to a named queue.
     * 
     * @param context
     *        the DSpace context
     * @param queueName
     *        the name of the queue on which to write
     * @param entrySet
     *        the set of task entries
     * @throws IOException
     */
    void enqueue(Context context, String queueName, Set<TaskQueueEntry> entrySet) throws IOException, SQLException;
    
    /**
     * Returns the set of task entries from the named queue. The operation locks
     * the queue from any further enqueue or dequeue operations until a
     * <code>release</code> is called. The ticket may be any number, but a
     * timestamp should guarantee sufficient uniqueness.
     *  
     * @param context
     *        the DSpace context
     * @param queueName
     *        the name of the queue to read
     * @param ticket
     *        a token which must be presented to release the queue
     * @return set
     *        the current set of queued task entries
     * @throws IOException
     */
    Set<TaskQueueEntry> dequeue(Context context, String queueName, long ticket) throws IOException, SQLException;

    /**
     * Returns the set of task entries from the named queue without locking
     * the queue from any further enqueue or dequeue operations.
     *  
     * @param context
     *        the DSpace context
     * @param queueName
     *        the name of the queue to read
     * @return set
     *        the current set of queued task entries
     * @throws IOException
     */
    Set<TaskQueueEntry> peek(Context context, String queueName) throws IOException, SQLException;

    /**
     * Releases the lock upon the named queue, deleting it if <code>removeEntries</code>
     * is set to true.
     * 
     * @param context
     *        the DSpace context
     * @param queueName
     *        the name of the queue to release
     * @param ticket
     *        a token that was presented when queue was dequeued.
     * @param removeEntries
     *        flag to indicate whether entries may be deleted
     */
    void release(Context context, String queueName, long ticket, boolean removeEntries) throws SQLException;
}
