/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.webapi;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

import javax.xml.bind.annotation.XmlRootElement;

/**
 * A reference to a resource entity
 *
 * @author richardrodgers
 */

@XmlRootElement(name="entityRef")
public class EntityRef implements Injectable {

    private String name;
    private String pid;
    private String entityType;
    private URI resourceUri;

    public EntityRef() {}

    public EntityRef(String name, String pid, String entityType) {
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
        return resourceUri;
    }

    public void setURI(URI uri) {
        resourceUri = uri;
    }

    @Override
    public Map<String, String> getUriInjections() {
        Map<String, String> injectionMap = new HashMap<>();
        injectionMap.put("uri", pid);
        return injectionMap;
    }

    @Override
    public void injectUri(String key, URI uri) {
        if ("uri".equals(key)) {
            setURI(uri);
        }
    }
}
