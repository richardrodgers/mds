/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.ctask.replicate;

import java.io.IOException;
import java.sql.Timestamp;
import java.util.Map;
import java.util.Properties;

import org.skife.jdbi.v2.Handle;

import org.dspace.core.Context;

/**
 * Odometer holds a small set of persistent operational parameters of service
 * usage. This can assist the consumer of the service to monitor it's cost,
 * inter alia.
 * <p>
 * The Odometer tracks basic statistics of replication activities: bytes uploaded, 
 * modified, count of objects, and external objectstore size.
 * <p>
 * See org.dspace.ctask.replicate.ReplicaManager for how the Odometer readings
 * are kept up-to-date.
 *
 * @author richardrodgers
 * @see org.dspace.ctask.replicate.ReplicaManager
 */
public class Odometer {

    // names of fixed properties
    public static final String COUNT = "count";
    public static final String SIZE = "storesize";
    public static final String UPLOADED = "uploaded";
    public static final String DOWNLOADED = "downloaded";
    public static final String MODIFIED = "modified";
    // is this a read-only copy?
    private boolean readOnly = false;
    // odometer properties - hold the values
    private Properties odoProps = null;
    
    Odometer(boolean readOnly) throws IOException {
        this.readOnly = readOnly;
        odoProps = new Properties();
        try (Context context = new Context()) {
            Map<String, Object> m = context.getHandle().select("SELECT * FROM odometer").get(0);
            setProperty(COUNT, (Long)m.get(COUNT));
            setProperty(SIZE, (Long)m.get(SIZE));
            setProperty(UPLOADED, (Long)m.get(UPLOADED));
            setProperty(DOWNLOADED, (Long)m.get(DOWNLOADED));
            context.abort();
        } catch (Exception e) {}
    }

    void save() throws IOException {
        if (! readOnly) {
            try (Context context = new Context()) {
                context.getHandle().execute("UPDATE odometer SET count = ?, storesize = ?, uploaded = ?, downloaded = ?, modified = ?",
                                            getProperty(COUNT), getProperty(SIZE),
                                            getProperty(UPLOADED), getProperty(DOWNLOADED), new Timestamp(System.currentTimeMillis()));
                context.complete();
            } catch (Exception e) {}
        }
    }

    void adjustProperty(String name, long adjustment) {
        long val = getProperty(name);
        setProperty(name, val + adjustment);
    }

    void setProperty(String name, long value) {
        odoProps.setProperty(name, String.valueOf(value));
    }
    
    public long getProperty(String name) {
       String val = odoProps.getProperty(name);
       long lval = val != null ? Long.valueOf(val) : 0L;
       return lval;
    }
}
