/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.content;

import org.dspace.core.Context;

/**
 * Base class for Lifecycle Events
 * 
 * @author richardrodgers
 */

public class LifecycleEvent {

    /** The DSpaceObject */
    private final DSpaceObject dso;

    /** the lifecycle event name */
    private final String eventName;

    /**
     * Construct an Event from a DSpaceObject
     * 
     * @param dso
     *            the DSpaceObject
     * @param eventName
     *            the lifecycle phase name
     */
    public LifecycleEvent(DSpaceObject dso, String eventName) {
        this.dso = dso;
        this.eventName = eventName;
    }

    public DSpaceObject getObject() {
        return dso;
    }

    public String getEventName() {
        return eventName;
    }

    public Context getContext() {
        return dso.context;
    }

}
