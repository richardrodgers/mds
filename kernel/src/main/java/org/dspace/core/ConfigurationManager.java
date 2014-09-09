/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.core;

//import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.JmxReporter;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.joran.JoranConfigurator;
import ch.qos.logback.core.joran.spi.JoranException;
import ch.qos.logback.core.util.StatusPrinter;

/**
 * Class for reading the DSpace system configuration. The main configuration is
 * read in as properties from a standard properties file.
 * <P>
 * The main configuration is by default read from the <em>resource</em>
 * <code>/dspace.cfg</code>.
 * To specify a different configuration, the system property
 * <code>dspace.configuration</code> should be set to the <em>filename</em>
 * of the configuration file.
 * <P>
 * Other configuration files are read from the <code>config</code> directory
 * of the DSpace installation directory (specified as the property
 * <code>dspace.dir</code> in the main configuration file.)
 *
 *  Notes: (1) this class should gradually be superceded by ConfigManager, which uses newer config libary
 *         (2) Metrics registry is exposed here, since it is among the first classes to be loaded in the app,
 *             but should eventually be moved out.
 */
public class ConfigurationManager
{
    /** log4j category */
    private static Logger log = null; // LoggerFactory.getLogger(ConfigurationManager.class);

    public static final MetricRegistry metrics = new MetricRegistry();
    // NB: development-mode only reporter for all gathered metrics
    private static JmxReporter reporter;

    /** The configuration properties */
    private static Properties properties = null;
    
    /** module configuration properties */
    private static Map<String, Properties> moduleProps = null;

    // default name of base config file
    private static final String DEFAULT_CONFIG = "kernel.cfg";

    // limit of recursive depth of property variable interpolation in
    // configuration; anything greater than this is very likely to be a loop.
    private static final int RECURSION_LIMIT = 9;

    protected ConfigurationManager() {}

    public static void reportMetricsJmx() {
        // dev-mode only - metrics reporting via JMX
        reporter.start();
    }

    /**
     * Identify if DSpace is properly configured
     * @return boolean true if configured, false otherwise
     */
    public static boolean isConfigured() {
        return properties != null;
    }
    
    public static boolean isConfigured(String module) {
        return moduleProps.get(module) != null;
    }

    /**
     * REMOVED - Flushing the properties could be dangerous in the current DSpace state
     * Need to consider how it will affect in-flight processes
     *
     * Discard all current properties - will force a reload from disk when
     * any properties are requested.
     */
//    public static void flush()
//    {
//        properties = null;
//    }
    
    /**
     * Discard properties for a module -  will force a reload from disk
     * when any of module's properties are requested
     * 
     * @param module the module name
     */
    public static void flush(String module)  {
        moduleProps.remove(module);
    }
    
    /**
     * Returns all properties in main configuration
     * 
     * @return properties - all non-modular properties
     */
    public static Properties getProperties() {
        Properties props = getMutableProperties();
        return props == null ? null : (Properties)props.clone();
    }
    
    /**
     * Returns all properties in main configuration matching passed prefix
     * 
     * @param prefix - property name prefix to match
     * @return properties - all non-modular matching properties
     */
    public static Properties getMatchedProperties(String prefix) {
        Properties props = getMutableProperties();
        return props == null ? null : matchedProperties(props, prefix);
    }

    private static Properties getMutableProperties() {
        if (properties == null) {
            loadConfig();
        }
        return properties;
    }
    
    private static Properties matchedProperties(Properties props, String prefix) {
        Properties matched = new Properties();
        Enumeration pn = props.propertyNames();
        while(pn.hasMoreElements()) {
            String name = (String)pn.nextElement();
            if (name.startsWith(prefix)) {
                matched.put(name.substring(prefix.length() + 1), props.getProperty(name));
            }
        }
        return matched;
    }

    /**
     * Returns all properties for a given module
     * 
     * @param module
     *        the name of the module
     * @return properties - all module's properties
     */
    public static Properties getProperties(String module)  {
        Properties props = getMutableProperties(module);
        return props == null ? null : (Properties)props.clone();
    }

    /**
     * Returns all properties in module configuration matching passed prefix
     * @param module
     *        the name of the module
     * @param prefix - property name prefix to match
     * @return properties - all non-modular matching properties
     */
    public static Properties getMatchedProperties(String module, String prefix) {
        Properties props = getMutableProperties(module);
        return props == null ? null : matchedProperties(props, prefix);
    }

    private static Properties getMutableProperties(String module) {
        if (properties == null) {
            loadConfig();
        }
        Properties retProps = (module != null) ? moduleProps.get(module) : properties;
        if (retProps == null) {
            loadModuleConfig(module);
            retProps = moduleProps.get(module);
        }
        return retProps;
    }

    /**
     * Get a configuration property
     * 
     * @param property
     *            the name of the property
     * 
     * @return the value of the property, or <code>null</code> if the property
     *         does not exist.
     */
    public static String getProperty(String property) {
        Properties props = getMutableProperties();
        String value = props == null ? null : props.getProperty(property);
        return (value != null) ? value.trim() : null;
    }
       
    /**
     * Get a module configuration property value.
     * 
     * @param module 
     *      the name of the module, or <code>null</code> for regular configuration
     *      property
     * @param property
     *      the name (key) of the property
     * @return
     *      the value of the property, or <code>null</code> if the
     *      property does not exist
     */
    public static String getProperty(String module, String property) {
        if (module == null) {
            return getProperty(property);
        }
        
        String value = null;
        Properties modProps = getMutableProperties(module);

        if (modProps != null) {
            value = modProps.getProperty(property);
        }

        if (value == null) {
            // look in regular properties with module name prepended
            value = getProperty(module + "." + property);
        }

        return (value != null) ? value.trim() : null;
    }

    /**
     * Get a configuration property as an integer
     * 
     * @param property
     *            the name of the property
     * 
     * @return the value of the property. <code>0</code> is returned if the
     *         property does not exist. To differentiate between this case and
     *         when the property actually is zero, use <code>getProperty</code>.
     */
    public static int getIntProperty(String property) {
        return getIntProperty(property, 0);
    }
    
    /**
     * Get a module configuration property as an integer
     *
     * @param module
     *         the name of the module
     * 
     * @param property
     *            the name of the property
     * 
     * @return the value of the property. <code>0</code> is returned if the
     *         property does not exist. To differentiate between this case and
     *         when the property actually is zero, use <code>getProperty</code>.
     */
    public static int getIntProperty(String module, String property) {
        return getIntProperty(module, property, 0);
    }

    /**
     * Get a configuration property as an integer, with default
     * 
     * @param property
     *            the name of the property
     *            
     * @param defaultValue
     *            value to return if property is not found or is not an Integer.
     *
     * @return the value of the property. <code>default</code> is returned if
     *         the property does not exist or is not an Integer. To differentiate between this case
     *         and when the property actually is false, use
     *         <code>getProperty</code>.
     */
    public static int getIntProperty(String property, int defaultValue) {
        return getIntProperty(null, property, defaultValue);
    }
    
    /**
     * Get a module configuration property as an integer, with default
     * 
     * @param module
     *         the name of the module
     * 
     * @param property
     *            the name of the property
     *            
     * @param defaultValue
     *            value to return if property is not found or is not an Integer.
     *
     * @return the value of the property. <code>default</code> is returned if
     *         the property does not exist or is not an Integer. To differentiate between this case
     *         and when the property actually is false, use
     *         <code>getProperty</code>.
     */
    public static int getIntProperty(String module, String property, int defaultValue) {
       String stringValue = getProperty(module, property);
       int intValue = defaultValue;

       if (stringValue != null)  {
           try {
               intValue = Integer.parseInt(stringValue.trim());
           } catch (NumberFormatException e) {
               lazyWarn("Warning: Number format error in property: " + property);
           }
        }

        return intValue;
    }

    /**
     * Get a configuration property as a long
     *
     * @param property
     *            the name of the property
     *
     * @return the value of the property. <code>0</code> is returned if the
     *         property does not exist. To differentiate between this case and
     *         when the property actually is zero, use <code>getProperty</code>.
     */
    public static long getLongProperty(String property) {
        return getLongProperty(property, 0);
    }
    
    /**
     * Get a module configuration property as a long
     *
     * @param module
     *         the name of the module    
     * @param property
     *            the name of the property
     *
     * @return the value of the property. <code>0</code> is returned if the
     *         property does not exist. To differentiate between this case and
     *         when the property actually is zero, use <code>getProperty</code>.
     */
    public static long getLongProperty(String module, String property) {
        return getLongProperty(module, property, 0);
    }
     
   /**
     * Get a configuration property as an long, with default
     * 
     *
     * @param property
     *            the name of the property
     *
     * @param defaultValue
     *            value to return if property is not found or is not a Long.
     *
     * @return the value of the property. <code>default</code> is returned if
     *         the property does not exist or is not an Integer. To differentiate between this case
     *         and when the property actually is false, use
     *         <code>getProperty</code>.
     */
    public static long getLongProperty(String property, int defaultValue)  {
        return getLongProperty(null, property, defaultValue);
    }

    /**
     * Get a configuration property as an long, with default
     * 
     * @param module  the module, or <code>null</code> for regular property
     *
     * @param property
     *            the name of the property
     *
     * @param defaultValue
     *            value to return if property is not found or is not a Long.
     *
     * @return the value of the property. <code>default</code> is returned if
     *         the property does not exist or is not an Integer. To differentiate between this case
     *         and when the property actually is false, use
     *         <code>getProperty</code>.
     */
    public static long getLongProperty(String module, String property, int defaultValue) {
        String stringValue = getProperty(module, property);
        long longValue = defaultValue;

        if (stringValue != null) {
            try {
                longValue = Long.parseLong(stringValue.trim());
            } catch (NumberFormatException e) {
                lazyWarn("Warning: Number format error in property: " + property);
            }
        }

        return longValue;
    }

    /**
     * Returns an instance of configured class
     *
     * @param module
     *         the name of the module 
     * @param name
     *         the implementation type
     * @return object
     *         new instance of the type
     */
    public static Object getInstance(String module, String name) {
        Object instance = null;
        String className = getProperty(module, name);
        if (className != null) {
            try {
                instance = Class.forName(className).newInstance();
            } catch (Exception e) {
                log.error("Error instantiating class", e);
                instance = null;
            }
        }
        return instance;
    }
     
    /**
     * Get a configuration property as a boolean. True is indicated if the value
     * of the property is <code>TRUE</code> or <code>YES</code> (case
     * insensitive.)
     * 
     * @param property
     *            the name of the property
     * 
     * @return the value of the property. <code>false</code> is returned if
     *         the property does not exist. To differentiate between this case
     *         and when the property actually is false, use
     *         <code>getProperty</code>.
     */
    public static boolean getBooleanProperty(String property) {
        return getBooleanProperty(property, false);
    }
    
    /**
     * Get a module configuration property as a boolean. True is indicated if 
     * the value of the property is <code>TRUE</code> or <code>YES</code> (case
     * insensitive.)
     * 
     * @param module the module, or <code>null</code> for regular property   
     * 
     * @param property
     *            the name of the property
     * 
     * @return the value of the property. <code>false</code> is returned if
     *         the property does not exist. To differentiate between this case
     *         and when the property actually is false, use
     *         <code>getProperty</code>.
     */
    public static boolean getBooleanProperty(String module, String property)  {
        return getBooleanProperty(module, property, false);
    }
    
   /**
     * Get a configuration property as a boolean, with default.
     * True is indicated if the value
     * of the property is <code>TRUE</code> or <code>YES</code> (case
     * insensitive.)
     *
     * @param property
     *            the name of the property
     *
     * @param defaultValue
     *            value to return if property is not found.
     *
     * @return the value of the property. <code>default</code> is returned if
     *         the property does not exist. To differentiate between this case
     *         and when the property actually is false, use
     *         <code>getProperty</code>.
     */
    public static boolean getBooleanProperty(String property, boolean defaultValue) {
        return getBooleanProperty(null, property, defaultValue);
    }

    /**
     * Get a module configuration property as a boolean, with default.
     * True is indicated if the value
     * of the property is <code>TRUE</code> or <code>YES</code> (case
     * insensitive.)
     * 
     * @param module     module, or <code>null</code> for regular property   
     *
     * @param property
     *            the name of the property
     *
     * @param defaultValue
     *            value to return if property is not found.
     *
     * @return the value of the property. <code>default</code> is returned if
     *         the property does not exist. To differentiate between this case
     *         and when the property actually is false, use
     *         <code>getProperty</code>.
     */
    public static boolean getBooleanProperty(String module, String property, boolean defaultValue)  {
        String stringValue = getProperty(module, property);

        if (stringValue != null) {
            stringValue = stringValue.trim();
            return  stringValue.equalsIgnoreCase("true") ||
                    stringValue.equalsIgnoreCase("yes");
        }
        return defaultValue;
    }

    /**
     * Returns an enumeration of all the keys in the DSpace configuration
     * 
     * @return an enumeration of all the keys in the DSpace configuration
     */
    public static Enumeration<?> propertyNames() {
        return propertyNames(null);
    }
    
    /**
     * Returns an enumeration of all the keys in a module configuration
     * 
     * @param  module    module, or <code>null</code> for regular property  
     * 
     * @return an enumeration of all the keys in the module configuration,
     *         or <code>null</code> if the module does not exist.
     */
    public static Enumeration<?> propertyNames(String module) {
        Properties props = getProperties(module);
        return props == null ? null : props.propertyNames();
    }

    private static synchronized void loadModuleConfig(String module) {
        // try to find it in modules
        try (InputStream cfgIn = ConfigurationManager.class.getResourceAsStream("/modules/" + module + ".cfg")) {
            if (cfgIn != null) {
                Properties modProps = new Properties();
                modProps.load(cfgIn);
                envOverride(modProps, module);
                interpolateProps(modProps, 1);
                moduleProps.put(module, modProps);
            } else {
                // log invalid request
                lazyWarn("Requested configuration module: " + module + " not found");
            }
        } catch (IOException ioE) {
            lazyError("Can't load configuration: " + module, ioE);
        }
    }

    /**
     * Load the DSpace configuration properties. Only does anything if
     * properties are not already loaded.
     * 
     */
    public static synchronized void loadConfig() {
        if (properties != null) {
            return;
        }
        // See if default config overridden
        String overrideCfg = System.getProperty("dspace.configuration");
        String cfgName = (overrideCfg != null) ? overrideCfg : DEFAULT_CONFIG;

        try (InputStream cfgIn = ConfigurationManager.class.getResourceAsStream("/" + cfgName)) {
            if (cfgIn != null) {
                properties = new Properties();
                moduleProps = new HashMap<String, Properties>();
                properties.load(cfgIn);
                envOverride(properties, null);
                interpolateProps(properties, 1);
            } else {
                 throw new IllegalStateException("No such configuration: " + cfgName);
            }
        } catch (IOException ioE) {
            throw new IllegalStateException("Can't load configuration: " + cfgName, ioE);
        }
            /*
             * Initialize Logging once ConfigurationManager is initialized.
             * 
             * This is selection from a property in dspace.cfg, if the property
             * is absent then nothing will be configured and the application
             * will use the defaults provided by logback.
             * 
             * Property format is:
             * log.config = ${dspace.dir}/conf/logback.xml
             * 
             * If there is a problem with the file referred to in
             * "log.configuration" it needs to be sent to System.err
             * so do not instantiate another Logging configuration.
             *
             */
            //log = LoggerFactory.getLogger(ConfigurationManager.class);
            /*
            String dsLogConfiguration = ConfigurationManager.getProperty("log.config");

            if (dsLogConfiguration == null || System.getProperty("dspace.log.init.disable") != null)
            {
                // 
                // Do nothing if log config not set in dspace.cfg or "dspace.log.init.disable" 
                // system property set.  Leave it upto logback to properly init its logging 
                // via classpath or system properties.
                //
                log.info("Using default logback provided log configuration," +
                        "if unintended, check your dspace.cfg for (log.config)");
            }
            else
            {
                log.info("Using dspace provided log configuration (log.config)");
                               
                File logConfigFile = new File(dsLogConfiguration);
                
                // handle special case of logging during module installation
                // the configured location is on the deployment directory, which doesn't
                // exist yet. Use the copy in the (source) classpath, which will be correct
                if (! logConfigFile.exists()) {
                    URL lbUrl = ConfigurationManager.class.getResource("/logback.xml");
                    if (lbUrl != null) {
                        log.info("Loading from classloader: " + lbUrl);
                        logConfigFile = new File(lbUrl.getPath());
                    }
                }
                
                if(logConfigFile.exists())
                {
                    log.info("Loading: " + dsLogConfiguration);
                    // Logback-specific configuration sequence - boilerplate
                    LoggerContext context = (LoggerContext)LoggerFactory.getILoggerFactory();
                    try {
                        JoranConfigurator configurator = new JoranConfigurator();
                        configurator.setContext(context);
                        context.reset(); 
                        configurator.doConfigure(logConfigFile);
                      } catch (JoranException je) {
                        // StatusPrinter will handle this
                      }
                      StatusPrinter.printInCaseOfErrorsOrWarnings(context);
                }
                else
                {
                    log.info("File does not exist: " + dsLogConfiguration);
                }
            }
            */
    }
    
    // these lazy log initialization methods needed because config data needed to
    // initialize the logging system
    
    private static void lazyInfo(String message) {
        if (log == null) {
            log = LoggerFactory.getLogger(ConfigurationManager.class);
            postLogInit();
        }
        log.info(message);
    }
    
    private static void lazyWarn(String message) {
        if (log == null) {
            log = LoggerFactory.getLogger(ConfigurationManager.class);
            postLogInit();
        }
        log.warn(message);
    }
    
    private static void lazyError(String message, Throwable t) {
        if (log == null) {
            log = LoggerFactory.getLogger(ConfigurationManager.class);
            postLogInit();
        }
        log.error(message, t);
    }

    private static void postLogInit() {
        reporter = JmxReporter.forRegistry(metrics).build();
    }

    // look for environment variable property overrides and replace values if found 
    private static void envOverride(Properties props, String module) {
        for (String name : props.stringPropertyNames()) {
            // for a property named 'foo.bar' the environment variable 
            // used to override must be named 'MDS_FOO_BAR'
            // for a module property in module 'baz', the variable will be named
            // MDS_MOD_BAZ_FOO_BAR
            StringBuilder envSB = new StringBuilder("MDS_");
            if (module != null) {
                envSB.append("MOD_").append(module.toUpperCase()).append("_");
            }
            envSB.append(name.replace('.', '_').toUpperCase());
            String envValue = System.getenv(envSB.toString());
            if (envValue != null) {
                props.setProperty(name, envValue);
            }
        }
    }

    /**
     * Interpolates variable references for a property set
     *
     * @param props - the properties to be interpolated
     */
    public static void interpolateProps(Properties props, int level) {
        // walk values, interpolating any embedded references.
        for (String key : props.stringPropertyNames() ) {
            String value = interpolate(key, props.getProperty(key), level);
            if (value != null) {
                props.setProperty(key, value);
            }
        }
    }

    /**
     * Recursively interpolate variable references in value of
     * property named "key".
     * @return new value if it contains interpolations, or null
     *   if it had no variable references.
     */
    private static String interpolate(String key, String value, int level) {
        if (level > RECURSION_LIMIT) {
            throw new IllegalArgumentException("ConfigurationManager: Too many levels of recursion in configuration property variable interpolation, property=" + key);
        }
        //String value = (String)properties.get(key);
        int from = 0;
        StringBuffer result = null;
        while (from < value.length()) {
            int start = value.indexOf("${", from);
            if (start >= 0) {
                int end = value.indexOf('}', start);
                if (end < 0) {
                    break;
                }
                String var = value.substring(start+2, end);
                if (result == null) {
                    result = new StringBuffer(value.substring(from, start));
                } else {
                    result.append(value.substring(from, start));
                }
                // Environmental variable if defined overrides default interpolated value
                String envValue = System.getenv(var);
                if (envValue != null) {
                    result.append(envValue);
                } else if (properties.containsKey(var)) {
                    String ivalue = interpolate(var, properties.getProperty(var), level+1);
                    if (ivalue != null) {
                        result.append(ivalue);
                        properties.setProperty(var, ivalue);
                    } else {
                        result.append(((String)properties.getProperty(var)).trim());
                    }
                } else {
                    //log.warn("Interpolation failed in value of property \""+key+
                    //         "\", there is no property named \""+var+"\"");
                }
                from = end+1;
            } else {
                break;
            }
        }
        if (result != null && from < value.length()) {
            result.append(value.substring(from));
        }
        return (result == null) ? null : result.toString();
    }

    /**
     * Command-line interface for running configuration tasks. Possible
     * arguments:
     * <ul>
     * <li><code>-property name</code> prints the value of the property
     * <code>name</code> from <code>dspace.cfg</code> to the standard
     * output. If the property does not exist, nothing is written.</li>
     * </ul>
     * 
     * @param argv
     *            command-line arguments
     */
    public static void main(String[] argv) {
        if ((argv.length == 2) && argv[0].equals("-property"))  {
            String val = getProperty(argv[1]);

            if (val != null) {
                System.out.println(val);
            } else {
                System.out.println("");
            }

            System.exit(0);
        } else if ((argv.length == 4) && argv[0].equals("-module") &&
                                        argv[2].equals("-property"))  {
            String val = getProperty(argv[1], argv[3]);

            if (val != null) {
                System.out.println(val);
            } else {
                System.out.println("");
            }

            System.exit(0);
        } else {
            System.err
                    .println("Usage: ConfigurationManager OPTION\n  [-module mod.name] -property prop.name  get value of prop.name from module or dspace.cfg");
        }

        System.exit(1);
    }
  
}
