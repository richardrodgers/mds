/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */

package org.dspace.ctask.replicate.checkm;

import java.io.IOException;
import java.nio.file.Path;

import org.dspace.content.DSpaceObject;
import org.dspace.ctask.replicate.ReplicaManager;
import org.dspace.curate.AbstractCurationTask;
import org.dspace.curate.Curator;

/**
 * FetchManifest task will simply retrieve a manifest representation of the object
 * into the local staging area
 * <p>
 * Manifests conform to the CDL Checkm v0.7 manifest format spec.
 * http://www.cdlib.org/uc3/docs/checkmspec.html
 * 
 * @author richardrodgers
 * @see TransmitManifest
 */

public class FetchManifest extends AbstractCurationTask {
    
    /**
     * Perform 'Fetch Manifest' task
     * @param dso DSpace Object to perform on
     * @return integer which represents Curator return status
     * @throws IOException 
     */
    @Override
    public int perform(DSpaceObject dso) throws IOException {
        ReplicaManager repMan = ReplicaManager.instance();
        String objId = repMan.storageId(dso.getHandle(), TransmitManifest.MANIFEST_EXTENSION);
        Path archive = repMan.fetchManifest(objId);
        boolean found = archive != null;
        setResult("Manifest for object: " + dso.getHandle() + " found: " + found);
        return found ? Curator.CURATE_SUCCESS : Curator.CURATE_FAIL;
    }
}
