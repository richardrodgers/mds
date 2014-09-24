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
import org.dspace.core.Constants;
import org.dspace.core.Context;
import org.dspace.mxres.MetadataView;
import org.dspace.mxres.MDFieldDisplay;
import org.dspace.mxres.ResourceMap;

/**
 * ViewEntity is a representation of a set of metadata values
 * with rendering information, belonging to a DSpaceObject entity:
 * Community, Collection, Item, Bitstream
 *
 * @author richardrodgers
 */

@XmlRootElement(name="view")
public class ViewEntity extends ContentEntity {

    private String mdName;
    @XmlElementWrapper(name="depictions")
    @XmlElement(name="depiction")
    private List<Depiction> depictions = new ArrayList<>();

    public ViewEntity() {}

    public ViewEntity(Context ctx, DSpaceObject dso, String name) throws SQLException {
        super(dso);
        this.mdName = name;
        // fetch the view
        String scope = Constants.typeText[dso.getType()].toLowerCase() + "-mdv-" + name;
        MetadataView view = (MetadataView)new ResourceMap(MetadataView.class, ctx).findResource(dso, scope);
        // for each field in view, construct a depiction
        if (view != null) {
            for (MDFieldDisplay field: view.getViewFields()) {
                List<MDValue> metadata = dso.getMetadata(field.getFieldKey());
                for (MDValue mdv: metadata) {
                    String label = field.getLabel();
                    if (label == null) {
                        label = field.getFieldKey();
                    }
                    depictions.add(new Depiction(label, field.getRenderType(), field.getLanguage(), mdv.getValue()));
                }
            }
        }   
    }

    public String getType() {
        return mdName;
    }

    public void setType(String mdName) {
        this.mdName = mdName;
    }

    public List<Depiction> getDepictions() {
        return depictions;
    }

    public void setDepictions(List<Depiction> depictions) {
        this.depictions = depictions;
    }
}
