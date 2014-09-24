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
 * A Bitstream format
 *
 * @author richardrodgers
 */

@XmlType(name="format")
public class Format {

    private String name;
    private String mimeType;
    private long count;

    public Format() {}

    public Format(String name, String mimeType, long count) {
        this.name = name;
        this.mimeType = mimeType;
        this.count = count;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getMimeType() {
        return mimeType;
    }

    public void setMimeType(String mimeType) {
        this.mimeType = mimeType;
    }

    public long getCount() {
        return count;
    }

    public void setCount(long count) {
        this.count = count;
    }
}
