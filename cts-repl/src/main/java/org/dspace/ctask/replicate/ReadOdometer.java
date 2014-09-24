/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.ctask.replicate;

import java.io.IOException;

import org.dspace.content.DSpaceObject;
import org.dspace.curate.AbstractCurationTask;
import org.dspace.curate.Curator;
import org.dspace.curate.Distributive;

/**
 * ReadOdometer simply reads and displays the odometer data. Since this data
 * is currently only maintained per site, the actual data object is ignored. 
 * <p>
 * 
 * @author richardrodgers
 * @see Odometer
 */
@Distributive
public class ReadOdometer extends AbstractCurationTask
{
    /**
     * Performs the "Read Odometer" task.
     * @param dso this param is ignored, as the odometer is sitewide
     * @return integer which represents Curator return status
     * @throws IOException 
     */
    @Override
    public int perform(DSpaceObject dso) throws IOException  {

        Odometer odometer = new Odometer(true);
        StringBuilder sb = new StringBuilder();
        sb.append("Objects:    ").append(odometer.getProperty("count")).append(", \n");
        sb.append("Size:       ").append(scaledSize(odometer.getProperty("storesize"), 0)).append(", \n");
        sb.append("Uploaded:   ").append(scaledSize(odometer.getProperty("uploaded"), 0)).append(", \n");
        sb.append("Downloaded: ").append(scaledSize(odometer.getProperty("downloaded"), 0)).append("\n");
        String msg = sb.toString();           
        report(msg);
        setResult(msg);
        return Curator.CURATE_SUCCESS;
    }
    
    String[] prefixes = { "", "kilo", "mega", "giga", "tera", "peta", "exa" };
    private String scaledSize(long size, int idx) {
        return (size < 1000L) ? size + " " + prefixes[idx] + "bytes" :
               scaledSize(size / 1000L, idx + 1);
    }
}
