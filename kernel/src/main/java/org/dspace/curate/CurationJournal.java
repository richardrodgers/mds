/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.curate;

import java.io.IOException;

import org.dspace.authorize.AuthorizeException;
import org.dspace.core.Context;

/**
 * CurationJournal describes a service interface to capture task execution
 * data to a persistent store.  Entries represent curation task 
 * executions and their outcomes.
 *
 * @author richardrodgers
 */
public interface CurationJournal {

    /**
     * Writes a single entry to the journal.
     * 
     * @param timestamp
     *        when the curation occurred
     * @param entry
     *        the task entry
     * @throws IOException
     */
    void write(Context context, long timestamp, String task, String objectId, int status, String result) throws AuthorizeException, IOException;
}
