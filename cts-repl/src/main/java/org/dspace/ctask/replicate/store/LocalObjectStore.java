/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.ctask.replicate.store;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

//import com.google.common.io.Files;
import com.google.common.hash.Hashing;

import org.dspace.core.ConfigurationManager;
import org.dspace.ctask.replicate.ObjectStore;

/**
 * LocalObjectStore uses the local file system to manage replicas or other
 * content. As such, it is not intended to provide the level of assurance that
 * a remote, externally managed service can provide. It's primary use is in
 * testing and validating the replication service, not as a production replica
 * store. Note in particular that certain filesystem limits on number of files
 * in a directory may limit its use. It stores replicas as archive files.
 * Also note that for efficiency, this store assumes that File.renameTo will
 * succeed, and renames are preferred to copies when possible. Where this
 * assumption is not valid (e.g. with an NFS-mounted store), use the
 * MountableObjectStore class instead.
 * 
 * @author richardrodgers
 */
public class LocalObjectStore implements ObjectStore {

    // where replicas are kept
    protected String storeDir = null;
    
    // need no-arg constructor for PluginManager
    public LocalObjectStore() {}

    @Override
    public void init() throws IOException {
        storeDir = ConfigurationManager.getProperty("replicate", "store.dir");
        Path storePath = Paths.get(storeDir);
        if (Files.notExists(storePath)) {
            Files.createDirectories(storePath);
        }
    }

    @Override
    public long fetchObject(String group, String id, Path file) throws IOException {
        // locate archive and copy to file
        long size = 0L;
        Path archFile = Paths.get(storeDir, group, id);
        if (Files.exists(archFile)) {
            size = Files.size(archFile);
            Files.copy(archFile, file);
        }
        return size;
    }

    @Override
    public boolean objectExists(String group, String id) {
        // do we have a copy in our managed area?
        return Files.exists(Paths.get(storeDir, group, id));
    }

    @Override
    public long removeObject(String group, String id) throws IOException {
        // remove file if present
        long size = 0L;
        Path remFile = Paths.get(storeDir, group, id);
        if (Files.exists(remFile)) {
            size = Files.size(remFile);
            Files.delete(remFile);
        }
        return size;
    }

    @Override
    public long transferObject(String group, Path file) throws IOException {
        // local transfer is a simple matter of renaming the file,
        // we don't bother checking if replica is really new, since
        // local deletes/copies are cheap
        Path archDir = Paths.get(storeDir, group);
        if (! Files.isDirectory(archDir))  {
            Files.createDirectories(archDir);
        }
        Path archFile = archDir.resolve(file.getFileName().toString());
        try {
            Files.move(file, archFile, StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception e) {
            throw new UnsupportedOperationException("Store does not support move");
        }
        return Files.size(archFile);
    }

    @Override
    public String objectAttribute(String group, String id, String attrName) throws IOException {
        Path archFile = Paths.get(storeDir, group, id);
        if ("checksum".equals(attrName)) {
            return com.google.common.io.Files.hash(archFile.toFile(), Hashing.md5()).toString();
            //return Utils.checksum(archFile, "MD5");
        } else if ("sizebytes".equals(attrName)) {
            return String.valueOf(Files.size(archFile));
        } else if ("modified".equals(attrName)) {
            return String.valueOf(Files.getLastModifiedTime(archFile).toMillis());
        }
        return null;
    }
    
    @Override
    public long moveObject(String srcGroup, String destGroup, String id) throws IOException {
        long size = 0L;
        //Find the file
        Path file = Paths.get(storeDir, srcGroup, id);
        if (Files.exists(file)) {
            //If file is found, just transfer it to destination,
            // as transferObject() just does a file rename
            size = transferObject(destGroup, file);
        }
        return size;
    }
}
