/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.content;

import java.io.InputStream;
import java.io.IOException;
import java.sql.SQLException;
import java.util.List;

import com.google.common.base.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.dspace.authorize.AuthorizeConfiguration;
import org.dspace.authorize.AuthorizeException;
import org.dspace.authorize.AuthorizeManager;
import org.dspace.authorize.ResourcePolicy;
import org.dspace.core.ConfigurationManager;
import org.dspace.core.Constants;
import org.dspace.core.Context;
import org.dspace.core.LogManager;
import org.dspace.handle.HandleManager;
import org.dspace.storage.rdbms.DatabaseManager;
import org.dspace.storage.rdbms.TableRow;

/**
 * Represents the root of the DSpace Archive.
 * By default, the handle suffix "0" represents the Site, e.g. "1721.1/0"
 */
public class Site extends DSpaceObject
{
    /** log4j category */
    private static Logger log = LoggerFactory.getLogger(Site.class);

    /** The logo bitstream */
    private Bitstream logo;

    /** "database" identifier of the site */
    public static final int SITE_ID = 0;

    // cache for Handle that is persistent ID for entire site.
    private static String handle = null;

    /**
     * Construct a site object from a database row.
     * 
     * @param context
     *            the context this object exists in
     * @param row
     *            the corresponding row in the table
     */
    Site(Context context, TableRow row) throws SQLException {
        this.context = context;
        tableRow = row;

        // Get the logo bitstream
        if (tableRow.isColumnNull("logo_bitstream_id")) {
            logo = null;
        } else {
            logo = Bitstream.find(context, tableRow.getIntColumn("logo_bitstream_id"));
        }

        // Cache ourselves
        context.cache(this, row.getIntColumn("site_id"));
    }

    /**
     * Get the site from the database. Loads in the metadata
     * 
     * @param context
     *            DSpace context object
     * @param site DB id - ignored
     * 
     * @return the site, or null if no site record.
     */
    public static Site find(Context context, int id) throws SQLException  {
        // First check the cache
        Site fromCache = (Site) context.fromCache(Site.class, 1);

        if (fromCache != null) {
            return fromCache;
        }

        TableRow row = DatabaseManager.find(context, "site", 1);

        if (row == null) {
            log.debug(LogManager.getHeader(context, "find_site", "not_found"));
            return null;
        } else {
            log.debug(LogManager.getHeader(context, "find_site", "found"));
            return new Site(context, row);
        }
    }

    /**
     * Create the site
     *
     * @param context
     *            DSpace context object
     *
     * @return the newly created site
     */
    public static Site create(Context context) throws SQLException, AuthorizeException {
        if (!AuthorizeManager.isAdmin(context)){
            throw new AuthorizeException("Only administrators can create the site");
        }
        
        TableRow row = DatabaseManager.create(context, "site");
        Site s = new Site(context, row);
        s.createDSO();

        log.info(LogManager.getHeader(context, "create_site",
                "site_id=" + row.getIntColumn("site_id")));

        return s;
    }

    /**
     * Get the type of this object, found in Constants
     *
     * @return type of the object
     */
    @Override
    public int getType() {
        return Constants.SITE;
    }

    /**
     * Get the internal ID (database primary key) of this object
     *
     * @return internal ID of object
     */
    @Override
    public int getID() {
        return tableRow.getIntColumn("site_id");
    }

    /**
     * Get the Handle of the object. This may return <code>null</code>
     *
     * @return Handle of the object, or <code>null</code> if it doesn't have
     *         one
     */
    @Override
    public String getHandle() {
        return getSiteHandle();
    }

    /**
     * Get the logo for the site. <code>null</code> is return if the
     * site does not have a logo.
     * 
     * @return the logo of the site, or <code>null</code>
     */
    public Bitstream getLogo() {
        return logo;
    }

    /**
     * Give the site a logo. Passing in <code>null</code> removes any
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
     */
    public Bitstream setLogo(InputStream is) throws AuthorizeException,
            IOException, SQLException {
        // Check authorisation
        // authorized to remove the logo when DELETE rights
        // authorized when canEdit
        if (!((is == null) && AuthorizeManager.authorizeActionBoolean(
                context, this, Constants.DELETE))) {
            canEdit();
        }

        // First, delete any existing logo
        if (logo != null) {
            log.info(LogManager.getHeader(context, "remove_logo", "site_id=" + getID()));
            tableRow.setColumnNull("logo_bitstream_id");
            logo.delete();
            logo = null;
        }

        if (is != null) {
            Bitstream newLogo = Bitstream.create(context, is);
            // give it some standard attributes
            newLogo.setSequenceID(1);
            newLogo.setName("logo");
            newLogo.update();
            tableRow.setColumn("logo_bitstream_id", newLogo.getID());
            logo = newLogo;

            // now create policy for logo bitstream
            // to match our READ policy
            List<ResourcePolicy> policies = AuthorizeManager.getPoliciesActionFilter(context, this, Constants.READ);
            AuthorizeManager.addPolicies(context, policies, newLogo);

            log.info(LogManager.getHeader(context, "set_logo",
                    "site_id=" + getID() + "logo_bitstream_id="
                            + newLogo.getID()));
        }
        modified = true;
        return logo;
    }

    /**
     * Static method to return site Handle without creating a Site.
     * @return handle of the Site.
     */
    public static String getSiteHandle()
    {
        if (handle == null)
        {
            handle = HandleManager.getPrefix() + "/" + String.valueOf(SITE_ID);
        }
        return handle;
    }

    public void delete() throws SQLException, AuthorizeException, IOException {
         // Destroy DSO info
        destroyDSO();

        // Delete site row
        DatabaseManager.delete(context, tableRow);
    }

    @Override
    public void update() throws AuthorizeException, SQLException {
        updateDSO();
    }

    public void canEdit() throws AuthorizeException, SQLException {
        /*
        for (Community parent : getAllParents()) {
            if (AuthorizeManager.authorizeActionBoolean(context, parent, Constants.WRITE)) {
                return;
            }

            if (AuthorizeManager.authorizeActionBoolean(context, parent, Constants.ADD)) {
                return;
            }
        }
        AuthorizeManager.authorizeAction(context, this, Constants.WRITE);
        */
    }

   /**
     * Returns the name of the site.
     * 
     * @return the name string
     */
    @Override
    public String getName() {
        return tableRow.getStringColumn("name");
    }

    /**
     * Set the name of the site
     * 
     * @param name
     *            string name of the site
     */
    public void setName(String name) {
        tableRow.setColumn("name", name);
    }

    public String getURL()
    {
        return ConfigurationManager.getProperty("site.url");
    }

    /**
     * Return <code>true</code> if <code>other</code> is the same Site
     * as this object, <code>false</code> otherwise
     * 
     * @param other
     *            object to compare to
     * 
     * @return <code>true</code> if object passed in represents the same
     *         site as this object
     */
    public boolean equals(Object other) {
        if (!(other instanceof Site)) {
            return false;
        }
        return (getID() == ((Site) other).getID());
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(getID());
    }
}
