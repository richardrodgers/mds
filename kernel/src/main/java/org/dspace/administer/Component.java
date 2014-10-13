/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.administer;

import java.sql.Timestamp;
import java.util.List;

import org.skife.jdbi.v2.Handle;
import org.skife.jdbi.v2.BeanMapper;

import org.dspace.core.Context;
    
/**
 * An installable unit in a DSpace instance, or dependency thereon.
 * Bean friendly.
 * 
 * @author richardrodgers
 */

public class Component {
    // primary key
    private int compId;
    // component type 0 = dspace module, 1 = third party jar
    private int compType;
    // maven coordinates
    private String groupId;
    private String artifactId;
    private String versionStr;
    // MD5 checksum of jar or war
    private String checksum;
    // dependency graph: list of all connecting node compIds, "-" separated
    private String graph;
    // update timestamp
    private Timestamp updated;

    public Component() {}

    public Component(int compId, int compType, String groupId, String artifactId, String versionStr, String checksum, String graph) {
        this.compId = compId;
        this.compType = compType;
        this.groupId = groupId;
        this.artifactId = artifactId;
        this.versionStr = versionStr;
        this.checksum = checksum;
        this.graph = graph;
    }

    public static List<Component> findAll(Handle handle) {
        return handle.createQuery("SELECT * FROM installation").
               map(new BeanMapper<Component>(Component.class)).list();        
    }

    public static List<Component> findAllType(Handle handle, int ctype) {
        return handle.createQuery("SELECT * FROM installation WHERE comptype = ?").
               bind(0, ctype).map(new BeanMapper<Component>(Component.class)).list();
    }

    public static Component findByCoordinates(Handle handle, String grpId, String artId) {
        return handle.createQuery("SELECT * FROM installation WHERE groupid = :gid AND artifactid = :aid").
               bind("gid", grpId).bind("aid", artId).
               map(new BeanMapper<Component>(Component.class)).first();
    }

    public void updateReferenceGraph(Handle handle, int node) {
        setGraph(graph + "-" + String.valueOf(node));
        handle.execute("UPDATE installation SET graph = :rc WHERE groupid = :gid AND artifactid = :aid",
                       graph, groupId, artifactId);
    }

    public void updateChecksum(Handle handle, String checksum) {
        setChecksum(checksum);
        handle.execute("UPDATE installation SET checksum = :csum, updated = :upd WHERE groupid = :gid AND artifactid = :aid",
                       checksum, new Timestamp(System.currentTimeMillis()), groupId, artifactId);
    }

    /*
    private void updateVersion(Handle handle, String grpId, String artId, String version, String checksum) {
        h.execute("UPDATE installation SET versionstr = :vsn, checksum = :csum, updated = :upd WHERE groupid = :gid AND artifactid = :aid",
                  version, checksum, new Timestamp(System.currentTimeMillis()), grpId, artId);
    }
    */

    public void delete(Handle handle) {
        handle.execute("DELETE FROM installation WHERE compid = :cid", getCompId());
    }

    // Bean verbiage
    public int getCompId() { return compId; }
    public void setCompId(int compId) { this.compId = compId; }
    public int getCompType() { return compType; }
    public void setCompType(int compType) { this.compType = compType; }
    public String getGroupId() { return groupId; }
    public void setGroupId(String groupId) { this.groupId = groupId; }
    public String getArtifactId() { return artifactId; }
    public void setArtifactId(String artifactId) { this.artifactId = artifactId; }
    public String getVersionStr() { return versionStr; }
    public void setVersionStr(String versionStr) { this.versionStr = versionStr; }
    public String getChecksum() { return checksum; }
    public void setChecksum(String checksum) { this.checksum = checksum; }
    public String getGraph() { return graph; }
    public void setGraph(String graph) { this.graph = graph; }
    public Timestamp getUpdated() { return updated; }
    public void setUpdated(Timestamp updated) { this.updated = updated; }
}
