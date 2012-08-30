/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.content;

import java.io.IOException;
import java.io.InputStream;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.MissingResourceException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.dspace.authorize.AuthorizeConfiguration;
import org.dspace.authorize.AuthorizeException;
import org.dspace.authorize.AuthorizeManager;
import org.dspace.authorize.ResourcePolicy;
import org.dspace.browse.BrowseException;
import org.dspace.browse.IndexBrowse;
import org.dspace.browse.ItemCounter;
import org.dspace.browse.ItemCountException;
import org.dspace.core.ConfigurationManager;
import org.dspace.core.Constants;
import org.dspace.core.Context;
import org.dspace.core.I18nUtil;
import org.dspace.core.LogManager;
import org.dspace.eperson.Group;
import org.dspace.event.Event;
import org.dspace.handle.HandleManager;
import org.dspace.storage.rdbms.DatabaseManager;
import org.dspace.storage.rdbms.TableRow;
import org.dspace.storage.rdbms.TableRowIterator;
import org.dspace.workflow.WorkflowItem;
//import org.dspace.xmlworkflow.storedcomponents.CollectionRole;
//import org.dspace.xmlworkflow.storedcomponents.XmlWorkflowItem;

/**
 * Class representing a collection.
 * <P>
 * The collection's metadata (name, introductory text etc), workflow groups, and
 * default group of submitters are loaded into memory. Changes to metadata are
 * not written to the database until <code>update</code> is called. If you
 * create or remove a workflow group, the change is only reflected in the
 * database after calling <code>update</code>. The default group of
 * submitters is slightly different - creating or removing this has instant
 * effect.
 * 
 * @author Robert Tansley
 * @version $Revision: 6526 $
 */
public class Collection extends DSpaceObject
{
    /** log4j category */
    private static Logger log = LoggerFactory.getLogger(Collection.class);

    /** The logo bitstream */
    private Bitstream logo;

    /** Our Handle */
    private String handle;

    /**
     * Groups corresponding to workflow steps - NOTE these start from one, so
     * workflowGroups[0] corresponds to workflow_step_1.
     */
    private Group[] workflowGroup;

    /** The default group of submitters */
    private Group submitters;

    /** The default group of administrators */
    private Group admins;

    /**
     * Construct a collection with the given table row
     * 
     * @param context
     *            the context this object exists in
     * @param row
     *            the corresponding row in the table
     * @throws SQLException
     */
    Collection(Context context, TableRow row) throws SQLException  {
        this.context = context;
        tableRow = row;

        // Get the logo bitstream
        if (tableRow.isColumnNull("logo_bitstream_id")) {
            logo = null;
        } else {
            logo = Bitstream.find(context, tableRow.getIntColumn("logo_bitstream_id"));
        }

        // Get the relevant groups
        workflowGroup = new Group[3];

        workflowGroup[0] = groupFromColumn("workflow_step_1");
        workflowGroup[1] = groupFromColumn("workflow_step_2");
        workflowGroup[2] = groupFromColumn("workflow_step_3");

        submitters = groupFromColumn("submitter");
        admins = groupFromColumn("admin");
        
        // Get our Handle if any
        handle = HandleManager.findHandle(context, this);

        // Cache ourselves
        context.cache(this, row.getIntColumn("collection_id"));
    }

    /**
     * Get a collection from the database. Loads in the metadata
     * 
     * @param context
     *            DSpace context object
     * @param id
     *            ID of the collection
     * 
     * @return the collection, or null if the ID is invalid.
     * @throws SQLException
     */
    public static Collection find(Context context, int id) throws SQLException  {
        // First check the cache
        Collection fromCache = (Collection) context.fromCache(Collection.class, id);

        if (fromCache != null)  {
            return fromCache;
        }

        TableRow row = DatabaseManager.find(context, "collection", id);

        if (row == null) {
            log.debug(LogManager.getHeader(context, "find_collection",
                        "not_found,collection_id=" + id));
            return null;
        }

        // not null, return Collection
        log.debug(LogManager.getHeader(context, "find_collection",
                                       "collection_id=" + id));

        return new Collection(context, row);
    }

    /**
     * Create a new collection, with a new ID. This method is not public, and
     * does not check authorisation.
     * 
     * @param context
     *            DSpace context object
     * 
     * @return the newly created collection
     * @throws SQLException
     * @throws AuthorizeException
     */
    static Collection create(Context context) throws SQLException,
            AuthorizeException {
        return create(context, null);
    }

    /**
     * Create a new collection, with a new ID. This method is not public, and
     * does not check authorisation.
     *
     * @param context
     *            DSpace context object
     *
     * @param handle the pre-determined Handle to assign to the new community
     * @return the newly created collection
     * @throws SQLException
     * @throws AuthorizeException
     */
    static Collection create(Context context, String handle) throws SQLException,
            AuthorizeException {
        TableRow row = DatabaseManager.create(context, "collection");
        Collection c = new Collection(context, row);
        c.createDSO();

        try {
            c.handle = (handle == null) ?
                       HandleManager.createHandle(context, c) :
                       HandleManager.createHandle(context, c, handle);
        } catch(IllegalStateException ie) {
            //If an IllegalStateException is thrown, then an existing object is already using this handle
            //Remove the collection we just created -- as it is incomplete
            try {
                if(c!=null) {
                    c.delete();
                }
            } catch(Exception e) { }

            //pass exception on up the chain
            throw ie;
        }

        // create the default authorization policy for collections
        // of 'anonymous' READ
        Group anonymousGroup = Group.find(context, 0);

        ResourcePolicy myPolicy = ResourcePolicy.create(context);
        myPolicy.setResource(c);
        myPolicy.setAction(Constants.READ);
        myPolicy.setGroup(anonymousGroup);
        myPolicy.update();

        // now create the default policies for submitted items
        myPolicy = ResourcePolicy.create(context);
        myPolicy.setResource(c);
        myPolicy.setAction(Constants.DEFAULT_ITEM_READ);
        myPolicy.setGroup(anonymousGroup);
        myPolicy.update();

        myPolicy = ResourcePolicy.create(context);
        myPolicy.setResource(c);
        myPolicy.setAction(Constants.DEFAULT_BITSTREAM_READ);
        myPolicy.setGroup(anonymousGroup);
        myPolicy.update();

        context.addEvent(new Event(Event.CREATE, Constants.COLLECTION, c.getID(), c.handle));

        log.info(LogManager.getHeader(context, "create_collection",
                "collection_id=" + row.getIntColumn("collection_id"))
                + ",handle=" + c.handle);

        return c;
    }

    /**
     * Get all collections in the system. These are alphabetically sorted by
     * collection name.
     * 
     * @param context
     *            DSpace context object
     * 
     * @return the collections in the system
     * @throws SQLException
     */
    public static BoundedIterator<Collection> findAll(Context context) throws SQLException {
        TableRowIterator tri = DatabaseManager.queryTable(context, "collection",
                "SELECT * FROM collection ORDER BY name");
        return new BoundedIterator<Collection>(context, tri);
    }

    /**
     * Get the in_archive items in this collection. The order is indeterminate.
     * 
     * @return an iterator over the items in the collection.
     * @throws SQLException
     */
    public BoundedIterator<Item> getItems() throws SQLException {
        String myQuery = "SELECT item.* FROM item, collection2item WHERE "
                + "item.item_id=collection2item.item_id AND "
                + "collection2item.collection_id= ? "
                + "AND item.in_archive='1'";

        TableRowIterator rows = DatabaseManager.queryTable(context, "item",
                myQuery,getID());

        return new BoundedIterator<Item>(context, rows);
    }

    /**
     * Get all the items in this collection. The order is indeterminate.
     * 
     * @return an iterator over the items in the collection.
     * @throws SQLException
     */
    public BoundedIterator<Item> getAllItems() throws SQLException {
        String myQuery = "SELECT item.* FROM item, collection2item WHERE "
                + "item.item_id=collection2item.item_id AND "
                + "collection2item.collection_id= ? ";

        TableRowIterator rows = DatabaseManager.queryTable(context, "item",
                myQuery,getID());

        return new BoundedIterator<Item>(context, rows);
    }

     /**
     * Get the internal ID of this collection
     * 
     * @return the internal identifier
     */
    @Override
    public int getID() {
        return tableRow.getIntColumn("collection_id");
    }
    
    @Override
    public String getName()
    {
    	return getMetadataValue("dsl.name");
    }
    
    /**
     * Set the name of the collection
     * 
     * @param name
     *            string name of the collection
     */
    public void setName(String name) {
        tableRow.setColumn("name", name);
    }
    
    /**
     * @see org.dspace.content.DSpaceObject#getHandle()
     */
    public String getHandle() {
        if (handle == null) {
        	try {
				handle = HandleManager.findHandle(this.context, this);
			} catch (SQLException e) {
				// TODO Auto-generated catch block
				//e.printStackTrace();
			}
        }
    	return handle;
    }

    /**
     * Get the logo for the collection. <code>null</code> is return if the
     * collection does not have a logo.
     * 
     * @return the logo of the collection, or <code>null</code>
     */
    public Bitstream getLogo() {
        return logo;
    }

    /**
     * Give the collection a logo. Passing in <code>null</code> removes any
     * existing logo. You will need to set the format of the new logo bitstream
     * before it will work, for example to "JPEG". Note that
     * <code>update(/code> will need to be called for the change to take
     * effect.  Setting a logo and not calling <code>update</code> later may
     * result in a previous logo lying around as an "orphaned" bitstream.
     *
     * @param  is   the stream to use as the new logo
     *
     * @return   the new logo bitstream, or <code>null</code> if there is no
     *           logo (<code>null</code> was passed in)
     * @throws AuthorizeException
     * @throws IOException
     * @throws SQLException
     */
    public Bitstream setLogo(InputStream is) throws AuthorizeException,
            IOException, SQLException {
        // Check authorisation
        // authorized to remove the logo when DELETE rights
        // authorized when canEdit
        if (!((is == null) && AuthorizeManager.authorizeActionBoolean(
                context, this, Constants.DELETE))) {
            canEdit(true);
        }

        // First, delete any existing logo
        if (!tableRow.isColumnNull("logo_bitstream_id")) {
            logo.delete();
        }

        if (is == null) {
            tableRow.setColumnNull("logo_bitstream_id");
            logo = null;

            log.info(LogManager.getHeader(context, "remove_logo",
                    "collection_id=" + getID()));
        } else {
            Bitstream newLogo = Bitstream.create(context, is);
            tableRow.setColumn("logo_bitstream_id", newLogo.getID());
            logo = newLogo;

            // now create policy for logo bitstream
            // to match our READ policy
            List<ResourcePolicy> policies = AuthorizeManager.getPoliciesActionFilter(context, this, Constants.READ);
            AuthorizeManager.addPolicies(context, policies, newLogo);

            log.info(LogManager.getHeader(context, "set_logo",
                    "collection_id=" + getID() + "logo_bitstream_id="
                            + newLogo.getID()));
        }

        modified = true;
        return logo;
    }

    /**
     * Create a workflow group for the given step if one does not already exist.
     * Returns either the newly created group or the previously existing one.
     * Note that while the new group is created in the database, the association
     * between the group and the collection is not written until
     * <code>update</code> is called.
     * 
     * @param step
     *            the step (1-3) of the workflow to create or get the group for
     * 
     * @return the workflow group associated with this collection
     * @throws SQLException
     * @throws AuthorizeException
     */
    public Group createWorkflowGroup(int step) throws SQLException,
            AuthorizeException {
        // Check authorisation - Must be an Admin to create Workflow Group
        AuthorizeManager.authorizeManageWorkflowsGroup(context, this);

        if (workflowGroup[step - 1] == null) {
            //turn off authorization so that Collection Admins can create Collection Workflow Groups
            context.turnOffAuthorisationSystem();
            Group g = Group.create(context);
            context.restoreAuthSystemState();

            g.setName("COLLECTION_" + getID() + "_WORKFLOW_STEP_" + step);
            g.update();
            setWorkflowGroup(step, g);

            AuthorizeManager.addPolicy(context, this, Constants.ADD, g);
        }

        return workflowGroup[step - 1];
    }

    /**
     * Set the workflow group corresponding to a particular workflow step.
     * <code>null</code> can be passed in if there should be no associated
     * group for that workflow step; any existing group is NOT deleted.
     * 
     * @param step
     *            the workflow step (1-3)
     * @param g
     *            the new workflow group, or <code>null</code>
     */
    public void setWorkflowGroup(int step, Group g) {
        workflowGroup[step - 1] = g;

        if (g == null) {
            tableRow.setColumnNull("workflow_step_" + step);
        } else {
            tableRow.setColumn("workflow_step_" + step, g.getID());
        }
        modified = true;
    }

    /**
     * Get the the workflow group corresponding to a particular workflow step.
     * This returns <code>null</code> if there is no group associated with
     * this collection for the given step.
     * 
     * @param step
     *            the workflow step (1-3)
     * 
     * @return the group of reviewers or <code>null</code>
     */
    public Group getWorkflowGroup(int step) {
        return workflowGroup[step - 1];
    }

    /**
     * Create a default submitters group if one does not already exist. Returns
     * either the newly created group or the previously existing one. Note that
     * other groups may also be allowed to submit to this collection by the
     * authorization system.
     * 
     * @return the default group of submitters associated with this collection
     * @throws SQLException
     * @throws AuthorizeException
     */
    public Group createSubmitters() throws SQLException, AuthorizeException {
        // Check authorisation - Must be an Admin to create Submitters Group
        AuthorizeManager.authorizeManageSubmittersGroup(context, this);

        if (submitters == null) {
            //turn off authorization so that Collection Admins can create Collection Submitters
            context.turnOffAuthorisationSystem();
            submitters = Group.create(context);
            context.restoreAuthSystemState();

            submitters.setName("COLLECTION_" + getID() + "_SUBMIT");
            submitters.update();
        }

        // register this as the submitter group
        tableRow.setColumn("submitter", submitters.getID());
        
        AuthorizeManager.addPolicy(context, this, Constants.ADD, submitters);

        modified = true;
        return submitters;
    }

    /**
     * Remove the submitters group, if no group has already been created 
     * then return without error. This will merely dereference the current 
     * submitters group from the collection so that it may be deleted 
     * without violating database constraints.
     */
    public void removeSubmitters() throws SQLException, AuthorizeException {
    	// Check authorisation - Must be an Admin to delete Submitters Group
        AuthorizeManager.authorizeManageSubmittersGroup(context, this);

        // just return if there is no administrative group.
        if (submitters == null) {
            return;
        }

        // Remove the link to the collection table.
        tableRow.setColumnNull("submitter");
        submitters = null;
       
        modified = true;
    }

    
    /**
     * Get the default group of submitters, if there is one. Note that the
     * authorization system may allow others to submit to the collection, so
     * this is not necessarily a definitive list of potential submitters.
     * <P>
     * The default group of submitters for collection 100 is the one called
     * <code>collection_100_submit</code>.
     * 
     * @return the default group of submitters, or <code>null</code> if there
     *         is no default group.
     */
    public Group getSubmitters() {
        return submitters;
    }

    /**
     * Create a default administrators group if one does not already exist.
     * Returns either the newly created group or the previously existing one.
     * Note that other groups may also be administrators.
     * 
     * @return the default group of editors associated with this collection
     * @throws SQLException
     * @throws AuthorizeException
     */
    public Group createAdministrators() throws SQLException, AuthorizeException {
        // Check authorisation - Must be an Admin to create more Admins
        AuthorizeManager.authorizeManageAdminGroup(context, this);

        if (admins == null)  {
            //turn off authorization so that Community Admins can create Collection Admins
            context.turnOffAuthorisationSystem();
            admins = Group.create(context);
            context.restoreAuthSystemState();
            
            admins.setName("COLLECTION_" + getID() + "_ADMIN");
            admins.update();
        }

        AuthorizeManager.addPolicy(context, this,
                Constants.ADMIN, admins);

        // register this as the admin group
        tableRow.setColumn("admin", admins.getID());
        
        modified = true;
        return admins;
    }
    
    /**
     * Remove the administrators group, if no group has already been created 
     * then return without error. This will merely dereference the current 
     * administrators group from the collection so that it may be deleted 
     * without violating database constraints.
     */
    public void removeAdministrators() throws SQLException, AuthorizeException {
        // Check authorisation - Must be an Admin of the parent community to delete Admin Group
        AuthorizeManager.authorizeRemoveAdminGroup(context, this);

        // just return if there is no administrative group.
        if (admins == null) {
            return;
        }

        // Remove the link to the collection table.
        tableRow.setColumnNull("admin");
        admins = null;
       
        modified = true;
    }

    /**
     * Get the default group of administrators, if there is one. Note that the
     * authorization system may allow others to be administrators for the
     * collection.
     * <P>
     * The default group of administrators for collection 100 is the one called
     * <code>collection_100_admin</code>.
     * 
     * @return group of administrators, or <code>null</code> if there is no
     *         default group.
     */
    public Group getAdministrators() {
        return admins;
    }

    /**
     * Get the license that users must grant before submitting to this
     * collection. If the collection does not have a specific license, the
     * site-wide default is returned.
     * 
     * @return the license for this collection
     */
    public String getLicense() {
        String license = getMetadataValue("dsl.license");

        if (license == null || license.trim().equals("")) {
            // Fallback to site-wide default
            license = ConfigurationManager.getDefaultSubmissionLicense();
        }

        return license;
    }

    /**
     * Get the license that users must grant before submitting to this
     * collection. 
     * 
     * @return the license for this collection
     */
    public String getLicenseCollection() {
        return getMetadataValue("dsl.license");
    }

    /**
     * Find out if the collection has a custom license
     * 
     * @return <code>true</code> if the collection has a custom license
     */
    public boolean hasCustomLicense() {
        String license = getMetadataValue("dsl.license");
        return !( license == null || license.trim().equals("") );
    }

    /**
     * Set the license for this collection. Passing in <code>null</code> means
     * that the site-wide default will be used.
     * 
     * @param license
     *            the license, or <code>null</code>
     */
    public void setLicense(String license) throws AuthorizeException, SQLException {
        setMetadataValue("dsl.license", license);
    }

    /**
     * Add an item to the collection. This simply adds a relationship between
     * the item and the collection - it does nothing like set an issue date,
     * remove a personal workspace item etc. This has instant effect;
     * <code>update</code> need not be called.
     * 
     * @param item
     *            item to add
     * @throws SQLException
     * @throws AuthorizeException
     */
    public void addItem(Item item) throws SQLException, AuthorizeException {
        // Check authorisation
        AuthorizeManager.authorizeAction(context, this, Constants.ADD);

        log.info(LogManager.getHeader(context, "add_item", "collection_id="
                + getID() + ",item_id=" + item.getID()));

        // Create mapping
        TableRow row = DatabaseManager.row("collection2item");

        row.setColumn("collection_id", getID());
        row.setColumn("item_id", item.getID());

        DatabaseManager.insert(context, row);

        context.addEvent(new Event(Event.ADD, Constants.COLLECTION, getID(), Constants.ITEM, item.getID(), item.getHandle()));
    }

    /**
     * Remove an item. If the item is then orphaned, it is deleted.
     * 
     * @param item
     *            item to remove
     * @throws SQLException
     * @throws AuthorizeException
     * @throws IOException
     */
    public void removeItem(Item item) throws SQLException, AuthorizeException,
            IOException  {
        // Check authorisation
        AuthorizeManager.authorizeAction(context, this, Constants.REMOVE);

        // will be the item an orphan?
        TableRow row = DatabaseManager.querySingle(context,
                "SELECT COUNT(DISTINCT collection_id) AS num FROM collection2item WHERE item_id= ? ",
                item.getID());

        DatabaseManager.setConstraintDeferred(context, "coll2item_item_fk");
        if (row.getLongColumn("num") == 1) {
            // Orphan; delete it
            item.delete();
        }
        log.info(LogManager.getHeader(context, "remove_item",
                "collection_id=" + getID() + ",item_id=" + item.getID()));
    
        DatabaseManager.updateQuery(context,
                "DELETE FROM collection2item WHERE collection_id= ? "+
                "AND item_id= ? ",
                getID(), item.getID());
        DatabaseManager.setConstraintImmediate(context, "coll2item_item_fk");
        
        context.addEvent(new Event(Event.REMOVE, Constants.COLLECTION, getID(), Constants.ITEM, item.getID(), item.getHandle()));
    }

    /**
     * Update the collection metadata (including logo, and workflow groups) to
     * the database. Inserts if this is a new collection.
     * 
     * @throws SQLException
     * @throws IOException
     * @throws AuthorizeException
     */
    public void update() throws SQLException, IOException, AuthorizeException {
        // Check authorisation
        canEdit(true);
        log.info(LogManager.getHeader(context, "update_collection", "collection_id=" + getID()));
        updateDSO();
    }
    
    public boolean canEditBoolean() throws java.sql.SQLException  {
        return canEditBoolean(true);
    }

    public boolean canEditBoolean(boolean useInheritance) throws SQLException {
        try {
            canEdit(useInheritance);
            return true;
        } catch (AuthorizeException e) {
            return false;
        }
    }

    public void canEdit()  throws AuthorizeException, SQLException {
        canEdit(true);
    }
    
    public void canEdit(boolean useInheritance) throws AuthorizeException, SQLException  {
        for (Community parent : getCommunities()) {
            if (AuthorizeManager.authorizeActionBoolean(context, parent,
                    Constants.WRITE, useInheritance)) {
                return;
            }

            if (AuthorizeManager.authorizeActionBoolean(context, parent,
                    Constants.ADD, useInheritance)) {
                return;
            }
        }

        AuthorizeManager.authorizeAction(context, this, Constants.WRITE, useInheritance);
    }

    /**
     * Delete the collection, including the metadata and logo. Items that are
     * then orphans are deleted. Groups associated with this collection
     * (workflow participants and submitters) are NOT deleted.
     * 
     * @throws SQLException
     * @throws AuthorizeException
     * @throws IOException
     */
    void delete() throws SQLException, AuthorizeException, IOException {
        log.info(LogManager.getHeader(context, "delete_collection",
                "collection_id=" + getID()));

        context.addEvent(new Event(Event.DELETE, Constants.COLLECTION, getID(), getHandle()));

        // Remove from cache
        context.removeCached(this, getID());

        // remove subscriptions - hmm, should this be in Subscription.java?
        DatabaseManager.updateQuery(context,
                "DELETE FROM subscription WHERE collection_id= ? ", 
                getID());
        
        // Remove items
        BoundedIterator<Item> items = getAllItems();

        try {
        	while (items.hasNext()) {
        		Item item = items.next();
        		IndexBrowse ib = new IndexBrowse(context);
        		
        		if (item.isOwningCollection(this))	{
        			// the collection to be deletd is the owning collection, thus remove
        			// the item from all collections it belongs to
        			for (Collection collection : item.getCollections()) {
        				//notify Browse of removing item.
        				ib.itemRemoved(item);
        				// Browse.itemRemoved(context, itemId);
        				collection.removeItem(item);
        			}
        			
        		} 
        		// the item was only mapped to this collection, so just remove it
        		else {
        			//notify Browse of removing item mapping. 
        			ib.indexItem(item);
        			// Browse.itemChanged(context, item);
        			removeItem(item);
        		}
        	}
        } catch (BrowseException e) {
        	log.error("caught exception: ", e);
        	throw new IOException(e.getMessage(), e);
        } finally {
            if (items != null) {
                items.close();
            }
        }
        
        // Delete the metadata
        deleteMetadata();

        // Delete bitstream logo
        setLogo(null);

        // Remove all authorization policies
        AuthorizeManager.removeAllPolicies(context, this);

        /*
        if(ConfigurationManager.getProperty("workflow","workflow.framework").equals("xmlworkflow")){
            // Remove any xml_WorkflowItems
            XmlWorkflowItem[] xmlWfarray = XmlWorkflowItem
                    .findByCollection(context, this);

            for (XmlWorkflowItem aXmlWfarray : xmlWfarray) {
                // remove the workflowitem first, then the item
                Item myItem = aXmlWfarray.getItem();
                aXmlWfarray.deleteWrapper();
                myItem.delete();
            }
        }else{
        */
            // Remove any WorkflowItems
            WorkflowItem[] wfarray = WorkflowItem
                    .findByCollection(context, this);

            for (WorkflowItem aWfarray : wfarray) {
                // remove the workflowitem first, then the item
                Item myItem = aWfarray.getItem();
                aWfarray.deleteWrapper();
                myItem.delete();
            }
        // }

        // Remove any WorkspaceItems
        WorkspaceItem[] wsarray = WorkspaceItem.findByCollection(context,
                this);

        for (WorkspaceItem aWsarray : wsarray) {
            aWsarray.deleteAll();
        }

        //  get rid of the content count cache if it exists
        try {
        	ItemCounter ic = new ItemCounter(context);
        	ic.remove(this);
        } catch (ItemCountException e) {
        	// FIXME: upside down exception handling due to lack of good
        	// exception framework
        	throw new IllegalStateException(e.getMessage(), e);
        }

        // Remove any Handle
        HandleManager.unbindHandle(context, this);
        
        // Destroy DSO info
        destroyDSO();

        /*
        if(ConfigurationManager.getProperty("workflow","workflow.framework").equals("xmlworkflow")){
            // delete all CollectionRoles for this Collection
            for (CollectionRole collectionRole : CollectionRole.findByCollection(context, this.getID())) {
                collectionRole.delete();
            }
        }
        */

        // Delete collection row
        DatabaseManager.delete(context, tableRow);

        // Remove any workflow groups - must happen after deleting collection
        Group g = null;

        g = getWorkflowGroup(1);

        if (g != null) {
            g.delete();
        }

        g = getWorkflowGroup(2);

        if (g != null) {
            g.delete();
        }

        g = getWorkflowGroup(3);

        if (g != null) {
            g.delete();
        }

        // Remove default administrators group
        g = getAdministrators();

        if (g != null) {
            g.delete();
        }

        // Remove default submitters group
        g = getSubmitters();

        if (g != null) {
            g.delete();
        }
    }

    /**
     * Get the communities this collection appears in
     * 
     * @return array of <code>Community</code> objects
     * @throws SQLException
     */
    public List<Community> getCommunities() throws SQLException {
        // Get the bundle table rows
        TableRowIterator tri = DatabaseManager.queryTable(context,"community",
                        "SELECT community.* FROM community, community2collection WHERE " +
                        "community.community_id=community2collection.community_id " +
                        "AND community2collection.collection_id= ? ",
                        getID());

        // Build a list of Community objects
        List<Community> communities = new ArrayList<Community>();

        try {
            while (tri.hasNext()) {
                TableRow row = tri.next();

                // First check the cache
                Community owner = (Community) context.fromCache(Community.class,
                        row.getIntColumn("community_id"));

                if (owner == null) {
                    owner = new Community(context, row);
                }

                communities.add(owner);

                // now add any parent communities
                communities.addAll(owner.getAllParents());
            }
        } finally {
            // close the TableRowIterator to free up resources
            if (tri != null) {
                tri.close();
            }
        }

        return communities;
    }

    /**
     * Return <code>true</code> if <code>other</code> is the same Collection
     * as this object, <code>false</code> otherwise
     * 
     * @param other
     *            object to compare to
     * 
     * @return <code>true</code> if object passed in represents the same
     *         collection as this object
     */
     @Override
     public boolean equals(Object other) {
         if (other == null) {
             return false;
         }
         if (getClass() != other.getClass()) {
             return false;
         }
         final Collection otherCollection = (Collection) other;
         if (this.getID() != otherCollection.getID()) {
             return false;
         }
         return true;
     }

     @Override
     public int hashCode() {
         int hash = 7;
         hash = 89 * hash + (this.tableRow != null ? this.tableRow.hashCode() : 0);
         return hash;
     }

    /**
     * Utility method for reading in a group from a group ID in a column. If the
     * column is null, null is returned.
     * 
     * @param col
     *            the column name to read
     * @return the group referred to by that column, or null
     * @throws SQLException
     */
    private Group groupFromColumn(String col) throws SQLException  {
        if (tableRow.isColumnNull(col)) {
            return null;
        }
        return Group.find(context, tableRow.getIntColumn(col));
    }

    /**
     * return type found in Constants
     * 
     * @return int Constants.COLLECTION
     */
    @Override
    public int getType() {
        return Constants.COLLECTION;
    }

    /**
     * return an array of collections that user has a given permission on
     * (useful for trimming 'select to collection' list) or figuring out which
     * collections a person is an editor for.
     * 
     * @param context
     * @param comm
     *            (optional) restrict search to a community, else null
     * @param actionID
     *            fo the action
     * 
     * @return Collection [] of collections with matching permissions
     * @throws SQLException
     */
    public static List<Collection> findAuthorized(Context context, Community comm,
            int actionID) throws java.sql.SQLException  {
        List<Collection> myResults = new ArrayList<Collection>();
        BoundedIterator<Collection> colIter = (comm != null) ? comm.getCollections() : Collection.findAll(context);
        while(colIter.hasNext()) {
            Collection c = colIter.next();
            if (AuthorizeManager.authorizeActionBoolean(context, c, actionID)) {
            	myResults.add(c);
            }
        }
        colIter.close();
        return myResults;
    }

	/**
     * counts items in this collection
     *
     * @return  total items
     */
     public int countItems()
        throws SQLException {
         int itemcount = 0;
         PreparedStatement statement = null;
         ResultSet rs = null;

         try {
             String query = "SELECT count(*) FROM collection2item, item WHERE "
                    + "collection2item.collection_id =  ? "
                    + "AND collection2item.item_id = item.item_id "
                    + "AND in_archive ='1' AND item.withdrawn='0' ";

            statement = context.getDBConnection().prepareStatement(query);
            statement.setInt(1,getID());

            rs = statement.executeQuery();
            if (rs != null) {
                rs.next();
                itemcount = rs.getInt(1);
            }
         } finally  {
             if (rs != null) {
                 try { rs.close(); } catch (SQLException sqle) { }
             }

             if (statement != null) {
                 try { statement.close(); } catch (SQLException sqle) { }
             }
         }

        return itemcount;
     }
     
    public DSpaceObject getAdminObject(int action) throws SQLException {
        DSpaceObject adminObject = null;
        Community community = null;
        List<Community> communities = getCommunities();
        if (communities.size() > 0) {
            community = communities.get(0);
        }

        switch (action) {
        case Constants.REMOVE:
            if (AuthorizeConfiguration.canCollectionAdminPerformItemDeletion()) {
                adminObject = this;
            }
            else if (AuthorizeConfiguration.canCommunityAdminPerformItemDeletion()) {
                adminObject = community;
            }
            break;

        case Constants.DELETE:
            if (AuthorizeConfiguration.canCommunityAdminPerformSubelementDeletion()) {
                adminObject = community;
            }
            break;
        default:
            adminObject = this;
            break;
        }
        return adminObject;
    }
    
    @Override
    public DSpaceObject getParentObject() throws SQLException {
        List<Community> communities = getCommunities();
        return (communities.size() > 0) ? communities.get(0) : null;
    }
}
