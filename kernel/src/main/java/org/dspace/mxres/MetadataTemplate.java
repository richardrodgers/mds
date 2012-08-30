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
 * MetadataTemplates are resources containing sets of metadata values.
 * They are typically used to pre-populate items or other objects in ingest
 * workflows. This implementation stores these values in the database.
 * 
 * @author richardrodgers
 */

public class MetadataTemplate implements ExtensibleResource {
	
	private static Logger log = LoggerFactory.getLogger(MetadataTemplate.class);
	private Context context;	
	private TableRow row;
	
	MetadataTemplate(Context context, TableRow row) {
		this.context = context;
		this.row = row;
	}
	
	public static MetadataTemplate find(Context context, int id) throws SQLException  {
		
        TableRow row = DatabaseManager.find(context, "mdtemplate", id);
        
        if (row == null) {
            log.debug(LogManager.getHeader(context, "find_mdtemplate",
                        "not_found,mdtemplate_id=" + id));
            return null;
        }
        return new MetadataTemplate(context, row);
	}
	
	public static MetadataTemplate create(Context context) throws SQLException {
		 TableRow row = DatabaseManager.create(context, "mdtemplate");
		 return new MetadataTemplate(context, row);
	}
	
	public void delete() throws SQLException {
	    // remove all values first
	    DatabaseManager.updateQuery(context, "DELETE FROM mdtemplatevalue WHERE mdtemplate_id = ?",
	        		               getID());
	    DatabaseManager.delete(context, row);
	}
	
	public int getID() {
		return row.getIntColumn("mdtemplate_id");
	}
	
	public String getDescription() {
		return row.getStringColumn("description");
	}
	
	public void setDescription(String description) {
		row.setColumn("description", description);
	}
	
	public void addTemplateValues(List<MDValue> valueList) throws AuthorizeException, SQLException {
		for (MDValue value : valueList) {
			addTemplateValue(value);
		}
	}
	
	public void addTemplateValue(MDValue value) throws AuthorizeException, SQLException {
	  	MetadataSchema schema = MetadataSchema.find(context, value.getSchema());
    	MetadataField field = MetadataField.findByElement(context, schema.getSchemaID(),
    			                                          value.getElement(), value.getQualifier());
    	
        // Create a table row and update it with the values
    	TableRow valRow = DatabaseManager.create(context, "mdtemplatevalue");
        valRow.setColumn("mdtemplate_id", getID());
        valRow.setColumn("metadata_field_id", field.getFieldID());
        valRow.setColumn("text_value", value.getValue());
        valRow.setColumn("text_lang", value.getLanguage());
		DatabaseManager.update(context, valRow);
	}
	
	public List<MDValue> getTemplateValues() throws SQLException {
		List<MDValue> valueList = new ArrayList<>();
		try (TableRowIterator tri = DatabaseManager.queryTable(context, "mdtemplatevalue",
	                "SELECT * FROM mdtemplatevalue WHERE mdtemplate_id = ?",
	                 row.getLongColumn("mdtemplate_id"))) {
	        while (tri.hasNext()) {
	        	TableRow resultRow = tri.next();
	        	 // Get the associated metadata field and schema information
                int fieldID = resultRow.getIntColumn("metadata_field_id");
                MetadataField field = MetadataField.find(context, fieldID);
                if (field == null) {
                    log.error("Loading object - cannot find metadata field " + fieldID);
                } else {
                    MetadataSchema schema = MetadataSchema.find(context, field.getSchemaID());
                    if (schema == null) {
                        log.error("Loading object - cannot find metadata schema " +
                                  field.getSchemaID() + ", field " + fieldID);
                    } else {
                        // Add MDValue object to list
                        valueList.add(new MDValue(schema.getName(),
                        					      field.getElement(),
                                                  field.getQualifier(),
                                                  resultRow.getStringColumn("text_lang"),
                                                  resultRow.getStringColumn("text_value")));
                    }
                }
	        }
		}
	    return valueList;
	}
}
