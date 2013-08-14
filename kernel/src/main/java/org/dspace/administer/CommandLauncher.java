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
 * DSpace command launcher. Reads command data from DB.
 * Adapted from ScriptLauncher (author: Stuart Lewis)
 *
 * @author richardrodgers
 */
public class CommandLauncher {
	  // built-in commands are not defined in the DB, 
    // but are either internal (1st two) or known to exist on the classpath
    private static final String[] builtins = { "list", "help", "install", "update", "vizadmin" };
    /**
     * Execute the DSpace command launcher
     *
     * @param args Any parameters required to be passed to the commands it executes
     */
    public static void main(String[] args) throws Exception {
        // Check that there is at least one argument
        if (args.length < 1) {
            System.err.println("You must provide at least one command argument");
            System.exit(1);
        }
        
        List<String> cmdLineArgs = new ArrayList(Arrays.asList(args));
        String cmdName = cmdLineArgs.remove(0);
        int exitCode = 0;
        // Check for 'built-in' commands before trying to look command up
        if ("dsrun".equals(cmdName)) {
            if (cmdLineArgs.size() < 1) {
                System.err.println("Error in launcher: Missing class name for dsrun");
                exitCode = 1;
            } else {
                String className = cmdLineArgs.remove(0);
                invokeCommand(className, cmdLineArgs);
            }
        } else if ("install".equals(cmdName) || "update".equals(cmdName)) {
        	// Installer built-in simply because it is needed before DB exists
        	// so cannot be read from registry - thus hard-coded case here
        	// push the argument back to the command
        	cmdLineArgs.add(0, cmdName);
        	invokeCommand("org.dspace.administer.Installer", cmdLineArgs);
        } else if ("vizadmin".equals(cmdName)) {
            // launch the visual admin console
            invokeCommand("org.dspace.administer.VizAdmin", cmdLineArgs);
        } else {
        	try (Context ctx = new Context()) {
              ctx.turnOffAuthorisationSystem();
              if ("list".equals(cmdName)) {
                  // display list of commands accessible from launcher
                	System.out.println("Available commands:");
                	for (String cn : listCommands(ctx)) {
                	    System.out.println(cn);
                	}
              } else if ("help".equals(cmdName)) {
                	// Display a helpful message for a command
              		if (cmdLineArgs.size() == 0) {
              		    System.out.println("Use 'help <command>' for information on a command");
              		    System.out.println("Use 'list' to see all available commands");
              		} else {
              		    String cName = cmdLineArgs.get(0);
              			  if ("install".equals(cName) || "update".equals(cName)) {
              				    System.out.println("Use '" + cName + " <module>' to " + cName + " a module on your live system");
                      } else if ("vizadmin".equals(cName)) {
                          System.out.println("Use '" + cName + "' to launch an administration console");
              			  } else {
              				    Command cmd = Command.findByName(ctx, cName);
              				    if (cmd != null) {
              					      System.out.println(cName + ": " + cmd.getDescription());
              					      System.out.println("class: " + cmd.getClassName());
              					      String argList = cmd.getArguments();
              					      if (argList.length() > 0) {
              						        System.out.println("arguments: " + argList);
              					      }
              				    } else {
              					      System.out.println("Unknown command: " + cName);
              					      exitCode = 1;
              				    }
              			  }
              		}
              } else {
            		  Command	command = Command.findByName(ctx, cmdName);
            		  if (command == null) {
            			    System.out.println("Unknown command: " + cmdName);
            			    exitCode = 1;
            		  } else {
            			    do {
            				      List<String> effectiveArgs = buildArgList(command, cmdLineArgs);
            				      invokeCommand(command.getClassName(), effectiveArgs);
            				      command = command.getSuccessor(ctx);
            			    } while (command != null);
            		  }
              }
              ctx.complete();
        	} catch (ClassNotFoundException cfnE) {
        		System.err.println("Error in command: Invalid class name: " + cfnE.getMessage());
        		exitCode = 1;
        	} catch (Exception e) {
        		Throwable cause = e.getCause();
            if (cause != null) {
        		    System.err.println("Exception: " + cause.getMessage());
            } else {
              e.printStackTrace();
            }
        		exitCode = 1;
        	} 
        }
        System.exit(exitCode);
    }
    
    private static List<String> listCommands(Context ctx) throws Exception {
    	List<String> commands = new ArrayList<String>(Arrays.asList(builtins));
    	// then installed commands
    	for (Command cmd : Command.findAll(ctx)) {
    		if (cmd.isLaunchable()) {
    			commands.add(cmd.getName());
    		}
    	}
    	return commands;
    }
    
    private static List<String> buildArgList(Command command, List<String> cmdLineArgs) {
    	List<String> retArgs = new ArrayList<String>();
    	if (command.getUserArgs()) {
    		// start with cmd-line args
    		retArgs.addAll(cmdLineArgs);
    	}
    	if (command.getArguments() != null && command.getArguments().length() > 0) {
    		retArgs.addAll(Arrays.asList(command.getArguments().split("\\s+")));
    	}
    	return retArgs;
    }
    
    private static void invokeCommand(String className, List<String> useargs)
    		throws ClassNotFoundException, Exception {
        // Run the main() method
        Class target = Class.forName(className, true,
        						     Thread.currentThread().getContextClassLoader());
        String[] mainArgs = useargs.toArray(new String[useargs.size()]);
        Class[] argTypes = new Class[] { String[].class };
        Method main = target.getDeclaredMethod("main", argTypes);
        main.invoke(null, (Object)mainArgs);
    }
}
