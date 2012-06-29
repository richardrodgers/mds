/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.oaipmh;

import java.beans.Introspector;
import java.io.File;
import java.net.URL;
import java.net.URLConnection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.util.Enumeration;

import javax.servlet.ServletContextListener;
import javax.servlet.ServletContextEvent;
import javax.servlet.annotation.WebListener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.dspace.core.ConfigurationManager;
import org.dspace.storage.rdbms.DatabaseManager;

/**
 * Class to initialize / cleanup resources used by DSpace when the web application
 * is started or stopped
 */

@WebListener
public class DSpaceContextListener implements ServletContextListener
{
    private static Logger log = null;

    /**
     * The DSpace home parameter, this is the base where paths to the DSpace
     * configuration files can be calculated
     */
    public static final String DSPACE_HOME_PARAMETER = "dspaceHome";
    
    /**
     * Initialize any resources required by the application
     * @param event
     */
    @Override
    public void contextInitialized(ServletContextEvent event) {
        
        /**
         * Stage 1
         * 
         * Locate the dspace config
         */
        // first check the local per webapp parameter, then check the global parameter.
        String dspaceHome = event.getServletContext().getInitParameter(DSPACE_HOME_PARAMETER);
               
        // Finally, if no config parameter found throw an error
        if (dspaceHome == null || "".equals(dspaceHome)) {
            throw new IllegalStateException(
                    "\n\nDSpace has failed to initialize. This has occurred because it was unable to determine \n" +
                    "where the dspace.cfg file is located. The path to the configuration file should be stored \n" +
                    "in a context variable, '"+DSPACE_HOME_PARAMETER+"', in the global context. \n" +
                    "No context variable was found in either location.\n\n");
        }
            
        /**
         * Stage 2
         * 
         * Load the dspace config.
         * (Please rely on ConfigurationManager or Log4j to configure logging)
         * 
         */
        // Paths to the various config files
        String dspaceConfig = dspaceHome + File.separator + "conf" + File.separator + "kernel.cfg";   
        try  {
            ConfigurationManager.loadConfig(dspaceConfig);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException(
                    "\n\nDSpace has failed to initialize, during stage 2. Error while attempting to read the \n" +
                    "DSpace configuration file (Path: '"+dspaceConfig+"'). \n" +
                    "This has likely occurred because either the file does not exist, or it's permissions \n" +
                    "are set incorrectly, or the path to the configuration file is incorrect. The path to \n" +
                    "the DSpace configuration file is stored in a context variable, 'dspace-config', in \n" +
                    "either the local servlet or global context.\n\n",e);
        }
        
        /**
         * Stage 3 - initialize logging (which needed an initialized ConfigManager)
         */
        log = LoggerFactory.getLogger(DSpaceContextListener.class);
        
        // On Windows, URL caches can cause problems, particularly with undeployment
        // So, here we attempt to disable them if we detect that we are running on Windows
        try {
            String osName = System.getProperty("os.name");
            if (osName != null && osName.toLowerCase().contains("windows")) {
                URL url = new URL("http://localhost/");
                URLConnection urlConn = url.openConnection();
                urlConn.setDefaultUseCaches(false);
            }
        }
        // Any errors thrown in disabling the caches aren't significant to
        // the normal execution of the application, so we ignore them
        catch (RuntimeException e) {
            log.error(e.getMessage(), e);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
        
        /**
         * Stage 4 - set up environment for OAI-PMH servlet
         */
        String oaiProps = dspaceHome + File.separator + "conf" + File.separator + "oaicat.properties";
        log.info("Setting OAICat prop: " + oaiProps);
        event.getServletContext().setInitParameter("properties", oaiProps);
    }

    /**
     * Clean up resources used by the application when stopped
     * 
     * @param event
     */
    @Override
    public void contextDestroyed(ServletContextEvent event) {
        try {
            // Remove the database pool
            DatabaseManager.shutdown();

            // Clean out the introspector
            Introspector.flushCaches();

            // Remove any drivers registered by this classloader
            for (Enumeration e = DriverManager.getDrivers(); e.hasMoreElements();) {
                Driver driver = (Driver) e.nextElement();
                if (driver.getClass().getClassLoader() == getClass().getClassLoader()) {
                    DriverManager.deregisterDriver(driver);
                }
            }
        } catch (RuntimeException e) {
            log.error("Failed to cleanup ClassLoader for webapp", e);
        } catch (Exception e) {
            log.error("Failed to cleanup ClassLoader for webapp", e);
        }
    }
}
