/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.mxres;

import java.sql.SQLException;
import java.util.HashSet;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.dspace.content.DSpaceObject;
import org.dspace.core.Context;
import org.dspace.storage.rdbms.DatabaseManager;
import org.dspace.storage.rdbms.TableRow;
import org.dspace.storage.rdbms.TableRowIterator;

/**
 * ResourceMaps are the primary entry points into the Mapped 
 * Extensible Resources API - see {@link org.dspace.mxres.ExtensibleResource}.
 * The 'admin' side of the API manages the mappings between lookup
 * keys and instance IDs of particular resource types, as well as mappings
 * for resolution rules (applying to all instances of the type),
 * and finally the names of the builder classes for each resource type.
 * The 'client' side of the API is a single method to lookup a resource
 * instance using these mappings together with the resolution rules
 * (known as 'resource composition language' expressions). The intent of MXR
 * is to provide an integration point for such resource management, rather
 * than including all sorts of resources themselves in the core distribution.
 * Therefore only a few resource types are implemented in this package: metadata
 * templates, and metadata views. See {@link org.dspace.mxres.MetadataTemplate} 
 * See {@link org.dspace.mxres.MetdataView} Since the former (templates) were
 * provided as 'Item templates' as a core DSpace API service, they are retained
 * for rough backwards compatibility. Other resource types (e.g. submission
 * input forms, submission steps, workflow curation steps, etc), are intended
 * to be add-ons wired into this API.
 * 
 *  @author richardrodgers
 */

public class ResourceMap<T extends ExtensibleResource> {

    private static Logger log = LoggerFactory.getLogger(ResourceMap.class);
    private static final String ANY = "*";

    private Class<T> clazz;
    private Context context;

    /**
     * Constructor
     * 
     * @param clazz - the class of the resource
     * @param context - the DSpace context
     */
    public ResourceMap(Class<T> clazz, Context context) {
        this.clazz = clazz;
        this.context = context;
    }

    /**
     * Adds a mapping from passed key to passed resource instance Id
     * 
     * @param key - the lookup key for resource instance
     * @param resId - the ID of the resource instance
     */
    public void addResource(String key, String resId) throws SQLException {
        String curId = getResource("instance", key);
        if (curId == null) {
            TableRow row = DatabaseManager.create(context, "xresmap");
            row.setColumn("res_class", clazz.getName());
            row.setColumn("mapping", "instance");
            row.setColumn("res_key", key);
            row.setColumn("resource", resId);
            DatabaseManager.update(context, row);
        } else if (! curId.equals(resId)) {
            updateResource("instance", key, resId);
        }
    }

    /**
     * Adds a mapping from passed key to passed resolution rule
     * (RCL expression).
     * 
     * @param key - the lookup key for resource instance
     * @param rule - the RCL expression string
     */
    public void addRule(String key, String rule) throws SQLException {
        String curRule = getResource("rule", key);
        if (curRule == null) {
            TableRow row = DatabaseManager.create(context, "xresmap");
            row.setColumn("res_class", clazz.getName());
            row.setColumn("mapping", "rule");
            row.setColumn("res_key", key);
            row.setColumn("resource", rule);
            DatabaseManager.update(context, row);
        } else if (! curRule.equals(rule)) {
            updateResource("rule", key, rule);
        }
    }

    /**
     * Assigns a builder className for this resource type.
     * 
     * @param builder - the fully qualified class name of the builder
     */
    public void setBuilder(String builder) throws SQLException {
        // remove any existing builder
        if (getBuilder() != null) {
            DatabaseManager.updateQuery(context,
                 "DELETE FROM xresmap WHERE res_class = ? AND mapping = 'builder'",
                    clazz.getName());
        }
        TableRow row = DatabaseManager.create(context, "xresmap");
        row.setColumn("res_class", clazz.getName());
        row.setColumn("mapping", "builder");
        row.setColumn("resource", builder);
        DatabaseManager.update(context, row);
    }

    /**
     * Removes a mapping to a named resource ID
     * 
     * @param key - the lookup key for resource instance
     */
    public void removeResource(String key) throws SQLException {
        DatabaseManager.updateQuery(context,
                "DELETE FROM xresmap WHERE res_class = ? AND mapping = 'instance' AND res_key = ?",
                clazz.getName(), key);
    }

    /**
     * Removes a mapping to a named resolution rule
     * 
     * @param key - the lookup key for resource instance
     */
    public void removeRule(String key) throws SQLException {
        DatabaseManager.updateQuery(context,
            "DELETE FROM xresmap WHERE res_class = ? AND mapping = 'rule' AND res_key = ?",
            clazz.getName(), key);
    }

    /**
     * Obtains set of all keys for passed resource Id
     * 
     * @param resId - the ID of the resource instance
     */
    public Set<String> resourceKeysFor(String resId) throws SQLException {
        Set<String> keySet = new HashSet<>();
        try (TableRowIterator tri = DatabaseManager.queryTable(context, "xresmap",
                "SELECT * FROM xresmap WHERE res_class = ? AND mapping = 'instance' AND resource = ?",
                clazz.getName(), resId)) {
            while (tri.hasNext()) {
                keySet.add(tri.next().getStringColumn("res_key"));
            }
        }
        return keySet;
    }

    /**
     * Obtains set of all keys for passed rule
     * 
     * @param rule - the RCL expression string
     */
    public Set<String> ruleKeysFor(String rule) throws SQLException {
        Set<String> keySet = new HashSet<>();
        try (TableRowIterator tri = DatabaseManager.queryTable(context, "xresmap",
                "SELECT * FROM xresmap WHERE res_class = ? AND mapping = 'rule' AND resource = ?",
                clazz.getName(), rule)) {
            while (tri.hasNext()) {
                keySet.add(tri.next().getStringColumn("res_key"));
            }
        }
        return keySet;
    }

    /**
     * Obtains set of all keys with passed pattern
     * 
     * @param rule - the RCL expression string
     */
    public Set<String> ruleKeysLike(String keyPat) throws SQLException {
        Set<String> keySet = new HashSet<>();
        try (TableRowIterator tri = DatabaseManager.queryTable(context, "xresmap",
                "SELECT * FROM xresmap WHERE res_class = ? AND mapping = 'rule' AND res_key LIKE ?",
                clazz.getName(), keyPat + "%")) {
            while (tri.hasNext()) {
                keySet.add(tri.next().getStringColumn("res_key"));
            }
        }
        return keySet;
    }

    /**
     * Returns the resource appropriate for DSpaceObject in given scope.
     * This method constitutes the entire 'client' API of ResourceMaps.
     * 
     * @param dso - the DSpaceObject
     * @param scope - the application scope
     * @return res - a resource mapped to the scope
     */
    public T findResource(DSpaceObject dso, String scope) throws SQLException {
        // determine if there is a rule (rcl expression) for this scope
        String rcle = getResource("rule", scope);
        // if not, is there a default?
        if (rcle == null) {
            rcle = getResource("rule", ANY);
        }
        if (rcle != null) {
            // evaluate expression against current object and scope
            for (String token : rcle.split(",")) {
                int idx = token.indexOf(":");
                String name = token.substring(0, idx);
                DSpaceObject myDso = dso;
                while (myDso != null && name.startsWith("^")) {
                    myDso = myDso.getParentObject();
                    name = name.substring(1);
                }
                String value = token.substring(idx + 1);
                if ("?".equals(value)) {
                    // try to look up
                    value = (myDso != null) ? myDso.getAttribute(scope, name) : null;
                    if (value == null) {
                        continue;
                    }
                }
                return mappedResource(name + ":" + value);
            }
        } else {
            log.info("No rule found for key: " + scope);
        }
        return null;
    }

    public T mappedResource(String key) throws SQLException {
        // is there an instance of resource mapped?
        String resId = getResource("instance", key);
        if (resId != null) {
            // create a builder to construct the resource
            String builderName = getBuilder();
            if (builderName != null) {
                try {
                    ResourceBuilder builder = (ResourceBuilder)Class.forName(builderName).newInstance();
                    return (T)builder.build(context, resId);
                } catch (ClassNotFoundException cfnE) {
                    log.error("Class not found for builder: " + clazz.getName());
                } catch (InstantiationException | IllegalAccessException instE) {
                    log.error("Error instantiating builder: " + clazz.getName());
                }
            } else {
                log.error("No builder configured for resource class: "  + clazz.getName());
            }
        } else {
            log.info("No resource found for key: " + key);
        }
        return null;
    }

    private String getBuilder() throws SQLException {
        try (TableRowIterator tri = DatabaseManager.queryTable(context, "xresmap",
                "SELECT * FROM xresmap WHERE res_class = ? AND mapping = 'builder'",
                clazz.getName())) {
            return tri.hasNext() ? tri.next().getStringColumn("resource") : null;
        }
    }

    private String getResource(String mapping, String key) throws SQLException {
        try (TableRowIterator tri = DatabaseManager.queryTable(context, "xresmap",
                "SELECT * FROM xresmap WHERE res_class = ? AND mapping = ? AND res_key = ?",
                clazz.getName(), mapping, key)) {
            return tri.hasNext() ? tri.next().getStringColumn("resource") : null;
        }
    }

    private void updateResource(String mapping, String key, String value) throws SQLException {
        try (TableRowIterator tri = DatabaseManager.queryTable(context, "xresmap",
                "SELECT * FROM xresmap WHERE res_class = ? AND mapping = ? AND res_key = ?",
                clazz.getName(), mapping, key)) {
            if (tri.hasNext()) {
                TableRow row = tri.next();
                row.setColumn("resource", value);
                DatabaseManager.update(context, row);
           }
        }
    }
}
