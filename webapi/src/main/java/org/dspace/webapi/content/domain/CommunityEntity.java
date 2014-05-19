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
import org.dspace.content.Community;

@XmlRootElement(name="community")
public class CommunityEntity extends ContentEntity {

    private String logoPath;
    private URI logo;
    private URI subcommunityUri;
    private URI collectionUri;
    private URI mdUri;
    private URI mdviewUri;
    private URI packageUri;
    
    public CommunityEntity() {}

    public CommunityEntity(Community comm) throws SQLException {
        super(comm);
        Community parent = comm.getParentCommunity();
        if (parent != null) {
            parentPid = parent.getHandle();
        }

        Bitstream logoBS = comm.getLogo();
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
        injectionMap.put("collections", pid + ":collections");
        injectionMap.put("subcommunities", pid + ":subcommunities");
        if (logoPath != null) {
            injectionMap.put("logo", logoPath);
        }
        injectionMap.put("mdsets", pid + ":mdsets");
        injectionMap.put("mdviews", pid + ":mdviews");
        injectionMap.put("packages", pid + ":packages");
        return injectionMap;
    }

    @Override
    public void injectUri(String key, URI uri) {
        switch (key) {
            case "collections": setCollections(uri); break;
            case "subcommunities": setSubcommunities(uri); break;
            case "logo": setLogo(uri); break;
            case "mdsets": setMetadataUri(uri); break;
            case "mdviews": setMetadataViewUri(uri); break;
            case "packages": setPackageUri(uri); break;
            default: super.injectUri(key, uri); break;
        }
    }
}
