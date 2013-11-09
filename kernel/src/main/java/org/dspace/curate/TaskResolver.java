/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.curate;

import java.io.File;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.util.Arrays;
import java.util.Properties;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;

import groovy.lang.GroovyClassLoader;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.dspace.core.ConfigurationManager;

/**
 * TaskResolver takes the logical (local) name of a curation task and attempts to
 * deliver a suitable implementation object. Supported implementation types include:
 * (1) Classpath-local Java classes loaded via ConfigurationManager.
 * (2) Local task 'programs', kept in a directory configured with the
 * dspace/config/modules/curate.cfg property "program.dir".
 * (3) Local script-based tasks, viz. coded in any scripting language whose
 * runtimes are accessible via the JSR-223 scripting API. This really amounts
 * to the family of dynamic JVM languages: JRuby, Jython, Groovy, Javascript, etc
 * Note that the requisite jars and other resources for these languages must be
 * installed in the DSpace instance for them to be used here.
 * Further work may involve remote URL-loadable code, etc. 
 * 
 * Scripted tasks are managed in a directory configured with the
 * dspace/config/modules/curate.cfg property "script.dir". A catalog of
 * scripted tasks named 'task.catalog" is kept in this directory.
 * Each task has a 'descriptor' property with value syntax:
 * <engine>|<relFilePath>|<implClassCtor>
 * An example property:
 * 
 * linkchecker = ruby|rubytask.rb|LinkChecker.new
 * 
 * This descriptor means that a 'ruby' script engine will be created,
 * a script file named 'rubytask.rb' in the directory <script.dir> will be
 * loaded and the resolver will expect an evaluation of 'LinkChecker.new' will 
 * provide a correct implementation object.
 * 
 * Script files may embed their descriptors to facilitate deployment.
 * To accomplish this, a script must include the descriptor string with syntax:
 * $td=<descriptor> somewhere on a comment line. for example:
 * 
 * # My descriptor $td=ruby|rubytask.rb|LinkChecker.new
 * 
 * For portability, the <relFilePath> component may be omitted in this context.
 * Thus, $td=ruby||LinkChecker.new will be expanded to a descriptor
 * with the name of the embedding file.
 * 
 * @author richardrodgers
 */

public class TaskResolver {

    // logging service
    private static Logger log = LoggerFactory.getLogger(TaskResolver.class);

    // base directory of task scripts, programs & catalog name
    private static final String CATALOG = "task.catalog";
    private static final String scriptDir = ConfigurationManager.getProperty("curate", "script.dir");
    private static final String programDir = ConfigurationManager.getProperty("curate", "program.dir");

    // catalog of script tasks
    private Properties catalog;

    public TaskResolver() {}

    /**
     * Installs a task script. Succeeds only if script:
     * (1) exists in the configured script directory and
     * (2) contains a recognizable descriptor in a comment line.
     * If script lacks a descriptor, it may still be installed
     * by manually invoking <code>addDescriptor</code>.
     * 
     * @param taskName
     *        logical name of task to associate with script
     * @param fileName
     *        name of file containing task script
     * @return true if script installed, false if installation failed
     */
    public boolean installScript(String taskName, String fileName) {
        // Can we locate the file in the script directory?
        File script = new File(scriptDir, fileName);
        if (script.exists()) {
            try (BufferedReader reader = new BufferedReader(new FileReader(script))) {
				String line = null;
				while((line = reader.readLine()) != null) {
					if (line.startsWith("#") && line.indexOf("$td=") > 0) {
						String desc = line.substring(line.indexOf("$td=") + 4);
						// insert relFilePath if missing
						String[] tokens = desc.split("\\|");
						if (tokens[1].length() == 0) {
							desc = tokens[0] + "|" + fileName + "|" + tokens[2];
						}
						addDescriptor(taskName, desc);
						return true;
					}
				}
			} catch(IOException ioE) {
				log.error("Error reading task script: " + fileName);
			}			
		} else {
            log.error("Task script: " + fileName + "not found in: " + scriptDir);
		}
		return false;
    }

    /**
     * Adds a task descriptor property and flushes catalog to disk.
     * 
     * @param taskName
     *        logical task name
     * @param descriptor
     *         descriptor for task
     */
    public void addDescriptor(String taskName, String descriptor) {
        loadCatalog();
        catalog.put(taskName, descriptor);
        try (Writer writer = new FileWriter(new File(scriptDir, CATALOG))) {
            catalog.store(writer, "do not edit");
        } catch(IOException ioE) {
            log.error("Error saving scripted task catalog: " + CATALOG);
        }
    }

	/**
	 * Returns whether the task name can be resolved against the locally
	 * configured system.
	 *
	 * @param taskName
	 *        logical task name
	 * @return resolvable
	 *        true if task can be resolved, else false
	 */
	public boolean canResolveTask(String taskName) {
	    // likely a java task - try first
		String taskClass = ConfigurationManager.getProperty("curate", "task." + taskName);
		if (taskClass != null) {
            try {
                Class.forName(taskClass);
			} catch (Exception e) {
				log.error("Error locating task class: " + taskClass + " for task: " + taskName, e);
				return false;
			}
			return true;
		}
		// try programs next
		if (new File(programDir, taskName).exists()) {
	        return true;
		}
		// finally scripted tasks
		loadCatalog();
		return (catalog.getProperty(taskName) != null);
    }

    /**
     * Returns a task implementation for a given task name,
     * or <code>null</code> if no implementation could be obtained.
     * 
     * @param taskName
     *        logical task name
     * @return task
     *        an object that implements the CurationTask interface
     */
    public ResolvedTask resolveTask(String taskName) {
        // likely a java task - try first
        String taskClass = ConfigurationManager.getProperty("curate", "task." + taskName);
		if (taskClass != null) {
			try {
				CurationTask ctask = (CurationTask)Class.forName(taskClass).newInstance();
				return new ResolvedTask(taskName, ctask);
			} catch (Exception e) {
				log.error("Error instantiating task: " + taskName, e);
				return null;
			}
		}
		// maybe it's a program?
		File program = new File(programDir, taskName);
		if (program.exists()) {
	        return new ResolvedTask(taskName, program);
		}
		// maybe it is implemented by a script?
		loadCatalog();
		String scriptDesc = catalog.getProperty(taskName);
		if (scriptDesc != null)	{
			String[] tokens = scriptDesc.split("\\|");
			// first descriptor token is name ('alias') of scripting engine
			ScriptEngineManager mgr = new ScriptEngineManager();
			ScriptEngine engine = mgr.getEngineByName(tokens[0]);
			if (engine != null)	{
			    // see if we can locate the script file and load it
			    // the second token is the relative path to the file
			    File script = new File(scriptDir, tokens[1]);
			    if (script.exists()) {
			        try (Reader reader = new FileReader(script)) {
			    		engine.eval(reader);
			    		// third token is the constructor expression for the class
			    		// implementing CurationTask interface
			    		ScriptedTask stask = (ScriptedTask)engine.eval(tokens[2]);
			    		return new ResolvedTask(taskName, stask);
			    	} catch (FileNotFoundException fnfE) {
			    		log.error("Script: '" + script.getName() + "' not found for task: " + taskName);
			    	} catch (IOException ioE) {
			    		log.error("Error loading script: '" + script.getName() + "'");
			    	} catch (ScriptException scE) {
			    		log.error("Error evaluating script: '" + script.getName() + "' msg: " + scE.getMessage());
			    	}
			    } else {
			    	log.error("No script: '" + script.getName() + "' found for task: " + taskName);
			    }
			} else {
                log.error("Script engine: '" + tokens[0] + "' is not installed");
            } 
        }
        return null;
    }

    /**
     * Returns a task implementation for a given script,
     * or <code>null</code> if no implementation could be obtained.
     * 
     * @param scriptFile
     *        a file containing a scripted implementation of CurationTask
     * @return task
     *        an object that implements the CurationTask interface
     */
    public ResolvedTask resolveScript(File scriptFile) {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new FileReader(scriptFile))) {
            String line = br.readLine();
            while (line != null) {
                sb.append(line).append("\n");
                line = br.readLine();
            }
        } catch (Exception e) {}
        return resolveScript(sb.toString());
    }

    /**
     * Returns a task implementation for a given script,
     * or <code>null</code> if no implementation could be obtained.
     * 
     * @param script
     *        a script containing an implementation of CurationTask
     * @return task
     *        an object that implements the CurationTask interface
     */
    public ResolvedTask resolveScript(String script) {
        GroovyClassLoader gcl = new GroovyClassLoader(getClass().getClassLoader());
        Class clazz = gcl.parseClass(script, "Anon.groovy");
        try {
            Object scriptObj = clazz.newInstance();
            return new ResolvedTask("Anon.groovy", (CurationTask)scriptObj);
        } catch (Exception e) {
            e.printStackTrace();
            log.error("Error instantiating task: " + "Anon.groovy", e);
            return null;
        }
    }

    /*
     * Loads catalog of descriptors for tasks if not already loaded
     */
    private void loadCatalog() {
        if (catalog == null) {
            catalog = new Properties();
            File catalogFile = new File(scriptDir, CATALOG);
            if (catalogFile.exists()) {
                try (Reader reader = new FileReader(catalogFile)) {
                    catalog.load(reader);
                } catch(IOException ioE) {
                    log.error("Error loading scripted task catalog: " + CATALOG);
                }
            }
        }
    }
}
