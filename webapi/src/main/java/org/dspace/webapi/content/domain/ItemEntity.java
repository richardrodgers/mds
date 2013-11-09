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

import org.dspace.content.Collection;
import org.dspace.content.Item;

@XmlRootElement(name="item")
public class ItemEntity extends ContentEntity {

    private URI bitstreamUri;
    private URI filterUri;

    public ItemEntity() {}

    public ItemEntity(Item item) throws SQLException {
        super(item);
        Collection parent = item.getOwningCollection();
        if (parent != null) {
            parentHandle = parent.getHandle();
        }
    }

    public URI getBitstreams() {
        return bitstreamUri;
    }

    public void setBitstreams(URI uri) {
        bitstreamUri = uri;
    }

    public URI getFilters() {
        return filterUri;
    }

    public void setFilters(URI uri) {
        filterUri = uri;
    }

    @Override
    public Map<String, String> getUriInjections() {
        Map<String, String> injectionMap = new HashMap<>();
        if (parentHandle != null) {
            injectionMap.put("parent", "collection:" + parentHandle);
        }
        injectionMap.put("self", "item:" + handle);
        injectionMap.put("bitstreams", "item/" + handle + ":bitstreams");
        injectionMap.put("filters", "item/" + handle + ":filters");
        return injectionMap;
    }

    @Override
    public void injectUri(String key, URI uri) {
        switch (key) {
            case "self": setURI(uri); break;
            case "parent": setParentUri(uri); break;
            case "bitstreams": setBitstreams(uri); break;
            case "filters": setFilters(uri); break;
            default: break;
        }
    }
}
