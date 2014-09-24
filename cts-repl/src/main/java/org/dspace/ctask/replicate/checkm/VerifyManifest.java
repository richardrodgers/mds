/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */

package org.dspace.ctask.replicate.checkm;

import java.io.IOException;

import org.dspace.content.DSpaceObject;
import org.dspace.ctask.replicate.ReplicaManager;
import org.dspace.curate.AbstractCurationTask;
import org.dspace.curate.Curator;
import org.dspace.curate.Suspendable;

/**
 * VerifyManifest task will simply test for the presence of a manifest
 * of the object in the remote store. It succeeds if found, otherwise fails.
 * <P>
 * Manifests conform to the CDL Checkm v0.7 manifest format spec.
 * http://www.cdlib.org/uc3/docs/checkmspec.html
 * <P>
 * This task is "suspendable" when invoked from the UI.  This means that if
 * you run a verification from the UI, this task will return an immediate failure
 * once a single object fails the verification. However, when run from the Command-Line
 * this task will run to completion (i.e. even if an object fails it will continue
 * processing to completion).
 * 
 * @author richardrodgers
 * @see TransmitManifest
 */
@Suspendable(invoked=Curator.Invoked.INTERACTIVE)
public class VerifyManifest extends AbstractCurationTask {

    /**
     * Perform the 'Verify Manifest' task
     * @param dso the DSpace Object to be verified
     * @return integer which represents Curator return status
     * @throws IOException 
     */
    @Override
    public int perform(DSpaceObject dso) throws IOException {
        ReplicaManager repMan = ReplicaManager.instance();
        String objId = repMan.storageId(dso.getHandle(), TransmitManifest.MANIFEST_EXTENSION);
        boolean found = repMan.objectExists(repMan.manifestGroupName(), objId);
        String result = "Manifest for object: " + dso.getHandle() + " found: " + found;
        report(result);
        setResult(result);
        return found ? Curator.CURATE_SUCCESS : Curator.CURATE_FAIL;
    }
}
