/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.event;

import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.dspace.core.ConfigurationManager;
import org.dspace.core.Context;

/**
 * Class for managing the content event environment. The EventManager mainly
 * acts as a factory for Dispatchers, which are used by the Context to send
 * events to subscribers. It also contains generally useful utility methods.
 *
 * @author richardrodgers
 */
public class EventManager
{
    /** log4j category */
    private static Logger log = LoggerFactory.getLogger(EventManager.class);

    private static final String SUBSCRIBER_PFX = "event.subscriber";

    private static MulticastDispatcher dispatcher;

    static {
      init();
    }

    private static void init() {
        dispatcher = new MulticastDispatcher();
        Properties props = ConfigurationManager.getMatchedProperties(SUBSCRIBER_PFX);
        for (String name : props.stringPropertyNames()) {
            String fqn = SUBSCRIBER_PFX + "." + name;
            dispatcher.addSubscriber(name, ConfigurationManager.getInstance(null, fqn));
        }
    }

    public static void dispatchEvents(Context context) {
        dispatcher.dispatch(context.getContentEvents());
    }
}
