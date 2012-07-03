/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.search;

import java.io.InputStream;
import java.io.IOException;

/**
 * Interface for indexing parsers that read streams
 * and produce maps of indexable values. Typical use-case
 * would be a bitstream that contains rich medatadata that
 * could be indexed.
 * 
 * @author richardrodgers
 */
public interface StreamParser {
	
	/**
	 * Initializes Parser
	 * 
	 * @param config configuration data
	 */
	void init(String config);
    
	/**
	 * Parses the stream for indexable values, which
	 * are added to the passed indexing task.
	 * 
	 * @param stream the input stream to parse
	 * @params task the indexing task to update
	 */
    void parse(InputStream stream, IndexingTask task) throws IOException;   
}
