/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.administer;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Objects;

import org.dspace.authorize.AuthorizeException;
import org.dspace.authorize.AuthorizeManager;
import org.dspace.core.Context;
import org.dspace.core.LogManager;
import org.dspace.storage.rdbms.DatabaseManager;
import org.dspace.storage.rdbms.TableRow;

/**
 * Class representing a specific binding of an argument list
 * to a class having an executable main method.
 * Used by CommandLauncher for uniform command-line invocation.
 * Commands are 'reference' objects (essentially read-only), so
 * lack full CRUD operations. One can load them (= create),
 * look them up by name, or remove (= delete) by name.
 * 
 * @author richardrodgers
 */
public class Command
{    
    /** log4j logger */
    private static Logger log = LoggerFactory.getLogger(Command.class);

    /** The row in the table representing this command */
    private TableRow myRow;

    /**
     * Construct a Command
     * 
     * @param context
     *            the context this object exists in
     * @param row
     *            the corresponding row in the table
     */
    private Command(TableRow row) {
        myRow = row;
    }

    /**
     * Return true if this object equals obj, false otherwise.
     * 
     * @param obj
     * @return true if ResourcePolicy objects are equal
     */
    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final Command other = (Command) obj;
        if (this.getID() != other.getID()) {
            return false;
        }
        if (!this.getName().equals(other.getName())) {
            return false;
        }
        return true;
    }

    /**
     * Return a hash code for this object.
     *
     * @return int hash of object
     */
    @Override
    public int hashCode() {
    	return Objects.hashCode(getID(), getName(), getDescription());
    }

    /**
     * Find the command by name.
     * 
     * @return Command, or {@code null} if none such exists.
     */
    
    public static Command findByName(Context context, String name) throws SQLException, AuthorizeException {
    	
        if (name == null) {
            return null;
        }
        
        // All names are stored as lowercase, so ensure that lookup string lowercased 
        TableRow row = DatabaseManager.findByUnique(context, "command",
                									"name", name.toLowerCase());
        
        return (row != null && row.getBooleanColumn("launchable")) ? new Command(row) : null;
    }

    /**
     * Load (create) a command
     * 
     * @param context
     *            DSpace context object
     */

    public static Command load(Context context, String name, String description,
    		                   String className, String arguments,
    		                   boolean launchable, boolean userArgs, int successor) throws SQLException,
            AuthorizeException {
        // authorized?
        if (!AuthorizeManager.isAdmin(context)) {
            throw new AuthorizeException("You must be an admin to create a Command");
        }

        // Create a table row
        TableRow row = DatabaseManager.create(context, "command");
        
        row.setColumn("name", name);
        row.setColumn("description", description);
        row.setColumn("class_name", className);
        row.setColumn("arguments", arguments);
        row.setColumn("launchable", launchable);
        row.setColumn("fwd_user_args", userArgs);
        row.setColumn("successor", successor);
        
        DatabaseManager.update(context, row);

        Command c = new Command(row);

        log.info(LogManager.getHeader(context, "create_command", "command_id="+ c.getID()));

        return c;
    }

    /**
     * Removes a command, including any chained successors.
     * 
     * @param context the DSpace context
     * @param name the name of the command to remove
     */

    public static void remove(Context context, String name) throws SQLException, AuthorizeException {
        // authorized?
        if (!AuthorizeManager.isAdmin(context)) {
            throw new AuthorizeException("You must be an admin to remove a Command");
        }
        
        Command command = findByName(context, name);
        if (command != null) {
        	do {
        		Command successor = command.getSuccessor(context);
        		// Remove command
        		DatabaseManager.delete(context, command.myRow);
        		log.info(LogManager.getHeader(context, "delete_command", "command_id=" + command.getID()));
        		command = successor;
        	} while (command != null);
        } else {
        	log.info(LogManager.getHeader(context, "delete_command", "name=" + name), "not found");
        }
    }
    
    /**
     * Get the Command's internal identifier
     * 
     * @return the internal identifier
     */
    public int getID() {
        return myRow.getIntColumn("command_id");
    }
  
    /**
     * Get the command name.
     * 
     * @return the name
     */
    public String getName() {
        return myRow.getStringColumn("name");
    }
    
    /**
     * Get the command's description
     * 
     * @return  description
     */
    public String getDescription() {
        return myRow.getStringColumn("description");
    }
           
    /**
     * Get the command's class name
     * 
     * @return  name of class
     */
    public String getClassName() {
        return myRow.getStringColumn("class_name");
    }
    
    /**
     * Get the command's argument list
     * 
     * @return  list of command arguments
     */
    public String getArguments() {
        return myRow.getStringColumn("arguments");
    }
         
    /**
     * Get the command's launchability flag
     * 
     * @return  launchable - true if can be launched
     *          a command is launchable iff head of successor chain
     *          or has no successors
     */
    public boolean isLaunchable() {
        return myRow.getBooleanColumn("launchable");
    }
    
    /**
     * Get the command's user-args flag
     * 
     * @param  userArgs - true if user arguments used
     */
    public boolean getUserArgs() {
        return myRow.getBooleanColumn("fwd_user_args");
    }
    
    /**
     * Get the command's successor command
     * 
     * @return  successor Command or null if no successor
     */
    public Command getSuccessor(Context context) throws SQLException {
    	int successorId = myRow.getIntColumn("successor");
    	return (successorId != -1) ? find(context, successorId) : null;
    }
    
    /**
     * Get a Command from the database.
     * 
     * @param context
     *            DSpace context object
     * @param id
     *            ID of the Command
     * 
     * @return the Command, or null if the ID is invalid.
     */
    private static Command find(Context context, int id) throws SQLException  {
        TableRow row = DatabaseManager.find(context, "command", id);
        return (row != null) ? new Command(row) : null;
    }
}
