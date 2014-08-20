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
 * Immutable object representing a metadata field input specification. It has a
 * field id, label, and other information for assisting/constraining metadata values.
 *
 * @author richardrodgers
 */

public final class MDFieldSpec {
    
    /** The dotted notation for the metadata field */
    private final String fieldKey;

    /** Alternative name. if <code>null</code>, use field FQN */
    private final String altName;

    /** The label. if <code>null</code>, use field FQN */
    private final String label;

    /** A description, which might be used as a contextual prompt for data entry */
    private final String description;

    /** Cardinality of field occurence 0-1 = optional single value, 1 = required single value,  1-n = minimum one, etc */
    private final String cardinality;

    /** Input type - used as a hint to UIs */
    private final String inputType;

    /** Locked indicates field is display/advisory only - used to protect template values, e.g. */
    private final boolean locked;
    
    /** The language of the field, may be <code>null</code> */
    private final String language;
    

    public MDFieldSpec(String fieldKey, String altName, String label, String description, String cardinality, String inputType, boolean locked, String language) {
        this.fieldKey = fieldKey;
        this.altName = altName;
        this.label = label;
        this.description = description;
        this.cardinality= cardinality;
        this.inputType = inputType;
        this.locked = locked;
        this.language = language;
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

    public String getDescription() {
        return description;
    }

    public String getCardinality() {
        return cardinality;
    }

    public String getInputType() {
        return inputType;
    }

    public boolean isLocked() {
        return locked;
    }

    public String getLanguage() {
        return language;
    }

    
    @Override
    public boolean equals(Object value) {
        if (this.getClass() != value.getClass()) return false;
        final MDFieldSpec mdfs = (MDFieldSpec)value;
        return(Objects.equal(this.fieldKey, mdfs.fieldKey) &&
               Objects.equal(this.altName, mdfs.altName) &&
               Objects.equal(this.label, mdfs.label) &&
               Objects.equal(this.description, mdfs.description) &&
               Objects.equal(this.cardinality, mdfs.cardinality) &&
               Objects.equal(this.inputType, mdfs.inputType) &&
               Objects.equal(this.locked, mdfs.locked) &&
               Objects.equal(this.language, mdfs.language));
    }
}
