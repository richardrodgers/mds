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
import java.nio.file.Path;
import java.sql.SQLException;

import org.dspace.authorize.AuthorizeException;
import org.dspace.content.BoundedIterator;
import org.dspace.content.Collection;
import org.dspace.content.Community;
import org.dspace.content.DSpaceObject;
import org.dspace.content.Item;
import org.dspace.content.Site;
import org.dspace.core.Context;
import org.dspace.curate.AbstractCurationTask;
import org.dspace.curate.Curator;
import org.dspace.curate.Distributive;

/**
 * RemoveAIP task will remove requested objects from the replica store. If the
 * object is a container, all its children (members) will also be removed.
 * 
 * @author richardrodgers
 * @see TransmitAIP
 */
@Distributive
public class RemoveAIP extends AbstractCurationTask {
    
    /**
     * Removes replicas of passed object from the replica store.
     * If a container, removes all the member replicas, in addition
     * to the replica of the container object. No change is made to
     * the DSPace object itself.
     * 
     * @param dso the DSpace object
     * @return integer which represents Curator return status
     * @throws IOException
     */
    @Override
    public int perform(DSpaceObject dso) throws AuthorizeException, IOException, SQLException {
        ReplicaManager repMan = ReplicaManager.instance();
        remove(repMan, dso);
        setResult("AIP for '" + dso.getHandle() + "' has been removed");
        return Curator.CURATE_SUCCESS;
    }

    /**
     * Remove replica(s) of the passed in DSpace object from a particular
     * replica ObjectStore.
     * @param repMan ReplicaManager (used to access ObjectStore)
     * @param dso the DSpace object whose replicas we will remove
     * @throws IOException 
     */
    private void remove(ReplicaManager repMan, DSpaceObject dso) throws IOException, SQLException {
        //Remove object from AIP storage
        // NB: a bit of a cheat here, using default format, rather than dso-specific, but in practice
        // it will always be OK
        String archFmt = null;
        try (Context ctx = new Context()) {
            archFmt = repMan.getDefaultFormat(ctx);
        }
        String objId = repMan.storageId(dso.getHandle(), archFmt);
        repMan.removeObject(repMan.storeGroupName(), objId);
        report("Removing AIP for: " + objId);
        
        //If it is a Collection, also remove all Items from AIP storage
        if (dso instanceof Collection) {
            BoundedIterator<Item> iter = ((Collection)dso).getItems();
            while (iter.hasNext()) {
                remove(repMan, iter.next());
            }
           
        } // else if it a Community, also remove all sub-communities, collections (and items) from AIP storage 
        else if (dso instanceof Community) {
            Community comm = (Community)dso;
            BoundedIterator<Community> scIter = comm.getSubcommunities();
            while (scIter.hasNext()) {
                remove(repMan, scIter.next());
            }
            BoundedIterator<Collection> colIter = comm.getCollections();
            while (colIter.hasNext()) {
                remove(repMan, colIter.next());
            }
        } //else if it is a Site object, remove all top-level communities (and everything else) from AIP storage
        else if (dso instanceof Site) {
            BoundedIterator<Community> topIter = Community.findAllTop(Curator.curationContext());
             while (topIter.hasNext()) {
                remove(repMan, topIter.next());
            }
        }
    }

    /**
     * Removes replicas of passed id from the replica store. This can act in
     * one of two ways: either there is an existing DSpace Object with
     * this id, in which case it behaves like the previous method, or there
     * is no DSpace Object, in which case we assume that the object has been
     * deleted. In this case, the replica store is purged of the deleted
     * object, or objects, if the id is (was) a container.
     *
     * @param ctx current DSpace Context
     * @param id Identifier of the object to be removed.
     * @return integer which represents Curator return status
     * @throws IOException
     */
    @Override
    public int perform(Context ctx, String id) throws AuthorizeException, IOException, SQLException {
        ReplicaManager repMan = ReplicaManager.instance();
        DSpaceObject dso = dereference(ctx, id);
        if (dso != null) {
            return perform(dso);
        }
        // treat as a deletion GC
        String archFmt =  repMan.getDefaultFormat(ctx);
        String objId = repMan.storageId(id, archFmt);
        int status = Curator.CURATE_FAIL;
        Path catFile = repMan.fetchObject(repMan.deleteGroupName(), objId);
        if (catFile != null) {
            BagItCatalog cpack = new BagItCatalog(id);
            cpack.unpack(catFile);
            // remove the object, then all members, last of all the deletion catalog
            repMan.removeObject(repMan.storeGroupName(), objId);
            for (String mem : cpack.getMembers()) {
                String memId = repMan.storageId(mem, archFmt);
                repMan.removeObject(repMan.storeGroupName(), memId);
            }
            repMan.removeObject(repMan.deleteGroupName(), objId);
            status = Curator.CURATE_SUCCESS;
        }
        return status;
    }
}
