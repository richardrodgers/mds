/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.core;

import ch.qos.logback.core.PropertyDefinerBase;

/**
 * Class for reading configuration file for log directory
 */
public class LogDirectory extends PropertyDefinerBase {
    @Override
    public String getPropertyValue() {
    	return ConfigurationManager.getProperty("log.dir");
    }
}
