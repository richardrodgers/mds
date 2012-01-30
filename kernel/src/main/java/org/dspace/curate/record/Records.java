/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.curate.record;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Annotation type for CurationTasks. Tasks may pass information out of
 * the curation framework using Record annotations. Since there may be
 * occasions when a task may wish to express different sorts of
 * information in possibly different contexts of use (or to multiple targets),
 * this container annotation type is provided (since simple annotations are not
 * repeatable). When only one Record annotation is used, there is no need
 * to wrap it in a Records block.
 * 
 * @author richardrodgers
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
public @interface Records
{    
    // no default
    Record[] value() default {};
}
