/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.mxres;

import java.sql.SQLException;

import org.dspace.core.Context;
import org.dspace.storage.rdbms.DatabaseManager;
import org.dspace.storage.rdbms.TableRow;

/**
 * MetadataSpecBuilder delivers MetadataSpecs, interpreting the
 * resource ID as a primary DB key to Spec table.
 * 
 * @author richardrodgers
 */

public class MetadataSpecBuilder implements ResourceBuilder {

     public MetadataSpecBuilder() {}
     
    /**
     * Constructs a metadata spec resource given an identifier.
     * Identifier presumed to be unique within the resource type.
     * 
     * @parm context - the DSpace context
     * @param resId - the unique (modulo class) identifier for the resource
     * @return resource - the extensible resource instance
     */
    @Override
    public ExtensibleResource build(Context context, String resId) {
        try {
            TableRow tRow = DatabaseManager.find(context, "mdspec", Integer.valueOf(resId));
            if (tRow != null) {
                return new MetadataSpec(context, tRow);
            }
        } catch (SQLException sqlE) {}
        return null;
    }
}
