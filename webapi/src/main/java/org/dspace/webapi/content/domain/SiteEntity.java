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

import org.dspace.content.Site;
import org.dspace.core.Constants;
import org.dspace.webapi.Injectable;

@XmlRootElement(name="site")
public class SiteEntity implements Injectable {

    private String name;
    private String pid;
    private String entityType;
    private URI selfUri;
    private URI logoUri;
    private URI communityUri;
    private URI mdUri;
    private URI mdviewUri;
    private URI packageUri;
    
    public SiteEntity() {}

    public SiteEntity(Site site) throws SQLException {

        name = site.getName();
        pid = "notsure";
        entityType = Constants.typeText[site.getType()].toLowerCase();
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPid() {
        return pid;
    }

    public void setPid(String pid) {
        this.pid = pid;
    }

    public String getEntityType() {
        return entityType;
    }

    public void setEntityType(String entityType) {
        this.entityType = entityType;
    }

    public URI getURI() {
        return selfUri;
    }

    public void setURI(URI uri) {
        selfUri = uri;
    }

    public URI getLogo() {
        return logoUri;
    }

    public void setLogo(URI logoUri) {
        this.logoUri = logoUri;
    }

    public URI getCommunities() {
        return communityUri;
    }

    public void setCommunities(URI communityUri) {
        this.communityUri = communityUri;
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
        Map<String, String> injectionMap = new HashMap<>();
        injectionMap.put("self", pid);
        injectionMap.put("communities", "site/communities");
        injectionMap.put("logo", "site/logo");
        injectionMap.put("mdsets", "site/mdsets");
        injectionMap.put("mdviews", "site/mdviews");
        injectionMap.put("packages", "site/packages");
        return injectionMap;
    }

    @Override
    public void injectUri(String key, URI uri) {
        switch (key) {
            case "self": setURI(uri); break;
            case "communities": setCommunities(uri); break;
            case "logo": setLogo(uri); break;
            case "mdsets": setMetadataUri(uri); break;
            case "mdviews": setMetadataViewUri(uri); break;
            case "packages": setPackageUri(uri); break;
            default: break;
        }
    }
}
