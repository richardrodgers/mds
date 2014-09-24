/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.ctask.replicate;

import java.sql.SQLException;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.dspace.content.DSpaceObject;
import org.dspace.core.ConfigurationManager;
import org.dspace.core.Constants;
import org.dspace.core.Context;
import org.dspace.mxres.ResourceMap;
import org.dspace.pack.PackingSpec;
import org.dspace.handle.HandleManager;

import static org.dspace.ctask.replicate.Odometer.*;

/**
 * Singleton access point for communicating with replication access providers.
 * ReplicaManager adds a thin accounting or bookkeeping layer, recording
 * activity with the storage provider.
 *
 * @author richardrodgers
 */
public class ReplicaManager {

    private final Logger log = LoggerFactory.getLogger(ReplicaManager.class);
    // singleton instance
    private static ReplicaManager instance = null;
    // the replica provider
    private ObjectStore objStore = null;
    // base directory for replication activities
    private final String repDir = ConfigurationManager.getProperty("replicate", "base.dir");
    // an odometer for recording activity
    private Odometer odometer = null;
    // lock for updating odometer
    private final Object odoLock = new Object();
    // Primary store group name
    private final String storeGroupName = ConfigurationManager.getProperty("replicate", "group.aip.name");
    // Delete store group name
    private final String deleteGroupName = ConfigurationManager.getProperty("replicate", "group.delete.name");
    // Manifest store group name - optional
    private final String manifestGroupName = ConfigurationManager.getProperty("replicate", "group.manifest.name");
     // scope in which packing spec defined
    private final String scope = ConfigurationManager.getProperty("replicate", "packingspec.scope");
    // Separating character between Type prefix and object identifier, used when packages are named with a Type prefix
    private final String typePrefixSeparator = "@";


    private ReplicaManager() throws IOException {
        objStore = (ObjectStore)ConfigurationManager.getInstance("replicate", "store.impl");
        if (objStore == null) {
            log.error("No ObjectStore configured in 'replicate.cfg'!");
            throw new IOException("No ObjectStore configured in 'replicate.cfg'!");
        }
        
        objStore.init();
        
        // create directory structures
        new File(repDir).mkdirs();
        // load our odometer - writeable copy
        try {
            odometer = new Odometer(false);
        } catch (IOException ioE) {
            //just log a warning
            log.warn("Unable to read odometer: ", ioE);
        }
    }

    public static synchronized ReplicaManager instance() throws IOException {
        if (instance == null) {
            instance = new ReplicaManager();
        }
        return instance;
    }

    public PackingSpec packingSpec(DSpaceObject dso) throws SQLException {
        try (Context context = new Context()) {
            return (PackingSpec)new ResourceMap(PackingSpec.class, context).findResource(dso, scope);
        } 
    }

    public String scope() {
        return scope;
    }

    public String storeGroupName() {
        return storeGroupName;
    }

    public String deleteGroupName() {
        return deleteGroupName;
    }

    public String manifestGroupName() {
        return manifestGroupName;
    }

    public Path stage(String id) {
        return stage(storeGroupName, id);
    }
    
    public Path stage(String group, String id) {
        // ensure path exists
        File stageDir = new File(repDir + File.separator + group);
        if (! stageDir.isDirectory()) {
            stageDir.mkdirs();
        }
        return new File(stageDir, storageId(id, null)).toPath(); 
    }
    
    public String getDefaultFormat(Context ctx) throws SQLException {
        // this method presupposes that the resource rule for this (scope, spec) has a default value
        // since it is passing in a null DSO
        PackingSpec spec = (PackingSpec)new ResourceMap(PackingSpec.class, ctx).findResource(null, scope);
        return (spec != null) ? spec.getFormat() : "zip";
    }
    
    /**
     * Determine the Identifier of an object once it is placed 
     * in storage. This method ensures any special characters are 
     * escaped. It also ensures all objects are named in a similar
     * manner once they are in a given store (so that they can similarly
     * be retrieved from storage using this same 'storageId').
     * 
     * @param objId - original object id (canonical ID)
     * @param fileExtension - file extension, if any (may be null)
     * @return reformatted storage ID for this object (including file extension)
     */
    public String storageId(String objId, String fileExtension)
    {
        // canonical handle notation bedevils file system semantics
        String storageId = objId.replaceAll("/", "-");
        
        // add appropriate file extension, if needed
        if(fileExtension!=null)
            storageId = storageId + "." + fileExtension;
        
        // If 'packer.typeprefix' setting is 'true', 
        // then prefix the storageID with the DSpace Type (if it doesn't already have a prefix)
        if (ConfigurationManager.getBooleanProperty("replicate", "packer.typeprefix", true) && 
           !storageId.contains(typePrefixSeparator)) {    
            String typePrefix = null;
        
            try  {    
                Context ctx = new Context();
                //Get object associated with this handle
                DSpaceObject dso = HandleManager.resolveToObject(ctx, objId);
                ctx.complete();

                //typePrefix format = 'TYPE@'
                if(dso!=null)
                    typePrefix = Constants.typeText[dso.getType()] + typePrefixSeparator;
            }
            catch(SQLException sqle)
            {
                //do nothing, just ignore -- we'll handle this in a moment
            }
            
            // If we were unable to determine a type prefix, then this must mean the object
            // no longer exists in DSpace!  Let's see if we can find it in storage!
            if(typePrefix==null) {
                try
                {
                    //Currently we need to try and lookup the object in storage
                    //Hopefully, there will be an easier way to do this in the future
                    
                    //see if this object exists in main storage group
                    typePrefix = findTypePrefix(storeGroupName, storageId);
                    if(typePrefix==null && deleteGroupName!=null) //if not found, check deletion group as well
                        typePrefix = findTypePrefix(deleteGroupName, storageId);
                }
                catch(IOException io)
                {
                    //do nothing, just ignore
                }
            }    
            
            //if we found a typePrefix, prepend it on storageId
            if(typePrefix!=null)
                storageId = typePrefix + storageId;
        }
        
       
        // Return final storage ID
        return storageId;
    }
    
    /**
     * Convert a Storage ID back into a Canonical Identifier
     * (opposite of 'storageId()' method).
     * @param storageId the given object's storage ID
     * @return the objects canonical identifier
     */
    public String canonicalId(String storageId) {
        //If this 'storageId' includes a TYPE prefix (see 'storageId()' method),
        // then remove it, before returning the reformatted ID.
        if(storageId.contains(typePrefixSeparator))
            storageId = storageId.substring(storageId.indexOf(typePrefixSeparator)+1);
        
        //If this 'storageId' includes a file extension suffix, also remove it.
        if(storageId.contains("."))
            storageId = storageId.substring(0, storageId.indexOf("."));
        
        //Finally revert all dashes back to slashes (to create the original canonical ID)
        return storageId.replaceAll("-", "/");
    }

    // Replica store-backed methods

    public Path fetchObject(String objId) throws IOException {
        return fetchObject(storeGroupName, objId);
    }

    public Path fetchManifest(String objId) throws IOException {
        return fetchObject(manifestGroupName, objId);
    }

    public Path fetchObject(String group, String objId) throws IOException {
        //String repId = safeId(id) + "." + arFmt;
        Path file = stage(group, objId);
        long size = objStore.fetchObject(group, objId, file);
        if (size > 0L)
        {
            synchronized (odoLock)
            {
                odometer.adjustProperty(DOWNLOADED, size);
                odometer.save();
            }
        }
       
        return Files.exists(file) ? file : null;
    }

    public void transferObject(Path file) throws IOException {
        transferObject(storeGroupName, file);
    }

    public void transferManifest(Path file) throws IOException {
        transferObject(manifestGroupName, file);
    }
    
    public void transferObject(String group, Path file) throws IOException {
        String psStr = objStore.objectAttribute(group, file.getFileName().toString(), "sizebytes");
        long prevSize = psStr != null ? Long.valueOf(psStr) : 0L;
        long size = objStore.transferObject(group, file);
        if (size > 0L) {
            synchronized (odoLock) {
                odometer.adjustProperty(UPLOADED, size);
                // this may be an update - not a new object
                odometer.adjustProperty(SIZE, size - prevSize);
                if (prevSize == 0L) {
                    odometer.adjustProperty(COUNT, 1L);
                }
                odometer.save();
            }
        }       
    }
    
    public boolean objectExists(String group, String objId) throws IOException {
        return objStore.objectExists(group, objId);
    }

    public String objectAttribute(String group, String objId, String attrName) throws IOException {
        return objStore.objectAttribute(group, objId, attrName);
    }

    public void removeObject(String group, String objId) throws IOException {
        long size = objStore.removeObject(group, objId);
        if (size > 0L) {
            synchronized (odoLock) {
                odometer.adjustProperty(SIZE, -size);
                odometer.adjustProperty(COUNT, -1L);
                odometer.save();
            }
        }
    }
    
    public boolean moveObject(String srcGroup, String destGroup, String objId) throws IOException {
        long size = objStore.moveObject(srcGroup, destGroup, objId);
        
        // NOTE: no need to adjust the odometer. In this case we haven't 
        // actually uploaded or downloaded any content. 
        if (size > 0L)
            return true;
        else
            return false;
    }
    
    /**
     * This method is only called if we cannot determine an object's type prefix
     * via DSpace (i.e. the object no longer exists in DSpace). In this case,
     * we'll perform some basic searching of the given object store group to see
     * if we can find an object with this ID that has a type prefix.
     * 
     * @param group store group name to search
     * @param baseId base object id we are looking for (without type prefix)
     * @return Type prefix if a matching object is located successfully. Null otherwise.
     */
    private String findTypePrefix(String group, String baseId) throws IOException
    {
        boolean exists = false;
        
        // This next part may look a bit like a hack, but it's actually safer than
        // it seems. Essentially, we are going to try to "guess" what the Type Prefix
        // may be, and see if we can find an object with that name in our object Store.
        // The reason this is still "safe" is that the "objId" should be unique with or without
        // the Type prefix. Even if it wasn't unique, DSpace HandleManager has checks in place 
        // to ensure we can never restore an object of a different Type to a Handle that was 
        // used previously (e.g. cannot restore an Item with a handle that was previously used by a Collection)
        
        // NOTE: If DSpace ever provided a way to lookup Object type for an unbound handle, then
        // we may no longer need to guess which type this object may have been.
        // ALTERNATIVELY: If DuraCloud & other stores provide a way to search by file properties, we could change
        // our store plugins to always save the object handle as a property & retrieve files via that property.

        //Most objects are Items, so lets see if this object can be found with an Item Type prefix
        String typePrefix = Constants.typeText[Constants.ITEM] + typePrefixSeparator;
        exists = objStore.objectExists(group, typePrefix + baseId);

        if(!exists)
        {
            //Ok, our second guess will be that this used to be a Collection
            typePrefix = Constants.typeText[Constants.COLLECTION] + typePrefixSeparator;
            exists = objStore.objectExists(group, typePrefix + baseId);
        }

        if(!exists)
        {
            //Final guess: maybe this used to be a Community?
            typePrefix = Constants.typeText[Constants.COMMUNITY] + typePrefixSeparator;
            exists = objStore.objectExists(group, typePrefix + baseId);
        }    
        
        // That's it. We're done guessing. If we still couldn't find this object, 
        // it obviously doesn't exist in our object Store.
        if(exists)
            return typePrefix;
        else
            return null;
    }
}
