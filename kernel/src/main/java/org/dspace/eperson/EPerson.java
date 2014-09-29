/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.eperson;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Objects;

import org.dspace.authorize.AuthorizeException;
import org.dspace.authorize.AuthorizeManager;
import org.dspace.content.DSpaceObject;
import org.dspace.core.Constants;
import org.dspace.core.Context;
import org.dspace.core.LogManager;
import org.dspace.core.Utils;
import org.dspace.event.ContentEvent.EventType;
import org.dspace.storage.rdbms.DatabaseManager;
import org.dspace.storage.rdbms.TableRow;
import org.dspace.storage.rdbms.TableRowIterator;

/**
 * Class representing an e-person.
 *
 * @author David Stuve
 * @version $Revision: 6677 $
 */
public class EPerson extends DSpaceObject
{
    /** The e-mail field (for sorting) */
    public static final int EMAIL = 1;

    /** The last name (for sorting) */
    public static final int LASTNAME = 2;

    /** The e-mail field (for sorting) */
    public static final int ID = 3;

    /** The netid field (for sorting) */
    public static final int NETID = 4;

    /** The e-mail field (for sorting) */
    public static final int LANGUAGE = 5;

    /** log4j logger */
    private static Logger log = LoggerFactory.getLogger(EPerson.class);


    /**
     * Construct an EPerson
     *
     * @param context
     *            the context this object exists in
     * @param row
     *            the corresponding row in the table
     */
    EPerson(Context context, TableRow row) {
        this.context = context;
        tableRow = row;

        // Cache ourselves
        context.cache(this, row.getIntColumn("eperson_id"));
    }

    /**
     * Return true if this object equals obj, false otherwise.
     *
     * @param obj
     * @return true if ResourcePolicy objects are equal
     */
    @Override
    public boolean equals(Object obj) {
    	if (obj instanceof EPerson) {
    		final EPerson other = (EPerson) obj;
    		return (getID() == other.getID() &&
    			    Objects.equal(getEmail(), other.getEmail()) &&
    			    Objects.equal(getFullName(), other.getFullName()));
    	}
        return false;
    }

    /**
     * Return a hash code for this object.
     *
     * @return int hash of object
     */
    @Override
    public int hashCode() {
    	return Objects.hashCode(getID(), getEmail(), getFullName());
    }

    /**
     * Get an EPerson from the database.
     *
     * @param context
     *            DSpace context object
     * @param id
     *            ID of the EPerson
     *
     * @return the EPerson format, or null if the ID is invalid.
     */
    public static EPerson find(Context context, int id) throws SQLException {
        // First check the cache
        EPerson fromCache = (EPerson) context.fromCache(EPerson.class, id);

        if (fromCache != null) {
            return fromCache;
        }

        TableRow row = DatabaseManager.find(context, "eperson", id);

        if (row == null) {
            return null;
        } else {
            return new EPerson(context, row);
        }
    }

    /**
     * Find the eperson by their email address.
     *
     * @return EPerson, or {@code null} if none such exists.
     */
    public static EPerson findByEmail(Context context, String email)
            throws SQLException, AuthorizeException {
        if (email == null) {
            return null;
        }

        // All email addresses are stored as lowercase, so ensure that the email address is lowercased for the lookup
        TableRow row = DatabaseManager.findByUnique(context, "eperson",
                "email", email.toLowerCase());

        if (row == null)
        {
            return null;
        }
        else
        {
            // First check the cache
            EPerson fromCache = (EPerson) context.fromCache(EPerson.class, row
                    .getIntColumn("eperson_id"));

            if (fromCache != null)
            {
                return fromCache;
            }
            else
            {
                return new EPerson(context, row);
            }
        }
    }

    /**
     * Find the eperson by their netid.
     *
     * @param context
     *            DSpace context
     * @param netid
     *            Network ID
     *
     * @return corresponding EPerson, or <code>null</code>
     */
    public static EPerson findByNetid(Context context, String netid)
            throws SQLException
    {
        if (netid == null)
        {
            return null;
        }

        TableRow row = DatabaseManager.findByUnique(context, "eperson", "netid", netid);

        if (row == null)
        {
            return null;
        }
        else
        {
            // First check the cache
            EPerson fromCache = (EPerson) context.fromCache(EPerson.class, row
                    .getIntColumn("eperson_id"));

            if (fromCache != null)
            {
                return fromCache;
            }
            else
            {
                return new EPerson(context, row);
            }
        }
    }

    /**
     * Find the epeople that match the search query across firstname, lastname or email.
     *
     * @param context
     *            DSpace context
     * @param query
     *            The search string
     *
     * @return array of EPerson objects
     */
    public static EPerson[] search(Context context, String query)
            throws SQLException {
        return search(context, query, -1, -1);
    }

    /**
     * Find the epeople that match the search query across firstname, lastname or email.
     * This method also allows offsets and limits for pagination purposes.
     *
     * @param context
     *            DSpace context
     * @param query
     *            The search string
     * @param offset
     *            Inclusive offset
     * @param limit
     *            Maximum number of matches returned
     *
     * @return array of EPerson objects
     */
    public static EPerson[] search(Context context, String query, int offset, int limit) throws SQLException {
        String params = "%"+query.toLowerCase()+"%";
        StringBuffer queryBuf = new StringBuffer();
        queryBuf.append("SELECT * FROM eperson WHERE eperson_id = ? OR ");
        queryBuf.append("LOWER(firstname) LIKE LOWER(?) OR LOWER(lastname) LIKE LOWER(?) OR LOWER(email) LIKE LOWER(?) ORDER BY lastname, firstname ASC ");

        if (limit > 0) {
            queryBuf.append(" LIMIT ? ");
        }

        if (offset > 0) {
            queryBuf.append(" OFFSET ? ");
        }

        String dbquery = queryBuf.toString();

        // When checking against the eperson-id, make sure the query can be made into a number
        Integer int_param;
        try {
            int_param = Integer.valueOf(query);
        } catch (NumberFormatException e) {
            int_param = Integer.valueOf(-1);
        }

        // Create the parameter array, including limit and offset if part of the query
        Object[] paramArr = new Object[] {int_param,params,params,params};
        if (limit > 0 && offset > 0)
        {
            paramArr = new Object[]{int_param, params, params, params, limit, offset};
        }
        else if (limit > 0)
        {
            paramArr = new Object[]{int_param, params, params, params, limit};
        }
        else if (offset > 0)
        {
            paramArr = new Object[]{int_param, params, params, params, offset};
        }

        // Get all the epeople that match the query
		TableRowIterator rows = DatabaseManager.query(context,
		        dbquery, paramArr);
		try
        {
            List<TableRow> epeopleRows = rows.toList();
            EPerson[] epeople = new EPerson[epeopleRows.size()];

            for (int i = 0; i < epeopleRows.size(); i++)
            {
                TableRow row = (TableRow) epeopleRows.get(i);

                // First check the cache
                EPerson fromCache = (EPerson) context.fromCache(EPerson.class, row
                        .getIntColumn("eperson_id"));

                if (fromCache != null)
                {
                    epeople[i] = fromCache;
                }
                else
                {
                    epeople[i] = new EPerson(context, row);
                }
            }

            return epeople;
        }
        finally
        {
            if (rows != null) {
                rows.close();
            }
        }
    }

    /**
     * Returns the total number of epeople returned by a specific query, without the overhead
     * of creating the EPerson objects to store the results.
     *
     * @param context
     *            DSpace context
     * @param query
     *            The search string
     *
     * @return the number of epeople matching the query
     */
    public static int searchResultCount(Context context, String query) throws SQLException {
        String dbquery = "%"+query.toLowerCase()+"%";
        Long count;

        // When checking against the eperson-id, make sure the query can be made into a number
        Integer int_param;
        try {
             int_param = Integer.valueOf(query);
        } catch (NumberFormatException e) {
             int_param = Integer.valueOf(-1);
        }

        // Get all the epeople that match the query
        TableRow row = DatabaseManager.querySingle(context,
                "SELECT count(*) as epcount FROM eperson WHERE eperson_id = ? OR " +
                "LOWER(firstname) LIKE LOWER(?) OR LOWER(lastname) LIKE LOWER(?) OR LOWER(email) LIKE LOWER(?)",
                new Object[] {int_param,dbquery,dbquery,dbquery});

        count = Long.valueOf(row.getLongColumn("epcount"));

        return count.intValue();
    }

    /**
     * Find all the epeople that match a particular query
     * <ul>
     * <li><code>ID</code></li>
     * <li><code>LASTNAME</code></li>
     * <li><code>EMAIL</code></li>
     * <li><code>NETID</code></li>
     * </ul>
     *
     * @return array of EPerson objects
     */
    public static List<EPerson> findAll(Context context, int sortField)
            throws SQLException
    {
        String s;

        switch (sortField)
        {
        case ID:
            s = "eperson_id";
            break;

        case EMAIL:
            s = "email";
            break;

        case LANGUAGE:
            s = "language";
            break;
        case NETID:
            s = "netid";
            break;

        default:
            s = "lastname";
        }

        // NOTE: The use of 's' in the order by clause can not cause an SQL
        // injection because the string is derived from constant values above.
        TableRowIterator tri = DatabaseManager.query(context,
                "SELECT * FROM eperson ORDER BY "+s);

        List<EPerson> epeople = new ArrayList<EPerson>();

        try {
            while (tri.hasNext()) {
            	TableRow row = tri.next();
            	// First check the cache
                EPerson fromCache = (EPerson) context.fromCache(EPerson.class, row
                        .getIntColumn("eperson_id"));

                if (fromCache != null) {
                    epeople.add(fromCache);
                } else {
                    epeople.add(new EPerson(context, row));
                }
            }
        } finally {
            if (tri != null)  {
                tri.close();
            }
        }
        return epeople;
    }

    /**
     * Create a new eperson
     *
     * @param context
     *            DSpace context object
     */
    public static EPerson create(Context context) throws SQLException,
            AuthorizeException
    {
        // authorized?
        if (!AuthorizeManager.isAdmin(context))
        {
            throw new AuthorizeException(
                    "You must be an admin to create an EPerson");
        }

        // Create a table row
        TableRow row = DatabaseManager.create(context, "eperson");

        EPerson e = new EPerson(context, row);
        e.createDSO();

        log.info(LogManager.getHeader(context, "create_eperson", "eperson_id="
                + e.getID()));

        context.addContentEvent(e, EventType.CREATE);

        return e;
    }

    /**
     * Delete an eperson
     *
     */
    public void delete() throws SQLException, AuthorizeException,
            EPersonDeletionException
    {
        // authorized?
        if (!AuthorizeManager.isAdmin(context))   {
            throw new AuthorizeException(
                    "You must be an admin to delete an EPerson");
        }

        // check for presence of eperson in tables that
        // have constraints on eperson_id
        List<String> constraintList = getDeleteConstraints();

        // if eperson exists in tables that have constraints
        // on eperson, throw an exception
        if (constraintList.size() > 0)
        {
            throw new EPersonDeletionException(constraintList);
        }

        context.addContentEvent(this, EventType.DELETE);

        // Remove from cache
        context.removeCached(this, getID());

        // XXX FIXME: This sidesteps the object model code so it won't
        // generate  REMOVE events on the affected Groups.

        // Remove any group memberships first
        DatabaseManager.updateQuery(context,
                "DELETE FROM EPersonGroup2EPerson WHERE eperson_id= ? ",
                getID());

        // Remove any subscriptions
        DatabaseManager.updateQuery(context,
                "DELETE FROM subscription WHERE eperson_id= ? ",
                getID());

        // remove DSO info
        destroyDSO();

        // Remove ourself
        DatabaseManager.delete(context, tableRow);

        log.info(LogManager.getHeader(context, "delete_eperson",
                "eperson_id=" + getID()));
    }

    /**
     * Get the e-person's internal identifier
     *
     * @return the internal identifier
     */
    @Override
    public int getID() {
        return tableRow.getIntColumn("eperson_id");
    }

    /**
     * Get the e-person's language
     *
     * @return  language
     */
     public String getLanguage()
     {
         return tableRow.getStringColumn("language");
     }

     /**
     * Set the EPerson's language.  Value is expected to be a Unix/POSIX
     * Locale specification of the form {language} or {language}_{territory},
     * e.g. "en", "en_US", "pt_BR" (the latter is Brazilian Portugese).
     *
     * @param language
     *            language
     */
     public void setLanguage(String language)
     {
         tableRow.setColumn("language", language);
     }


    public String getHandle()
    {
        // No Handles for e-people
        return null;
    }

    /**
     * Get the e-person's email address
     *
     * @return their email address
     */
    public String getEmail()
    {
        return tableRow.getStringColumn("email");
    }

    /**
     * Set the EPerson's email
     *
     * @param s
     *            the new email
     */
    public void setEmail(String s)
    {
        if (s != null)
        {
            s = s.toLowerCase();
        }

        tableRow.setColumn("email", s);
        modified = true;
    }

    /**
     * Get the e-person's netid
     *
     * @return their netid
     */
    public String getNetid()
    {
        return tableRow.getStringColumn("netid");
    }

    /**
     * Set the EPerson's netid
     *
     * @param s
     *            the new netid
     */
    public void setNetid(String s)
    {
        tableRow.setColumn("netid", s);
        modified = true;
    }

    /**
     * Get the e-person's full name, combining first and last name in a
     * displayable string.
     *
     * @return their full name
     */
    public String getFullName() {
        String f = tableRow.getStringColumn("firstname");
        String l = tableRow.getStringColumn("lastname");

        if ((l == null) && (f == null))
        {
            return getEmail();
        }
        else if (f == null)
        {
            return l;
        }
        else
        {
            return (f + " " + l);
        }
    }

    /**
     * Get the eperson's first name.
     *
     * @return their first name
     */
    public String getFirstName()
    {
        return tableRow.getStringColumn("firstname");
    }

    /**
     * Set the eperson's first name
     *
     * @param firstname
     *            the person's first name
     */
    public void setFirstName(String firstname)
    {
        tableRow.setColumn("firstname", firstname);
        modified = true;
    }

    /**
     * Get the eperson's last name.
     *
     * @return their last name
     */
    public String getLastName()
    {
        return tableRow.getStringColumn("lastname");
    }

    /**
     * Set the eperson's last name
     *
     * @param lastname
     *            the person's last name
     */
    public void setLastName(String lastname)
    {
        tableRow.setColumn("lastname", lastname);
        modified = true;
    }

    /**
     * Indicate whether the user can log in
     *
     * @param login
     *            boolean yes/no
     */
    public void setCanLogIn(boolean login)
    {
        tableRow.setColumn("can_log_in", login);
        modified = true;
    }

    /**
     * Can the user log in?
     *
     * @return boolean, yes/no
     */
    public boolean canLogIn()
    {
        return tableRow.getBooleanColumn("can_log_in");
    }

    /**
     * Set require cert yes/no
     *
     * @param isrequired
     *            boolean yes/no
     */
    public void setRequireCertificate(boolean isrequired)
    {
        tableRow.setColumn("require_certificate", isrequired);
        modified = true;
    }

    /**
     * Get require certificate or not
     *
     * @return boolean, yes/no
     */
    public boolean getRequireCertificate()
    {
        return tableRow.getBooleanColumn("require_certificate");
    }

    /**
     * Indicate whether the user self-registered
     *
     * @param sr
     *            boolean yes/no
     */
    public void setSelfRegistered(boolean sr)
    {
        tableRow.setColumn("self_registered", sr);
        modified = true;
    }

    /**
     * Can the user log in?
     *
     * @return boolean, yes/no
     */
    public boolean getSelfRegistered()
    {
        return tableRow.getBooleanColumn("self_registered");
    }

    /**
     * Get the value of a metadata field
     *
     * @param field
     *            the name of the metadata field to get
     *
     * @return the value of the metadata field
     *
     * @exception IllegalArgumentException
     *                if the requested metadata field doesn't exist
     */
    public String getLocalMetadata(String field)
    {
        return tableRow.getStringColumn(field);
    }

    /**
     * Set a metadata value
     *
     * @param field
     *            the name of the metadata field to get
     * @param value
     *            value to set the field to
     *
     * @exception IllegalArgumentException
     *                if the requested metadata field doesn't exist
     */
    public void setMetadata(String field, String value)
    {
        tableRow.setColumn(field, value);
        modifiedMetadata = true;
    }

    /**
     * Set the EPerson's password
     *
     * @param s
     *            the new email
     */
    public void setPassword(String s)
    {
        // FIXME: encoding
        String encoded = Utils.getMD5(s);

        tableRow.setColumn("password", encoded);
        modified = true;
    }

    /**
     * Set the EPerson's password hash
     *
     * @param s
     *          hash of the password
     */
    public void setPasswordHash(String s)
    {
        tableRow.setColumn("password", s);
        modified = true;
    }

    /**
     * Return the EPerson's password hash
     * @return hash of the password
     */
    public String getPasswordHash()
    {
        return tableRow.getStringColumn("password");
    }

    /**
     * Check EPerson's password
     *
     * @param attempt
     *            the password attempt
     * @return boolean successful/unsuccessful
     */
    public boolean checkPassword(String attempt)
    {
        String encoded = Utils.getMD5(attempt);

        return (encoded.equals(tableRow.getStringColumn("password")));
    }

    /**
     * Update the EPerson
     */
    @Override
    public void update() throws AuthorizeException, SQLException
    {
        // Check authorisation - if you're not the eperson
        // see if the authorization system says you can
        if (!context.ignoreAuthorization()
                && ((context.getCurrentUser() == null) || (getID() != context
                        .getCurrentUser().getID()))) {
            AuthorizeManager.authorizeAction(context, this, Constants.WRITE);
        }

        log.info(LogManager.getHeader(context, "update_eperson",
                "eperson_id=" + getID()));

        updateDSO();
    }

    /**
     * return type found in Constants
     */
    @Override
    public int getType()
    {
        return Constants.EPERSON;
    }

    /**
     * Check for presence of EPerson in tables that have constraints on
     * EPersons. Called by delete() to determine whether the eperson can
     * actually be deleted.
     *
     * An EPerson cannot be deleted if it exists in the item, workflowitem, or
     * tasklistitem tables.
     *
     * @return List of tables that contain a reference to the eperson.
     */
    public List<String> getDeleteConstraints() throws SQLException
    {
        List<String> tableList = new ArrayList<String>();

        // check for eperson in item table
        TableRowIterator tri = DatabaseManager.query(context,
                "SELECT * from item where submitter_id= ? ",
                getID());

        try
        {
            if (tri.hasNext())
            {
                tableList.add("item");
            }
        }
        finally
        {
            // close the TableRowIterator to free up resources
            if (tri != null)
            {
                tri.close();
            }
        }

        //if(ConfigurationManager.getProperty("workflow","workflow.framework").equals("xmlworkflow")){
        //    getXMLWorkflowConstraints(tableList);
        //}else{
            getOriginalWorkflowConstraints(tableList);

        //}
        // the list of tables can be used to construct an error message
        // explaining to the user why the eperson cannot be deleted.
        return tableList;
    }

    private void getXMLWorkflowConstraints(List<String> tableList) throws SQLException {
         TableRowIterator tri;
        // check for eperson in claimtask table
        tri = DatabaseManager.queryTable(context, "cwf_claimtask",
                "SELECT * from cwf_claimtask where owner_id= ? ",
                getID());

        try
        {
            if (tri.hasNext())
            {
                tableList.add("cwf_claimtask");
            }
        }
        finally
        {
            // close the TableRowIterator to free up resources
            if (tri != null)
            {
                tri.close();
            }
        }

        // check for eperson in pooltask table
        tri = DatabaseManager.queryTable(context, "cwf_pooltask",
                "SELECT * from cwf_pooltask where eperson_id= ? ",
                getID());

        try
        {
            if (tri.hasNext())
            {
                tableList.add("cwf_pooltask");
            }
        }
        finally
        {
            // close the TableRowIterator to free up resources
            if (tri != null)
            {
                tri.close();
            }
        }

        // check for eperson in workflowitemrole table
        tri = DatabaseManager.queryTable(context, "cwf_workflowitemrole",
                "SELECT * from cwf_workflowitemrole where eperson_id= ? ",
                getID());

        try
        {
            if (tri.hasNext())
            {
                tableList.add("cwf_workflowitemrole");
            }
        }
        finally
        {
            // close the TableRowIterator to free up resources
            if (tri != null)
            {
                tri.close();
            }
        }

    }

    private void getOriginalWorkflowConstraints(List<String> tableList) throws SQLException {
        TableRowIterator tri;
        // check for eperson in workflowitem table
        tri = DatabaseManager.query(context,
                "SELECT * from workflowitem where owner= ? ",
                getID());

        try
        {
            if (tri.hasNext())
            {
                tableList.add("workflowitem");
            }
        }
        finally
        {
            // close the TableRowIterator to free up resources
            if (tri != null)
            {
                tri.close();
            }
        }

        // check for eperson in tasklistitem table
        tri = DatabaseManager.query(context,
                "SELECT * from tasklistitem where eperson_id= ? ",
                getID());

        try
        {
            if (tri.hasNext())
            {
                tableList.add("tasklistitem");
            }
        }
        finally
        {
            // close the TableRowIterator to free up resources
            if (tri != null)
            {
                tri.close();
            }
        }
    }

    public String getName()
    {
        return getEmail();
    }

}
