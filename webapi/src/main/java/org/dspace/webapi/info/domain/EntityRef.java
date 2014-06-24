/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.webapi.info.domain;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

import javax.xml.bind.annotation.XmlRootElement;

import org.dspace.webapi.Injectable;

/**
 * A reference to an Info resource entity
 *
 * @author richardrodgers
 */

@XmlRootElement(name="entityRef")
public class EntityRef implements Injectable {

    private String name;
    private String id;
    private String entityType;
    private URI resourceUri;

    public EntityRef() {}

    public EntityRef(String name, String id, String entityType) {
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

    public String getId() {
        return id;
    }

    public void setId(String id) {
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
        String uriStr = (entityType.equals("metadata")) ? entityType + ":" + id : id;
        injectionMap.put("uri", uriStr);
        return injectionMap;
    }

    @Override
    public void injectUri(String key, URI uri) {
        if ("uri".equals(key)) {
            setURI(uri);
        }
    }
}
