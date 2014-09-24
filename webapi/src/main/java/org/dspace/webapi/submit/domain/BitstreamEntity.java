/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.webapi.submit.domain;

import java.net.URI;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

import javax.xml.bind.annotation.XmlRootElement;

import org.dspace.content.Bitstream;
import org.dspace.content.Item;
import org.dspace.content.WorkspaceItem;

@XmlRootElement(name="bitstream")
public class BitstreamEntity extends SubmitEntity {

    private String mediaPath;
    private URI mediaUrl;
    private String name;
    private long mediaSize;
    private String format;
    private URI mdUri;
    private URI mdviewUri;
    private URI packageUri;

    public BitstreamEntity() {}

    public BitstreamEntity(WorkspaceItem wsi, Bitstream bitstream) throws SQLException {
        super(wsi, bitstream);
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

    public URI getMetadataUri() {
        return mdUri;
    }

    public void setMetadataUri(URI mdUri) {
        this.mdUri = mdUri;
    }

    public URI getMetadataViewUri() {
        return mdviewUri;
    }

    public void setMetadataViewUri(URI mdviewUri) {
        this.mdviewUri = mdviewUri;
    }

    public URI getPackageUri() {
        return packageUri;
    }

    public void setPackageUri(URI packageUri) {
        this.packageUri = packageUri;
    }

    @Override
    public Map<String, String> getUriInjections() {
        Map<String, String> injectionMap = super.getUriInjections();
        String[] parts = pid.split("\\.");
        String path = parts[0] + "/bitstream/" + parts[1];
        injectionMap.put("media", mediaPath);
        injectionMap.put("mdsets", path + ":mdsets");
        injectionMap.put("mdviews", path + ":mdviews");
        injectionMap.put("packages", path + ":packages");
        return injectionMap;
    }

    @Override
    public void injectUri(String key, URI uri) {
        switch (key) {
            case "media": setMediaUrl(uri); break;
            case "mdsets": setMetadataUri(uri); break;
            case "mdviews": setMetadataViewUri(uri); break;
            case "packages": setPackageUri(uri); break;
            default: super.injectUri(key, uri); break;
        }
    }
}
