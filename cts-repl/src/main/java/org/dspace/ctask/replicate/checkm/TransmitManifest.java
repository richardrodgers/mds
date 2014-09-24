/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */

package org.dspace.ctask.replicate.checkm;

import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;

//import com.google.common.io.Files;
import com.google.common.hash.Hashing;
import com.google.common.hash.HashCode;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.dspace.authorize.AuthorizeException;
import org.dspace.content.*;
import org.dspace.core.ConfigurationManager;
import org.dspace.core.Constants;
import org.dspace.ctask.replicate.ReplicaManager;
import org.dspace.curate.AbstractCurationTask;
import org.dspace.curate.Curator;
import org.dspace.curate.Distributive;

/**
 * TransmitManifest task produces a manifest file for the content files contained
 * in the passed DSpace object, and forwards it to the replication system for
 * transmission (upload). If the DSpace Object is a container,
 * the task produces a multi-level set of manifests representing the container.
 * <p>
 * The manifests produced conform to the CDL Checkm v0.7 manifest format spec.
 * http://www.cdlib.org/uc3/docs/checkmspec.html
 * 
 * @author richardrodgers
 */
@Distributive
public class TransmitManifest extends AbstractCurationTask {

    //Version of CDL Checkm spec that this manifest conforms to
    private static final String CKM_VSN = "0.7";
    
    //Format extension for manifest files
    protected static final String MANIFEST_EXTENSION = "txt";

    private static String template = null;
    
    // Group where all Manifests will be stored
    //private final String manifestGroupName = ConfigurationManager.getProperty("replicate", "group.manifest.name");
    
    static {
        template = ConfigurationManager.getProperty("replicate", "checkm.template");
    }

    private static Logger log = LoggerFactory.getLogger(TransmitManifest.class);
    
    /**
     * Perform 'Transmit Manifest' task
     * <p>
     * Actually generates manifest and transmits to Replica ObjectStore
     * @param dso DSpace Object to perform on
     * @return integer which represents Curator return status
     * @throws IOException 
     */
    @Override
    public int perform(DSpaceObject dso) throws AuthorizeException, IOException, SQLException {
        ReplicaManager repMan = ReplicaManager.instance();
        Path manFile = null;
        switch (dso.getType()) {
            case Constants.ITEM : 
                manFile = itemManifest(repMan, (Item)dso); break;
            case Constants.COLLECTION :
                // create manifests for each item - link in collection manifest
                manFile = collectionManifest(repMan, (Collection)dso); break;
            case Constants.COMMUNITY :
                // create manifests for Community on down
                manFile = communityManifest(repMan, (Community)dso); break;
            case Constants.SITE :
                // create manifests for all objects in DSpace
                manFile = siteManifest(repMan, (Site)dso); break;
            default: break;
        }
            
        repMan.transferManifest(manFile);
        setResult("Created manifest for: " + dso.getHandle());
        return Curator.CURATE_SUCCESS;
    } 
    
    /**
     * Generate a manifest for the DSpace Site. Also
     * generate & transfer to replica ObjectStore the manifests for all
     * objects in DSpace, starting with the top-level Communities.
     * @param repMan ReplicaManager (used to access ObjectStore)
     * @param site the DSpace Site object
     * @return reference to manifest file generated for Community
     * @throws IOException
     * @throws SQLException 
     */
    private Path siteManifest(ReplicaManager repMan, Site site) throws IOException, SQLException {
        //Manifests stored as text files
        String filename = repMan.storageId(site.getHandle(), MANIFEST_EXTENSION);
        
        log.debug("Creating manifest for: " + site.getHandle());
        
        //Create site manifest
        Path manFile = repMan.stage(repMan.manifestGroupName(), filename);
        Writer writer = manifestWriter(manFile);
        int count = 0;
        
        BoundedIterator<Community> topIter = Community.findAllTop(Curator.curationContext());
        //Create top-level community manifests & transfer each
        while (topIter.hasNext()) {
            Path scFile = communityManifest(repMan, topIter.next());
            writer.write(tokenized(scFile) + "\n");
            count++;
            repMan.transferManifest(scFile);
        }
        if (count == 0) {
            // write EOF marker to prevent confusion if container empty
            writer.write("#%eof" + "\n");
        }
        writer.close();
        report("Created manifest for: " + site.getHandle());
        return manFile;
    }
    
    /**
     * Generate a manifest for the specified DSpace Community. Also
     * generate & transfer to replica ObjectStore the manifests for any child 
     * objects (sub-communities, collections).
     * @param repMan ReplicaManager (used to access ObjectStore)
     * @param comm the DSpace Community
     * @return reference to manifest file generated for Community
     * @throws IOException
     * @throws SQLException 
     */
    private Path communityManifest(ReplicaManager repMan, Community comm) throws IOException, SQLException {
        //Manifests stored as text files
        String filename = repMan.storageId(comm.getHandle(), MANIFEST_EXTENSION);
        
        log.debug("Creating manifest for: " + comm.getHandle());
        
        //Create community manifest
        Path manFile = repMan.stage(repMan.manifestGroupName(), filename);
        Writer writer = manifestWriter(manFile);
        int count = 0;
        //Create sub-community manifests & transfer each
        BoundedIterator<Community> scIter = comm.getSubcommunities();
        while (scIter.hasNext()) {
            Path scFile = communityManifest(repMan, scIter.next());
            writer.write(tokenized(scFile) + "\n");
            count++;
            repMan.transferManifest(scFile); 
        }
        //Create collection manifests & transfer each
        BoundedIterator<Collection> colIter = comm.getCollections();
        while (colIter.hasNext()) {
            Path colFile = collectionManifest(repMan, colIter.next());
            writer.write(tokenized(colFile) + "\n");
            count++;
            repMan.transferManifest(colFile);
        }
        if (count == 0) {
            // write EOF marker to prevent confusion if container empty
            writer.write("#%eof" + "\n");
        }
        writer.close();
        report("Created manifest for: " + comm.getHandle());
        return manFile;
    }

    /**
     * Generate a manifest for the specified DSpace Collection. Also
     * generate & transfer to replica ObjectStore the manifests for any child 
     * objects (items).
     * @param repMan ReplicaManager (used to access ObjectStore)
     * @param coll the DSpace Collection
     * @return reference to manifest file generated for Collection
     * @throws IOException
     * @throws SQLException 
     */
    private Path collectionManifest(ReplicaManager repMan, Collection coll) throws IOException, SQLException {
         //Manifests stored as text files
        String filename = repMan.storageId(coll.getHandle(), MANIFEST_EXTENSION);
       
        log.debug("Creating manifest for: " + coll.getHandle());
        
        //Create Collection manifest
        Path manFile = repMan.stage(repMan.manifestGroupName(), filename);
        Writer writer = manifestWriter(manFile);
        int count = 0;
        
        //Create all Item manifests & transfer each
        try (BoundedIterator<Item> ii = coll.getItems()) {
            while (ii.hasNext()) {
                Path itemMan = itemManifest(repMan, ii.next());
                count++;
                writer.write(tokenized(itemMan) + "\n");
                repMan.transferManifest(itemMan);
            }
        }
        if (count == 0) {
            // write EOF marker to prevent confusion if container empty
            writer.write("#%eof" + "\n");
        }
        writer.close();
        report("Created manifest for: " + coll.getHandle());
        return manFile;
    }

    /**
     * Generate a manifest for the specified DSpace Item. 
     * @param repMan ReplicaManager (used to access ObjectStore)
     * @param item the DSpace Item
     * @return reference to manifest file generated for Item
     * @throws IOException
     * @throws SQLException 
     */
    private Path itemManifest(ReplicaManager repMan, Item item) throws IOException, SQLException {
        String filename = repMan.storageId(item.getHandle(), MANIFEST_EXTENSION);
        
        log.debug("Creating manifest for: " + item.getHandle());
        
        //Create Item manifest
        Path manFile = repMan.stage(repMan.manifestGroupName(), filename);
        Writer writer = manifestWriter(manFile);
       
        // look through all ORIGINAL bitstreams, and add
        // information about each (e.g. checksum) to manifest
        int count = 0;
        List<Bundle> bundles = item.getBundles("ORIGINAL");
        if (bundles.size() > 0 ) {    
            //there should be only one ORIGINAL bundle
            Bundle bundle = bundles.get(0);
            for (Bitstream bs : bundle.getBitstreams()) {
                int i = 0;
                StringBuilder sb = new StringBuilder();
                for (String token : Arrays.asList(template.split("\\|"))) {
                    if (! token.startsWith("x")) {
                        // tokens are positionally defined
                        switch (i) {
                            case 0:
                                // what URL/name format?
                                sb.append(item.getHandle()).append("/").append(bs.getSequenceID());
                                break;
                            case 1:
                                // Checksum algorithm
                                sb.append(bs.getChecksumAlgorithm().toLowerCase());
                                break;
                            case 2:
                                // Checksum
                                sb.append(bs.getChecksum());
                                break;
                            case 3:
                                // length
                                sb.append(bs.getSize());
                                break;
                            case 4:
                                // modified - use item level data?
                                sb.append(item.getLastModified());
                                break;
                            case 5:
                                // target name - skip for now
                            default:
                                break;
                        }
                    }
                    sb.append("|");
                    i++;
                }
                count++;
                writer.write(sb.substring(0, sb.length() - 1) + "\n");
            } //end for each bitstream
        }//end if ORIGINAL bundle
        
        //If no bitstreams found, then this is an empty manifest
        if (count == 0) {
            // write EOF marker to prevent confusion if container empty
            writer.write("#%eof" + "\n");
        }
        
        writer.close();
        report("Created manifest for: " + item.getHandle());
        return manFile;
    }

    /**
     * Initialize a Writer for a Manifest file. Also, writes header to manifest file.
     * @param file file where manifest will be stored
     * @return reference to Writer
     * @throws IOException 
     */
    private Writer manifestWriter(Path file) throws IOException {
        FileWriter writer = new FileWriter(file.toFile());
        writer.write("#%checkm_" + CKM_VSN + "\n");
        // write out template as explanatory metadata
        writer.write("# " + template + "\n");
        return writer;
    }

    private String tokenized(Path file) throws IOException {
        int i = 0;
        StringBuilder sb = new StringBuilder();
        for (String token : Arrays.asList(template.split("\\|"))) {
            if (! token.startsWith("x")) {
                // tokens are positionally defined
                switch (i) {
                    case 0:
                        // what URL/name format?
                        sb.append(file.getFileName().toString());
                        break;
                    case 1:
                        // Checksum algorithm
                        sb.append("md5");
                        break;
                    case 2:
                        // Checksum
                        // test this! RLR
                        sb.append(com.google.common.io.Files.hash(file.toFile(), Hashing.md5()).toString());
                        //sb.append(Utils.checksum(file, "md5"));
                        break;
                    case 3:
                        // length
                        sb.append(Files.size(file));
                        break;
                    case 4:
                        // modified - use item level data?
                        sb.append(Files.getLastModifiedTime(file).toMillis());
                        break;
                    case 5:
                         // target name - skip for now
                    default:
                         break;
                }
            }
            sb.append("|");
            i++;
        }
        return sb.substring(0, sb.length() - 1);
    }
}
