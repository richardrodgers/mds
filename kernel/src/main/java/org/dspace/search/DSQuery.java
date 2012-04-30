/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.search;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.dspace.content.Collection;
import org.dspace.content.Community;
import org.dspace.core.ConfigurationManager;
import org.dspace.core.Constants;
import org.dspace.core.Context;
import org.dspace.core.LogManager;
import org.dspace.sort.SortOption;

// issues
// need to filter query string for security
// cmd line query needs to process args correctly (seems to split them up)
/**
 * DSQuery contains various static methods for performing queries on indices,
 * for collections and communities.
 *
 */
public class DSQuery {
    // Result types
    static final String ALL = "999";
    
    private static String operator = null;
    
    /** logger */
    private static Logger log = LoggerFactory.getLogger(DSQuery.class);
    
    static
    {        
        operator = ConfigurationManager.getProperty("search", "operator.default");   
    }

    /**
     * Do a query, returning a QueryResults object
     *
     * @param c  context
     * @param args query arguments in QueryArgs object
     * 
     * @return query results QueryResults
     */
    public static QueryResults doQuery(Context c, QueryArgs args) throws IOException {
    	
    	// extract the index to query and fire it off
    	IndexService service = DSIndexer.getService(args.getTargetIndex());
    	return service.doQuery(args);
    }

    static String checkEmptyQuery(String myquery)
    {
        if (myquery == null || myquery.equals("()") || myquery.equals(""))
        {
            myquery = "empty_query_string";
        }

        return myquery;
    }
    
    static String getOperator() {
    	return operator;
    }

    static String stripHandles(String myquery)
    {
        // Drop beginning pieces of full handle strings
        return myquery.replaceAll("^\\s*http://hdl\\.handle\\.net/", "")
                      .replaceAll("^\\s*hdl:", "");
    }

    static String stripAsterisk(String myquery)
    {
        // query strings (or words) beginning with "*" cause a null pointer error
        return myquery.replaceAll("^\\*", "")
                      .replaceAll("\\s\\*", " ")
                      .replaceAll("\\(\\*", "(")
                      .replaceAll(":\\*", ":");
    }

    /**
     * Do a query, restricted to a collection
     * 
     * @param c
     *            context
     * @param args
     *            query args
     * @param coll
     *            collection to restrict to
     * 
     * @return QueryResults same results as doQuery, restricted to a collection
     */
    public static QueryResults doQuery(Context c, QueryArgs args,
            Collection coll) throws IOException
    {
        String querystring = args.getQuery();

        querystring = checkEmptyQuery(querystring);

        String location = "l" + (coll.getID());

        String newquery = "+(" + querystring + ") +location:\"" + location + "\"";

        args.setQuery(newquery);

        return doQuery(c, args);
    }

    /**
     * Do a query, restricted to a community
     * 
     * @param c
     *            context
     * @param args
     *            query args
     * @param comm
     *            community to restrict to
     * 
     * @return QueryResults same results as doQuery, restricted to a collection
     */
    public static QueryResults doQuery(Context c, QueryArgs args, Community comm)
            throws IOException
    {
        String querystring = args.getQuery();

        querystring = checkEmptyQuery(querystring);

        String location = "m" + (comm.getID());

        String newquery = "+(" + querystring + ") +location:\"" + location + "\"";

        args.setQuery(newquery);

        return doQuery(c, args);
    }


    /**
     * Do a query, printing results to stdout largely for testing, but it is
     * useful
     */
    public static void doCMDLineQuery(String query) {
        System.out.println("Command line query: " + query);
        System.out.println("Only reporting default-sized results list");

        try
        {
            Context c = new Context();

            QueryArgs args = new QueryArgs();
            args.setQuery(query);

            QueryResults results = doQuery(c, args);

            Iterator i = results.getHitHandles().iterator();
            Iterator j = results.getHitTypes().iterator();

            while (i.hasNext())
            {
                String thisHandle = (String) i.next();
                Integer thisType = (Integer) j.next();
                String type = Constants.typeText[thisType];

                // also look up type
                System.out.println(type + "\t" + thisHandle);
            }
        }
        catch (Exception e)
        {
            System.out.println("Exception caught: " + e);
        }
    }

    /**
     * Close any IndexSearcher that is currently open.
     */
    public static synchronized void close()
    {
    	/*
        if (searcher != null)
        {
            try
            {
                searcher.close();
                searcher = null;
            }
            catch (IOException ioe)
            {
                log.error("DSQuery: Unable to close open IndexSearcher", ioe);
            }
        }
        */
    }
    
    public static void main(String[] args) {
        if (args.length > 0) {
            doCMDLineQuery(args[0]);
        }
    }
}
