/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.webapi.content;

import java.net.URI;
import java.util.List;
import java.util.Map;

import org.dspace.webapi.content.domain.EntityRef;


public interface Injectable {

    Map<String, String> getUriInjections();

    Map<String, List<EntityRef>> getRefInjections();
   
    void injectUri(String key, URI uri);

    void injectRefs(String key, List<EntityRef> refs);
}
