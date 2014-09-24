/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.webapi.content;

import java.io.InputStream;
import java.net.URI;

/**
 * MediaReader is a helper class containing attributes
 * of a Media File (Bitstream)
 *
 * @author richardrodgers
 */

public class MediaReader {

    private final InputStream octetStream;
    private final String mimeType;
    private final long size;

    public MediaReader(InputStream in, String mimeType, long size) {
        this.octetStream = in;
        this.mimeType = mimeType;
        this.size = size;
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
}
