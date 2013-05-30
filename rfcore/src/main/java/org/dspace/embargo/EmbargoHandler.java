/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.embargo;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Date;
import java.util.List;

import org.joda.time.DateTime;
import org.joda.time.format.ISODateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

import com.google.common.eventbus.Subscribe;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.dspace.authorize.AuthorizeException;
import org.dspace.content.MDValue;
import org.dspace.content.Item;
import org.dspace.content.LifecycleEvent;
import org.dspace.core.ConfigManager;
import org.dspace.core.Context;

/**
 * Lifecycle Handler for Embargo Functions
 *
 * @author Richard Rodgers
 */
public class EmbargoHandler {

    /** log4j category */
    private static Logger log = LoggerFactory.getLogger(EmbargoHandler.class);

    // embargo date formatter and parser (ISO-8601 "yyyy-MM-dd")
    private static final DateTimeFormatter isoDate = ISODateTimeFormat.date();

    // metadata field holding terms
    private final String termsField = ConfigManager.getProperty("embargo", "mdfield.terms");

    // metadata field holding terms
    private final String liftField = ConfigManager.getProperty("embargo", "mdfield.lift");

    // fallback terms for uninterpretable given terms 
    private final String fallback = ConfigManager.getProperty("embargo", "terms.fallback");

    // class name of setter
    private final String setterClass = ConfigManager.getProperty("embargo", "implclass.setter");

    // plugin implementations
    private EmbargoSetter setter = null;
    
    public EmbargoHandler() {
        if (termsField == null) {
            throw new IllegalStateException("Missing mdfield.terms configuration property.");
        }

        if (liftField == null) {
            throw new IllegalStateException("Missing mdfield.lift configuration property.");
        }

        if (fallback == null) {
            throw new IllegalStateException("No Fallback terms defined in DSpace configuration.");
        }
            
        if (setterClass == null) {
            throw new IllegalStateException("No EmbargoSetter implementation defined in DSpace configuration.");
        }

        try {
            setter = (EmbargoSetter)Class.forName(setterClass).newInstance();
        } catch (Exception e) {
            throw new IllegalStateException("Instantiation failure for Embargo Setter");
        }
    }

    @Subscribe
    public void setEmbargo(LifecycleEvent installEvent)
           throws AuthorizeException, IOException, SQLException {

        if (! "install".equals(installEvent.getEventName())) return;

        Item item = (Item)installEvent.getObject();
        Context context = installEvent.getContext();
        List<MDValue> terms = item.getMetadata(termsField);
        if (terms.size() > 0) {
             Date liftDate = setter.parseTerms(context, item, terms.get(0).getValue());
             if (liftDate == null) {
                 // setter unable to parse terms - use 'fallback' terms
                 liftDate = setter.parseTerms(context, item, fallback);
                 log.info("Cannot parse given terms: '" + terms.get(0).getValue() + 
                          "' - imposing fallback terms: '" + fallback + "'' on item " + item.getHandle());
             }
             if (liftDate != null && liftDate.after(new Date())) {
                 String slift = isoDate.print(liftDate.getTime());
                 try {
                     context.turnOffAuthorisationSystem();
                     item.clearMetadata("dc", "date", "available", MDValue.ANY);
                     item.setMetadataValue(liftField, slift);
                     log.info("Set embargo on Item " + item.getHandle() + ", expires on: " + slift);
                     setter.setEmbargo(context, item);
                     item.update();
                 } finally {
                    context.restoreAuthSystemState();
                 }
             }
        }
    }

     // return the lift date assigned when embargo was set, or null, if either:
    // it was never under embargo, or the lift date has passed.
    private Date recoverEmbargoDate(Item item) {
        Date liftDate = null;
        List<MDValue> lifts = item.getMetadata(liftField);
        if (lifts.size() > 0) {
            DateTime dt = isoDate.parseDateTime(lifts.get(0).getValue());
            liftDate = dt.toDate();
            // sanity check: do not allow an embargo lift date in the past.
            if (liftDate.before(new Date())) {
                liftDate = null;
            }
        }
        return liftDate;       
    }
}
