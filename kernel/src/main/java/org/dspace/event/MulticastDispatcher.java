/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.event;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.dspace.core.ConfigurationManager;

/**
 * MulticastDispatcher accepts a context, and dispatches
 * its events to all configured channels.
 *
 * @author richardrodgers
 */
public class MulticastDispatcher {

    public MulticastDispatcher() {}

    /** log4j category */
    private static Logger log = LoggerFactory.getLogger(MulticastDispatcher.class);

    private Map<String, Channel> channelMap = new HashMap<>();

    public void addSubscriber(String name, Object subscriber) {
        Channel chan = null;
        // ensure the channel the subscriber wants is present, add it if not
        Class subClazz = subscriber.getClass();
        log.info("Adding subscriber: " + name + " class: " + subClazz.getName());
        if (subClazz.isAnnotationPresent(Consumes.class)) {
            String chanName = ((Consumes)subClazz.getAnnotation(Consumes.class)).value();
            if (! channelMap.containsKey(chanName)) {
                chan = (Channel)ConfigurationManager.getInstance(null, "event.channel." + chanName);
                chan.init(chanName);
                channelMap.put(chanName, chan);
                log.info("Adding channel: " + chanName + " class: " + chan.getClass().getName());
            } else {
                chan = channelMap.get(chanName);
            }
        } else {
            // misconfiguration - subscriber must be annotated with a channel
            // to listen to
            throw new IllegalStateException("Subscriber lacks channel");
        }
        // now register this subscriber
        chan.register(subscriber);
    }

    /**
     * Dispatch all events in the list to any listeners
     * consumers.
     *
     * @param events
     *            the events list
     */
    public void dispatch(List<ContentEvent> events) {
        if (events != null && events.size() > 0) {
            for (Channel chan : channelMap.values()) {
                chan.propogate(events);
            }
        }
    }
}
