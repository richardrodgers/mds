/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.pack;

import java.nio.file.Path;
import java.io.InputStream;
import java.io.IOException;
import java.sql.SQLException;

import org.dspace.authorize.AuthorizeException;
import org.dspace.content.DSpaceObject;

/**
 * Packers create standard containers (packages) for DSpace objects
 * and can manifest (create) objects from such packages.
 *
 * @author richardrodgers
 */
public interface Packer {

    /**
     * Sets the specification used for packing
     */
    void setPackingSpec(PackingSpec spec);

    /**
     * Packs (maps) the contents of this object into an archive file.
     *
     * @param dso the DSpaceObject to pack
     * @param packDir the locus of the packing
     * @return the packed archive file
     *
     * @throws AuthorizeException
     * @throws IOException
     * @throws SQLException
     */
    Path pack(DSpaceObject dso, Path packDir) throws AuthorizeException, IOException, SQLException;

    /**
     * Unpacks (maps) the contents of the passed archive file into passed object.
     *
     * @param dso the DSpaceObject to update
     * @param archFile the archive file to unpack
     * @throws AuthorizeException
     * @throws IOException
     * @throws SQLException
     */
    void unpack(DSpaceObject dso, Path archFile) throws AuthorizeException, IOException, SQLException;

    /**
     * Unpacks (maps) the contents of the passed archive stream into passed object.
     *
     * @param dso the DSpaceObject to update
     * @param archStream the archive stream to unpack
     * @throws AuthorizeException 
     * @throws IOException
     * @throws SQLException
     */
    void unpack(DSpaceObject dso, InputStream archStream) throws AuthorizeException, IOException, SQLException;
}
