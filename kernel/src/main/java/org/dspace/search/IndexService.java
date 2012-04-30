/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.search;

import java.io.IOException;
import java.util.Date;

/**
 * Interface for indexing and querying structured content.
 * 
 * @author richardrodgers
 */
public interface IndexService {
	
	/**
	 * Initializes Index service
	 * 
	 * @param config configuration data
	 */
	void init(String config);
    
	/**
	 * Performs an indexing operation
	 * 
	 * @param task indexing task to perform
	 */
    void doTask(IndexingTask task) throws IOException;
    
    /**
     * Returns index currency for a Document
     * 
     * @param documentKey the document identifier
     * @param lastModified the date when this document was lst modified
     * @return true if index older than modification date, else false
     */
    boolean isDocumentStale(String documentKey, Date lastmodified) throws IOException;
    
    /**
     * Performs a query on an index.
     * 
     * @param args the query arguments
     * @return results a QueryResults object
     */
    QueryResults doQuery(QueryArgs args) throws IOException;

}
