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

    public BitstreamEntity() {}

    public BitstreamEntity(Bitstream bitstream) throws SQLException {
        super(bitstream.getName(), bitstream.getParentObject().getHandle() + "." + bitstream.getSequenceID());
        Item parent = (Item)bitstream.getParentObject();
        if (parent != null) {
            parentHandle = parent.getHandle();
        }
        mediaPath = parentHandle + "." + bitstream.getSequenceID() + "/media/" + bitstream.getName();
    }

    public URI getMediaUrl() {
        return mediaUrl;
    }

    public void setMediaUrl(URI mediaUrl) {
        this.mediaUrl = mediaUrl;
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
