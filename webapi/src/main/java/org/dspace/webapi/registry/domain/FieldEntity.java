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

import org.dspace.content.MetadataField;
import org.dspace.webapi.Injectable;

@XmlRootElement(name="field")
public class FieldEntity implements Injectable {

    private int id;
    private int schemaId;
    private String element;
    private String qualifier;
    private String scopeNote;
    private URI selfUri;
    private URI schemaUri;

    public FieldEntity() {}

    public FieldEntity(MetadataField field) throws SQLException {
        this.id = field.getFieldID();
        this.schemaId = field.getSchemaID();
        this.element = field.getElement();
        this.qualifier = field.getQualifier();
        this.scopeNote = field.getScopeNote();
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getSchemaId() {
        return schemaId;
    }

    public void setSchemaId(int schemaId) {
        this.schemaId = schemaId;
    }

    public String getElement() {
        return element;
    }

    public void setElement(String element) {
        this.element = element;
    }

    public String getQualifier() {
        return qualifier;
    }

    public void setQualifier(String qualifier) {
        this.qualifier = qualifier;
    }

    public String getScopeNote() {
        return scopeNote;
    }

    public void setScopeNote(String scopeNote) {
        this.scopeNote = scopeNote;
    }

    public URI getURI() {
        return selfUri;
    }

    public void setURI(URI uri) {
        selfUri = uri;
    }

    public URI getSchema() {
        return schemaUri;
    }

    public void setSchema(URI schemaUri) {
        schemaUri = schemaUri;
    }

    public void sync(MetadataField field) {
        field.setSchemaID(getSchemaId());
        field.setElement(getElement());
        field.setQualifier(getQualifier());
        field.setScopeNote(getScopeNote());
    }

    @Override
    public Map<String, String> getUriInjections() {
        Map<String, String> injectionMap = new HashMap<>();
        injectionMap.put("self", "field:" + id);
        injectionMap.put("schema", "schema:" + schemaId);
        return injectionMap;
    }

    @Override
    public void injectUri(String key, URI uri) {
        switch (key) {
            case "self": setURI(uri); break;
            case "schema": setSchema(uri); break;
            default: break;
        }
    }
}
