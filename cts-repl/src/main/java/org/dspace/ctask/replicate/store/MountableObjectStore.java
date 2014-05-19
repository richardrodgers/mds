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

/**
 * MountableObjectStore uses a mountable file system to manage replicas or other
 * content. As such, it is not intended to provide the level of assurance that
 * a remote, externally managed service can provide. Its primary use is in
 * testing and validating the replication service, not as a production replica
 * store. Note in particular that certain filesystem limits on number of files
 * in a directory may limit its use. It stores replicas as archive files.
 * Also note that MountableObjectStore differs from LocalObjectStore only in
 * that all objects are copied, rather than moved (renamed). This will result
 * in slower performance, but may be required when more complex storage
 * architectures (e.g. an NFS-mounted storage) are used.
 * 
 * @author richardrodgers
 */
public class MountableObjectStore extends LocalObjectStore {

    // need a no-arg constructor for config-based loading
    public MountableObjectStore() {}

    @Override
    public long transferObject(String group, Path file) throws IOException {
        // local transfer is a simple matter of copying the file,
        // we don't bother checking if replica is really new, since
        // local deletes/copies are cheap
        Path archFile = Paths.get(storeDir, group, file.getFileName().toString());
        Files.copy(file, archFile, StandardCopyOption.REPLACE_EXISTING);
        return Files.size(file);
    }
}
