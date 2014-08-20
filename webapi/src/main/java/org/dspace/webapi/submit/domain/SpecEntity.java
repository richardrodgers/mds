/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.webapi.submit.domain;

import java.net.URI;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;

import org.dspace.content.Bitstream;
import org.dspace.content.DSpaceObject;
import org.dspace.content.MDValue;
import org.dspace.content.WorkspaceItem;
import org.dspace.core.Constants;
import org.dspace.core.Context;
import org.dspace.mxres.MetadataSpec;
import org.dspace.mxres.MDFieldSpec;
import org.dspace.mxres.ResourceMap;

/**
 * SpecEntity is a representation of a set of metadata values
 * with input information, belonging to a submission entity:
 * Item or Bitstream
 *
 * @author richardrodgers
 */

@XmlRootElement(name="spec")
public class SpecEntity extends SubmitEntity {

    private String mdName;
    @XmlElementWrapper(name="specifications")
    @XmlElement(name="specification")
    private List<Specification> specifications = new ArrayList<>();

    public SpecEntity() {}

    public SpecEntity(Context ctx, WorkspaceItem wsi, Bitstream bs, String name) throws SQLException {
        super(wsi, bs);
        this.mdName = name;
        // fetch the spec
        DSpaceObject dso = (bs != null) ? bs : wsi.getItem();
        int objType = (bs != null) ? Constants.BITSTREAM : Constants.ITEM;
        String scope = Constants.typeText[dso.getType()].toLowerCase() + "-mds-" + name;
        MetadataSpec spec = (MetadataSpec)new ResourceMap(MetadataSpec.class, ctx).findResource(dso, scope);
        // for each field in view, construct a depiction
        if (spec != null) {
            for (MDFieldSpec field: spec.getFieldSpecs()) {
                String label = field.getLabel();
                if (label == null) {
                    label = field.getFieldKey();
                }
                List<MDValue> metadata = dso.getMetadata(field.getFieldKey());
                List<String> values = new ArrayList<String>(metadata.size());
                for (MDValue mdv: metadata) {
                    values.add(mdv.getValue());
                }
                specifications.add(new Specification(field.getFieldKey(), label, field.getDescription(), 
                                                     field.getCardinality(), field.getInputType(),
                                                     field.isLocked(), values, field.getLanguage()));
            }
        }   
    }

    public String getType() {
        return mdName;
    }

    public void setType(String mdName) {
        this.mdName = mdName;
    }

    public List<Specification> getSpecifications() {
        return specifications;
    }

    public void setSpecifications(List<Specification> specifications) {
        this.specifications = specifications;
    }
}
