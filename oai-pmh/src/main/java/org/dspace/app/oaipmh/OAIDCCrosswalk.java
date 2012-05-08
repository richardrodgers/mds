/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.oaipmh;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.sql.SQLException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ORG.oclc.oai.server.crosswalk.Crosswalk;
import ORG.oclc.oai.server.verb.CannotDisseminateFormatException;

import org.dspace.content.MDValue;
import org.dspace.content.Item;
import org.dspace.core.ConfigurationManager;
import org.dspace.core.LogManager;
import org.dspace.search.HarvestedItemInfo;

/**
 * OAI_DC Crosswalk implementation based on oaidc.properties file. All metadata
 * included in the oaidc.properties file will be mapped on a valid oai_dc
 * element, invalid oai_dc element will be not used. 
 * 
 * @author Robert Tansley
 * @author Andrea Bollini
 */
public class OAIDCCrosswalk extends Crosswalk
{
	// Pattern containing all the characters we want to filter out / replace
    // converting a String to xml
    private static final Pattern invalidXmlPattern = Pattern
           .compile("([^\\t\\n\\r\\u0020-\\ud7ff\\ue000-\\ufffd\\u10000-\\u10ffff]+|[&<>])");

    /** Location of config file */
    private static final String configFilePath = ConfigurationManager
            .getProperty("dspace.dir")
            + File.separator + "conf" + File.separator + "crosswalks"
            + File.separator + "oaidc.properties";

    /** logger */
    private static Logger log = LoggerFactory.getLogger(OAIDCCrosswalk.class);
    
    // crosswalk
    private static final Properties xwalk = new Properties();

    static {
        // Read in configuration
        FileInputStream fis = null;
        try {
            fis = new FileInputStream(configFilePath);
            xwalk.load(fis);
        } catch (IOException e) {
            throw new IllegalArgumentException(
                    "Wrong configuration for OAI_DC", e);
        } finally {
            if (fis != null) {
                try {
                    fis.close();
                } catch (IOException ioe) {
                    log.error("Error closing config file", ioe);
                }
            }
        }
    }

    public OAIDCCrosswalk(Properties properties)  {
        super("http://www.openarchives.org/OAI/2.0/oai_dc/ "
                + "http://www.openarchives.org/OAI/2.0/oai_dc.xsd");
    }

    public boolean isAvailableFor(Object nativeItem) {
        // We have DC for everything
        return true;
    }

    public String createMetadata(Object nativeItem)
            throws CannotDisseminateFormatException {
        Item item = ((HarvestedItemInfo) nativeItem).item;

        StringBuffer metadata = new StringBuffer();
        metadata
                .append("<oai_dc:dc xmlns:oai_dc=\"http://www.openarchives.org/OAI/2.0/oai_dc/\" ")
                .append("xmlns:dc=\"http://purl.org/dc/elements/1.1/\" ")
                .append("xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" ")
                .append("xsi:schemaLocation=\"http://www.openarchives.org/OAI/2.0/oai_dc/ http://www.openarchives.org/OAI/2.0/oai_dc.xsd\">");

        // RLR note - performance here could be (I think) greatly improved by refactoring
        // this loop to make only 1 call to item.getMetadata(*,*,*), and then pattern matching the
        // xwalk property keys. TODO
        for (String mdString : xwalk.stringPropertyNames()) {
        	String element = xwalk.getProperty(mdString);
            for (MDValue mdValue : item.getMetadata(mdString)) {
                String value = mdValue.getValue();                  
                // Also replace all invalid characters with ' '
                if (value != null) {
                    StringBuffer valueBuf = new StringBuffer(value.length());
                    Matcher xmlMatcher = invalidXmlPattern.matcher(value.trim());
                    while (xmlMatcher.find())  {
                        String group = xmlMatcher.group();
                        // group will either contain a character that we
                        // need to encode for xml
                        // (ie. <, > or &), or it will be an invalid
                        // character
                        // test the contents and replace appropriately
                        if (group.equals("&")) {
                            xmlMatcher.appendReplacement(valueBuf,"&amp;");
                        } else if (group.equals("<")) {
                            xmlMatcher.appendReplacement(valueBuf, "&lt;");
                        } else if (group.equals(">")) {
                            xmlMatcher.appendReplacement(valueBuf, "&gt;");
                        } else {
                            xmlMatcher.appendReplacement(valueBuf, " ");
                        }
                    }
                    // add bit of the string after the final match
                    xmlMatcher.appendTail(valueBuf);
                    metadata.append("<dc:").append(element).append(">")
                                    .append(valueBuf.toString())
                                    .append("</dc:").append(element)
                                    .append(">");
                }
            }
        }
        metadata.append("</oai_dc:dc>");
        return metadata.toString();
    }
}
