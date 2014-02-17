/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.webapi.content.domain;

import java.net.URI;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

import javax.xml.bind.annotation.XmlRootElement;

import org.dspace.content.Bitstream;
import org.dspace.content.Item;

@XmlRootElement(name="bitstream")
public class BitstreamEntity extends ContentEntity {

    private String mediaPath;
    private URI mediaUrl;
    private String name;
    private long mediaSize;
    private String format;

    public BitstreamEntity() {}

    public BitstreamEntity(Bitstream bitstream) throws SQLException {
        super(bitstream);
        Item parent = (Item)bitstream.getParentObject();
        if (pid != null) {
            parentPid = parent.getHandle();
        }
        mediaPath = parentPid + "." + bitstream.getSequenceID() + "/media/" + bitstream.getName();
        mediaSize = bitstream.getSize();
        format = bitstream.getFormatDescription();
    }

    public URI getMediaUrl() {
        return mediaUrl;
    }

    public void setMediaUrl(URI mediaUrl) {
        this.mediaUrl = mediaUrl;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public long getMediaSize() {
        return mediaSize;
    }

    public void setMediaSize(long mediaSize) {
        this.mediaSize = mediaSize;
    }

    public String getFormat() {
        return format;
    }

    public void setFormat(String format) {
        this.format = format;
    }

    @Override
    public Map<String, String> getUriInjections() {
        Map<String, String> injectionMap = super.getUriInjections();
        injectionMap.put("media", mediaPath);
        return injectionMap;
    }

    @Override
    public void injectUri(String key, URI uri) {
        switch (key) {
            case "media": setMediaUrl(uri); break;
            default: super.injectUri(key, uri); break;
        }
    }
}
