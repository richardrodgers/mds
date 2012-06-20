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
import org.joda.time.DateTimeZone;
import org.joda.time.format.ISODateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.dspace.authorize.AuthorizeException;
import org.dspace.content.MDValue;
import org.dspace.content.DSpaceObject;
import org.dspace.content.Item;
import org.dspace.content.BoundedIterator;
import org.dspace.content.MetadataSchema;
import org.dspace.core.ConfigurationManager;
import org.dspace.core.Constants;
import org.dspace.core.Context;
import org.dspace.handle.HandleManager;

/**
 * Public interface to the embargo subsystem.
 * <p>
 * Configuration properties: (with examples)
 *   <br/># DC metadata field to hold the user-supplied embargo terms
 *   <br/>embargo.field.terms = dc.embargo.terms
 *   <br/># DC metadata field to hold computed "lift date" of embargo
 *   <br/>embargo.field.lift = dc.date.available
 *   <br/># String to indicate indefinite (forever) embargo in terms
 *   <br/>embargo.terms.open = Indefinite
 *   <br/># implementation of embargo setter plugin
 *   <br/>plugin.single.org.dspace.embargo.EmbargoSetter = edu.my.Setter
 *   <br/># implementation of embargo lifter plugin
 *   <br/>plugin.single.org.dspace.embargo.EmbargoLifter = edu.my.Lifter
 *
 * @author Larry Stone
 * @author Richard Rodgers
 */
public class EmbargoManager
{
    /** Special date signalling an Item is to be embargoed forever.
     **/
    public static final Date FOREVER = new Date(Long.MAX_VALUE);

    /** log4j category */
    private static Logger log = LoggerFactory.getLogger(EmbargoManager.class);

    // Metadata field components for user-supplied embargo terms
    // set from the DSpace configuration by init()
    private static String terms_schema = null;
    private static String terms_element = null;
    private static String terms_qualifier = null;

    // set from the DSpace configuration by init()
    private static String lift_schema = null;
    private static String lift_element = null;
    private static String lift_qualifier = null;

    // plugin implementations
    // set from the DSpace configuration by init()
    private static EmbargoSetter setter = null;
    private static EmbargoLifter lifter = null;
    
    // embargo date formatter and parser (ISO-8601 "yyyy-MM-dd")
	private static final DateTimeFormatter isoDate = ISODateTimeFormat.date();
	// long format of same
	private static final DateTimeFormatter iso8601 = 
			ISODateTimeFormat.dateTimeNoMillis().withZone(DateTimeZone.UTC);
    
    // command-line options
    @Option(name="-q", usage="Do not print anything except for errors")
    private boolean quiet;
    
    @Option(name="-v", usage="Print a line describing action taken for each embargoed Item found")
    private boolean verbose;
    
    @Option(name="-n", usage="Do not change anything in the data model, print message instead")
    private boolean noOp;
    
    @Option(name="-c", usage="Function: ONLY check the state of embargoed Items, do NOT lift any embargoes")
    private boolean checkOnly;
    
    @Option(name="-l", usage="Function: ONLY lift embargoes, do NOT check the state of any embargoed Items")
    private boolean liftOnly;
    
    @Option(name="-i", usage="Process ONLY this Handle identifier(s), which must be an Item. Repeatable")
    private List<String> idList;
    
    @Option(name="-h", usage="Print helpful message")
    private boolean help;
    
    private EmbargoManager() {}

    /**
     * Put an Item under embargo until the specified lift date.
     * Calls EmbargoSetter plugin to adjust Item access control policies.
     *
     * @param context the DSpace context
     * @param item the item to embargo
     * @param lift date on which the embargo is to be lifted.
     */
    public static void setEmbargo(Context context, Item item, Date lift)
        throws SQLException, AuthorizeException, IOException
    {
        init();
        // if lift is null, we might be restoring an item from an AIP
        Date myLift = lift;
        if (myLift == null)
        {
             if ((myLift = recoverEmbargoDate(item)) == null)
             {
                 return;
             }
        }
        String slift = isoDate.print(myLift.getTime());
        try
        {
            context.turnOffAuthorisationSystem();
            item.clearMetadata(lift_schema, lift_element, lift_qualifier, MDValue.ANY);
            item.addMetadata(lift_schema, lift_element, lift_qualifier, null, slift);
            log.info("Set embargo on Item "+item.getHandle()+", expires on: "+slift);
            setter.setEmbargo(context, item);
            item.update();
        }
        finally
        {
            context.restoreAuthSystemState();
        }
    }

    /**
     * Get the embargo lift date for an Item, if any.  This looks for the
     * metadata field configured to hold embargo terms, and gives it
     * to the EmbargoSetter plugin's method to interpret it into
     * an absolute timestamp.  This is intended to be called at the time
     * the Item is installed into the archive.
     * <p>
     * Note that the plugin is *always* called, in case it gets its cue for
     * the embargo date from sources other than, or in addition to, the
     * specified field.
     *
     * @param context the DSpace context
     * @param item the item to embargo
     * @return lift date on which the embargo is to be lifted, or null if none
     */
    public static Date getEmbargoDate(Context context, Item item)
        throws SQLException, AuthorizeException, IOException
    {
        init();
        List<MDValue> terms = item.getMetadata(terms_schema, terms_element,
                							   terms_qualifier, MDValue.ANY);

        if (terms == null)
            return null;

        Date liftDate = setter.parseTerms(context, item,
                				   		 terms.size() > 0 ? terms.get(0).getValue() : null);

        if (liftDate == null)
            return null;

        // sanity check: do not allow an embargo lift date in the past.
        if (liftDate.before(new Date()))  {
            throw new IllegalArgumentException(
                    "Embargo lift date must be in the future, but this is in the past: "
                            + liftDate.toString());
        }
        return liftDate;
    }

    /**
     * Lift the embargo on an item which is assumed to be under embargo.
     * Call the plugin to manage permissions in its own way, then delete
     * the administrative metadata fields that dictated embargo date.
     *
     * @param context the DSpace context
     * @param item the item on which to lift the embargo
     */
    public static void liftEmbargo(Context context, Item item)
        throws SQLException, AuthorizeException, IOException
    {
        init();
        lifter.liftEmbargo(context, item);
        item.clearMetadata(lift_schema, lift_element, lift_qualifier, MDValue.ANY);

        // set the dc.date.available value to right now
        String now = iso8601.print(System.currentTimeMillis());
        item.clearMetadata(MetadataSchema.DC_SCHEMA, "date", "available", MDValue.ANY);
        item.addMetadata(MetadataSchema.DC_SCHEMA, "date", "available", null, now);

        log.info("Lifting embargo on Item "+item.getHandle());
        item.update();
    }

    /**
     * Command-line service to scan for every Item with an expired embargo,
     * and then lift that embargo.
     * <p>
     * Options:
     * <dl>
     *   <dt>-c</dt>
     *   <dd>         Function: ONLY check the state of embargoed Items, do
     *                      NOT lift any embargoes.</dd>
     *   <dt>-h</dt>
     *   <dd>         Help.</dd>
     *   <dt>-i</dt>
     *   <dd>         Process ONLY this Handle identifier(s), which must be
     *                      an Item.  Can be repeated.</dd>
     *   <dt>-l</dt>
     *   <dd>         Function: ONLY lift embargoes, do NOT check the state
     *                      of any embargoed Items.</dd>
     *   <dt>-n</dt>
     *   <dd>         Do not change anything in the data model; print
     *                      message instead.</dd>
     *   <dt>-v</dt>
     *   <dd>         Print a line describing action taken for each
     *                      embargoed Item found.</dd>
     *   <dt>-q</dt>
     *   <dd>         No output except upon error.</dd>
     * </dl>
     */
    public static void main(String[] args)
    {
        init();
        int status = 0;
        EmbargoManager em = new EmbargoManager();
        CmdLineParser parser = new CmdLineParser(em);
        try {
        	parser.parseArgument(args);
        } catch(CmdLineException clE) {
        	System.err.println(clE.getMessage());
        	parser.printUsage(System.err);
            System.exit(1);
        }

        if (em.help)
        {
        	parser.printUsage(System.err);
            System.exit(0);
        }

        // sanity check, liftOnly and checkOnly are mutually exclusive:
        if (em.liftOnly && em.checkOnly)
        {
            System.err.println("Command error: -l and -c are mutually exclusive, try --help for assistance.");
            System.exit(1);
        }

        Context context = null;
        try
        {
            context = new Context();
            context.turnOffAuthorisationSystem();
            Date now = new Date();
             
            // scan items under embargo
            if (em.idList.size() > 0)
            {
                for (String handle : em.idList)
                {
                    DSpaceObject dso = HandleManager.resolveToObject(context, handle);
                    if (dso == null)
                    {
                        System.err.println("Error, cannot resolve handle="+handle+" to a DSpace Item.");
                        status = 1;
                    }
                    else if (dso.getType() != Constants.ITEM)
                    {
                        System.err.println("Error, the handle="+handle+" is not a DSpace Item.");
                        status = 1;
                    }
                    else
                    {
                        if (em.processOneItem(context, (Item)dso, now))
                        {
                            status = 1;
                        }
                    }
                }
            }
            else {
                BoundedIterator<Item> ii = Item.findByMetadataField(context, lift_schema, lift_element, lift_qualifier, MDValue.ANY);
                while (ii.hasNext()) {
                    if (em.processOneItem(context, ii.next(), now)) {
                        status = 1;
                    }
                }
                ii.close();
            }
            log.debug("Cache size at end = "+context.getCacheSize());
            context.complete();
            context = null;
        }
        catch (Exception e)
        {
            System.err.println("ERROR, got exception: "+e);
            e.printStackTrace();
            status = 1;
        }
        finally
        {
            if (context != null)
            {
                try
                {
                    context.abort();
                }
                catch (Exception e)
                {
                }
            }
        }
        System.exit(status);
    }

    // lift or check embargo on one Item, handle exceptions
    // return false on success, true if there was fatal exception.
    private boolean processOneItem(Context context, Item item, Date now) throws Exception {
        boolean status = false;
        List<MDValue> lifts = item.getMetadata(lift_schema, lift_element, lift_qualifier, MDValue.ANY);

        if (lifts.size() > 0) {
            // need to survive any failure on a single item, go on to process the rest.
            try {
            	String liftStr = lifts.get(0).getValue();
                Date liftDate = isoDate.parseDateTime(liftStr).toDate();
                log.debug("Testing embargo on item="+item.getHandle()+", date="+liftStr);
                if (liftDate.before(now)) {
                    if (verbose) {
                        System.err.println("Lifting embargo from Item handle=" + item.getHandle() + ", lift date=" + liftStr);
                    }
                    if (noOp) {
                        if (! quiet) {
                            System.err.println("DRY RUN: would have lifted embargo from Item handle=" + item.getHandle() + ", lift date=" + liftStr);
                        }
                    }
                    else if (! checkOnly)
                    {
                        liftEmbargo(context, item);
                    }
                }
                else if (! liftOnly)
                {
                    if (verbose)
                    {
                        System.err.println("Checking current embargo on Item handle=" + item.getHandle() + ", lift date=" + liftStr);
                    }
                    setter.checkEmbargo(context, item);
                }
            }
            catch (Exception e)
            {
                log.error("Failed attempting to lift embargo, item="+item.getHandle()+": ", e);
                System.err.println("Failed attempting to lift embargo, item="+item.getHandle()+": "+ e);
                status = true;
            }
        }
        context.removeCached(item, item.getID());
        return status;
    }

    // initialize - get plugins and MD field settings from config
    private static void init()
    {
        if (terms_schema == null)
        {
            String terms = ConfigurationManager.getProperty("embargo.field.terms");
            String lift = ConfigurationManager.getProperty("embargo.field.lift");
            if (terms == null || lift == null)
            {
                throw new IllegalStateException("Missing one or more of the required DSpace configuration properties for EmbargoManager, check your configuration file.");
            }
            terms_schema = getSchemaOf(terms);
            terms_element = getElementOf(terms);
            terms_qualifier = getQualifierOf(terms);
            lift_schema = getSchemaOf(lift);
            lift_element = getElementOf(lift);
            lift_qualifier = getQualifierOf(lift);
            
        	String setterClass = ConfigurationManager.getProperty("embargo.setter");
        	if (setterClass == null)
        	{
        		throw new IllegalStateException("No EmbargoSetter implementation defined in DSpace configuration.");
        	}
        	
        	String lifterClass = ConfigurationManager.getProperty("embargo.lifter");
        	if (lifterClass == null)
        	{
        		throw new IllegalStateException("No EmbargoLifter implementation defined in DSpace configuration.");
        	}

            try {
            	setter = (EmbargoSetter)Class.forName(setterClass).newInstance();
            	lifter = (EmbargoLifter)Class.forName(lifterClass).newInstance();
            } catch (Exception e) {
            	throw new IllegalStateException("Instantiation failure for Embargo Setter or Lifter");
            }
        }
    }

    // return the schema part of "schema.element.qualifier" metadata field spec
    private static String getSchemaOf(String field)
    {
        String sa[] = field.split("\\.", 3);
        return sa[0];
    }

    // return the element part of "schema.element.qualifier" metadata field spec, if any
    private static String getElementOf(String field)
    {
        String sa[] = field.split("\\.", 3);
        return sa.length > 1 ? sa[1] : null;
    }

    // return the qualifier part of "schema.element.qualifier" metadata field spec, if any
    private static String getQualifierOf(String field)
    {
        String sa[] = field.split("\\.", 3);
        return sa.length > 2 ? sa[2] : null;
    }
    
    // return the lift date assigned when embargo was set, or null, if either:
    // it was never under embargo, or the lift date has passed.
    private static Date recoverEmbargoDate(Item item) {
        Date liftDate = null;
        List<MDValue> lifts = item.getMetadata(lift_schema, lift_element, lift_qualifier, MDValue.ANY);
        if (lifts.size() > 0) {
            DateTime dt = isoDate.parseDateTime(lifts.get(0).getValue());
            liftDate = dt.toDate();
            // sanity check: do not allow an embargo lift date in the past.
            if (liftDate.before(new Date()))
            {
                liftDate = null;
            }
        }
        return liftDate;       
    }
}
