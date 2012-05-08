/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.oaipmh;

import java.util.Date;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;

import org.joda.time.DateTimeZone;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.ISODateTimeFormat;

import ORG.oclc.oai.server.catalog.RecordFactory;
import ORG.oclc.oai.server.verb.CannotDisseminateFormatException;

import org.dspace.search.HarvestedItemInfo;

/**
 * Implementation of the OAICat RecordFactory base class for DSpace items.
 * 
 * @author Robert Tansley
 */
public class DSpaceRecordFactory extends RecordFactory {
	
	// ISO8601 date formatter
	private static final DateTimeFormatter iso8601 = 
			ISODateTimeFormat.dateTimeNoMillis().withZone(DateTimeZone.UTC);
	
    public DSpaceRecordFactory(Properties properties) {
        // We don't use the OAICat properties; pass on up
        super(properties);
    }

    @Override
    public String fromOAIIdentifier(String identifier) {
        // Our local identifier is actually the same as the OAI one (the Handle)
        return identifier;
    }

    @Override
    public String quickCreate(Object nativeItem, String schemaURL, String metadataPrefix)
    		throws IllegalArgumentException, CannotDisseminateFormatException  {
        // Not supported
        return null;
    }

    @Override
    public String getOAIIdentifier(Object nativeItem) {
        return DSpaceOAICatalog.OAI_ID_PREFIX + ((HarvestedItemInfo) nativeItem).handle;
    }

    @Override
    public String getDatestamp(Object nativeItem) {
        Date d = ((HarvestedItemInfo) nativeItem).datestamp;
        // Return as ISO8601
        return iso8601.print(d.getTime());
    }

    @Override
    public Iterator getSetSpecs(Object nativeItem) {
        HarvestedItemInfo hii = (HarvestedItemInfo) nativeItem;
        Iterator<String> i = hii.collectionHandles.iterator();
        List<String> setSpecs = new LinkedList<String>();

        // Convert the DB Handle string 123.456/789 to the OAI-friendly
        // hdl_123.456/789
        while (i.hasNext()) {
            String handle = "hdl_" + i.next();
            setSpecs.add(handle.replace('/', '_'));
        }
        return setSpecs.iterator();
    }

    @Override
    public boolean isDeleted(Object nativeItem) {
        return ((HarvestedItemInfo)nativeItem).withdrawn;
    }

    @Override
    public Iterator getAbouts(Object nativeItem) {
        // Nothing in the about section for now
        return new LinkedList().iterator();
    }
}
