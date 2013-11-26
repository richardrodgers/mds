/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.webapi.content.domain;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

import javax.xml.bind.annotation.XmlRootElement;

import org.dspace.webapi.content.Injectable;

/**
 * A reference to a resource entity
 *
 * @author richardrodgers
 */

@XmlRootElement(name="entityRef")
public class EntityRef implements Injectable {

    private String name;
    private String handle;
    private String resourceType;
    private URI resourceUri;

    public EntityRef() {}

    public EntityRef(String name, String handle, String resourceType) {
        this.name = name;
        this.handle = handle;
        this.resourceType = resourceType;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getType() {
        return resourceType;
    }

    public void setType(String resourceType) {
        this.resourceType = resourceType;
    }

    public URI getURI() {
        return resourceUri;
    }

    public void setURI(URI uri) {
        resourceUri = uri;
    }

    @Override
    public Map<String, String> getUriInjections() {
        Map<String, String> injectionMap = new HashMap<>();
        injectionMap.put("uri", handle);
        return injectionMap;
    }

    @Override
    public void injectUri(String key, URI uri) {
        if ("uri".equals(key)) {
            setURI(uri);
        }
    }
}
