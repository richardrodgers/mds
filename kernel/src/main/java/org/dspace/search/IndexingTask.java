/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.search;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Set;

class IndexingTask {
	
    enum Action { ADD, UPDATE, DELETE, TX_BEGIN, TX_END, PURGE };

    private Action action;
    private Map<String, List<String>> fields;
    private Map<String, List<InputStream>> streams;

    IndexingTask(Action pAction) {
        action = pAction;
        fields = new HashMap<String, List<String>>();
    }
    
    void addField(String name, String value) {
    	List<String> values = fields.get(name);
    	if (values == null) {
    		values = new ArrayList<String>();
    		fields.put(name, values);
    	}
    	values.add(value);
    }
    
    void addFieldSet(Map<String, String> fieldSet) {
    	for (String key : fieldSet.keySet()) {
    		addField(key, fieldSet.get(key));
    	}
    }
    
    void addStream(String name, InputStream stream) {
    	if (streams == null) {
    		streams = new HashMap<String, List<InputStream>>();
    	}
    	List<InputStream> values = streams.get(name);
    	if (values == null) {
    		values = new ArrayList<InputStream>();
    		streams.put(name, values);
    	}
    	values.add(stream);
    }
    
    public Action getAction() {
    	return action;
    }
    
    public Set<String> getFieldKeys() {
    	return fields.keySet();
    }
    
    public List<String> getFieldValues(String key) {
    	List<String> ret = fields.get(key);
    	return (ret != null) ? ret : new ArrayList<String>();
    }
    
    public String getFieldValue(String key) {
    	List<String> ret = getFieldValues(key);
    	return (ret.size() > 0) ? ret.get(0) : null;
    }
    
    public Set<String> getStreamKeys() {
    	return (streams != null) ? streams.keySet() : new HashSet<String>();
    }
    
    public List<InputStream> getStreamValues(String key) {
    	List<InputStream> ret = streams.get(key);
    	return (ret != null) ? ret : new ArrayList<InputStream>();
    }
}
