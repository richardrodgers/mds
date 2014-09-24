/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.webapi.submit;

import java.io.InputStream;
import java.net.URI;

/**
 * MediaWriter is a helper class containing attributes
 * of a Media File (Bitstream) being uploaded
 *
 * @author richardrodgers
 */

public class MediaWriter {

    private final InputStream octetStream;
    private final String mimeType;
    private final long size;
    private boolean created;

    public MediaWriter(String name, InputStream in) {
        this.octetStream = in;
        this.mimeType = null; //mimeType;
        this.size = 0L; //size;
    }

    public InputStream getStream() {
        return octetStream;
    }

    public String getMimeType() {
        return mimeType;
    }

    public long getSize() {
        return size;
    }

    public boolean resourceCreated() {
        return created;
    }
}
