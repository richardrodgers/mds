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
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.dspace.authorize.AuthorizeException;
import org.dspace.content.Collection;
import org.dspace.content.Community;
import org.dspace.content.BoundedIterator;
import org.dspace.content.DSpaceObject;
import org.dspace.content.Item;
import org.dspace.core.ConfigurationManager;
import org.dspace.core.Constants;
import org.dspace.core.Context;
import org.dspace.handle.HandleManager;

/**
 * AbstractCurationTask encapsulates a few common patterns of task use,
 * resources, and convenience methods.
 * 
 * @author richardrodgers
 */
public abstract class AbstractCurationTask implements CurationTask {

    // curation context
    protected Curation curation = null;
    // curator-assigned taskId
    protected String taskId = null;
    // logger
    private static Logger log = LoggerFactory.getLogger(AbstractCurationTask.class);

    @Override
    public void init(Curation curation, String taskId) throws IOException {
        this.curation = curation;
        this.taskId = taskId;
    }

    @Override
    public abstract int perform(DSpaceObject dso) throws AuthorizeException, IOException, SQLException;
    
    /**
     * Distributes a task through a DSpace container - a convenience method
     * for tasks declaring the <code>@Distributive</code> property. Users must
     * override the 'performItem' invoked by this method.
     * 
     * @param dso
     * @throws IOException
     * @throws SQLException
     */
    protected void distribute(DSpaceObject dso) throws IOException, SQLException {
        BoundedIterator<Item> itIter = null;
        BoundedIterator<Community> scIter = null;
        BoundedIterator<Collection> colIter = null;
        try {
            int type = dso.getType();
            if (Constants.ITEM == type) {
                performItem((Item)dso);
            } else if (Constants.COLLECTION == type)  {
                itIter = ((Collection)dso).getItems();
                while (itIter.hasNext()) {
                    performItem(itIter.next());
                }
            } else if (Constants.COMMUNITY == type) {
                Community comm = (Community)dso;
                scIter = comm.getSubcommunities();
                while(scIter.hasNext()) {
                    distribute(scIter.next());
                }
                colIter = comm.getCollections();
                while(colIter.hasNext()) {
                    distribute(colIter.next());
                }
            }
        } finally {
            if (itIter != null) {
                itIter.close();
            }
            if (scIter != null) {
                scIter.close();
            }
            if (colIter != null) {
                colIter.close();
            }
        }
    }
    
    /**
     * Performs task upon an Item. Must be overridden if <code>distribute</code>
     * method is used.
     * 
     * @param item
     * @throws SQLException
     * @throws IOException
     */
    protected void performItem(Item item) throws SQLException, IOException {
        // no-op - override when using 'distribute' method
    }

    @Override
    public int perform(Context ctx, String id) throws AuthorizeException, IOException, SQLException {
        DSpaceObject dso = dereference(ctx, id);
        return (dso != null) ? perform(dso) : Curator.CURATE_FAIL;
    }
    
    /**
     * Returns a DSpaceObject for passed identifier, if it exists
     * 
     * @param ctx
     *        DSpace context
     * @param id 
     *        canonical id of object
     * @return dso
     *        DSpace object, or null if no object with id exists
     * @throws IOException
     * @throws SQLException
     */
    protected DSpaceObject dereference(Context ctx, String id) throws IOException, SQLException {
        return HandleManager.resolveToObject(ctx, id);
    }

    /**
     * Sends message to the reporting stream
     * 
     * @param message
     *        the message to stream
     */
    protected void report(String message) {
        curation.report(message);
    }

    /**
     * Assigns the result of the task performance
     * 
     * @param result
     *        the result string
     */
    protected void setResult(String result) {
        curation.setResult(taskId, result);
    }

    /**
     * Returns the context object used in the current curation thread.
     * This is primarily a utility method to allow tasks access to the context when necessary.
     * <P>
     * If the context is null or not set, then this just returns
     * a brand new Context object representing an Anonymous User.
     * 
     * @return curation thread's Context object (or a new, anonymous Context if no curation Context exists)
     */
    protected Context curationContext() throws SQLException {
        return Curator.curationContext();
    }
    
    /**
     * Returns task configuration property value for passed name, else
     * <code>null</code> if no properties defined or no value for passed key.
     * 
     * @param name
     *        the property name
     * @return value
     *        the property value, or null
     */
    protected String taskProperty(String name) {
        return curation.taskProperty(taskId, name);
    }
    
    /**
     * Returns task configuration integer property value for passed name, else
     * passed default value if no properties defined or no value for passed key.
     * 
     * @param name
     *        the property name
     * @param defaultValue value
     *        the default value
     * @return value
     *        the property value, or default value
     * 
     */
    protected int taskIntProperty(String name, int defaultValue) {
        int intVal = defaultValue;
        String strVal = taskProperty(name);
        if (strVal != null) {
            try {
                intVal = Integer.parseInt(strVal.trim());
            } catch(NumberFormatException nfE) {
                log.warn("Warning: Number format error in module: " + taskId + " property: " + name);
            }
        }
        return intVal;
    } 
    
    /**
     * Returns task configuration long property value for passed name, else
     * passed default value if no properties defined or no value for passed key.
     * 
     * @param name
     *        the property name
     * @param defaultValue value
     *        the default value
     * @return value
     *        the property value, or default
     */
    protected long taskLongProperty(String name, long defaultValue) {
        long longVal = defaultValue;
        String strVal = taskProperty(name);
        if (strVal != null) {
            try {
                longVal = Long.parseLong(strVal.trim());
            } catch(NumberFormatException nfE) {
                log.warn("Warning: Number format error in module: " + taskId + " property: " + name);
            }
        }
        return longVal;
    }  
    
    /**
     * Returns task configuration boolean property value for passed name, else
     * passed default value if no properties defined or no value for passed key.
     * 
     * @param name
     *        the property name
     * @param defaultValue value
     *        the default value
     * @return value
     *        the property value, or default
     */
    protected boolean taskBooleanProperty(String name, boolean defaultValue) {
        String strVal = taskProperty(name);
        if (strVal != null) {
            strVal = strVal.trim();
            return strVal.equalsIgnoreCase("true") ||
                   strVal.equalsIgnoreCase("yes");
        }
        return defaultValue;
    }
}
