/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.ctask.replicate;

import java.sql.SQLException;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.common.eventbus.Subscribe;

import org.dspace.authorize.AuthorizeException;
import org.dspace.content.Collection;
import org.dspace.content.Community;
import org.dspace.content.Item;
import org.dspace.core.Context;
import org.dspace.core.ConfigurationManager;
import org.dspace.core.Constants;
import org.dspace.curate.Curator;
import org.dspace.curate.queue.TaskQueue;
import org.dspace.curate.queue.TaskQueueEntry;
import org.dspace.eperson.EPerson;
import org.dspace.event.Consumes;
import org.dspace.event.ContainerEvent;
import org.dspace.event.ContentEvent;
import org.dspace.event.ContentEvent.EventType;


/**
 * BagItReplicateConsumer is an event consumer that tracks events relevant to
 * replication synchronization. In response to deletions, it creates and
 * transmits a catalog of deleted objects (so they may be restored if 
 * deletion was an error). For new or changed objects, it queues a request
 * to perform the configured curation tasks, or directly performs the task
 * if so indicated.
 * 
 * RLR FIXME - this class needs to be redone using new event model
 * @author richardrodgers
 */
@Consumes("content")
public class BagItReplicateConsumer {

    private ReplicaManager repMan = null;
    private TaskQueue taskQueue = null;
    private String queueName = null;
    // list and sense for id filtering
    private List<String> idFilter = null;
    private boolean idExclude = true;
    // map of task names to id sets
    private Map<String, Set<String>> taskQMap = null;
    private Map<String, Set<String>> taskPMap = null;
    private String delObjId = null;
    private String delOwnerId = null;
    private List<String> delMemIds = null;
    // tasks to queue upon add events
    private List<String> addQTasks = null;
    // tasks to perform immediately upon add events
    private List<String> addPTasks = null;
    // tasks to queue upon modify events
    private List<String> modQTasks = null;
    // tasks to perform immediately upon modify events
    private List<String> modPTasks = null;
    // tasks to queue upon delete events
    private List<String> delTasks = null;
    // create deletion catalogs?
    private boolean catalogDeletes = false;

    public void initialize() throws Exception {
        repMan = ReplicaManager.instance();
        taskQueue = (TaskQueue)ConfigurationManager.getInstance("curate", "taskqueue.impl");
        queueName = localProperty("consumer.queue");
        // look for and load any idFilters - excludes trump includes
        // which contains a list of handles to filter from the Consumer
        String excludes = ConfigurationManager.getProperty("replicate", "consumer.filter.exclude");
        if (excludes != null) {
            idFilter = Arrays.asList(excludes.split(","));
        } else {
            String includes = ConfigurationManager.getProperty("replicate", "consumer.filter.include");
            if (includes != null) {
                idFilter = Arrays.asList(includes.split(","));
                idExclude = false;
            }
        } 
        taskQMap = new HashMap<String, Set<String>>();
        taskPMap = new HashMap<String, Set<String>>();
        parseTasks("add");
        parseTasks("mod");
        delMemIds = new ArrayList<String>();
        parseTasks("del");
    }

    /**
     * Consume a content event. At a high level, 2 sorts of actions are
     * performed: first, for all new or modified objects, the object handle
     * is added to a set of objects to be processed. When a Curator batch
     * next runs, this list will be read and whatever tasks are configured to
     * be performed will be. Typically, a new AIP will be generated and
     * uploaded to the replication service. Second, for deletions, the event
     * stream is parsed to construct a 'delete catalog' containing an enumeration
     * of the objects that are being deleted. This also is uploaded to the
     * replication service, and can be used either to recover from mistaken
     * deletions, or purge the replica store when desired.
     *
     * @param ctx
     * @param event
     * @throws Exception
     */
    @Subscribe
    public void consumeContentEvent(ContentEvent event) throws Exception {
        //int subjType = event.getSubjectType();
        // This is the Handle of the object on which an event occured
        String id = event.getObject().getHandle();
        //System.out.println("got event type: " + evType + " for subject type: " + subjType);
        switch (event.getEventType())  {
            case CREATE: //CREATE = Create a new object.
            case INSTALL: //INSTALL = Install an object (exits workflow/workspace). Only used for Items.
                // if NOT (Item & Create)
                // (i.e. We don't want to replicate items UNTIL they are Installed)
                if (event.getObject().getType() != Constants.ITEM || event.getEventType() != EventType.CREATE) {
                    if (acceptId(event)) {
                        // add it to the master lists of added/new objects
                        // for which we need to perform tasks
                        mapId(taskQMap, addQTasks, id);
                        mapId(taskPMap, addPTasks, id);
                    }
                }
                break;
            case MODIFY: //MODIFY = modify an object
           // case MODIFY_METADATA: //MODIFY_METADATA = just modify an object's metadata
                //For MODIFY events, the Handle of modified object needs to be obtained from the Subject
                // make sure handle resolves - these could be events
                // for a newly created item that hasn't been assigned a handle
                if (id != null)  {
                    // make sure we are supposed to process this object
                    if (acceptId(event))    {
                        // add it to the master lists of modified objects
                        // for which we need to perform tasks
                        mapId(taskQMap, modQTasks, id);
                        mapId(taskPMap, modPTasks, id);
                    }
                }
                break;
            case REMOVE: //REMOVE = Remove an object from a container or group
            case DELETE: //DELETE = Delete an object (actually destroy it)
                // make sure we are supposed to process this object
                if (acceptId(event)) { 
                    // analyze & process the deletion/removal event
                    deleteEvent(id, event);
                }
                break;
            default:
                break;
        }
    }

    public void end(Context ctx) throws Exception {
        // if there are any pending objectIds, pass them to the curation
        // system to queue for later processing, or perform immediately
        EPerson ep = ctx.getCurrentUser();
        String name = (ep != null) ? ep.getName() : "unknown";
        long stamp = System.currentTimeMillis();
        // first the queueables
        Set<TaskQueueEntry> entrySet = new HashSet<TaskQueueEntry>();
        if (taskQMap.size() > 0) {
            List<String> taskList = new ArrayList<String>();
            for (String task : taskQMap.keySet()) {
                taskList.add(task);
                for (String id : taskQMap.get(task)) {
                    entrySet.add(new TaskQueueEntry(name, stamp, taskList, id, "n"));
                }
                taskList.clear();
            }
            taskQMap.clear();
        }
        // now the performables
        if (taskPMap.size() > 0) {
            Curator curator = new Curator();
            for (String task : taskPMap.keySet()) {
                curator.addTask(ctx, task);
                for (String id : taskQMap.get(task)) {
                    curator.curate(ctx, id);
                }
                curator.clear();
            }
            taskPMap.clear();
        }
       
        // if there any uncommitted deletions, record them now
        if (delObjId != null) {
            if (delTasks != null) {
                entrySet.add(new TaskQueueEntry(name, stamp, delTasks, delObjId, "n"));
            }
            processDelete();
        }
        if (entrySet.size() > 0) {
            taskQueue.enqueue(ctx, queueName, entrySet);
        }
    }

    /**
     * Check to see if an object ID (Handle) is allowed to be processed by
     * this consumer. Individual Objects may be filtered out of consumer
     * processing by using a filter file (a textual file with a list of
     * handles to either include or exclude).
     *
     * @param event Event that was performed on the Object
     * @return true if this consumer should process this object event, false if it should not
     * @throws SQLException if database error occurs
     */
    private boolean acceptId(ContentEvent event) throws SQLException {
        // always accept if not filtering
        if (idFilter == null) {
            return true;
        }
        // filter supports only container ids - so if id is for an item,
        // find its owning collection
        String id2check = event.getObject().getHandle();
        if (event.getObject().getType() == Constants.ITEM) {
            // NB: Item should be available form context cache - should
            // not incur a performance hit here
            Item item = (Item)event.getObject();
            Collection coll = item.getOwningCollection();
            if (coll != null) {
                id2check = coll.getHandle();
            }
        }
        boolean onList = idFilter.contains(id2check);
        return idExclude ? ! onList : onList;
    }

    /**
     * Process a DELETE (destroy object) or REMOVE (remove object from container) event.
     * For a DELETE, record all objects that were deleted (parent & possible child objects)
     * For a REMOVE, if this was preceded by deletion of a parent, record a deletion catalog
     * @param ctx current DSpace Context
     * @param id Object on which the delete/remove event was triggered
     * @param event event that was triggered
     * @throws Exception
     */
    private void deleteEvent(String id, ContentEvent event) throws Exception {
        switch(event.getEventType()) {
            case DELETE:
            // either marks start of new deletion or a member of enclosing one
            if (delObjId == null) {
                //Start of a new deletion
                delObjId = id;
            } else {
                // just add to list of deleted members
                delMemIds.add(id);
            }
            break;
            case REMOVE:
            // either marks end of current deletion or is member of
            // enclosing one: ignore if latter
            if (delObjId.equals(id)) {
                // determine owner and write out deletion catalog
                int type = event.getObject().getType();
                if (Constants.COLLECTION == type || Constants.COMMUNITY == type) {
                    // my owner is a collection or community
                    delOwnerId = event.getObject().getHandle();
                }
                processDelete();
             }
            break;
            default: break;
        }
    }

    /*
     * Process a deletion event by recording a deletion catalog if configured
     */
    private void processDelete() throws IOException {
        // write out deletion catalog if defined
        if (catalogDeletes) {
            BagItCatalog catalog = new BagItCatalog(delObjId, delOwnerId, delMemIds);
            Path packDir = repMan.stage(repMan.deleteGroupName(), delObjId);
            Path archive = catalog.pack(packDir);
            //System.out.println("delcat about to transfer");
            repMan.transferObject(repMan.deleteGroupName(), archive);
        }
        // reset for next events
        delObjId = delOwnerId = null;
        delMemIds.clear();
    }

    /**
     * Record the given object tasklist in the given "map".  This is essentially
     * providing a master list (map) of tasks to perform for particular objects.
     * NOTE: if this object and task already exist in the master list, it will
     * NOT be duplicated.
     * @param map Master task list to add to (String task, Set<String> ids)
     * @param tasks Tasks to be performed
     * @param id Object for which the tasks should be performed.
     */
    private void mapId(Map<String, Set<String>> map, List<String> tasks, String id) {
        if (tasks != null) {
            for (String task : tasks)  {
                Set<String> ids = map.get(task);
                if (ids == null) {
                    ids = new HashSet<String>();
                    map.put(task, ids);
                }
                ids.add(id);
            }
        }
    }
    
    /**
     * Parse the list of Consumer tasks to perform.  This list of tasks
     * is in the 'replicate.cfg' file.
     * @param propName property name
     */
    private void parseTasks(String propName)  {
        String taskStr = localProperty("consumer.tasks." + propName);
        if (taskStr == null || taskStr.length() == 0) {
            return;
        }
        for (String task : taskStr.split(",")) {
            task = task.trim();
            //If the task in question does NOT end in "+p",
            // then it should be queued for later processing
            if (! task.endsWith("+p"))  {
                if ("add".equals(propName)) {
                    if (addQTasks == null) {
                        addQTasks = new ArrayList<String>();
                    }
                    addQTasks.add(task);
                } else if ("mod".equals(propName)) {
                    if (modQTasks == null) {
                        modQTasks = new ArrayList<String>();
                    }
                    modQTasks.add(task);   
                } else if ("del".equals(propName)) {
                    if (delTasks == null) {
                        delTasks = new ArrayList<String>();
                    }
                    delTasks.add(task);   
                }
            }
            //Otherwise (if the task ends in "+p"),
            //  it should be added to the list of tasks to perform immediately
            else {
                String sTask = task.substring(0, task.lastIndexOf("+p"));
                if ("add".equals(propName)) {
                    if (addPTasks == null) {
                        addPTasks = new ArrayList<String>();
                    }
                    addPTasks.add(sTask);
                } else if ("mod".equals(propName)) {
                    if (modPTasks == null) {
                        modPTasks = new ArrayList<String>();
                    }
                    addPTasks.add(sTask);
                } else if ("del".equals(propName)) {
                    // just test for special case of deletion catalogs.
                    if ("catalog".equals(sTask)) {
                        catalogDeletes = true;
                    } 
                }
            }
        }
    }

    /**
     * Load a single property value from the "replicate.cfg" configuration file
     * @param propName property name
     * @return property value
     */
    private String localProperty(String propName) {
        return ConfigurationManager.getProperty("replicate", propName);
    }

}
