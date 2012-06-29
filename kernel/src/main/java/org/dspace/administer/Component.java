/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.administer;

import java.sql.Timestamp;
    
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
    public void setUpdated(Timestamp date) { this.updated = updated; }
}
