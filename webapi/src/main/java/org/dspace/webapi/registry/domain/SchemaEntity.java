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
import java.util.HashMap;
import java.util.Map;

import javax.xml.bind.annotation.XmlRootElement;

import org.dspace.content.MetadataSchema;
import org.dspace.webapi.Injectable;

@XmlRootElement(name="schema")
public class SchemaEntity implements Injectable {

    private int id;
    private String name;
    private String nameSpace;
    private URI selfUri;
    private URI fieldsUri;

    public SchemaEntity() {}

    public SchemaEntity(MetadataSchema schema) throws SQLException {
        id = schema.getSchemaID();
        name = schema.getName();
        nameSpace = schema.getNamespace();
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getNamespace() {
        return nameSpace;
    }

    public void setNamespace(String nameSpace) {
        this.nameSpace = nameSpace;
    }

    public URI getURI() {
        return selfUri;
    }

    public void setURI(URI uri) {
        selfUri = uri;
    }

    public URI getFields() {
        return fieldsUri;
    }

    public void setFields(URI uri) {
        fieldsUri = uri;
    }

    public void sync(MetadataSchema schema) {
        schema.setName(getName());
        schema.setNamespace(getNamespace());
    }

    @Override
    public Map<String, String> getUriInjections() {
        Map<String, String> injectionMap = new HashMap<>();
        injectionMap.put("self", "schema:" + id);
        injectionMap.put("fields", "schema:" + id + ":fields");
        return injectionMap;
    }

    @Override
    public void injectUri(String key, URI uri) {
        switch (key) {
            case "self": setURI(uri); break;
            case "fields": setFields(uri); break;
            default: break;
        }
    }
}
