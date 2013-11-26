/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.webapi.curation.domain;

import java.util.List;

import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name="curationOrder")
public class CurationOrder {

    private String invoked;
    private String txScope;
    private int    cacheLimit;
    private String jrnFilter;
    private String queue;
    private List<String> tasks;

    public String getInvoked() {
        return invoked;
    }

    public void setInvoked(String invoked) {
        this.invoked = invoked;
    }

    public String getTransactionScope() {
        return txScope;
    }

    public void setTransactionScope(String txScope) {
        this.txScope = txScope;
    }

    public int getCacheLimit() {
        return cacheLimit;
    }

    public void setCacheLimit(int cacheLimit) {
        this.cacheLimit = cacheLimit;
    }

    public String getJournalFilter() {
        return jrnFilter;
    }

    public void setJournalFilter(String jrnFilter) {
        this.jrnFilter = jrnFilter;
    }

    public String getQueue() {
        return queue;
    }

    public void setQueue(String queue) {
        this.queue = queue;
    }

    public List<String> getTasks() {
        return tasks;
    }

    public void setTasks(List<String> tasks) {
        this.tasks = tasks;
    }
}
