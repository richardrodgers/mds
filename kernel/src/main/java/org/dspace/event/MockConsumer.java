/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.event;

import java.io.PrintStream;
import java.text.SimpleDateFormat;
import java.util.Date;

import com.google.common.eventbus.Subscribe;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.dspace.core.ConfigurationManager;
import org.dspace.core.Context;
import org.dspace.event.ContentEvent;

/**
 * Demonstration and test consumer for the event system. This consumer only
 * makes an entry in the log, and on an output stream, for each event it
 * receives. It also logs when consume() and end() get called. It is intended
 * for testing, exploring, and debugging the event system.
 */
@Consumes("content")
public class MockConsumer {
    // Log4j logger
    private static Logger log = LoggerFactory.getLogger(MockConsumer.class);

    // Send diagnostic output here - set to null to turn it off.
    private static PrintStream out = ConfigurationManager
            .getBooleanProperty("testConsumer.verbose") ? System.out : null;

    @Subscribe
    public void consume(ContentEvent event) {
        long elapsed = System.currentTimeMillis() - event.getTimestamp();
        System.out.println("MockConsumer got object: " + event.getObject().getID() + " type: " + event.getEventType() + " elapsed: " + elapsed);
    }
}
