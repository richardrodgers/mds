/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.content;

import java.io.IOException;
import java.sql.SQLException;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import org.skife.jdbi.v2.Query;
import org.skife.jdbi.v2.StatementContext;
import org.skife.jdbi.v2.tweak.ResultSetMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.dspace.authorize.AuthorizeException;
import org.dspace.core.Context;
import org.dspace.core.LogManager;
import org.dspace.storage.rdbms.DatabaseManager;
import org.dspace.storage.rdbms.TableRow;
import org.dspace.storage.rdbms.TableRowIterator;

/**
 * Database access class representing a Dublin Core metadata value.
 * It represents a value of a given <code>MetadataField</code> on an Item.
 * (The Item can have many values of the same field.)  It contains element, qualifier, value and language.
 * the field (which names the schema, element, and qualifier), language,
 * and a value.
 *
 * @author Martin Hald
 * @see org.dspace.content.MetadataSchema
 * @see org.dspace.content.MetadataField
 */
public class MetadataValue
{
    /** The reference to the metadata field */
    private int fieldId;

    /** The primary key for the metadata value */
    private int valueId;

    /** The reference to the DSpace object */
    private int dsoId;

    /** The value of the field */
    public String value;

    /** The language of the field, may be <code>null</code> */
    public String language;

    /** The position of the record. */
    public int place = 1;

    /** log4j logger */
    private static Logger log = LoggerFactory.getLogger(MetadataValue.class);

     /** DB table name */
    private static final String TABLE_NAME = "MetadataValue";

     /** BD -> Object mapper */
    public static final ResultSetMapper<MetadataValue> MAPPER = new Mapper();

    /**
     * Construct the metadata object from the matching database row.
     *
     * @param row database row to use for contents
     */
    public MetadataValue(TableRow row) {
        if (row != null) {
            fieldId = row.getIntColumn("metadata_field_id");
            valueId = row.getIntColumn("metadata_value_id");
            dsoId = row.getIntColumn("dso_id");
            value = row.getStringColumn("text_value");
            language = row.getStringColumn("text_lang");
            place = row.getIntColumn("place");
        }
    }

    /**
     * Default constructor.
     */
    public MetadataValue() {
    }

    /**
     * Constructor the metadata object from the matching database row.
     *
     * @param row database row to use for contents
     */
    public MetadataValue(int valueId, int dsoId, int fieldId, String value, String language, int place) {
        this.valueId = valueId;
        this.dsoId = dsoId;
        this.fieldId = fieldId;
        this.value = value;
        this.language = language;
        this.place = place;
    }

    /**
     * Constructor to create a value for a given field.
     *
     * @param field initial value for field
     */
    public MetadataValue(MetadataField field) {
        this.fieldId = field.getFieldID();
    }

    /**
     * Get the field ID the metadata value represents.
     *
     * @return metadata field ID
     */
    public int getFieldId() {
        return fieldId;
    }

    /**
     * Set the field ID that the metadata value represents.
     *
     * @param fieldId new field ID
     */
    public void setFieldId(int fieldId) {
        this.fieldId = fieldId;
    }

    /**
     * Get the item ID.
     *
     * @return item ID
     */
    public int getDsoId() {
        return dsoId;
    }

    /**
     * Set the item ID.
     *
     * @param itemId new item ID
     */
    public void setDsoId(int dsoId) {
        this.dsoId = dsoId;
    }

    /**
     * Get the language (e.g. "en").
     *
     * @return language
     */
    public String getLanguage() {
        return language;
    }

    /**
     * Set the language (e.g. "en").
     *
     * @param language new language
     */
    public void setLanguage(String language) {
        this.language = language;
    }

    /**
     * Get the place ordering.
     *
     * @return place ordering
     */
    public int getPlace() {
        return place;
    }

    /**
     * Set the place ordering.
     *
     * @param place new place (relative order in series of values)
     */
    public void setPlace(int place) {
        this.place = place;
    }

    /**
     * Get the value ID.
     *
     * @return value ID
     */
    public int getValueId() {
        return valueId;
    }

    /**
     * Get the metadata value.
     *
     * @return metadata value
     */
    public String getValue() {
        return value;
    }

    /**
     * Set the metadata value
     *
     * @param value new metadata value
     */
    public void setValue(String value) {
        this.value = value;
    }

    /**
     * Creates a new metadata value.
     *
     * @param context
     *            DSpace context object
     * @throws SQLException
     * @throws AuthorizeException
     */
    public void create(Context context) throws SQLException, AuthorizeException {
        // Create a table row and update it with the values
        TableRow row = DatabaseManager.row("MetadataValue");
        row.setColumn("dso_id", dsoId);
        row.setColumn("metadata_field_id", fieldId);
        row.setColumn("text_value", value);
        row.setColumn("text_lang", language);
        row.setColumn("place", place);
        DatabaseManager.insert(context, row);

        // Remember the new row number
        this.valueId = row.getIntColumn("metadata_value_id");

//        log.info(LogManager.getHeader(context, "create_metadata_value",
//                "metadata_value_id=" + valueId));
    }

    /**
     * Retrieves the metadata value from the database.
     *
     * @param context dspace context
     * @param valueId database key id of value
     * @return recalled metadata value
     * @throws IOException
     * @throws SQLException
     * @throws AuthorizeException
     */
    public static MetadataValue find(Context context, int valueId)
            throws IOException, SQLException, AuthorizeException {
        return context.getHandle()
               .createQuery("SELECT * FROM " + TABLE_NAME + " WHERE metadata_value_id = ? ")
               .bind(0, valueId).map(MAPPER).first();
    }

    /**
     * Retrieves the metadata values for a given field from the database.
     *
     * @param context dspace context
     * @param fieldId field whose values to look for
     * @return a collection of metadata values
     * @throws IOException
     * @throws SQLException
     * @throws AuthorizeException
     */
    public static List<MetadataValue> findByField(Context context, int fieldId)
            throws IOException, SQLException, AuthorizeException {
        return context.getHandle()
               .createQuery("SELECT * FROM " + TABLE_NAME + " WHERE metadata_field_id = ? ")
               .bind(0, fieldId).map(MAPPER).list();
    }

    /**
     * Update the metadata value in the database.
     *
     * @param context dspace context
     * @throws SQLException
     * @throws AuthorizeException
     */
    public void update(Context context) throws SQLException, AuthorizeException {

        context.getHandle()
        .createStatement("UPDATE " + TABLE_NAME + " SET dso_id = ?, metadata_field_id = ?, text_value = ?, text_lang = ?, place = ? WHERE metadata_value_id = ?")
        .bind(0, dsoId).bind(1, fieldId).bind(2, value).bind(3, language).bind(4, place).bind(5, valueId).execute();

        log.info(LogManager.getHeader(context, "update_metadatavalue",
                "metadata_value_id=" + getValueId()));
    }

    /**
     * Delete the metadata field.
     *
     * @param context dspace context
     * @throws SQLException
     * @throws AuthorizeException
     */
    public void delete(Context context) throws SQLException, AuthorizeException {
        log.info(LogManager.getHeader(context, "delete_metadata_value",
                " metadata_value_id=" + getValueId()));
        DatabaseManager.delete(context, TABLE_NAME, getValueId());
    }

    /**
     * Return <code>true</code> if <code>other</code> is the same MetadataValue
     * as this object, <code>false</code> otherwise
     *
     * @param obj
     *            object to compare to
     *
     * @return <code>true</code> if object passed in represents the same
     *         MetadataValue as this object
     */
    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final MetadataValue other = (MetadataValue) obj;
        if (! Objects.equals(this.fieldId, other.fieldId)) {
            return false;
        }
        if (! Objects.equals(this.valueId, other.valueId)) {
            return false;
        }
        if (! Objects.equals(this.dsoId, other.dsoId)) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 47 * hash + this.fieldId;
        hash = 47 * hash + this.valueId;
        hash = 47 * hash + this.dsoId;
        return hash;
    }

     /**
     * Maps database result sets to metadata value instances.
     * ResultSetMapper interface contract
     *
     */
    static class Mapper implements ResultSetMapper<MetadataValue> {
        @Override
        public MetadataValue map(int index, ResultSet rs, StatementContext sctx) throws SQLException {
            return new MetadataValue(rs.getInt(0), rs.getInt(1), rs.getInt(2), rs.getString(3), rs.getString(4), rs.getInt(5));
        }
    }
}
