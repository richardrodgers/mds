/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.storage.bitstore;

import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.dspace.core.Context;

/**
 * Cleans up asset store.
 * 
 * @author Peter Breton
 */
public class Cleanup
{
    // log 
    private static Logger log = LoggerFactory.getLogger(Cleanup.class);
    
    // option to leave database records intact
    @Option(name="-l", usage="Leave database records but delete file from assetstore")
    private boolean leaveDB;
    
    // help
    @Option(name="-h", usage="Print helpful message")
    private boolean help;  
    
    private Cleanup() {}

    /**
     * Cleans up asset store.
     * 
     * @param args -
     *            Command-line arguments
     */
    public static void main(String[] args) throws Exception
    {	
        Cleanup cleanup = new Cleanup();
        CmdLineParser parser = new CmdLineParser(cleanup);
        Context context = null;
        try
        {
            parser.parseArgument(args);
            if (! cleanup.help) {
            	log.info("Cleaning up asset store");
            	log.debug("leave db records = " + cleanup.leaveDB);
            	context = new Context();
            	BitstreamStorageManager.cleanup(context, ! cleanup.leaveDB);
                context.complete();
            } else {
            	parser.printUsage(System.err);
            }
            System.exit(0);
        } catch (CmdLineException clE) {
            System.err.println(clE.getMessage());
            parser.printUsage(System.err);
        } catch (Exception e) {
        	log.error("Exception", e);
            System.err.println(e.getMessage());
        } finally {
        	if (context != null && context.isValid()) {
        		context.abort();
        	}
        }
        System.exit(1);
    }
}
