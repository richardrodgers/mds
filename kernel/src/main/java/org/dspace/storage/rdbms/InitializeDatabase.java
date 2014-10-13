/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.storage.rdbms;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.dspace.core.ConfigurationManager;

/**
 * Command-line executed class for initializing the DSpace database. This should
 * be invoked with a single argument, the filename of the database schema file.
 * 
 * @author Robert Tansley
 */
public class InitializeDatabase
{
    /** log4j category */
    private static Logger log = LoggerFactory.getLogger(InitializeDatabase.class);

    public static void main(String[] argv) {
        // Usage checks
        if (argv.length != 1) {
            log.warn("Schema file not specified");
            System.exit(1);
        }

        ConfigurationManager.loadConfig();
        log.info("Initializing Database");

        try {
            DatabaseManager.loadSql(getScript(argv[0]));          
            System.exit(0);
        } catch (Exception e) {
            log.error("Caught exception:", e);
            System.exit(1);
        }
    }

    /**
     * Attempt to get the named script, with the following rules:
     * etc/<db.name>/<name>
     * etc/<name>
     * <name>
     */
    private static FileReader getScript(String name) throws FileNotFoundException, IOException
    {
        String dbName = ConfigurationManager.getProperty("db.name");
        File myFile = null;
        
        if (dbName != null) {
            myFile = new File("db/" + dbName + "/" + name);
            if (myFile.exists()) {
                return new FileReader(myFile.getCanonicalPath());
            }
        }
        
        myFile = new File("db/" + name);
        if (myFile.exists()) {
            return new FileReader(myFile.getCanonicalPath());
        }
        
        return new FileReader(name);
    }
}
