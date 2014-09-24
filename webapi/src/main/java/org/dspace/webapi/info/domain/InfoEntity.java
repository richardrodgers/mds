/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.webapi.info.domain;

import javax.xml.bind.annotation.XmlSeeAlso;

/**
 * InfoEntity is the abstract base class for
 * all Info entity classes.
 * NB: this class is currently needed only to enable JAXB marshalling
 * of subclasses (that have lists as members) by JAX-RS. It probably could be eliminated.
 *
 * @author richardrodgers
 */

@XmlSeeAlso({FormatsEntity.class, FieldsEntity.class, SystemEntity.class})
public abstract class InfoEntity {

    public InfoEntity() {}

}
