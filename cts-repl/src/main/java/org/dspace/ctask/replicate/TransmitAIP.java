/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */

package org.dspace.ctask.replicate;

import java.nio.file.Files;
import java.nio.file.Path;
import java.io.IOException;
import java.sql.SQLException;

import org.dspace.authorize.AuthorizeException;
import org.dspace.content.DSpaceObject;
import org.dspace.curate.AbstractCurationTask;
import org.dspace.curate.Curator;
import org.dspace.curate.Suspendable;
import org.dspace.pack.Packager;

/**
 * TransmitAIP task creates an AIP suitable for replication, and forwards it
 * to the replication system for transmission (upload).
 * <p>
 * The type of AIP produced is based on the packing spec resource mapped
 * to the object.
 * <p>
 * This task is "suspendable" when invoked from the UI. If a single AIP fails
 * to be generated & transmitted to storage, we should inform the user ASAP.
 * We wouldn't want them to assume everything was transferred successfully, 
 * if there were actually underlying errors.
 * <p>
 * Note that this task has a companion task called TransmitSingleAIP which
 * ensures that no child/member objects are transmitted.
 * 
 * @author richardrodgers
 * @see ReplicaManager
 * @see TransmitSingleAIP
 */
@Suspendable(invoked=Curator.Invoked.INTERACTIVE)
public class TransmitAIP extends AbstractCurationTask {
    
    /**
     * Perform 'Transmit AIP' task
     * <p>
     * Actually generates the AIP and transmits it to the replica ObjectStore
     *
     * @param dso DSpace Object to perform on
     * @return integer which represents Curator return status
     * @throws AuthorizeException
     * @throws IOException  
     * @throws SQLException 
     */
    @Override
    public int perform(DSpaceObject dso) throws AuthorizeException, IOException, SQLException {

        ReplicaManager repMan = ReplicaManager.instance();
        Path archive = repMan.stage(dso.getHandle());
        repMan.transferObject(Packager.toPackage(dso, repMan.scope(), archive));
        setResult("Created AIP: '" + archive.getFileName().toString() + 
                    "' size: " + Files.size(archive));
        return Curator.CURATE_SUCCESS;
    }
}
