/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.webapi.curation.domain;

import javax.xml.bind.annotation.XmlRootElement;

/**
 * A reference to a resource group
 *
 * @author richardrodgers
 */

@XmlRootElement(name="groupRef")
public class GroupRef {

    private String name;
    private String description;
    private String groupType;

    public GroupRef() {}

    public GroupRef(String name, String description, String groupType) {
        this.name = name;
        this.description = description;
        this.groupType = groupType;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getGroupType() {
        return groupType;
    }

    public void setGroupType(String groupType) {
        this.groupType = groupType;
    }
}
