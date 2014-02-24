/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.webapi.authz.domain;

import java.net.URI;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

import javax.xml.bind.annotation.XmlRootElement;

import org.dspace.eperson.Group;
import org.dspace.webapi.Injectable;

@XmlRootElement(name="group")
public class GroupEntity implements Injectable {

    private int id;
    private String name;
    private URI selfUri;
    private URI membersUri;
    private URI groupsUri;

    public GroupEntity() {}

    public GroupEntity(Group group) throws SQLException {
        this.id = group.getID();
        this.name = group.getName();
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public URI getURI() {
        return selfUri;
    }

    public void setURI(URI uri) {
        selfUri = uri;
    }

    public URI getMembers() {
        return membersUri;
    }

    public void setMembers(URI uri) {
        membersUri = uri;
    }

    public URI getGroups() {
        return groupsUri;
    }

    public void setGroups(URI uri) {
        groupsUri = uri;
    }

    public void sync(Group group) {
        group.setName(getName());
    }

    @Override
    public Map<String, String> getUriInjections() {
        Map<String, String> injectionMap = new HashMap<>();
        injectionMap.put("self", "group:" + id);
        injectionMap.put("members", "group:" + id + ":members");
        injectionMap.put("groups", "group:" + id + ":groupmembers");
        return injectionMap;
    }

    @Override
    public void injectUri(String key, URI uri) {
        switch (key) {
            case "self": setURI(uri); break;
            case "members": setMembers(uri); break;
            case "groups": setGroups(uri); break;
            default: break;
        }
    }
}
