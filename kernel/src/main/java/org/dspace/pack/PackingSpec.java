/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.pack;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.dspace.authorize.AuthorizeException;
import org.dspace.content.MetadataField;
import org.dspace.content.MetadataSchema;
import org.dspace.content.MDValue;
import org.dspace.core.Context;
import org.dspace.core.LogManager;
import org.dspace.mxres.ExtensibleResource;
import org.dspace.storage.rdbms.DatabaseManager;
import org.dspace.storage.rdbms.TableRow;
import org.dspace.storage.rdbms.TableRowIterator;

/**
 * PackingSpecs are resources containing specifications
 * (instructions) on how content should be packaged.
 * This implementation stores these values in the database.
 * 
 * @author richardrodgers
 */

public class PackingSpec implements ExtensibleResource {

    private static Logger log = LoggerFactory.getLogger(PackingSpec.class);
    private Context context;
    private TableRow row;

    PackingSpec(Context context, TableRow row) {
        this.context = context;
        this.row = row;
    }

    public static PackingSpec find(Context context, int id) throws SQLException  {

        TableRow row = DatabaseManager.find(context, "packingspec", id);
        
        if (row == null) {
            log.debug(LogManager.getHeader(context, "find_packingspec",
                        "not_found,pakingspec_id=" + id));
            return null;
        }
        return new PackingSpec(context, row);
    }

    public static PackingSpec create(Context context) throws SQLException {
         TableRow row = DatabaseManager.create(context, "packingspec");
         return new PackingSpec(context, row);
    }

    public void update() throws SQLException {
        DatabaseManager.update(context, row);
    }

    public void delete() throws SQLException {
        // remove all values first
        DatabaseManager.updateQuery(context, "DELETE FROM mddisplay WHERE mdview_id = ?",
                                    getID());
        DatabaseManager.delete(context, row);
    }

    public int getID() {
        return row.getIntColumn("packingspec_id");
    }

    public String getName()  {
        return row.getStringColumn("name");
    }

    public void setName(String name) {
        row.setColumn("name", name);
    }

    public String getDescription() {
        return row.getStringColumn("description");
    }

    public void setDescription(String description) {
        row.setColumn("description", description);
    }

    public String getPacker() {
        return row.getStringColumn("packer");
    }

    public void setPacker(String packer) {
        row.setColumn("packer", packer);
    }

    public String getFormat() {
        return row.getStringColumn("format");
    }

    public void setFormat(String format) {
        row.setColumn("format", format);
    }

    public String getContentFilter() {
        return row.getStringColumn("content_filter");
    }

    public void setContentFilter(String filter) {
        row.setColumn("content_filter", filter);
    }

    public String getMetadataFilter() {
        return row.getStringColumn("metadata_filter");
    }

    public void setMetadataFilter(String filter) {
        row.setColumn("metadata_filter", filter);
    }

    public String getReferenceFilter() {
        return row.getStringColumn("reference_filter");
    }

    public void setReferenceFilter(String filter) {
        row.setColumn("reference_filter", filter);
    }

    public String getMimeType() {
        return row.getStringColumn("mimetype");
    }

    public void setMimeType(String mimetype) {
        row.setColumn("mimetype", mimetype);
    }

    public String getPackageId() {
        return row.getStringColumn("package_id");
    }

    public void setPackageId(String specId) {
        row.setColumn("package_id", specId);
    }
}
