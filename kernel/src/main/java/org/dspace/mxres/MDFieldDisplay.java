/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.mxres;

import com.google.common.base.Objects;

/**
 * Immutable object representing a metadata field display. It has a
 * field id, label, renderType, wrapper, and language.
 *
 * @author richardrodgers
 */

public final class MDFieldDisplay {
    
    /** The dotted notation for the metadata field */
    private final String fieldKey;

    /** Alternative name. if <code>null</code>, use field FQN */
    private final String altName;

    /** The label. if <code>null</code>, use field FQN */
    private final String label;

    /** Render type - used as a hint to UIs */
    private final String renderType;

    /** The wrapper, or <code>null</code> if no wrapper */
    private final String wrapper;
    
    /** The language of the field, may be <code>null</code> */
    private final String language;
    
    /** The index of the value in multi-value fields: -1 if unknown, not relevant */
    private final int place;

    public MDFieldDisplay(String fieldKey, String altName, String label, String renderType, String wrapper, String language) {
        this.fieldKey = fieldKey;
        this.altName = altName;
        this.label = label;
        this.renderType = renderType;
        this.wrapper = wrapper;
        this.language = language;
        this.place = -1;
    }

    public MDFieldDisplay(String fieldKey, String altName, String label, String renderType, String wrapper, String language, int place) {
        this.fieldKey = fieldKey;
        this.altName = altName;
        this.label = label;
        this.renderType = renderType;
        this.wrapper = wrapper;
        this.language = language;
        this.place = place;
    }

    public String getFieldKey() {
        return fieldKey;
    }

    public String getAltName() {
        return altName;
    }

    public String getLabel() {
        return label;
    }

    public String getRenderType() {
        return renderType;
    }

    public String getWrapper() {
        return wrapper;
    }

    public String getLanguage() {
        return language;
    }

    public int getPlace() {
        return place;
    }
    
    @Override
    public boolean equals(Object value) {
        if (this.getClass() != value.getClass()) return false;
        final MDFieldDisplay mdfd = (MDFieldDisplay)value;
        return(Objects.equal(this.fieldKey, mdfd.fieldKey) &&
               Objects.equal(this.altName, mdfd.altName) &&
               Objects.equal(this.label, mdfd.label) &&
               Objects.equal(this.renderType, mdfd.renderType) &&
               Objects.equal(this.wrapper, mdfd.wrapper) &&
               Objects.equal(this.language, mdfd.language) &&
               Objects.equal(this.place, mdfd.place));
    }
}
