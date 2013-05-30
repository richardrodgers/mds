/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.core;

import com.google.common.eventbus.DeadEvent;
import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;

import org.dspace.content.LifecycleEvent;

/**
 * Class for posting and processing Lifecycle events.
 *
 * @author richardrodgers
 */

public class LifecycleMux {

    private static final EventBus mux = new EventBus("lifecycle");

    private static boolean initialized = false;

   /**
    * Posts a lifecycle event to all registered handlers (aka listeners)
    *
    * @param event    the lifecycycle event
    */
    public static void postEvent(LifecycleEvent event) {
        init();
        mux.post(event);
    }

    private static void init() {
        if (! initialized) {
            // read configuration data to find handlers - stub implementation here
            try {
            	for (String hcName : ConfigManager.getConfig("kernel").getStringList("lifecycle-handlers")) {
                    mux.register(Class.forName(hcName).newInstance());
                }
                // register a drain handler for DeadEvents
                mux.register(new EventDrain());
            } catch (ClassNotFoundException | InstantiationException | IllegalAccessException ex) {}
            initialized = true;
        }
    }

    private static class EventDrain {
        @Subscribe
        public void handleDead(DeadEvent event) {
            // no-op - maybe log out of curiousity?
        }
    }
}
