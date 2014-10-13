/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.administer;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.FileInputStream;
import java.net.URI;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Paths;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import static java.util.Arrays.*;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Scanner;

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

import org.skife.jdbi.v2.Handle;
import org.skife.jdbi.v2.BeanMapper;
import org.skife.jdbi.v2.util.StringMapper;

import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.Option;

import static com.google.common.base.Preconditions.*;
import com.google.common.base.Strings;

import org.dspace.core.ConfigurationManager;
import org.dspace.core.Context;
import org.dspace.core.Email;
import org.dspace.core.I18nUtil;
import org.dspace.eperson.Group;
import org.dspace.search.DSIndexer;
import org.dspace.storage.rdbms.DatabaseManager;

/**
 * Contains methods for installing/updating mds modules from built code
 * to a configured location. main() method is a command-line tool invoking same.
 * 
 * The Installer requires a very specific file and directory structure to operate.
 * There is a single directory (the 'base') where the kernel module is present.
 * It contains a file declaring the maven coordinates of any dependent jars.
 * This is a simple text file ('deps.txt') in the format emitted by
 * the maven dependency plugin. The base also must contain the maven POM
 * ('pom.xml') which is interrogated for module data.
 * 
 * The base also can contain any/all of the subdirectories: 
 *   'bin' - containing any command-line scripts for the module,
 *   'db' - containing any DDL for the module
 *   'conf' - containing any modular '.cfg' files, or others
 *   'lib' - containing jar files
 *   'reg' - containg registry files (in XML) to be loaded into registries
 *   'modules' - contains module tree subdirectories with this structure
 *   
 * The modules directory will contain any modules that are to be installed on
 * the kernel, and they can be added at any time after the kernel has been
 * installed. They can have arbitrary directory names and have the same
 * sub-structure as the kernel, except they will lack a 'modules' subdirectory.
 * The directory name is used as the module name to install, update etc.
 * Another subdirectory - 'webapps' will contain any deployable wars.
 *
 * The specific actions performed by the installer are: 'install' a module - 
 * which means copying the necessary files to the target runtime environment
 * (which will be a directory, or a WAR, depending on the type of module), and adding
 * any schema changes and data to the database instance. Alternately, these operations
 * can be performed using a combination of the 'stage' and 'register' actions. Here one would
 * 'stage' a set of modules (which establish a runtime environment), then 'register' that
 * whole environment with an mds instance. It will fail if any of the modules in the environment
 * conflict with the existing instance. Roughly then, 'install' = 'stage' + 'register'.
 * Generally, 'install' should be preferred for a typical local installation,
 * since it can examine the database prior to each operation to ensure its validity
 * Stage/register is used when the runtime environment needs to be constructed independently,
 * for example in a Docker container. The 'update' action will act very much
 * like 'install', except that the module is assumed to be present. And again, updates can
 * can be alternatively managed via a set of 'stage' actions with the updated modules,
 * followed by a 'register' to the instance.
 * Finally, a 'cleandb' action will purge the instance database.
 *
 * These action names are wired into the CommandLauncher as 'builtin' commands - so
 * one would typically invoke the Installer using the 'dspace' script:
 *   ./dspace install kernel
 * 
 * @author richardrodgers
 */
public final class Installer {
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
    // directory where reassembled WAR files reside
    private static final String WEBAPPS_DIR = "webapps";
    // webapps sub-directory where source wars reside
    private static final String WARS_DIR = WEBAPPS_DIR + File.separator + "wars";
    // webapps sub-directory where copies of core jars reside (added to webapps)
    private static final String JARS_DIR = WEBAPPS_DIR + File.separator + "jars";
    // webapps sub-directory where deplyable wars reside
    private static final String DEPLOY_DIR = WEBAPPS_DIR + File.separator + "deploy";
    // expected directory for registry files
    private static final String REG_DIR = "reg";
    // expected directory for email template files
    private static final String EMAIL_DIR = REG_DIR + File.separator + "emails";
    // expected name of module dependents list
    private static final String DEPS_FILE = "deps.txt";
    // maven pom file
    private static final String POM_FILE = "pom.xml";
    // staged data file 
    private static final String STAGED_FILE = "staged.csv";
    // maven build dir
    private static final String BUILD_DIR = "target";
    // list of content locations to exclude from installation
    private static final String[] exclusions = { DEPS_FILE, POM_FILE, DDL_DIR, SRC_DIR, BUILD_DIR, LIB_DIR, REG_DIR, MODULES_DIR, WEBAPPS_DIR, JARS_DIR }; 
    // list of runtime resource directories
    private static final String[] rtResources = { CONF_DIR };

    private DSIndexer indexer = null;

    enum Action {stage, register, install, update, cleandb}
    @Argument(index=0, usage="action to take", required=true)
    private Action action;

    @Argument(index=1, usage="module name", required=true)
    private String module;

    @Option(name="-s", usage="staging directory")
    private String stagingDir;

    @Option(name="-r", usage="runtime directory")
    private String runtimeDir;

    // source/staging filesystem locations
    private File kernelDir;
    private File baseDir;
    // maven coordinates of module being worked on
    private String groupId;
    private String artifactId;
    private String version;
    private String packaging;
    // module functional scope
    private String modScope;
    // path to deploy webapps to
    private String deployAs;
    // map of staged components
    private Map<String, Component> stagedMap = new LinkedHashMap<>();

    public Installer() {}

    public Installer(String module) {
        this.module = module;
    }

    /**
     * For invoking via the command line.
     * 
     * @param args
     *            command-line arguments
     */
    public static void main(String[] args) throws Exception {
        Installer installer = new Installer();
        CmdLineParser parser = new CmdLineParser(installer);
        try {
            parser.parseArgument(args);
            installer.checkEnv();
            installer.process();
        } catch (CmdLineException clE) {
            System.err.println(clE.getMessage());
            parser.printUsage(System.err);
        } catch (Exception e) {
            System.err.println(e.getMessage());
        }
    }

    public static List<String> listModules() {
        File kernelDir = /* (stagingDir != null) ? new File(stagingDir) : */ new File(System.getProperty("user.dir")).getParentFile();
        checkState(new File(kernelDir, "lib").isDirectory(),
                  "Installer must be run from kernel 'bin' directory");
        List<String> modules = new ArrayList<>();
        modules.add("kernel");
        File modulesDir =  new File(kernelDir, MODULES_DIR);
        for (File modDir : modulesDir.listFiles()) {
            if (modDir.isDirectory()) {
                modules.add(modDir.getName());
            }
        }
        return modules;
    }

    private void setBaseDir() {
        // make sure we are executing where we ought to be
        kernelDir = (stagingDir != null) ? new File(stagingDir) : new File(System.getProperty("user.dir")).getParentFile();
        if ("kernel".equals(module)) {
             baseDir = kernelDir;
        } else {
            baseDir = new File(baseDir, MODULES_DIR + File.separator + module);
        }
    }

    public String status(Handle h) throws IOException {
        // determine whether staged module has been modified more recently
        // than the module update timestamp in DB
        setBaseDir();
        readPOM();
        Component comp = Component.findByCoordinates(h, groupId, artifactId);
        if (comp != null) {
            File modBase = new File(System.getProperty("user.dir")).getParentFile();
            if (! "kernel".equals(module)) {
                modBase =  new File(modBase, MODULES_DIR);
            }
            if (comp.getUpdated().getTime() >= latest(modBase.listFiles())) {
                return "current";
            } else {
                String canUpdate = canInstall(h);
                return (canUpdate == null) ? "update" : "conflict: " + canUpdate;
            }
        }
        String canInstall = canInstall(h);
        return (canInstall == null) ? "install" : "conflict: " + canInstall;
    }

    private long latest1(List<File> files) {
        if (files.isEmpty()) return 0L;
        else if (files.size() == 1) {
            File file = files.get(0);
            return (file.isDirectory()) ? latest1(asList(file.listFiles())) : file.lastModified();
        } else {
            return Math.max(latest1(files.subList(0, 1)), latest1(files.subList(1, files.size())));
        }
    }

    private long latest(File[] files) {
        if (files.length == 0) return 0L;
        else if (files.length == 1) {
            return (files[0].isDirectory()) ? latest(files[0].listFiles()) : files[0].lastModified();
        } else {
            return Math.max(latest(copyOfRange(files, 0, 1)), latest(copyOfRange(files, 1, files.length)));
        }
    }
        
    public void checkEnv() throws Exception {
        // check JRE version
        checkState(System.getProperty("java.version").charAt(2) >= 7,
                   "Installed Java runtime below minimum required version: 1.7");
        // make sure we are executing where we ought to be
        baseDir = (stagingDir != null) ? new File(stagingDir) : new File(System.getProperty("user.dir")).getParentFile();
        checkState(new File(baseDir, "lib").isDirectory(),
                  "Installer must be run from kernel 'bin' directory");
        // and a pom is present
        checkState(new File(baseDir, POM_FILE).exists(),
                  "Module must possess a maven POM file");
    }
    
    public void process() throws IOException, SQLException, Exception {
        if (action.equals(Action.stage)) {
            // no DB connection needed
            stage();
        } else {
            try (Handle h = DatabaseManager.getHandle()) {
                switch (action) {
                    case install: install(h); break;
                    case register: register(h); break;
                    case update: update(h); break;
                    case cleandb: cleanDB(h); break;
                    default: break;
                }
            }
        }
    }

    public String canInstall(Handle h) throws IOException {
        // Determine whether the installation would create any classpath conflicts
        //setBaseDir();
        //readPOM();
        //if (Component.findByCoordinates(h, groupId, artifactId) != null) {
        //    return  "Module: '" + artifactId + "' already installed";
        //}
        List<List<String>> components = getDependencyActions(h, baseDir.toPath(), false);
        for (List<String> comp : components) {
            if ("fail".equals(comp.get(5))) {
                return comp.get(6);
            }
        }
        return null;
    }

    private void init(String action) throws IOException {
        // reset base if not kernel
        setBaseDir();
        // read module POM so we know what we are dealing with
        readPOM();
        // make sure module obeys the naming convention
        checkState(artifactId.startsWith("dsm"),
                   "Cannot " + action + " module: " + module + " improperly named");
        checkState("jar".equals(packaging) || "war".equals(packaging),
                   "Cannot " + action + " module: " + module + " not a valid dspace module");
    }

    public void stage() throws IOException, Exception {
        init("stage");
        // load up map of staging info
        loadStagingInfo();
        // Determine whether this module has already been staged
        checkState(stagedMap.get(groupId + artifactId) == null,
                   "Module: '" + artifactId + "' already installed");
        System.out.println("Start dependency check");
        // Determine whether the installation would create any classpath conflicts
        List<List<String>> components = getStagingDependencyActions(baseDir.toPath());
        for (List<String> comp : components) {
            if ("fail".equals(comp.get(5))) {
                throw new IOException(comp.get(6));
            }
        }
        System.out.println("Finished dependency check");
        String destPath = (runtimeDir != null) ? runtimeDir : ConfigurationManager.getProperty("site.home");
        File destFile = new File(destPath);
        if ("kernel".equals(module)) {
            // create destination directory if it doesn't exist
            if (! destFile.isDirectory()) {
                if (! destFile.exists()) {
                    destFile.mkdirs();
                }
            }
            createStagingEnv();
        }
        File modJar = getModuleArtifact();
        stageModuleJar(modJar, destFile);
        // next, update staging data with module
        stagedMap.put(groupId + artifactId, new Component(stagedMap.size(), 0, groupId, artifactId, version, checksum(modJar), "self"));

        // now process module resources
        List<String> excludes = asList(exclusions);
        for (File file : baseDir.listFiles()) {
            if (! excludes.contains(file.getName()) && file.isDirectory()) {
                // also exclude war config info - will remain in war classpath
                if (! (file.getName().equals(CONF_DIR) && "war".equals(packaging))) {
                    safeCopy(file, destFile, false);
                }
            }
        }
        // if a WAR module, stop after this step - dependent jars need not be added to the classpath,
        // since they will be used only in the container classpath
        if ("war".equals(packaging)) {
            String warName = artifactId + "-" + version + ".war";
            File srcLib = new File(baseDir, LIB_DIR);
            File srcFile = new File(srcLib, warName);
            File targLib = new File(kernelDir, WARS_DIR);
            File targFile = new File(targLib, deployAs + "-" + warName);
            Files.copy(srcFile.toPath(), targFile.toPath());
            rebuildWar(artifactId, version);
            saveStagingInfo();
            return;
        }

        // Install dependent jars that aren't already there, updating their reference graph in any case
        Component comp = stagedMap.get(groupId + artifactId);
        File libSrcDir = new File(baseDir, LIB_DIR);
        File libDestDir = new File(destFile, LIB_DIR);
        for (List<String> cparts : components) {
            String grpId = cparts.get(0);
            String artId = cparts.get(1);
            String vsn = cparts.get(3);
            String status = cparts.get(5);
            if ("count".equals(status)) {
                // just update reference graph
                Component updComp = stagedMap.get(grpId + artId);
                if (updComp != null) {
                    String graph = updComp.getGraph();
                    updComp.setGraph(graph + "-" + String.valueOf(comp.getCompId()));
                    //updComp.updateReferenceGraph(h, comp.getCompId());
                }
            } else if ("install".equals(status)) {
                // copy jar to lib & add to installation table
                File jarFile = new File(libSrcDir, artId + "-" + vsn + ".jar");
                safeCopy(jarFile, libDestDir, false);
                // also copy to (staging) jars directory for adding to webapps if core
                if ("core".equals(modScope)) {
                    safeCopy(jarFile, new File(kernelDir, JARS_DIR), false);
                }
                stagedMap.put(grpId + artId, new Component(stagedMap.size(), 1, grpId, artId, vsn, checksum(jarFile), String.valueOf(comp.getCompId())));
                /*
                h.execute("INSERT INTO installation (compid, comptype, groupid, artifactid, versionstr, checksum, graph, updated) " +
                          "VALUES (nextval('installation_seq'), ?, ?, ?, ?, ?, ?, ?)",
                          1, grpId, artId, vsn, checksum(jarFile), String.valueOf(comp.getCompId()), new Timestamp(System.currentTimeMillis()));
                */
            }
        }
        System.out.println("Copied components");
        // OK - if this is a 'core'-scoped module, we need to regenerate all webapp modules, since they
        // have new potential dependencies. Add any new dependencies to the war & republish
        if ("core".equals(modScope)) {
            rebuildWars();
        }
        saveStagingInfo();
    }

    public void register(Handle h) throws IOException, SQLException, Exception {
        // make sure we are executing where we ought to be
        kernelDir = (stagingDir != null) ? new File(stagingDir) : new File(System.getProperty("user.dir")).getParentFile();
        baseDir = kernelDir;
        // load up map of staging info
        loadStagingInfo();
        // kernel is a special case as it has to be the first module installed 
        // - initialize DB if not already done
        if (! dbInitialized(h)) {
            if (stagedMap.containsKey("org.dspacedsm-kernel")) {
                initDB(h);
            } else {
                throw new IOException("Module 'kernel' must be staged");
            }
        }
        // OK - for each module staged, consult the instance DB to determine:
        // (1) if not present in DB, then it a new registration - ensure it is compatible - if so add it
        // (2) if present in DB, and same version, skip it
        // (3) if present in DB and different version, then upgrade - ensure it is compatible - if so, ugrade
        // Since the registration is an atomic operation (the whole set of staged modules are accepted or rejected),
        // we perform the dependency checks on *all* modules up front, then iterate over them again to register each one
        System.out.println("Start dependency checks");
        for (Component scomp : stagedMap.values()) {
            if (scomp.getCompType() == 0) {
                // it's a module (not a module dependency)
                Component icomp = Component.findByCoordinates(h, scomp.getGroupId(), scomp.getArtifactId());
                if (icomp == null) {
                    // Determine whether the installation would create any classpath conflicts
                    Path refDir = getModuleDir(scomp);
                    List<List<String>> components = getDependencyActions(h, refDir, true);
                    for (List<String> comp : components) {
                        if ("fail".equals(comp.get(5))) {
                            throw new IOException(comp.get(6));
                        }
                    }
                } else {
                    // check version
                    if (! scomp.getVersionStr().equals(icomp.getVersionStr())) {
                        // if version is newer, its an upgrade
                        // RLR TODO
                    }
                } 
            }
        }
        System.out.println("Finished dependency checks");
        for (Component scomp : stagedMap.values()) {
            if (scomp.getCompType() == 0) {
                // it's a module
                Component icomp = Component.findByCoordinates(h, scomp.getGroupId(), scomp.getArtifactId());
                if (icomp == null) {
                    // Cool - proceed to record this module and its dependencies and load its resources to DB
                    System.out.println("About to insert - g: " + scomp.getGroupId() + " a: " + scomp.getArtifactId() + 
                                       " v: " + scomp.getVersionStr() + " c: " + scomp.getChecksum());
                    h.execute("INSERT INTO installation (compid, comptype, groupid, artifactid, versionstr, checksum, graph, updated) " +
                              "VALUES (nextval('installation_seq'), ?, ?, ?, ?, ?, ?, ?)",
                              0, scomp.getGroupId(), scomp.getArtifactId(), scomp.getVersionStr(), scomp.getChecksum(), "self",
                              new Timestamp(System.currentTimeMillis()));
                    // Install dependent jars that aren't already there, updating their reference graph in any case
                    Path refDir = getModuleDir(scomp);
                    List<List<String>> components = getDependencyActions(h, refDir, false);
                    Component comp = Component.findByCoordinates(h, scomp.getGroupId(), scomp.getArtifactId());
                    for (List<String> cparts : components) {
                        String grpId = cparts.get(0);
                        String artId = cparts.get(1);
                        String vsn = cparts.get(3);
                        String status = cparts.get(5);
                        if ("count".equals(status)) {
                            // just update reference graph
                            Component updComp = Component.findByCoordinates(h, grpId, artId);
                            if (updComp != null) {
                                updComp.updateReferenceGraph(h, comp.getCompId());
                            }
                        } else if ("install".equals(status)) {
                            Component stagedDep = stagedMap.get(grpId + artId);
                            h.execute("INSERT INTO installation (compid, comptype, groupid, artifactid, versionstr, checksum, graph, updated) " +
                           "VALUES (nextval('installation_seq'), ?, ?, ?, ?, ?, ?, ?)",
                           1, grpId, artId, vsn, stagedDep.getChecksum(), String.valueOf(comp.getCompId()), new Timestamp(System.currentTimeMillis()));
                        }
                    }
                    System.out.println("Copied components");
                    loadModuleResources(refDir);
                } else {
                    // check version
                    if (! scomp.getVersionStr().equals(icomp.getVersionStr())) {
                        // if version is newer, its an upgrade
                    } 
                }
            }
        }
    }
    
    public void install(Handle h) throws IOException, SQLException, Exception {
        init("install");
        // kernel is a special case as it has to be the first module installed 
        // - initialize DB if not already done
        if (! dbInitialized(h)) {
            if ("kernel".equals(module)) {
                initDB(h);
            } else {
                throw new IOException("Module 'kernel' must be installed first");
            }
        }
        // Determine whether this module has already been installed
        checkState(Component.findByCoordinates(h, groupId, artifactId) == null,
                   "Module: '" + artifactId + "' already installed");
        System.out.println("Start dependency check");
        // Determine whether the installation would create any classpath conflicts
        List<List<String>> components = getDependencyActions(h, baseDir.toPath(), false);
        for (List<String> comp : components) {
            if ("fail".equals(comp.get(5))) {
                throw new IOException(comp.get(6));
            }
        }
        System.out.println("Finished dependency check");
        String destPath = (runtimeDir != null) ? runtimeDir : ConfigurationManager.getProperty("site.home");
        File destFile = new File(destPath);
        if ("kernel".equals(module)) {
            // create destination directory if it doesn't exist
            if (! destFile.isDirectory()) {
                if (! destFile.exists()) {
                    destFile.mkdirs();
                }
            }
            // also create asset store dir if necessary, so we can query storage
            String storePath = ConfigurationManager.getProperty("assetstore.dir");
            File storeDir = new File(storePath);
            if (! storeDir.isDirectory()) {
                if (! storeDir.exists()) {
                    storeDir.mkdirs();
                }
            }
            createStagingEnv();
        }
    
        // first install the module jar itself - this is a special case,
        // since we check for locally modified version if available
        File modJar = getModuleArtifact();
        stageModuleJar(modJar, destFile);
        /*
        File modJar = getModuleArtifact();
        if ("jar".equals(packaging)) {
            safeCopy(modJar, new File(destFile, LIB_DIR), false);
            // also copy to (staging) jars directory for adding to webapps if core
            if ("core".equals(modScope)) {
                safeCopy(modJar, new File(kernelDir, JARS_DIR), false);
            }
        }
        */
        System.out.println("About to insert - g: " + groupId + " a: " + artifactId + " v: " + version + " c: " + checksum(modJar));
        // next, update the installation data with module
        h.execute("INSERT INTO installation (compid, comptype, groupid, artifactid, versionstr, checksum, graph, updated) " +
                  "VALUES (nextval('installation_seq'), ?, ?, ?, ?, ?, ?, ?)",
                  0, groupId, artifactId, version, checksum(modJar), "self", new Timestamp(System.currentTimeMillis()));
        // update this new component with ref_graph data
        //updateReferenceGraph(h, groupId, artifactId, -1);
    
        // now process module resources
        List<String> excludes = asList(exclusions);
        for (File file : baseDir.listFiles()) {
            if (! excludes.contains(file.getName()) && file.isDirectory()) {
                // also exclude war config info - will remain in war classpath
                if (! (file.getName().equals(CONF_DIR) && "war".equals(packaging))) {
                    safeCopy(file, destFile, false);
                }
            }
        }
    
        // if a WAR module, stop after this step - dependent jars need not be added to the classpath,
        // since they will be used only in the container classpath
        if ("war".equals(packaging)) {
            String warName = artifactId + "-" + version + ".war";
            File srcLib = new File(baseDir, LIB_DIR);
            File srcFile = new File(srcLib, warName);
            File targLib = new File(kernelDir, WARS_DIR);
            File targFile = new File(targLib, deployAs + "-" + warName);
            Files.copy(srcFile.toPath(), targFile.toPath());
            rebuildWar(artifactId, version);
            return;
        }
    
        // Install dependent jars that aren't already there, updating their reference graph in any case
        Component comp = Component.findByCoordinates(h, groupId, artifactId);
        File libSrcDir = new File(baseDir, LIB_DIR);
        File libDestDir = new File(destFile, LIB_DIR);
        for (List<String> cparts : components) {
            String grpId = cparts.get(0);
            String artId = cparts.get(1);
            String vsn = cparts.get(3);
            String status = cparts.get(5);
            if ("count".equals(status)) {
                // just update reference graph
                Component updComp = Component.findByCoordinates(h, grpId, artId);
                if (updComp != null) {
                    updComp.updateReferenceGraph(h, comp.getCompId());
                }
            } else if ("install".equals(status)) {
                // copy jar to lib & add to installation table
                File jarFile = new File(libSrcDir, artId + "-" + vsn + ".jar");
                safeCopy(jarFile, libDestDir, false);
                // also copy to (staging) jars directory for adding to webapps if core
                if ("core".equals(modScope)) {
                    safeCopy(jarFile, new File(kernelDir, JARS_DIR), false);
                }
                h.execute("INSERT INTO installation (compid, comptype, groupid, artifactid, versionstr, checksum, graph, updated) " +
                          "VALUES (nextval('installation_seq'), ?, ?, ?, ?, ?, ?, ?)",
                          1, grpId, artId, vsn, checksum(jarFile), String.valueOf(comp.getCompId()), new Timestamp(System.currentTimeMillis()));
            }
        }
        System.out.println("Copied components");
        // OK - if this is a 'core'-scoped module, we need to regenerate all webapp modules, since they
        // have new potential dependencies. Add any new dependencies to the war & republish
        if ("core".equals(modScope)) {
            rebuildWars();
        }
        // update database schema, and load any module data into DB
        loadModuleResources(baseDir.toPath());
        //loadDDL();
        // special initialization in kernel module
        if ("kernel".equals(module)) {
            // create system-required groups   
            try (Context ctx = new Context()) {
                ctx.turnOffAuthorisationSystem();
                Group anon = Group.create(ctx);
                anon.setName("Anonymous");
                anon.update();
    
                Group admin = Group.create(ctx);
                admin.setName("Administrator");
                admin.update();
                ctx.complete();
            } catch (Exception e) {
                System.out.println("Exception: " + e.getMessage());
            }
            initIndexes();
        }
    }
    
    public void update(Handle h) throws IOException, SQLException, Exception {
        init("update");
        checkState(dbInitialized(h), "No kernel module present - install one");
        Component curComp = Component.findByCoordinates(h, groupId, artifactId);
        checkState(curComp != null, "Module: '" + artifactId + "' is not installed - cannot update");
    
        boolean updateWars = false;
        String destPath = (runtimeDir != null) ? runtimeDir : ConfigurationManager.getProperty("site.home");
        File destFile = new File(destPath);
        File libDestDir = new File(destFile, LIB_DIR);
      
        if (curComp.getVersionStr().equals(version)) {
            // if the version is the same, the *only* code change we permit
            // is a difference in the module jar itself (if not a war module)
            if ("jar".equals(packaging)) {
                File modJar = getModuleArtifact();
                String checksum = checksum(modJar);
                if (! checksum.equals(curComp.getChecksum())) {
                    safeCopy(modJar, libDestDir, true);
                    // update DB to reflect this change
                    curComp.updateChecksum(h, checksum);
                    // also copy to (staging) jars directory for adding to webapps if core
                    if ("core".equals(modScope)) {
                        safeCopy(modJar, new File(kernelDir, JARS_DIR), true);
                        updateWars = true;
                    }
                }
            }
        } else {
            // a version change, need to check everything
            // Determine whether the update would create any classpath conflicts
            List<List<String>> components = getDependencyActions(h, baseDir.toPath(), false);
            for (List<String> comp : components) {
                if ("fail".equals(comp.get(5))) {
                    throw new IOException(comp.get(6));
                }
            }
        
            // Next step, remove any modules that are no longer needed
        	for (Component comp : Component.findAll(h)) {
        		String graph = comp.getGraph();
        		// we only need to examine potentially orphaned components
        		// i.e. those only referenced by module being updated
        		if (comp.getCompId() != curComp.getCompId() && graph.equals(String.valueOf(curComp.getCompId()))) {
        			// OK, see it they are on current list of components
        			boolean found = false;
        			for (List<String> cparts : components) {
        				if (cparts.get(0).equals(comp.getCompId()) && cparts.get(1).equals(comp.getArtifactId())) {
        					found = true;
        					break;
        				}
        			}
        			if (! found) {
        				// we can remove this component - it will no longer be needed
        				System.out.println("Removing orphaned component: " + comp.getArtifactId());
        				File delFile = new File(libDestDir, comp.getArtifactId() + "-" + comp.getVersionStr() + ".jar");
        				delFile.delete();
                        comp.delete(h);
        			}
        		}
        	}
        
            // reinstall module jar and update its Component entry
    		File modJar = getModuleArtifact();
    		String checksum = checksum(modJar);
            if ("jar".equals(packaging)) {
                safeCopy(modJar, libDestDir, false);
                // also copy to (staging) jars directory for adding to webapps if core
                if ("core".equals(modScope)) {
                    safeCopy(modJar, new File(kernelDir, JARS_DIR), true);
                    updateWars = true;
                }
            }
			// update DB to reflect this change
			h.execute("UPDATE installation SET versionstr = :vsn, checksum = :csum, updated = :upd WHERE groupid = :gid AND artifactid = :aid",
					  version, checksum, new Timestamp(System.currentTimeMillis()), groupId, artifactId);
    	
        	// Install dependent jars that aren't already there, updating their reference graph in any case
        	File libSrcDir = new File(baseDir, LIB_DIR);
        	for (List<String> cparts : components) {
        		String grpId = cparts.get(0);
        		String artId = cparts.get(1);
        		String vsn = cparts.get(3);
        		String status = cparts.get(5);
        		File jarFile = new File(libSrcDir, artId + "-" + vsn + ".jar");
        		if ("update".equals(status)) {
        			// just update version of component
        			safeCopy(jarFile, libDestDir, true);
        			updateVersion(h, grpId, artId, version, checksum(jarFile));
        		} else if ("install".equals(status)) {
        			// copy jar to lib & add to installation table
        			safeCopy(jarFile, libDestDir, false);
                    // also copy to (staging) jars directory for adding to webapps
                    if ("core".equals(modScope)) {
                        safeCopy(jarFile, new File(kernelDir, JARS_DIR), true);
                    } 
        			h.execute("INSERT INTO installation (compid, comptype, groupid, artifactid, versionstr, checksum, graph, updated) " +
        					"VALUES (nextval('installation_seq'), ?, ?, ?, ?, ?, ?, ?)",
        					1, grpId, artId, vsn, checksum(jarFile), String.valueOf(curComp.getCompId()), new Timestamp(System.currentTimeMillis()));
        		}
            }
            System.out.println("Copied components");
        }
    
        // in either case, update the resource data
        List<String> excludes = asList(exclusions);
        for (File file : baseDir.listFiles()) {
            if (! excludes.contains(file.getName()) && file.isDirectory()) {
                safeCopy(file, destFile, true);
            }
        }

        if (updateWars) {
            rebuildWars();
        }
    }

    private Path getModuleDir(Component comp) {
        if (comp.getArtifactId().endsWith("kernel")) {
            return baseDir.toPath();
        }
        String moduleDir = comp.getArtifactId();
        moduleDir = moduleDir.substring("dsm-".length());
        return baseDir.toPath().resolve(MODULES_DIR).resolve(moduleDir);
    }

    private void loadModuleResources(Path refDir) throws IOException, SQLException, Exception {
        // first any schema changes, then data
        loadDDL(refDir);
        loadRegistries(refDir);
        loadEmailTemplates(refDir);
    }

    /// create necessary directories for staging/installation environment
    private void createStagingEnv() throws IOException {
        // create modules (src) directory if it doesn't exist
        File modsFile = new File(kernelDir, MODULES_DIR);
        if (! modsFile.exists()) {
            modsFile.mkdir();
        }
        // create webapps directory if it doesn't exist
        File webappsFile = new File(kernelDir, WEBAPPS_DIR);
        if (! webappsFile.exists()) {
            webappsFile.mkdir();
        }
        // create jars sub-directory if it doesn't exist
        File jarsFile = new File(kernelDir, JARS_DIR);
        if (! jarsFile.exists()) {
            jarsFile.mkdir();
        }
        // create wars sub-directory if it doesn't exist
        File warsFile = new File(kernelDir, WARS_DIR);
        if (! warsFile.exists()) {
            warsFile.mkdir();
        }
        // create deploy sub-directory if it doesn't exist
        File deployFile = new File(kernelDir, DEPLOY_DIR);
        if (! deployFile.exists()) {
            deployFile.mkdir();
        }
    }

    private void loadStagingInfo() {
        try (Scanner scanner = new Scanner(new File(kernelDir, STAGED_FILE))) {
            while (scanner.hasNext()) {
                String[] comps = scanner.next().split(",");
                stagedMap.put(comps[2] + comps[3], new Component(Integer.parseInt(comps[0]), Integer.parseInt(comps[1]), 
                             comps[2], comps[3], comps[4], comps[5], comps[6]));
            }
        } catch (FileNotFoundException fnfE) {}
    }

    private void stageModuleJar(File modJar, File destDir) throws IOException {
        if ("jar".equals(packaging)) {
            System.out.println("stageMJ - jar packaging");
            safeCopy(modJar, new File(destDir, LIB_DIR), false);
            // also copy to (staging) jars directory for adding to webapps if core
            if ("core".equals(modScope)) {
                safeCopy(modJar, new File(kernelDir, JARS_DIR), false);
            }
        }
    }

    private void saveStagingInfo() throws IOException {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(new File(kernelDir, STAGED_FILE)))) {
            for (Component comp: stagedMap.values()) {
                StringBuilder compSB = new StringBuilder();
                compSB.append(comp.getCompId()).append(",").append(comp.getCompType()).append(",").
                append(comp.getGroupId()).append(",").append(comp.getArtifactId()).append(",").
                append(comp.getVersionStr()).append(",").append(comp.getChecksum()).append(",").
                append(comp.getGraph()).append("\n");
                writer.write(compSB.toString());
            }
        }
    }

    private void rebuildWars() throws IOException {
        // just redo all wars found in directory
        for (File warFile : new File(kernelDir, WARS_DIR).listFiles()) {
            rebuildWar(warFile.getName());
        }
    }

    private void rebuildWar(String artId, String vsn) throws IOException {
        String warName = deployAs + "-" + artId + "-" + vsn + ".war";
        rebuildWar(warName);
    }

    private void rebuildWar(String warName) throws IOException {
        // copy war to webapps deploy directory, then supplement it with core jars
        // and the contents of the conf directories of both the kernel and the module itself
        File srcDir = new File(kernelDir, WARS_DIR);
        File deployDir = new File(kernelDir, DEPLOY_DIR);
        // extract 'deployAs' name
        String shortName = warName.substring(0, warName.indexOf("-"));
        File deployFile = new File(deployDir, shortName + ".war");
        Files.copy(new File(srcDir, warName).toPath(), deployFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
        URI jarUri = URI.create("jar:file:" + deployFile.getAbsolutePath());
        try (FileSystem jarfs = FileSystems.newFileSystem(jarUri, new HashMap())) {
            // the core jars
            for (File jarFile : new File(kernelDir, JARS_DIR).listFiles()) {
                Path jarTarget = jarfs.getPath("/WEB-INF/lib/" + jarFile.getName());
                Files.copy(jarFile.toPath(), jarTarget);
            }
            // the kernel conf files - put in 'classes' since Tomcat's classpath is spartan
            Path kernelConf = new File(kernelDir, CONF_DIR).toPath();
            Path jarPath = jarfs.getPath("/WEB-INF/classes/");
            Files.walkFileTree(kernelConf, new CopyVisitor(kernelConf, jarPath));
            // the module conf files - same deal
            Path moduleConf = new File(baseDir, CONF_DIR).toPath();
            Files.walkFileTree(moduleConf, new CopyVisitor(moduleConf, jarPath));
        }
        System.out.println("rebuilt WAR: " + warName);
    }

    static class CopyVisitor extends SimpleFileVisitor<Path> {
        private Path srcPath;
        private Path targPath;
        private StandardCopyOption copyOption = StandardCopyOption.REPLACE_EXISTING;

        public CopyVisitor(Path srcPath, Path targPath) {
            this.srcPath = srcPath;
            this.targPath = targPath;
        }

        @Override
        public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
            String relPath = srcPath.relativize(dir).toString();
            if (relPath.length() > 0) {
                Path targetPath = targPath.resolve(relPath);
                if (! Files.exists(targetPath)) {
                    Files.createDirectory(targetPath);
                }
            }
            return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
            String relPath = srcPath.relativize(file).toString();
            Files.copy(file, targPath.resolve(relPath), copyOption);
            return FileVisitResult.CONTINUE;
        }
    }

    private List<List<String>> getDependencyActions(Handle h, Path refDir, boolean checkStaged) throws IOException {
        List<List<String>> components = readModuleDependencies(refDir);
        for (List<String> cparts : components) {
            // determine if this component needs to be checked
            String grpId = cparts.get(0);
            String artId = cparts.get(1);
            String vsn = cparts.get(3);
            String scope = cparts.get(4);
            if ("compile".equals(scope) || artId.startsWith("dsm-")) {
                // Query the deployed system for this component
                Component depComp = Component.findByCoordinates(h, grpId, artId);
                // if not in instance, it might be in the staged transaction set we are examining
                if (depComp == null && checkStaged) {
                    depComp = stagedMap.get(grpId + artId);
                }
                if (depComp != null) {
                    // version differences will only matter if other components also depend on this one
                    String graph = depComp.getGraph();
                    if (graph.indexOf("-") > 0 || ! graph.equals(String.valueOf(depComp.getCompId()))) {
                        // do versions conflict?
                        if (! depComp.getVersionStr().equals(vsn)) {
                            cparts.add("fail");
                            cparts.add("Module dependency: '" + artId + "' version (" + vsn +
                                       ") conflicts with existing component (" + depComp.getVersionStr() + ")");
                        } else {
                            cparts.add("ignore");
                        }
                    } else {
                        cparts.add("update");
                    }
                } else {
                    if (artId.startsWith("dsm-")) {
                        cparts.add("fail");
                        cparts.add("Module dependency: '" + artId + "' must be installed first");
                    } else {
                        cparts.add("install");
                    }
                }
            } else {
                cparts.add("ignore");
            }
        }
        return components;
    } 

    private List<List<String>> getStagingDependencyActions(Path refDir) throws IOException {
        List<List<String>> components = readModuleDependencies(refDir);
        for (List<String> cparts : components) {
            // determine if this component needs to be checked
            String grpId = cparts.get(0);
            String artId = cparts.get(1);
            String vsn = cparts.get(3);
            String scope = cparts.get(4);
            if ("compile".equals(scope) || artId.startsWith("dsm-")) {
                // Query the deployed system for this component
                Component depComp = stagedMap.get(grpId + artId);
                if (depComp != null) {
                    // version differences will only matter if other components also depend on this one
                    String graph = depComp.getGraph();
                    if (graph.indexOf("-") > 0 || ! graph.equals(String.valueOf(depComp.getCompId()))) {
                        // do versions conflict?
                        if (! depComp.getVersionStr().equals(vsn)) {
                            cparts.add("fail");
                            cparts.add("Module dependency: '" + artId + "' version (" + vsn +
                                       ") conflicts with existing component (" + depComp.getVersionStr() + ")");
                        } else {
                            cparts.add("ignore");
                        }
                    } else {
                        cparts.add("update");
                    }
                } else {
                    if (artId.startsWith("dsm-")) {
                        cparts.add("fail");
                        cparts.add("Module dependency: '" + artId + "' must be installed first");
                    } else {
                        cparts.add("install");
                    }
                }
            } else {
                cparts.add("ignore");
            }
        }
        return components;
    }   
    
    private boolean dbInitialized(Handle h) throws SQLException {
        DatabaseMetaData md = h.getConnection().getMetaData();
        return md.getTables(null, null, "installation", null).next();
    }
    
    private void updateVersion(Handle h, String grpId, String artId, String version, String checksum) throws SQLException {
        h.execute("UPDATE installation SET versionstr = :vsn, checksum = :csum, updated = :upd WHERE groupid = :gid AND artifactid = :aid",
                  version, checksum, new Timestamp(System.currentTimeMillis()), grpId, artId);
    }
    
    private File getModuleArtifact() throws IOException {
        File libSrcDir = new File(baseDir, LIB_DIR);
        File buildDir = new File(baseDir, BUILD_DIR);
        File modArt = null;
        // prefer the locally built (customized) version if present
        if (buildDir.isDirectory()) {
            modArt = new File(buildDir, artifactId + "-" + version + "." + packaging);
        }
        // use packaged version otherwise
        if (modArt == null || ! modArt.exists()) {
            if (libSrcDir.isDirectory()) {
                modArt = new File(libSrcDir, artifactId + "-" + version + "." + packaging);
            }
        }
        return modArt;
    }
    
    private void safeCopy(File src, File destDir, boolean overwrite) throws IOException {
        // ensure destDir exists if no overwrite assumption
        if (! overwrite && Files.notExists(destDir.toPath())) {
            destDir.mkdir();
        }
        File destFile = new File(destDir, src.getName());
        if (src.isDirectory()) {
            if (! overwrite) {
                destFile.mkdir();
            }
            for (File file : src.listFiles()) {
                safeCopy(file, destFile, overwrite);
            }
        } else if (overwrite) {
            if (src.exists() && destFile.exists()) {
                Files.copy(src.toPath(), destFile.toPath());
            } else {
                System.out.println("Error - expected file to be present: " + destFile.getName());
            }
        } else {
            if (src.exists() && ! destFile.exists()) {
                Files.copy(src.toPath(), destFile.toPath());
            } else {
                System.out.println("Error - expected file to be unique: " + destFile.getName());
            }
        }
    }
    
    private void readPOM() throws IOException {
        File pomFile = new File(baseDir, POM_FILE);
        if (pomFile.exists()) {
            try {
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
                if ("war".equals(packaging)) {
                    deployAs = findPomValue(pomDoc, xpath.compile("/project/properties/deployAs/text()"));
                    if (deployAs == null) {
                        deployAs = "mdsapp";
                    }
                }
                modScope = findPomValue(pomDoc, xpath.compile("/project/properties/modscope/text()"));
                if (modScope == null) {
                    // default to most conservative assumption if not a war & not annotated
                    if ("war".equals(packaging)) modScope = "webapp";
                    else if (! "kernel".equals(module)) modScope = "core";
                    else modScope = "kernel";
                }
            } catch (ParserConfigurationException | SAXException | XPathExpressionException ex) {
                throw new IOException("POM parsing exception");
            }
        } else {
            System.out.println("No POM file at expected location: " + pomFile.getAbsolutePath());
        }
        // did we read from POM successfully?
        checkState(artifactId != null, "Bad POM file - unable to process");
    }
    
    private String findPomValue(Document doc, XPathExpression expr) throws XPathExpressionException {
        NodeList nodes = (NodeList)expr.evaluate(doc, XPathConstants.NODESET);
        if (nodes.getLength() > 0) {
            return nodes.item(0).getNodeValue();
        }
        return null;
    }
    
    private List<List<String>> readModuleDependencies(Path refDir) throws IOException {
        List<List<String>> compList = new ArrayList<List<String>>();
        try (BufferedReader reader = Files.newBufferedReader(refDir.resolve(DEPS_FILE), Charset.forName("UTF-8"))) {
            String lineIn = null;
            while ((lineIn = reader.readLine()) != null) {
                lineIn = lineIn.trim();
                if (lineIn.length() > 0 && ! lineIn.startsWith("The")) {
                    List<String> parts = new ArrayList(asList(lineIn.split(":")));
                    compList.add(parts);
                }
            }
        }
        return compList;
    }
    
    private void cleanDB(Handle h) throws IOException, SQLException {
        unloadDDL();
    }
    
    private void initDB(Handle h) throws IOException, SQLException {
        checkState(ConfigurationManager.getProperty("db.password") != null, "no database password defined");
        // the only module-independent data to be 'bootstrapped' is in the installation
        // table itself: everything else will be loaded when a module is installed
   	 	h.execute("CREATE SEQUENCE installation_seq");
   	 	h.execute("CREATE TABLE installation (" +
   	 	          "compid INTEGER PRIMARY KEY, " +
   	 			  "comptype INTEGER, " +
   	 	          "groupid VARCHAR, " +
   	 			  "artifactid VARCHAR, " +
   	 	          "versionstr VARCHAR, " +
   	 			  "checksum VARCHAR, " +
   	 			  "graph VARCHAR, " +
   	 	          "updated TIMESTAMP)");
    	//"CREATE USER dspace WITH CREATEDB PASSWORD '" + dbPassword + "';");
    	//"CREATE DATABASE dspace ENCODING UTF8 OWNER dspace;");
    }
    
    private void loadDDL(Path refDir) throws IOException, SQLException {
        String dbName = ConfigurationManager.getProperty("db.name");
        checkState(dbName != null, "no database name defined");

        //String path = baseDir.getAbsolutePath() + File.separator + DDL_DIR + File.separator + dbName;
        File ddlFile = refDir.resolve(DDL_DIR).resolve(dbName).resolve(DDL_UPFILE).toFile();
        // not all modules have DDLs
        //checkState(ddlFile.exists(), "no DDL file present");
        if (ddlFile.exists()) {
            DatabaseManager.loadSql(new FileReader(ddlFile.getCanonicalPath()));
        }
    }
    
    private void unloadDDL() throws IOException, SQLException {
        String dbName = ConfigurationManager.getProperty("db.name");
        checkState(dbName != null, "no database name defined");

        String path = baseDir.getAbsolutePath() + File.separator + DDL_DIR + File.separator + dbName;
        File ddlFile = new File(path, DDL_DOWNFILE);
        //checkState(ddlFile.exists(), "no DDL file present");
        if (ddlFile.exists()) {
            DatabaseManager.loadSql(new FileReader(ddlFile.getCanonicalPath()));
        }
    }
    
    private void loadRegistries(Path refDir) throws Exception {
        File regDir = refDir.resolve(REG_DIR).toFile();
        if (regDir.isDirectory()) {
            try (Context context = new Context()) {
                context.turnOffAuthorisationSystem();
                for (File regFile : regDir.listFiles()) {
                    if (! Files.isDirectory(regFile.toPath())) {
                        RegistryLoader.loadRegistryFile(context, regFile.getAbsolutePath());
                    }
                }
                context.complete();
            }
        }
    }

    private void loadEmailTemplates(Path refDir) throws Exception {
        File emailDir = refDir.resolve(EMAIL_DIR).toFile();
        if (emailDir.isDirectory()) {
            try (Context context = new Context()) {
                context.turnOffAuthorisationSystem();
                for (File emailFile : emailDir.listFiles()) {
                    BufferedReader reader = new BufferedReader(new FileReader(emailFile));
                    StringBuilder templateSb = new StringBuilder();
                    String line = reader.readLine();
                    while (line != null) {
                        templateSb.append(line).append("\n");
                        line = reader.readLine();
                    }
                    reader.close();
                    Email.loadTemplate(context, emailFile.getName(), templateSb.toString());
                }
                context.complete();
            }
        }
    }
    
    private void initIndexes() throws Exception {
        try (Context context = new Context()) {
            indexer = new DSIndexer();
            indexer.createIndex(context);
            context.complete();
        } 
    }
    
    private String checksum(File file) throws IOException, NoSuchAlgorithmException {
        MessageDigest md = MessageDigest.getInstance("MD5");
        FileInputStream fis = new FileInputStream(file);
        byte[] dataBytes = new byte[1024];
 
        int nread = 0; 
        while ((nread = fis.read(dataBytes)) != -1) {
            md.update(dataBytes, 0, nread);
        }
        fis.close();
        byte[] mdbytes = md.digest();
        StringBuffer sb = new StringBuffer();
        for (int i = 0; i < mdbytes.length; i++) {
            sb.append(Integer.toString((mdbytes[i] & 0xff) + 0x100, 16).substring(1));
        }
        return sb.toString();
    }
}
