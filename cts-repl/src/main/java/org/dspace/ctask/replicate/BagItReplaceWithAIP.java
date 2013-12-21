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
import java.sql.SQLException;

import org.dspace.authorize.AuthorizeException;
import org.dspace.content.Bundle;
import org.dspace.content.Collection;
import org.dspace.content.Community;
import org.dspace.content.DSpaceObject;
import org.dspace.content.Item;
import org.dspace.content.MDValue;
import org.dspace.core.ConfigurationManager;
import org.dspace.core.Constants;
import org.dspace.curate.AbstractCurationTask;
import org.dspace.curate.Curator;
import org.dspace.curate.Mutative;
import org.dspace.pack.Packer;
import org.dspace.pack.PackerFactory;

/**
 * BagItReplaceWithAIP task will instate the replica representation of the object in
 * place of the current (repository) one.
 * 
 * @author richardrodgers
 * @see TransmitAIP
 */
@Mutative
public class BagItReplaceWithAIP extends AbstractCurationTask {

    private String archFmt = ConfigurationManager.getProperty("replicate", "packer.archfmt");

    // Group where all AIPs are stored
    private final String storeGroupName = ConfigurationManager.getProperty("replicate", "group.aip.name");
    
    /**
     * Perform the 'Replace with AIP' task.
     * <P>
     * Actually overwrite any existing object data in the repository with
     * whatever information is contained in the AIP.
     * @param dso the DSpace object to replace
     * @return integer which represents Curator return status
     * @throws IOException 
     */
    @Override
    public int perform(DSpaceObject dso) throws AuthorizeException, IOException, SQLException { 
        ReplicaManager repMan = ReplicaManager.instance();
        // overwrite with AIP data
        Packer packer = PackerFactory.instance(dso);
        int status = Curator.CURATE_FAIL;
        String result = null;
        String objId = repMan.storageId(dso.getHandle(), archFmt);
        File archive = repMan.fetchObject(storeGroupName, objId);
        if (archive != null)  {
            // clear object where necessary
            if (dso.getType() == Constants.ITEM) {
                Item item = (Item)dso;
                item.clearMetadata(MDValue.ANY, MDValue.ANY, MDValue.ANY, MDValue.ANY);
                for (Bundle bundle : item.getBundles()) {
                    item.removeBundle(bundle);
                }   
            }
            packer.unpack(archive);
            // now update the dso
            int type = dso.getType();
            if (type == Constants.ITEM) {
                ((Item)dso).update();
            } else if (type == Constants.COLLECTION) {
                ((Collection)dso).update();
            } else if (type == Constants.COMMUNITY) {
                ((Community)dso).update();
            }
            status = Curator.CURATE_SUCCESS;
            result = "Object: " + dso.getHandle() + " replaced from AIP";
        } else {
            result = "Failed to replace Object. AIP could not be found in Replica Store.";
        }
        report(result);
        setResult(result);
        return status; 
    }
}
