/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.ctask.general;

import java.io.IOException;
import java.sql.SQLException;

import org.dspace.authorize.AuthorizeException;
import org.dspace.content.DSpaceObject;
import org.dspace.content.Item;
import org.dspace.core.Context;
import org.dspace.core.Constants;
import org.dspace.curate.AbstractCurationTask;
import org.dspace.curate.Curator;
import org.dspace.curate.Mutative;

/**
 * Withdraw is a task that withdraws an item in archive. 
 * If item is already withdrawn, it is ignored, so task is idempotent.
 *
 * @author richardrodgers
 */
@Mutative
public class Withdraw extends AbstractCurationTask {
    
    /**
     * Perform the curation task upon passed DSO
     * Withdraws an item that is in archive
     *
     * @param dso the DSpace object
     * @throws AuthorizeException
     * @throws IOException
     * @throws SQLException
     */
    @Override
    public int perform(DSpaceObject dso) throws AuthorizeException, IOException, SQLException {
        if (dso.getType() == Constants.ITEM) {
            Item item = (Item)dso;
            if (! item.isWithdrawn()) {
                setResult("Withdrawing");
                item.withdraw();
            } else {
                setResult("Already withdrawn");
            }
            return Curator.CURATE_SUCCESS;
        }
        return Curator.CURATE_SKIP;
    }
}
