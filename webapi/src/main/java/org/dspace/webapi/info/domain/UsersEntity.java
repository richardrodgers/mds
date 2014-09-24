/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.webapi.info.domain;

import javax.xml.bind.annotation.XmlRootElement;

/**
 * UsersEntity contains basic aggregate information about
 * repository users, viz Epeople and Groups. Currently,
 * reports only counts of each. 
 *
 * @author richardrodgers
 */

@XmlRootElement(name="users")
public class UsersEntity {

    private long epeople;
    private long groups;
    
    public UsersEntity() {}

    public UsersEntity(long epeople, long groups) {
        this.epeople = epeople;
        this.groups = groups;
    }

    public long getEPeople() {
        return epeople;
    }

    public void setEPeople(long epeople) {
         this.epeople = epeople;
    }

    public long getGroups() {
        return groups;
    }

    public void setGroups(long groups) {
         this.groups = groups;
    }
}
