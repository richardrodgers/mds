/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.content;

import java.sql.SQLException;
import java.sql.ResultSet;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

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
 * Class representing a schema in DSpace.
 * <p>
 * The schema object exposes a name which can later be used to generate
 * namespace prefixes in RDF or XML, e.g. the core DSpace Dublin Core schema
 * would have a name of <code>'dc'</code>.
 * </p>
 *
 * @author Martin Hald
 * @see org.dspace.content.MetadataValue
 * @see org.dspace.content.MetadataField
 */
public class MetadataSchema
{
    /** log4j logger */
    private static Logger log = LoggerFactory.getLogger(MetadataSchema.class);

    /** Numeric Identifier of built-in Dublin Core schema. */
    public static final int DC_SCHEMA_ID = 1;

    /** Short Name of built-in Dublin Core schema. */
    public static final String DC_SCHEMA = "dc";

    /** DB table name */
    private static final String TABLE_NAME = "MetadataSchemaRegistry";

    /** BD -> Object mapper */
    public static final ResultSetMapper<MetadataSchema> MAPPER = new Mapper();

    /** numeric identifier (primary DB key) of schema */
    private int schemaID;
    private String namespace;
    private String name;

    // cache of schema by ID (Integer)
    private static Map<Integer, MetadataSchema> id2schema = new HashMap<>();

    // cache of schema by short name
    private static Map<String, MetadataSchema> name2schema = new HashMap<>();

    // cache initialization flag
    private static boolean cacheInitialized = false;

    /**
     * Default constructor.
     */
    public MetadataSchema() {
    }

    /**
     * Object constructor.
     *
     * @param schemaID  database key ID number
     * @param namespace  XML namespace URI
     * @param name  short name of schema
     */
    public MetadataSchema(int schemaID, String namespace, String name)  {
        this.schemaID = schemaID;
        this.namespace = namespace;
        this.name = name;
    }

    /**
     * Immutable object constructor for creating a new schema.
     *
     * @param namespace  XML namespace URI
     * @param name  short name of schema
     */
    public MetadataSchema(String namespace, String name) {
        this.namespace = namespace;
        this.name = name;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final MetadataSchema other = (MetadataSchema) obj;
        if (! Objects.equals(this.schemaID, other.schemaID)) {
            return false;
        }
        if (! Objects.equals(this.namespace, other.namespace)) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        int hash = 5;
        hash = 67 * hash + this.schemaID;
        hash = 67 * hash + (this.namespace != null ? this.namespace.hashCode() : 0);
        return hash;
    }

    /**
     * Get the schema namespace.
     *
     * @return namespace String
     */
    public String getNamespace() {
        return namespace;
    }

    /**
     * Set the schema namespace.
     *
     * @param namespace XML namespace URI
     */
    public void setNamespace(String namespace) {
        this.namespace = namespace;
    }

    /**
     * Get the schema name.
     *
     * @return name String
     */
    public String getName() {
        return name;
    }

    /**
     * Set the schema name.
     *
     * @param name  short name of schema
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * Get the schema record key number.
     *
     * @return schema record key
     */
    public int getSchemaID() {
        return schemaID;
    }

    /**
     * Creates a new metadata schema in the database, out of this object.
     *
     * @param context
     *            DSpace context object
     * @throws SQLException
     * @throws AuthorizeException
     * @throws NonUniqueMetadataException
     */
    public void create(Context context) throws SQLException,
            AuthorizeException, NonUniqueMetadataException {
        // Check authorisation: Only admins may create metadata schemas
        if (!AuthorizeManager.isAdmin(context)) {
            throw new AuthorizeException(
                    "Only administrators may modify the metadata registry");
        }

        // Ensure the schema name is unique
        if (findByName(context, name) != null) {
            throw new NonUniqueMetadataException("Please make the name " + name
                    + " unique");
        }
        
        // Ensure the schema namespace is unique
        if (findByNamespace(context, namespace) != null) {
            throw new NonUniqueMetadataException("Please make the namespace " + namespace
                    + " unique");
        }

        // Create a table row and update it with the values
        TableRow row = DatabaseManager.row(TABLE_NAME);
        row.setColumn("namespace", namespace);
        row.setColumn("short_id", name);
        DatabaseManager.insert(context, row);

        // Remember the new row number
        this.schemaID = row.getIntColumn("metadata_schema_id");

        // update cache
        cache(this);

        log.info(LogManager.getHeader(context, "create_metadata_schema",
                 "metadata_schema_id=" + row.getIntColumn("metadata_schema_id")));
    }

    /**
     * Get the schema object corresponding to this namespace URI.
     *
     * @param context DSpace context
     * @param namespace namespace URI to match
     * @return metadata schema object or null if none found.
     * @throws SQLException
     */
    public static MetadataSchema findByNamespace(Context context, String namespace) throws SQLException {
        return context.getHandle()
               .createQuery("SELECT * FROM " + TABLE_NAME + " WHERE namespace= ? ")
               .bind(0, namespace).map(MAPPER).first();
    }

    /**
     * Get the schema object corresponding to this short name.
     *
     * @param context DSpace context
     * @param name short name to match
     * @return metadata schema object or null if none found.
     * @throws SQLException
     */
    public static MetadataSchema findByName(Context context, String name) throws SQLException {
        return context.getHandle()
               .createQuery("SELECT * FROM " + TABLE_NAME + " WHERE short_id= ? ")
               .bind(0, name).map(MAPPER).first();
    }

    /**
     * Update the metadata schema in the database.
     *
     * @param context DSpace context
     * @throws SQLException
     * @throws AuthorizeException
     * @throws NonUniqueMetadataException
     */
    public void update(Context context) throws SQLException,
            AuthorizeException, NonUniqueMetadataException {
        // Check authorisation: Only admins may update the metadata registry
        if (!AuthorizeManager.isAdmin(context)) {
            throw new AuthorizeException(
                    "Only administrators may modify the metadata registry");
        }

        // Ensure the schema name is unique
        if (findByName(context, name) != null) {
            throw new NonUniqueMetadataException("Please make the name " + name + " unique");
        }

        // Ensure the schema namespace is unique
        if (findByNamespace(context, namespace) != null) {
            throw new NonUniqueMetadataException("Please make the namespace " + namespace + " unique");
        }
        
        context.getHandle()
        .createStatement("UPDATE " + TABLE_NAME + " SET namespace = ?, short_id = ? WHERE metadata_schema_id = ?")
        .bind(0, namespace).bind(1, name).bind(2, schemaID).execute();

        cache(this);

        log.info(LogManager.getHeader(context, "update_metadata_schema",
                "metadata_schema_id=" + getSchemaID() + "namespace="
                        + getNamespace() + "name=" + getName()));
    }

    /**
     * Delete the metadata schema.
     *
     * @param context DSpace context
     * @throws SQLException
     * @throws AuthorizeException
     */
    public void delete(Context context) throws SQLException, AuthorizeException  {
        // Check authorisation: Only admins may create DC types
        if (!AuthorizeManager.isAdmin(context)) {
            throw new AuthorizeException(
                    "Only administrators may modify the metadata registry");
        }

        log.info(LogManager.getHeader(context, "delete_metadata_schema",
                "metadata_schema_id=" + getSchemaID()));

        DatabaseManager.delete(context, TABLE_NAME, getSchemaID());
        decache(this);
    }

    /**
     * Return all metadata schemas.
     *
     * @param context DSpace context
     * @return array of metadata schemas
     * @throws SQLException
     */
    public static List<MetadataSchema> findAll(Context context) throws SQLException {
        return context.getHandle()
               .createQuery("SELECT * FROM " + TABLE_NAME + " ORDER BY metadata_schema_id")
               .map(MAPPER).list();
    }

    /**
     * Get the schema corresponding with this numeric ID.
     * The ID is a database key internal to DSpace.
     *
     * @param context
     *            context, in case we need to read it in from DB
     * @param id
     *            the schema ID
     * @return the metadata schema object
     * @throws SQLException
     */
    public static MetadataSchema find(Context context, int id) throws SQLException {
        if (!cacheInitialized) {
            initCache(context);
        }
        return id2schema.get(id);
    }

    /**
     * Get the schema corresponding with this short name.
     *
     * @param context
     *            context, in case we need to read it in from DB
     * @param shortName
     *            the short name for the schema
     * @return the metadata schema object
     * @throws SQLException
     */
    public static MetadataSchema find(Context context, String shortName) throws SQLException {
        // If we are not passed a valid schema name then return
        if (shortName == null)  {
            return null;
        }

        if (!cacheInitialized) {
            initCache(context);
        }
        return name2schema.get(shortName);
    }

    /**
     * Maps database result sets to metadata schema instances.
     * ResultSetMapper interface contract
     *
     */
    static class Mapper implements ResultSetMapper<MetadataSchema> {
        @Override
        public MetadataSchema map(int index, ResultSet rs, StatementContext sctx) throws SQLException {
            return new MetadataSchema(rs.getInt(1), rs.getString(2), rs.getString(3));
        }
    }

    // load caches if necessary
    private static synchronized void initCache(Context context) throws SQLException {
        log.info("Loading schema cache for fast finds");
           
        for (MetadataSchema mds : findAll(context)) {
            cache(mds);
        }
        cacheInitialized = true;
    }

    private static void cache(MetadataSchema mds) {
        id2schema.put(mds.schemaID, mds);
        name2schema.put(mds.name, mds);
    }

    private static void decache(MetadataSchema mds) {
        id2schema.remove(mds.schemaID);
        name2schema.remove(mds.name);
    }
}
