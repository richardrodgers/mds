/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.curate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.dspace.core.ConfigurationManager;
import org.dspace.core.Context;

/**
 * SelectorResolver takes a name of a selector 'profile' and attempts to 
 * deliver a selector suitably initialized to that profile. Syntax of
 * profile description, which live in 'selectors' property in curate.cfg:
 * <selectorName> = <selectorClassName>[:<initializer>]
 * Example:
 * containsFoo = org.dspace.curate.SearchSelector:Foo
 * 
 * @author richardrodgers
 */

public class SelectorResolver {
    // logging service
    private static Logger log = LoggerFactory.getLogger(SelectorResolver.class);

    // keep a singleton
    private SelectorResolver() {}

    /**
     * Returns a configured implementation for a given selector (profile) name,
     * or <code>null</code> if no implementation could be obtained.
     * 
     * @param context
     *        context to supply to selector instance
     * @param selectorName
     *        logical selector profile name
     * @return selector
     *        an object that implements the ObjectSelector interface
     */
    public static ObjectSelector resolveSelector(Context context, String selectorName) {
        ObjectSelector selector = null;
        // try to find a selector profile description matching name
        String selList = ConfigurationManager.getProperty("curate", "selectors");
        if (selList != null) {
            String className = null;
            for (String desc : selList.split(",")) {
                String tdesc = desc.trim();
                if (tdesc.startsWith(selectorName))	{
                    // parse description
                    String[] prof = tdesc.split("=");
                    // prof contains selector class name, and optionally load profile
                    String[] profile = prof[1].split(":");
                    className = profile[0];
                    try {
                        selector = (ObjectSelector)Class.forName(className).newInstance();
                        selector.setName(selectorName);
                        selector.setContext(context);
                        if (profile.length > 1) {
                            selector.configure(profile[1]);
                        }
                    } catch(Exception e) {
                        log.error("Ouch");
                    }
                }
            }
            if (className == null) {
                log.info("No named selector found for name: " + selectorName);
            }
        } else {
            log.info("No named selectors defined");
        }
        return selector;
    }
}
