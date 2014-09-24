/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.webapi.info.domain;

import javax.xml.bind.annotation.XmlType;

/**
 * A metadata field with occurrence count
 *
 * @author richardrodgers
 */

@XmlType(name="field")
public class Field {

    private String element;
    private String qualifier;
    private long count;

    public Field() {}

    public Field(String element, String qualifier, long count) {
        this.element = element;
        this.qualifier = qualifier;
        this.count = count;
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

    public long getCount() {
        return count;
    }

    public void setCount(long count) {
        this.count = count;
    }
}
