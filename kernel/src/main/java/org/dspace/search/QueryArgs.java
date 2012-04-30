/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.search;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;

import org.dspace.core.ConfigurationManager;
import org.dspace.core.Constants;
import org.dspace.sort.SortOption;

/**
 * Contains the arguments for a query. Fill it out and pass to the query engine
 */
public class QueryArgs
{
    // the query string
    private String query;

    // start and count defines a search 'cursor' or page
    // query will return 'count' hits beginning at offset 'start'
    private int start = 0; // default values

    private int pageSize = 10;

    private SortOption sortOption = null;

    private String sortOrder = SortOption.DESCENDING;

    /** number of metadata elements to display before truncating using "et al" */
    private int etAl = ConfigurationManager.getIntProperty("webui.itemlist.author-limit");
    
    // target index - initialized to default
    private String indexName = DSIndexer.DEFAULT_INDEX;

    /**
     * @return  the number of metadata fields at which to truncate with "et al"
     */
    public int getEtAl()
    {
        return etAl;
    }

    /**
     * set the number of metadata fields at which to truncate with "et al"
     *
     * @param etAl
     */
    public void setEtAl(int etAl)
    {
        this.etAl = etAl;
    }

    /**
     * set the query string
     * 
     * @param newQuery
     */
    public void setQuery(String newQuery)
    {
        query = newQuery;
    }

    /**
     * retrieve the query string
     * 
     * @return the current query string
     */
    public String getQuery()
    {
        return query;
    }

    /**
     * set the offset of the desired search results, beginning with 0 ; used to
     * page results (the default value is 0)
     * 
     * @param newStart
     *            index of first desired result
     */
    public void setStart(int newStart)
    {
        start = newStart;
    }

    /**
     * read the search's starting offset
     * 
     * @return current index of first desired result
     */
    public int getStart()
    {
        return start;
    }

    /**
     * set the count of hits to return; used to implement paged searching see
     * the initializer for the default value
     * 
     * @param newSize
     *            number of hits per page
     */
    public void setPageSize(int newSize)
    {
        pageSize = newSize;
    }

    /**
     * get the count of hits to return
     * 
     * @return number of results per page
     */
    public int getPageSize()
    {
        return pageSize;
    }

    public SortOption getSortOption()
    {
        return sortOption;
    }

    public void setSortOption(SortOption sortOption)
    {
        this.sortOption = sortOption;
    }

    public String getSortOrder()
    {
        return sortOrder;
    }

    public void setSortOrder(String sortOrder)
    {
        this.sortOrder = sortOrder;
    }
    
    /**
     * Returns the index target of this query
     * 
     */
    public String getTargetIndex() {
    	return indexName;
    }
    
    /**
     * Assigns the index target. 
     * NB: all queryArgs intialized to the 'default' index
     * 
     */
    public void setTargetIndex(String indexName) {
    	this.indexName = indexName;
    }
}
