/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.webapi.info.domain;

import java.util.Date;

import javax.xml.bind.annotation.XmlType;
import javax.xml.bind.annotation.XmlSchemaType;

/**
 * A DSpace Module (system component)
 *
 * @author richardrodgers
 */

@XmlType(name="module")
public class Module {

    private String group;
    private String artifact;
    private String version;
    @XmlSchemaType(name="dateTime")
    private Date installed;

    public Module() {}

    public Module(String group, String artifact, String version, Date installed) {
        this.group = group;
        this.artifact = artifact;
        this.version = version;
        this.installed = installed;
    }

    public String getGroup() {
        return group;
    }

    public void setGroup(String group) {
        this.group = group;
    }

    public String getArtifact() {
        return artifact;
    }

    public void setArtifact(String artifact) {
        this.artifact = artifact;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public Date getInstalled() {
        return installed;
    }

    public void setInstalled(Date installed) {
        this.installed = installed;
    }
}
