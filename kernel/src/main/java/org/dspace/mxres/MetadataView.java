/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.mxres;

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
import org.dspace.storage.rdbms.DatabaseManager;
import org.dspace.storage.rdbms.TableRow;
import org.dspace.storage.rdbms.TableRowIterator;

/**
 * MetadataViews are resources containing sets of metadata field descriptions.
 * They are typically used to render user interfaces, since the descriptions
 * include labels, type hints, etc.
 * This implementation stores these values in the database.
 * 
 * @author richardrodgers
 */

public class MetadataView implements ExtensibleResource {

    private static Logger log = LoggerFactory.getLogger(MetadataView.class);

    private TableRow row;
    private List<MDFieldDisplay> fdList;

    MetadataView(Context context, TableRow row) throws SQLException {
        this.row = row;
        this.fdList = loadViewFields(context);
    }

    public static MetadataView find(Context context, int id) throws SQLException  {

        TableRow row = DatabaseManager.find(context, "mdview", id);
        
        if (row == null) {
            log.debug(LogManager.getHeader(context, "find_mdview",
                        "not_found,mdview_id=" + id));
            return null;
        }
        return new MetadataView(context, row);
    }

    public static MetadataView create(Context context) throws SQLException {
         TableRow row = DatabaseManager.create(context, "mdview");
         return new MetadataView(context, row);
    }

    public void delete(Context context) throws SQLException {
        // remove all values first
        DatabaseManager.updateQuery(context, "DELETE FROM mddisplay WHERE mdview_id = ?",
                                    getID());
        DatabaseManager.delete(context, row);
    }

    public int getID() {
        return row.getIntColumn("mdview_id");
    }

    public String getDescription() {
        return row.getStringColumn("description");
    }

    public void setDescription(String description) {
        row.setColumn("description", description);
    }

    public List<MDFieldDisplay> getViewFields() {
        return fdList;
    }

    public void addViewFields(Context context, List<MDFieldDisplay> fieldList) throws AuthorizeException, SQLException {
        for (MDFieldDisplay field : fieldList) {
            addViewField(context, field);
        }
    }

    public void addViewField(Context context, MDFieldDisplay dfield) throws AuthorizeException, SQLException {
        String[] parts = dfield.getFieldKey().split("\\.");
        MetadataSchema schema = MetadataSchema.find(context, parts[0]);
        String qual = null;
        if (parts.length == 3) {
            qual = parts[2];
        }
        MetadataField field = MetadataField.findByElement(context, schema.getSchemaID(), parts[1], qual);
                                                         
        // Create a table row and update it with the values
        TableRow valRow = DatabaseManager.create(context, "mddisplay");
        valRow.setColumn("mdview_id", getID());
        valRow.setColumn("metadata_field_id", field.getFieldID());
        valRow.setColumn("altname", dfield.getAltName());
        valRow.setColumn("label", dfield.getLabel());
        valRow.setColumn("render_type", dfield.getRenderType());
        valRow.setColumn("wrapper", dfield.getWrapper());
        valRow.setColumn("disp_lang", dfield.getLanguage());
        DatabaseManager.update(context, valRow);
    }

    private List<MDFieldDisplay> loadViewFields(Context context) throws SQLException {
        List<MDFieldDisplay> fieldList = new ArrayList<>();
        try (TableRowIterator tri = DatabaseManager.queryTable(context, "mddisplay",
                    "SELECT * FROM mddisplay WHERE mdview_id = ?", getID())) {
            while (tri.hasNext()) {
                TableRow resultRow = tri.next();
                // Add object to list
                MetadataField mdf = MetadataField.find(context, resultRow.getIntColumn("metadata_field_id"));
                MetadataSchema mds = MetadataSchema.find(context, mdf.getSchemaID());
                StringBuilder sb = new StringBuilder(mds.getName());
                sb.append(".").append(mdf.getElement());
                if (mdf.getQualifier() != null) {
                    sb.append(".").append(mdf.getQualifier());
                }
                //log.info("Adding field: " + sb.toString());
                fieldList.add(new MDFieldDisplay(sb.toString(),
                                                 resultRow.getStringColumn("altname"),
                                                 resultRow.getStringColumn("label"),
                                                 resultRow.getStringColumn("render_type"),
                                                 resultRow.getStringColumn("wrapper"),
                                                 resultRow.getStringColumn("disp_lang")));
            }
        }
        return fieldList;
    }
}
