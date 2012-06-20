/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.admin;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FilenameFilter;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.InputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.URL;
import java.net.MalformedURLException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.zip.ZipFile;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;

import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;

import com.google.common.base.Strings;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.xpath.XPathAPI;

import org.dspace.administer.RegistryLoader;
import org.dspace.authorize.AuthorizeException;
import org.dspace.authorize.AuthorizeManager;
import org.dspace.authorize.ResourcePolicy;
import org.dspace.content.Bitstream;
import org.dspace.content.BitstreamFormat;
import org.dspace.content.Bundle;
import org.dspace.content.Collection;
import org.dspace.content.Community;
import org.dspace.content.DSpaceObject;
import org.dspace.content.FormatIdentifier;
import org.dspace.content.InstallItem;
import org.dspace.content.Item;
import org.dspace.content.MetadataField;
import org.dspace.content.MetadataSchema;
import org.dspace.content.WorkspaceItem;
import org.dspace.core.ConfigurationManager;
import org.dspace.core.Constants;
import org.dspace.core.Context;
import org.dspace.eperson.EPerson;
import org.dspace.eperson.Group;
import org.dspace.handle.HandleManager;
import org.dspace.search.DSIndexer;
import org.dspace.workflow.WorkflowManager;

/**
 * Import content - communities, collections, items, etc into DSpace from
 * directories in slightly reformed 'Simple Archive Format', or zip archives thereof.
 * Based on ItemImport in DSpace 1.X
 * SAF 2.0 description: an archive directory contains one or more directories,
 * which represent sub-tree(s) to be attached to a 'root' in the content hierarchy
 * of the existing repository. The root is specified when ContentImport is run,
 * with the special case of no root meaning that the top directories are construed
 * to represent 'top-level' communities. Otherwise roots may be any point in
 * the repository, down to individual items or bundles. The required directory
 * names, and hierarchy are:
 * 
 *  community*
 *    community* <- subcommunity
 *    collection*
 *       item*
 *         bundle*
 *           bitstream*
 *             data or 'fetch.txt'
 *               <bitstream file>
 *                  
 *  when multiple directories exist at the same depth - as is the common case of
 *  multiple items in a collection, e.g - they must of course be distingushed by
 *  some suffixing scheme such as 'item_001' 'item_002', although the importer
 *  assigns no meaning to these directory names (like order). 
 *  
 *  Each directory may contain a file called 'metadata.xml'. Community and Collection
 *  directories may optionally contain a file named 'logo' which is the container
 *  logo bitstream.
 *  
 *  'metadata.xml' file has following structure:
 *  
 *    <?xml version="1.0" encoding="UTF-8" ?>
 *    <metadata>
 *      <mdvalues schema="dc">
 *      	<mdvalue element="contributor" qualifier="author" language="us" place="1">Rodgers, Richard</mdvalue>
 *          ....
 *      </mdvalues>
 *      <mdvalues schema="etd">
 *         ....
 *      </mdvalues>
 *    </metadata>
 *    
 *  If the 'schema' attribute is not set, it is assumed to be an internal schema
 *  (e.g. as would be the case for some community or collection metadata). 
 *  'element' is the only required attribute of the 'mdvalue' element.
 *  
 *  Features removed from ItemImport (maybe restore at some point):
 *  -> passing permission data for bitstreams
 *  -> linking items to non-owning collections
 *  -> registered bitstreams
 *  
 * @author richardrodgers
 */
public class ContentImport {
	
	// name of metdata file
	private static final String METADATAFILE = "metadata.xml";
	// name of bitstream data directory
	private static final String DATADIR = "data";
	// name of remote data description file
	private static final String FETCHFILE = "fetch.txt";
    private static final Logger log = LoggerFactory.getLogger(ContentImport.class);
    enum Action {add, replace, delete}
    @Argument(index=0, usage="action to take: add, replace, or delete", required=true)
    private Action action;
    @Option(name="-w", usage="send submission(s) through collection's workflow")
    private boolean useWorkflow;
    @Option(name="-n", usage="if sending submissions through the workflow, send notification emails")
    private boolean useWorkflowSendEmail;
    @Option(name="-t", usage="test run - do not actually import content")
    private boolean isTest;
    @Option(name="-R", usage="resume a failed import (add only)")
    private boolean isResume;
    @Option(name="-e", usage="email of eperson doing importing")
    private String epersonId; // db ID or email
    @Option(name="-m", usage="map content in mapfile")
    private String mapFileName;
    @Option(name="-s", usage="source of content (directory)")   
    private String sourceDirName;
    @Option(name="-r", usage="import root - object Handle or ID")
    private String importRoot;
    @Option(name="-z", usage="name or URL of zip file - name relative to source directory")    
    private String zipFileName;
    @Option(name="-h", usage="help")
    private boolean help;
    @Option(name="-v", usage="write processing details to console")
    private boolean verbose;

    private PrintWriter mapOut;
    private EPerson eperson;
    private File sourceDir;
    private DSpaceObject root;
    
    private ContentImport() {}

    // File listing filter to check for folders
    private static FilenameFilter directoryFilter = new FilenameFilter() {
        public boolean accept(File dir, String n) {
            File item = new File(dir.getAbsolutePath() + File.separatorChar + n);
            return item.isDirectory();
        }
    };
    
    public static void main(String[] args) throws Exception {
    	DSIndexer indexer = new DSIndexer();
        indexer.setBatchProcessingMode(true);
        Date startTime = new Date();
        int status = 1;

        ContentImport importer = new ContentImport();
        CmdLineParser parser = new CmdLineParser(importer);
        Context context = null;
        try {
            parser.parseArgument(args);
            if (! importer.help) {
            	context = new Context();
            	String errmsg = importer.validateArguments(context);
            	if (errmsg != null) {
            		throw new CmdLineException(parser, errmsg);
            	}
            	status = importer.importData(context);
            	context.complete();
            } else {
            	parser.printUsage(System.err);
            	status = 0;
            }
        }  catch (CmdLineException clE) {
            System.err.println(clE.getMessage());
            parser.printUsage(System.err);
        } catch (Exception e) {
        	System.err.println(e.getMessage());
        } finally {
            indexer.setBatchProcessingMode(false);
            Date endTime = new Date();
            System.out.println("Started: " + startTime.getTime());
            System.out.println("Ended: " + endTime.getTime());
            System.out.println("Elapsed time: " + ((endTime.getTime() - startTime.getTime()) / 1000) + 
            		           " secs (" + (endTime.getTime() - startTime.getTime()) + " msecs)");
            if (context != null && context.isValid()) {
            	context.abort();
            }
        }
        System.exit(status);
    }
        
    private String validateArguments(Context context) throws Exception {
      
        if (action.equals(Action.add) || action.equals(Action.replace)) {
            if (sourceDirName == null) {
            	return "Error - a source directory containing content must be set";
            } else {
            	sourceDir = new File(sourceDirName);
            	if (! sourceDir.isDirectory() || ! sourceDir.canRead()) {
            		return "Error, cannot open source directory " + sourceDirName;
            	}
            }
            
            String errmsg = openZip();
            if (errmsg != null) {
            	return errmsg;
            }

            if (importRoot != null) {
            	// ensure it is a valid reference
            	root = resolveRoot(context, importRoot);
            	if (root == null) {
            		return "Error - cannot resolve import root: " + importRoot;
            	}
            } 
        } 
        
        if (mapFileName == null) {
            return "Error - a map file to hold import results must be specified";
        }
        
        if (epersonId == null) {
            return "Error - an eperson to do the importing must be specified";
        }
        // find the EPerson
        if (epersonId.indexOf('@') != -1) {
            // @ sign, must be an email
            eperson = EPerson.findByEmail(context, epersonId);
        } else {
            eperson = EPerson.find(context, Integer.parseInt(epersonId));
        }

        if (eperson == null) {
            return "Error, eperson cannot be found: " + epersonId;
        }
        
        // can only resume for adds
        if (isResume && ! action.equals(Action.add)) {
            return "Error - resume option only works with -a add command";
        }

        // do checks around mapfile - if mapfile exists and 'add' is selected,
        // resume must be chosen
        File mapFile = new File(mapFileName);

        if (! isResume && action.equals(Action.add) && mapFile.exists()) {
            return "Error - the mapfile " + mapFileName + " already exists.";
        }

        return null;
    }
    
    private int importData(Context context) throws Exception {
    	
    	int status = 0;
    	
        if (isTest) {
            System.out.println("**Test Run** - not actually importing items.");
        }

        if (isResume) {
            System.out.println("**Resume import** - attempting to import items not already imported");
        }

        context.setCurrentUser(eperson);
        
        // delete actions are simple - dispense with them here
        if (action.equals(Action.delete)) {
        	return deleteObjects(context, mapFileName);
        }

        try {
        	
            // really?
            context.turnOffAuthorisationSystem();

            if (action.equals(Action.add))  {
            	addObjects(context, mapFileName);
            } else if (action.equals(Action.replace)) {
            	replaceObjects(context, mapFileName);
            } else {
            	if (verbose) {
            		System.out.println("No action to take");
            	}
            }

        } catch (Exception e)  {
            e.printStackTrace();
            System.out.println(e);
            status = 1;
        } finally {
        	if (mapOut != null) {
        		mapOut.close();
        	}
        }

        if (isTest)  {
           System.out.println("***End of Test Run***");
        }
        
        return status;
    }
    
    private String openZip() throws Exception {
        // If there is a zip archive, unzip it first from file or URL
    	// much easier to process than via an input stream
    	String errmsg = null;
        if (zipFileName != null) {
        	try {
        		URL zipUrl = new URL(zipFileName);
        		explodeZip(new ZipInputStream(zipUrl.openConnection().getInputStream()));
        	} catch (MalformedURLException mfuE) {
        		// assume a file relative to sourceDir
        		try {
        			File zipFile = new File(sourceDir, zipFileName);
        			if (zipFile.exists()) {
        				explodeZip(new ZipInputStream(new FileInputStream(zipFile)));
        				// conserve disk space and delete the zip, we have an exploded copy
        				zipFile.delete();
        			} else {
        				errmsg = "Zip file is neither a URL nor an extant file: " + zipFileName;
        			}
        		} catch (Exception e) {
        			errmsg = "Error exploding zip file: " + zipFileName;
        			log.error(errmsg);
        		}
        	} catch (Exception e) {
        		errmsg = "Error exploding zip file: " + zipFileName;
        		log.error(errmsg);
        	}
        }
        return errmsg;
    }
        
    private void explodeZip(ZipInputStream in) throws Exception {
    	
    	ZipEntry entry = null;
    	while ((entry = in.getNextEntry()) != null) {
            if (entry.isDirectory()) {
                if (! new File(sourceDir, entry.getName()).mkdir())  {
                    log.error("Unable to create contents directory: " + entry.getName());
                }
            } else {
                if (verbose) {
                	System.out.println("Extracting file: " + entry.getName());
                }
                int index = entry.getName().lastIndexOf('/');
                if (index == -1) {
                    // Was it created on Windows instead?
                    index = entry.getName().lastIndexOf('\\');
                }
                File theFile = null;
                if (index > 0) {
                    File dir = new File(sourceDir, entry.getName().substring(0, index));
                    theFile = new File(dir, entry.getName().substring(index + 1));
                }
                byte[] buffer = new byte[1024];
                int len;
                BufferedOutputStream out = new BufferedOutputStream(
                        				   new FileOutputStream(theFile));
                while((len = in.read(buffer)) >= 0) {
                    out.write(buffer, 0, len);
                }
                in.closeEntry();
                out.close();
            }
    	}
    	in.close();
    }
    
    private DSpaceObject resolveRoot(Context context, String objectId) throws SQLException {
    	
        if (objectId.indexOf('/') != -1) {
            // string has a / so it must be a handle - try and resolve it
         	return HandleManager.resolveToObject(context, objectId);
        } else {
            // not a handle, try to treat it as a UUID
        	return DSpaceObject.findByObjectID(context, objectId);
        }
    }
    
    private List<Collection> resolveCollections(Context context, List<String> collIds) throws Exception {
    	
        List<Collection> collections = new ArrayList<Collection>(collIds.size());
        // validate each collection arg to see if it's a real collection
        int i = 0;
        for (String collId : collIds) {
        	boolean found = true;
            // is the ID a handle?
            if (collId.indexOf('/') != -1) {
                // string has a / so it must be a handle - try and resolve it
            	DSpaceObject dso = HandleManager.resolveToObject(context, collId);
            	if (dso != null && dso.getType() == Constants.COLLECTION) {
            		collections.add((Collection)dso);
            	} else {
            		found = false;
            	}
            } else {
            	// not a handle, try and treat it as an integer collection database ID
            	Collection addColl = Collection.find(context, Integer.parseInt(collId));
            	if (addColl != null) {
            		collections.add(addColl);
            	} else {
            		found = false;
            	}
            }

            // was the collection valid?
            if (! found) {
                throw new IllegalArgumentException("Cannot resolve " + collId + " to collection");
            }

            // print progress info
            if (verbose) {
            	System.out.println("Linked collection: " + collections.get(i).getMetadata("name"));
            }
            i++;
        }       
        return collections;
    }

    private void addObjects(Context c, String mapFile) throws Exception {
    	
    	// set of items to skip if in 'resume' mode
        Map<String, String> skipItems = new HashMap<String, String>(); 

        if (verbose) {
        	System.out.println("Adding items from directory: " + sourceDirName);
        	System.out.println("Generating mapfile: " + mapFile);
        }

        // create the mapfile
        File outFile = null;

        if (!isTest) {
            // get the directory names of items to skip (will be in keys of
            // hash)
            if (isResume) {
                skipItems = readMapFile(mapFile);
            }

            // sneaky isResume == true means open file in append mode
            outFile = new File(mapFile);
            mapOut = new PrintWriter(new FileWriter(outFile, isResume));

            if (mapOut == null) {
                throw new Exception("can't open mapfile: " + mapFile);
            }
        }

        // open and process the source directory
        String[] dircontents = sourceDir.list(directoryFilter);
        
        Community rootCommunity = (root != null && root.getType() == Constants.COMMUNITY) ? (Community)root : null;
        Collection rootCollection = (root != null && root.getType() == Constants.COLLECTION) ? (Collection)root : null;
        Item rootItem = (root != null && root.getType() == Constants.ITEM) ? (Item)root : null;
        for (int i = 0; i < dircontents.length; i++) {
            if (skipItems.containsKey(dircontents[i])) {
            	if (verbose) {
            		System.out.println("Skipping import of " + dircontents[i]);
            	}
            } else {
            	File workingDir = new File(sourceDir, dircontents[i]);
            	if (dircontents[i].startsWith("community")) {
            	    addCommunityTree(c, rootCommunity, workingDir, mapOut);	
            	} else if (dircontents[i].startsWith("collection")) {
            		addCollectionTree(c, rootCommunity, workingDir, mapOut);
            	} else {
            		addItemTree(c, rootCollection, workingDir, mapOut);
            	}
            	if (verbose) {
            		System.out.println(i + " " + dircontents[i]);
            	}
                c.clearCache();
            }
        }
    }

    private void replaceObjects(Context c, String mapFile) throws Exception {

        // read in HashMap first, to get list of handles & source dirs
        Map<String, String> myHash = readMapFile(mapFile);

        // for each handle, re-import the item, discard the new handle
        // and re-assign the old handle
        for (Map.Entry<String, String> mapEntry : myHash.entrySet()) {
            // get the old handle
            String newItemName = mapEntry.getKey();
            String oldHandle = mapEntry.getValue();

            Item oldItem = null;

            if (oldHandle.indexOf('/') != -1) {
            	if (verbose) {
            		System.out.println("\tReplacing:  " + oldHandle);
            	}
                oldItem = (Item) HandleManager.resolveToObject(c, oldHandle);
            } else {
                oldItem = Item.find(c, Integer.parseInt(oldHandle));
            }

            /* Rather than exposing public item methods to change handles --
             * two handles can't exist at the same time due to key constraints
             * so would require temp handle being stored, old being copied to new and
             * new being copied to old, all a bit messy -- a handle file is written to
             * the import directory containing the old handle, the existing item is
             * deleted and then the import runs as though it were loading an item which
             * had already been assigned a handle (so a new handle is not even assigned).
             * As a commit does not occur until after a successful add, it is safe to
             * do a delete as any error results in an aborted transaction without harming
             * the original item */
            File handleFile = new File(sourceDirName + File.separator + newItemName + File.separator + "handle");
            PrintWriter handleOut = new PrintWriter(new FileWriter(handleFile, true));

            if (handleOut == null) {
                throw new Exception("can't open handle file: " + handleFile.getCanonicalPath());
            }

            handleOut.println(oldHandle);
            handleOut.close();

            deleteItem(c, oldItem);
            addItemTree(c, (Collection)root, new File(sourceDir, newItemName), null);
            c.clearCache();
        }
    }

    private int deleteObjects(Context c, String mapFile) throws Exception  {
    	
    	if (verbose) {
    		System.out.println("Deleting objects listed in mapfile: " + mapFile);
    	}

        // read in the mapfile
        Map<String, String> myhash = readMapFile(mapFile);

        // now delete everything that appeared in the mapFile
        Iterator<String> iterator = myhash.keySet().iterator();

        while (iterator.hasNext()) {
            String objId = myhash.get(iterator.next());
            if (verbose) {
        		System.out.println("Deleting object " + objId);
            }
            if (objId.indexOf('/') != -1) {
                deleteObject(c, objId);
            } else {
                // it's an Item ID
                Item item = Item.find(c, Integer.parseInt(objId));
                deleteItem(c, item);
            }
            c.clearCache();
        }
        return 0;
    }
    
    private void addCommunityTree(Context c, Community parent, File workingDir,
    							  PrintWriter mapOut) throws Exception {
    	Community community = (parent != null) ? parent.createSubcommunity() :
            									 Community.create(null, c);     
        // now load metadata, etc
    	loadMetadata(c, community, workingDir);
        // add logo file if presents
        File logoFile = new File(workingDir, "logo");
        if (logoFile.exists()) {
        	FileInputStream in = new FileInputStream(logoFile);
        	community.setLogo(in);
        	in.close();
        }
        
        community.update();
        
        if (mapOut != null) {
            mapOut.println(workingDir.getName() + " " + community.getHandle());
        }
        // now descend into any sub-structure
        for (String dirName : workingDir.list(directoryFilter)) {
        	if (dirName.startsWith("community")) {
        		addCommunityTree(c, community, new File(workingDir, dirName), mapOut);
        	} else if (dirName.startsWith("collection")) {
        		addCollectionTree(c, community, new File(workingDir, dirName), mapOut);
        	}
        }
    }
    
    private void addCollectionTree(Context c, Community parent, File workingDir,
    						       PrintWriter mapOut) throws Exception {
    	
    	Collection collection =  parent.createCollection();
    	
        // now load metadata, etc
    	loadMetadata(c, collection, workingDir);
        // add logo file if present
        File logoFile = new File(workingDir, "logo");
        if (logoFile.exists()) {
        	FileInputStream in = new FileInputStream(logoFile);
        	collection.setLogo(in);
        	in.close();
        }
        
        collection.update();
        
        if (mapOut != null) {
            mapOut.println(workingDir.getName() + " " + collection.getHandle());
        }
        
        // now descend into any sub-structure
        for (String dirName : workingDir.list(directoryFilter)) {
        	if (dirName.startsWith("item")) {
        		addItemTree(c, collection, new File(workingDir, dirName), mapOut);
        	}
        }
    }
    
    /**
     * item? try and add it to the archive.
     * @param collections - add item to these Collections.
     * @param path - directory containing the item directories.
     * @param itemname handle - non-null means we have a pre-defined handle already 
     * @param mapOut - mapfile we're writing
     */
    private void addItemTree(Context c, Collection home,
            			 	File workingDir, PrintWriter mapOut) throws Exception
    {
        String mapOutput = null;

        if (verbose) {
        	System.out.println("Adding item from directory " + workingDir.getName());
        }

        // create workspace item
        Item item = null;
        WorkspaceItem wi = null;

        if (!isTest) {
            wi = WorkspaceItem.create(c, home, false);
            item = wi.getItem();
        }
        
        // now load metadata, etc
        loadMetadata(c, item, workingDir);
        
        // now descend into any sub-structure
        for (String dirName : workingDir.list(directoryFilter)) {
        	if (dirName.startsWith("bundle")) {
        		addBundleTree(c, item, new File(workingDir, dirName), mapOut);
        	}
        }

        if (useWorkflow) {
            // don't process handle file
            // start up a workflow
            if (!isTest) {
                // Should we send a workflow alert email or not?
            	/*
                if (ConfigurationManager.getProperty("workflow", "workflow.framework").equals("xmlworkflow")) {
                    if (useWorkflowSendEmail) {
                        XmlWorkflowManager.start(c, wi);
                    } else {
                        XmlWorkflowManager.startWithoutNotify(c, wi);
                    }
                } else {
                */
                    if (useWorkflowSendEmail) {
                        WorkflowManager.start(c, wi);
                    } else {
                        WorkflowManager.startWithoutNotify(c, wi);
                    }
                //}

                // send ID to the mapfile
                //mapOutput = itemname + " " + item.getID();
            }
        } else {
            // only process handle file if not using workflow system
            String handle = null; //processHandleFile(sourceDirName + File.separator + itemname, "handle");

            // put item in system
            if (!isTest) {
                InstallItem.installItem(c, wi, handle);

                // find the handle, and output to map file
                handle = HandleManager.findHandle(c, item);
                mapOutput = workingDir.getName() + " " + handle;
            }
        }

        // made it this far, everything is fine, commit transaction
        if (mapOut != null) {
            mapOut.println(mapOutput);
        }

        c.commit();
    }
    
    private void addBundleTree(Context c, Item parent, File workingDir,
		       PrintWriter mapOut) throws Exception {
    	// temp name will be reset in metadata assignments
    	Bundle bundle = parent.createBundle("temp");

    	// now load metadata, etc
    	loadMetadata(c, bundle, workingDir);

    	bundle.update();

    	// now descend into any sub-structure
    	for (String dirName : workingDir.list(directoryFilter)) {
    		if (dirName.startsWith("bitstream")) {
    			addBitstream(c, bundle, new File(workingDir, dirName), mapOut);
    		}
    	}
    }
    
    private void addBitstream(Context c, Bundle parent, File workingDir,
		       				   PrintWriter mapOut) throws Exception {
    	// get the bitstream data, which is either in the 'data' directory,
    	// or at the end of the URL in 'fetch.txt' file
    	Bitstream bitstream =  null;
    	File dataDir = new File(workingDir, DATADIR);
    	if (dataDir.exists()) {
    		File[] dataFiles = dataDir.listFiles();
            // get an input stream
            BufferedInputStream bis = new BufferedInputStream(new FileInputStream(dataFiles[0]));
            bitstream = parent.createBitstream(bis);
            bis.close();
            bitstream.setName(dataFiles[0].getName());
    	} else {
    		File fetchFile = new File(workingDir, FETCHFILE);
    		if (fetchFile.exists()) {
    			BufferedReader reader = new BufferedReader(new FileReader(fetchFile));
    			URL fetchUrl = new URL(reader.readLine().trim());
    			reader.close();
    			InputStream in = fetchUrl.openStream();
    			bitstream = parent.createBitstream(in);
    			in.close();
    		} else {
    			// log error - can we allow bistreams with only MD?
    			return;
    		}
    	}

    	// now load metadata, etc
    	loadMetadata(c, bitstream, workingDir);
    	
        BitstreamFormat bf = FormatIdentifier.guessFormat(c, bitstream);
        bitstream.setFormat(bf);

    	bitstream.update();
    }

    // remove, given the actual item
    private void deleteItem(Context c, Item item) throws Exception {
        if (!isTest) {
            // Remove item from all the collections it's in
        	for (Collection coll : item.getCollections()) {
                coll.removeItem(item);
            }
        }
    }

    // remove, given a handle
    private void deleteObject(Context c, String handle) throws Exception {
        
        DSpaceObject object = HandleManager.resolveToObject(c, handle);
        if (object == null) {
        	if (verbose) {
        		System.out.println("Error - cannot locate object - already deleted?");
        	}
        } else {
        	if (object.getType() == Constants.ITEM) { 
        		deleteItem(c, (Item)object);
        	} else if (object.getType() == Constants.COLLECTION) {
        		Collection coll = (Collection)object;
        		((Community)coll.getParentObject()).removeCollection(coll);
        	} else if (object.getType() == Constants.COMMUNITY) {
        		Community comm = (Community)object;
        		Community parent = comm.getParentCommunity();
        		if (parent != null) {
        			parent.removeSubcommunity(comm);
        		} else {
        			comm.delete();
        		}
        	}
        }
    }

    ////////////////////////////////////
    // utility methods
    ////////////////////////////////////
    // read in the map file and generate a hashmap of (file,handle) pairs
    private Map<String, String> readMapFile(String filename) throws Exception {
        Map<String, String> myHash = new HashMap<String, String>();

        BufferedReader is = null;
        try {
            is = new BufferedReader(new FileReader(filename));

            String line;
            while ((line = is.readLine()) != null) {
                String myFile;
                String myHandle;

                // a line should be archive filename<whitespace>handle
                StringTokenizer st = new StringTokenizer(line);

                if (st.hasMoreTokens()) {
                    myFile = st.nextToken();
                }  else {
                    throw new Exception("Bad mapfile line:\n" + line);
                }

                if (st.hasMoreTokens()) {
                    myHandle = st.nextToken();
                } else {
                    throw new Exception("Bad mapfile line:\n" + line);
                }
                myHash.put(myFile, myHandle);
            }
        } finally {
            if (is != null) {
                is.close();
            }
        }
        return myHash;
    }
    
    // load basic metadata
    private void loadMetadata(Context c, DSpaceObject dso, File workingDir)
            throws SQLException, IOException, ParserConfigurationException,
            SAXException, TransformerException, AuthorizeException {
    	File mdFile = new File(workingDir, METADATAFILE);
        if (mdFile.exists()) {
        	if (verbose) {
        		System.out.println("\tLoading community metadata from " + mdFile.getName());
        	}
        	Document document = RegistryLoader.loadXML(mdFile.getPath());
        	
            NodeList mdSets = XPathAPI.selectNodeList(document, "/metadata/mdvalues");
            for (int i = 0; i < mdSets.getLength(); i++) {
            	Node mdSet = mdSets.item(i);
            	String schema = null;
            	Node schemaAttr = mdSet.getAttributes().getNamedItem("schema");
            	if (schemaAttr != null) {
            		schema = schemaAttr.getNodeValue();
            	} 
             
            	// Get the nodes corresponding to metadata values
            	NodeList mvNodes = XPathAPI.selectNodeList(mdSet, "mdvalue");

            	for (int j = 0; j < mvNodes.getLength(); j++) {
            		Node n = mvNodes.item(j);
            		if (schema != null) {
            			addMDValue(c, dso, schema, n);
            		} else {
            			// argh
            			switch (dso.getType()) {
            			   case Constants.COMMUNITY: ((Community)dso).setName(getStringValue(n)); break;
            			   case Constants.COLLECTION: ((Collection)dso).setName(getStringValue(n)); break;
            			   case Constants.BUNDLE: ((Bundle)dso).setName(getStringValue(n)); break;
            			   case Constants.BITSTREAM: ((Bitstream)dso).setName(getStringValue(n)); break;
            			   default: break;
            			}
            			//dso.setMetadataValue(getAttributeValue(n, "element"), getStringValue(n));
            		}
            	}
            }
        }
    }

    private void addMDValue(Context c, DSpaceObject dso, String schema, Node n)
    		throws TransformerException, SQLException, AuthorizeException {
    	
        // compensate for empty value getting read as "null", which won't display
        String value = Strings.nullToEmpty(getStringValue(n)); //n.getNodeValue();
       
        String element = getAttributeValue(n, "element");
        String qualifier = getAttributeValue(n, "qualifier"); //NodeValue();
        String language = getAttributeValue(n, "language");
        if (! Strings.isNullOrEmpty(language)) {
            language = language.trim();
        }
        int place = -1;
        String placeStr = getAttributeValue(n, "place");
        if (! Strings.isNullOrEmpty(placeStr)) {
        	place = Integer.parseInt(placeStr);
        } 

        if (verbose) {
            System.out.println("\tSchema: " + schema + " Element: " + element + " Qualifier: " + qualifier
                    + " Value: " + value);
        }

        if ("none".equals(qualifier) || "".equals(qualifier)) {
            qualifier = null;
        }

        // if language isn't set, use the system's default value
        if (Strings.isNullOrEmpty(language)) {
            language = ConfigurationManager.getProperty("default.language");
        }

        // a goofy default, but there it is
        if (language == null) {
            language = "en";
        }

        if (!isTest) {
        	if (dso.getType() == Constants.ITEM) {
        		((Item)dso).addMetadata(schema, element, qualifier, language, value);
        	} else if (dso.getType() == Constants.BITSTREAM) {
        		((Bitstream)dso).addMetadata(schema, element, qualifier, language, place, value);
        	}
        } else {
            // If we're just test the import, let's check that the actual metadata field exists.
        	MetadataSchema foundSchema = MetadataSchema.find(c,schema);
        	
        	if (foundSchema == null) {
        		System.out.println("ERROR: schema '"+schema+"' was not found in the registry.");
        		return;
        	}
        	
        	int schemaID = foundSchema.getSchemaID();
        	MetadataField foundField = MetadataField.findByElement(c, schemaID, element, qualifier);
        	
        	if (foundField == null) {
        		System.out.println("ERROR: Metadata field: '"+schema+"."+element+"."+qualifier+"' was not found in the registry.");
        		return;
            }		
        }
    }

    /**
     * Read in the handle file or return null if empty or doesn't exist
     */
    private String processHandleFile(String path, String filename)
    {
        File file = new File(path, filename);
        String result = null;

        if (verbose) {
        	System.out.println("Processing handle file: " + filename);
        }
        if (file.exists()) {
            BufferedReader is = null;
            try {
                is = new BufferedReader(new FileReader(file));
                // result gets contents of file, or null
                result = is.readLine();
                if (verbose) {
                	System.out.println("read handle: '" + result + "'");
                }
            } catch (FileNotFoundException e) {
                // probably no handle file, just return null
            	if (verbose) {
            		System.out.println("It appears there is no handle file -- generating one");
            	}
            } catch (IOException e) {
                // probably no handle file, just return null
            	if (verbose) {
            		System.out.println("It appears there is no handle file -- generating one");
            	}
            } finally {
                if (is != null) {
                    try {
                        is.close();
                    } catch (IOException e1){
                        System.err.println("Non-critical problem releasing resources.");
                    }
                }
            }
        } else {
            // probably no handle file, just return null
        	if (verbose) {
        		System.out.println("It appears there is no handle file -- generating one");
        	}
        }
        return result;
    }

    // XML utility methods
    /**
     * Lookup an attribute from a DOM node.
     * @param n
     * @param name
     * @return
     */
    private String getAttributeValue(Node n, String name)
    {
        NamedNodeMap nm = n.getAttributes();
        for (int i = 0; i < nm.getLength(); i++) {
            Node node = nm.item(i);
            if (name.equals(node.getNodeName())) {
                return node.getNodeValue();
            }
        }
        return "";
    }
   
    /**
     * Return the String value of a Node.
     * @param node
     * @return
     */
    private String getStringValue(Node node) {
        String value = node.getNodeValue();

        if (node.hasChildNodes()) {
            Node first = node.getFirstChild();
            if (first.getNodeType() == Node.TEXT_NODE) {
                return first.getNodeValue();
            }
        }
        return value;
    }
}
