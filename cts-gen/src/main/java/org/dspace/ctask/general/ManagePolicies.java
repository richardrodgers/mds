/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.ctask.general;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileSystems;
import java.nio.file.Paths;
import java.nio.file.PathMatcher;
import java.sql.SQLException;

import com.google.common.base.CharMatcher;

import org.dspace.authorize.AuthorizeManager;
import org.dspace.authorize.AuthorizeException;
import org.dspace.content.Bitstream;
import org.dspace.content.Bundle;
import org.dspace.content.DSpaceObject;
import org.dspace.content.Item;
import org.dspace.core.Constants;
import org.dspace.core.Context;
import org.dspace.curate.Curation;
import org.dspace.curate.AbstractCurationTask;
import org.dspace.curate.Mutative;
import org.dspace.eperson.Group;

import static org.dspace.curate.Curator.*;

/**
 * ManagePolicies assigns or removes Resource Policies on items or their
 * selected bundles or bitstreams. Task is a general replacement for the so-called 
 * 'Wildcard Policy Tool'. Task succeeds if policies set/removed, else fails.
 *
 * The task relies on 3 task properties and profile task naming:
 * (1) 'group' contains the name of the group to put in the resource policy
 * (2) 'action' contains the name (case-indifferent) of the policy action, e.g. 'read'
 * (3) 'path' specifies the policy-receiving object. It's called a path since it
 * represents content as a tree. The root is the Item, the Bundle is under it, and
 * the Bitstream under that. For example, to target all bitstreams, the path would be:
 * (where 'star'='*', since star literals in source code are parsed as 'end comment'):
 * 'item/star/star', or just JPEGs in the ORIGINAL bundle: 'item/ORIGINAL/star.jpg', etc
 * To simply target item resource policies, one would use the simple token 'item'.
 * The syntax used for the patterns is 'glob' format, rather than regex.
 * Finally, the task name should end with 'remove' if policies are to be removed, 'add' for
 * adding, and 'replace' for remove+add. A reasonable naming convention might be:
 * 'anon_read_remove' for a task that removes anonymous READ policies.
 *
 * NB: Glob parsing requires Java 7 library, so task is *not* Java 6 compatible.
 *
 * @author richardrodgers
 */

@Mutative
public class ManagePolicies extends AbstractCurationTask {   

    private int groupId = -1;
    private int action = -1;
    private String resType = null;
    private PathMatcher matcher = null;

    @Override
    public void init(Curation curation, String taskId) throws IOException {
        super.init(curation, taskId);
        try {
            resType = resourceType(taskProperty("path"));
            matcher = FileSystems.getDefault().getPathMatcher("glob:" + taskProperty("path"));
            action = Constants.getActionID(taskProperty("action").toUpperCase());
            if (action == -1) {
                throw new IOException("Invalid action: " + taskProperty("action"));
            }
        } catch (SQLException e) {}
    }

    /**
     * Perform the curation task upon passed DSO
     *
     * @param dso the DSpace object
     * @throws AuthorizeException
     * @throws IOException
     * @throws SQLException
     */
    @Override
    public int perform(DSpaceObject dso) throws AuthorizeException, IOException, SQLException {
        if (dso.getType() == Constants.ITEM) {
            Item item = (Item)dso;
            Context context = curationContext();
            if ("item".equals(resType)) {
                process(context, item);
            } else {
                for (Bundle bundle : item.getBundles()) {
                    if ("bundle".equals(resType)) {
                        if (matcher.matches(Paths.get("item", bundle.getName()))) {
                            process(context, bundle);
                        }
                    } else {
                        for (Bitstream bs : bundle.getBitstreams()) {
                            if (matcher.matches(Paths.get("item", bundle.getName(), bs.getName()))) {  
                                process(context, bs);
                            }
                        }
                    }
                }
            }
            setResult("Resource Policy managed for: " + item.getHandle());
            return CURATE_SUCCESS;
        } else {
            return CURATE_SKIP;
        }
    }

    private String resourceType(String path) {
        switch(CharMatcher.is('/').countIn(path)) {
            case 0: return "item";
            case 1: return "bundle";
            case 2: return "bitstream";
            default: return "item";
        }
    }

    private void process(Context context, DSpaceObject dso) throws AuthorizeException, SQLException {
        if (taskId.endsWith("remove") || taskId.endsWith("replace")) {
            AuthorizeManager.removeAllPolicies(context, dso);
        }
        if (taskId.endsWith("add") || taskId.endsWith("replace")) {
            AuthorizeManager.addPolicy(context, dso, action, getGroup(context)); 
        }
    }

    private Group getGroup(Context context) throws SQLException {
        // there is a small fiddle here due to the fact that Group lookups by Id hit
        // the context cache, whereas by name hit the DB. For usability, the task property
        // is expressed as a name, so on the first request, we look up by name, and then
        // remember the id (for subsequent uses)
        if (groupId != -1) {
            return Group.find(context, groupId);
        } else {
            Group group = Group.findByName(context, taskProperty("group"));
            if (group != null) {
                groupId = group.getID();
            } else {
                throw new SQLException("No such group: " + taskProperty("group"));
            }
            return group;
        }
    }
}
