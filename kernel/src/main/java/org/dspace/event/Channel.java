/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.event;

import java.util.ArrayList;
import java.util.List;
import java.util.HashSet;

import com.google.common.eventbus.EventBus;

import org.dspace.event.ContentEvent.EventType;

/**
 * Channel is an abstraction over an event bus with
 * optional additional logic to transform event lists.
 * 
 * @author richardrodgers
 */
public class Channel {

    private final String name;
    private final EventBus bus;

    Channel(String name) {
        this.name = name;
        this.bus = new EventBus(name);
    }

    public String getId() { return name; }

    public void register(Object consumer) {
        bus.register(consumer);
    }

    public void propogate(List<ContentEvent> events) {
        // apply transforms then push to bus
        for (ContentEvent event : transform(events)) {
            bus.post(event);
        }
    }

    public List<ContentEvent> transform(List<ContentEvent> list) {
        // current default (debatable) is a 'post-delete filter' transform
        // which means that any events for an object encountered after
        // its deletion event are removed.
        // subclasses should override this method to insert other/additional logic
        HashSet<Integer> deletes = new HashSet<>();
        List<ContentEvent> filtered = new ArrayList<>();
        for (ContentEvent event : list) {
            Integer objId = event.getObject().getID();
            if (! deletes.contains(objId)) {
                filtered.add(event);
                if (event.getEventType().equals(EventType.DELETE)) {
                    deletes.add(objId);
                }
            }
        }
        return filtered;
    }
}
