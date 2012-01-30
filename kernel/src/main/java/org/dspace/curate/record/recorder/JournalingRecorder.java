/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.curate.record.recorder;

import java.io.BufferedOutputStream;
import java.io.Closeable;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.text.DateFormat;
import java.util.Date;

import org.dspace.core.ConfigurationManager;
import org.dspace.curate.record.Recorder;

/**
 * JournalingRecorder handles curation records by appending them,
 * with minimal formatting, to a simple, local, flat file.
 *
 * @author richardrodgers
 */
public class JournalingRecorder implements Recorder, Closeable
{
	private static DateFormat df = DateFormat.getDateInstance();
	
	private OutputStream out = null;
	private String location = null;
	
	@Override
	public void init() throws IOException
	{
		// locate the journal file
		location = ConfigurationManager.getProperty("curate", "taskJournal");
		out = new BufferedOutputStream(
			  new FileOutputStream(location, true));
	}
    
    /**
     * Handles the creation and disposition of the curation task message
     * @param id
     *        the id of the object upon whom task performed
     * @param eperson
     *        the eperson ID of task invoker
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
	@Override
    public void record(long timestamp, String id, String eperson, String taskName,
    		           String msgType, String msgValue, int status, String result)
	                   throws IOException
	{
		String fmtTime = df.format(new Date(timestamp));
		String ep = (eperson != null) ? eperson : "unknown eperson";
		String res = (result != null) ? result : "no result set";
		String buf = fmtTime + " " + id + " " + ep + " " + taskName + " " + msgType + " " + msgValue + " " + status + " " + res;
		out.write(buf.getBytes());
	}
	
	@Override
	public void close() throws IOException
	{
		if (out != null)
		{
			out.close();
		}
	}
}
