/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */

package org.dspace.ctask.replicate;

import java.io.File;
import java.io.IOException;

import org.dspace.content.DSpaceObject;
import org.dspace.core.ConfigurationManager;
import org.dspace.core.Context;
import org.dspace.curate.AbstractCurationTask;
import org.dspace.curate.Curator;

/**
 * FetchAIP task will simply retrieve replica representations of the object
 * into the local staging area.
 * 
 * @author richardrodgers
 * @see TransmitAIP
 */

public class FetchAIP extends AbstractCurationTask
{
    private String archFmt = ConfigurationManager.getProperty("replicate", "packer.archfmt");
    
    private String baseFolder = ConfigurationManager.getProperty("replicate", "base.dir");

    // Group where all AIPs are stored
    private final String storeGroupName = ConfigurationManager.getProperty("replicate", "group.aip.name");
    
    /**
     * Perform the 'Fetch AIP' task
     * @param dso DSpace Object to perform on
     * @return integer which represents Curator return status
     * @throws IOException 
     */
    @Override
    public int perform(DSpaceObject dso) throws IOException
    {
        if(dso!=null)
        {
            //NOTE: we can get away with passing in a 'null' Context because
            // the context isn't actually used to fetch the AIP
            // (see below 'perform(ctx,id)' method)
            return perform(null, dso.getHandle());
        }
        else
        {
            String result = "DSpace Object not found!";
            report(result);
            setResult(result);
            return Curator.CURATE_FAIL;
        }
    }
    
    
    /**
     * Perform the 'Fetch AIP' task
     * @param ctx DSpace Context (this param is ignored for this task)
     * @param id ID of object whose AIP should be fetched
     * @return integer which represents Curator return status
     * @throws IOException 
     */
    @Override
    public int perform(Context ctx, String id) throws IOException
    {
        ReplicaManager repMan = ReplicaManager.instance();
        String objId = repMan.storageId(id, archFmt);
        File archive = repMan.fetchObject(storeGroupName, objId);
        boolean found = archive != null;
        String result = "AIP for object: " + id + " located : " + found + ".";
        if(found)
            result += " AIP file downloaded to '" 
                + baseFolder + "/" + storeGroupName + "/" + objId + "'";
        report(result);
        setResult(result);
        return found ? Curator.CURATE_SUCCESS : Curator.CURATE_FAIL;
    }
}
