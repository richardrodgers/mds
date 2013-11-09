/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.curate;

import java.io.IOException;
import java.sql.SQLException;

import org.dspace.authorize.AuthorizeException;
import org.dspace.content.DSpaceObject;
import org.dspace.core.Context;

/**
 * CurationTask describes a rather generic ability to perform an operation
 * upon a DSpace object.
 *
 * @author richardrodgers
 */
public interface CurationTask {
    /**
     * Initialize task - parameters inform the task of it's invoking curation
     * context. Since this context can provide services to the task, this
     * represents curation DI.
     * 
     * @param curation the Curation this task is bound to
     * @param taskId identifier task should use in invoking services
     * @throws IOException
     */
    void init(Curation curation, String taskId) throws IOException;

    /**
     * Perform the curation task upon passed DSO
     *
     * @param dso the DSpace object
     * @return status code
     * @throws AuthorizeException
     * @throws IOException
     * @throws SQLException
     */
    int perform(DSpaceObject dso) throws AuthorizeException, IOException, SQLException;

    /**
     * Perform the curation task for passed id
     * 
     * @param ctx DSpace context object
     * @param id persistent ID for DSpace object
     * @return status code
     * @throws AuthorizeException
     * @throws IOException
     * @throws SQLException
     */
    int perform(Context ctx, String id) throws AuthorizeException, IOException, SQLException;
}
