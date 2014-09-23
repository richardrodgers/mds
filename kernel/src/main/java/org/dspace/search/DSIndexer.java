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
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.Reader;
import java.io.StringWriter;
import java.lang.reflect.Constructor;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.List;
import java.util.regex.Pattern;

import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.CharMatcher;
import com.google.common.base.Strings;
import com.google.common.eventbus.Subscribe;

import org.dspace.content.Bitstream;
import org.dspace.content.BoundedIterator;
import org.dspace.content.Bundle;
import org.dspace.content.Collection;
import org.dspace.content.Community;
import org.dspace.content.MDValue;
import org.dspace.content.DSpaceObject;
import org.dspace.content.Item;
import org.dspace.core.ConfigurationManager;
import org.dspace.core.Constants;
import org.dspace.core.Context;
import org.dspace.core.Email;
import org.dspace.core.I18nUtil;
import org.dspace.core.LogManager;
import org.dspace.event.Consumes;
import org.dspace.event.ContainerEvent;
import org.dspace.event.ContentEvent;
import org.dspace.event.ContentEvent.EventType;
import org.dspace.handle.HandleManager;
import org.dspace.sort.SortOption;
import org.dspace.sort.OrderFormat;

/**
 * DSIndexer contains the methods that index DSpaceObjects and their metadata.
 * It is meant to either be invoked from the command line (see dspace/bin/index-all)
 * or via the search service API (e.g. indexContent()) within DSpace.
 * 
 * DSIndexer is the 'front-end' of indexing, and relies on one or more
 * 'IndexService' implementations to manage the actual indices.
 * Service implementations include a disk-local Lucene index, 
 * and a Solr index server.
 *
 * @author richardrodgers
 */
@Consumes("content")
public class DSIndexer {

    private static final Logger log = LoggerFactory.getLogger(DSIndexer.class);

    public static final String LAST_INDEXED_FIELD = "DSIndexer.lastIndexed";
    public static final String DOCUMENT_STATUS_FIELD = "DSIndexer.status";
    public static final String DOCUMENT_KEY = "handle";
    public static final String DEFAULT_INDEX = "default";
    
    private static final CharMatcher matchDot = CharMatcher.is('.');
    private static final CharMatcher matchStar = CharMatcher.is('*');
    
    // map of configured indexes
    private static Map<String, IndexConfig> configs;
    
    // command-line options
    @Option(name="-r", usage="remove an Item, Collection or Community from index based on its handle")
    private String removeHandle;
        
    @Option(name="-b", usage="(re)build index, wiping out current one if it exists")
    private boolean build;
    
    @Option(name="-f", usage="if updating existing index, force each handle to be reindexed even if uptodate")
    private boolean force;
    
    @Option(name="-h", usage="print helpful message")
    private boolean help;
    
    public DSIndexer() {}

    static {    	   	
        // read in indexing configuration - for each index
    	configs = new HashMap<String, IndexConfig>();
    	Properties idxProps = ConfigurationManager.getMatchedProperties("search", "index");
    	for(String indexName : idxProps.stringPropertyNames()) {
    		configs.put(indexName, new IndexConfig(indexName, idxProps.getProperty(indexName)));
    	}
    	if (! configs.containsKey(DEFAULT_INDEX)) {
    		log.error("No default index configured");
    	}
    }
    
    /**
     * Returns index service implementation instance for a given name
     */
    public static IndexService getService(String indexName) {
    	String key = (indexName != null) ? indexName : DEFAULT_INDEX;
    	IndexConfig config = configs.get(key);
    	return (config != null) ? config.service : null;
    }
    
    /**
     * Instructs the indexing system to operate in a batch if it is able.
     * 
     * @param enable start a batch if true, end a batch if false
     */
    public void setBatchProcessingMode(boolean enable) throws IOException {
    		distributeTask(new IndexingTask(enable ?
    				                        IndexingTask.Action.TX_BEGIN :
    		                                IndexingTask.Action.TX_END));
    }

    /**
     * Event Listeners for Indexing
     *
     */
    @Subscribe
    public void indexContentEvent(ContentEvent event) throws IOException, SQLException {
        String handle = event.getObject().getHandle();
        switch(event.getEventType()) {
            case CREATE:
            case MODIFY:
                if (handle != null) {
                    indexContent(event.getContext(), event.getObject(), true);
                }
                break;
            case DELETE: 
                if (handle != null) {
                    // RLR NOte - context not used!
                    unIndexContent(event.getContext(), handle);
                }
                break;
            default: break;
        }
    }

    @Subscribe
    public void indexContainerEvent(ContainerEvent event) throws IOException, SQLException {
        switch(event.getEventType()) {
            case ADD:
            case REMOVE:
                indexContent(event.getContext(), event.getMember(), true);
                break;
            default: break;
        }
    }

    /**
     * If the handle for the "dso" already exists in the index, and
     * the "dso" has a lastModified timestamp that is newer than 
     * the document in the index then it is updated, otherwise a 
     * new document is added.
     * 
     * @param context Users Context
     * @param dso DSpace Object (Item, Collection or Community)
     * @param force Force update even if not stale.
     * @throws SQLException
     */
    public void indexContent(Context context, DSpaceObject dso, boolean force) throws SQLException {
    	for (IndexConfig cfg : configs.values()) {
    		indexContent(context, dso, force, cfg);
    	}
    }

    private void indexContent(Context context, DSpaceObject dso, boolean force, IndexConfig config) throws SQLException {
        try {
            String handle = dso.getHandle();
            IndexingTask task = null;
            switch (dso.getType()) {
            	case Constants.ITEM :
            		Item item = (Item)dso;
            		if (item.isArchived() && !item.isWithdrawn()) {
            			/** If the item is in the repository now, add it to the index*/
            			if (force || config.service.isDocumentStale(handle, ((Item)dso).getLastModified()))	{
            				log.info("Writing Item: " + handle + " to Index");
            				task = buildItemTask((Item)dso, config);
            			}
            		} else {
            			task = new IndexingTask(IndexingTask.Action.DELETE);
            			task.addField(DOCUMENT_KEY, handle);
            		}
            		break;

            	case Constants.COLLECTION :
            		log.info("Writing Collection: " + handle + " to Index");
            		task = buildCollectionTask((Collection)dso, config);
            		break;

            	case Constants.COMMUNITY :
            		log.info("Writing Community: " + handle + " to Index");
            		task = buildCommunityTask((Community)dso, config);
            		break;

            	default :
            		log.error("Only Items, Collections and Communities can be Indexed");
            }
            if (task != null) {
            	config.service.doTask(task);
            }
        } catch (IOException e) {
            log.error("Error indexing", e);
        }
    }

    /**
     * unIndex removes an Item, Collection, or Community only works if the
     * DSpaceObject has a handle (uses the handle for its unique ID)
     * 
     * @param context DSpace context
     * @param dso DSpace Object, can be Community, Item, or Collection
     * @throws SQLException
     * @throws IOException
     */
    public void unIndexContent(Context context, DSpaceObject dso) throws SQLException, IOException {
        try {
        	unIndexContent(context, dso.getHandle());
        } catch(Exception exception) {
            log.error("Error Unindexing", exception.getMessage(),exception);
            emailException(context, exception);
        }
    }

    /**
     * Unindex a Document in the Lucene Index.
     * 
     * @param context
     * @param handle 
     * @throws SQLException
     * @throws IOException
     */
    public void unIndexContent(Context context, String handle) throws SQLException, IOException {
        if (handle != null) {
            IndexingTask task = new IndexingTask(IndexingTask.Action.DELETE);
            task.addField(DOCUMENT_KEY, handle);
            distributeTask(task);
        } else {
            log.warn("unindex of content with null handle attempted");
            // FIXME: no handle, fail quietly - should log failure
            //System.out.println("Error in unIndexContent: Object had no
            // handle!");
        }
    }
    
    /**
     * reIndexContent removes something from the index, then re-indexes it
     *
     * @param context context object
     * @param dso  object to re-index
     */
    public void reIndexContent(Context context, DSpaceObject dso)
            throws SQLException, IOException  {
        try {
        	indexContent(context, dso, false);
        } catch(Exception exception) {
            log.error(exception.getMessage(), exception);
            emailException(context, exception);
        }
    }
    
    /**
	 * create full index - wiping old index
	 * 
	 * @param c context to use
	 */
    public void createIndex(Context c) throws SQLException, IOException {
    	/* Create a new index, blowing away the old. */
        distributeTask(new IndexingTask(IndexingTask.Action.PURGE)); 
        /* Reindex all content preemptively. */
        updateIndex(c, true);
    }
    
    /**
     * When invoked as a command-line tool, creates, updates, removes 
     * content from the whole index
     *
     * @param args
     *            the command-line arguments, none used
     * @throws IOException 
     * @throws SQLException 
     */
    public static void main(String[] args) throws SQLException, IOException {
        Date startTime = new Date();
        DSIndexer dsi = new DSIndexer();
        CmdLineParser parser = new CmdLineParser(dsi);
        try
        {
        	parser.parseArgument(args);
            dsi.setBatchProcessingMode(true);
            Context context = new Context();
            context.turnOffAuthorisationSystem();

            if (dsi.help) {
            	// automatically generate the help statement
            	parser.printUsage(System.err);
            	System.exit(0);
            }

            if (dsi.removeHandle != null) {
                //log.info("Removing " + line.getOptionValue("r") + " from Index");
            	dsi.unIndexContent(context, dsi.removeHandle);
            } else if (dsi.build) {
            	log.info("(Re)building index from scratch.");
            	dsi.createIndex(context);
            } else {
            	log.info("Updating and Cleaning Index");
            	dsi.updateIndex(context, dsi.force);
            }

            log.info("Done with indexing");
    	} catch (CmdLineException clE) {
    		System.err.println(clE.getMessage());
    		parser.printUsage(System.err);
    	} catch (Exception e) {
    		System.err.println(e.getMessage());
    	} finally {
            dsi.setBatchProcessingMode(false);
            Date endTime = new Date();
            System.out.println("Started: " + startTime.getTime());
            System.out.println("Ended: " + endTime.getTime());
            System.out.println("Elapsed time: " + ((endTime.getTime() - startTime.getTime()) / 1000) + " secs (" + (endTime.getTime() - startTime.getTime()) + " msecs)");
        }
    	System.exit(1);
    }
    
    /**
     * Iterates over all Items, Collections and Communities. And updates
     * them in the index. Uses BoundedIterators to control memory footprint.
     * Uses indexContent and isStale to check state of item in index.
     * 
     * At first it may appear counterintuitive to have an IndexWriter/Reader
     * opened and closed on each DSO. But this allows the UI processes
     * to step in and attain a lock and write to the index even if other
     * processes/jvms are running a reindex.
     * 
     * @param context
     * @param force 
     */
    public void updateIndex(Context context, boolean force) {
    	BoundedIterator<Item> items = null;
    	BoundedIterator<Collection> colIter = null;
    	BoundedIterator<Community> comIter = null;
    	try {
            items = Item.findAll(context);
            while (items.hasNext()) {
                indexContent(context, items.next(), false);
            }

            colIter = Collection.findAll(context);
            while (colIter.hasNext()) {
                indexContent(context, colIter.next(), false);
            }
            comIter = Community.findAll(context);
            while (comIter.hasNext()) {
                indexContent(context, comIter.next(), false);
    	    }

        } catch(Exception e) {
    		log.error(e.getMessage(), e);
    	} finally {
    		if (items != null) {
    			items.close();
    		}   		
    		if (colIter != null) {
    			colIter.close();
    		}
    		if (comIter != null) {
    			comIter.close();
    		}
    	}
    }
        
    ////////////////////////////////////
    //      Private
    ////////////////////////////////////
    
    // NB: Only use when task applies to any index
    private void distributeTask(IndexingTask task) throws IOException {
    	for (IndexConfig cfg : configs.values()) {
    		cfg.service.doTask(task);
    	}
    }

    private static void emailException(Context context, Exception exception) {
		// Also email an alert, system admin may need to check for stale lock
		try {
			String recipient = ConfigurationManager
					.getProperty("alert.recipient");

			if (recipient != null) {
				Email email = Email.fromTemplate(context, I18nUtil.getEmailFilename(Locale.getDefault(), "internal_error"));
				email.addRecipient(recipient);
				email.addArgument(ConfigurationManager
						.getProperty("dspace.url"));
				email.addArgument(new Date());

				String stackTrace;

				if (exception != null) {
					StringWriter sw = new StringWriter();
					PrintWriter pw = new PrintWriter(sw);
					exception.printStackTrace(pw);
					pw.flush();
					stackTrace = sw.toString();
				} else {
					stackTrace = "No exception";
				}

				email.addArgument(stackTrace);
				email.send();
			}
		} catch (Exception e) {
			// Not much we can do here!
			log.warn("Unable to send email alert", e);
		}
	}
    

    /**
     * Build a task for a DSpace Community.
     *
     * @param community Community to be indexed
     * @throws SQLException
     * @throws IOException
     */
    private IndexingTask buildCommunityTask(Community community, IndexConfig config) 
    		throws SQLException, IOException {
    	IndexingTask task = new IndexingTask(IndexingTask.Action.UPDATE);
    	task.addField(DOCUMENT_KEY, community.getHandle());
        task.addFieldSet(buildCommon(community));
        String name = community.getName();
        if (name != null) {
        	task.addField("name", name);
        	task.addField("default", name);
        }
        // add in any generic metadata
        addMetadata(community, task, config.commRules);
        return task;
    }

    /**
     * Build a field map for a DSpace Collection.
     *
     * @param collection Collection to be indexed
     * @throws SQLException
     * @throws IOException
     */
    private IndexingTask buildCollectionTask(Collection collection, IndexConfig config)
    		throws SQLException, IOException {
    	IndexingTask task = new IndexingTask(IndexingTask.Action.UPDATE);
    	task.addField(DOCUMENT_KEY, collection.getHandle());
        task.addFieldSet(buildCommon(collection));
        String name = collection.getName();
        if (name != null) {
        	task.addField("name", name);
        	task.addField("default", name);
        }
        // add in any generic metadata
        addMetadata(collection, task, config.collRules);
        return task;
    }

    /**
     * Build an indexing task for a DSpace Item.
     *
     * @param item The DSpace Item to be indexed
     * @throws SQLException
     * @throws IOException
     */
    private IndexingTask buildItemTask(Item item, IndexConfig config) throws SQLException, IOException {
        String handle = item.getHandle();
        IndexingTask task = new IndexingTask(IndexingTask.Action.UPDATE);
        task.addField(DOCUMENT_KEY, handle);
        task.addFieldSet(buildCommon(item));
    
        log.debug("Building Item: " + handle);
        
        // generic item metadata
        addMetadata(item, task, config.itemRules);

        try {
            // Now get the configured sort options, and add those as untokenized fields
            // Note that we will use the sort order delegates to normalise the values written
            for (SortOption so : SortOption.getSortOptions()) {
                String[] somd = so.getMdBits();
                List<MDValue> dcv = item.getMetadata(somd[0], somd[1], somd[2], MDValue.ANY);
                if (dcv.size() > 0) {
                    String value = OrderFormat.makeSortString(dcv.get(0).getValue(), dcv.get(0).getLanguage(), so.getType());
                    task.addField("sort_" + so.getName(), value);
                }
            }
        } catch (Exception e) {
            log.error(e.getMessage(),e);
        }

        log.debug("  Added Sorting");

        try {
        	// now examine the bundles and bitstreams for indexable content
            for (Bundle bundle : item.getBundles()) {
            	// start with any bundle metadata
            	addMetadata(bundle, task, config.bundleRules);
            	for (Bitstream bitstream : bundle.getBitstreams()) {
            		// check both metadata and files themselves
            		addMetadata(bitstream, task, config.bitstreamRules);
                	String[] fields = firstMatch(bundle.getName() + "." + bitstream.getName(), config.fileRules);
                	for (String field : fields) {
                		if (field.startsWith("@")) {
                			StreamParser sp = getParser(field.substring(1));
                			if (sp != null) {
                				sp.parse(bitstream.retrieve(), task);
                			} else {
                				log.error("Invalid StreamParser: " + field.substring(1));
                			}
                		} else {
                			// no parsing required - presumed to be simple text
                			task.addStream(field, bitstream.retrieve());
                		}
                	}
                }
            }
        } catch(Exception e) {
        	log.error(e.getMessage(),e);
        }

        log.info("Wrote Item: " + handle + " to Index");        
        return task;
    }
    
    private void addMetadata(DSpaceObject dso, IndexingTask task, List<IndexRule> rules) throws SQLException {
        // Examine all md fields and assign those matching any indexing rule
    	int type = dso.getType();
    	String prefix = "";
    	if (Constants.BUNDLE == type) {
    		prefix = ((Bundle)dso).getName() + ".";
    	} else if (Constants.BITSTREAM == type) {
    		Bitstream bs = (Bitstream)dso;
    		prefix = bs.getBundles().get(0).getName() + "." + bs.getName() + ".";
    	}
        for (MDValue value : dso.getMetadata(MDValue.ANY, MDValue.ANY, MDValue.ANY, MDValue.ANY)) {
        	String[] indexes = firstMatch(prefix + value.getSchema() + "." + value.getElement() + "." + value.getQualifier(), rules);
        	for (String index : indexes) {
        		task.addField(index, value.getValue());
        	}
        }
        log.debug("  Added Metadata");
    }
    
    /**
     * Create map of all fields common to DSOs
     */
    private Map<String, String> buildCommon(DSpaceObject dso) throws SQLException {
        Map<String, String> fieldMap = new HashMap<String, String>();
        // always record last update
        fieldMap.put(LAST_INDEXED_FIELD, Long.toString(System.currentTimeMillis()));
        fieldMap.put(DOCUMENT_STATUS_FIELD, "archived");
        fieldMap.put("search.resourcetype", Integer.toString(dso.getType()));
        fieldMap.put("search.resourceid", Integer.toString(dso.getID()));
        String handle = dso.getHandle();
        if (handle != null) {
            fieldMap.put("handle", handle);
            fieldMap.put("default", handle);
        }
        StringBuffer sb = new StringBuffer();
        int type = dso.getType();
        if (type == Constants.COLLECTION) {
            for (Community community : ((Collection)dso).getCommunities()) {
                sb.append(" m").append(community.getID());
            }
    	} else if (type == Constants.ITEM) {
       		for (Community community : ((Item)dso).getCommunities()) {
    			sb.append(" m").append(community.getID());
    		}
    		for (Collection collection : ((Item)dso).getCollections()) {
    			sb.append(" l").append(collection.getID());
    		}
    	}
    	String location = sb.toString();
    	if (location.length() > 0) {
    		fieldMap.put("location", location);
    		fieldMap.put("default", location);
    	}
    	return fieldMap;
    }
    
    private StreamParser getParser(String name) {
    	StreamParser parser = null;
    	String pdata = ConfigurationManager.getProperty("search", "parser." + name);
    	if (pdata != null) {
    		String[] parts = pdata.split(":");
    		try {
    			parser = (StreamParser)Class.forName(parts[0]).newInstance();
    			if (parts.length > 1) {
        			parser.init(parts[1]);
        		}
    		} catch (Exception e) {}
    	}
    	return parser;
    }
    
    private static Pattern glob2regex(String theGlob) {
    	String glob = (theGlob == null) ? "*" : theGlob;
    	StringBuffer sb = new StringBuffer("^");
    	for (int i = 0; i < glob.length(); i++) {
    		char c = glob.charAt(i);
    		switch (c) {
    			case '*': sb.append(".*"); break;
    			case '?': sb.append('.'); break;
    			case '.': sb.append("\\."); break;
    			case '\\': sb.append("\\\\"); break;
    			default: sb.append(c);
    		}
    	}
    	sb.append("$");
    	return Pattern.compile(sb.toString());
    }
    
    /*
     * Returns the field names associated with the indexing rule that *best* matches
     * the passed metadata field.
     */
    private static String[] firstMatch(String metadataName, List<IndexRule> rules) {
    	for (IndexRule ir : rules) {
    		if (ir.matches(metadataName)) {
    			return ir.indexNames;
    		}
    	}
    	return new String[0];
    }
    
    // contains all config info for a given index
    private static class IndexConfig {
    	String indexName;
    	IndexService service;
    	List<IndexRule> commRules;
    	List<IndexRule> collRules;
    	List<IndexRule> itemRules;
    	List<IndexRule> bundleRules;
    	List<IndexRule> bitstreamRules;
    	List<IndexRule> fileRules;
    	
    	public IndexConfig(String indexName, String svcInfo) {
    		this.indexName = indexName;
    		// try to load service
    		String[] parts = svcInfo.split("\\|");
    		try {
    			service = (IndexService)Class.forName(parts[0]).newInstance();
    		} catch (Exception e) {}
    		service.init(parts[1]);
    		String prefix = DEFAULT_INDEX.equals(indexName) ? "" : indexName + ".";
    		commRules = loadIndexingRules(prefix + "community");
    		collRules = loadIndexingRules(prefix + "collection");
    		itemRules = loadIndexingRules(prefix + "item");
    		bundleRules = loadIndexingRules(prefix + "bundle");
    		bitstreamRules = loadIndexingRules(prefix + "bitstream");
    		fileRules = loadIndexingRules(prefix + "file");
    	}
    }
    
    private static List<IndexRule> loadIndexingRules(String ruleSetName) {
        // read in indexing configuration
    	Properties idxConfProps = ConfigurationManager.getMatchedProperties("search", ruleSetName);
    	IndexRule[] ruleArray = new IndexRule[idxConfProps.size()];

    	int i = 0;
        for (String name : idxConfProps.stringPropertyNames()) {
            ruleArray[i++] = new IndexRule(name, idxConfProps.getProperty(name));
        }
        // sort by longest-match
        Arrays.sort(ruleArray);       
        return Arrays.asList(ruleArray);
    }
    
    // Class holds one index rule
    private static class IndexRule implements Comparable<IndexRule> {
        String[] indexNames;
        Pattern ruleExpr;
        int score;
        
        public IndexRule(String rule, String indexNameList) {
        	// calculate the 'specificity' score for determining length of match
        	// essentially, each '.' adds specificity and each '*' reduces it
        	// e.g. dc.contributor.author (score 2) is more specific that dc.contributor.* (score 2-1=1)
        	score = matchDot.countIn(rule) - matchStar.countIn(rule);
        	ruleExpr = glob2regex(rule);
        	indexNames = indexNameList.split(",");
        }
        
        public boolean matches(String name) {
        	return ruleExpr.matcher(name).matches();
        }
        
        @Override
        public int compareTo(IndexRule rule) {
        	// natural ordering is longest -> shortest
        	int diff = rule.score - score;
        	// use expression length as tie-breaker
        	return (diff != 0) ? diff : rule.ruleExpr.pattern().length() - ruleExpr.pattern().length();
        }
    }
}
