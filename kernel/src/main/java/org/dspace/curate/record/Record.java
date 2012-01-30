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
 * Annotation type for CurationTasks. In using this type, a task requests that
 * the curation framework pass information about its execution out to
 * receiving systems. Such systems might include simple logs/journals,
 * message queues, database tables, etc. which are accessed via pluggable
 * 'recorders' in the curation framework. Each time a task is performed, if a
 * suitable 'recorder' is configured, an informational message is generated and
 * passed to it.  Record contents are:
 * time-stamp, object identifier, eperson id, record 'type', record 'value',
 * task name, status code, result string.
 * Thus for the simplest case - a bare <code>@Record</code> annotation - output might be:
 * 01-01-2012-14:20Z 123456789/1 curation mytask performed 0 'All OK'
 * A more informative example - <code>@Record(type=Premis, value=Replication)</code>:
 * 01-01-2012-14:20:23Z 123456789/1 rrodgers@foobar.com 'Premis Preservation Event' 'Replicaton' 0 'OK'   
 * 
 * @author richardrodgers
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
public @interface Record
{    
    // by default, type is non-specific
    String type() default "curation";
    // by default, value is just "performed"
    String value() default "performed";
    // by default, ERROR, SUCCESS, FAILURE, or SKIP status codes trigger output
    int[] statusCodes() default {-1, 0, 1, 2};
}
