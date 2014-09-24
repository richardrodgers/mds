/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.webapi.submit.domain;

import javax.xml.bind.annotation.XmlType;
import javax.xml.bind.annotation.XmlAttribute;

/**
 * A metadata statement (assertion)
 *
 * @author richardrodgers
 */

@XmlType(name="statement")
public class Statement {

    private String element;
    private String qualifier;
    private String value;
    @XmlAttribute
    private String language;

    public Statement() {}

    public Statement(String element, String qualifier, String language, String value) {
        this.element = element;
        this.qualifier = qualifier;
        this.language = language;
        this.value = value;
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
