/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.curate;

import java.io.Closeable;
import java.io.Flushable;
import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.dspace.content.Collection;
import org.dspace.content.Community;
import org.dspace.content.DSpaceObject;
import org.dspace.content.BoundedIterator;
import org.dspace.content.Item;
import org.dspace.content.Site;
import org.dspace.core.Constants;
import org.dspace.core.Context;
import org.dspace.core.ConfigurationManager;
import org.dspace.handle.HandleManager;

/**
 * Curator orchestrates and manages the application of a one or more curation
 * tasks to a DSpace object. It provides common services and runtime
 * environment to the tasks.
 * 
 * @author richardrodgers
 */
public class Curator
{
    // status code values
    /** Curator unable to find requested task */
    public static final int CURATE_NOTASK = -3;
    /** no assigned status code - typically because task not yet performed */
    public static final int CURATE_UNSET = -2;
    /** task encountered a error in processing */
    public static final int CURATE_ERROR = -1;
    /** task completed successfully */
    public static final int CURATE_SUCCESS = 0;
    /** task failed */
    public static final int CURATE_FAIL = 1;
    /** task was not applicable to passed object */
    public static final int CURATE_SKIP = 2;
    
    // managed resource policies
    public static final String POLICY_CLOSE = "close";
    public static final String POLICY_FLUSH = "flush";
    
    // invocation modes - used by Suspendable tasks
    public static enum Invoked { INTERACTIVE, BATCH, ANY };
    // transaction scopes
    public static enum TxScope { OBJECT, CURATION, OPEN };

    private static Logger log = LoggerFactory.getLogger(Curator.class);
    
    private static final ThreadLocal<Context> curationCtx = new ThreadLocal<Context>();
    
    private Map<String, TaskRunner> trMap = new HashMap<String, TaskRunner>();
    private Map<String, ManagedResource> mrMap = null;
    private List<String> perfList = new ArrayList<String>();
    private TaskQueue taskQ = null;
    private String reporter = null;
    private Invoked iMode = null;
    private TaskResolver resolver = new TaskResolver();
    private int cacheLimit = Integer.MAX_VALUE;
    private TxScope txScope = TxScope.OPEN;

    /**
     * No-arg constructor
     */
    public Curator()
    {
    }

    /**
     * Add a task to the set to be performed. Caller should make no assumptions
     * on execution ordering.
     * 
     * @param taskName - logical name of task
     * @return this curator - to support concatenating invocation style
     */
    public Curator addTask(String taskName)
    {
    	ResolvedTask task = resolver.resolveTask(taskName);
        if (task != null)
        {
            try
            {
                task.init(this);
                trMap.put(taskName, new TaskRunner(task));
                // performance order currently FIFO - to be revisited
                perfList.add(taskName);
            }
            catch (IOException ioE)
            {
               log.error("Task: '" + taskName + "' initialization failure: " + ioE.getMessage()); 
            }
        }
        else
        {
            log.error("Task: '" + taskName + "' does not resolve");
        }
        return this;
    }
    
    /**
     * Returns whether this curator has the specified task
     * 
     * @param taskName - logical name of the task
     * @return true if task has been configured, else false
     */
     public boolean hasTask(String taskName)
     {
         return perfList.contains(taskName);
     }
      
    /**
     * Removes a task from the set to be performed.
     * 
     * @param taskName - logical name of the task
     * @return this curator - to support concatenating invocation style
     */
    public Curator removeTask(String taskName)
    {
        trMap.remove(taskName);
        perfList.remove(taskName);
        return this;
    }
    
    /**
     * Returns a list of currently configured task names.
     * 
     * @return name list - the list of task names
     */
    public List<String> getTasks()
    {
    	return new ArrayList<String>(perfList);
    }
    
    /**
     * Assigns invocation mode.
     * 
     * @param mode one of INTERACTIVE, BATCH, ANY
     * @return this curator - to support concatenating invocation style
     */
    public Curator setInvoked(Invoked mode)
    {
        iMode = mode;
        return this;
    }
    
    /**
     * Returns invocation mode.
     * 
     * @return mode - the invocation moede
     */
    public Invoked getInvoked()
    {
        return iMode;
    }

    /**
     * Sets the reporting stream for this curator.
     * 
     * @param reporter name of reporting stream. The name '-'
     *                 causes reporting to standard out. 
     * @return this curator - to support concatenating invocation style
     */
    public Curator setReporter(String reporter)
    {
        this.reporter = reporter;
        return this;
    }
    
    /**
     * Sets an upper limit for the number of objects in the context cache 
     * used in a curation, if context accessible. Note that for many forms of
     * invocation, the context is not accessible. If limit is reached,
     * context cache will be emptied. The default is no limit.
     *
     * @param limit the maximum number of objects to allow in the cache
     * @return this curator - to support concatenating invocation style
     */
    public Curator setCacheLimit(int limit)
    {
    	cacheLimit = limit;
    	return this;
    }
    
    /**
     * Returns the current cache limit.
     * 
     * @return limit - the cache limit, or MAX_VALUE if unset
     */
    public int getCacheLimit()
    {
    	return cacheLimit;
    }
    
    /**
     * Defines the transactional scope of curator executions.
     * The default is 'open' meaning that no commits are
     * performed by the framework during curation. A scope of
     * 'curation' means that a single commit will occur after the
     * entire performance is complete, and a scope of 'object'
     * will commit for each object (e.g. item) encountered in
     * a given execution.
     * 
     * @param scope the transaction scope to apply
     * @return this curator - to support concatenating invocation style
     */
    public Curator setTransactionScope(TxScope scope)
    {
    	txScope = scope;
    	return this;
    }
    
    /**
     * Returns the current transaction scope
     * 
     * @return scope - the current transaction scope
     */
    public TxScope getTransactionScope()
    {
    	return txScope;
    }
    
    /**
     * Obtains a resource object managed by this curator.
     * 
     * @param key the lookup key for the object
     * 
     * @return object instance or null if no 
     *         managed object mapped to passed key
     */
    public Object obtainResource(String key)
    {
    	ManagedResource mr = null;
    	if (mrMap != null && (mr = mrMap.get(key)) != null)
    	{
    		return mr.getResource();
    	}
    	return null;
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
    public void enrollResource(Object resource, String policy)
    {
    	if (policy != null && (policy.contains(POLICY_FLUSH) ||
    			               policy.contains(POLICY_CLOSE)))
    	{		
    		// security through obscurity
    		manageResource(UUID.randomUUID().toString(), resource, policy);
    	}
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
    protected boolean manageResource(String key, Object resource, String policy)
    {
    	if (mrMap == null)
    	{
    		mrMap = new HashMap<String, ManagedResource>();
    	}
    	if (mrMap.containsKey(key))
    	{
    		// cannot manage, another object already mapped to key
    		log.info("Cannot manage resource - key: " + key + " already assigned");
    		return false;
    	}
    	// ensure policy makes sense for this object
    	if (policy != null)
    	{
    		if ((policy.contains(POLICY_CLOSE) && ! (resource instanceof Closeable)) ||
    			(policy.contains(POLICY_FLUSH) && ! (resource instanceof Flushable)))
    		{
    			log.info("Cannot manage resourceo with key: " + key + " - invalid policy");
    			return false;
    		}
    	}
    	mrMap.put(key, new ManagedResource(resource, policy));
    	return true;
    }

    /**
     * Performs all configured tasks upon object identified by id. If
     * the object can be resolved as a handle, the DSO will be the
     * target object.
     * 
     * @param c a Dpace context
     * @param id an object identifier
     * @throws IOException
     */
    public void curate(Context c, String id) throws IOException
    {
        if (id == null)
        {
           throw new IOException("Cannot perform curation task(s) on a null object identifier!");            
        }
        try
        {
            //Save the context on current execution thread
            curationCtx.set(c);        
            DSpaceObject dso = HandleManager.resolveToObject(c, id);
            if (dso != null)
            {
                curate(dso);
            }
            else
            {
                for (String taskName : perfList)
                {
                    trMap.get(taskName).run(c, id);
                }
            }
            finish();
        }
        catch (SQLException sqlE)
        {
            throw new IOException(sqlE.getMessage(), sqlE);
        }
        finally
        {
            curationCtx.remove();
        }
    }

    /**
     * Performs all configured tasks upon DSpace object 
     * (Community, Collection or Item).
     * <P>
     * Note: Site-wide tasks will default to running as
     * an Anonymous User unless you call the Site-wide task
     * via the 'curate(Context,String)' method with an 
     * authenticated Context object.
     * 
     * @param dso the DSpace object
     * @throws IOException
     */
    public void curate(DSpaceObject dso) throws IOException
    {
        if (dso == null)
        {
            throw new IOException("Cannot perform curation task(s) on a null DSpaceObject!");
        }
        int type = dso.getType();
        for (String taskName : perfList)
        {
            TaskRunner tr = trMap.get(taskName);
            // do we need to iterate over the object ?
            if (type == Constants.ITEM || tr.task.isDistributive())
            {
                tr.run(dso);
            }
            else if (type == Constants.COLLECTION)
            {
                doCollection(tr, (Collection)dso);
            }
            else if (type == Constants.COMMUNITY)
            {
                doCommunity(tr, (Community)dso);
            }  
            else if (type == Constants.SITE)
            {
                doSite(tr, (Site) dso);    
            }
        }
    }
    
    /**
     * Performs all configured tasks upon all DSpace objects 
     * provided by the object selector.
     * 
     * @param selector the object selector
     * @throws IOException
     */
    public void curate(ObjectSelector selector) throws IOException
    {
        if (selector == null)
        {
            throw new IOException("Cannot perform curation task(s) with a null selector!");
        }
        try
        {
            //Save the context on current execution thread
            curationCtx.set(selector.getContext());         
            while (selector.hasNext())
            {
            	curate(selector.next());
            }            
            finish();
        }
        catch (SQLException sqlE)
        {
            throw new IOException(sqlE.getMessage(), sqlE);
        }
        finally
        {
            curationCtx.remove();
        }    	
    }
    
    /**
     * Places a curation request for the object identified by id on a
     * managed queue named by the queueId.
     * 
     * @param c A DSpace context
     * @param id an object Id
     * @param queueId name of a queue. If queue does not exist, it will
     *                be created automatically.
     * @throws IOException
     */
    public void queue(Context c, String id, String queueId) throws IOException
    {
        if (taskQ == null)
        {
        	String tqClass = ConfigurationManager.getProperty("curate", "taskqueue.impl");
        	if (tqClass != null) {
        		try {
        			taskQ = (TaskQueue)Class.forName(tqClass).newInstance();
        		} catch (Exception e) {
        			log.error("Error instantiating task queue", e);
        			taskQ = null;
        		}
        	}
        }
        if (taskQ != null)
        {
            taskQ.enqueue(queueId, new TaskQueueEntry(c.getCurrentUser().getName(),
                                    System.currentTimeMillis(), perfList, id));
        }
        else
        {
            log.error("curate - no TaskQueue implemented");
        }
    }
    
    /**
     * Places a curation request for each selector-provided object on a
     * managed queue named by the queueId.
     * 
     * @param selector an object selector 
     * @param queueId name of a queue. If queue does not exist, it will
     *                be created automatically.
     * @throws IOException
     */
    public void queue(ObjectSelector selector, String queueId) throws IOException
    {
    	Context c = selector.getContext();
    	while(selector.hasNext())
    	{
    		DSpaceObject dso = selector.next();
    		String objId = dso.getHandle();
    		if (objId == null)
    		{
    			// workflow object
    			objId = String.valueOf(dso.getID());
    		}
    		queue(c, objId, queueId);
    	}
    }
    
    /**
     * Removes all configured tasks from the Curator.
     */
    public void clear()
    {
        trMap.clear();
        perfList.clear();
    }
    
    /**
     * Releases any managed resources
     */
    public void complete() throws IOException
    {
    	if (mrMap != null)
    	{
    		for (ManagedResource mr : mrMap.values())
    		{
    			mr.release();
    		}
    		mrMap.clear();
    		mrMap = null;
    		clear();
    	}
    }

    /**
     * Adds a message to the configured reporting stream.
     * 
     * @param message the message to output to the reporting stream.
     */
    public void report(String message)
    {
        // Stub for now
        if ("-".equals(reporter))
        {
            System.out.println(message);
        }
    }

    /**
     * Returns the status code for the latest performance of the named task.
     * 
     * @param taskName the task name
     * @return the status code - one of CURATE_ values
     */
    public int getStatus(String taskName)
    {
        TaskRunner tr = trMap.get(taskName);
        return (tr != null) ? tr.statusCode : CURATE_NOTASK;
    }

    /**
     * Returns the result string for the latest performance of the named task.
     * 
     * @param taskName the task name
     * @return the result string, or <code>null</code> if task has not set it.
     */
    public String getResult(String taskName)
    {
        TaskRunner tr = trMap.get(taskName);
        return (tr != null) ? tr.result : null;
    }

    /**
     * Assigns a result to the performance of the named task.
     * 
     * @param taskName the task name
     * @param result a string indicating results of performing task.
     */
    public void setResult(String taskName, String result)
    {
        TaskRunner tr = trMap.get(taskName);
        if (tr != null)
        {
            tr.setResult(result);
        }
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
    public static Context curationContext() throws SQLException
    {
    	// Return curation context or new context if undefined/invalid
    	Context curCtx = curationCtx.get();
        
        if(curCtx==null || !curCtx.isValid())
        {
            //Create a new context (represents an Anonymous User)
            curCtx = new Context();
            //Save it to current execution thread
            curationCtx.set(curCtx);
        }    
        return curCtx;
    }

    /**
     * Returns whether a given DSO is a 'container' - collection or community
     * @param dso a DSpace object
     * @return true if a container, false otherwise
     */
    public static boolean isContainer(DSpaceObject dso)
    {
        return (dso.getType() == Constants.COMMUNITY ||
                dso.getType() == Constants.COLLECTION);
    }
    
    /**
     * Ensures instance has been completed.
     */
    @Override
    protected void finalize() throws Throwable
    {
    	complete();
    	super.finalize();
    }
    
    /**
     * Completes curation operation
     */
    private void finish() throws SQLException
    {
    	// if curation scoped, commit transaction
        if (txScope.equals(TxScope.CURATION))
        {
        	Context ctx = curationCtx.get();
        	if (ctx != null)
        	{
        		ctx.commit();
        	}
        }
    }

    /**
     * Run task for entire Site (including all Communities, Collections & Items)
     * @param tr TaskRunner
     * @param site DSpace Site object
     * @return true if successful, false otherwise
     * @throws IOException 
     */
    private boolean doSite(TaskRunner tr, Site site) throws IOException
    {
        Context ctx = null;
        BoundedIterator<Community> cIter = null;
        try
        {
            //get access to the curation thread's current context
            ctx = curationContext();
            
            // Site-wide Tasks really should have an EPerson performer associated with them,
            // otherwise they are run as an "anonymous" user with limited access rights.
            if(ctx.getCurrentUser()==null && !ctx.ignoreAuthorization())
            {
                log.warn("You are running one or more Site-Wide curation tasks in ANONYMOUS USER mode," +
                         " as there is no EPerson 'performer' associated with this task. To associate an EPerson 'performer' " +
                         " you should ensure tasks are called via the Curator.curate(Context, ID) method.");
            }
            
            //Run task for the Site object itself
            if (! tr.run(site))
            {
                return false;
            }
            
            //Then, perform this task for all Top-Level Communities in the Site
            // (this will recursively perform task for all objects in DSpace)
            cIter = Community.findAllTop(ctx);
            while(cIter.hasNext()) {
                if (! doCommunity(tr, cIter.next())) {
                    return false;
                }
            }
        }
        catch (SQLException sqlE)
        {
            throw new IOException(sqlE);
        } finally {
        	if (cIter != null) {
        		cIter.close();
        	}
        }

        return true;
    }
    
    /**
     * Run task for Community along with all sub-communities and collections.
     * @param tr TaskRunner
     * @param comm Community
     * @return true if successful, false otherwise
     * @throws IOException 
     */
    private boolean doCommunity(TaskRunner tr, Community comm) throws IOException {
    	BoundedIterator<Community> scIter = null;
    	BoundedIterator<Collection> colIter = null;
        try  {
            if (! tr.run(comm)) {
                return false;
            }
            scIter = comm.getSubcommunities();
            while(scIter.hasNext()) {
                if (! doCommunity(tr, scIter.next())) {
                    return false;
                }
            }
            colIter = comm.getCollections();
            while(colIter.hasNext()) {
                if (! doCollection(tr, colIter.next())) {
                    return false;
                }
            }
        } catch (SQLException sqlE) {
            throw new IOException(sqlE.getMessage(), sqlE);
        } finally {
        	if (scIter != null) {
        		scIter.close();
        	}
        	if (colIter != null) {
        		colIter.close();
        	}
        }
        return true;
    }

    /**
     * Run task for Collection along with all Items in that collection.
     * @param tr TaskRunner
     * @param coll Collection
     * @return true if successful, false otherwise
     * @throws IOException 
     */
    private boolean doCollection(TaskRunner tr, Collection coll) throws IOException
    {
    	BoundedIterator<Item> iter = null;
        try {
            if (! tr.run(coll)) {
                return false;
            }
            iter = coll.getItems();
            while (iter.hasNext()) {
                if (! tr.run(iter.next())) {
                    return false;
                }
            }
        } catch (SQLException sqlE)  {
            throw new IOException(sqlE.getMessage(), sqlE);
        } finally {
        	if (iter != null) {
        		iter.close();
        	}
        }
        return true;
    }
    
    /**
     * Record a 'visit' to a DSpace object and enforce any policies set
     * on this curator.
     */
    private void visit(DSpaceObject dso) throws IOException
    {
    	Context curCtx = curationCtx.get();
    	if (curCtx != null)
    	{
    		try
    		{
    			if (txScope.equals(TxScope.OBJECT))
    			{
    				curCtx.commit();
    			}
    			if (curCtx.getCacheSize() % cacheLimit == 0)
    			{
    				curCtx.clearCache();
    			}
    		}
    		catch (SQLException sqlE)
    		{
    			throw new IOException(sqlE.getMessage(), sqlE);
    		}
    	}
    }

    private class TaskRunner
    {
        ResolvedTask task = null;
        int statusCode = CURATE_UNSET;
        String result = null;

        public TaskRunner(ResolvedTask task)
        {
            this.task = task;
        }
        
        public boolean run(DSpaceObject dso) throws IOException
        {
            try
            {    
                if (dso == null)
                {
                    throw new IOException("DSpaceObject is null");
                }
                statusCode = task.perform(dso);
                String id = (dso.getHandle() != null) ? dso.getHandle() : "workflow item: " + dso.getID();
                log.info(logMessage(id));
                visit(dso);
                task.record(id, curationCtx.get(), statusCode, result);
                return ! suspend(statusCode);
            }
            catch(IOException ioe)
            {
                //log error & pass exception upwards
                log.error("Error executing curation task '" + task.getName() + "'", ioe);
                throw ioe;
            }
        }
        
        public boolean run(Context c, String id) throws IOException
        {
            try
            {
                if (c == null || id == null)
                {
                    throw new IOException("Context or identifier is null");
                }
                statusCode = task.perform(c, id);
                log.info(logMessage(id));
                visit(null);
                task.record(id, c, statusCode, result);
                return ! suspend(statusCode);
            }
            catch(IOException ioe)
            {
                //log error & pass exception upwards
                log.error("Error executing curation task '" + task.getName() + "'", ioe);
                throw ioe;
            }
        }

        public void setResult(String result)
        {
            this.result = result;
        }
        
        private boolean suspend(int code)
        {
        	Invoked mode = task.getMode();
            if (mode != null && (mode.equals(Invoked.ANY) || mode.equals(iMode)))
            {
                for (int i : task.getCodes())
                {
                    if (code == i)
                    {
                        return true;
                    }
                }
            }
            return false;
        }
        
        /**
         * Builds a useful log message for a curation task.
         * @param id ID of DSpace Object
         * @return log message text
         */
        private String logMessage(String id) 
        {
            StringBuilder mb = new StringBuilder();
            mb.append("Curation task: ").append(task.getName()).
               append(" performed on: ").append(id).
               append(" with status: ").append(statusCode);
            if (result != null)
            {
                mb.append(". Result: '").append(result).append("'");
            }
            return mb.toString();
        }
    }
    
    private class ManagedResource
    {
    	private Object resource = null;
    	private String policy = null;
    	
    	public ManagedResource(Object resource, String policy)
    	{
    		this.resource = resource;
    		this.policy = policy;
    	}
    	
    	public Object getResource()
    	{
    		return resource;
    	}
    	
    	public void release() throws IOException
    	{
    		if (policy != null)
    		{
    			if (policy.contains(POLICY_FLUSH))
    			{
    				((Flushable)resource).flush();
    			}
    			if (policy.contains(POLICY_CLOSE))
    			{
    				((Closeable)resource).close();
    			}
    		}
    	}
    }
}
