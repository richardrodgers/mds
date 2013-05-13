/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.core;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.joran.JoranConfigurator;
import ch.qos.logback.core.joran.spi.JoranException;
import ch.qos.logback.core.util.StatusPrinter;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigValue;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Class for managing system configurations, utilizing the Typesafe
 * config library. This library allows a richer configuration syntax 
 * called HOCON (superset of JSON), but also accepts simple java properties
 * files.
 * The main configuration is by default read from the <em>resource</em>
 * <code>/conf/kernel.conf</code>.
 * To specify a different configuration, the system property
 * <code>kernel.config</code> should be set to the <em>filename</em>
 * of the configuration file.
 * Other configuration files are read from the <code>conf/modules</code> directory
 * of the installation directory (specified as the property
 * <code>dspace.dir</code> in the main configuration file.)
 * 
 * @author richardrodgers
 */
public class ConfigManager {
    /** log4j category */
    private static Logger log = null; // LoggerFactory.getLogger(ConfigurationManager.class);

    /** reserved name for required base configuration */
    public static final String KERNEL = "kernel";

    /** name of default kernel config file */
    private static final String KERNEL_CONF_FILE = "kernel.conf";

    /** Exception messages for error conditions */
    private static final String NULL_MOD_MSG = "Module name cannot be null";
    private static final String NULL_KEY_MSG = "Property key cannot be null";

    /** name of system property for location of kernel config file */
    public static final String KERNEL_CONF_PROP = "kernel.config";

    /** module configuration data */
    private static Map<String, Config> moduleConfig = new HashMap<>();
    
    /** The default license */
    private static String license;

    private ConfigManager() {  }

    /**
     * Identify if configuration is properly configured
     * @return boolean true if configured, false otherwise
     */
    public static boolean isConfigured() {
        return isConfigured(KERNEL);
    }
    
    /**
     * Identify if module configuration is properly configured
     * @param module the name of the module
     * @return boolean true if configured, false otherwise
     */
    public static boolean isConfigured(String module) {
        checkArgument(module != null, NULL_MOD_MSG);
        return moduleConfig.get(module) != null;
    }
    
    /**
     * Discard properties for a module -  will force a reload from disk
     * when any of module's properties are requested
     * 
     * @param module the module name
     */
    public static void flush(String module) {
        checkArgument(module != null, NULL_MOD_MSG);
        // deny kernel flush
        if (! KERNEL.equals(module)) {
            moduleConfig.remove(module);
        }
    }

    /**
     * Returns configuration data for the given module
     * 
     * @param module
     *        the name of the module
     * @return config - the module's configuration data
     */
    public static Config getConfig(String module) {
        checkArgument(module != null, NULL_MOD_MSG);
        Config modConf = moduleConfig.get(module);
        if (modConf == null) {
            // perhaps not yet loaded
            if (KERNEL.equals(module)) {
                loadConfig(null);
            } else {
                loadModuleConfig(module);
            }
        }
        return checkNotNull(moduleConfig.get(module));
    }

    /**
     * Get a kernel configuration string property - convenience method
     * 
     * @param key - the name (key) of the property
     * 
     * @return value - the value of the property. 
     *
     * @throws <code>com.typesafe.config.ConfigException.Missing</code>
     *      if the property is not defined.
     */
    public static String getProperty(String key) {
        return getProperty(KERNEL, key);
    }
       
    /**
     * Get a module configuration string value.
     * 
     * @param module 
     *      the name of the module
     * @param key
     *      the name (key) of the property
     * @return value
     *      the value of the property
     * @throws <code>com.typesafe.config.ConfigException.Missing</code>
     *      if the property is not defined,
     */
    public static String getProperty(String module, String key) {
        checkArgument(key != null, NULL_KEY_MSG);
        return getConfig(module).getString(key);
    }
    
    /**
     * Get a module configuration integer value.
     *
     * @param module
     *        the name of the module
     * 
     * @param key
     *        the name (key) of the property
     * 
     * @return the value of the property. A <code>Missing</code> exception is thrown if the
     *         property does not exist. 
     */
    public static int getIntProperty(String module, String key) {
        checkArgument(key != null, NULL_KEY_MSG);
        return getConfig(module).getInt(key);
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
    public static long getLongProperty(String module, String key) {
        checkArgument(key != null, NULL_KEY_MSG);
        return getConfig(module).getLong(key);
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
    public static boolean getBooleanProperty(String module, String key) {
        checkArgument(key != null, NULL_KEY_MSG);
        return getConfig(module).getBoolean(key);
    }

    /**
     * Get the License
     * 
     * @param
     *         licenseFile   file name
     *  
     *  @return
     *         license text
     * 
     */
    public static String getLicenseText(String licenseFile) {
        // Load in default license
        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new FileReader(licenseFile))) {
            String lineIn;
            while ((lineIn = br.readLine()) != null) {
                sb.append(lineIn).append('\n');
            }
        } catch (IOException e) {
            lazyError("Can't load configuration", e);

            // FIXME: Maybe something more graceful here, but with the
           // configuration we can't do anything
            throw new IllegalStateException("Failed to read default license.", e);
        }
        license = sb.toString();
        return license;
    }

    /**
     * Returns an enumeration of all the keys in the DSpace configuration
     * 
     * @return an enumeration of all the keys in the DSpace configuration
     */
    public static List<String> propertyNames() {
        return propertyNames(KERNEL);
    }
    
    /**
     * Returns an enumeration of all the keys in a module configuration
     * 
     * @param  module    module, or <code>null</code> for regular property  
     * 
     * @return an enumeration of all the keys in the module configuration,
     *         or <code>null</code> if the module does not exist.
     */
    public static List<String> propertyNames(String module) {
        Config modConfig = getConfig(module);
        List<String> names = new ArrayList<>();
        if (modConfig != null) {
            Iterator<Map.Entry<String, ConfigValue>> mapIter = modConfig.entrySet().iterator();
            while (mapIter.hasNext()) {
                names.add(mapIter.next().getKey());
            }
        } 
        return names;
        //return modConfig == null ? null : modConfig.entrySet().iterator();
    }

    /**
     * Get the site-wide default license that submitters need to grant
     * 
     * @return the default license
     */
    public static String getDefaultSubmissionLicense() {
        /*
        if (properties == null)
        {
            loadConfig(null);
        }
        */
        return license;
    }

    /**
     * Get the path for the news files.
     * 
     */
    public static String getNewsFilePath() {
        String filePath = ConfigManager.getProperty("dspace.dir")
                + File.separator + "config" + File.separator;
        return filePath;
    }

    /**
     * Reads news from a text file.
     * 
     * @param newsFile
     *        name of the news file to read in, relative to the news file path.
     */
    public static String readNewsFile(String newsFile) {
        String fileName = getNewsFilePath() + newsFile;
        StringBuilder text = new StringBuilder();

        try (FileInputStream fir = new FileInputStream(fileName);
            InputStreamReader ir = new InputStreamReader(fir, "UTF-8");
            BufferedReader br = new BufferedReader(ir)) {
            // retrieve existing news from file
            String lineIn;
            while ((lineIn = br.readLine()) != null) {
                text.append(lineIn);
            }
        } catch (IOException e) {
            lazyWarn("news_read: " + e.getLocalizedMessage());
        }
        return text.toString();
    }

    /**
     * Writes news to a text file.
     * 
     * @param newsFile
     *        name of the news file to read in, relative to the news file path.
     * @param news
     *            the text to be written to the file.
     */
    public static String writeNewsFile(String newsFile, String news) {
        String fileName = getNewsFilePath() + newsFile;

        try (FileOutputStream fos = new FileOutputStream(fileName);
             OutputStreamWriter osr = new OutputStreamWriter(fos, "UTF-8");
             PrintWriter out = new PrintWriter(osr)) {
             // write the news out to the appropriate file
            out.print(news);
        } catch (IOException e) {
            lazyWarn("news_write: " + e.getLocalizedMessage());
        }
        return news;
    }

    /**
     * Writes license to a text file.
     * 
     * @param licenseFile
     *            name for the file int which license will be written, 
     *            relative to the current directory.
     */
    public static void writeLicenseFile(String licenseFile, String newLicense) {
        try (FileOutputStream fos = new FileOutputStream(licenseFile);
             OutputStreamWriter osr = new OutputStreamWriter(fos, "UTF-8");
             PrintWriter out = new PrintWriter(osr)) {
            // write the news out to the appropriate file
            out.print(newLicense);
        } catch (IOException e) {
           lazyWarn("license_write: " + e.getLocalizedMessage());
        }
        license = newLicense;
     }

    private static File loadedFile = null;

    private static synchronized void loadModuleConfig(String module) {
        // try to find it in modules
        File modFile = new File(getProperty("dspace.dir") +
                           File.separator + "conf" +
                           File.separator + "modules" +
                           File.separator + module + ".conf");

        if (modFile.exists()) {
            Config modConfRaw = ConfigFactory.parseFile(modFile);
            // resolve against kernel config data
            Config modConf = modConfRaw.withFallback(getConfig(KERNEL)).resolve();
            moduleConfig.put(module, modConf);
        } else {
            // log invalid request
            lazyWarn("Requested configuration module: " + module + " not found");
        }
    }

    /**
     * Load named kernel configuration
     */
    private static synchronized void loadKernelConfig(File kernelConfigFile) {
        checkArgument(kernelConfigFile.exists(), 
                      "Kernel configuration file '%s' not found", kernelConfigFile.getAbsolutePath());
        Config kernelConf = ConfigFactory.parseFile(kernelConfigFile).resolve();
        moduleConfig.put(KERNEL, kernelConf);
    }

    /**
     * Load the base (kernel) DSpace configuration. Only does anything if
     * configuration not already loaded. Location of config file taken in from the
     * specified file, or default locations. Module configuration is loaded on-demand,
     * relative to coordinates determined from kernel configuration.
     * 
     * @param configFile
     *            The <code>kernel.conf</code> configuration file to use, or
     *            <code>null</code> to try default locations
     */
    public static synchronized void loadConfig(String configFile) {
        if (isConfigured()) {
            return;
        }
        
        String configProperty = null;
        try {
            configProperty = System.getProperty(KERNEL_CONF_PROP);
        } catch (SecurityException se) {
            // A security manager may stop us from accessing the system properties.
            // This isn't really a fatal error though, so catch and ignore
            //log.warn("Unable to access system properties, ignoring.", se);
        }
            
        // should only occur after a flush()
        if (loadedFile != null) {
            //log.info("Reloading current config file: " + loadedFile.getAbsolutePath());
            ;
        } else if (configFile != null) {
            //log.info("Loading provided config file: " + configFile);
                
            loadedFile = new File(configFile);
        }
        // Has the default configuration location been overridden?
        else if (configProperty != null) {
            //log.info("Loading system provided config property (-Ddspace.configuration): " + configProperty);
                
            // Load the overriding configuration
            loadedFile = new File(configProperty);
        }
        // Load configuration from default location
        else {
            URL url = ConfigManager.class.getResource("/" + KERNEL_CONF_FILE);
            if (url != null) {
                //log.info("Loading from classloader: " + url);
                    
                loadedFile = new File(url.getPath());
            }
        }
            
        checkNotNull(loadedFile, "Cannot find suitable kernel configuration");
        loadKernelConfig(loadedFile);

        /*
        // Load in default license
        File licenseFile = new File(getProperty("dspace.dir") + File.separator
                + "config" + File.separator + "default.license");

        FileInputStream  fir = null;
        InputStreamReader ir = null;
        BufferedReader br = null;
        try
        {
            
            fir = new FileInputStream(licenseFile);
            ir = new InputStreamReader(fir, "UTF-8");
            br = new BufferedReader(ir);
            String lineIn;
            license = "";

            while ((lineIn = br.readLine()) != null)
            {
                license = license + lineIn + '\n';
            }

            br.close();
            
        }
        catch (IOException e)
        {
            log.error("Can't load license: " + licenseFile.toString() , e);

            // FIXME: Maybe something more graceful here, but with the
            // configuration we can't do anything
            throw new IllegalStateException("Cannot load license: " + licenseFile.toString(),e);
        }
        finally
        {
            if (br != null)
            {
                try
                {
                    br.close();
                } 
                catch (IOException ioe)
                {                  
                }
            }

            if (ir != null)
            {
                try
                { 
                    ir.close();
                } 
                catch (IOException ioe)
                {             
                }
            }

            if (fir != null)
            {
                try
                {
                    fir.close();
                }
                catch (IOException ioe)
                {                
                }
            }
        }
        */

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
    		log = LoggerFactory.getLogger(ConfigManager.class);
    	}
    	log.info(message);
    }
    
    private static void lazyWarn(String message) {
    	if (log == null) {
    		log = LoggerFactory.getLogger(ConfigManager.class);
    	}
    	log.warn(message);
    }
    
    private static void lazyError(String message, Throwable t) {
    	if (log == null) {
    		log = LoggerFactory.getLogger(ConfigManager.class);
    	}
    	log.error(message, t);
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
        if ((argv.length == 2) && argv[0].equals("-property")) {
            String val = getProperty(argv[1]);

            if (val != null) {
                System.out.println(val);
            } else {
                System.out.println("");
            }

            System.exit(0);
        } else if ((argv.length == 4) && argv[0].equals("-module") &&
                                        argv[2].equals("-property")) {
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
