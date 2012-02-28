/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.content;

import java.io.IOException;
import java.io.InputStream;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.dspace.authorize.AuthorizeException;
import org.dspace.authorize.AuthorizeManager;
import org.dspace.core.ConfigurationManager;
import org.dspace.core.Constants;
import org.dspace.core.Context;
import org.dspace.core.LogManager;
import org.dspace.event.Event;
import org.dspace.storage.bitstore.BitstreamStorageManager;
import org.dspace.storage.rdbms.DatabaseManager;
import org.dspace.storage.rdbms.TableRow;
import org.dspace.storage.rdbms.TableRowIterator;

/**
 * Class representing bitstreams stored in the DSpace system.
 * <P>
 * When modifying the bitstream metadata, changes are not reflected in the
 * database until <code>update</code> is called. Note that you cannot alter
 * the contents of a bitstream; you need to create a new bitstream.
 * 
 * @author Robert Tansley
 * @version $Revision: 6281 $
 */
public class Bitstream extends DSpaceObject
{    
    /** logger */
    private static Logger log = LoggerFactory.getLogger(Bitstream.class);

    /** Our context */
    private Context bContext;

    /** The row in the table representing this bitstream */
    private TableRow bRow;
    
    /** metadata for this bitstream */
    private List<MDValue> metadata;

    /** The bitstream format corresponding to this bitstream */
    private BitstreamFormat bitstreamFormat;

    /** Flag set when data is modified, for events */
    private boolean modified;

    /** Flag set when metadata is modified, for events */
    private boolean modifiedMetadata;

    /**
     * Private constructor for creating a Bitstream object based on the contents
     * of a DB table row.
     * 
     * @param context
     *            the context this object exists in
     * @param row
     *            the corresponding row in the table
     * @throws SQLException
     */
    Bitstream(Context context, TableRow row) throws SQLException
    {
        bContext = context;
        bRow = row;

        // Get the bitstream format
        bitstreamFormat = BitstreamFormat.find(context, row
                .getIntColumn("bitstream_format_id"));

        if (bitstreamFormat == null)
        {
            // No format: use "Unknown"
            bitstreamFormat = BitstreamFormat.findUnknown(context);

            // Panic if we can't find it
            if (bitstreamFormat == null)
            {
                throw new IllegalStateException("No Unknown bitstream format");
            }
        }

        // Cache ourselves
        context.cache(this, row.getIntColumn("bitstream_id"));

        modified = false;
        modifiedMetadata = false;
        clearDetails();
    }

    /**
     * Get a bitstream from the database. The bitstream metadata is loaded into
     * memory.
     * 
     * @param context
     *            DSpace context object
     * @param id
     *            ID of the bitstream
     * 
     * @return the bitstream, or null if the ID is invalid.
     * @throws SQLException
     */
    public static Bitstream find(Context context, int id) throws SQLException
    {
        // First check the cache
        Bitstream fromCache = (Bitstream) context
                .fromCache(Bitstream.class, id);

        if (fromCache != null)
        {
            return fromCache;
        }

        TableRow row = DatabaseManager.find(context, "bitstream", id);

        if (row == null)
        {
            if (log.isDebugEnabled())
            {
                log.debug(LogManager.getHeader(context, "find_bitstream",
                        "not_found,bitstream_id=" + id));
            }

            return null;
        }

        // not null, return Bitstream
        if (log.isDebugEnabled())
        {
            log.debug(LogManager.getHeader(context, "find_bitstream",
                    "bitstream_id=" + id));
        }

        return new Bitstream(context, row);
    }

    public static Bitstream[] findAll(Context context) throws SQLException
    {
        TableRowIterator tri = DatabaseManager.queryTable(context, "bitstream",
                "SELECT * FROM bitstream");

        List<Bitstream> bitstreams = new ArrayList<Bitstream>();

        try
        {
            while (tri.hasNext())
            {
                TableRow row = tri.next();

                // First check the cache
                Bitstream fromCache = (Bitstream) context.fromCache(
                        Bitstream.class, row.getIntColumn("bitstream_id"));

                if (fromCache != null)
                {
                    bitstreams.add(fromCache);
                }
                else
                {
                    bitstreams.add(new Bitstream(context, row));
                }
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

        Bitstream[] bitstreamArray = new Bitstream[bitstreams.size()];
        bitstreamArray = bitstreams.toArray(bitstreamArray);

        return bitstreamArray;
    }

    /**
     * Create a new bitstream, with a new ID. The checksum and file size are
     * calculated. This method is not public, and does not check authorisation;
     * other methods such as Bundle.createBitstream() will check authorisation.
     * The newly created bitstream has the "unknown" format.
     * 
     * @param context
     *            DSpace context object
     * @param is
     *            the bits to put in the bitstream
     * 
     * @return the newly created bitstream
     * @throws IOException
     * @throws SQLException
     */
    static Bitstream create(Context context, InputStream is)
            throws IOException, SQLException
    {
        // Store the bits
        int bitstreamID = BitstreamStorageManager.store(context, is);

        log.info(LogManager.getHeader(context, "create_bitstream",
                "bitstream_id=" + bitstreamID));

        // Set the format to "unknown"
        Bitstream bitstream = find(context, bitstreamID);
        bitstream.setFormat(null);

        context.addEvent(new Event(Event.CREATE, Constants.BITSTREAM, bitstreamID, null));

        return bitstream;
    }

    /**
     * Register a new bitstream, with a new ID.  The checksum and file size
     * are calculated.  This method is not public, and does not check
     * authorisation; other methods such as Bundle.createBitstream() will
     * check authorisation.  The newly created bitstream has the "unknown"
     * format.
     *
     * @param  context DSpace context object
     * @param assetstore corresponds to an assetstore in dspace.cfg
     * @param bitstreamPath the path and filename relative to the assetstore 
     * @return  the newly registered bitstream
     * @throws IOException
     * @throws SQLException
     */
    static Bitstream register(Context context, 
    		int assetstore, String bitstreamPath)
        	throws IOException, SQLException
    {
        // Store the bits
        int bitstreamID = BitstreamStorageManager.register(
        		context, assetstore, bitstreamPath);

        log.info(LogManager.getHeader(context,
            "create_bitstream",
            "bitstream_id=" + bitstreamID));

        // Set the format to "unknown"
        Bitstream bitstream = find(context, bitstreamID);
        bitstream.setFormat(null);

        context.addEvent(new Event(Event.CREATE, Constants.BITSTREAM, bitstreamID, "REGISTER"));

        return bitstream;
    }

    /**
     * Get the internal identifier of this bitstream
     * 
     * @return the internal identifier
     */
    public int getID()
    {
        return bRow.getIntColumn("bitstream_id");
    }

    public String getHandle()
    {
        // No Handles for bitstreams
        return null;
    }

    /**
     * Get the sequence ID of this bitstream
     * 
     * @return the sequence ID
     */
    public int getSequenceID()
    {
        return bRow.getIntColumn("sequence_id");
    }

    /**
     * Set the sequence ID of this bitstream
     * 
     * @param sid
     *            the ID
     */
    public void setSequenceID(int sid)
    {
        bRow.setColumn("sequence_id", sid);
        modifiedMetadata = true;
        addDetails("SequenceID");
    }

    /**
     * Get the name of this bitstream - typically the filename, without any path
     * information
     * 
     * @return the name of the bitstream
     */
    public String getName()
    {
        return bRow.getStringColumn("name");
    }

    /**
     * Set the name of the bitstream
     * 
     * @param n
     *            the new name of the bitstream
     */
    public void setName(String n)
    {
        bRow.setColumn("name", n);
        modifiedMetadata = true;
        addDetails("Name");
    }

    /**
     * Get the source of this bitstream - typically the filename with path
     * information (if originally provided) or the name of the tool that
     * generated this bitstream
     * 
     * @return the source of the bitstream
     */
    public String getSource()
    {
        return bRow.getStringColumn("source");
    }

    /**
     * Set the source of the bitstream
     * 
     * @param n
     *            the new source of the bitstream
     */
    public void setSource(String n)
    {
        bRow.setColumn("source", n);
        modifiedMetadata = true;
        addDetails("Source");
    }

    /**
     * Get the description of this bitstream - optional free text, typically
     * provided by a user at submission time
     * 
     * @return the description of the bitstream
     */
    public String getDescription()
    {
        return bRow.getStringColumn("description");
    }

    /**
     * Set the description of the bitstream
     * 
     * @param n
     *            the new description of the bitstream
     */
    public void setDescription(String n)
    {
        bRow.setColumn("description", n);
        modifiedMetadata = true;
        addDetails("Description");
    }

    /**
     * Get the checksum of the content of the bitstream, for integrity checking
     * 
     * @return the checksum
     */
    public String getChecksum()
    {
        return bRow.getStringColumn("checksum");
    }

    /**
     * Get the algorithm used to calculate the checksum
     * 
     * @return the algorithm, e.g. "MD5"
     */
    public String getChecksumAlgorithm()
    {
        return bRow.getStringColumn("checksum_algorithm");
    }

    /**
     * Get the size of the bitstream
     * 
     * @return the size in bytes
     */
    public long getSize()
    {
        return bRow.getLongColumn("size_bytes");
    }

    /**
     * Set the user's format description. This implies that the format of the
     * bitstream is uncertain, and the format is set to "unknown."
     * 
     * @param desc
     *            the user's description of the format
     * @throws SQLException
     */
    public void setUserFormatDescription(String desc) throws SQLException
    {
        // FIXME: Would be better if this didn't throw an SQLException,
        // but we need to find the unknown format!
        setFormat(null);
        bRow.setColumn("user_format_description", desc);
        modifiedMetadata = true;
        addDetails("UserFormatDescription");
    }

    /**
     * Get the user's format description. Returns null if the format is known by
     * the system.
     * 
     * @return the user's format description.
     */
    public String getUserFormatDescription()
    {
        return bRow.getStringColumn("user_format_description");
    }

    /**
     * Get the description of the format - either the user's or the description
     * of the format defined by the system.
     * 
     * @return a description of the format.
     */
    public String getFormatDescription()
    {
        if (bitstreamFormat.getShortDescription().equals("Unknown"))
        {
            // Get user description if there is one
            String desc = bRow.getStringColumn("user_format_description");

            if (desc == null)
            {
                return "Unknown";
            }

            return desc;
        }

        // not null or Unknown
        return bitstreamFormat.getShortDescription();
    }

    /**
     * Get the format of the bitstream
     * 
     * @return the format of this bitstream
     */
    public BitstreamFormat getFormat()
    {
        return bitstreamFormat;
    }

    /**
     * Set the format of the bitstream. If the user has supplied a type
     * description, it is cleared. Passing in <code>null</code> sets the type
     * of this bitstream to "unknown".
     * 
     * @param f
     *            the format of this bitstream, or <code>null</code> for
     *            unknown
     * @throws SQLException
     */
    public void setFormat(BitstreamFormat f) throws SQLException
    {
        // FIXME: Would be better if this didn't throw an SQLException,
        // but we need to find the unknown format!
        if (f == null)
        {
            // Use "Unknown" format
            bitstreamFormat = BitstreamFormat.findUnknown(bContext);
        }
        else
        {
            bitstreamFormat = f;
        }

        // Remove user type description
        bRow.setColumnNull("user_format_description");

        // Update the ID in the table row
        bRow.setColumn("bitstream_format_id", bitstreamFormat.getID());
        modified = true;
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
    public List<MDValue> getMetadata(String schema, String element, String qualifier,
            					     String lang) {
    	
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
            			    List<String> values) {
        List<MDValue> mdValues = getMetadata();
        String language = (lang == null ? null : lang.trim());
        //String fieldName = schema+"."+element+((qualifier==null)? "": "."+qualifier);

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
            metadata.add(new MDValue(schema, element, qualifier, language, theValue));
            //addDetails(fieldName);
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
    public void clearMetadata(String schema, String element, String qualifier,
            				  String lang)
    {
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

    /**
     * Update the bitstream metadata. Note that the content of the bitstream
     * cannot be changed - for that you need to create a new bitstream.
     * 
     * @throws SQLException
     * @throws AuthorizeException
     */
    public void update() throws SQLException, AuthorizeException
    {
        // Check authorisation
        AuthorizeManager.authorizeAction(bContext, this, Constants.WRITE);

        log.info(LogManager.getHeader(bContext, "update_bitstream",
                "bitstream_id=" + getID()));

        if (modified)
        {
            bContext.addEvent(new Event(Event.MODIFY, Constants.BITSTREAM, getID(), null));
            modified = false;
        }
        if (modifiedMetadata)
        {
            // Synchonize DB to in-memory MD values
        	List<MDValue> dbValues = new ArrayList<MDValue>();
        	loadMetadata(dbValues);
        	// first pass - additions
            for (MDValue addValue : metadata) {
            	if (! dbValues.contains(addValue)) {
                	DatabaseManager.insert(bContext, createMetadataRow(addValue));
            	}
            }
            // second pass - deletions
            for (MDValue delValue : dbValues) {
            	if (! metadata.contains(delValue)) {
            		DatabaseManager.delete(bContext, createMetadataRow(delValue));
            	}
            }
            bContext.addEvent(new Event(Event.MODIFY_METADATA, Constants.BITSTREAM, getID(), getDetails()));
            modifiedMetadata = false;
            clearDetails();
        }

        DatabaseManager.update(bContext, bRow);
    }

    /**
     * Delete the bitstream, including any mappings to bundles
     * 
     * @throws SQLException
     */
    void delete() throws SQLException
    {
        boolean oracle = false;
        if ("oracle".equals(ConfigurationManager.getProperty("db.name")))
        {
            oracle = true;
        }

        // changed to a check on remove
        // Check authorisation
        //AuthorizeManager.authorizeAction(bContext, this, Constants.DELETE);
        log.info(LogManager.getHeader(bContext, "delete_bitstream",
                "bitstream_id=" + getID()));

        bContext.addEvent(new Event(Event.DELETE, Constants.BITSTREAM, getID(), String.valueOf(getSequenceID())));

        // Remove from cache
        bContext.removeCached(this, getID());

        // Remove policies
        AuthorizeManager.removeAllPolicies(bContext, this);

        // Remove references to primary bitstreams in bundle
        String query = "update bundle set primary_bitstream_id = ";
        query += (oracle ? "''" : "Null") + " where primary_bitstream_id = ? ";
        DatabaseManager.updateQuery(bContext,
                query, bRow.getIntColumn("bitstream_id"));
        
        // Remove any metadata
        DatabaseManager.updateQuery(bContext, "DELETE FROM BitstreamMDValue WHERE bitstream_id= ? ",
                					getID());

        // Remove bitstream itself
        BitstreamStorageManager.delete(bContext, bRow
                .getIntColumn("bitstream_id"));
    }

    /**
     * Bitstreams are only logically deleted (via a flag in the database).
     * This method allows us to verify is the bitstream is still valid
     *
     * @return true if the bitstream has been deleted
     */
    boolean isDeleted() throws SQLException
    {
        String query = "select count(*) as mycount from Bitstream where deleted = '1' and bitstream_id = ? ";
        TableRowIterator tri = DatabaseManager.query(bContext, query, bRow.getIntColumn("bitstream_id"));
        long count = 0;

        try
        {
            TableRow r = tri.next();
            count = r.getLongColumn("mycount");
        }
        finally
        {
            // close the TableRowIterator to free up resources
            if (tri != null)
            {
                tri.close();
            }
        }
        
        return count == 1;
    }

    /**
     * Retrieve the contents of the bitstream
     * 
     * @return a stream from which the bitstream can be read.
     * @throws IOException
     * @throws SQLException
     * @throws AuthorizeException
     */
    public InputStream retrieve() throws IOException, SQLException,
            AuthorizeException
    {
        // Maybe should return AuthorizeException??
        AuthorizeManager.authorizeAction(bContext, this, Constants.READ);

        return BitstreamStorageManager.retrieve(bContext, bRow
                .getIntColumn("bitstream_id"));
    }

    /**
     * Get the bundles this bitstream appears in
     * 
     * @return array of <code>Bundle</code> s this bitstream appears in
     * @throws SQLException
     */
    public Bundle[] getBundles() throws SQLException
    {
        // Get the bundle table rows
        TableRowIterator tri = DatabaseManager.queryTable(bContext, "bundle",
                "SELECT bundle.* FROM bundle, bundle2bitstream WHERE " + 
                "bundle.bundle_id=bundle2bitstream.bundle_id AND " +
                "bundle2bitstream.bitstream_id= ? ",
                 bRow.getIntColumn("bitstream_id"));

        // Build a list of Bundle objects
        List<Bundle> bundles = new ArrayList<Bundle>();
        try
        {
            while (tri.hasNext())
            {
                TableRow r = tri.next();

                // First check the cache
                Bundle fromCache = (Bundle) bContext.fromCache(Bundle.class, r
                        .getIntColumn("bundle_id"));

                if (fromCache != null)
                {
                    bundles.add(fromCache);
                }
                else
                {
                    bundles.add(new Bundle(bContext, r));
                }
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

        Bundle[] bundleArray = new Bundle[bundles.size()];
        bundleArray = (Bundle[]) bundles.toArray(bundleArray);

        return bundleArray;
    }

    /**
     * return type found in Constants
     * 
     * @return int Constants.BITSTREAM
     */
    public int getType()
    {
        return Constants.BITSTREAM;
    }
    
    /**
     * Determine if this bitstream is registered
     * 
     * @return true if the bitstream is registered, false otherwise
     */
    public boolean isRegisteredBitstream() {
        return BitstreamStorageManager
				.isRegisteredBitstream(bRow.getStringColumn("internal_id"));
    }
    
    /**
     * Get the asset store number where this bitstream is stored
     * 
     * @return the asset store number of the bitstream
     */
    public int getStoreNumber() {
        return bRow.getIntColumn("store_number");
    }

    /**
     * Get the parent object of a bitstream. It can either be an item if this is a normal 
     * bitstream, otherwise it could be a collection or a community if it is a logo.     
     * @return
     * @throws SQLException
     */    
    public DSpaceObject getParentObject() throws SQLException
    {
        Bundle[] bundles = getBundles();
        if (bundles != null && (bundles.length > 0 && bundles[0] != null))
        {
            // the ADMIN action is not allowed on Bundle object so skip to the item
            Item[] items = bundles[0].getItems();
            if (items != null && items.length > 0)
            {
                return items[0];
            }
            else
            {
                return null;
            }
        }
        else
        {
            // is the bitstream a logo for a community or a collection?
            TableRow qResult = DatabaseManager.querySingle(bContext,
                       "SELECT collection_id FROM collection " +
                       "WHERE logo_bitstream_id = ?",getID());
            if (qResult != null) 
            {
                return Collection.find(bContext,qResult.getIntColumn("collection_id"));
            }
            else
            {   
                // is the bitstream related to a community?
                qResult = DatabaseManager.querySingle(bContext,
                        "SELECT community_id FROM community " +
                        "WHERE logo_bitstream_id = ?",getID());
    
                if (qResult != null)
                {
                    return Community.find(bContext,qResult.getIntColumn("community_id"));
                }
                else
                {
                    return null;
                }
            }                                   
        }
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
                    MetadataField field = MetadataField.find(bContext, fieldID);
                    if (field == null) {
                        log.error("Loading bitstream - cannot find metadata field " + fieldID);
                    } else {
                        MetadataSchema schema = MetadataSchema.find(bContext, field.getSchemaID());
                        if (schema == null) {
                            log.error("Loading bitstream - cannot find metadata schema " + field.getSchemaID() + ", field " + fieldID);
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
            log.error("Error loading bitstream metadata");
        } finally {
            // close the TableRowIterator to free up resources
            if (tri != null) {
                tri.close();
            }
        }
    }
       
    private TableRowIterator retrieveMetadata() throws SQLException {
    	return DatabaseManager.queryTable(bContext, "BitstreamMDValue",
                "SELECT * FROM BitstreamMDValue WHERE bitstream_id= ? ORDER BY metadata_field_id, place",
                getID());
    }
        
    private TableRow createMetadataRow(MDValue value) throws SQLException, AuthorizeException {
    	
    	MetadataSchema schema = MetadataSchema.findByNamespace(bContext, value.getSchema());
    	MetadataField field = MetadataField.findByElement(bContext, schema.getSchemaID(),
    			                                          value.getElement(), value.getQualifier());
    	
        // Create a table row and update it with the values
        TableRow row = DatabaseManager.row("BitstreamMDValue");
        row.setColumn("bitstream_id", getID());
        row.setColumn("metadata_field_id", field.getFieldID());
        row.setColumn("text_value", value.getValue());
        row.setColumn("text_lang", value.getLanguage());
        row.setColumn("place", "foo");
        return row;
    }
}
