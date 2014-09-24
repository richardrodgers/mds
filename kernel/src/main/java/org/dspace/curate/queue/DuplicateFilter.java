/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */

package org.dspace.curate.queue;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.Iterator;

import org.dspace.core.Context;

/**
 * DuplicateFilter is a TaskQueueFilter that eliminates all 'isoFunctional'
 * duplicates (same task list and target). It does not sort results.
 *
 * @author richardrodgers
 */
public class DuplicateFilter implements TaskQueueFilter {

     /**
      *
      * Required no-arg constructor
      */
     public DuplicateFilter() {}
    
    /**
     * Returns an iterator over a TaskQueueEntry set.
     * 
     * @param entrySet
     *        a set of TaskQueueEntries
     * @return iterator
     *         an iterator over the (possibly filtered or ordered) entrySet
     */
    @Override
    public Iterator<TaskQueueEntry> filter(Set<TaskQueueEntry> entrySet) {
         Map<String, TaskQueueEntry> isoMap = new HashMap<>();
         Iterator<TaskQueueEntry> tqIter = entrySet.iterator();
         while (tqIter.hasNext()) {
             TaskQueueEntry tqe = tqIter.next();
             String key = tqe.getTaskList() + tqe.getTarget();
             if (! isoMap.containsKey(key)) {
                 isoMap.put(key, tqe);
             }
         }
         return isoMap.values().iterator();
    }
}
