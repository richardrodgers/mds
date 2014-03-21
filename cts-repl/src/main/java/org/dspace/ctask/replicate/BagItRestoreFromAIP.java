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
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.mit.lib.bagit.Bag;
import edu.mit.lib.bagit.Filler;
import edu.mit.lib.bagit.Loader;

import org.dspace.authorize.AuthorizeException;
import org.dspace.content.Collection;
import org.dspace.content.Community;
import org.dspace.content.DSpaceObject;
import org.dspace.content.InstallItem;
import org.dspace.content.Item;
import org.dspace.content.WorkspaceItem;
import org.dspace.core.ConfigurationManager;
import org.dspace.core.Context;
import org.dspace.curate.AbstractCurationTask;
import org.dspace.curate.Curator;
import org.dspace.curate.Distributive;
import org.dspace.curate.Mutative;
//import org.dspace.embargo.EmbargoManager;
import org.dspace.handle.HandleManager;
import org.dspace.pack.Packer;
import org.dspace.pack.PackerFactory;
import org.dspace.pack.bagit.CatalogPacker;

import static org.dspace.pack.PackerFactory.*;

/**
 * BagItRestoreFromAIP task performs essentially an 'undelete' on an object that
 * has been deleted from the repository, using the replica copy.
 * If the object is a container, it recovers all its children/members.
 *
 * @author richardrodgers
 * @see TransmitAIP
 */
@Distributive
@Mutative
public class BagItRestoreFromAIP extends AbstractCurationTask {

    private static Logger log = LoggerFactory.getLogger(BagItRestoreFromAIP.class);
    private final String archFmt = ConfigurationManager.getProperty("replicate", "packer.archfmt");

    // Group where all AIPs are stored
    private final String storeGroupName = ConfigurationManager.getProperty("replicate", "group.aip.name");
    
    // Group where all AIPs are temporarily moved when deleted
    private final String deleteGroupName = ConfigurationManager.getProperty("replicate", "group.delete.name");
    
    /**
     * Perform 'Recover From AIP' task on a particular object.
     * As you cannot recover an object that already exists, this method
     * always returns an exception. 
     * @param dso DSpace Object to recover
     * @return integer which represents Curator return status
     * @throws IOException 
     */
    @Override
    public int perform(DSpaceObject dso) throws AuthorizeException, IOException, SQLException {
        throw new IllegalStateException("Cannot recover if object exists");
    }

    /**
     * Perform 'Recover From AIP' task by retrieving an object package
     * of a particular ID (name) and restoring it to the current DSpace
     * Context.
     * @param ctx current DSpace context
     * @param id identifier of object to restore
     * @return integer which represents Curator return status
     * @throws IOException 
     */
    @Override
    public int perform(Context ctx, String id) throws AuthorizeException, IOException, SQLException {
        ReplicaManager repMan = ReplicaManager.instance();
        // first we locate the deletion catalog for this object
        String objId = repMan.storageId(id, archFmt);
        File catArchive = repMan.fetchObject(deleteGroupName, objId);
        int status = Curator.CURATE_FAIL;
        if (catArchive != null) {
            CatalogPacker cpack = new CatalogPacker(id);
            cpack.unpack(catArchive);
            // RLR TODO - remove filename collision next delete requires
            catArchive.delete();
            // recover root object itself, then any members
            recover(ctx, repMan, id);
            for (String mem : cpack.getMembers()) {
                recover(ctx, repMan, mem);
            }
            status = Curator.CURATE_SUCCESS;
        }
        return status;
    }

    /**
     * Recover an object from an ObjectStore based on its identifier
     * @param ctx current DSpace Context
     * @param repMan ReplicaManager (used to access ObjectStore)
     * @param id Identifier of object in ObjectStore
     * @throws IOException 
     */
    private void recover(Context ctx, ReplicaManager repMan, String id) throws AuthorizeException, IOException, SQLException {
        String objId = repMan.storageId(id, archFmt);
        File archive = repMan.fetchObject(storeGroupName, objId);
        if (archive != null) {
            Bag bag = new Loader(archive.toPath()).load();
            Properties props = new Properties();
            props.load(bag.payloadStream(OBJFILE));
            String type = props.getProperty(OBJECT_TYPE);
            String ownerId = props.getProperty(OWNER_ID);
            if ("item".equals(type)) {
                recoverItem(ctx, archive, id, props);
            } else if ("collection".equals(type)) {
                recoverCollection(ctx, archive, id, ownerId);
            } else if ("community".equals(type)) {
                recoverCommunity(ctx, archive, id, ownerId);
            }
            // discard bag when done
            //bag.empty();
        }
    }

    /**
     * Recover a DSpace Item from a particular AIP package file
     * @param ctx current DSpace context
     * @param archive AIP package file 
     * @param objId identifier of object we are restoring
     * @param props properties which control how item is restored
     * @throws IOException 
     */
    private void recoverItem(Context ctx, File archive, String objId, Properties props) throws AuthorizeException, IOException, SQLException {
        String collId = props.getProperty(OWNER_ID);
        Collection coll = (Collection)HandleManager.resolveToObject(ctx, collId);
        WorkspaceItem wi = WorkspaceItem.create(ctx, coll, false);
        Packer packer = PackerFactory.instance(wi.getItem());
        // stuff bag contents into item
        packer.unpack(archive);
        // Install item
        Item item = InstallItem.restoreItem(ctx, wi, objId);
        String colls = props.getProperty(OTHER_IDS);
        if (colls != null) {
            // reset linked collections
            for (String link : colls.split(",")) {
                Collection linkC = (Collection)HandleManager.resolveToObject(ctx, link);
                linkC.addItem(item);
            }
        }
        // now post-process: withdrawals, embargoes, etc
        if (props.getProperty(WITHDRAWN) != null) {
            item.withdraw();
        }
        // RLR FIXME    EmbargoManager.setEmbargo(ctx, item); - should now be handled via LCH
    }

    /**
     * Recover a DSpace Collection from a particular AIP package file
     * @param ctx current DSpace context
     * @param archive AIP package file 
     * @param collId identifier of collection we are restoring
     * @param commId identifier of parent community for this collection
     * @throws IOException 
     */
    private void recoverCollection(Context ctx, File archive, String collId, String commId) throws AuthorizeException, IOException, SQLException { 
        Collection coll = null;
        if (commId != null) {
            Community pcomm = (Community)HandleManager.resolveToObject(ctx, commId);
            coll = pcomm.createCollection(collId);
        } else {
            log.error("Collection '" + collId + "' lacks parent community");
        }
        // update with AIP data
        Packer packer = PackerFactory.instance(coll);
        packer.unpack(archive);
    }

    /**
     * Recover a DSpace Community from a particular AIP package file
     * @param ctx current DSpace context
     * @param archive AIP package file 
     * @param commId identifier of community we are restoring
     * @param parentId identifier of parent community (if any) for community
     * @throws IOException 
     */
    private void recoverCommunity(Context ctx, File archive, String commId, String parentId) throws AuthorizeException, IOException, SQLException { 
        // if not top-level, have parent create it
        Community comm = null;
        if (parentId != null) {
            Community pcomm = (Community)HandleManager.resolveToObject(ctx, parentId);
            comm = pcomm.createSubcommunity(commId);
        } else {
            comm = Community.create(null, ctx, commId);
        }
        // update with AIP data
        Packer packer = PackerFactory.instance(comm);
        packer.unpack(archive);
    }

}
