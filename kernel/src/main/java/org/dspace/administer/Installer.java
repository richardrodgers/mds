/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.administer;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import org.skife.jdbi.v2.DBI;
import org.skife.jdbi.v2.Handle;
import org.skife.jdbi.v2.util.IntegerMapper;
import org.skife.jdbi.v2.util.StringMapper;

import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Argument;

import static com.google.common.base.Preconditions.*;
import com.google.common.base.Strings;
import com.google.common.io.Files;

import org.dspace.browse.BrowseException;
import org.dspace.browse.IndexBrowse;
import org.dspace.core.ConfigurationManager;
import org.dspace.core.Context;
import org.dspace.core.I18nUtil;
import org.dspace.eperson.Group;
import org.dspace.search.DSIndexer;
import org.dspace.storage.rdbms.DatabaseManager;

/**
 * Contains methods for installing/deploying mds modules from built code
 * to a configured location. main() method is a command-line tool invoking same.
 * 
 * The Installer requires a very specific configuration to operate. There
 * is a single directory (the 'base') where the kernel module is present.
 * It contains a file declaring the maven coordinates of any dependent jars.
 * It is a simple text file ('deps.txt') in the format emitted by
 * the maven dependency plugin. It also must contain the maven
 * POM ('pom.xml') which is interrogated for module data.
 * 
 * It also can contain the subdirectories:
 * 
 *   bin
 *   conf
 *   db
 *   lib
 *   reg
 *   modules
 *   
 * The modules directory will contain any modules that are to be installed on
 * the kernel, and they can be added at any time after the kernel has been
 * installed. They can have arbitrary directory names and have the some
 * sub-structure as the kernel, except they will lack the modules subdirectory.
 * The directory name is used as the module name to install, update etc.
 * 
 * @author richardrodgers
 */
public final class Installer
{
	// expected directory for executables
	private static final String BIN_DIR = "bin";
	// expected directory for configuration
	private static final String CONF_DIR = "conf";
	// expected directory for DDL code
	private static final String DDL_DIR = "db";
	// expected name for DDL 'up' definition
	private static final String DDL_UPFILE = "database_schema.sql";
	// expected name for DDL 'down' definition
	private static final String DDL_DOWNFILE = "clean-database.sql";
	// expected directory for jars
	private static final String LIB_DIR = "lib";
	// expected directory for module source
	private static final String SRC_DIR = "src";
	// directory where modules reside
	private static final String MODULES_DIR = "modules";
	// expected directory for registry files
	private static final String REG_DIR = "reg";
	// expected name of module dependents list
	private static final String DEPS_FILE = "deps.txt";
	// maven pom file
	private static final String POM_FILE = "pom.xml";
	// maven build dir
	private static final String BUILD_DIR = "target";
	// list of content locations to exclude from installation
	private static final String[] exclusions = { DEPS_FILE, POM_FILE, DDL_DIR, SRC_DIR, BUILD_DIR, LIB_DIR, REG_DIR }; 
			
	private DSIndexer indexer = null;
	
	enum Action {install, update, remove, cleandb}
	
	@Argument(index=0, usage="action to take", required=true)
	private Action action;
		
	@Argument(index=1, usage="module name", required=true)
	private String module;
	
	// source/staging filesystem location
	private File baseDir;
	
	// coordinates of module being worked with
	private String groupId;
	private String artifactId;
	private String version;
	private String packaging = "jar";
	
    /**
     * For invoking via the command line.
     * 
     * @param args
     *            command-line arguments
     */
    public static void main(String[] args) throws Exception
    {
    	Installer installer = new Installer();
    	CmdLineParser parser = new CmdLineParser(installer);
        try {
        	parser.parseArgument(args);
        	installer.checkEnv();
        	installer.process();
        }  catch (CmdLineException clE) {
        	System.err.println(clE.getMessage());
        	parser.printUsage(System.err);
        } catch (Exception e) {
        	System.err.println(e.getMessage());
        }
    }
        
    public void checkEnv() throws Exception {
    	// check JRE version
    	checkState(System.getProperty("java.version").charAt(2) >= 6,
    		       "Java runtime below minimum required version: 1.6");
    	// make sure we are executing where we ought to be
    	baseDir = new File(System.getProperty("user.dir")).getParentFile();
    	checkState(new File(baseDir, "lib").isDirectory(),
    			  "Installer must be run from kernel 'bin' directory");
    	// and a pom is present
    	checkState(new File(baseDir, POM_FILE).exists(),
    		       "Module must possess a maven pom file");
    }
    
    public void process() throws BrowseException, IOException, SQLException, Exception {
    	Handle h = new DBI(DatabaseManager.getDataSource()).open();
    	try {
    		if (action.equals(Action.install)) {
    			install(h, module);
    		} else if (action.equals(Action.cleandb)) {
    	    	File modRoot = baseDir;
    	    	if (! "kernel".equals(module)) {
    	    		modRoot = new File(baseDir, MODULES_DIR + File.separator + module);
    	    	}
    			cleanDB(h, modRoot);
    		}
    	} finally {
    		if (h != null) {
    			h.close();
    		}
    	}
    }
    
    private void install(Handle h, String module) throws BrowseException, IOException, SQLException, Exception {

    	// Determine whether this module has already been installed
    	// or if not, whether we *can* install it.
    	File modRoot = baseDir;
    	if (! "kernel".equals(module)) {
    		modRoot = new File(baseDir, MODULES_DIR + File.separator + module);
    	}
    	// first read pom to see what we are working with
    	readPOM(modRoot);
    	List<List<String>> components = readDependencies(modRoot, module);
    	
    	// first component is the module itself - did we read from POM successfully?
    	checkState(artifactId != null, "Bad POM file - unable to process");
    	
    	// kernel is a special case as first module - initialize DB if not already done
    	boolean dbReady = dbInitialized(h);
    	if ("kernel".equals(module) && ! dbReady) {
    		initDB(h);
    	}
    	
    	// Query the deployed system for this module
    	String installed = h.createQuery("SELECT artifact_id FROM installation WHERE component_type = 0 and artifact_id = :aid").
    			             bind("aid", artifactId).
    			             map(StringMapper.FIRST).first();
    	
    	if (installed != null) {
    		throw new IOException("Module: '" + artifactId + "' already installed");
    	}
    	
    	// OK, now determine if the installation would create any classpath conflicts
    	for (List<String> cparts : components) {
        	// Query the deployed system for this component
        	String version = h.createQuery("SELECT version_str FROM installation WHERE group_id = :gid AND artifact_id = :aid").
        			             bind("gid", cparts.get(0)).bind("aid", cparts.get(1)).
        			             map(StringMapper.FIRST).first();
        	if (version != null) {
        		// do versions conflict?
        		if (! version.equals(cparts.get(3))) {
        			throw new IOException("Module dependency: '" + cparts.get(1) + "' conflicts with an existing component");
        		} else {
        			// notate that we can skip installing this jar - it's already there
        			cparts.add("skip");
        		}
        	} else {
        		cparts.add("install");
        	}
    	}
    	 			
    	String destPath = ConfigurationManager.getProperty("dspace.dir");
    	File destFile = new File(destPath);
    	if ("kernel".equals(module)) {
    		// create destination directory if it doesn't exist
    		if (! destFile.isDirectory()) {
    			if (! destFile.exists()) {
    				destFile.mkdirs();
    			}
    		}
    	}
    	
    	// first install the module jar itself - this is a special case,
    	// since we check for locally modified version if available
    	File libSrcDir = new File(modRoot, LIB_DIR);
    	File buildDir = new File(modRoot, BUILD_DIR);
    	File modJar = null;
    	if (buildDir.isDirectory()) {
    		modJar = new File(buildDir, artifactId + "-" + version + ".jar");
    	}
    	// use packaged version otherwise
    	if (modJar == null || ! modJar.exists()) {
    		if (libSrcDir.isDirectory()) {
    			modJar = new File(libSrcDir, artifactId + "-" + version + ".jar");
    		}
    	}
    	File libDestDir = new File(destFile, LIB_DIR);
    	libDestDir.mkdir();
    	safeCopy(modJar, libDestDir);
    	// next, update the installation data with module
    	h.execute("INSERT INTO installation (component_id, component_type, group_id, artifact_id, version_str, ref_count, updated) " +
    	          "VALUES (nextval('installation_seq'), ?, ?, ?, ?, ?, ?)",
    			  0, groupId, artifactId, version, 1, new Timestamp(System.currentTimeMillis()));
    	// now process module resources
    	List<String> excludes = Arrays.asList(exclusions);
    	for (File file : modRoot.listFiles()) {
    		if (! excludes.contains(file.getName()) && file.isDirectory()) {
    			safeCopy(file, destFile);
    		}
    	}
    	
    	// OK now dependent install jars that aren't already there, updating the reference count in any case
    	for (List<String> cparts : components) {
    		String grpId = cparts.get(0);
    		String artId = cparts.get(1);
    		String status = cparts.get(5);
    		if ("skip".equals(status)) {
    			// just update reference count
            	int count = h.createQuery("SELECT ref_count FROM installation WHERE group_id = :gid AND artifact_id = :aid").
          	             				 bind("gid", grpId).bind("aid", artId).
          	             				 map(IntegerMapper.FIRST).first();
    			h.execute("UPDATE installation SET ref_count = :rc WHERE artifact_id = :aid", count + 1, artId);
    		} else {
    			// copy jar to lib & add to installation table
    			File jarFile = new File(libSrcDir, cparts.get(1) + "-" + cparts.get(3) + ".jar");
    			safeCopy(jarFile, libDestDir);
    	    	h.execute("INSERT INTO installation (component_id, component_type, group_id, artifact_id, version_str, ref_count, updated) " +
    	    	          "VALUES (nextval('installation_seq'), ?, ?, ?, ?, ?, ?)",
    	    			  1, grpId, artId, cparts.get(3), 1, new Timestamp(System.currentTimeMillis()));
    		}
    	}
    	System.out.println("Copied components");
    	loadDDL(modRoot);
    	// special initialization in kernel module
    	if ("kernel".equals(module)) {
    		// create system-required groups   
    		Context ctx = null;
    		try {
    			ctx = new Context();
    			ctx.turnOffAuthorisationSystem();
    			Group anon = Group.create(ctx);
    			anon.setName("Anonymous");
    			anon.update();
    			
    			Group admin = Group.create(ctx);
    			admin.setName("Administrator");
    			admin.update();
    		} catch (Exception e) {
    			System.out.println("Exception: " + e.getMessage());
    		} finally {
    			if (ctx != null) {
    				try {
    					ctx.complete();
    				} catch (Exception e) {System.out.println("Complete Exception: " + e.getMessage());}
    			}
    		}
    	}
    	//initBrowse();
    	// now load registry data into DB
    	loadRegistries(modRoot);
    	//initIndexes();
    }
    
    private void safeCopy(File src, File dest) throws IOException {
    	File destFile = new File(dest, src.getName());
    	if (src.isDirectory()) {
    		destFile.mkdir();
    		for (File file : src.listFiles()) {
    			safeCopy(file, destFile);
    		}
    	} else {
    		if (src.exists() && ! destFile.exists()) {
    			Files.copy(src, destFile);
    		} else {
    			System.out.println("Error - expected file to be unique: " + destFile.getName());
    		}
    	}
    }
    
    private void readPOM(File base) throws IOException, ParserConfigurationException,
    	SAXException, XPathExpressionException {
    	File pomFile = new File(base, POM_FILE);
    	if (pomFile.exists()) {
    		DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
    		Document pomDoc = builder.parse(pomFile);
    		// All we currently need is module coordinates
    		XPath xpath = XPathFactory.newInstance().newXPath();   		
    		groupId = findPomValue(pomDoc, xpath.compile("/project/groupId/text()"));
    		artifactId = findPomValue(pomDoc, xpath.compile("/project/artifactId/text()"));
    		version = findPomValue(pomDoc, xpath.compile("/project/version/text()"));
    		// packaging defaults to jar so this call may fail
    		packaging = findPomValue(pomDoc, xpath.compile("/project/packaging/text()"));
    		if (packaging == null) {
    			packaging = "jar";
    		}
    	} else {
    		System.out.println("No POM file at expected location: " + pomFile.getAbsolutePath());
    	}
    }
    
    private String findPomValue(Document doc, XPathExpression expr) throws XPathExpressionException {
	    NodeList nodes = (NodeList)expr.evaluate(doc, XPathConstants.NODESET);
	    if (nodes.getLength() > 0) {
	    	return nodes.item(0).getNodeValue();
	    }
	    return null;
    }
    
    private List<List<String>> readDependencies(File root, String module) throws IOException {
    	List<List<String>> compList = new ArrayList<List<String>>();
    	File comps = new File(root, DEPS_FILE);

    	BufferedReader reader = new BufferedReader(new FileReader(comps));
    	String lineIn = null;
    	while ((lineIn = reader.readLine()) != null) {
    		lineIn = lineIn.trim();
    		if (lineIn.length() > 0 && ! lineIn.startsWith("The")) {
    			List<String> parts = new ArrayList(Arrays.asList(lineIn.split(":")));
    			compList.add(parts);
    		}
    	}
    	reader.close();
    	return compList;
    }
    
    private void cleanDB(Handle h, File modRoot) throws BrowseException, IOException, SQLException {
    	clearBrowse();
    	unloadDDL(modRoot);
    }
    
    private boolean dbInitialized(Handle h) throws SQLException {
   	 	DatabaseMetaData md = h.getConnection().getMetaData();
   	 	return md.getTables(null, null, "installation", null).next();
    }
    
    private void initDB(Handle h) throws IOException, SQLException {
   	 	checkState(ConfigurationManager.getProperty("db.password") != null, "no database password defined");
   	 	// the only module-independent data to be 'bootstrapped' is in the installation
   	 	// table itself: everything else will be loaded when a module is installed
   	 	h.execute("CREATE SEQUENCE installation_seq");
   	 	h.execute("CREATE TABLE installation (" +
   	 	          "component_id INTEGER PRIMARY KEY, " +
   	 			  "component_type INTEGER, " +
   	 	          "group_id VARCHAR, " +
   	 			  "artifact_id VARCHAR, " +
   	 	          "version_str VARCHAR, " +
   	 			  "ref_count INTEGER, " +
   	 	          "updated TIMESTAMP)");
    	//h.execute("CREATE USER dspace WITH CREATEDB PASSWORD '" + dbPassword + "';");
    	//h.execute("CREATE DATABASE dspace ENCODING UTF8 OWNER dspace;");
    }
    
    private void loadDDL(File base) throws IOException, SQLException {
    	 String dbName = ConfigurationManager.getProperty("db.name");
    	 checkState(dbName != null, "no database name defined");

    	 String path = base.getAbsolutePath() + File.separator + DDL_DIR + File.separator + dbName;
    	 File ddlFile = new File(path, DDL_UPFILE);
    	 // not all modules have DDLs
    	 //checkState(ddlFile.exists(), "no DDL file present");
    	 if (ddlFile.exists()) {
    		 DatabaseManager.loadSql(new FileReader(ddlFile.getCanonicalPath()));
    	 }
    }
    
    private void unloadDDL(File base) throws IOException, SQLException {
   	    String dbName = ConfigurationManager.getProperty("db.name");
   	    checkState(dbName != null, "no database name defined");

    	String path = base.getAbsolutePath() + File.separator + DDL_DIR + File.separator + dbName;
    	File ddlFile = new File(path, DDL_DOWNFILE);
    	//checkState(ddlFile.exists(), "no DDL file present");
    	if (ddlFile.exists()) {
    		DatabaseManager.loadSql(new FileReader(ddlFile.getCanonicalPath()));
    	}
    }
    
    private void initBrowse() throws BrowseException, SQLException {
    	 IndexBrowse browse = new IndexBrowse();
         browse.setRebuild(true);
         browse.setExecute(true);
         browse.initBrowse();
    }
    
    private void clearBrowse() throws BrowseException, SQLException {
        IndexBrowse browse = new IndexBrowse();
        browse.setDelete(true);
        browse.setExecute(true);
        browse.clearDatabase();
    }
    
    private void loadRegistries(File base) throws Exception {
   	 	File regDir = new File(base, REG_DIR);
   	 	if (regDir.isDirectory()) {
   	 		Context context = null;
   	 		try {
   	 			context = new Context();
   	 			context.turnOffAuthorisationSystem();
   	 			for (File regFile : regDir.listFiles()) {
   	 				RegistryLoader.loadRegistryFile(context, regFile.getAbsolutePath());
   	 			}
   	 		} finally {
   	 			if (context != null) {
   	 				context.complete();
   	 			}
   	 		}
   	 	}
    }
    
    private void initIndexes() throws Exception {
	 	Context context = null;
   	 	try {
   	 		context = new Context();
   	 		indexer = new DSIndexer();
   	 		indexer.createIndex(context);
	 	} finally {
   	 		if (context != null) {
   	 			context.complete();
   	 		}
   	 	}
    }
}
