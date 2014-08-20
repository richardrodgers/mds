/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.webapi.submit.domain;

import java.net.URI;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

import javax.xml.bind.annotation.XmlSeeAlso;

import org.dspace.content.Bitstream;
import org.dspace.content.WorkspaceItem;
import org.dspace.core.Constants;
import org.dspace.webapi.Injectable;

/**
 * SubmitEntity is the abstract base class for
 * all DSpaceObject submission entity classes: Item, Bitstream, Spec, etc
 *
 * @author richardrodgers
 */

@XmlSeeAlso({ItemEntity.class, BitstreamEntity.class, MetadataEntity.class, SpecEntity.class})
public abstract class SubmitEntity implements Injectable {

    private String name;
    protected String pid;
    private String entityType;
    private URI selfUri;
    protected String parentPid;
    private URI parentUri;

    public SubmitEntity() {}

    public SubmitEntity(WorkspaceItem wsi, Bitstream bs) throws SQLException {
        if (bs != null) {
            name = bs.getName();
            pid = String.valueOf(wsi.getID()) + "." + bs.getSequenceID();
            entityType = Constants.typeText[Constants.BITSTREAM].toLowerCase();
        } else {
            name = "submission";
            pid = String.valueOf(wsi.getID());
            entityType = Constants.typeText[Constants.ITEM].toLowerCase();
        }
    }

    public SubmitEntity(String name, String pid, String entityType) {
        this.name = name;
        this.pid = pid;
        this.entityType = entityType;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPid() {
        return pid;
    }

    public void setPid(String pid) {
        this.pid = pid;
    }

    public String getEntityType() {
        return entityType;
    }

    public void setEntityType(String entityType) {
        this.entityType = entityType;
    }

    public URI getURI() {
        return selfUri;
    }

    public void setURI(URI uri) {
        selfUri = uri;
    }

    public URI getParentUri() {
        return parentUri;
    }

    public void setParentUri(URI parentUri) {
        this.parentUri = parentUri;
    }

    @Override
    public Map<String, String> getUriInjections() {
        Map<String, String> injectionMap = new HashMap<>();
        injectionMap.put("self", pid);
        if (parentPid != null) {
            injectionMap.put("parent", parentPid);
        }
        return injectionMap;
    }

    @Override
    public void injectUri(String key, URI uri) {
        switch (key) {
            case "self": setURI(uri); break;
            case "parent": setParentUri(uri); break;
            default: break;
        }
    }
}
