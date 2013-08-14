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
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.StringTokenizer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Objects;

import org.joda.time.DateTimeZone;
import org.joda.time.format.ISODateTimeFormat;

import org.dspace.authorize.AuthorizeConfiguration;
import org.dspace.authorize.AuthorizeException;
import org.dspace.authorize.AuthorizeManager;
import org.dspace.authorize.ResourcePolicy;
import org.dspace.browse.BrowseException;
import org.dspace.browse.IndexBrowse;
import org.dspace.core.Constants;
import org.dspace.core.Context;
import org.dspace.core.LogManager;
import org.dspace.authority.Choices;
import org.dspace.authority.ChoiceAuthorityManager;
import org.dspace.authority.MetadataAuthorityManager;
import org.dspace.event.Event;
import org.dspace.eperson.EPerson;
import org.dspace.eperson.Group;
import org.dspace.handle.HandleManager;
import org.dspace.storage.rdbms.DatabaseManager;
import org.dspace.storage.rdbms.TableRow;
import org.dspace.storage.rdbms.TableRowIterator;

/**
 * Class representing an item in DSpace.
 * <P>
 * This class holds in memory the item Dublin Core metadata, the bundles in the
 * item, and the bitstreams in those bundles. When modifying the item, if you
 * modify the Dublin Core or the "in archive" flag, you must call
 * <code>update</code> for the changes to be written to the database.
 * Creating, adding or removing bundles or bitstreams has immediate effect in
 * the database.
 *
 * @author Robert Tansley
 * @author Martin Hald
 * @version $Revision: 6887 $
 */
public class Item extends DSpaceObject
{
    /** log4j category */
    private static final Logger log = LoggerFactory.getLogger(Item.class);

    /** The bundles in this item - kept in sync with DB */
    private List<Bundle> bundles;

    /** Handle, if any */
    private String handle;

    /**
     * Construct an item with the given table row
     *
     * @param context
     *            the context this object exists in
     * @param row
     *            the corresponding row in the table
     * @throws SQLException
     */
    Item(Context context, TableRow row) throws SQLException {
        this.context = context;
        tableRow = row;

        // Get our Handle if any
        handle = HandleManager.findHandle(context, this);

        // Cache ourselves
        context.cache(this, row.getIntColumn("item_id"));
    }

    /**
     * Get an item from the database. The item, its Dublin Core metadata, and
     * the bundle and bitstream metadata are all loaded into memory.
     *
     * @param context
     *            DSpace context object
     * @param id
     *            Internal ID of the item
     * @return the item, or null if the internal ID is invalid.
     * @throws SQLException
     */
    public static Item find(Context context, int id) throws SQLException {
        // First check the cache
        Item fromCache = (Item) context.fromCache(Item.class, id);

        if (fromCache != null) {
            return fromCache;
        }

        TableRow row = DatabaseManager.find(context, "item", id);

        if (row == null) {
            log.debug(LogManager.getHeader(context, "find_item",
                                   "not_found,item_id=" + id));
            return null;
        }

        // not null, return item
        log.debug(LogManager.getHeader(context, "find_item", "item_id=" + id));

        return new Item(context, row);
    }

    /**
     * Create a new item, with a new internal ID. This method is not public,
     * since items need to be created as workspace items. Authorisation is the
     * responsibility of the caller.
     *
     * @param context
     *            DSpace context object
     * @return the newly created item
     * @throws SQLException
     * @throws AuthorizeException
     */
    static Item create(Context context) throws SQLException, AuthorizeException {
        TableRow row = DatabaseManager.create(context, "item");
        Item i = new Item(context, row);
        i.createDSO();

        // Call update to give the item a last modified date. OK this isn't
        // amazingly efficient but creates don't happen that often.
        context.turnOffAuthorisationSystem();
        i.update();
        context.restoreAuthSystemState();

        context.addEvent(new Event(Event.CREATE, Constants.ITEM, i.getID(), null));

        log.info(LogManager.getHeader(context, "create_item", "item_id="
                + row.getIntColumn("item_id")));

        return i;
    }

    /**
     * Get all the items in the archive. Only items with the "in archive" flag
     * set are included. The order of the list is indeterminate.
     *
     * @param context
     *            DSpace context object
     * @return an iterator over the items in the archive.
     * @throws SQLException
     */
    public static BoundedIterator<Item> findAll(Context context) throws SQLException {
        String myQuery = "SELECT * FROM item WHERE in_archive='1'";
        TableRowIterator rows = DatabaseManager.queryTable(context, "item", myQuery);
        return new BoundedIterator<Item>(context, rows);
    }

    /**
     * Get the internal ID of this item. In general, this shouldn't be exposed
     * to users
     *
     * @return the internal identifier
     */
    @Override
    public int getID() {
        return tableRow.getIntColumn("item_id");
    }
    
    /**
     * @see org.dspace.content.DSpaceObject#getHandle()
     */
    @Override
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
     * Find out if the item is part of the main archive
     *
     * @return true if the item is in the main archive
     */
    public boolean isArchived() {
        return tableRow.getBooleanColumn("in_archive");
    }

    /**
     * Find out if the item has been withdrawn
     *
     * @return true if the item has been withdrawn
     */
    public boolean isWithdrawn() {
        return tableRow.getBooleanColumn("withdrawn");
    }

    /**
     * Get the date the item was last modified, or the current date if
     * last_modified is null
     *
     * @return the date the item was last modified, or the current date if the
     *         column is null.
     */
    public Date getLastModified() {
        Date myDate = tableRow.getDateColumn("last_modified");

        if (myDate == null) {
            myDate = new Date();
        }

        return myDate;
    }

    /**
     * Method that updates the last modified date of the item
     * The modified boolean will be set to true and the actual date update will occur on item.update().
     */
    void updateLastModified() {
        modified = true;
    }

    /**
     * Set the "is_archived" flag. This is public and only
     * <code>WorkflowItem.archive()</code> should set this.
     *
     * @param isArchived
     *            new value for the flag
     */
    public void setArchived(boolean isArchived) {
        tableRow.setColumn("in_archive", isArchived);
        modified = true;
    }

    /**
     * Set the owning Collection for the item
     *
     * @param c
     *            Collection
     */
    public void setOwningCollection(Collection c) {
        tableRow.setColumn("owning_collection", c.getID());
        modified = true;
    }

    /**
     * Get the owning Collection for the item
     *
     * @return Collection that is the owner of the item
     * @throws SQLException
     */
    public Collection getOwningCollection() throws java.sql.SQLException {
        Collection myCollection = null;

        // get the collection ID
        int cid = tableRow.getIntColumn("owning_collection");

        myCollection = Collection.find(context, cid);

        return myCollection;
    }

        // just get the collection ID for internal use
    private int getOwningCollectionID() {
        return tableRow.getIntColumn("owning_collection");
    }

    /**
     * See whether this Item is contained by a given Collection.
     * @param collection
     * @return true if {@code collection} contains this Item.
     * @throws SQLException
     */
    public boolean isIn(Collection collection) throws SQLException {
        TableRow tr = DatabaseManager.querySingle(context,
                "SELECT COUNT(*) AS count" +
                " FROM collection2item" +
                " WHERE collection_id = ? AND item_id = ?",
                collection.getID(), tableRow.getIntColumn("item_id"));
        return tr.getLongColumn("count") > 0;
    }

    /**
     * Get the collections this item is in. The order is indeterminate.
     *
     * @return the collections this item is in, if any.
     * @throws SQLException
     */
    public List<Collection> getCollections() throws SQLException {
        List<Collection> collections = new ArrayList<Collection>();

        // Get collection table rows
        TableRowIterator tri = DatabaseManager.queryTable(context,"collection",
                        "SELECT collection.* FROM collection, collection2item WHERE " +
                        "collection2item.collection_id=collection.collection_id AND " +
                        "collection2item.item_id= ? ",
                        tableRow.getIntColumn("item_id"));

        try {
            while (tri.hasNext()) {
                TableRow row = tri.next();

                // First check the cache
                Collection fromCache = (Collection) context.fromCache(
                        Collection.class, row.getIntColumn("collection_id"));

                if (fromCache != null) {
                    collections.add(fromCache);
                } else {
                    collections.add(new Collection(context, row));
                }
            }
        } finally {
            // close the TableRowIterator to free up resources
            if (tri != null) {
                tri.close();
            }
        }
        return collections;
    }

    /**
     * Get the communities this item is in. Returns an unordered array of the
     * communities that house the collections this item is in, including parent
     * communities of the owning collections.
     *
     * @return the communities this item is in.
     * @throws SQLException
     */
    public List<Community> getCommunities() throws SQLException {
        List<Community> communities = new ArrayList<Community>();

        // Get community table rows
        TableRowIterator tri = DatabaseManager.queryTable(context,"community",
                        "SELECT community.* FROM community, community2item " +
                        "WHERE community2item.community_id=community.community_id " +
                        "AND community2item.item_id= ? ",
                        tableRow.getIntColumn("item_id"));

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
     * Get the bundles in this item.
     *
     * @return the bundles in an unordered array
     */
    public List<Bundle> getBundles() throws SQLException {
        if (bundles == null) {
            bundles = new ArrayList<Bundle>();
            // Get bundles
            TableRowIterator tri = DatabaseManager.queryTable(context, "bundle",
                                    "SELECT bundle.* FROM bundle, item2bundle WHERE " +
                                    "item2bundle.bundle_id=bundle.bundle_id AND " +
                                    "item2bundle.item_id= ? ",
                                     tableRow.getIntColumn("item_id"));

            try {
                while (tri.hasNext()) {
                    TableRow r = tri.next();

                    // First check the cache
                    Bundle fromCache = (Bundle) context.fromCache(Bundle.class,
                                                r.getIntColumn("bundle_id"));

                    if (fromCache != null) {
                        bundles.add(fromCache);
                    } else {
                        bundles.add(new Bundle(context, r));
                    }
                }
            } finally {
                // close the TableRowIterator to free up resources
                if (tri != null) {
                    tri.close();
                }
            }
        }
        return bundles;
    }

    /**
     * Get the bundles matching a bundle name (name corresponds roughly to type)
     *
     * @param name
     *            name of bundle (ORIGINAL/TEXT/THUMBNAIL)
     *
     * @return the bundles in an unordered array
     */
    public List<Bundle> getBundles(String name) throws SQLException  {
        List<Bundle> matchingBundles = new ArrayList<Bundle>();

        // now only keep bundles with matching names
        for (Bundle b : getBundles()) {
            if (name.equals(b.getName())) {
                matchingBundles.add(b);
            }
        }
        return matchingBundles;
    }

    /**
     * Create a bundle in this item, with immediate effect
     *
     * @param name
     *            bundle name (ORIGINAL/TEXT/THUMBNAIL)
     * @return the newly created bundle
     * @throws SQLException
     * @throws AuthorizeException
     */
    public Bundle createBundle(String name) throws SQLException,
            AuthorizeException {
        if ((name == null) || "".equals(name)) {
            throw new SQLException("Bundle must be created with non-null name");
        }

        // Check authorisation
        AuthorizeManager.authorizeAction(context, this, Constants.ADD);

        Bundle b = Bundle.create(context);
        b.setName(name);
        b.update();

        addBundle(b);

        return b;
    }

    /**
     * Add an existing bundle to this item. This has immediate effect.
     *
     * @param b
     *            the bundle to add
     * @throws SQLException
     * @throws AuthorizeException
     */
    public void addBundle(Bundle b) throws SQLException, AuthorizeException {
        // Check authorisation
        AuthorizeManager.authorizeAction(context, this, Constants.ADD);

        log.info(LogManager.getHeader(context, "add_bundle", "item_id="
                + getID() + ",bundle_id=" + b.getID()));

        // Check it's not already there
        for (Bundle bund : getBundles()) {
            if (b.getID() == bund.getID()) {
                // Bundle is already there; no change
                return;
            }
        }

        // now add authorization policies from owning item
        // hmm, not very "multiple-inclusion" friendly
        AuthorizeManager.inheritPolicies(context, this, b);

        // Add the bundle to in-memory list
        bundles.add(b);

        // Insert the mapping
        TableRow mappingRow = DatabaseManager.row("item2bundle");
        mappingRow.setColumn("item_id", getID());
        mappingRow.setColumn("bundle_id", b.getID());
        DatabaseManager.insert(context, mappingRow);

        context.addEvent(new Event(Event.ADD, Constants.ITEM, getID(), Constants.BUNDLE, b.getID(), b.getName()));
    }

    /**
     * Remove a bundle. This may result in the bundle being deleted, if the
     * bundle is orphaned.
     *
     * @param b
     *            the bundle to remove
     * @throws SQLException
     * @throws AuthorizeException
     * @throws IOException
     */
    public void removeBundle(Bundle b) throws SQLException, AuthorizeException,
            IOException {
        // Check authorisation
        AuthorizeManager.authorizeAction(context, this, Constants.REMOVE);

        log.info(LogManager.getHeader(context, "remove_bundle", "item_id="
                + getID() + ",bundle_id=" + b.getID()));

        // Remove from internal list of bundles
        for (Bundle bund : getBundles()) {
            if (b.getID() == bund.getID()) {
                // We've found the bundle to remove
                bundles.remove(bund);
                break;
            }
        }

        // Remove mapping from DB
        DatabaseManager.updateQuery(context,
                "DELETE FROM item2bundle WHERE item_id= ? " +
                "AND bundle_id= ? ",
                getID(), b.getID());

        context.addEvent(new Event(Event.REMOVE, Constants.ITEM, getID(), Constants.BUNDLE, b.getID(), b.getName()));

        // If the bundle is orphaned, it's removed
        TableRowIterator tri = DatabaseManager.query(context,
                "SELECT * FROM item2bundle WHERE bundle_id= ? ",
                b.getID());

        try {
            if (!tri.hasNext()) {
                //make the right to remove the bundle explicit because the implicit
                // relation
                //has been removed. This only has to concern the currentUser
                // because
                //he started the removal process and he will end it too.
                //also add right to remove from the bundle to remove it's
                // bitstreams.
                AuthorizeManager.addPolicy(context, b, Constants.DELETE,
                        context.getCurrentUser());
                AuthorizeManager.addPolicy(context, b, Constants.REMOVE,
                        context.getCurrentUser());

                // The bundle is an orphan, delete it
                b.delete();
            }
        } finally {
            // close the TableRowIterator to free up resources
            if (tri != null) {
                tri.close();
            }
        }
    }

    /**
     * Create a single bitstream in a new bundle. Provided as a convenience
     * method for the most common use.
     *
     * @param is
     *            the stream to create the new bitstream from
     * @param name
     *            is the name of the bundle (ORIGINAL, TEXT, THUMBNAIL)
     * @return Bitstream that is created
     * @throws AuthorizeException
     * @throws IOException
     * @throws SQLException
     */
    public Bitstream createSingleBitstream(InputStream is, String name)
            throws AuthorizeException, IOException, SQLException {
        // Authorisation is checked by methods below
        // Create a bundle
        Bundle bnd = createBundle(name);
        Bitstream bitstream = bnd.createBitstream(is);
        addBundle(bnd);

        // FIXME: Create permissions for new bundle + bitstream
        return bitstream;
    }

    /**
     * Convenience method, calls createSingleBitstream() with name "ORIGINAL"
     *
     * @param is
     *            InputStream
     * @return created bitstream
     * @throws AuthorizeException
     * @throws IOException
     * @throws SQLException
     */
    public Bitstream createSingleBitstream(InputStream is)
            throws AuthorizeException, IOException, SQLException {
        return createSingleBitstream(is, "ORIGINAL");
    }

    /**
     * Get all non-internal bitstreams in the item. This is mainly used for
     * auditing for provenance messages and adding format.* DC values. The order
     * is indeterminate.
     *
     * @return non-internal bitstreams.
     */
    public List<Bitstream> getNonInternalBitstreams() throws SQLException {
        List<Bitstream> bitstreamList = new ArrayList<Bitstream>();

        // Go through the bundles and bitstreams picking out ones which aren't
        // of internal formats
        for (Bundle bund : getBundles()) {
            for (Bitstream bitstream : bund.getBitstreams()) {
                if (!bitstream.getFormat().isInternal()) {
                    // Bitstream is not of an internal format
                    bitstreamList.add(bitstream);
                }
            }
        }
        return bitstreamList;
    }

    /**
     * Remove just the DSpace license from an item This is useful to update the
     * current DSpace license, in case the user must accept the DSpace license
     * again (either the item was rejected, or resumed after saving)
     * <p>
     * This method is used by the org.dspace.submit.step.LicenseStep class
     *
     * @throws SQLException
     * @throws AuthorizeException
     * @throws IOException
     */
    public void removeDSpaceLicense() throws SQLException, AuthorizeException,
            IOException   {
        // get all bundles with name "LICENSE" (these are the DSpace license
        // bundles)
        for (Bundle bundle : getBundles("LICENSE")) {
            // FIXME: probably serious troubles with Authorizations
            // fix by telling system not to check authorization?
            removeBundle(bundle);
        }
    }

    /**
     * Remove all licenses from an item - it was rejected
     *
     * @throws SQLException
     * @throws AuthorizeException
     * @throws IOException
     */
    public void removeLicenses() throws SQLException, AuthorizeException,
            IOException {
        // Find the License format
        BitstreamFormat bf = BitstreamFormat.findByShortDescription(context,
                "License");
        int licensetype = bf.getID();

        // search through bundles, looking for bitstream type license
        for (Bundle bundle : getBundles()) {
            boolean removethisbundle = false;
            for (Bitstream bs : bundle.getBitstreams()) {
                BitstreamFormat bft = bs.getFormat();
                if (bft.getID() == licensetype) {
                    removethisbundle = true;
                }
            }

            // probably serious troubles with Authorizations
            // fix by telling system not to check authorization?
            if (removethisbundle) {
                removeBundle(bundle);
            }
        }
    }

    /**
     * Update the item "in archive" flag and Dublin Core metadata in the
     * database
     *
     * @throws SQLException
     * @throws AuthorizeException
     */
    public void update() throws SQLException, AuthorizeException {
        // Check authorisation
        // only do write authorization if user is not an editor
        if (!canEdit()) {
            AuthorizeManager.authorizeAction(context, this, Constants.WRITE);
        }

        log.info(LogManager.getHeader(context, "update_item", "item_id=" + getID()));

        // Set sequence IDs for bitstreams in item
        int sequence = 0;
        List<Bundle> bundles = getBundles();
        // find the highest current sequence number
        for (Bundle bundle : bundles) {
            for (Bitstream bs : bundle.getBitstreams()) {
                if (bs.getSequenceID() > sequence) {
                    sequence = bs.getSequenceID();
                }
            }
        }

        // start sequencing bitstreams without sequence IDs
        sequence++;

        for (Bundle bundle : bundles) {
            for (Bitstream bs : bundle.getBitstreams()) {
                if (bs.getSequenceID() < 0) {
                    bs.setSequenceID(sequence);
                    sequence++;
                    bs.update();
                    modified = true;
                }
            }
        }

        if (modifiedMetadata || modified)
        {
            // Set the last modified date
            tableRow.setColumn("last_modified", new Date());

            // Make sure that withdrawn and in_archive are non-null
            if (tableRow.isColumnNull("in_archive"))
            {
                tableRow.setColumn("in_archive", false);
            }

            if (tableRow.isColumnNull("withdrawn"))
            {
                tableRow.setColumn("withdrawn", false);
            }
            
            updateDSO();
        }
    }

    private transient List<MetadataField> allMetadataFields = null;
    private MetadataField getMetadataField(MDValue mdv) throws SQLException, AuthorizeException
    {
        if (allMetadataFields == null) {
            allMetadataFields = MetadataField.findAll(context);
        }

        int schemaID = getMetadataSchemaID(mdv);
        for (MetadataField field : allMetadataFields) {
            if (field.getSchemaID() == schemaID &&
                Objects.equal(field.getElement(), mdv.getElement()) &&
                Objects.equal(field.getQualifier(), mdv.getQualifier())) {
                    return field;
            }
        }

        return null;
    }

    private int getMetadataSchemaID(MDValue mdv) throws SQLException {
        int schemaID;
        MetadataSchema schema = MetadataSchema.find(context, mdv.getSchema());
        if (schema == null)
        {
            schemaID = MetadataSchema.DC_SCHEMA_ID;
        }
        else
        {
            schemaID = schema.getSchemaID();
        }
        return schemaID;
    }

    /**
     * Withdraw the item from the archive. It is kept in place, and the content
     * and metadata are not deleted, but it is not publicly accessible.
     *
     * @throws SQLException
     * @throws AuthorizeException
     * @throws IOException
     */
    public void withdraw() throws SQLException, AuthorizeException, IOException  {
        // Check permission. User either has to have REMOVE on owning collection
        // or be COLLECTION_EDITOR of owning collection
        AuthorizeManager.authorizeWithdrawItem(context, this);

        String timestamp = ISODateTimeFormat.dateTimeNoMillis().withZone(DateTimeZone.UTC).print(System.currentTimeMillis());

        // Add suitable provenance - includes user, date, collections +
        // bitstream checksums
        EPerson e = context.getCurrentUser();

        // Build some provenance data while we're at it.
        StringBuilder prov = new StringBuilder();

        prov.append("Item withdrawn by ").append(e.getFullName()).append(" (")
                .append(e.getEmail()).append(") on ").append(timestamp).append("\n")
                .append("Item was in collections:\n");

        for (Collection coll : getCollections()) {
            prov.append(coll.getMetadata("name")).append(" (ID: ").append(coll.getID()).append(")\n");
        }

        // Set withdrawn flag. timestamp will be set; last_modified in update()
        tableRow.setColumn("withdrawn", true);

        // in_archive flag is now false
        tableRow.setColumn("in_archive", false);

        prov.append(InstallItem.getBitstreamProvenanceMessage(this));

        addMetadata("dc", "description", "provenance", "en", prov.toString());

        // Update item in DB
        update();

        context.addEvent(new Event(Event.MODIFY, Constants.ITEM, getID(), "WITHDRAW"));

        // and all of our authorization policies
        // FIXME: not very "multiple-inclusion" friendly
        AuthorizeManager.removeAllPolicies(context, this);

        // Write log
        log.info(LogManager.getHeader(context, "withdraw_item", "user="
                + e.getEmail() + ",item_id=" + getID()));
    }

    /**
     * Reinstate a withdrawn item
     *
     * @throws SQLException
     * @throws AuthorizeException
     * @throws IOException
     */
    public void reinstate() throws SQLException, AuthorizeException,
            IOException {
        // check authorization
        AuthorizeManager.authorizeReinstateItem(context, this);

        String timestamp = ISODateTimeFormat.dateTimeNoMillis().withZone(DateTimeZone.UTC).print(System.currentTimeMillis());

        // Check permission. User must have ADD on all collections.
        // Build some provenance data while we're at it.

        // Add suitable provenance - includes user, date, collections +
        // bitstream checksums
        EPerson e = context.getCurrentUser();
        StringBuilder prov = new StringBuilder();
        prov.append("Item reinstated by ").append(e.getFullName()).append(" (")
                .append(e.getEmail()).append(") on ").append(timestamp).append("\n")
                .append("Item was in collections:\n");

        List<Collection> colls = getCollections();
        for (Collection coll : colls) {
            prov.append(coll.getMetadata("name")).append(" (ID: ").append(coll.getID()).append(")\n");
        }
        
        // Clear withdrawn flag
        tableRow.setColumn("withdrawn", false);

        // in_archive flag is now true
        tableRow.setColumn("in_archive", true);

        // Add suitable provenance - includes user, date, collections +
        // bitstream checksums
        prov.append(InstallItem.getBitstreamProvenanceMessage(this));

        addMetadata("dc", "description", "provenance", "en", prov.toString());

        // Update item in DB
        update();

        context.addEvent(new Event(Event.MODIFY, Constants.ITEM, getID(), "REINSTATE"));

        // authorization policies
        if (colls.size() > 0)  {
            // FIXME: not multiple inclusion friendly - just apply access
            // policies from first collection
            // remove the item's policies and replace them with
            // the defaults from the collection
            inheritCollectionDefaultPolicies(colls.get(0));
        }

        // Write log
        log.info(LogManager.getHeader(context, "reinstate_item", "user="
                + e.getEmail() + ",item_id=" + getID()));
    }

    /**
     * Delete (expunge) the item. Bundles and bitstreams are also deleted if
     * they are not also included in another item. The Dublin Core metadata is
     * deleted.
     *
     * @throws SQLException
     * @throws AuthorizeException
     * @throws IOException
     */
    void delete() throws SQLException, AuthorizeException, IOException  {
        // Check authorisation here. If we don't, it may happen that we remove the
        // metadata but when getting to the point of removing the bundles we get an exception
        // leaving the database in an inconsistent state
        AuthorizeManager.authorizeAction(context, this, Constants.REMOVE);

        context.addEvent(new Event(Event.DELETE, Constants.ITEM, getID(), getHandle()));

        log.info(LogManager.getHeader(context, "delete_item", "item_id="
                + getID()));

        // Remove from cache
        context.removeCached(this, getID());

        // Remove from browse indices, if appropriate
        /** XXX FIXME
         ** Although all other Browse index updates are managed through
         ** Event consumers, removing an Item *must* be done *here* (inline)
         ** because otherwise, tables are left in an inconsistent state
         ** and the DB transaction will fail.
         ** Any fix would involve too much work on Browse code that
         ** is likely to be replaced soon anyway.   --lcs, Aug 2006
         **
         ** NB Do not check to see if the item is archived - withdrawn /
         ** non-archived items may still be tracked in some browse tables
         ** for administrative purposes, and these need to be removed.
         **/
//               FIXME: there is an exception handling problem here
        try  {
//               Remove from indices
            IndexBrowse ib = new IndexBrowse(context);
            ib.itemRemoved(this);
        } catch (BrowseException e) {
            log.error("caught exception: ", e);
            throw new SQLException(e.getMessage(), e);
        }

        // Delete the metadata
        deleteMetadata();

        // Remove bundles
        for (Bundle bundle : getBundles()) {
            removeBundle(bundle);
        }

        // remove all of our authorization policies
        AuthorizeManager.removeAllPolicies(context, this);
        
        // Remove any Handle
        HandleManager.unbindHandle(context, this);
        
        // Remove DSO info
        destroyDSO();
        
        // Finally remove item row
        DatabaseManager.delete(context, tableRow);
    }
    
    /**
     * Remove item and all its sub-structure from the context cache.
     * Useful in batch processes where a single context has a long,
     * multi-item lifespan
     */
    public void decache() throws SQLException
    {
        // Remove item and it's submitter from cache
        context.removeCached(this, getID());
        // Remove bundles & bitstreams from cache if they have been loaded
        if (bundles != null) {
            for (Bundle bundle : getBundles()) {
                context.removeCached(bundle, bundle.getID());
                for (Bitstream bitstream : bundle.getBitstreams()) {
                    context.removeCached(bitstream, bitstream.getID());
                }
            }
        }
    }

    /**
     * Return <code>true</code> if <code>other</code> is the same Item as
     * this object, <code>false</code> otherwise
     *
     * @param obj
     *            object to compare to
     * @return <code>true</code> if object passed in represents the same item
     *         as this object
     */
     @Override
     public boolean equals(Object obj)
     {
         if (obj == null)
         {
             return false;
         }
         if (getClass() != obj.getClass())
         {
             return false;
         }
         final Item other = (Item) obj;
         if (this.getType() != other.getType())
         {
             return false;
         }
         if (this.getID() != other.getID())
         {
             return false;
         }

         return true;
     }

     @Override
     public int hashCode()
     {
         int hash = 5;
         hash = 71 * hash + (this.tableRow != null ? this.tableRow.hashCode() : 0);
         return hash;
     }

    /**
     * Return true if this Collection 'owns' this item
     *
     * @param c
     *            Collection
     * @return true if this Collection owns this item
     */
    public boolean isOwningCollection(Collection c)
    {
        int owner_id = tableRow.getIntColumn("owning_collection");

        if (c.getID() == owner_id)
        {
            return true;
        }

        // not the owner
        return false;
    }

    /**
     * return type found in Constants
     *
     * @return int Constants.ITEM
     */
    @Override
    public int getType() {
        return Constants.ITEM;
    }

    /**
     * remove all of the policies for item and replace them with a new list of
     * policies
     *
     * @param newpolicies -
     *            this will be all of the new policies for the item and its
     *            contents
     * @throws SQLException
     * @throws AuthorizeException
     */
    public void replaceAllItemPolicies(List<ResourcePolicy> newpolicies) throws SQLException,
            AuthorizeException {
        // remove all our policies, add new ones
        AuthorizeManager.removeAllPolicies(context, this);
        AuthorizeManager.addPolicies(context, newpolicies, this);
    }

    /**
     * remove all of the policies for item's bitstreams and bundles and replace
     * them with a new list of policies
     *
     * @param newpolicies -
     *            this will be all of the new policies for the bundle and
     *            bitstream contents
     * @throws SQLException
     * @throws AuthorizeException
     */
    public void replaceAllBitstreamPolicies(List<ResourcePolicy> newpolicies)
            throws SQLException, AuthorizeException  {
        // remove all policies from bundles, add new ones
        for (Bundle bundle : getBundles()) {
            bundle.replaceAllBitstreamPolicies(newpolicies);
        }
    }

    /**
     * remove all of the policies for item's bitstreams and bundles that belong
     * to a given Group
     *
     * @param g
     *            Group referenced by policies that needs to be removed
     * @throws SQLException
     */
    public void removeGroupPolicies(Group g) throws SQLException {
        // remove Group's policies from Item
        AuthorizeManager.removeGroupPolicies(context, this, g);

        // remove all policies from bundles
        for (Bundle bundle : getBundles()) {
            for (Bitstream bs : bundle.getBitstreams()) {
                // remove bitstream policies
                AuthorizeManager.removeGroupPolicies(context, bs, g);
            }
            // change bundle policies
            AuthorizeManager.removeGroupPolicies(context, bundle, g);
        }
    }

    /**
     * remove all policies on an item and its contents, and replace them with
     * the DEFAULT_ITEM_READ and DEFAULT_BITSTREAM_READ policies belonging to
     * the collection.
     *
     * @param c
     *            Collection
     * @throws java.sql.SQLException
     *             if an SQL error or if no default policies found. It's a bit
     *             draconian, but default policies must be enforced.
     * @throws AuthorizeException
     */
    public void inheritCollectionDefaultPolicies(Collection c)
            throws SQLException, AuthorizeException {
        List<ResourcePolicy> policies;

        // remove the submit authorization policies
        // and replace them with the collection's default READ policies
        policies = AuthorizeManager.getPoliciesActionFilter(context, c,
                Constants.DEFAULT_ITEM_READ);

        // MUST have default policies
        if (policies.size() < 1) {
            throw new java.sql.SQLException("Collection " + c.getID()
                    + " (" + c.getHandle() + ")"
                    + " has no default item READ policies");
        }

        // change the action to just READ
        // just don't call update on the resourcepolicies!!!
        for (ResourcePolicy rp : policies) {
            rp.setAction(Constants.READ);
        }

        replaceAllItemPolicies(policies);

        policies = AuthorizeManager.getPoliciesActionFilter(context, c, Constants.DEFAULT_BITSTREAM_READ);

        if (policies.size() < 1) {
            throw new SQLException("Collection " + c.getID()
                    + " (" + c.getHandle() + ")"
                    + " has no default bitstream READ policies");
        }

        // change the action to just READ
        // just don't call update on the resourcepolicies!!!
        for (ResourcePolicy rp : policies) {
            rp.setAction(Constants.READ);
        }

        replaceAllBitstreamPolicies(policies);

        log.debug(LogManager.getHeader(context, "item_inheritCollectionDefaultPolicies",
                                                   "item_id=" + getID()));
    }

    /**
     * Moves the item from one collection to another one
     *
     * @throws SQLException
     * @throws AuthorizeException
     * @throws IOException
     */
    public void move (Collection from, Collection to) throws SQLException, AuthorizeException, IOException {
        // Use the normal move method, and default to not inherit permissions
        this.move(from, to, false);
    }

    /**
     * Moves the item from one collection to another one
     *
     * @throws SQLException
     * @throws AuthorizeException
     * @throws IOException
     */
    public void move (Collection from, Collection to, boolean inheritDefaultPolicies)
    		throws SQLException, AuthorizeException, IOException {
        // Check authorisation on the item before that the move occur
        // otherwise we will need edit permission on the "target collection" to archive our goal
        // only do write authorization if user is not an editor
        if (!canEdit()) {
            AuthorizeManager.authorizeAction(context, this, Constants.WRITE);
        }
        
        // Move the Item from one Collection to the other
        to.addItem(this);
        from.removeItem(this);

        // If we are moving from the owning collection, update that too
        if (isOwningCollection(from)) {
            // Update the owning collection
            log.info(LogManager.getHeader(context, "move_item",
                                          "item_id=" + getID() + ", from " +
                                          "collection_id=" + from.getID() + " to " +
                                          "collection_id=" + to.getID()));
            setOwningCollection(to);

            // If applicable, update the item policies
            if (inheritDefaultPolicies) {
                log.info(LogManager.getHeader(context, "move_item",
                         "Updating item with inherited policies"));
                inheritCollectionDefaultPolicies(to);
            }

            // Update the item
            context.turnOffAuthorisationSystem();
            update();
            context.restoreAuthSystemState();
        } else {
            // Although we haven't actually updated anything within the item
            // we'll tell the event system that it has, so that any consumers that
            // care about the structure of the repository can take account of the move

            // Note that updating the owning collection above will have the same effect,
            // so we only do this here if the owning collection hasn't changed.
            
            context.addEvent(new Event(Event.MODIFY, Constants.ITEM, getID(), null));
        }
    }
    
    /**
     * Check the bundle ORIGINAL to see if there are any uploaded files
     *
     * @return true if there is a bundle named ORIGINAL with one or more
     *         bitstreams inside
     * @throws SQLException
     */
    public boolean hasUploadedFiles() throws SQLException {
        List<Bundle> bundles = getBundles("ORIGINAL");
        if (bundles.size() == 0) {
            // if no ORIGINAL bundle,
            // return false that there is no file!
            return false;
        } else {
            List<Bitstream> bitstreams = bundles.get(0).getBitstreams();
            if (bitstreams.size() == 0) {
                // no files in ORIGINAL bundle!
                return false;
            }
        }
        return true;
    }
    
    /**
     * Get the collections this item is not in.
     *
     * @return the collections this item is not in, if any.
     * @throws SQLException
     */
    public List<Collection> getCollectionsNotLinked() throws SQLException
    {
        BoundedIterator<Collection> allIter = Collection.findAll(context);
        List<Collection> linkedCollections = getCollections();
        List<Collection> notLinkedCollections = new ArrayList<Collection>();
        //List<Collection> notLinkedCollections = new ArrayList<Collection>(allCollections.size() - linkedCollections.size());

        //if ((allCollections.size() - linkedCollections.size()) == 0) {
        //    return notLinkedCollections;
        //}
        
        while (allIter.hasNext()) {
        	Collection collection = allIter.next();
            boolean alreadyLinked = false;            
            for (Collection linkedCommunity : linkedCollections) {
                if (collection.getID() == linkedCommunity.getID()) {
                    alreadyLinked = true;
                    break;
                }
            }            
            if (!alreadyLinked) {
                notLinkedCollections.add(collection);
            }
        }
        allIter.close();
        return notLinkedCollections;
    }

    /**
     * return TRUE if context's user can edit item, false otherwise
     *
     * @return boolean true = current user can edit item
     * @throws SQLException
     */
    public boolean canEdit() throws SQLException {
        // can this person write to the item?
        if (AuthorizeManager.authorizeActionBoolean(context, this, Constants.WRITE) ||
        	// is this collection not yet created, and an item template is created
            getOwningCollection() == null ||
            // is this person an COLLECTION_EDITOR for the owning collection?
            getOwningCollection().canEditBoolean(false)) {
            return true;
        }

        return false;
    }
    
    @Override
    public String getName()  {
        List<MDValue> t = getMetadata("dc", "title", null, MDValue.ANY);
        return (t.size() >= 1) ? t.get(0).getValue() : null;
    }
        
    /**
     * Returns an iterator of Items possessing the passed metadata field, or only
     * those matching the passed value, if value is not Item.ANY
     *
     * @param context DSpace context object
     * @param schema metadata field schema
     * @param element metadata field element
     * @param qualifier metadata field qualifier
     * @param value field value or Item.ANY to match any value
     * @return an iterator over the items matching that authority value
     * @throws SQLException, AuthorizeException, IOException
     *
     */
    public static BoundedIterator<Item> findByMetadataField(Context context,
               String schema, String element, String qualifier, String value)
          throws SQLException, AuthorizeException, IOException {
        MetadataSchema mds = MetadataSchema.find(context, schema);
        if (mds == null) {
            throw new IllegalArgumentException("No such metadata schema: " + schema);
        }
        MetadataField mdf = MetadataField.findByElement(context, mds.getSchemaID(), element, qualifier);
        if (mdf == null) {
            throw new IllegalArgumentException(
                    "No such metadata field: schema=" + schema + ", element=" + element + ", qualifier=" + qualifier);
        }
        
        String query = "SELECT item.* FROM metadatavalue,item WHERE item.in_archive='1' "+
                       "AND item.dso_id = metadatavalue.dso_id AND metadata_field_id = ?";
        TableRowIterator rows = null;
        if (MDValue.ANY.equals(value))
        {
            rows = DatabaseManager.queryTable(context, "item", query, mdf.getFieldID());
        }
        else
        {
            query += " AND metadatavalue.text_value = ?";
            rows = DatabaseManager.queryTable(context, "item", query, mdf.getFieldID(), value);
        }
        return new BoundedIterator<Item>(context, rows);
     }
    
    public DSpaceObject getAdminObject(int action) throws SQLException {
        DSpaceObject adminObject = null;
        Collection collection = getOwningCollection();
        Community community = null;
        if (collection != null) {
            List<Community> communities = collection.getCommunities();
            if (communities.size() > 0) {
                community = communities.get(0);
            }
        }
        else
        {
            // is a template item?
            TableRow qResult = DatabaseManager.querySingle(context,
                       "SELECT collection_id FROM collection " +
                       "WHERE template_item_id = ?",getID());
            if (qResult != null)
            {
                collection = Collection.find(context, qResult.getIntColumn("collection_id"));
                List<Community> communities = collection.getCommunities();
                if (communities.size() > 0) {
                    community = communities.get(0);
                }
            }
        }
        
        switch (action)
        {
            case Constants.ADD:
                // ADD a cc license is less general than add a bitstream but we can't/won't
                // add complex logic here to know if the ADD action on the item is required by a cc or
                // a generic bitstream so simply we ignore it.. UI need to enforce the requirements.
                if (AuthorizeConfiguration.canItemAdminPerformBitstreamCreation())
                {
                    adminObject = this;
                }
                else if (AuthorizeConfiguration.canCollectionAdminPerformBitstreamCreation())
                {
                    adminObject = collection;
                }
                else if (AuthorizeConfiguration.canCommunityAdminPerformBitstreamCreation())
                {
                    adminObject = community;
                }
                break;
            case Constants.REMOVE:
                // see comments on ADD action, same things...
                if (AuthorizeConfiguration.canItemAdminPerformBitstreamDeletion())
                {
                    adminObject = this;
                }
                else if (AuthorizeConfiguration.canCollectionAdminPerformBitstreamDeletion())
                {
                    adminObject = collection;
                }
                else if (AuthorizeConfiguration.canCommunityAdminPerformBitstreamDeletion())
                {
                    adminObject = community;
                }
                break;
            case Constants.DELETE:
                if (getOwningCollection() != null)
                {
                    if (AuthorizeConfiguration.canCollectionAdminPerformItemDeletion())
                    {
                        adminObject = collection;
                    }
                    else if (AuthorizeConfiguration.canCommunityAdminPerformItemDeletion())
                    {
                        adminObject = community;
                    }
                }
                else
                {
                    if (AuthorizeConfiguration.canCollectionAdminManageTemplateItem())
                    {
                        adminObject = collection;
                    }
                    else if (AuthorizeConfiguration.canCommunityAdminManageCollectionTemplateItem())
                    {
                        adminObject = community;
                    }
                }
                break;
            case Constants.WRITE:
                // if it is a template item we need to check the
                // collection/community admin configuration
                if (getOwningCollection() == null)
                {
                    if (AuthorizeConfiguration.canCollectionAdminManageTemplateItem())
                    {
                        adminObject = collection;
                    }
                    else if (AuthorizeConfiguration.canCommunityAdminManageCollectionTemplateItem())
                    {
                        adminObject = community;
                    }
                }
                else
                {
                    adminObject = this;
                }
                break;
            default:
                adminObject = this;
                break;
            }
        return adminObject;
    }
    
    public DSpaceObject getParentObject() throws SQLException {
        Collection ownCollection = getOwningCollection();
        if (ownCollection != null) {
            return ownCollection;
        } else {
            // is a template item?
            TableRow qResult = DatabaseManager.querySingle(context,
                       "SELECT collection_id FROM collection " +
                       "WHERE template_item_id = ?",getID());
            if (qResult != null) {
                return Collection.find(context,qResult.getIntColumn("collection_id"));
            }
            return null;
        }
    }

}
