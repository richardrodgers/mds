/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */

package org.dspace.ctask.replicate;

import java.io.IOException;
import java.sql.SQLException;

import org.dspace.authorize.AuthorizeException;
import org.dspace.content.DSpaceObject;
import org.dspace.curate.AbstractCurationTask;
import org.dspace.curate.Curator;
import org.dspace.curate.Distributive;
import org.dspace.pack.Packer;
import org.dspace.pack.PackerFactory;

/**
 * EstimateAIPSize task computes the total number of bytes in all the content
 * files (viz Bitstreams, including logos for containers) of the passed object.
 * If a container, it includes all it's members or children. Note that this
 * is quite inexact (and always too small), since real AIPs will include
 * metadata, etc, but should be adequate for a gross approximation. Also note
 * that the size estimates exclude files in bundles that are filtered, as
 * defined by <code>setContentFilter</code> on the AIP Packer.
 * 
 * @author richardrodgers
 * @see TransmitAIP
 */
@Distributive
public class EstimateAIPSize extends AbstractCurationTask {

    @Override
    public int perform(DSpaceObject dso) throws AuthorizeException, IOException, SQLException {
        Packer packer = PackerFactory.instance(dso);
        // just report the size
        long size = packer.size("");
        String msg = "ID: " + dso.getHandle() + " (" + dso.getName() +
                     ") estimated AIP size: " + scaledSize(size, 0);
        report(msg);
        setResult(scaledSize(size, 0));
        return Curator.CURATE_SUCCESS;
    }
    
    String[] prefixes = { "", "kilo", "mega", "giga", "tera", "peta", "exa" };
    private String scaledSize(long size, int idx)
    {
        return (size < 1000L) ? size + " " + prefixes[idx] + "bytes" :
               scaledSize(size / 1000L, idx + 1);
    }
}
