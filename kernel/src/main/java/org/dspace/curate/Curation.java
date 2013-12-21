/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.curate;

import java.sql.SQLException;
import java.util.List;

/**
 * Curation is the service interface injected into
 * executing tasks. It offers common curation services
 * to tasks, such as reporting, task properties, etc
 * 
 * @author richardrodgers
 */
public interface Curation {
            
    /**
     * Returns invocation mode.
     * 
     * @return mode - the invocation mode
     */
    Curator.Invoked getInvoked();

    /**
     * Returns the journal filter
     *
     * @return filter the status filter for journaling
     */
    String getJournalFilter();
      
    /**
     * Returns the current cache limit.
     * 
     * @return limit - the cache limit, or MAX_VALUE if unset
     */
    int getCacheLimit();
    
    /**
     * Returns the current transaction scope
     * 
     * @return scope - the current transaction scope
     */
    Curator.TxScope getTransactionScope();
    
    /**
     * Obtains a resource object managed in this curation.
     * 
     * @param key the lookup key for the object
     * 
     * @return object instance or null if no 
     *         managed object mapped to passed key
     */
    Object obtainResource(String key);
    
    /**
     * Places a resource (instance object) under curator's care.
     * Curator will perform actions based on policy when curation
     * is complete.
     * 
     * @param resource - the object to be managed
     * @param policy - the instructions on how to manage it
     *                 'flush' and 'close' only policies supported
     */
    void enrollResource(Object resource, String policy);

    /**
     * Puts an object under curator's management. Management typically
     * means that the object is cleaned up/released after curation.
     * 
     * @param key lookup key for object
     * @param object to be managed
     * @param policy instructions on object management or null if no specific policy
     *       - only 'close' and 'flush' currently supported policies
     * @return boolean true if object put under management, else false
     */
    boolean manageResource(String key, Object resource, String policy);
    
    /**
     * Adds a message to the configured reporting stream.
     * 
     * @param message the message to output to the reporting stream.
     */
    void report(String message);

    /**
     * Returns the value of the named property for the given task
     *
     * @param taskName the task name
     * @param propName the property name (key)
     * @return the property value, or <code>null</code> if task has not defined it.
     */
    String taskProperty(String taskName, String propName) throws SQLException;

    /**
     * Assigns a result to the performance of the named task.
     * 
     * @param taskName the task name
     * @param result a string indicating results of performing task.
     */
    void setResult(String taskName, String result);

}
