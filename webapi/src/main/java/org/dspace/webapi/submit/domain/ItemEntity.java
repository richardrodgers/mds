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
import java.util.Map;

import javax.xml.bind.annotation.XmlRootElement;

import org.dspace.content.Collection;
import org.dspace.content.Item;
import org.dspace.content.WorkspaceItem;

@XmlRootElement(name="item")
public class ItemEntity extends SubmitEntity {

    private URI bitstreamUri;
    private URI mdspecUri;

    public ItemEntity() {}

    public ItemEntity(WorkspaceItem wsi) throws SQLException {
        super(wsi, null);
        Collection parent = wsi.getCollection();
        if (pid != null) {
            parentPid = parent.getHandle();
        }
    }

    public URI getBitstreamUri() {
        return bitstreamUri;
    }

    public void setBitstreamUri(URI bitstreamUri) {
        this.bitstreamUri = bitstreamUri;
    }

    public URI getMetadataSpecUri() {
        return mdspecUri;
    }

    public void setMetadataSpecUri(URI mdspecUri) {
        this.mdspecUri = mdspecUri;
    }


    @Override
    public Map<String, String> getUriInjections() {
        Map<String, String> injectionMap = super.getUriInjections();
        injectionMap.put("bitstreams", pid + ":bitstreams");
        injectionMap.put("mdspecs", pid + ":mdspecs");
        return injectionMap;
    }

    @Override
    public void injectUri(String key, URI uri) {
        switch (key) {
            case "bitstreams": setBitstreamUri(uri); break;
            case "mdspecs": setMetadataSpecUri(uri); break;
            default: super.injectUri(key, uri); break;
        }
    }
}
