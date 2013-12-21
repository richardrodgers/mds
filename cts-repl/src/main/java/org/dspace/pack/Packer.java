/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.pack;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;

import org.dspace.authorize.AuthorizeException;

/**
 * Packer packages and unpackages DSpace objects into archive files
 *
 * @author richardrodgers
 */
public interface Packer
{
    /**
     * Packs (maps) the contents of this object into an archive file.
     *
     * @param packDir the locus of the packing
     * @return the packed archive file
     * @throws AuthorizeException
     * @throws IOException
     * @throws SQLException
     */
    File pack(File packDir) throws AuthorizeException, IOException, SQLException;

    /**
     * Unpacks (maps) the contents of the passed archive file into this object.
     *
     * @param archFile the archive file to unpack
     * @throws AuthorizeException
     * @throws IOException
     * @throws SQLException
     */
    void unpack(File archFile) throws AuthorizeException, IOException, SQLException;

    /**
     * Estimate the size of the object as packed into a bag. This estimate uses
     * any defined content filters.
     *
     * @param method how to estimate - not currently implemented
     * @return the bagged object size in bytes
     * @throws SQLException
     */
    long size(String method) throws SQLException;

    /**
     * Sets a filter on this packer. A filter will ignore (skip) object content
     * when packing to a bag. Filter syntax:
     * <code>["+"]{BundleName,}+</code>
     * That is, a list of Item bundle names taken to be exclusions, unless
     * preceeded by a '+", where the list is taken to be inclusive.
     *
     * @param filter
     */
    void setContentFilter(String filter);

    void setReferenceFilter(String filter);
}
