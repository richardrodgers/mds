/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.webapi.submit.domain;

import java.util.List;

import javax.xml.bind.annotation.XmlType;
import javax.xml.bind.annotation.XmlAttribute;

/**
 * A metadata statement (assertion) with input specification instructions.
 *
 * @author richardrodgers
 */

@XmlType(name="specification")
public class Specification {

    private String fieldKey;
    private String label;
    private String description;
    private String cardinality;
    private String inputType;
    private boolean locked;
    private List<String> values;
    @XmlAttribute
    private String language;

    public Specification() {}

    public Specification(String fieldKey, String label, String description, String cardinality, String inputType, boolean locked, List<String> values, String language) {
        this.label = fieldKey;
        this.label = label;
        this.description = description;
        this.cardinality = cardinality;
        this.inputType = inputType;
        this.locked = locked;
        this.values = values;
        this.language = language;
    }

    public String getFieldKey() {
        return fieldKey;
    }

    public void setFieldKey(String fieldKey) {
        this.fieldKey = fieldKey;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getCardinality() {
        return cardinality;
    }

    public void setCardinality(String cardinality) {
        this.cardinality = cardinality;
    }

    public String getInputType() {
        return inputType;
    }

    public void setInputType(String inputType) {
        this.inputType = inputType;
    }

    public String getLanguage() {
        return language;
    }

    public List<String> getValues() {
        return values;
    }

    public void setValues(List<String> values) {
        this.values = values;
    }
}
