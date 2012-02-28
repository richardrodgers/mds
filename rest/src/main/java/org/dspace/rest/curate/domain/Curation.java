/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.rest.curate.domain;

import java.util.List;

import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name="curation")
public class Curation {
	
	private String id;
	private String idType;
	private String invoker;
	private String invoked;
	private String txScope;
	private int    cacheLimit;
	private String queue;
	private List<Action> actions;
	
	public String getId() {
		return id;
	}
	
	public void setId(String id) {
		this.id = id;
	}
	
	public String getIdType() {
		return idType;
	}
	
	public void setIdType(String idType) {
		this.idType = idType;
	}
	
	public String getInvoker() {
		return invoker;
	}
	
	public void setInvoker(String invoker) {
		this.invoker = invoker;
	}
	
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
	
	public String getQueue() {
		return queue;
	}
	
	public void setQueue(String queue) {
		this.queue = queue;
	}
	
	public void addAction(Action action) {
		actions.add(action);
	}
	
	public int numberActions() {
		return actions.size();
	}
	
	public List<Action> getActions() {
		return actions;
	}
	
	public void setActions(List<Action> actions) {
		this.actions = actions;
	}
}
