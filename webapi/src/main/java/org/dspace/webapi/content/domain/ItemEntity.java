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
import java.util.Map;

import javax.xml.bind.annotation.XmlRootElement;

import org.dspace.content.Collection;
import org.dspace.content.Item;

@XmlRootElement(name="item")
public class ItemEntity extends ContentEntity {

    private URI filterUri;
    private URI mdUri;
    private URI mdviewUri;
    private URI packageUri;

    public ItemEntity() {}

    public ItemEntity(Item item) throws SQLException {
        super(item);
        Collection parent = item.getOwningCollection();
        if (pid != null) {
            parentPid = parent.getHandle();
        }
    }

    public URI getFilters() {
        return filterUri;
    }

    public void setFilters(URI uri) {
        filterUri = uri;
    }

    public URI getMetadataUri() {
        return mdUri;
    }

    public void setMetadataUri(URI mdUri) {
        this.mdUri = mdUri;
    }

    public URI getMetadataViewUri() {
        return mdviewUri;
    }

    public void setMetadataViewUri(URI mdviewUri) {
        this.mdviewUri = mdviewUri;
    }

    public URI getPackageUri() {
        return packageUri;
    }

    public void setPackageUri(URI packageUri) {
        this.packageUri = packageUri;
    }

    @Override
    public Map<String, String> getUriInjections() {
        Map<String, String> injectionMap = super.getUriInjections();
        injectionMap.put("filters", pid + ":filters");
        injectionMap.put("mdsets", pid + ":mdsets");
        injectionMap.put("mdviews", pid + ":mdviews");
        injectionMap.put("packages", pid + ":packages");
        return injectionMap;
    }

    @Override
    public void injectUri(String key, URI uri) {
        switch (key) {
            case "filters": setFilters(uri); break;
            case "mdsets": setMetadataUri(uri); break;
            case "mdviews": setMetadataViewUri(uri); break;
            case "packages": setPackageUri(uri); break;
            default: super.injectUri(key, uri); break;
        }
    }
}
