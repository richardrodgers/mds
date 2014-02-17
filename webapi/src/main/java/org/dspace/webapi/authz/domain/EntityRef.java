/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.webapi.authz.domain;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

import javax.xml.bind.annotation.XmlRootElement;

import org.dspace.webapi.Injectable;

/**
 * A reference to an AuthZ resource entity
 *
 * @author richardrodgers
 */

@XmlRootElement(name="entityRef")
public class EntityRef implements Injectable {

    private String name;
    private int id;
    private String entityType;
    private URI resourceUri;

    public EntityRef() {}

    public EntityRef(String name, int id, String entityType) {
        this.name = name;
        this.id = id;
        this.entityType = entityType;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
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
        injectionMap.put("uri", entityType + ":" + id);
        return injectionMap;
    }

    @Override
    public void injectUri(String key, URI uri) {
        if ("uri".equals(key)) {
            setURI(uri);
        }
    }
}
