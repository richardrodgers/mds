/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.event;

import org.dspace.content.DSpaceObject;
import org.dspace.core.Context;

/**
 * ContentEvents are used to express basic
 * lifecycle changes of content objects - creation,
 * modification, deletion, etc
 *
 * @author richardrodgers
 */
public class ContentEvent {

    public enum EventType {
        CREATE, MODIFY, DELETE, ADD, REMOVE, INSTALL, WITHDRAW, REINSTATE
    };

    private final Context context;
    private final DSpaceObject dso;
    private final EventType eventType;
    private final long timestamp;

    public ContentEvent(Context context, DSpaceObject dso, EventType eventType) {
        this.context = context;
        this.dso = dso;
        this.eventType = eventType;
        this.timestamp = System.currentTimeMillis();
    }

    public Context getContext() { return context; }
    public DSpaceObject getObject() { return dso; }
    public EventType getEventType() { return eventType; }
    public long getTimestamp() { return timestamp; }
}
