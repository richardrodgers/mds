/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.search;

import java.io.InputStream;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;

/**
 * Container for index data and instructions to pass
 * to an indexer. Essentially a set of mappings from
 * field names to values or data streams.
 * 
 * @author richardrodgers
 */

public class IndexingTask {

    enum Action { ADD, UPDATE, DELETE, TX_BEGIN, TX_END, PURGE };

    private Action action;
    private Multimap<String, String> fields;
    private Multimap<String, InputStream> streams;

    IndexingTask(Action pAction) {
        action = pAction;
        fields = ArrayListMultimap.create();
    }
    
    void addField(String name, String value) {
        fields.put(name, value);
    }
    
    void addFieldSet(Map<String, String> fieldSet) {
        for (String key : fieldSet.keySet()) {
            addField(key, fieldSet.get(key));
        }
    }
    
    void addStream(String name, InputStream stream) {
        if (streams == null) {
            streams = ArrayListMultimap.create();
        }
        streams.put(name, stream);
    }
    
    public Action getAction() {
        return action;
    }
    
    public Set<String> getFieldKeys() {
        return fields.keySet();
    }
    
    public Collection<String> getFieldValues(String key) {
        return fields.get(key);
    }
    
    public String getFieldValue(String key) {
        Collection<String> ret = getFieldValues(key);
        return (ret.size() > 0) ? ret.iterator().next() : null;
    }
    
    public Set<String> getStreamKeys() {
        return (streams != null) ? streams.keySet() : new HashSet<String>();
    }
    
    public Collection<InputStream> getStreamValues(String key) {
        return (streams != null) ? streams.get(key) : new HashSet<InputStream>();
    }
}
