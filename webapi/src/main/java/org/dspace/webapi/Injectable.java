/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.webapi;

import java.net.URI;
import java.util.Map;

/**
 * Injectable interface allows resource URIs to
 * appear in domain entities through 'injection', decoupling
 * the entity from its resource mappings. This means that
 * domain entities need not be aware of any server environment.
 *
 * @author richardrodgers
 */

public interface Injectable {

    Map<String, String> getUriInjections();
   
    void injectUri(String key, URI uri);
}
