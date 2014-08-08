/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.handle;

import java.io.UnsupportedEncodingException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.handle.hdllib.*;

import org.dspace.content.Collection;
import org.dspace.content.Community;
import org.dspace.content.DSpaceObject;
import org.dspace.content.Item;
import org.dspace.content.Site;
import org.dspace.core.ConfigurationManager;
import org.dspace.core.Constants;
import org.dspace.core.Context;
import org.dspace.core.LogManager;
import org.dspace.storage.rdbms.DatabaseManager;
import org.dspace.storage.rdbms.TableRow;
import org.dspace.storage.rdbms.TableRowIterator;

/**
 * Interface to the <a href="http://www.handle.net" target=_new>CNRI Handle
 * System </a>.
 * 
 * <p>
 * Currently, this class simply maps handles to local facilities; handles which
 * are owned by other sites (including other DSpaces) are treated as
 * non-existent.
 * </p>
 * 
 * @author Peter Breton
 * @version $Revision: 5844 $
 */
public class HandleManager {
    /** log4j category */
    private static Logger log = LoggerFactory.getLogger(HandleManager.class);

    /** Prefix registered to no one */
    static final String EXAMPLE_PREFIX = "123456789";

    /** Private Constructor */
    private HandleManager()
    {
    }

    /**
     * Return the local URL for handle, or null if handle cannot be found.
     * 
     * The returned URL is a (non-handle-based) location where a dissemination
     * of the object referred to by handle can be obtained.
     * 
     * @param context
     *            DSpace context
     * @param handle
     *            The handle
     * @return The local URL
     * @exception SQLException
     *                If a database error occurs
     */
    public static String resolveToURL(Context context, String handle)
            throws SQLException {
        TableRow dbhandle = findHandleInternal(context, handle);

        if (dbhandle == null) {
            return null;
        }

        String url = ConfigurationManager.getProperty("site.url")
                + "/handle/" + handle;

        if (log.isDebugEnabled()) {
            log.debug("Resolved " + handle + " to " + url);
        }

        return url;
    }

    /**
     * Transforms handle into the canonical form <em>hdl:handle</em>.
     * 
     * No attempt is made to verify that handle is in fact valid.
     * 
     * @param handle
     *            The handle
     * @return The canonical form
     */
    public static String getCanonicalForm(String handle) {
    
        // Let the admin define a new prefix, if not then we'll use the 
        // CNRI default. This allows the admin to use "hdl:" if they want too or
        // use a locally branded prefix handle.myuni.edu.
        String handlePrefix = ConfigurationManager.getProperty("handle", "canonical.prefix");
        if (handlePrefix == null || handlePrefix.length() == 0) {
            handlePrefix = "http://hdl.handle.net/";
        }
    
        return handlePrefix + handle;
    }

    /**
     * Returns displayable string of the handle's 'temporary' URL
     * <em>http://hdl.handle.net/handle/em>.
     *
     * No attempt is made to verify that handle is in fact valid.
     *
     * @param handle The handle
     * @return The canonical form
     */

    //    public static String getURLForm(String handle)
    //    {
    //        return "http://hdl.handle.net/" + handle;
    //    }

    /**
     * Creates a new handle in the database.
     * 
     * @param context
     *            DSpace context
     * @param dso
     *            The DSpaceObject to create a handle for
     * @return The newly created handle
     * @exception SQLException
     *                If a database error occurs
     */
    public static String createHandle(Context context, DSpaceObject dso) throws SQLException {
        TableRow handle = DatabaseManager.create(context, "Handle");
        // obtain handleId from next primary key
        String handleId = createId(handle.getIntColumn("handle_id"));
        return createHandleInternal(context, handle, dso, handleId);
    }

    /**
     * Creates a handle entry, but with a handle supplied by the caller (new
     * Handle not generated)
     * 
     * @param context
     *            DSpace context
     * @param dso
     *            DSpaceObject
     * @param suppliedHandle
     *            existing handle value
     * @return the Handle
     * @throws IllegalStateException if specified handle is already in use by another object
     */
    public static String createHandle(Context context, DSpaceObject dso,
            String suppliedHandle) throws SQLException, IllegalStateException {
        //Check if the supplied handle is already in use -- cannot use the same handle twice
        TableRow handle = findHandleInternal(context, suppliedHandle);

        if (handle == null) { 
            //handle not found in DB table -- create a new table entry
            handle = DatabaseManager.create(context, "Handle");
            return createHandleInternal(context, handle, dso, suppliedHandle);
        }

        if (!handle.isColumnNull("resource_id")) {
            //Check if this handle is already linked up to this specified DSpace Object
            if(handle.getIntColumn("resource_id")==dso.getID() &&
               handle.getIntColumn("resource_type_id")==dso.getType()) {
                //This handle already links to this DSpace Object -- so, there's nothing else we need to do
                return suppliedHandle;
            } else {
                //handle found in DB table & already in use by another existing resource
                throw new IllegalStateException("Attempted to create a handle which is already in use: " + suppliedHandle);
            }
        } 

        if (!handle.isColumnNull("resource_type_id")) {
            //If there is a 'resource_type_id' (but 'resource_id' is empty), then the object using
            // this handle was previously unbound (see unbindHandle() method) -- likely because object was deleted
            int previousType = handle.getIntColumn("resource_type_id");

            //Since we are restoring an object to a pre-existing handle, double check we are restoring the same *type* of object
            // (e.g. we will not allow an Item to be restored to a handle previously used by a Collection)
            if(previousType != dso.getType()) {
                throw new IllegalStateException("Attempted to reuse a handle previously used by a " +
                        Constants.typeText[previousType] + " for a new " +
                        Constants.typeText[dso.getType()]);
            }

            handle.setColumn("resource_type_id", dso.getType());
            handle.setColumn("resource_id", dso.getID());
            DatabaseManager.update(context, handle);

            if (log.isDebugEnabled())  {
                log.debug("Restored handle for "
                    + Constants.typeText[dso.getType()] + " (ID=" + dso.getID() + ") " + suppliedHandle );
            }
        }

        return suppliedHandle;
    }

    /**
     * Removes binding of Handle to a DSpace object, while leaving the
     * Handle in the table so it doesn't get reallocated.  The AIP
     * implementation also needs it there for foreign key references.
     *
     * @param context DSpace context
     * @param dso DSpaceObject whose Handle to unbind.
     */
    public static void unbindHandle(Context context, DSpaceObject dso)
        throws SQLException {
        TableRow row = getHandleInternal(context, dso.getType(), dso.getID());
        if (row != null) {
            //Only set the "resouce_id" column to null when unbinding a handle.
            // We want to keep around the "resource_type_id" value, so that we
            // can verify during a restore whether the same *type* of resource
            // is reusing this handle!
            row.setColumnNull("resource_id");
            DatabaseManager.update(context, row);

            if(log.isDebugEnabled()) {
                log.debug("Unbound Handle " + row.getStringColumn("handle") + " from object " + Constants.typeText[dso.getType()] + " id=" + dso.getID());
            }

        } else {
            log.warn("Cannot find Handle entry to unbind for object " + Constants.typeText[dso.getType()] + " id=" + dso.getID());
        }
    }

    /**
     * Returns the object type associated with the handle, or -1
     * if the handle is unassigned, unbound, etc. This method may be used to
     * 'sex' handles without incurring the overhead of full object instantiation.
     *
     * @param context
     *            DSpace context
     * @param handle
     *            The handle to resolve
     * @return type
     *            the object type id
     * @exception IllegalStateException
     *                If handle was found but is not bound to an object
     * @exception SQLException
     *                If a database error occurs
     */
    public static int resolveToType(Context context, String handle)
            throws IllegalStateException, SQLException {

        TableRow dbhandle = findHandleInternal(context, handle);

        if (dbhandle == null) {
            //If this is the Site-wide Handle, return Site type
            if (handle.equals(Site.getSiteHandle())) {
                return Constants.SITE;
            }
            //Otherwise, return -1 (i.e. handle not found in DB)
            return -1;
        }
        // check if handle was allocated previously, but is currently not
        // associated with a DSpaceObject 
        // (this may occur when 'unbindHandle()' is called for an obj that was removed)
        if ((dbhandle.isColumnNull("resource_type_id")) || (dbhandle.isColumnNull("resource_id"))) {
            //if handle has been unbound, just return -1 (as this will result in a PageNotFound)
            return -1;
        }

        return dbhandle.getIntColumn("resource_type_id");
    }

    /**
     * Return the object which handle maps to, or null. This is the object
     * itself, not a URL which points to it.
     * 
     * @param context
     *            DSpace context
     * @param handle
     *            The handle to resolve
     * @return The object which handle maps to, or null if handle is not mapped
     *         to any object.
     * @exception IllegalStateException
     *                If handle was found but is not bound to an object
     * @exception SQLException
     *                If a database error occurs
     */
    public static DSpaceObject resolveToObject(Context context, String handle)
            throws IllegalStateException, SQLException {
        TableRow dbhandle = findHandleInternal(context, handle);

        if (dbhandle == null) {
            //If this is the Site-wide Handle, return Site object
            if (handle.equals(Site.getSiteHandle())) {
                return Site.find(context, 0);
            }
            //Otherwise, return null (i.e. handle not found in DB)
            return null;
        }

        // check if handle was allocated previously, but is currently not
        // associated with a DSpaceObject 
        // (this may occur when 'unbindHandle()' is called for an obj that was removed)
        if ((dbhandle.isColumnNull("resource_type_id"))
                || (dbhandle.isColumnNull("resource_id")))  {
            //if handle has been unbound, just return null (as this will result in a PageNotFound)
            return null;
        }

        // What are we looking at here?
        int handletypeid = dbhandle.getIntColumn("resource_type_id");
        int resourceID = dbhandle.getIntColumn("resource_id");

        if (handletypeid == Constants.ITEM)  {
            Item item = Item.find(context, resourceID);

            if (log.isDebugEnabled()) {
                log.debug("Resolved handle " + handle + " to item "
                        + ((item == null) ? (-1) : item.getID()));
            }

            return item;
        } else if (handletypeid == Constants.COLLECTION) {
            Collection collection = Collection.find(context, resourceID);

            if (log.isDebugEnabled()) {
                log.debug("Resolved handle " + handle + " to collection "
                        + ((collection == null) ? (-1) : collection.getID()));
            }

            return collection;
        } else if (handletypeid == Constants.COMMUNITY) {
            Community community = Community.find(context, resourceID);

            if (log.isDebugEnabled()) {
                log.debug("Resolved handle " + handle + " to community "
                        + ((community == null) ? (-1) : community.getID()));
            }

            return community;
        }

        throw new IllegalStateException("Unsupported Handle Type "
                + Constants.typeText[handletypeid]);
    }

    /**
     * Return the handle for an Object, or null if the Object has no handle.
     * 
     * @param context
     *            DSpace context
     * @param dso
     *            The object to obtain a handle for
     * @return The handle for object, or null if the object has no handle.
     * @exception SQLException
     *                If a database error occurs
     */
    public static String findHandle(Context context, DSpaceObject dso)
            throws SQLException {
        TableRow row = getHandleInternal(context, dso.getType(), dso.getID());
        if (row == null) {
            if (dso.getType() == Constants.SITE) {
                return Site.getSiteHandle();
            } else {
                return null;
            }
        } else {
            return row.getStringColumn("handle");
        }
    }
    
    /**
     * Return the handle for an Object, or null if the Object has no handle.
     * 
     * @param context
     *            DSpace context
     * @param dso
     *            The object to obtain a handle for
     * @return The handle for object, or null if the object has no handle.
     * @exception SQLException
     *                If a database error occurs
     */
    public static String findHandle(Context context, int type, int id)
            throws SQLException {
        TableRow row = getHandleInternal(context, type, id);
        if (row == null) {
            if (type == Constants.SITE) {
                return Site.getSiteHandle();
            } else {
                return null;
            }
        } else {
            return row.getStringColumn("handle");
        }
    }

    /**
     * Return all the handles which start with prefix.
     * 
     * @param context
     *            DSpace context
     * @param prefix
     *            The handle prefix
     * @return A list of the handles starting with prefix. The list is
     *         guaranteed to be non-null. Each element of the list is a String.
     * @exception SQLException
     *                If a database error occurs
     */
    static List<String> getHandlesForPrefix(Context context, String prefix) throws SQLException {
        String sql = "SELECT handle FROM handle WHERE handle LIKE ? ";
        List<String> results = new ArrayList<String>();

        try (TableRowIterator iterator = DatabaseManager.queryTable(context, null, sql, prefix+"%")){
            while (iterator.hasNext()) {
                TableRow row = (TableRow) iterator.next();
                results.add(row.getStringColumn("handle"));
            }
        } 
        return results;
    }

    /**
     * Get the configured Handle prefix string, or a default
     * @return configured prefix or "123456789"
     */
    public static String getPrefix() {
        String prefix = ConfigurationManager.getProperty("handle", "handle.prefix");
        if (null == prefix) {
            prefix = EXAMPLE_PREFIX; // XXX no good way to exit cleanly
            log.error("handle.prefix is not configured; using " + prefix);
        }
        return prefix;
    }

    ////////////////////////////////////////
    // Internal methods
    ////////////////////////////////////////

     /**
     * Creates a new handle in the database.
     * 
     * @param context
     *            DSpace context
     * @param handleRow
     *            DB row to use
     * @param dso
     *            The DSpaceObject to create a handle for
     * @param handleId
     *             handle identifier to assign
     * @return The newly created handle
     * @exception SQLException
     *                If a database error occurs
     */
    private static String createHandleInternal(Context context, TableRow handleRow, DSpaceObject dso, String handleId) throws SQLException {
        
        handleRow.setColumn("handle", handleId);
        handleRow.setColumn("resource_type_id", dso.getType());
        handleRow.setColumn("resource_id", dso.getID());
        DatabaseManager.update(context, handleRow);

        if (ConfigurationManager.getBooleanProperty("handle", "server.remote")) {
            // register handle with LHS
            String url = ConfigurationManager.getProperty("dspace.url") + "/handle/" + handleId;
            String authHandle = ConfigurationManager.getProperty("handle", "auth.handle");
            String authIndex = ConfigurationManager.getProperty("handle", "auth.index");
            String passphrase = ConfigurationManager.getProperty("handle", "auth.passphrase");

            // A HandleResolver object is used not just for resolution, but for
            // all handle operations(including create)
            HandleResolver resolver = new HandleResolver();

            // Response object (declared here so we still have it if an exception occurs)
            AbstractResponse response = null;

            try {
                // Create a SecretKeyAuthenticationInfo object to pass to HandleResolver.
                // This is constructed with the admin handle, index, and SecretKey as arguments.
                SecretKeyAuthenticationInfo auth = new SecretKeyAuthenticationInfo(
                       authHandle.getBytes("UTF8"), Integer.valueOf(authIndex).intValue(), passphrase.getBytes());

                // We don't want to create a handle without an admin value -- otherwise
                // we would be locked out. Give ourselves all permissions, even
                // ones that only apply for NA handles.
                AdminRecord admin = new AdminRecord(authHandle.getBytes("UTF8"),
                    300, true, true, true, true, true, true, true, true, true, true, true, true);

                // All handle values need a timestamp, so get the current time in
                // seconds since the epoch
                int timestamp = (int) (System.currentTimeMillis() / 1000);

                // Now build the HandleValue object.
                HandleValue[] val = {
                      new HandleValue(1, "URL".getBytes("UTF8"), url.getBytes("UTF8"),
                                      HandleValue.TTL_TYPE_RELATIVE, 86400, timestamp,
                                      null, true, true, true, false),
                      new HandleValue(100, "HS_ADMIN".getBytes("UTF8"), Encoder.encodeAdminRecord(admin),
                                      HandleValue.TTL_TYPE_RELATIVE, 86400, timestamp,
                                      null, true, true, true, false) };

                // Now we can build our CreateHandleRequest object. As its first
                // parameter it takes the handle we are going to create. The second
                // argument is the array of initial values the handle should have.
                // The final argument is the authentication object that should be
                // used to gain permission to perform the creation.
                CreateHandleRequest req = new CreateHandleRequest(handleId.getBytes("UTF8"), val, auth);

                // Setting this flag lets us watch the request as it is processed.
                resolver.traceMessages = true;

                // Finally, we are ready to send the message. We do this by calling
                // the processRequest method of the resolver object with the request
                // object as an argument. The result is returned as either a
                // GenericResponse or ErrorResponse object. It is important to note that
                // a failed resolution will not throw an exception, only return an ErrorResponse.
                response = resolver.processRequest(req);

                // The responseCode value for a response indicates the status of
                // the request. A successful resolution will always return
                // RC_SUCCESS. Failed resolutions could return one of several
                // response codes, including RC_ERROR, RC_INVALID_ADMIN, and
                // RC_INSUFFICIENT_PERMISSIONS.
                if (response.responseCode == AbstractMessage.RC_SUCCESS) {
                    log.info(LogManager.getHeader(context, "create_remote_handle",
                                                  "handle=" + handleId + ",url=" + url));
                } else {
                    log.warn(LogManager.getHeader(context, "create_remote_handle_error", "handle=" + handleId +
                                                  ",url=" + url + ",response=" + response.toString()));

                    // If we get to this stage, there has been some error creating the Handle remotely
                    throw new SQLException("Could not register remote Handle");
                }
            } catch (HandleException he) {
                String responseText = (response == null) ? "null" : response.toString();
                log.warn(LogManager.getHeader(context, "create_remote_handle_error", "handle=" + handleId +
                                              ",url=" + url + ",response=" + responseText), he);
            } catch (UnsupportedEncodingException uee) {
                // This should never happen!
                throw new IllegalStateException(uee.toString());
            }
        }

        if (log.isDebugEnabled()) {
            log.debug("Created new handle for "
                    + Constants.typeText[dso.getType()] + " (ID=" + dso.getID() + ") " + handleId );
        }

        return handleId;
    }

    /**
     * Return the handle for an Object, or null if the Object has no handle.
     * 
     * @param context
     *            DSpace context
     * @param type
     *            The type of object
     * @param id
     *            The id of object
     * @return The handle for object, or null if the object has no handle.
     * @exception SQLException
     *                If a database error occurs
     */
    private static TableRow getHandleInternal(Context context, int type, int id)
            throws SQLException { 
        String sql = "SELECT * FROM Handle WHERE resource_type_id = ? AND resource_id = ?";

        return DatabaseManager.querySingleTable(context, "Handle", sql, type, id);
    }

    /**
     * Find the database row corresponding to handle.
     * 
     * @param context
     *            DSpace context
     * @param handle
     *            The handle to resolve
     * @return The database row corresponding to the handle
     * @exception SQLException
     *                If a database error occurs
     */
    private static TableRow findHandleInternal(Context context, String handle)
            throws SQLException {
        if (handle == null) {
            throw new IllegalArgumentException("Handle is null");
        }

        return DatabaseManager.findByUnique(context, "Handle", "handle", handle);
    }

    /**
     * Create a new handle id. The implementation uses the PK of the RDBMS
     * Handle table.
     *
     * @return A new handle id
     * @exception SQLException
     *                If a database error occurs
     */
    private static String createId(int id) throws SQLException {
        String handlePrefix = getPrefix();

        return new StringBuffer().append(handlePrefix).append(
                handlePrefix.endsWith("/") ? "" : "/").append(id).toString();
    }
}
