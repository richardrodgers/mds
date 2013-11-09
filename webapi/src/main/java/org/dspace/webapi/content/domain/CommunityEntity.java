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
import org.dspace.content.Collection;
import org.dspace.content.Community;

@XmlRootElement(name="community")
public class CommunityEntity extends ContentEntity {

    private String logoPath;
    private URI logo;
    private URI subcommunityUri;
    private URI collectionUri;
    
    public CommunityEntity() {}

    public CommunityEntity(Community comm) throws SQLException {
        super(comm);
        Community parent = comm.getParentCommunity();
        if (parent != null) {
            parentHandle = parent.getHandle();
        }

        Bitstream logoBS = comm.getLogo();
        if (logoBS != null) {
            logoPath = logoBS.getParentObject().getHandle() + "/" + logoBS.getSequenceID() + "/" + logoBS.getName();
        }
    }

    public URI getLogo() {
        return logo;
    }

    public void setLogo(URI logo) {
        this.logo = logo;
    }

    public URI getCollections() {
        return collectionUri;
    }

    public void setCollections(URI collectionUri) {
        this.collectionUri = collectionUri;
    }

    public URI getSubcommunities() {
        return subcommunityUri;
    }

    public void setSubcommunities(URI subcommunityUri) {
        this.subcommunityUri = subcommunityUri;
    }

    @Override
    public Map<String, String> getUriInjections() {
        Map<String, String> injectionMap = new HashMap<>();
        if (parentHandle != null) {
            injectionMap.put("parent", "community:" + parentHandle);
        }
        injectionMap.put("collections", "community/" + handle + ":collections");
        injectionMap.put("subcommunities", "community/" + handle + ":subcommunities");
        injectionMap.put("self", "community:" + handle);
        if (logoPath != null) {
            injectionMap.put("logo", "bitstream:" + logoPath);
        }
        return injectionMap;
    }

    @Override
    public void injectUri(String key, URI uri) {
        switch (key) {
            case "self": setURI(uri); break;
            case "parent": setParentUri(uri); break;
            case "collections": setCollections(uri); break;
            case "subcommunities": setSubcommunities(uri); break;
            case "logo": setLogo(uri); break;
            default: break;
        }
    }
}
