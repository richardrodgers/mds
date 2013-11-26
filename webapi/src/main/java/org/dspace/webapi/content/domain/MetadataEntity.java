/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.webapi.content.domain;

import java.net.URI;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;

import org.dspace.content.DSpaceObject;
import org.dspace.content.MDValue;

/**
 * MetadataEntity is a representation of a set of metadata
 * belonging to a  DSpaceObject entity: Community, Collection,
 * Item, Bitstream
 *
 * @author richardrodgers
 */

@XmlRootElement(name="metadata")
public class MetadataEntity extends ContentEntity {

    private String mdName;
    @XmlElementWrapper(name="statements")
    @XmlElement(name="statement")
    private List<Statement> statements = new ArrayList<>();

    public MetadataEntity() {}

    public MetadataEntity(DSpaceObject dso, String name) throws SQLException {
        super(dso);
        this.mdName = name;
        List<MDValue> metadata = dso.getMetadata(name, MDValue.ANY, MDValue.ANY, MDValue.ANY);
        for (MDValue mdv : metadata) {
            statements.add(new Statement(mdv.getElement(), mdv.getQualifier(), mdv.getLanguage(), mdv.getValue()));
        }   
    }

    public String getType() {
        return mdName;
    }

    public void setType(String mdName) {
        this.mdName = mdName;
    }
}
