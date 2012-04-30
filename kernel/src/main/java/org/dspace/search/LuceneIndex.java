/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.search;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.Reader;
import java.io.StringWriter;
import java.lang.reflect.Constructor;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.List;

import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.LimitTokenCountAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.DateTools;
import org.apache.lucene.index.CorruptIndexException;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.TermDocs;
import org.apache.lucene.queryParser.ParseException;
import org.apache.lucene.queryParser.QueryParser;
import org.apache.lucene.queryParser.TokenMgrError;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.SortField;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.Version;

import com.google.common.base.Strings;

import org.dspace.core.ConfigurationManager;
import org.dspace.core.Constants;
import org.dspace.core.Context;
import org.dspace.core.Email;
import org.dspace.core.I18nUtil;
import org.dspace.core.LogManager;
import org.dspace.handle.HandleManager;
import org.dspace.sort.SortOption;
import org.dspace.sort.OrderFormat;

import static org.dspace.search.DSIndexer.*;

/**
 * LuceneIndex provides indexing and querying services backed by a
 * Lucene index on local disk. This has been factored out of DSIndexer.
 * 
 * NB: there are several ways this service could be generalized.
 * It was implemented to offer compatibility with the old search code,
 * which had a number of limitations. For example, the 'schema'
 * (assignment of indexing attributes to fields, etc) is hard-coded
 * here (as it was in the original DSIndexer) - a more flexible
 * approach would make it file configurable as the SOLR service is.
 * Also, the threading model seems defective, if configured in
 * valid ways, collisions over IndexWriters seems possible.
 * For further analysis and refactor. Essentially bypassed for now.
 * 
 * @author richardrodgers
 */
public class LuceneIndex implements IndexService
{
    private static final Logger log = LoggerFactory.getLogger(LuceneIndex.class);

    private static final long WRITE_LOCK_TIMEOUT = 30000 /* 30 sec */;

    private Thread delayedIndexFlusher = null;
    private int indexFlushDelay = ConfigurationManager.getIntProperty("search", "flush.delay", -1);

    private int batchFlushAfterDocuments = ConfigurationManager.getIntProperty("search", "batch.documents", 20);
    
    private boolean batchProcessingMode = false;
    
    // search field schema - hard-coded here, but could easily be made more configurable
    private static final Map<String, FieldConfig> schema = new HashMap<String, FieldConfig>() {{
    	put(LAST_INDEXED_FIELD,    new FieldConfig(LAST_INDEXED_FIELD, "text", Field.Store.YES, Field.Index.NOT_ANALYZED));
    	put(DOCUMENT_STATUS_FIELD, new FieldConfig(DOCUMENT_STATUS_FIELD, "text", Field.Store.YES, Field.Index.NOT_ANALYZED));
    	put(DOCUMENT_KEY,          new FieldConfig(DOCUMENT_KEY, "text", Field.Store.YES, Field.Index.NOT_ANALYZED));
    	put("search.resourcetype", new FieldConfig("search.resourcetype", "text", Field.Store.YES, Field.Index.NOT_ANALYZED));
    	put("search.resourceid",   new FieldConfig("search.resourceid", "text", Field.Store.YES, Field.Index.NO));
    	put("name",                new FieldConfig("name", "text", Field.Store.NO, Field.Index.ANALYZED));
    	put("default",             new FieldConfig("default", "text", Field.Store.NO, Field.Index.ANALYZED));
    	put("location",            new FieldConfig("location", "text", Field.Store.NO, Field.Index.ANALYZED));
    	// following are templates
    	put("sort_",               new FieldConfig("sort_", "text", Field.Store.NO, Field.Index.NOT_ANALYZED));
    }};
        
    private String indexDirectory;
    
    private int maxFieldLength = -1;
    	
    // TODO: Support for analyzers per language, or multiple indices
    /** The analyzer for this DSpace instance */
    private volatile Analyzer analyzer = null;
    
    // cache a Lucene IndexSearcher for more efficient searches
    private static IndexSearcher searcher = null;
    
    private static long lastModified;
    
    public LuceneIndex() {}

    static { 	        
        /*
         * Increase the default write lock so that Indexing can be interrupted.
         */
        IndexWriterConfig.setDefaultWriteLockTimeout(WRITE_LOCK_TIMEOUT);
        int maxClauses = ConfigurationManager.getIntProperty("search", "max-clauses", -1);
        if (maxClauses > 0){
            BooleanQuery.setMaxClauseCount(maxClauses);
        } 
    }

    public void setBatchProcessingMode(boolean mode) {
        batchProcessingMode = mode;
        if (mode == false) {
            flushIndexingTaskQueue();
        }
    }
     
	/**
     * Get the Lucene analyzer to use according to current configuration (or
     * default). TODO: Should have multiple analyzers (and maybe indices?) for
     * multi-lingual DSpaces.
     *
     * @return <code>Analyzer</code> to use
     * @throws IllegalStateException
     *             if the configured analyzer can't be instantiated
     */
    Analyzer getAnalyzer() {
        if (analyzer == null)
        {
            // We need to find the analyzer class from the configuration
            String analyzerClassName = ConfigurationManager.getProperty("search", "analyzer.default");

            if (analyzerClassName == null) {
                // Use default
                analyzerClassName = "org.dspace.search.DSAnalyzer";
            }

            try {
                Class analyzerClass = Class.forName(analyzerClassName);
                Constructor constructor = analyzerClass.getDeclaredConstructor(Version.class);
                constructor.setAccessible(true);
                analyzer = (Analyzer) constructor.newInstance(Version.LUCENE_36);
                if (maxFieldLength > -1) {
                	analyzer = new LimitTokenCountAnalyzer(analyzer, maxFieldLength);
                }
            } catch (Exception e) {
                log.error(LogManager.getHeader(null, "no_search_analyzer",
                        "search.analyzer=" + analyzerClassName), e);

                throw new IllegalStateException(e.toString());
            }
        }

        return analyzer;
    }


    void processIndexingTask(IndexingTask task) throws IOException {
        if (batchProcessingMode) {
            addToIndexingTaskQueue(task);
        } else if (indexFlushDelay > 0) {
            addToIndexingTaskQueue(task);
            startDelayedIndexFlusher();
        } else {
            IndexWriter writer = null;
            try  {
                writer = openIndex(false);
                executeIndexingTask(writer, task);
            } finally {
                //if (task.getDocument() != null)
                //{
                //    closeAllReaders(task.getDocument());
                //}

                if (writer != null)
                {
                    try
                    {
                        writer.close();
                    }
                    catch (IOException e)
                    {
                        log.error("Unable to close IndexWriter", e);
                    }
                }
            }
        }
    }

    private static void executeIndexingTask(IndexWriter writer, IndexingTask action) throws IOException {
        if (action != null)
        {
        	/*
            if (action.isDelete())
            {
                if (action.getDocument() != null)
                {
                    writer.updateDocument(action.getTerm(), action.getDocument());
                }
                else
                {
                    writer.deleteDocuments(action.getTerm());
                }
            }
            else
            {
                writer.updateDocument(action.getTerm(), action.getDocument());
            }
            */
        }
    }

    private  Map<String, IndexingTask> queuedTaskMap = new HashMap<String, IndexingTask>();

    synchronized void addToIndexingTaskQueue(IndexingTask action)
    {
        if (action != null)
        {
           // queuedTaskMap.put(action.getTerm().text(), action);
            if (queuedTaskMap.size() >= batchFlushAfterDocuments)
            {
                flushIndexingTaskQueue();
            }
        }
    }

    void flushIndexingTaskQueue()
    {
        if (queuedTaskMap.size() > 0)
        {
            IndexWriter writer = null;

            try
            {
                writer = openIndex(false);
                flushIndexingTaskQueue(writer);
            }
            catch (IOException e)
            {
                log.error("Error flushing", e);
            }
            finally
            {
                if (writer != null)
                {
                    try
                    {
                        writer.close();
                    }
                    catch (IOException ex)
                    {
                        log.error("Error closing writer", ex);
                    }
                }
            }
        }
    }

    private synchronized void flushIndexingTaskQueue(IndexWriter writer)
    {
        for (IndexingTask action : queuedTaskMap.values())
        {
            try
            {
                executeIndexingTask(writer, action);
            }
            catch (IOException e)
            {
                log.error("Error indexing", e);
            }
           // finally
           // {
                //if (action.getDocument() != null)
                //{
                //    closeAllReaders(action.getDocument());
                //}
           // }
        }

        queuedTaskMap.clear();

        // We've flushed, so we don't need this thread
        if (delayedIndexFlusher != null)
        {
            delayedIndexFlusher.interrupt();
            delayedIndexFlusher = null;
        }
    }

    ////////////////////////////////////
    //      Private
    ////////////////////////////////////
    
    /**
	 * Is stale checks the lastModified time stamp in the database and the index
	 * to determine if the index is stale.
	 * 
	 * @param lastModified
	 * @throws SQLException
	 * @throws IOException
	 */
    @Override
    public boolean isDocumentStale(String documentKey, Date lastModified)  throws IOException {
		
		boolean reindexItem = false;
		boolean inIndex = false;
		
		IndexReader ir = getSearcher().getIndexReader();
		Term t = new Term("handle", documentKey);
		TermDocs docs = ir.termDocs(t);
						
		while(docs.next())
		{
			inIndex = true;
			int id = docs.doc();
			Document doc = ir.document(id);

			Field lastIndexed = (Field)doc.getFieldable(LAST_INDEXED_FIELD);

			if (lastIndexed == null || Long.parseLong(lastIndexed.stringValue()) < 
					lastModified.getTime()) {
				reindexItem = true;
			}
		}

		return reindexItem || !inIndex;
	}

    /**
     * prepare index, opening writer, and wiping out existing index if necessary
     */
    private IndexWriter openIndex(boolean wipeExisting) throws IOException {
        Directory dir = FSDirectory.open(new File(indexDirectory));
        IndexWriterConfig iwc = new IndexWriterConfig(Version.LUCENE_36, getAnalyzer());
        if(wipeExisting){
            iwc.setOpenMode(IndexWriterConfig.OpenMode.CREATE);
        }else{
            iwc.setOpenMode(IndexWriterConfig.OpenMode.CREATE_OR_APPEND);
        }

        IndexWriter writer = new IndexWriter(dir, iwc);
        
        return writer;
    }
    
    @Override
    public void doTask(IndexingTask task) throws IOException {
    	
    	switch (task.getAction()) {
    		case DELETE:
    			commit(task.getFieldValue(DOCUMENT_KEY), null, false);
    			break;
    		case UPDATE:
    			Document doc = new Document();
    			// add in fields
    			for (String key : task.getFieldKeys()) {
    				// get config for field
    				FieldConfig fc = schema.get(key);
    				if (fc != null) {
    					for (String value : task.getFieldValues(key)) {
    						mapValue(value, fc, doc);
    	    				// all get mapped to 'default' index
    	    				mapValue(value, schema.get("default"), doc);
    					}
    				} else {
    					log.error("Invalid field map - field: '" + key + "' undefined in schema");
    				}
    			}
    			// likewise any streams
    			for (String key : task.getStreamKeys()) {
    				for (InputStream is : task.getStreamValues(key)) {
    					doc.add(new Field("default", new BufferedReader(new InputStreamReader(is))));
    				}
    			}
    			commit(task.getFieldValue(DOCUMENT_KEY), doc, true);
    			break;
    		case TX_BEGIN:
    			setBatchProcessingMode(true);
    			break;
    		case TX_END:
    			setBatchProcessingMode(false);
    			break;
    		case PURGE:
    			openIndex(true).close();
    			break;
    		default:
    		  break;
    	}
    }
        
    @Override
    public QueryResults doQuery(QueryArgs args) throws IOException {
        String querystring = args.getQuery();
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
        querystring = DSQuery.checkEmptyQuery(querystring); // change nulls to an empty string
        querystring = DSQuery.stripHandles(querystring); // remove handles from query string
        querystring = DSQuery.stripAsterisk(querystring); // remove asterisk from beginning of string
        
        try  {
            // grab a searcher, and do the search
            IndexSearcher searcher = getSearcher();
            // FIXME
            QueryParser qp = new QueryParser(Version.LUCENE_36, "default", getAnalyzer());
            log.debug("Final query string: " + querystring);
            
            String operator = DSQuery.getOperator();
            if (operator == null || operator.equals("OR")) {
            	qp.setDefaultOperator(QueryParser.OR_OPERATOR);
            } else {
            	qp.setDefaultOperator(QueryParser.AND_OPERATOR);
            }

            Query myquery = qp.parse(querystring);
            //Retrieve enough docs to get all the results we need !
            TopDocs  hits = performQuery(args, searcher, myquery, args.getPageSize() * (args.getStart() + 1));

            // set total number of hits
            qr.setHitCount(hits.totalHits);

            // We now have a bunch of hits - snip out a 'window'
            // defined in start, count and return the handles
            // from that window
            // first, are there enough hits?
            if (args.getStart() < hits.totalHits) {
                // get as many as we can, up to the window size
                // how many are available after snipping off at offset 'start'?
                int hitsRemaining = hits.totalHits - args.getStart();

                int hitsToProcess = (hitsRemaining < args.getPageSize()) ? hitsRemaining
                        			: args.getPageSize();

                for (int i = args.getStart(); i < (args.getStart() + hitsToProcess); i++)  {
                    Document d = searcher.doc(hits.scoreDocs[i].doc);

                    String resourceId   = d.get("search.resourceid");
                    String resourceType = d.get("search.resourcetype");

                    String handleText = d.get("handle");
                    String handleType = d.get("type");

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
        }
        catch (NumberFormatException e)  {
            log.warn("Number format exception", e);
            qr.setErrorMsg("number-format-exception");
        } catch (ParseException e) {
            // a parse exception - log and return null results
            log.warn("Invalid search string", e);
            qr.setErrorMsg("invalid-search-string");
        }  catch (TokenMgrError tme) {
            // Similar to parse exception
            log.warn("Invalid search string", tme);
            qr.setErrorMsg("invalid-search-string");
        }  catch(BooleanQuery.TooManyClauses e) {
            log.warn("Query too broad", e.toString());
            qr.setErrorMsg("query-too-broad");
        }

    	return qr;
    }
    
    private static TopDocs performQuery(QueryArgs args, IndexSearcher searcher, Query myquery, int max) throws IOException {
        TopDocs hits;
        try
        {
            if (args.getSortOption() == null)
            {
                SortField[] sortFields = new SortField[] {
                        new SortField("search.resourcetype", SortField.INT, true),
                        new SortField(null, SortField.SCORE, SortOption.ASCENDING.equals(args.getSortOrder()))
                    };
                hits = searcher.search(myquery, max, new Sort(sortFields));
            }
            else
            {
                SortField[] sortFields = new SortField[] {
                        new SortField("search.resourcetype", SortField.INT, true),
                        new SortField("sort_" + args.getSortOption().getName(), SortField.STRING, SortOption.DESCENDING.equals(args.getSortOrder())),
                        SortField.FIELD_SCORE
                    };
                hits = searcher.search(myquery, max, new Sort(sortFields));
            }
        }
        catch (Exception e)
        {
            // Lucene can throw an exception if it is unable to determine a sort time from the specified field
            // Provide a fall back that just works on relevancy.
            log.error("Unable to use speficied sort option: " + (args.getSortOption() == null ? "type/relevance": args.getSortOption().getName()));
            hits = searcher.search(myquery, max, new Sort(SortField.FIELD_SCORE));
        }
        return hits;
    }
    
    @Override
    public void init(String config) {
    	indexDirectory = config;
        try {
            if (!IndexReader.indexExists(FSDirectory.open(new File(indexDirectory)))) {
                if (!new File(indexDirectory).mkdirs()) {
                    log.error("Unable to create index directory: " + indexDirectory);
                }
                openIndex(true).close();
            }
        } catch (IOException e) {
            throw new IllegalStateException("Could not create search index: " + e.getMessage(),e);
        }
    	// set maxfieldlength
        maxFieldLength = ConfigurationManager.getIntProperty("search", "maxfieldlength", -1);
    }
    
    private void commit(String documentKey, Document doc, boolean update) throws IOException {
        IndexWriter writer = null;
        Term term = new Term(DOCUMENT_KEY, documentKey);
        try {
            writer = openIndex(false);
            if (update) {
            	writer.updateDocument(term, doc);
            } else {
            	writer.deleteDocuments(term);
            }
        } finally {
            if (doc != null)
            {
                closeAllReaders(doc);
            }
            if (writer != null) {
                try {
                    writer.close();
                } catch (IOException e) {
                    log.error("Unable to close IndexWriter", e);
                }
            }
        } 	
    }
    
    private void mapValue(String value, FieldConfig fc, Document doc) {
    	if ("timestamp".equals(fc.fieldType)) {
    		Date date = toDate(value);
    		if (date != null) {
    			doc.add(new Field(fc.fieldName,
    					DateTools.dateToString(date, DateTools.Resolution.SECOND), fc.store, fc.index));
    			doc.add(new Field(fc.fieldName + ".year",
    					DateTools.dateToString(date, DateTools.Resolution.YEAR), fc.store, fc.index));
    		}
    	} else if ("date".equals(fc.fieldType)) {
    		Date date = toDate(value);
    		if (date != null) {
    			doc.add(new Field(fc.fieldName,
    					DateTools.dateToString(date, DateTools.Resolution.DAY), fc.store, fc.index));
    			doc.add(new Field(fc.fieldName + ".year",
    					DateTools.dateToString(date, DateTools.Resolution.YEAR), fc.store, fc.index));
    		}
    	} else {
    		// all other cases - just add one field with untransformed value
    		doc.add(new Field(fc.fieldName, value, fc.store, fc.index));
    	}
    }

    private static void closeAllReaders(Document doc) {
        if (doc != null)
        {
            int count = 0;
            List fields = doc.getFields();
            if (fields != null)
            {
                for (Field field : (List<Field>)fields)
                {
                    Reader r = field.readerValue();
                    if (r != null)
                    {
                        try
                        {
                            r.close();
                            count++;
                        }
                        catch (IOException e)
                        {
                            log.error("Unable to close reader", e);
                        }
                    }
                }
            }

            if (count > 0)
            {
                log.debug("closed " + count + " readers");
            }
        }
    }
    
    /**
     * get an IndexSearcher, hopefully a cached one (gives much better
     * performance.) checks to see if the index has been modified - if so, it
     * creates a new IndexSearcher
     */
    protected synchronized IndexSearcher getSearcher() throws IOException {
       
        // If we have already opened a searcher, check to see if the index has been updated
        // If it has, we need to close the existing searcher - we will open a new one later
        Directory searchDir = FSDirectory.open(new File(indexDirectory));
        IndexReader idxReader = getSearcher().getIndexReader();
        if (searcher != null && lastModified != idxReader.getVersion())
        {
            try
            {
                // Close the cached IndexSearcher
                searcher.close();
            }
            catch (IOException ioe)
            {
                // Index is probably corrupt. Log the error, but continue to either:
                // 1) Return existing searcher (may yet throw exception, no worse than throwing here)
                log.warn("DSQuery: Unable to check for updated index", ioe);
            }
            finally
            {
            	searcher = null;
            }
        }

        // There is no existing searcher - either this is the first execution,
        // or the index has been updated and we closed the old index.
        if (searcher == null)
        {
            // So, open a new searcher
            lastModified = idxReader.getVersion();
            String osName = System.getProperty("os.name");
            // RLR TODO - check Read only restriction here
            IndexReader reader = IndexReader.open(searchDir);
            if (osName != null && osName.toLowerCase().contains("windows"))
            {
                searcher = new IndexSearcher(reader){
                    /*
                     * TODO: Has Lucene fixed this bug yet?
                     * Lucene doesn't release read locks in
                     * windows properly on finalize. Our hack
                     * extend IndexSearcher to force close().
                     */
                    @Override
                    protected void finalize() throws Throwable {
                        this.close();
                        super.finalize();
                    }
                };
            }
            else
            {
                searcher = new IndexSearcher(reader);
            }
        }

        return searcher;
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

    private synchronized void startDelayedIndexFlusher()
    {
        if (delayedIndexFlusher != null && !delayedIndexFlusher.isAlive())
        {
            delayedIndexFlusher = null;
        }

        if (delayedIndexFlusher == null && queuedTaskMap.size() > 0)
        {
            delayedIndexFlusher = new Thread(new DelayedIndexFlushThread());
            delayedIndexFlusher.start();
        }
    }

    private class DelayedIndexFlushThread implements Runnable
    {
        @Override
        public void run()
        {
            try
            {
                Thread.sleep(indexFlushDelay);
                flushIndexingTaskQueue();
            }
            catch (InterruptedException e)
            {
                log.debug("Delayed flush", e);
            }
        }
    }
    
    private static class FieldConfig {
    	String fieldName;
    	String fieldType;
    	Field.Store store;
    	Field.Index index;
    	
    	public FieldConfig(String fieldName, String fieldType, Field.Store store, Field.Index index) {
    		this.fieldName = fieldName;
    		this.fieldType = fieldType;
    		this.store = store;
    		this.index = index;
    	}
    }
}
