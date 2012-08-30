/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.mxres;

import org.dspace.core.Context;

/**
 * ResourceBuilders are objects that know how to construct particular instances of
 * extensible resource types See {@link org.dspace.mxres.ExtensibleResource}.
 * The data backing these resources may come from DB tables, XML files, etc
 * and it is the principal responsibility of a ResourceBuilder to abstract this.
 * They differ from factory classes in that they make unique instances of resources,
 * identified by a resourceId.
 * 
 * @author richardrodgers
 */

public interface ResourceBuilder {
	/**
	 * Constructs a resource object given an identifier.
	 * Identifier presumed to be unique within the resource type.
	 * 
	 * @parm context - the DSpace context
	 * @param resId - the unique (modulo resource class) identifier for the resource
	 * @return resource - the extensible resource instance
	 */
	ExtensibleResource build(Context context, String resId);
}
