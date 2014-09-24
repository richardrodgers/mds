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
import java.io.StringReader;
import java.io.IOException;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;

import groovy.lang.GroovyClassLoader;

import com.google.common.base.Strings;

import org.skife.jdbi.v2.Handle;
import org.skife.jdbi.v2.util.IntegerMapper;
import org.skife.jdbi.v2.util.StringMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.dspace.core.ConfigurationManager;
import org.dspace.core.Context;

/**
 * TaskResolver takes the logical (local) name of a curation task or selector
 * and attempts to deliver a suitable implementation object. 
 * Supported implementation types include:
 * (1) Classpath-local Java classes
 * (2) Local task 'programs', kept in the database.
 * (3) Local Groovy scripted tasks, kept in the database.
 * (4) Other local script-based tasks, viz. coded in any scripting language whose
 * runtimes are accessible via the JSR-223 scripting API. This really amounts
 * to the family of dynamic JVM languages: JRuby, Jython, Javascript, etc
 * Note that the requisite jars and other resources for these languages must be
 * installed in the DSpace instance for them to be used here (with the exception of
 * Groovy, which is pre-installed).
 * Further work may involve remote URL-loadable code, etc. 
 * 
 * All task metadata and script files are stored in DB tables,
 * where java tasks are identified by a FQCN, and each
 * scripted task has a property with value syntax:
 * <engine>|<implClassCtor>
 * An example property:
 * 
 * linkchecker = ruby|LinkChecker.new
 * 
 * This descriptor means that a 'ruby' script engine will be created,
 * a script file stored with the name 'linkchecker' will be loaded
 * and the resolver will expect an evaluation of 'LinkChecker.new' will 
 * provide a correct implementation object.
 * 
 * Script files may embed their descriptors to facilitate deployment.
 * To accomplish this, a script must include the descriptor string with syntax:
 * $td=<descriptor> somewhere on a comment line. for example:
 * 
 * # My descriptor $td=rubyLinkChecker.new
 * 
 * 
 * @author richardrodgers
 */

public class TaskResolver {

    // logging service
    private static Logger log = LoggerFactory.getLogger(TaskResolver.class);

    /**
     * Installs a task or selector.
     *
     * @param taskName
     *        logical name to associate with task
     * @param description
     *        description of task to appear in UIs, etc
     * @param type
     *        type of task: 'java', 'program', 'script'
     * @param loadAddr
     *        how to load the task: FQDN for java tasks, etc
     */
     public static void installTask(Context context, String name, String description, String type, String impl, String loadAddr, String script, String config) {
        context.getHandle().execute("INSERT into ctask_data (name, description, type, impl, load_addr, script, config) values (?, ?, ?, ?, ?, ?, ?)",
            name, description, type, impl, loadAddr, script, config);
     }

    /**
     * Adds a task/selector group.
     *
     * @param type
     *        group type, either 'task' or 'selector'
     * @param name
     *        short group name, site-unique
     * @param description
     *        description of group to appear in UIs, etc
     * @param uiAccess
     *        if true, display in UI, else suppress
     * @param apiAccess
     *        if true, expose in API, else suppress
     */
     public static void addGroup(Context context, String type, String name, String description, boolean uiAccess, boolean apiAccess) {
        context.getHandle().execute("INSERT into ctask_group (type, group_name, description, ui_access, api_access) values (?, ?, ?, ?, ?)",
                                    type, name, description, uiAccess, apiAccess);
     }

     public static void addGroupMember(Context context, String group, String type, String name) {
         int groupId = context.getHandle().createQuery("SELECT ctask_group_id FROM ctask_group WHERE group_name = :name AND type = :type")
                                                       .bind("name", group).bind("type", type).map(IntegerMapper.FIRST).first();
         int taskId = context.getHandle().createQuery("SELECT ctask_id FROM ctask_data WHERE name = :name AND type = :type")
                                                       .bind("name", name).bind("type", type).map(IntegerMapper.FIRST).first();
         context.getHandle().execute("INSERT into group2ctask (group_id, ctask_id) values (?, ?)", groupId, taskId);
     }

     /**
      * Returns whether a group exists
      */
     public static boolean groupExists(Context context, String type, String name) {
         return context.getHandle().select("SELECT * FROM ctask_group WHERE type = ? AND group_name = ?", type, name).size() > 0;
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
    public static boolean canResolveTask(Context context, String taskName) {
        List<Map<String, Object>> r = context.getHandle().select("SELECT impl, load_addr FROM ctask_data WHERE type = 'task' AND name = ?", taskName);
        if (r.size() > 0) {
            String impl = (String)r.get(0).get("impl");
            if ("java".equals(impl)) {
                String taskClass = (String)r.get(0).get("load_addr");
                try {
                    Class.forName(taskClass);
                } catch (Exception e) {
                    log.error("Error locating task class: " + taskClass + " for task: " + taskName, e);
                    return false;
                }
            }
            return true;
        }
        return false;
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
    public static ResolvedTask resolveTask(Context context, String taskName) {
        ResolvedTask rtask = null;
        List<Map<String, Object>> r = context.getHandle().select("SELECT impl, load_addr FROM ctask_data WHERE type = 'task' AND name = ?", taskName);
        if (r.size() > 0) {
            switch ((String)r.get(0).get("impl")) {
                case "java":  rtask = new ResolvedTask(taskName, javaTask((String)r.get(0).get("load_addr"))); break;
                case "groovy": rtask = resolveScript(taskName, taskSource(context, taskName)); break;
                case "script": rtask = new ResolvedTask(taskName, scriptedTask(context, taskName, (String)r.get(0).get("load_addr"))); break;
                case "program": rtask = new ResolvedTask(taskName, new Program(context, taskSource(context, taskName))); break;
                default: break;
            }
        }
        return rtask;
    }

    /**
     * Returns a configured implementation for a given selector (profile) name,
     * or <code>null</code> if no implementation could be obtained.
     * 
     * @param context
     *        context to supply to selector instance
     * @param selectorName
     *        logical selector profile name
     * @return selector
     *        an object that implements the ObjectSelector interface
     */
    public static ObjectSelector resolveSelector(Context context, String selectorName) {
        ObjectSelector selector = null;
        // try to find a selector profile description matching name
        List<Map<String, Object>> r = context.getHandle().select("SELECT impl, load_addr, config FROM ctask_data WHERE type = 'selector' AND name = ?", selectorName);
        if (r.size() > 0) {
            switch ((String)r.get(0).get("impl")) {
                case "java": selector = javaSelector((String)r.get(0).get("load_addr")); break;
                // add other cases, Groovy, etc
                default: break;
            }
            selector.setName(selectorName);
            selector.setContext(context);
            // is there profile configuration to set?
            String config = (String)r.get(0).get("config");
            if (! Strings.isNullOrEmpty(config)) {
                selector.configure(config);
            }
        } else {
            log.info("No selector found with name: '" + selectorName + "'");
        }
        return selector;
    }

    private static CurationTask javaTask(String className) {
        CurationTask ctask = null;
        if (className != null) {
            try {
                ctask = (CurationTask)Class.forName(className).newInstance(); 
            } catch (Exception e) {
                log.error("Error instantiating task with className: " + className, e);
            }
        }
        return ctask;
    }

    private static ObjectSelector javaSelector(String className) {
        ObjectSelector jsel = null;
        if (className != null) {
            try {
                jsel = (ObjectSelector)Class.forName(className).newInstance(); 
            } catch (Exception e) {
                log.error("Error instantiating selector with className: " + className, e);
            }
        }
        return jsel;
    }

    private static ScriptedTask scriptedTask(Context context, String scriptName, String loadAddr) {
        String[] tokens = loadAddr.split("\\|");
        // first token is name ('alias') of scripting engine
        ScriptEngineManager mgr = new ScriptEngineManager();
        ScriptEngine engine = mgr.getEngineByName(tokens[0]);
        if (engine != null) {
            // see if we can locate the script file and load it
            String script = taskSource(context, scriptName);
            if (script != null && script.length() > 0) {
                try {
                    engine.eval(script);
                    // second token is the constructor expression for the class
                    // implementing ScriptedTask interface
                    ScriptedTask stask = (ScriptedTask)engine.eval(tokens[1]);
                    return stask;
                } catch (ScriptException scE) {
                    log.error("Error evaluating script: '" + scriptName + "' msg: " + scE.getMessage());
                }
            } else {
                log.error("No script: '" + scriptName + "' found");
            }
        } else {
            log.error("Script engine: '" + tokens[0] + "' is not installed");
        } 
        return null;
    }

    private static String taskSource(Context context, String taskName) {
        // source lives in the 'script' field
        String source = context.getHandle().createQuery("SELECT script FROM ctask_data WHERE type = 'task' AND name = :name")
                                                        .bind("name", taskName).map(StringMapper.FIRST).first();
        return source;
    }

    public static Properties taskConfig(Context context, String taskName) {
        String confStr = context.getHandle().createQuery("SELECT config FROM ctask_data WHERE type = 'task' AND name = :name")
                                                        .bind("name", taskName).map(StringMapper.FIRST).first();
        Properties confProps = new Properties();
        try (StringReader reader = new StringReader(confStr)) {
            confProps.load(reader);
        } catch (Exception e) {}
        ConfigurationManager.interpolateProps(confProps, 1);
        return confProps;
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
    public static ResolvedTask resolveScript(File scriptFile) {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new FileReader(scriptFile))) {
            String line = br.readLine();
            while (line != null) {
                sb.append(line).append("\n");
                line = br.readLine();
            }
        } catch (Exception e) {}
        return resolveScript(scriptFile.getName(), sb.toString());
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
    public static ResolvedTask resolveScript(String name, String script) {
        GroovyClassLoader gcl = new GroovyClassLoader(TaskResolver.class.getClassLoader());
        Class clazz = gcl.parseClass(script, name);
        try {
            Object scriptObj = clazz.newInstance();
            return new ResolvedTask(name, (CurationTask)scriptObj);
        } catch (Exception e) {
            e.printStackTrace();
            log.error("Error instantiating task: " + name, e);
            return null;
        }
    }
}
