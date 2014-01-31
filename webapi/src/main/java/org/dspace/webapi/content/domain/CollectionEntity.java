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
import java.util.Map;

import javax.xml.bind.annotation.XmlRootElement;

import org.dspace.content.Bitstream;
import org.dspace.content.Collection;
import org.dspace.content.Community;

@XmlRootElement(name="collection")
public class CollectionEntity extends ContentEntity {

    private String logoPath;
    private URI logo;
    private URI itemUri;

    public CollectionEntity() {}

    public CollectionEntity(Collection coll) throws SQLException {
        super(coll);
        Community parent = coll.getCommunities().get(0);
        if (pid != null) {
            parentPid = parent.getHandle();
        }
        Bitstream logoBS = coll.getLogo();
        if (logoBS != null) {
            logoPath = logoBS.getParentObject().getHandle() + "." + logoBS.getSequenceID() + "/media/" + logoBS.getName();
        }
    }

    public URI getLogo() {
        return logo;
    }

    public void setLogo(URI logo) {
        this.logo = logo;
    }

     public URI getItems() {
        return itemUri;
    }

    public void setItems(URI uri) {
        itemUri = uri;
    }

    @Override
    public Map<String, String> getUriInjections() {
        Map<String, String> injectionMap = super.getUriInjections();
        injectionMap.put("items", pid + ":items");
        if (logoPath != null) {
            injectionMap.put("logo", logoPath);
        }
        return injectionMap;
    }

    @Override
    public void injectUri(String key, URI uri) {
        switch (key) {
            case "items": setItems(uri); break;
            case "logo": setLogo(uri); break;
            default: super.injectUri(key, uri); break;
        }
    }
}
