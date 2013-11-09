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

import javax.xml.bind.annotation.XmlRootElement;

import org.dspace.content.Bitstream;
import org.dspace.content.Item;

@XmlRootElement(name="bitstream")
public class BitstreamEntity extends ContentEntity {

    private String accessPath;
    private URI accessUrl;

    public BitstreamEntity() {}

    public BitstreamEntity(Bitstream bitstream) throws SQLException {
        super(bitstream);
        Item parent = (Item)bitstream.getParentObject();
        if (parent != null) {
            parentHandle = parent.getHandle();
        }
        accessPath = parentHandle + "/" + bitstream.getSequenceID() + "/" + bitstream.getName();
    }

    public URI getAccessUrl() {
        return accessUrl;
    }

    public void setAccessUrl(URI accessUrl) {
        this.accessUrl = accessUrl;
    }

    @Override
    public Map<String, String> getUriInjections() {
        Map<String, String> injectionMap = new HashMap<>();
        if (parentHandle != null) {
            injectionMap.put("parent", "item:" + parentHandle);
        }
        injectionMap.put("self", "bitstream:" + handle);
        injectionMap.put("access", "bitstream:" + accessPath);
        return injectionMap;
    }

    @Override
    public void injectUri(String key, URI uri) {
        switch (key) {
            case "self": setURI(uri); break;
            case "parent": setParentUri(uri); break;
            case "access": setAccessUrl(uri); break;
            default: super.injectUri(key, uri); break;
        }
    }
}
