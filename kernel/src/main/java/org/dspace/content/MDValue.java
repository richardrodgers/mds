/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.content;

import com.google.common.base.Objects;

/**
 * Immutable object representing a metadata value. It has a
 * schema, element, qualifier, language, and value.
 *
 * @author richardrodgers
 */

public final class MDValue
{
	// wildcard
	public static final String ANY = "*";
	
    /** The schema name of the metadata element */
    private final String schema;
	
    /** The element */
    private final String element;

    /** The qualifier, or <code>null</code> if unqualified */
    private final String qualifier;
    
    /** The language of the field, may be <code>null</code> */
    private final String language;

    /** The value of the field */
    private final String value;
    
	public MDValue(String schema, String element, String qualifier, String language, String value) {
		this.schema = schema;
		this.element = element;
		this.qualifier = qualifier;
		this.language = language;
		this.value = value;
	}
	
	public String getSchema() {
		return schema;
	}
	
	public String getElement() {
		return element;
	}
	
	public String getQualifier() {
		return qualifier;
	}
	
	public String getLanguage() {
		return language;
	}
	
	public String getValue() {
		return value;
	}
		
    public boolean match(String schema, String element, String qualifier, String language) {
        // We will attempt to disprove a match - if we can't we have a match
        if (!element.equals(ANY) && !element.equals(getElement())) {
            // Elements do not match, no wildcard
            return false;
        }

        if (qualifier == null) {
            // Value must be unqualified
            if (getQualifier() != null) {
                // Value is qualified, so no match
                return false;
            }
        } else if (!qualifier.equals(ANY)) {
            // Not a wildcard, so qualifier must match exactly
            if (!qualifier.equals(getQualifier())) {
                return false;
            }
        }

        if (language == null) {
            // Value must be null language to match
            if (getLanguage() != null) {
                // Value is qualified, so no match
                return false;
            }
        } else if (!language.equals(ANY)) {
            // Not a wildcard, so language must match exactly
            if (!language.equals(getLanguage())) {
                return false;
            }
        }

        if (!schema.equals(ANY)) {
            if (getSchema() != null && ! getSchema().equals(schema)) {
                // The namespace doesn't match
                return false;
            }
        }

        // If we get this far, we have a match
        return true;
    }
    
    @Override
    public boolean equals(Object value) {
    	if (value == null) return false;
    	if (this.getClass() != value.getClass()) return false;
    	final MDValue mdValue = (MDValue)value;
    	return(Objects.equal(this.schema, mdValue.schema) &&
    		   Objects.equal(this.element, mdValue.element) &&
    		   Objects.equal(this.qualifier, mdValue.qualifier) &&
    		   Objects.equal(this.language, mdValue.language) &&
    		   Objects.equal(this.value, mdValue.value));
    }
}
