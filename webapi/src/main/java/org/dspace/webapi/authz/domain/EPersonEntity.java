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

import org.dspace.eperson.EPerson;
import org.dspace.webapi.Injectable;

@XmlRootElement(name="eperson")
public class EPersonEntity implements Injectable {

    private int id;
    private String email;
    private String firstName;
    private String lastName;
    private URI selfUri;
    private URI groupsUri;

    public EPersonEntity() {}

    public EPersonEntity(EPerson eperson) throws SQLException {
        id = eperson.getID();
        email = eperson.getEmail();
        firstName = eperson.getFirstName();
        lastName = eperson.getLastName();
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public URI getURI() {
        return selfUri;
    }

    public void setURI(URI uri) {
        selfUri = uri;
    }

    public URI getGroups() {
        return groupsUri;
    }

    public void setGroups(URI uri) {
        groupsUri = uri;
    }

    public void sync(EPerson ep) {
        ep.setEmail(getEmail());
        ep.setFirstName(getFirstName());
        ep.setLastName(getLastName());
    }

    @Override
    public Map<String, String> getUriInjections() {
        Map<String, String> injectionMap = new HashMap<>();
        injectionMap.put("self", "eperson:" + id);
        injectionMap.put("groups", "eperson:" + id + ":groups");
        return injectionMap;
    }

    @Override
    public void injectUri(String key, URI uri) {
        switch (key) {
            case "self": setURI(uri); break;
            case "groups": setGroups(uri); break;
            default: break;
        }
    }
}
