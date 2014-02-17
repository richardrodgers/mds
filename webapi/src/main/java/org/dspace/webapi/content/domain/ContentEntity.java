/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.webapi.content.domain;

import java.net.URI;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

import javax.xml.bind.annotation.XmlSeeAlso;

import org.dspace.content.DSpaceObject;
import org.dspace.content.Bitstream;
import org.dspace.core.Constants;
import org.dspace.webapi.Injectable;

/**
 * ContentEntity is the abstract base class for
 * all DSpaceObject entity classes: Community, Collection,
 * Item, Bitstream
 *
 * @author richardrodgers
 */

@XmlSeeAlso({CommunityEntity.class, CollectionEntity.class, ItemEntity.class, BitstreamEntity.class, MetadataEntity.class, ViewEntity.class})
public abstract class ContentEntity implements Injectable {

    private String name;
    protected String pid;
    private String entityType;
    private URI selfUri;
    protected String parentPid;
    private URI parentUri;
    private URI mdUri;

    public ContentEntity() {}

    public ContentEntity(DSpaceObject dso) throws SQLException {
        name = dso.getName();
        if (dso.getType() != Constants.BITSTREAM) {
            pid = dso.getHandle();
        } else {
            Bitstream bs = (Bitstream)dso;
            pid = bs.getParentObject().getHandle() + "." + bs.getSequenceID();
        }
        entityType = Constants.typeText[dso.getType()].toLowerCase();
    }

    public ContentEntity(String name, String pid, String entityType) {
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

    public URI getMetadataUri() {
        return mdUri;
    }

    public void setMetadataUri(URI mdUri) {
        this.mdUri = mdUri;
    }

    @Override
    public Map<String, String> getUriInjections() {
        Map<String, String> injectionMap = new HashMap<>();
        injectionMap.put("self", pid);
        if (parentPid != null) {
            injectionMap.put("parent", parentPid);
        }
        injectionMap.put("mdsets", pid + ":mdsets");
        return injectionMap;
    }

    @Override
    public void injectUri(String key, URI uri) {
        switch (key) {
            case "self": setURI(uri); break;
            case "parent": setParentUri(uri); break;
            case "mdsets": setMetadataUri(uri); break;
            default: break;
        }
    }
}
