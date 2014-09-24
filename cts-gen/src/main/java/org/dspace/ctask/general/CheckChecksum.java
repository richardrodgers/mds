/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.ctask.general;

import java.io.IOException;
import java.io.InputStream;
import java.sql.SQLException;

import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import org.dspace.authorize.AuthorizeException;
import org.dspace.content.Bitstream;
import org.dspace.content.Bundle;
import org.dspace.content.DSpaceObject;
import org.dspace.content.Item;
import org.dspace.core.Constants;
import org.dspace.curate.AbstractCurationTask;
import org.dspace.curate.Suspendable;

import static org.dspace.curate.Curator.*;

/**
 * CheckChecksum computes a checksum for each selected bitstream
 * and compares it to the stored ingest-time calculated value.
 * Task succeeds if all checksums agree, else fails.
 *
 * @author richardrodgers
 */

@Suspendable(invoked=Invoked.INTERACTIVE)
public class CheckChecksum extends AbstractCurationTask {   

    private static final int BUFF_SIZE = 4096;
    // we can live with 4k preallocation
    private static final byte[] buffer = new byte[BUFF_SIZE];

    /**
     * Perform the curation task upon passed DSO
     *
     * @param dso the DSpace object
     * @throws IOException
     */
    @Override
    public int perform(DSpaceObject dso) throws AuthorizeException, IOException, SQLException {
        if (dso.getType() == Constants.ITEM) {
            Item item = (Item)dso;
            for (Bundle bundle : item.getBundles()) {
                for (Bitstream bs : bundle.getBitstreams()) {
                    String compCs = checksum(bs.retrieve(), bs.getChecksumAlgorithm());
                    if (! compCs.equals(bs.getChecksum())) {
                        String result = "Checksum discrepancy in item: " + item.getHandle() +
                                      " for bitstream: '" + bs.getName() + "' (seqId: " + bs.getSequenceID() + ")" +
                                      " ingest: " + bs.getChecksum() + " current: " + compCs;
                        report(result);
                        setResult(result);
                        return CURATE_FAIL;
                    }
                }
            }
            setResult("All bitstream checksums agree in item: " + item.getHandle());
            return CURATE_SUCCESS;
        } else {
            return CURATE_SKIP;
        }
    }

    // Argh - should be better way using Guava, etc - this all a copy of obselete curate Utils code
    private String checksum(InputStream in, String algorithm) throws IOException {
        try {
            DigestInputStream din = new DigestInputStream(in,
                                        MessageDigest.getInstance(algorithm));
            while (true) {
                synchronized (buffer) {
                    if (din.read(buffer) == -1) {
                        break;
                    }
                    // otherwise, a no-op
                }
            }
            return toHex(din.getMessageDigest().digest());
        } catch (NoSuchAlgorithmException nsaE) {
            throw new IOException(nsaE.getMessage(), nsaE);
        }
    }

    static final char[] HEX_CHARS = "0123456789abcdef".toCharArray();
    static String toHex(byte[] data) {
         if ((data == null) || (data.length == 0)) {
            return null;
        }
        char[] chars = new char[2 * data.length];
        for (int i = 0; i < data.length; ++i) {
            chars[2 * i] = HEX_CHARS[(data[i] & 0xF0) >>> 4];
            chars[2 * i + 1] = HEX_CHARS[data[i] & 0x0F];
        }
        return new String(chars);
    }
}
