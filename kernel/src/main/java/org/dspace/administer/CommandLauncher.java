/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.administer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.lang.reflect.Method;

import org.dspace.core.Context;

/**
 * DSpace command launcher. Reads commands from DB.
 * Adapted from ScriptLauncher (authors: Stuart Lewis & Mark Diggory)
 *
 * @author richardrodgers
 */
public class CommandLauncher
{
    /**
     * Execute the DSpace command launcher
     *
     * @param args Any parameters required to be passed to the commands it executes
     */
    public static void main(String[] args) throws Exception
    {
        // Check that there is at least one argument
        if (args.length < 1) {
            System.err.println("You must provide at least one command argument");
            System.exit(1);
        }
        
        List<String> cmdLineArgs = Arrays.asList(args);
        String cmdName = cmdLineArgs.remove(0);
        // Check for 'built-in' commands before trying to look command up
        // Is it the special case 'dsrun' where the user provides the class name?
        if ("dsrun".equals(cmdName)) {
            if (cmdLineArgs.size() < 1) {
                System.err.println("Error in launcher: Missing class name for dsrun");
                System.exit(1);
            }
            String className = cmdLineArgs.remove(0);
            invokeCommand(className, cmdLineArgs);
            System.exit(0);
        } 
        // OK - try to look up command
        Context ctx = null;
        try {
        	ctx = new Context();
        	ctx.turnOffAuthorisationSystem();
        	Command	command = Command.findByName(ctx, cmdName);
        	if (command == null) {
        		System.err.println("Error in launcher: unknown command: " + cmdName);
        		System.exit(1);
        	}
        	do {
        		List<String> effectiveArgs = buildArgList(command, cmdLineArgs);
        		invokeCommand(command.getClassName(), effectiveArgs);
        		command = command.getSuccessor(ctx);
        	} while (command != null);
        	ctx.complete();
        	System.exit(0);
        } catch (ClassNotFoundException cfnE) {
        	System.err.println("Error in command: Invalid class name: " + cfnE.getMessage());
        } catch (Exception e) {
        	Throwable cause = e.getCause();
            System.err.println("Exception: " + cause.getMessage());
        } finally {
        	if (ctx != null && ctx.isValid()) {
        		ctx.abort();
        	}
        }
        System.exit(1);
    }
    
    private static List<String> buildArgList(Command command, List<String> cmdLineArgs) {
    	List<String> retArgs = new ArrayList<String>();
    	if (command.getUserArgs()) {
    		// start with cmd-line args
    		retArgs.addAll(cmdLineArgs);
    	}
    	if (command.getArguments() != null) {
    		retArgs.addAll(Arrays.asList(command.getArguments().split("\\s+")));
    	}
    	return retArgs;
    }
    
    private static void invokeCommand(String className, List<String> useargs)
    		throws ClassNotFoundException, Exception {
        // Run the main() method
        Class target = Class.forName(className, true,
        						     Thread.currentThread().getContextClassLoader());
        Object[] arguments = {useargs};

        // Useful for debugging, so left in the code...
        /**System.out.print("About to execute: " + className);
        for (String param : useargs) {
            System.out.print(" " + param);
        }
        System.out.println("");**/

        Class[] argTypes = {useargs.getClass()};
        Method main = target.getMethod("main", argTypes);
        main.invoke(null, arguments);
    }
}
