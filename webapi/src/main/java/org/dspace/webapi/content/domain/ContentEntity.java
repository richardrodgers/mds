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
import java.util.List;
import java.util.Map;

import org.dspace.content.DSpaceObject;
import org.dspace.webapi.content.Injectable;

/**
 * ContentEntity is the abstract base class for
 * all DSpaceObject entity classes: Community, Collection,
 * Item, Bitstream
 */

public abstract class ContentEntity implements Injectable {

    private String name;
    protected String handle;
    private URI selfUri;
    protected String parentHandle;
    private URI parentUri;

    public ContentEntity() {}

    public ContentEntity(DSpaceObject dso) {
        name = dso.getName();
        handle = dso.getHandle();
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

    @Override
    public Map<String, String> getUriInjections() {
        Map<String, String> injectionMap = new HashMap<>();
        return injectionMap;
    }

    @Override
    public void injectUri(String key, URI uri) {
    }

    @Override
    public Map<String, List<EntityRef>> getRefInjections() {
        return new HashMap<>();
    }

    @Override
    public void injectRefs(String key, List<EntityRef> refs) {}

}
