/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.webapi.registry.domain;

import java.net.URI;
import java.sql.SQLException;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import javax.xml.bind.annotation.XmlRootElement;

import org.dspace.content.BitstreamFormat;
import org.dspace.webapi.Injectable;

@XmlRootElement(name="format")
public class FormatEntity implements Injectable {

    private int id;
    private String shortDescription;
    private String description;
    private String mimeType;
    private int supportLevel;
    private URI selfUri;

    public FormatEntity() {}

    public FormatEntity(BitstreamFormat format) throws SQLException {
        id = format.getID();
        shortDescription = format.getShortDescription();
        description = format.getDescription();
        mimeType = format.getMIMEType();
        supportLevel = format.getSupportLevel();
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getShortDescription() {
        return shortDescription;
    }

    public void setShortDescription(String shortDescription) {
        this.shortDescription = shortDescription;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getMimeType() {
        return mimeType;
    }

    public void setMimeType(String mimeType) {
        this.mimeType = mimeType;
    } 

    public int getSupportLevel() {
        return supportLevel;
    }

    public void setSupportLevel(int supportLevel) {
        this.supportLevel = supportLevel;
    }

    public URI getURI() {
        return selfUri;
    }

    public void setURI(URI uri) {
        selfUri = uri;
    }

    public void sync(BitstreamFormat format) throws SQLException {
        format.setShortDescription(getShortDescription());
        format.setDescription(getDescription());
        format.setMIMEType(getMimeType());
        format.setSupportLevel(getSupportLevel());
    }

    @Override
    public Map<String, String> getUriInjections() {
        Map<String, String> injectionMap = new HashMap<>();
        injectionMap.put("self", "format:" + id);
        return injectionMap;
    }

    @Override
    public void injectUri(String key, URI uri) {
        switch (key) {
            case "self": setURI(uri); break;
            default: break;
        }
    }
}
