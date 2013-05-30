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
import java.util.List;

import org.joda.time.DateTimeZone;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.ISODateTimeFormat;

import org.dspace.authorize.AuthorizeException;
import org.dspace.core.Constants;
import org.dspace.core.Context;
import org.dspace.core.LifecycleMux;
import org.dspace.event.Event;
import org.dspace.handle.HandleManager;

/**
 * Support to install an Item in the archive.
 * 
 * @author dstuve
 */
public class InstallItem {
	// ISO8601 date formatters
	private static final DateTimeFormatter iso8601 = 
			ISODateTimeFormat.dateTimeNoMillis().withZone(DateTimeZone.UTC);
	private static final DateTimeFormatter isoDate = ISODateTimeFormat.date();
    /**
     * Take an InProgressSubmission and turn it into a fully-archived Item,
     * creating a new Handle.
     * 
     * @param c
     *            DSpace Context
     * @param is
     *            submission to install
     * 
     * @return the fully archived Item
     */
    public static Item installItem(Context c, InProgressSubmission is)
            throws SQLException, IOException, AuthorizeException {
        return installItem(c, is, null);
    }

    /**
     * Take an InProgressSubmission and turn it into a fully-archived Item.
     * 
     * @param c  current context
     * @param is
     *            submission to install
     * @param suppliedHandle
     *            the existing Handle to give to the installed item
     * 
     * @return the fully archived Item
     */
    public static Item installItem(Context c, InProgressSubmission is,
            String suppliedHandle) throws SQLException,
            IOException, AuthorizeException {
        Item item = is.getItem();
        String handle;
        
        // if no previous handle supplied, create one
        if (suppliedHandle == null) {
            // create a new handle for this item
            handle = HandleManager.createHandle(c, item);
        } else {
            // assign the supplied handle to this item
            handle = HandleManager.createHandle(c, item, suppliedHandle);
        }

        populateHandleMetadata(item, handle);

        populateMetadata(c, item);

        return finishItem(c, item, is);

    }

    /**
     * Turn an InProgressSubmission into a fully-archived Item, for
     * a "restore" operation such as ingestion of an AIP to recreate an
     * archive.  This does NOT add any descriptive metadata (e.g. for
     * provenance) to preserve the transparency of the ingest.  The
     * ingest mechanism is assumed to have set all relevant technical
     * and administrative metadata fields.
     *
     * @param c  current context
     * @param is
     *            submission to install
     * @param suppliedHandle
     *            the existing Handle to give the installed item, or null
     *            to create a new one.
     *
     * @return the fully archived Item
     */
    public static Item restoreItem(Context c, InProgressSubmission is, String suppliedHandle)
        throws SQLException, IOException, AuthorizeException {
        Item item = is.getItem();
        String handle;

        // if no handle supplied
        if (suppliedHandle == null) {
            // create a new handle for this item
            handle = HandleManager.createHandle(c, item);
            //only populate handle metadata for new handles
            // (existing handles should already be in the metadata -- as it was restored by ingest process)
            populateHandleMetadata(item, handle);
        } else {
            // assign the supplied handle to this item
            handle = HandleManager.createHandle(c, item, suppliedHandle);
        }

        // Even though we are restoring an item it may not have a have the proper dates. So lets
        // double check that it has a date accessioned and date issued, and if either of those dates
        // are not set then set them to today.
        long now = System.currentTimeMillis();
        
        // If the item dosn't have a date.accessioned create one.
        List<MDValue> dateAccessioned = item.getMetadata(MetadataSchema.DC_SCHEMA, "date", "accessioned", MDValue.ANY);
        if (dateAccessioned.size() == 0) {
	        item.addMetadata("dc", "date", "accessioned", null, iso8601.print(now));
        }
        
        // create issue date if not present
        List<MDValue> currentDateIssued = item.getMetadata(MetadataSchema.DC_SCHEMA, "date", "issued", MDValue.ANY);
        if (currentDateIssued.size() == 0) {
            item.addMetadata("dc", "date", "issued", null, isoDate.print(now));
        }
        
        // Record that the item was restored
		String provDescription = "Restored into DSpace on "+ now + " (GMT).";
		item.addMetadata("dc", "description", "provenance", "en", provDescription);

        return finishItem(c, item, is);
    }

    private static void populateHandleMetadata(Item item, String handle)
        throws SQLException, IOException, AuthorizeException {
        String handleref = HandleManager.getCanonicalForm(handle);

        // Add handle as identifier.uri DC value.
        // First check that identifier dosn't already exist.
        boolean identifierExists = false;
        for (MDValue id : item.getMetadata(MetadataSchema.DC_SCHEMA, "identifier", "uri", MDValue.ANY))  {
        	if (handleref.equals(id.getValue())) {
        		identifierExists = true;
            }
        }
        if (!identifierExists) {
        	item.addMetadata("dc", "identifier", "uri", null, handleref);
        }
    }


    private static void populateMetadata(Context c, Item item)
        throws SQLException, IOException, AuthorizeException  {
        // create accession date
    	long now = System.currentTimeMillis();
        item.addMetadata("dc", "date", "accessioned", null, iso8601.print(now));

        // add date available - later deleted if under embargo, where it will
        // be set when the embargo is lifted.
        item.addMetadata("dc", "date", "available", null, iso8601.print(now));

        // create issue date if not present
        List<MDValue> currentDateIssued = item.getMetadata(MetadataSchema.DC_SCHEMA, "date", "issued", MDValue.ANY);

        if (currentDateIssued.size() == 0) {
            item.addMetadata("dc", "date", "issued", null, isoDate.print(now));
        }

         String provDescription = "Made available in DSpace on " + now
                + " (GMT). " + getBitstreamProvenanceMessage(item);

        if (currentDateIssued.size() > 0) {
            String pid = currentDateIssued.get(0).getValue();
            provDescription = provDescription + "  Previous issue date: " + pid;
        }

        // Add provenance description
        item.addMetadata("dc", "description", "provenance", "en", provDescription);
    }

    // final housekeeping when adding new Item to archive
    // common between installing and "restoring" items.
    private static Item finishItem(Context c, Item item, InProgressSubmission is)
        throws SQLException, IOException, AuthorizeException  {
        // create collection2item mapping
        is.getCollection().addItem(item);

        // set owning collection
        item.setOwningCollection(is.getCollection());

        // set in_archive=true
        item.setArchived(true);

        // save changes ;-)
        item.update();

        // Notify interested parties of newly archived Item
        c.addEvent(new Event(Event.INSTALL, Constants.ITEM, item.getID(),
                item.getHandle()));

        // remove in-progress submission
        is.deleteWrapper();

        // remove the item's policies and replace them with
        // the defaults from the collection
        item.inheritCollectionDefaultPolicies(is.getCollection());

        // perform any lifecycle actions at installation (e.g. set embargo)
        LifecycleMux.postEvent(new LifecycleEvent(item, "install"));

        return item;
    }

    /**
     * Generate provenance-worthy description of the bitstreams contained in an
     * item.
     * 
     * @param myitem  the item generate description for
     * 
     * @return provenance description
     */
    public static String getBitstreamProvenanceMessage(Item myitem)
    						throws SQLException {
        // Get non-internal format bitstreams
        List<Bitstream> bitstreams = myitem.getNonInternalBitstreams();

        // Create provenance description
        StringBuilder myMessage = new StringBuilder();
        myMessage.append("No. of bitstreams: ").append(bitstreams.size()).append("\n");

        // Add sizes and checksums of bitstreams
        for (Bitstream bitstream : bitstreams) {
            myMessage.append(bitstream.getName()).append(": ")
                    .append(bitstream.getSize()).append(" bytes, checksum: ")
                    .append(bitstream.getChecksum()).append(" (")
                    .append(bitstream.getChecksumAlgorithm()).append(")\n");
        }

        return myMessage.toString();
    }
}
