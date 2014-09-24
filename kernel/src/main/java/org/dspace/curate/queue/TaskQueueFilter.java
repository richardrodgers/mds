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
import java.util.Iterator;

import org.dspace.core.Context;

/**
 * A TaskQueueFilter is used to condition a set of queue entries before
 * execution, but after removal from the queue. Can eliminate duplicates,
 * sequence for optimal use, etc
 *
 * @author richardrodgers
 */
public interface TaskQueueFilter {
    
    /**
     * Returns an iterator over a TaskQueueEntry set.
     * 
     * @param entrySet
     *        a set of TaskQueueEntries
     * @return iterator
     *         an iterator over the (possibly filtered or ordered) entrySet
     */
    Iterator<TaskQueueEntry> filter(Set<TaskQueueEntry> entrySet);
}
