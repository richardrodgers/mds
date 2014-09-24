/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.webapi.info.domain;

import java.net.URI;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;

import org.dspace.content.DSpaceObject;
import org.dspace.content.MDValue;

/**
 * FieldsEntity is a representation of a set of metadata
 * fields belonging to a schema.
 *
 * @author richardrodgers
 */

@XmlRootElement(name="fields")
public class FieldsEntity extends InfoEntity {

    private String schemaName;
    @XmlElementWrapper(name="fields")
    @XmlElement(name="field")
    private List<Field> fields = new ArrayList<>();

    public FieldsEntity() {}

    public FieldsEntity(String name, List<Field> fields) throws SQLException {
        this.schemaName = name;
        this.fields = fields;
    }

    public String getSchema() {
        return schemaName;
    }

    public void setSchema(String schemaName) {
        this.schemaName = schemaName;
    }

    public List<Field> getFields() {
        return fields;
    }

    public void setFields(List<Field> fields) {
        this.fields = fields;
    }
}
