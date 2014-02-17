/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.webapi.authz.domain;

import java.net.URI;
import java.sql.SQLException;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import javax.xml.bind.annotation.XmlRootElement;

import org.dspace.authorize.ResourcePolicy;
import org.dspace.webapi.Injectable;

@XmlRootElement(name="policy")
public class PolicyEntity implements Injectable {

    private int id;
    private int action;
    private long startDate;
    private long endDate;
    private URI selfUri;

    public PolicyEntity() {}

    public PolicyEntity(ResourcePolicy policy) throws SQLException {
        id = policy.getID();
        action = policy.getAction();
        startDate = policy.getStartDate().getTime();
        endDate = policy.getEndDate().getTime();
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getAction() {
        return action;
    }

    public void setAction(int action) {
        this.action = action;
    }

    public long getStartDate() {
        return startDate;
    }

    public void setStartDate(long startDate) {
        this.startDate = startDate;
    }

    public long getEndDate() {
        return endDate;
    }

    public void setEndDate(long endDate) {
        this.endDate = endDate;
    } 

    public URI getURI() {
        return selfUri;
    }

    public void setURI(URI uri) {
        selfUri = uri;
    }

    public void sync(ResourcePolicy policy) {
        policy.setResourceID(0);
        policy.setAction(action);
        policy.setStartDate(new Date(startDate));
        policy.setEndDate(new Date(endDate));
    }

    @Override
    public Map<String, String> getUriInjections() {
        Map<String, String> injectionMap = new HashMap<>();
        injectionMap.put("self", "policy:" + id);
        return injectionMap;
    }

    @Override
    public void injectUri(String key, URI uri) {
        switch (key) {
            case "self": setURI(uri); break;
            default: break;
        }
    }
}
