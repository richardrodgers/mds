/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.webapi.content.domain;

import javax.xml.bind.annotation.XmlType;
import javax.xml.bind.annotation.XmlAttribute;

/**
 * A metadata statement (assertion) with rendering labels, instructions.
 *
 * @author richardrodgers
 */

@XmlType(name="depiction")
public class Depiction {

    private String label;
    private String renderType;
    private String value;
    @XmlAttribute
    private String language;

    public Depiction() {}

    public Depiction(String label, String renderType, String language, String value) {
        this.label = label;
        this.renderType = renderType;
        this.language = language;
        this.value = value;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public String getRenderType() {
        return renderType;
    }

    public void setRenderType(String renderType) {
        this.renderType = renderType;
    }

    public String getLanguage() {
        return language;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }
}
