/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.curate;

import java.util.Iterator;

import org.dspace.content.DSpaceObject;
import org.dspace.core.Context;

/**
 * ObjectSelector provides an iterable collection of DSpaceObjects.
 * Each object is injected into a curation operation by the curation framework.
 * NB: there is no assumption of idempotence, i.e. a selector may return
 * a different collection each time it is created.
 *
 * @author richardrodgers
 */
public interface ObjectSelector extends Iterator<DSpaceObject> {

    /**
     * Returns the (local, aka logical) name of the selector, which is the
     * configured name by which the implementation was chosen.
     *
     * @return name
     *         the selector name
     */
     String getName();

    /**
     * Assigns the (local, aka logical) name of the selector, which is the
     * configured name by which the implementation was chosen.
     *
     * @param name
     *         the selector name
     */
     void setName(String name);
    
    /**
     * Returns context (if any) associated with selector.
     * 
     * @return context
     *         the selector context, or null if no context found
     */
    Context getContext();
    
    /**
     * Assigns a context for use by selector
     * 
     * @param context
     *        the context for selector
     */
    void setContext(Context context);
    
    /**
     * Defines selection behavior. Note that selectors will
     * typically offer other APIs or means for configuring; this method
     * is a common denominator for named selector loading. Also note
     * that definitions have selector-specific syntax and semantics,
     * and selectors may elect to ignore the passed definition.
     * 
     * @param definition
     *        a definition interpretable by the selector 
     */
    void configure(String definition);
}
