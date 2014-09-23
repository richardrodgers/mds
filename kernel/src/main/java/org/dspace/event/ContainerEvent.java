/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.event;

import java.util.List;

import org.dspace.content.DSpaceObject;
import org.dspace.core.Context;

/**
 * ContainerEvents are used to express container
 * lifecycle changes of content objects - addition or removal
 * members
 * 
 * @author richardrodgers
 */
public class ContainerEvent extends ContentEvent {

    private final DSpaceObject member;

    public ContainerEvent(Context context, DSpaceObject dso, EventType eventType, DSpaceObject member) {
        super(context, dso, eventType);
        this.member = member;
    }

    public DSpaceObject getMember() { return member; }
}
