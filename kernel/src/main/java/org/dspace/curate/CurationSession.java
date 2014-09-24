/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.curate;

import java.io.IOException;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.dspace.authorize.AuthorizeException;
import org.dspace.content.DSpaceObject;
import org.dspace.core.Context;

/**
 * CurationSession supports single-task curations
 * with some relaxed constraints, viz it accepts anonymous tasks,
 * and can override task properties with invocation arguments.
 *
 * @author richardrodgers
 */
public class CurationSession implements AutoCloseable {

    private static Logger log = LoggerFactory.getLogger(CurationSession.class);
    
    private Curator curator;
    private ResolvedTask task;

    CurationSession(Curator curator, ResolvedTask task) throws IOException {
        this.curator = curator;
        this.task = task;
    }

    public int curate(DSpaceObject dso) throws AuthorizeException, IOException, SQLException {
        return curate(dso, new HashMap<String, String>());
    }

    public int curate(DSpaceObject dso, Map<String, String> args) throws AuthorizeException, IOException, SQLException {
        initCuration(args);
        curator.curate(dso);
        return curator.getStatus(task.getName());
    }

    public int curate(Context context, String id) throws AuthorizeException, IOException, SQLException {
        return curate(context, id, new HashMap<String, String>());
    }

    public int curate(Context context, String id, Map<String, String> args) throws AuthorizeException, IOException, SQLException {
        initCuration(args);
        curator.curate(context, id);
        return curator.getStatus(task.getName());
    }

    public int curate(ObjectSelector selector) throws AuthorizeException, IOException, SQLException {
        return curate(selector, new HashMap<String, String>());
    }

    public int curate(ObjectSelector selector, Map<String, String> args) throws AuthorizeException, IOException, SQLException {
        initCuration(args);
        curator.curate(selector);
        return curator.getStatus(task.getName());
    }

    private void initCuration(Map<String, String> args) throws IOException {
        curator.clear();
        ResolvedTask clone = new ResolvedTask(task);
        clone.init(new TransientCuration(curator, args));
        curator.addInitializedTask(clone);
    }

    public int getStatus() {
        return curator.getStatus(task.getName());
    }

    public String getResult() {
        return curator.getResult(task.getName());
    }

    public void close() {
        try {
            curator.complete();
        } catch (IOException ioE) {
            log.error("Error closing curation session: " + ioE.getMessage());
        }
    }

    private class TransientCuration implements Curation {

        private Curator curator;
        private Map<String, String> args;

        public TransientCuration(Curator curator, Map<String, String> args) {
            this.curator = curator;
            this.args = args;
        }
        
        /**
         * Returns invocation mode.
         * 
         * @return mode - the invocation mode
         */
        public Curator.Invoked getInvoked() {
            return curator.getInvoked();
        }

        /**
         * Returns the current journal filter
         *
         * @return filter the status filter for journaling
         */
        public String getJournalFilter() {
            return curator.getJournalFilter();
        }
      
        /**
         * Returns the current cache limit.
         * 
         * @return limit - the cache limit, or MAX_VALUE if unset
         */
        public int getCacheLimit() {
            return curator.getCacheLimit();
        }
    
        /**
         * Returns the current transaction scope
         * 
         * @return scope - the current transaction scope
         */
        public Curator.TxScope getTransactionScope() {
            return curator.getTransactionScope();
        }
    
        /**
         * Obtains a resource object managed in this curation.
         * 
         * @param key the lookup key for the object
         * 
         * @return object instance or null if no 
         *         managed object mapped to passed key
         */
        public Object obtainResource(String key) {
            return curator.obtainResource(key);
        }
    
        /**
         * Places a resource (instance object) under curator's care.
         * Curator will perform actions based on policy when curation
         * is complete.
         * 
         * @param resource - the object to be managed
         * @param policy - the instructions on how to manage it
         *                 'flush' and 'close' only policies supported
         */
        public void enrollResource(Object resource, String policy) {
            curator.enrollResource(resource, policy);
        }

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
        public boolean manageResource(String key, Object resource, String policy) {
            return curator.manageResource(key, resource, policy);
        }
    
        /**
         * Adds a message to the configured reporting stream.
         * 
         * @param message the message to output to the reporting stream.
         */
        public void report(String message) {
            curator.report(message);
        }

        /**
         * Assigns a result to the performance of the named task.
         * 
         * @param taskName the task name
         * @param result a string indicating results of performing task.
         */
        public void setResult(String taskName, String result) {
            curator.setResult(taskName, result);
        }

        /**
         * Retrieves the property for given key. This override will
         * shadow any property value with supplied invocation argument
         * If not argument assigned, the property file value used.
         */
        @Override
        public String taskProperty(String taskName, String propName) throws SQLException {
            String value = args.get(propName);
            if (value == null) {
                value = curator.taskProperty(taskName, propName);
            }
            return value;
        }
    }
}
