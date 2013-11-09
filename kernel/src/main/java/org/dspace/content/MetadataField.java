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
import org.dspace.authorize.AuthorizeManager;
import org.dspace.core.Context;
import org.dspace.core.LogManager;
import org.dspace.storage.rdbms.DatabaseManager;
import org.dspace.storage.rdbms.TableRow;

/**
 * DSpace object that represents a metadata field, which is
 * defined by a combination of schema, element, and qualifier.
 * Every metadata element belongs in a field.
 *
 * @author Martin Hald
 * @see org.dspace.content.MetadataValue
 * @see org.dspace.content.MetadataSchema
 */
public class MetadataField {
    private int fieldID = 0;
    private int schemaID = 0;
    private String element;
    private String qualifier;
    private String scopeNote;

    /** log4j logger */
    private static Logger log = LoggerFactory.getLogger(MetadataField.class);

    /** DB table name */
    private static final String TABLE_NAME = "MetadataFieldRegistry";

     /** BD -> Object mapper */
    public static final ResultSetMapper<MetadataField> MAPPER = new Mapper();

    // cache of field by ID (Integer)
    private static Map<Integer, MetadataField> id2field = new HashMap<>();

     // cache initialization flag
    private static boolean cacheInitialized = false;


    /**
     * Default constructor.
     */
    public MetadataField()
    {
    }

    /**
     * Constructor creating a field within a schema.
     *
     * @param schema schema to which the field belongs
     */
    public MetadataField(MetadataSchema schema) {
        this.schemaID = schema.getSchemaID();
    }

    /**
     * Full constructor for new metadata field elements.
     *
     * @param schema schema to which the field belongs
     * @param element element of the field
     * @param qualifier qualifier of the field
     * @param scopeNote scope note of the field
     */
    public MetadataField(MetadataSchema schema, String element,
            String qualifier, String scopeNote) {
        this.schemaID = schema.getSchemaID();
        this.element = element;
        this.qualifier = qualifier;
        this.scopeNote = scopeNote;
    }

    /**
     * Full constructor for existing metadata field elements.
     *
     * @param schemaID schema to which the field belongs
     * @param fieldID database ID of field.
     * @param element element of the field
     * @param qualifier qualifier of the field
     * @param scopeNote scope note of the field
     */
    public MetadataField(int fieldID, int schemaID, String element,
            String qualifier, String scopeNote) {
        this.fieldID = fieldID;
        this.schemaID = schemaID;
        this.element = element;
        this.qualifier = qualifier;
        this.scopeNote = scopeNote;
    }

    /**
     * Get the element name.
     *
     * @return element name
     */
    public String getElement() {
        return element;
    }

    /**
     * Set the element name.
     *
     * @param element new value for element
     */
    public void setElement(String element) {
        this.element = element;
    }

    /**
     * Get the metadata field id.
     *
     * @return metadata field id
     */
    public int getFieldID() {
        return fieldID;
    }

    /**
     * Get the qualifier.
     *
     * @return qualifier
     */
    public String getQualifier() {
        return qualifier;
    }

    /**
     * Set the qualifier.
     *
     * @param qualifier new value for qualifier
     */
    public void setQualifier(String qualifier) {
        this.qualifier = qualifier;
    }

    /**
     * Get the schema record key.
     *
     * @return schema record key
     */
    public int getSchemaID() {
        return schemaID;
    }

    /**
     * Set the schema record key.
     *
     * @param schemaID new value for key
     */
    public void setSchemaID(int schemaID) {
        this.schemaID = schemaID;
    }

    /**
     * Get the scope note.
     *
     * @return scope note
     */
    public String getScopeNote() {
        return scopeNote;
    }

    /**
     * Set the scope note.
     *
     * @param scopeNote new value for scope note
     */
    public void setScopeNote(String scopeNote) {
        this.scopeNote = scopeNote;
    }

    /**
     * Creates a new metadata field.
     *
     * @param context
     *            DSpace context object
     * @throws IOException
     * @throws AuthorizeException
     * @throws SQLException
     * @throws NonUniqueMetadataException
     */
    public void create(Context context) throws IOException, AuthorizeException,
            SQLException, NonUniqueMetadataException {
        // Check authorisation: Only admins may create DC types
        if (!AuthorizeManager.isAdmin(context)) {
            throw new AuthorizeException(
                    "Only administrators may modify the metadata registry");
        }

        // Ensure the element and qualifier are unique within a given schema.
        if (findByElement(context, schemaID, element, qualifier) != null) {
            throw new NonUniqueMetadataException("Please make " + element + "."
                    + qualifier + " unique within schema #" + schemaID);
        }

        // Create a table row and update it with the values
        TableRow row = DatabaseManager.row("MetadataFieldRegistry");
        row.setColumn("metadata_schema_id", schemaID);
        row.setColumn("element", element);
        row.setColumn("qualifier", qualifier);
        row.setColumn("scope_note", scopeNote);
        DatabaseManager.insert(context, row);
        cache(this);

        // Remember the new row number
        this.fieldID = row.getIntColumn("metadata_field_id");

        log.info(LogManager.getHeader(context, "create_metadata_field",
                "metadata_field_id=" + row.getIntColumn("metadata_field_id")));
    }

    /**
     * Retrieves the metadata field from the database.
     *
     * @param context dspace context
     * @param schemaID schema by ID
     * @param element element name
     * @param qualifier qualifier (may be ANY or null)
     * @return recalled metadata field
     * @throws SQLException
     * @throws AuthorizeException
     */
    public static MetadataField findByElement(Context context, int schemaID,
            String element, String qualifier) throws SQLException, AuthorizeException {
        StringBuilder query = new StringBuilder("SELECT * FROM ");
        query.append(TABLE_NAME).append(" WHERE metadata_schema_id = ? AND element = ? AND qualifier ");
        if (qualifier == null) {
            query.append("is NULL");
        } else { 
            query.append("= ?");
        }
        Query<Map<String, Object>> q = context.getHandle().createQuery(query.toString())
            .bind(0, schemaID).bind(1, element);
        if (qualifier != null) {
            q.bind(2, qualifier);
        }

        return q.map(MAPPER).first();
    }

    /**
     * Retrieve all Dublin Core types from the registry
     *
     * @param context dspace context
     * @return an array of all the Dublin Core types
     * @throws SQLException
     */
    public static List<MetadataField> findAll(Context context) throws SQLException {
        return context.getHandle()
               .createQuery("SELECT mfr.* FROM " + TABLE_NAME + " mfr, MetadataSchemaRegistry msr WHERE mfr.metadata_schema_id= msr.metadata_schema_id " +
               " ORDER BY msr.short_id, mfr.element, mfr.qualifier")
               .map(MAPPER).list();
    }

    /**
     * Return all metadata fields that are found in a given schema.
     *
     * @param context dspace context
     * @param schemaID schema by db ID
     * @return array of metadata fields
     * @throws SQLException
     */
    public static List<MetadataField> findAllInSchema(Context context, int schemaID)
            throws SQLException {
        return context.getHandle()
               .createQuery("SELECT * FROM " + TABLE_NAME + " WHERE metadata_schema_id = ? " +
               "ORDER BY element, qualifier")
               .bind(0, schemaID).map(MAPPER).list();
    }

    /**
     * Update the metadata field in the database.
     *
     * @param context dspace context
     * @throws SQLException
     * @throws AuthorizeException
     * @throws NonUniqueMetadataException
     * @throws IOException
     */
    public void update(Context context) throws SQLException,
            AuthorizeException, NonUniqueMetadataException, IOException {
        // Check authorisation: Only admins may update the metadata registry
        if (!AuthorizeManager.isAdmin(context)) {
            throw new AuthorizeException(
                    "Only administrators may modiffy the Dublin Core registry");
        }

        // Ensure the element and qualifier are unique within a given schema.
        if (hasElement(context, schemaID, element, qualifier)) {
            throw new NonUniqueMetadataException("Please make " + element + "."
                    + qualifier + "unique");
        }

         context.getHandle()
        .createStatement("UPDATE " + TABLE_NAME + " SET metadata_schema_id = ?, element = ?, qualifier = ?, scope_note = ? WHERE metadata_schema_id = ?")
        .bind(0, schemaID).bind(1, element).bind(2, qualifier).bind(3, scopeNote).bind(4, fieldID).execute();

        cache(this);

        log.info(LogManager.getHeader(context, "update_metadatafieldregistry",
                "metadata_field_id=" + getFieldID() + "element=" + getElement()
                        + "qualifier=" + getQualifier()));
    }

    /**
     * Return true if and only if the schema has a field with the given element
     * and qualifier pair.
     *
     * @param context dspace context
     * @param schemaID schema by ID
     * @param element element name
     * @param qualifier qualifier name
     * @return true if the field exists
     * @throws SQLException
     * @throws AuthorizeException
     */
    private static boolean hasElement(Context context, int schemaID,
            String element, String qualifier) throws SQLException, AuthorizeException {
        return findByElement(context, schemaID, element, qualifier) != null;
    }

    /**
     * Delete the metadata field.
     *
     * @param context dspace context
     * @throws SQLException
     * @throws AuthorizeException
     */
    public void delete(Context context) throws SQLException, AuthorizeException {
        // Check authorisation: Only admins may create DC types
        if (!AuthorizeManager.isAdmin(context)) {
            throw new AuthorizeException(
                    "Only administrators may modify the metadata registry");
        }

        log.info(LogManager.getHeader(context, "delete_metadata_field",
                "metadata_field_id=" + getFieldID()));

        DatabaseManager.delete(context, TABLE_NAME, getFieldID());
        decache(this);
    }

    /**
     * Return the HTML FORM key for the given field.
     *
     * @param schema
     * @param element
     * @param qualifier
     * @return HTML FORM key
     */
    public static String formKey(String schema, String element, String qualifier) {
        if (qualifier == null) {
            return schema + "_" + element;
        } else {
            return schema + "_" + element + "_" + qualifier;
        }
    }

    /**
     * Find the field corresponding to the given numeric ID.  The ID is
     * a database key internal to DSpace.
     *
     * @param context
     *            context, in case we need to read it in from DB
     * @param id
     *            the metadata field ID
     * @return the metadata field object
     * @throws SQLException
     */
    public static MetadataField find(Context context, int id) throws SQLException {
        if (! cacheInitialized) {
            initCache(context);
        }
        return id2field.get(id);
    }

    /**
     * Maps database result sets to metadata fiels instances.
     * ResultSetMapper interface contract
     *
     */
    static class Mapper implements ResultSetMapper<MetadataField> {
        @Override
        public MetadataField map(int index, ResultSet rs, StatementContext sctx) throws SQLException {
            return new MetadataField(rs.getInt(1), rs.getInt(2), rs.getString(3), rs.getString(4), rs.getString(5));
        }
    }

    // set a value in the cache e.g. after something modifies DB state.
    private static void cache(MetadataField mdf) {
        id2field.put(mdf.fieldID, mdf);
    }

    // remove a value from the cache e.g. after something modifies DB state.
    private static void decache(MetadataField mdf) {
        id2field.remove(mdf.fieldID);
    }
    
    // load caches if necessary
    private static synchronized void initCache(Context context) throws SQLException {
        log.info("Loading MetadataField elements into cache.");

        for (MetadataField mdf : findAll(context)) {
            cache(mdf);
        }
        cacheInitialized = true;
    }

    /**
     * Return <code>true</code> if <code>other</code> is the same MetadataField
     * as this object, <code>false</code> otherwise
     *
     * @param other
     *            object to compare to
     *
     * @return <code>true</code> if object passed in represents the same
     *         MetadataField as this object
     */
    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final MetadataField other = (MetadataField) obj;
        if (! Objects.equals(this.fieldID, other.fieldID)) {
            return false;
        }
        if (! Objects.equals(this.schemaID, other.schemaID)) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 47 * hash + this.fieldID;
        hash = 47 * hash + this.schemaID;
        return hash;
    }
}
