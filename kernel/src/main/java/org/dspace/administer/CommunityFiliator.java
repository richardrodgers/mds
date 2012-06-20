/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.administer;

import java.io.IOException;
import java.sql.SQLException;

import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;

import org.dspace.authorize.AuthorizeException;
import org.dspace.content.BoundedIterator;
import org.dspace.content.Collection;
import org.dspace.content.Community;
import org.dspace.core.Constants;
import org.dspace.core.Context;
import org.dspace.handle.HandleManager;
import org.dspace.storage.rdbms.DatabaseManager;

/**
 * A command-line tool for setting/removing community/sub-community
 * relationships, or moving collections from one commnity to another.
 * Takes community DB Id or handle arguments as inputs.
 * 
 * @author richadrodgers
 */

public class CommunityFiliator
{
	// context object
	private Context context;
	
	enum Action {set, remove, move}
	@Argument(usage="action to take - set, remove, or move")
	private Action action;
	
	// parent identifier (handle or DBId)
	@Option(name="-p", usage="parent community (handle or database ID)")
	private String parentId;
	
	// child identifier (handle or DBId)
	@Option(name="-c", usage="child community or collection (handle or database ID)")
	private String childId;
	
    public static void main(String[] args) throws Exception {
    	
        CommunityFiliator filiator = new CommunityFiliator();
        CmdLineParser parser = new CmdLineParser(filiator);
        try {
        	parser.parseArgument(args);
        } catch (CmdLineException clE) {
        	System.err.println(clE.getMessage());
        	parser.printUsage(System.err);
        }
    }
    
    public void doAction() throws Exception {   
        // ve are superuser!
        context.turnOffAuthorisationSystem();

        try
        {
            // validate and resolve the parent and child IDs into commmunities/collections
            Community parent = resolveCommunity(context, parentId);

            if (parent == null) {
                System.out.println("Error, parent community cannot be found: " + parentId);
                System.exit(1);
            }
            
            if (action.equals(Action.move)) {
            	Collection coll = resolveCollection(context, childId);
            	if (coll == null) {
                    System.out.println("Error, child collection cannot be found: " + childId);
                    System.exit(1);
                }
            	move(context, parent, coll);
            } else {
            	Community child = resolveCommunity(context, childId);
            	if (child == null) {
                    System.out.println("Error, child community cannot be found: " + childId);
                    System.exit(1);
                }

                if (action.equals(Action.set)) {
                    filiate(context, parent, child);
                } else if (action.equals(Action.remove)) {
                    defiliate(context, parent, child);
                }
            }
        }  catch (SQLException sqlE) {
            System.out.println("Error - SQL exception: " + sqlE.toString());
        } catch (AuthorizeException authE) {
            System.out.println("Error - Authorize exception: " + authE.toString());
        } catch (IOException ioE) {
            System.out.println("Error - IO exception: " + ioE.toString());
        } finally {
        	if (context != null) {
        		context.complete();
        	}
        }   
    }
    
    private CommunityFiliator() throws Exception {
    	context = new Context();
    }
    
    public static void move(Context c, Community parent, Collection child) 
        throws SQLException, AuthorizeException, IOException {
    	Community oldParent = child.getCommunities().get(0);
    	// first give child a new (additional) parent - orphans are killed off
    	parent.addCollection(child);
    	// now it's safe to remove old parent
    	oldParent.removeCollection(child);
    }

    public static void filiate(Context c, Community parent, Community child)
            throws SQLException, AuthorizeException, IOException {
        // check that a valid filiation would be established
        // first test - proposed child must currently be an orphan (i.e.
        // top-level)
        Community childDad = child.getParentCommunity();

        if (childDad != null) {
            System.out.println("Error, child community: " + child.getID()
                    + " already a child of: " + childDad.getID());
            System.exit(1);
        }

        // second test - circularity: parent's parents can't include proposed child
        for (Community grandParent : parent.getAllParents()) {
            if (grandParent.getID() == child.getID()) {
                System.out.println("Error, circular parentage - child is parent of parent");
                System.exit(1);
            }
        }

        // everthing's OK
        parent.addSubcommunity(child);

        System.out.println("Filiation complete. Community: '" + parent.getID()
                		+ "' is parent of community: '" + child.getID() + "'");
    }

    public void defiliate(Context c, Community parent, Community child)
            throws SQLException, AuthorizeException, IOException {
        // verify that child is indeed a child of parent
        boolean isChild = false;
        BoundedIterator<Community> kidIter = parent.getSubcommunities();
        while(kidIter.hasNext()) {
            if (kidIter.next().getID() == child.getID()) {
                isChild = true;
                break;
            }
        }
        kidIter.close();
        if (! isChild) {
            System.out.println("Error, child community not a child of parent community");
            System.exit(1);
        }

        // OK remove the mappings - but leave the community, which will become
        // top-level
        DatabaseManager.updateQuery(c,
                "DELETE FROM community2community WHERE parent_comm_id= ? "+
                "AND child_comm_id= ? ", parent.getID(), child.getID());

        System.out.println("Defiliation complete. Community: '" + child.getID()
                + "' is no longer a child of community: '" + parent.getID()
                + "'");
    }

    private Community resolveCommunity(Context c, String communityID)
            throws SQLException  {
        Community community = null;
        if (communityID.indexOf('/') != -1) {
            // has a / must be a handle
            community = (Community) HandleManager.resolveToObject(c, communityID);
            // ensure it's a community
            if ((community != null) && (community.getType() != Constants.COMMUNITY)) {
                community = null;
            }
        } else {
            community = Community.find(c, Integer.parseInt(communityID));
        }
        return community;
    }
    
    private Collection resolveCollection(Context c, String collectionID)
            throws SQLException  {
        Collection collection = null;
        if (collectionID.indexOf('/') != -1) {
            // has a / must be a handle
            collection = (Collection) HandleManager.resolveToObject(c, collectionID);
            // ensure it's a collection
            if ((collection != null) && (collection.getType() != Constants.COLLECTION))  {
                collection = null;
            }
        } else {
            collection = Collection.find(c, Integer.parseInt(collectionID));
        }
        return collection;
    }
}
