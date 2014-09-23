/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.eperson;

import java.util.Date;
import javax.mail.MessagingException;

import com.google.common.eventbus.Subscribe;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.dspace.core.ConfigurationManager;
import org.dspace.core.Constants;
import org.dspace.core.Context;
import org.dspace.core.Email;
import org.dspace.core.I18nUtil;
import org.dspace.core.LogManager;
import org.dspace.event.Consumes;
import org.dspace.event.ContentEvent;
import org.dspace.event.ContentEvent.EventType;

/**
 * Class for handling updates to EPersons
 *
 *
 * @version $Revision: 5844 $
 *
 * @author Stuart Lewis
 */
@Consumes("content")
public class Registration {
    /** log4j logger */
    private static Logger log = LoggerFactory.getLogger(Registration.class);

    private static final String notifyRecipient;

    static {
        String recipient = ConfigurationManager.getProperty("registration.notify");
        if (recipient == null) {
            recipient = "";
        }
        notifyRecipient = recipient.trim();
    }

    /**
     * Consume the event
     *
     * @param content event
     * @throws Exception
     */
    @Subscribe
    public void adminNotify(ContentEvent event) throws Exception {
        if (event.getObject().getType() == Constants.EPERSON &&
            event.getEventType().equals(EventType.CREATE) &&
            ! notifyRecipient.equals("")) {
            
            EPerson eperson = (EPerson)event.getObject();
            Context context = event.getContext();
            try {
                Email adminEmail = Email.fromTemplate(context, I18nUtil.getEmailFilename(context.getCurrentLocale(), "registration_notify"));
                adminEmail.addRecipient(notifyRecipient);
                adminEmail.addArgument(ConfigurationManager.getProperty("site.name"));
                adminEmail.addArgument(ConfigurationManager.getProperty("site.url"));
                adminEmail.addArgument(eperson.getFirstName() + " " + eperson.getLastName()); // Name
                adminEmail.addArgument(eperson.getEmail());
                adminEmail.addArgument(new Date());                                                
                adminEmail.setReplyTo(eperson.getEmail());

                adminEmail.send();

                log.info(LogManager.getHeader(context, "registerion_alert", "user=" + eperson.getEmail()));
            } catch (MessagingException me) {
                log.warn(LogManager.getHeader(context, "error_emailing_administrator", ""), me);
            }
        }
    }
}
