/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.event;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Annotation type for Event Listeners. Value
 * will the channel ID (name of EventBus)
 * that this class will listen to.
 * 
 * @author richardrodgers
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
public @interface Consumes {    
    String value() default "content";
}
