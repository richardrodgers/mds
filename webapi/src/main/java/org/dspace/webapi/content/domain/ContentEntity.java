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

import org.dspace.content.DSpaceObject;
import org.dspace.webapi.content.Injectable;

/**
 * ContentEntity is the abstract base class for
 * all DSpaceObject entity classes: Community, Collection,
 * Item, Bitstream
 *
 * @author richardrodgers
 */

public abstract class ContentEntity implements Injectable {

    private String name;
    protected String handle;
    private URI selfUri;
    protected String parentHandle;
    private URI parentUri;
    private URI mdUri;

    public ContentEntity() {}

    public ContentEntity(DSpaceObject dso) {
        name = dso.getName();
        handle = dso.getHandle();
    }

    public ContentEntity(String name, String handle) {
        this.name = name;
        this.handle = handle;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getHandle() {
        return handle;
    }

    public void setHandle(String handle) {
        this.handle = handle;
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
        injectionMap.put("self", handle);
        if (parentHandle != null) {
            injectionMap.put("parent", parentHandle);
        }
        injectionMap.put("mdsets", handle + ":mdsets");
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
