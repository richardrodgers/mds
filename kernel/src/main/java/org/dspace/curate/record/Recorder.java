/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.curate.record;

import java.io.IOException;

/**
 * Recorder provides a means of publishing/recording a curation record
 * describing a task execution.
 *
 * @author richardrodgers
 */
public interface Recorder {
	
	/**
	 * Initializes the recorder for use
	 */
	void init() throws IOException;
       
    /**
     * Handles the creation and disposition of the curation task record.
     * 
     * @param timestamp
     * 		  millisecond time of message creation
     * @param id
     *        the id of the object upon which task performed
     * @param eperson
     *        the eperson ID of task invoker or null if unknown
     * @param taskName
     *        the logical name of the task
     * @param msgType
     *        the type of message
     * @param msgValue
     *        the specific value of the message
     * @param status
     *        status code of task invocation 
     * @param result
     *        result of task invocation or null if no result assigned
     */
    void record(long timestamp, String id, String eperson, String taskName,
    		    String msgType, String msgValue, int status, String result)
    		    throws IOException;
}
