/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */

package org.dspace.storage.bitstore.impl;

//import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.dspace.core.Utils;
import org.dspace.storage.bitstore.BitStore;

/**
 * Native DSpace (or "Directory Scatter" if you prefer) asset store.
 * Implements a directory 'scatter' algorithm to avoid OS limits on
 * files per directory.
 * 
 * @author Peter Breton, Robert Tansley, Richard Rodgers
 */

public class DSBitStore implements BitStore
{
    /** log4j log */
    private static Logger log = LoggerFactory.getLogger(DSBitStore.class);
    
    // These settings control the way an identifier is hashed into
    // directory and file names
    //
    // With digitsPerLevel 2 and directoryLevels 3, an identifier
    // like 12345678901234567890 turns into the relative name
    // /12/34/56/12345678901234567890.
    //
    // You should not change these settings if you have data in the
    // asset store, as the BitstreamStorageManager will be unable
    // to find your existing data.
    private static final int digitsPerLevel = 2;

    private static final int directoryLevels = 3;
    
    // Checksum algorithm
    private static final String CSA = "MD5";
    
    /** the asset directory */
    private Path baseDir = null;

    public DSBitStore() { }

    /**
     * Initialize the asset store
     * 
     * @param config
     *        String used to characterize configuration - the name
     *        of the directory root of the asset store
     */
    //@Override
    public void init(String config) {
        // the config string contains just the asset store directory path
        baseDir = Paths.get(config);
    }

    /**
     * Return an identifier unique to this asset store instnace
     * 
     * @return a unique ID
     */
    //@Override
    public String generateId() {
        return Utils.generateKey();
    }

    /**
     * Retrieve the bits for the asset with ID. If the asset does not
     * exist, returns null.
     * 
     * @param id
     *            The ID of the asset to retrieve
     * @exception IOException
     *                If a problem occurs while retrieving the bits
     * 
     * @return The stream of bits, or null
     */
    //@Override
    public InputStream get(String id) throws IOException {
        return Files.newInputStream(getFile(id));
    }

    /**
     * Store a stream of bits.
     * 
     * <p>
     * If this method returns successfully, the bits have been stored.
     * If an exception is thrown, the bits have not been stored.
     * </p>
     * 
     * @param context
     *            The current context
     * @param in
     *            The stream of bits to store
     * @exception IOException
     *             If a problem occurs while storing the bits
     * 
     * @return Map containing technical metadata (size, checksum, etc)
     */
    //@Override
    public Map<String, String> put(InputStream in, String id) throws IOException {
        Path file = getFile(id);
        // Make the parent dirs if necessary
        Files.createDirectories(file.getParent());
        Map<String, String> attrs = new HashMap<>();
        attrs.put("checksum_algorithm", CSA);
 
        // Read through a digest input stream that will work out the MD5
        try (DigestInputStream dis = new DigestInputStream(in, MessageDigest.getInstance(CSA))) {
            Files.copy(dis, file);
            in.close();
            attrs.put("size_bytes", String.valueOf(Files.size(file)));
            attrs.put("checksum", Utils.toHex(dis.getMessageDigest().digest()));
        } catch (NoSuchAlgorithmException nsaE) {
            // Should never happen
            log.warn("Caught NoSuchAlgorithmException", nsaE);
        }
      
        return attrs;
    }

    /**
     * Obtain technical metadata about an asset in the asset store.
     * 
     * @param context
     *            The current context
     * @param id
     *            The ID of the asset to describe
     * @param attrs
     *            A Map whose keys consist of desired metadata fields
     * 
     * @exception IOException
     *            If a problem occurs while obtaining metadata
     * @return attrs
     *            A Map with key/value pairs of desired metadata
     */
    //@Override
    public Map<String, String> about(String id, Map<String, String> attrs) throws IOException {
        // potentially expensive, since it may calculate the checksum
        Path file = getFile(id);
        if (file != null && Files.exists(file)) {
            if (attrs.containsKey("size_bytes")) {
                attrs.put("size_bytes", String.valueOf(Files.size(file)));
            }
            if (attrs.containsKey("checksum")) {
                // generate checksum by reading the bytes
                DigestInputStream dis = null;
                try {
                    InputStream fis = Files.newInputStream(file);
                    dis = new DigestInputStream(fis, MessageDigest.getInstance(CSA));
                } catch (NoSuchAlgorithmException e) {
                    log.warn("Caught NoSuchAlgorithmException", e);
                    throw new IOException("Invalid checksum algorithm");
                }
                final int BUFFER_SIZE = 1024 * 4;
                final byte[] buffer = new byte[BUFFER_SIZE];
                while (true) {
                    final int count = dis.read(buffer, 0, BUFFER_SIZE);
                    if (count == -1) {
                        break;
                    }
                }
                attrs.put("checksum", Utils.toHex(dis.getMessageDigest().digest()));
                attrs.put("checksum_algorithm", CSA);
                dis.close();
            }
            if (attrs.containsKey("modified")) {
                attrs.put("modified", String.valueOf(Files.getLastModifiedTime(file).toMillis()));
            }
            return attrs;
        }
        return null;
    }

    /**
     * Remove an asset from the asset store. An irreversible operation.
     * 
     * @param context
     *            The current context
     * @param id
     *            The ID of the asset to delete
     * @exception IOException
     *             If a problem occurs while removing the asset
     */
    //@Override
    public void remove(String id) throws IOException {
        Path file = getFile(id);
        if (file != null && Files.exists(file)) {
            if (Files.deleteIfExists(file)) {
                deleteParents(file);
            }
        } else {
            log.warn("Attempt to remove non-existent asset. ID: " + id);
        }
    }

    ////////////////////////////////////////
    // Internal methods
    ////////////////////////////////////////

    /**
     * Delete empty parent directories.
     * 
     * @param file
     *            The file with parent directories to delete
     */
    private synchronized static void deleteParents(Path file) throws IOException {
        if (file == null) {
            return;
        }
 
        Path tmp = file;
        for (int i = 0; i < directoryLevels; i++) {
            Path directory = tmp.getParent();
            try (DirectoryStream<Path> dirStream = Files.newDirectoryStream(directory)) {
                // Only delete empty directories
                if (dirStream.iterator().hasNext()) {
                    break;
                }
                Files.delete(directory);
                tmp = directory;
            }
        }
    }

    /**
     * Return the File for the passed internal_id.
     *
     * @param id
     *            The internal_id
     * @return The file resolved from the id
     */
    private Path getFile(String id) throws IOException {
        return baseDir.resolve(getIntermediatePath(id)).resolve(id);
    }

    /**
     * Return the path derived from the internal_id. This method
     * splits the id into groups which become subdirectories.
     *
     * @param id
     *            The internal_id
     * @return The path based on the id without leading or trailing separators
     */
    private static String getIntermediatePath(String id) {
        StringBuilder buf = new StringBuilder();
        for (int i = 0; i < directoryLevels; i++) {
            int digits = i * digitsPerLevel;
            if (i > 0) {
                buf.append(java.io.File.separator);
            }
            buf.append(id.substring(digits, digits + digitsPerLevel));
        }
        buf.append(java.io.File.separator);
        return buf.toString();
    }
}
