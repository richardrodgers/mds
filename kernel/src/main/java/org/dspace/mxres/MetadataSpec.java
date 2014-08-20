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
 * MetadataSpecs are resources containing sets of metadata field descriptions.
 * They are typically used to inform input user interfaces, since the descriptions
 * include labels, type hints, etc.
 * This implementation stores these values in the database.
 * 
 * @author richardrodgers
 */

public class MetadataSpec implements ExtensibleResource {

    private static Logger log = LoggerFactory.getLogger(MetadataSpec.class);

    private TableRow row;
    private List<MDFieldSpec> fsList;

    MetadataSpec(Context context, TableRow row) throws SQLException {
        this.row = row;
        this.fsList = loadFieldSpecs(context);
    }

    public static MetadataSpec find(Context context, int id) throws SQLException  {

        TableRow row = DatabaseManager.find(context, "mdspec", id);
        
        if (row == null) {
            log.debug(LogManager.getHeader(context, "find_mdspec",
                        "not_found,mdspec_id=" + id));
            return null;
        }
        return new MetadataSpec(context, row);
    }

    public static MetadataSpec create(Context context) throws SQLException {
         TableRow row = DatabaseManager.create(context, "mdspec");
         return new MetadataSpec(context, row);
    }

    public void delete(Context context) throws SQLException {
        // remove all values first
        DatabaseManager.updateQuery(context, "DELETE FROM mdfldspec WHERE mdspec_id = ?",
                                    getID());
        DatabaseManager.delete(context, row);
    }

    public int getID() {
        return row.getIntColumn("mdspec_id");
    }

    public String getDescription() {
        return row.getStringColumn("description");
    }

    public void setDescription(String description) {
        row.setColumn("description", description);
    }

    public List<MDFieldSpec> getFieldSpecs() {
        return fsList;
    }

    public void addFieldSpecs(Context context, List<MDFieldSpec> fieldList) throws AuthorizeException, SQLException {
        for (MDFieldSpec field : fieldList) {
            addFieldSpec(context, field);
        }
    }

    public void addFieldSpec(Context context, MDFieldSpec sfield) throws AuthorizeException, SQLException {
        String[] parts = sfield.getFieldKey().split("\\.");
        MetadataSchema schema = MetadataSchema.find(context, parts[0]);
        String qual = null;
        if (parts.length == 3) {
            qual = parts[2];
        }
        MetadataField field = MetadataField.findByElement(context, schema.getSchemaID(), parts[1], qual);
                                                         
        // Create a table row and update it with the values
        TableRow valRow = DatabaseManager.create(context, "mdfldspec");
        valRow.setColumn("mdspec_id", getID());
        valRow.setColumn("metadata_field_id", field.getFieldID());
        valRow.setColumn("altname", sfield.getAltName());
        valRow.setColumn("label", sfield.getLabel());
        valRow.setColumn("description", sfield.getDescription());
        valRow.setColumn("cardinality", sfield.getCardinality());
        valRow.setColumn("input_type", sfield.getInputType());
        valRow.setColumn("locked", sfield.isLocked());
        valRow.setColumn("disp_lang", sfield.getLanguage());
        DatabaseManager.update(context, valRow);
    }

    private List<MDFieldSpec> loadFieldSpecs(Context context) throws SQLException {
        List<MDFieldSpec> fieldList = new ArrayList<>();
        try (TableRowIterator tri = DatabaseManager.queryTable(context, "mdfldspec",
                    "SELECT * FROM mdfldspec WHERE mdspec_id = ?", getID())) {
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
                fieldList.add(new MDFieldSpec(sb.toString(),
                                              resultRow.getStringColumn("altname"),
                                              resultRow.getStringColumn("label"),
                                              resultRow.getStringColumn("description"),
                                              resultRow.getStringColumn("cardinality"),
                                              resultRow.getStringColumn("input_type"),
                                              resultRow.getBooleanColumn("locked"),
                                              resultRow.getStringColumn("disp_lang")));
            }
        }
        return fieldList;
    }
}
