/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.webapi.info.domain;

import javax.xml.bind.annotation.XmlRootElement;

/**
 * AssetsEntity contains basic aggregate information about
 * the repository assets, viz commmunity, collection, item
 * and bitstream counts, and asset storage used.
 *
 * @author richardrodgers
 */

@XmlRootElement(name="assets")
public class AssetsEntity {

    private long communities;
    private long collections;
    private long items;
    private long bitstreams;
    private long storage;

    public AssetsEntity() {}

    public AssetsEntity(long communities, long collections, long items, long bitstreams, long storage) {
        this.communities = communities;
        this.collections = collections;
        this.items = items;
        this.bitstreams = bitstreams;
        this.storage = storage;
    }

    public long getCommunities() {
        return communities;
    }

    public void setCommunities(long communities) {
         this.communities = communities;
    }

    public long getCollections() {
        return collections;
    }

    public void setCollections(long collections) {
         this.collections = collections;
    }

    public long getItems() {
        return items;
    }

    public void setItems(long items) {
         this.items = items;
    }

    public long getBitstreams() {
        return bitstreams;
    }

    public void setBitstreams(long bitstreams) {
         this.bitstreams = bitstreams;
    }

    public long getStorage() {
        return storage;
    }

    public void setStorage(long storage) {
        this.storage = storage;
    }
}
