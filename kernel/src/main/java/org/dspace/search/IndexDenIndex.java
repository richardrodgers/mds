/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.search;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.List;

import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.flaptor.indextank.apiclient.IndexAlreadyExistsException;
import com.flaptor.indextank.apiclient.IndexDoesNotExistException;
import com.flaptor.indextank.apiclient.InvalidSyntaxException;
import com.flaptor.indextank.apiclient.IndexTankClient;
import com.flaptor.indextank.apiclient.IndexTankClient.Index;
import com.flaptor.indextank.apiclient.IndexTankClient.Query;
import com.flaptor.indextank.apiclient.IndexTankClient.SearchResults;
import com.flaptor.indextank.apiclient.MaximumIndexesExceededException;

import org.apache.lucene.document.DateTools;

import com.google.common.base.Strings;

import org.dspace.core.Constants;
import org.dspace.core.LogManager;

import static org.dspace.search.DSIndexer.*;

/**
 * IndexDenIndex provides indexing and querying services backed by a
 * IndexDen Search as a service (hosted index). Pretty basic, but
 * demonstrator for using search SaaS.
 * 
 * @author richardrodgers
 */
public class IndexDenIndex implements IndexService {
    private static final Logger log = LoggerFactory.getLogger(IndexDenIndex.class);

    private static final String NOT_ANALYZED = "not-analyzed";
    private static final String ANALYZED = "analyzed";
    private static final String NONE = "no";
    
    // search field schema - hard-coded here, but could easily be made more configurable
    // RLR - this schema is incomplete and has errors - again, current use is as a demonstrator
    private static final Map<String, FieldConfig> schema = new HashMap<String, FieldConfig>() {{
    	put(LAST_INDEXED_FIELD,    new FieldConfig(LAST_INDEXED_FIELD, "text", true, NOT_ANALYZED));
    	put(DOCUMENT_STATUS_FIELD, new FieldConfig(DOCUMENT_STATUS_FIELD, "text", true, NOT_ANALYZED));
    	put(DOCUMENT_KEY,          new FieldConfig(DOCUMENT_KEY, "text", true, NOT_ANALYZED));
    	put("search.resourcetype", new FieldConfig("search.resourcetype", "text", true, NOT_ANALYZED));
    	put("search.resourceid",   new FieldConfig("search.resourceid", "text", true, NONE));
    	put("name",                new FieldConfig("name", "text", false, ANALYZED));
    	put("default",             new FieldConfig("default", "text", false, ANALYZED));
        put("abstract",            new FieldConfig("abstract", "text", false, ANALYZED));
        put("author",              new FieldConfig("author", "text", false, ANALYZED));
        put("title",               new FieldConfig("title", "text", false, ANALYZED));
        put("text",                new FieldConfig("text", "text", false, ANALYZED));
    	put("location",            new FieldConfig("location", "text", false, ANALYZED));
    	// following are templates
    	put("sort_",               new FieldConfig("sort_", "text", false, NOT_ANALYZED));
    }};

    private IndexTankClient client;
    private Index index;
    
    public IndexDenIndex() {}
     
    /**
     * Is stale checks the lastModified time stamp in the database and the index
     * to determine if the index is stale.
     * 
     * @param lastModified
     * @return document status true if index date earlier than lastModified
     * @throws IOException
     */
    @Override
    public boolean isDocumentStale(String documentKey, Date lastModified)  throws IOException {

        String queryStr = "handle:" + documentKey;
        
        try {
            SearchResults results = index.search(Query.forString(queryStr).withFetchFields("timestamp"));
            // compare with lastmodified
            if (results.matches > 0) {
                Map<String, Object> docMap = results.results.get(0);
                String ts = (String)docMap.get("timestamp");
                return (Long.valueOf(ts) < (lastModified.getTime() / 1000 ));
            }
        } catch (InvalidSyntaxException isE) {}
        return true;
    }
    
    @Override
    public void doTask(IndexingTask task) throws IOException {
    
        switch (task.getAction()) {
            case DELETE:
                try {
                    index.deleteDocument(task.getFieldValue(DOCUMENT_KEY));
                } catch (IndexDoesNotExistException idneE) {}
                break;
            case UPDATE:
                // for this simple demo, just add each field individually, then lump all in 'text' field (the default) 
                Map<String, String> fields = new HashMap<>();
                StringBuilder textSb = new StringBuilder();
                // add in fields
                for (String key : task.getFieldKeys()) {
                    // get config for field
                    FieldConfig fc = schema.get(key);
                    if (fc != null) {
                        for (String value : task.getFieldValues(key)) {
                            mapValue(value, fc, fields);
                            // all get mapped to 'text' index
                            textSb.append(value).append(" ");
                        }
                    } else {
                        log.error("Invalid field map - field: '" + key + "' undefined in schema");
                    }
                }
                mapValue(textSb.toString(), schema.get("text"), fields);
                // likewise any streams - not yet implemented
                /*
    			for (String key : task.getStreamKeys()) {
    				for (InputStream is : task.getStreamValues(key)) {
    					doc.add(new Field("default", new BufferedReader(new InputStreamReader(is))));
    				}
    			}
    			commit(task.getFieldValue(DOCUMENT_KEY), doc, true);
                */
                try {
                    /*
                    // RLR DEBUG
                    for (String fname : fields.keySet()) {
                        log.info("add2doc: " + fname + " :" + fields.get(fname));
                    }
                    */
                    index.addDocument(task.getFieldValue(DOCUMENT_KEY), fields);
                } catch (IndexDoesNotExistException idneE) {}
                break;
            case TX_BEGIN:
                // not implemented setBatchProcessingMode(true);
                break;
            case TX_END:
                // not implemented setBatchProcessingMode(false);
                break;
            case PURGE:
                try {
                    index.delete();
                } catch (IndexDoesNotExistException idneE) {}
                break;
            default:
                break;
        }
    }
        
    @Override
    public QueryResults doQuery(QueryArgs args) throws IOException {
        String queryStr = args.getQuery();
        QueryResults qr = new QueryResults();
        List<String> hitHandles = new ArrayList<String>();
        List<Integer> hitIds     = new ArrayList<Integer>();
        List<Integer> hitTypes   = new ArrayList<Integer>();

        // set up the QueryResults object
        qr.setHitHandles(hitHandles);
        qr.setHitIds(hitIds);
        qr.setHitTypes(hitTypes);
        qr.setStart(args.getStart());
        qr.setPageSize(args.getPageSize());
        qr.setEtAl(args.getEtAl());

        // massage the query string a bit
        queryStr = DSQuery.checkEmptyQuery(queryStr); // change nulls to an empty string
        queryStr = DSQuery.stripHandles(queryStr); // remove handles from query string
        queryStr = DSQuery.stripAsterisk(queryStr); // remove asterisk from beginning of string

        try {
            SearchResults results = index.search(Query.forString(queryStr)
                .withFetchFields("handle", "search.resourcetype", "search.resourceid"));
            qr.setHitCount((int)results.matches);
            // We now have a bunch of hits - snip out a 'window'
            // defined in start, count and return the handles
            // from that window, if there are enough hits
            if (args.getStart() < results.matches) {
                // get as many as we can, up to the window size
                // how many are available after snipping off at offset 'start'?
                int hitsRemaining = (int)results.matches - args.getStart();
                int hitsToProcess = (hitsRemaining < args.getPageSize()) ? hitsRemaining
                                    : args.getPageSize();

                for (int i = args.getStart(); i < (args.getStart() + hitsToProcess); i++)  {
                    Map<String, Object> result = results.results.get(i);

                    String resourceId = (String)result.get("search.resourceid");
                    String resourceType = (String)result.get("search.resourcetype");

                    String handleText = (String)result.get("handle");
                    String handleType = (String)result.get("type");

                    switch (Integer.parseInt(resourceType != null ? resourceType : handleType)) {
                        case Constants.ITEM:
                            hitTypes.add(Constants.ITEM);
                            break;

                        case Constants.COLLECTION:
                            hitTypes.add(Constants.COLLECTION);
                            break;

                        case Constants.COMMUNITY:
                            hitTypes.add(Constants.COMMUNITY);
                            break;
                    }

                    hitHandles.add( handleText );
                    hitIds.add( resourceId == null ? null: Integer.parseInt(resourceId) );
                }
            }
        } catch (InvalidSyntaxException isE) {}

        return qr;
    }
    
    @Override
    public void init(String config) {
        // config is API URL
        client = new IndexTankClient(config);
        index = client.getIndex("index1");
        try {
            if (! index.exists()) {
                // RLR TEST only - should likely be false in production
                boolean apiPublic = true;
                index = client.createIndex("index1", new IndexTankClient.IndexConfiguration().enablePublicSearch(apiPublic));
            }
        } catch (IndexAlreadyExistsException e) {
            // just checked - should not see this
            throw new IllegalStateException("Could not create search index: " + e.getMessage(),e);
        } catch (IOException | MaximumIndexesExceededException e) {
            throw new IllegalStateException("Could not create search index: " + e.getMessage(),e);
        }
    }
    
    private void mapValue(String value, FieldConfig fc, Map<String, String> fields) {
        if ("timestamp".equals(fc.fieldType)) {
            Date date = toDate(value);
            if (date != null) {
                fields.put(fc.fieldName, DateTools.dateToString(date, DateTools.Resolution.SECOND));
                fields.put(fc.fieldName + ".year", DateTools.dateToString(date, DateTools.Resolution.YEAR));
            }
        } else if ("date".equals(fc.fieldType)) {
            Date date = toDate(value);
            if (date != null) {
                fields.put(fc.fieldName, DateTools.dateToString(date, DateTools.Resolution.DAY));
                fields.put(fc.fieldName + ".year", DateTools.dateToString(date, DateTools.Resolution.YEAR));
            }
        } else {
            // all other cases - just add one field with untransformed value
            fields.put(fc.fieldName, value);
        }
    }
    
    /**
     * Helper function to retrieve a date using a best guess of the potential date encodings on a field
     *  
     * @param t
     * @return
     */
    private static Date toDate(String t) {
    
        List<String> fmts = new ArrayList<String>();
        // Choose the likely date formats based on string length
        switch (t.length())  {
            case 4:
                fmts.add("yyyy");
                break;
            case 6:
                fmts.add("yyyyMM");
                break;
            case 7:
                fmts.add("yyyy-MM");
                break;
            case 8:
                fmts.add("yyyyMMdd");
                fmts.add("yyyy MMM");
                break;
            case 10:
                fmts.add("yyyy-MM-dd");
                break;
            case 11:
                fmts.add("yyyy MM dd");
                break;
            case 20:
                fmts.add("yyyy-MM-dd'T'HH:mm:ss'Z'");
                break;
            default:
                fmts.add("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
                break;
        }


        for (String fmt : fmts) {
            try {
                // Parse the date
                DateTimeFormatter formatter = DateTimeFormat.forPattern(fmt);
                DateTime dt = formatter.parseDateTime(t);
                return dt.toDate();
            } catch (IllegalArgumentException pe) {
                log.error("Unable to parse date format", pe);
            }
        }
        return null;
    }
    
    private static class FieldConfig {
        String fieldName;
        String fieldType;
        boolean store;
        String index;
    
        public FieldConfig(String fieldName, String fieldType, boolean store, String index) {
            this.fieldName = fieldName;
            this.fieldType = fieldType;
            this.store = store;
            this.index = index;
        }
    }
}
