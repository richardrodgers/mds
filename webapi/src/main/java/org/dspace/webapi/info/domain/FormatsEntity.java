/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.webapi.info.domain;

import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;

/**
 * FormatsEntity is a list of formats and the distribution
 * of bitstreams in them
 *
 * @author richardrodgers
 */

@XmlRootElement(name="formats")
public class FormatsEntity extends InfoEntity {

    @XmlElementWrapper(name="formatset")
    @XmlElement(name="format")
    private List<Format> formats = new ArrayList<>();

    public FormatsEntity() {}

    public FormatsEntity(List<Format> formats) {
        this.formats = formats;
    }

    public List<Format> getFormats() {
        return formats;
    }

    public void setFormats(List<Format> formats) {
        this.formats = formats;
    }
}
