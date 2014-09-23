/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.content;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.dspace.authorize.AuthorizeException;
import org.dspace.core.Constants;
import org.dspace.core.Context;
import org.dspace.event.Event;
import org.dspace.event.ContentEvent.EventType;
import org.dspace.storage.bitstore.BitstreamStorageManager;
import org.dspace.storage.rdbms.DatabaseManager;
import org.dspace.storage.rdbms.TableRow;
import org.dspace.storage.rdbms.TableRowIterator;

/**
 * Abstract base class for DSpace objects
 */
public abstract class DSpaceObject
{
    /** logger */
    private static Logger log = LoggerFactory.getLogger(DSpaceObject.class);
    
    /** Our context */
    protected Context context;
    
    /** The row in the table representing this object */
    protected TableRow tableRow;
        
    // Object identifier (UUID)
    private String objectId;
    
    // Object metadata
    private List<MDValue> metadata;
    
    // Flag set when metadata is modified
    protected boolean modifiedMetadata;
    
    /** Flag set when data is modified, for events */
    protected boolean modified;
    
    /**
     * Returns a DSpaceObject for given objectID, or null
     * if no object can be found
     */
    public static DSpaceObject findByObjectID(Context context, String objectID) throws SQLException {
        TableRow dsoRow = DatabaseManager.findByUnique(context, "dspaceobject", "object_id", objectID);
        if (dsoRow != null) {
            // determine the object type and query appropriate table
            int type = dsoRow.getIntColumn("dso_type_id");
            String tableName = Constants.typeText[type].toLowerCase();
            TableRow row = DatabaseManager.findByUnique(context, tableName, "dso_id", dsoRow.getIntColumn("dso_id"));
            if (row != null) {
                return composeDSO(context, type, row);
            }
        }
        return null;
    }
    
    public static DSpaceObject composeDSO(Context context, int type, TableRow row) throws SQLException {
        switch (type) {
            case Constants.COMMUNITY:  return new Community(context, row);
            case Constants.COLLECTION: return new Collection(context, row);
            case Constants.ITEM:       return new Item(context, row);
            case Constants.BUNDLE:     return new Bundle(context, row);
            case Constants.BITSTREAM:  return new Bitstream(context, row);
            default: return null;
        }
    }
    
    public static Class<? extends DSpaceObject> classFromType(int type) {
        switch (type) {
            case Constants.COMMUNITY:  return Community.class;
            case Constants.COLLECTION: return Collection.class;
            case Constants.ITEM:       return Item.class;
            case Constants.BUNDLE:     return Bundle.class;
            case Constants.BITSTREAM:  return Bitstream.class;
            default: return null;
        }
    }

    /**
     * Returns the number of objects of the given object type
     *
     * @param type the Object type
     * @return count the number of objects of given type
     */
    public static long count(Context context, int type) throws SQLException {
        return DatabaseManager.querySingle(context,
         "SELECT count(*) as objct FROM dspaceobject WHERE dso_type_id = ?", type).getLongColumn("objct");
    }
    
    /**
     * Creates the DSpaceObject. This includes assigning
     * a unique object ID (a UUID).
     */
    protected void createDSO() throws SQLException {
        TableRow dsoRow = DatabaseManager.create(context, "dspaceobject");
        dsoRow.setColumn("dso_type_id", getType());
        dsoRow.setColumn("object_id", UUID.randomUUID().toString());
        DatabaseManager.update(context, dsoRow);
        // bind the DSO ID as a foreign key to this object
        tableRow.setColumn("dso_id", dsoRow.getIntColumn("dso_id"));
    }
    
    protected void updateDSO() throws AuthorizeException, SQLException {
        DatabaseManager.update(context, tableRow);
        if (modified) {
            context.addEvent(new Event(Event.MODIFY, getType(), getID(), null));
            context.addContentEvent(this, EventType.MODIFY);
            modified = false;
        }
        updateMetadata();
    }
    
    /**
     * Destroys the DSpaceObject belonging to this subclass.
     */
    protected void destroyDSO() throws SQLException {
        // first remove any attributes 
        DatabaseManager.updateQuery(context, "DELETE FROM attribute WHERE dso_id = ?", getDSOiD());
        DatabaseManager.delete(context, "dspaceobject", getDSOiD());
    }
    
    /**
     * Get metadata for the bitstream in a chosen schema.
     * See <code>MetadataSchema</code> for more information about schemas.
     * Passing in a <code>null</code> value for <code>qualifier</code>
     * or <code>lang</code> only matches metadata fields where that
     * qualifier or languages is actually <code>null</code>.
     * Passing in <code>MDValue.ANY</code>
     * retrieves all metadata fields with any value for the qualifier or
     * language, including <code>null</code>
     * <P>
     * Examples:
     * <P>
     * Return values of the unqualified "title" field, in any language.
     * Qualified title fields (e.g. "title.uniform") are NOT returned:
     * <P>
     * <code>bitstream.getMetadata("dc", "title", null, MDValue.ANY );</code>
     * <P>
     * Return all US English values of the "title" element, with any qualifier
     * (including unqualified):
     * <P>
     * <code>bitstream.getMetadata("dc, "title", MDValue.ANY, "en_US" );</code>
     * <P>
     * The ordering of values of a particular element/qualifier/language
     * combination is significant. When retrieving with wildcards, values of a
     * particular element/qualifier/language combinations will be adjacent, but
     * the overall ordering of the combinations is indeterminate.
     *
     * @param schema
     *            the schema for the metadata field. <em>Must</em> match
     *            the <code>name</code> of an existing metadata schema.
     * @param element
     *            the element name. <code>MDValue.ANY</code> matches any
     *            element. <code>null</code> doesn't really make sense as all
     *            metadata must have an element.
     * @param qualifier
     *            the qualifier. <code>null</code> means unqualified, and
     *            <code>MDValue.ANY</code> means any qualifier (including
     *            unqualified.)
     * @param lang
     *            the ISO639 language code, optionally followed by an underscore
     *            and the ISO3166 country code. <code>null</code> means only
     *            values with no language are returned, and
     *            <code>MDValue.ANY</code> means values with any country code or
     *            no country code are returned.
     * @return metadata fields that match the parameters
     */
    public List<MDValue> getMetadata(String schema, String element, String qualifier, String lang) {
    
        // Build up list of matching values
        List<MDValue> values = new ArrayList<MDValue>();
        for (MDValue mdv : getMetadata()) {
            if (mdv.match(schema, element, qualifier, lang)) {
                values.add(mdv);
            }
        }
        return values;
    }
    
    /**
     * Retrieve metadata field values from a given metadata string
     * of the form <schema prefix>.<element>[.<qualifier>|.*]
     *
     * @param mdString
     *            The metadata string of the form
     *            <schema prefix>.<element>[.<qualifier>|.*]
     */
    public List<MDValue> getMetadata(String mdString) {
        StringTokenizer dcf = new StringTokenizer(mdString, ".");
        
        String[] tokens = { "", "", "" };
        int i = 0;
        while(dcf.hasMoreTokens()) {
            tokens[i] = dcf.nextToken().trim();
            i++;
        }
        String schema = tokens[0];
        String element = tokens[1];
        String qualifier = tokens[2];
        
        if ("*".equals(qualifier))
        {
            return getMetadata(schema, element, MDValue.ANY, MDValue.ANY);
        }
        else if ("".equals(qualifier))
        {
            return getMetadata(schema, element, null, MDValue.ANY);
        }
        else
        {
            return getMetadata(schema, element, qualifier, MDValue.ANY);
        }
    }
    
    public String getMetadataValue(String name) {
        List<MDValue> vals = getMetadata(name);
        return (vals.size() >= 1) ? vals.get(0).getValue() : null;
    }
    
    public void addMetadata(String schema, String element, String qualifier, String lang, String value) {
        addMetadata(schema, element, qualifier, lang, -1, value);
    }
    
    public void addMetadata(String schema, String element, String qualifier, String lang,
                            int place, String value) {
        List<String> values = new ArrayList<String>();
        values.add(value);
        addMetadata(schema, element, qualifier, lang, place, values);
    }
    
    public void addMetadata(String schema, String element, String qualifier, String lang,
                            List<String> values) {
        addMetadata(schema, element, qualifier, lang, -1, values);
    }
    
    /**
     * Add metadata fields. These are appended to existing values.
     * Use <code>clearMetadata</code> to remove values. The ordering of values
     * passed in is maintained.
     * @param schema
     *            the schema for the metadata field. <em>Must</em> match
     *            the <code>name</code> of an existing metadata schema.
     * @param element
     *            the metadata element name
     * @param qualifier
     *            the metadata qualifier name, or <code>null</code> for
     *            unqualified
     * @param lang
     *            the ISO639 language code, optionally followed by an underscore
     *            and the ISO3166 country code. <code>null</code> means the
     *            value has no language (for example, a date).
     * @param values
     *            the values to add.
     */
    public void addMetadata(String schema, String element, String qualifier, String lang,
                            int place, List<String> values) {
        List<MDValue> mdValues = getMetadata();
        String language = (lang == null ? null : lang.trim());
        String fieldName = schema+"."+element+((qualifier==null)? "": "."+qualifier);

        // We will not verify that they are valid entries in the registry
        // until update() is called.
        for (String value : values) {
            String theValue = value;
            if (value != null) {
                // remove control unicode char
                String temp = value.trim();
                char[] dcvalue = temp.toCharArray();
                for (int charPos = 0; charPos < dcvalue.length; charPos++) {
                    if (Character.isISOControl(dcvalue[charPos]) &&
                        !String.valueOf(dcvalue[charPos]).equals("\u0009") &&
                        !String.valueOf(dcvalue[charPos]).equals("\n") &&
                        !String.valueOf(dcvalue[charPos]).equals("\r")) {
                        dcvalue[charPos] = ' ';
                    }
                }
                theValue = String.valueOf(dcvalue);
            } else {
                theValue = null;
            }
            metadata.add(new MDValue(schema, element, qualifier, language, place, theValue));
        }

        if (values.size() > 0) {
            modifiedMetadata = true;
        }
    }
    
    /**
     * Clear metadata values. As with <code>getMetadata</code> above,
     * passing in <code>null</code> only matches fields where the qualifier or
     * language is actually <code>null</code>.<code>MDValue.ANY</code> will
     * match any element, qualifier or language, including <code>null</code>.
     * Thus, <code>bitstream.clearMetadat(MDValue.ANY, MDValue.ANY, MDValue.ANY)</code>
     * will remove all metadata associated with a bitstream.
     *
     * @param schema
     *            the schema for the metadata field. <em>Must</em> match
     *            the <code>name</code> of an existing metadata schema.
     * @param element
     *            the element to remove, or <code>MDValue.ANY</code>
     * @param qualifier
     *            the qualifier. <code>null</code> means unqualified, and
     *            <code>MDValue.ANY</code> means any qualifier (including
     *            unqualified.)
     * @param lang
     *            the ISO639 language code, optionally followed by an underscore
     *            and the ISO3166 country code. <code>null</code> means only
     *            values with no language are removed, and <code>MDValue.ANY</code>
     *            means values with any country code or no country code are
     *            removed.
     */
    public void clearMetadata(String schema, String element, String qualifier, String lang) {
        // We will build a list of values NOT matching the values to clear
        List<MDValue> values = new ArrayList<MDValue>();
        for (MDValue mdv : getMetadata()) {
            if (! mdv.match(schema, element, qualifier, lang)) {
                values.add(mdv);
            }
        }

        // Now swap the old list of values for the new, unremoved values
        metadata = values;
        modifiedMetadata = true;
    }
    
    public void setMetadataValue(String name, String value) throws AuthorizeException, SQLException {
        String[] tokens = name.split("\\.");
        String qualifier = (tokens.length > 2) ? tokens[2] : null;
        if ("*".equals(qualifier)) {
            qualifier = MDValue.ANY;
        }
        
        clearMetadata(tokens[0], tokens[1], qualifier, MDValue.ANY);
        addMetadata(tokens[0], tokens[1], qualifier, "us_en", value);
    }
    
    protected void updateMetadata() throws AuthorizeException, SQLException {
        if (modifiedMetadata) {
            // Synchonize DB to in-memory MD values
            List<MDValue> dbValues = new ArrayList<MDValue>();
            loadMetadata(dbValues);
            StringBuffer details = new StringBuffer();
            int idx = 0;
            // first pass - additions
            for (MDValue addValue : metadata) {
                if (! dbValues.contains(addValue)) {
                    DatabaseManager.insert(context, createMetadataRow(addValue));
                    String fieldName = addValue.getSchema() + "." + addValue.getElement() +
                                       ((addValue.getQualifier() == null) ? "" : "." + addValue.getQualifier());
                    if (idx++ > 0) {
                        details.append(",");
                    }
                    details.append(fieldName);
                }
            }
            // second pass - deletions
            for (MDValue delValue : dbValues) {
                if (! metadata.contains(delValue)) {
                    DatabaseManager.delete(context, createMetadataRow(delValue));
                }
            }
            context.addEvent(new Event(Event.MODIFY_METADATA, getType(), getID(), details.toString()));
            modifiedMetadata = false;
        }
    }
    
    protected void deleteMetadata() throws AuthorizeException, SQLException {
        DatabaseManager.updateQuery(context, "DELETE FROM MetadataValue WHERE dso_id = ? ",
            getDSOiD());
    }

    /**
     * Update the object
     */
    public abstract void update() throws AuthorizeException, SQLException;

    /**
     * Get the type of this object, found in Constants
     * 
     * @return type of the object
     */
    public abstract int getType();

    /**
     * Get the internal ID (database primary key) of this object
     * 
     * @return internal ID of object
     */
    public abstract int getID();
    
    /**
     * Get the DSpaceObject ID (database primary key) of this object
     * 
     * @return internal DSpaceObject ID of object
     */
    int getDSOiD() {
        return tableRow.getIntColumn("dso_id");
    }
    
    /**
     * Get the Handle of the object. This may return <code>null</code>
     * 
     * @return Handle of the object, or <code>null</code> if it doesn't have
     *         one
     */
    public String getHandle() {
        return null;
    }
    
    public String getObjectId() throws SQLException {
        if (objectId == null) {
            TableRow row = DatabaseManager.find(context, "dspaceobject", getDSOiD());
            if (row != null) {
                objectId = row.getStringColumn("object_id");
            }
        }
        return objectId;
    }

    /**
     * Get a proper name for the object. This may return <code>null</code>.
     * Name should be suitable for display in a user interface.
     *
     * @return Name for the object, or <code>null</code> if it doesn't have
     *         one
     */
    public abstract String getName();

    /**
     * Assigns the EPerson who owns this object to a new context.
     *
     * @param delegate the Context which will assume the EPerson
     *                 identity of the current user.
     */
    public void delegate(Context delegateContext) {
        delegateContext.setCurrentUser(context.getCurrentUser());
    }

    /**
     * Return the dspace object where an ADMIN action right is sufficient to
     * grant the initial authorize check.
     * <p>
     * Default behaviour is ADMIN right on the object grant right on all other
     * action on the object itself. Subclass should override this method as
     * need.
     * 
     * @param action
     *            ID of action being attempted, from
     *            <code>org.dspace.core.Constants</code>. The ADMIN action is
     *            not a valid parameter for this method, an
     *            IllegalArgumentException should be thrown
     * @return the dspace object, if any, where an ADMIN action is sufficient to
     *         grant the original action
     * @throws SQLException
     * @throws IllegalArgumentException
     *             if the ADMIN action is supplied as parameter of the method
     *             call
     */
    public DSpaceObject getAdminObject(int action) throws SQLException {
        if (action == Constants.ADMIN) {
            throw new IllegalArgumentException("Illegal call to the DSpaceObject.getAdminObject method");
        }
        return this;
    }

    /**
     * Return the dspace object that "own" the current object in the hierarchy.
     * Note that this method has a meaning slightly different from the
     * getAdminObject because it is independent of the action but it is in a way
     * related to it. It defines the "first" dspace object <b>OTHER</b> then the
     * current one, where allowed ADMIN actions imply allowed ADMIN actions on
     * the object self.
     * 
     * @return the dspace object that "own" the current object in
     *         the hierarchy
     * @throws SQLException
     */
    public DSpaceObject getParentObject() throws SQLException {
        return null;
    }
    
    public void decacheMe() throws SQLException {
        // Remove item and it's submitter from cache
        context.removeCached(this, getID());
    }
    
    /**
     * Sets a scoped attribute on this object. If attribute does not exist,
     * it is created; if it does, its value is reset.
     * 
     * @param scope - the attribute scope
     * @param name - the name of the attribute
     * @param value - the attribute value
     */
    public void setAttribute(String scope, String name, String value) throws SQLException {
        // does attribute exist?
        TableRowIterator tri = DatabaseManager.queryTable(context, "attribute",
                "SELECT * FROM attribute WHERE dso_id = ? AND scope = ? AND attr_name = ?",
                getDSOiD(), scope, name);
        TableRow attrRow = null;
        try {
            if (tri.hasNext()) {
                attrRow = tri.next();
            } else {
                // otherwise, create it
                attrRow = DatabaseManager.create(context, "attribute");
                attrRow.setColumn("dso_id", getDSOiD());
                attrRow.setColumn("scope", scope);
                attrRow.setColumn("attr_name", name);
            }
            attrRow.setColumn("attr_value", value);
            DatabaseManager.update(context, attrRow);
        } finally {
            // close the TableRowIterator to free up resources
            if (tri != null) {
                tri.close();
            }
        }   
    }
    
    /**
     * Returns the value of the attribute with passed name, or <code>null</code>
     * if the attribute does not exist in the passed scope.
     * 
     * @param scope - the attribute scope
     * @param name - the name of the attribute
     * @return - the attribute value, or null if undefined
     */
    public String getAttribute(String scope, String name) throws SQLException {
        try (TableRowIterator tri = DatabaseManager.queryTable(context, "attribute",
                "SELECT * FROM attribute WHERE dso_id = ? AND scope = ? AND attr_name = ?",
                getDSOiD(), scope, name)) {
            return tri.hasNext() ? tri.next().getStringColumn("attr_value") : null;
        }
    }
    
    /**
     * Clears all attributes in the passed scope.
     * 
     * @param scope - the attribute scope
     */
    public void clearAttributes(String scope) throws SQLException {
        DatabaseManager.updateQuery(context, "DELETE FROM attribute WHERE dso_id = ? AND scope = ?",
                                    getDSOiD(), scope);
    }
    
    /**
     * Obtains a set of all the attribute names in the given scope.
     * If there are no attributes, an empty set is returned
     * 
     * @param scope - the attribute scope
     * @return the set of attribute names in the passed scope.
     */
    public Set<String> getAttributeNames(String scope) throws SQLException {
        Set<String> keySet = new HashSet<String>();
        try (TableRowIterator tri = DatabaseManager.queryTable(context, "attribute",
                "SELECT * FROM attribute WHERE dso_id = ? AND scope = ?",
                getDSOiD(), scope)) {
            while (tri.hasNext()) {
                keySet.add(tri.next().getStringColumn("name"));
            }
        }
        return keySet;
    }
    
    // lazy load of metadata
    private List<MDValue> getMetadata() {
        if (metadata == null) {
            metadata = new ArrayList<MDValue>();
            loadMetadata(metadata);
        }
        return metadata;
    }
    
    private void loadMetadata(List<MDValue> mdList) {
        TableRowIterator tri = null;
        try {
            tri = retrieveMetadata();
            if (tri != null) {
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
                            log.error("Loading object - cannot find metadata schema " + field.getSchemaID() + ", field " + fieldID);
                        } else {
                            // Add MDValue object to list
                            mdList.add(new MDValue(schema.getName(),
                                                   field.getElement(),
                                                   field.getQualifier(),
                                                   resultRow.getStringColumn("text_lang"),
                                                   resultRow.getStringColumn("text_value")));
                        }
                    }
                }
            }
        } catch (SQLException e)   {
            log.error("Error loading object metadata");
        } finally {
            // close the TableRowIterator to free up resources
            if (tri != null) {
                tri.close();
            }
        }
    }
       
    private TableRowIterator retrieveMetadata() throws SQLException {
        return DatabaseManager.queryTable(context, "MetadataValue",
                "SELECT * FROM MetadataValue WHERE dso_id= ? ORDER BY metadata_field_id, place",
                getDSOiD());
    }
    
    private TableRow createMetadataRow(MDValue value) throws SQLException, AuthorizeException {
    
        MetadataSchema schema = MetadataSchema.find(context, value.getSchema());
        MetadataField field = MetadataField.findByElement(context, schema.getSchemaID(),
                                                          value.getElement(), value.getQualifier());
    
        // Create a table row and update it with the values
        TableRow row = DatabaseManager.row("MetadataValue");
        row.setColumn("dso_id", getDSOiD());
        row.setColumn("metadata_field_id", field.getFieldID());
        row.setColumn("text_value", value.getValue());
        row.setColumn("text_lang", value.getLanguage());
        row.setColumn("place", value.getPlace());
        return row;
    }
}
