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
import org.dspace.eperson.Group;
import org.dspace.webapi.Injectable;

@XmlRootElement(name="link")
public class LinkEntity implements Injectable {

    private int sourceId;
    private int targetId;
    private String targetType;
    private String name;
    private URI selfUri;
    private URI sourceUri;
    private URI targetUri;

    public LinkEntity() {}

    public LinkEntity(Group group, EPerson eperson) throws SQLException {
        this.sourceId = group.getID();
        this.name = group.getName();
        this.targetId = eperson.getID();
        this.targetType = "eperson";
    }

    public LinkEntity(Group group, Group tgroup) throws SQLException {
        this.sourceId = group.getID();
        this.name = group.getName();
        this.targetId = tgroup.getID();
        this.targetType = "group";
    }

    public LinkEntity(int sourceId, int targetId, String targetType) {
        this.sourceId = sourceId;
        this.name = "link";
        this.targetId = targetId;
        this.targetType = targetType;
    }

    public int getSourceId() {
        return sourceId;
    }

    public void setSourceId(int sourceId) {
        this.sourceId = sourceId;
    }

    public int getTargetId() {
        return targetId;
    }

    public void setTargetId(int targetId) {
        this.targetId = targetId;
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

    public URI getSource() {
        return sourceUri;
    }

    public void setSource(URI uri) {
        sourceUri = uri;
    }

    public URI getTarget() {
        return targetUri;
    }

    public void setTarget(URI uri) {
        targetUri = uri;
    }

    @Override
    public Map<String, String> getUriInjections() {
        Map<String, String> injectionMap = new HashMap<>();
        injectionMap.put("self", "group:" + sourceId + ":" + targetType + ":" + targetId);
        injectionMap.put("source", "group:" + sourceId);
        injectionMap.put("target", targetType + ":" + targetId);
        return injectionMap;
    }

    @Override
    public void injectUri(String key, URI uri) {
        switch (key) {
            case "self": setURI(uri); break;
            case "source": setSource(uri); break;
            case "target": setTarget(uri); break;
            default: break;
        }
    }
}
