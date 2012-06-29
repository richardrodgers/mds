/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.content;

// NB: maybe should be lang.AutoCloseable when Java 7 supported
import java.io.Closeable;
import java.sql.SQLException;

import java.util.Iterator;

import org.dspace.core.Constants;
import org.dspace.core.Context;
import org.dspace.storage.rdbms.TableRow;
import org.dspace.storage.rdbms.TableRowIterator;

/**
 * Specialized DSO iterator for potentially large object sets.
 * Primary Characteristics:
 * Iterator may be restricted to either/both a starting record
 * offset, and a maximum number of records to deliver.
 * Iterator maintains a constant memory profile by decaching
 * last-accessed objects from context.
 *
 * @author richardrodgers
 */
public class BoundedIterator<T extends DSpaceObject> implements Iterator<T>, Closeable {

    /** Our context */
    private Context context;

    /** The table row iterator */
    private TableRowIterator rows;
    
    /** Reference to the revious object */
    private T prevRef;
    
    /** Read offset */
    private long offset;
    
    /** Maximum number to iterate over */
    private long max;
    
    /** Read cursor */
    private long cursor = 0L;
    
    /**
     * Construct a bounded iterator using a set of TableRow objects from
     * the item table
     * 
     * @param context
     *            our context
     * @param rows
     *            the rows that correspond to the Items to be iterated over
     */
    public BoundedIterator(Context context, TableRowIterator rows)  {
    	this(context, rows, 0L, -1L);
    }
    
    public BoundedIterator(Context context, TableRowIterator rows, long offset, long max) {
        this.context = context;
        this.rows = rows;
        this.offset = offset;
        this.max = max;
    }
   
    /**
     * Find out if there are any more objects to iterate over
     * 
     * @return <code>true</code> if there are more objects
     * @throws SQLException
     */
    @Override
    public boolean hasNext() {
    	try {
    		if (rows != null) {
    			return rows.hasNext() && (max < 0L || cursor < max);
    		}
    	} catch (SQLException sqlE) { }
    	return false;
    }

    /**
     * Get the next object in the iterator. Returns <code>null</code> if there
     * are no more objects.
     * 
     * @return the next item, or <code>null</code>
     * @throws SQLException
     */
    @Override
    public T next() {
    	try {
    		if (rows != null && rows.hasNext()) {
    			cursor++;
    			return nextByRow();
    		} else if (rows != null) {
    			close();
    		}
    	} catch (SQLException sqlE) {}
    	return null;
    }
    
    /**
     * Remove - not supported
     */
    @Override
    public void remove() {
    	throw new UnsupportedOperationException("remove not supported");
    }
    
    /**
     * Return the next object instantiated from the supplied TableRow
     * 
     * @return	the object or null if none
     * @throws SQLException
     */
    private T nextByRow() throws SQLException {
        // Convert the row into a DSO
        TableRow row = rows.next();
        String tName = row.getTable();
        int objType = Constants.getTypeID(tName.toUpperCase());
        // Check cache
        T dso = (T)context.fromCache(DSpaceObject.classFromType(objType), row.getIntColumn(tName.toLowerCase() + "_id"));

        if (dso == null) {
        	dso = (T)DSpaceObject.composeDSO(context, objType, row);
        }
        // if prev assigned, decache it
        if (prevRef != null) {
        	prevRef.decacheMe();
        }
        prevRef = dso;
        return dso;
    }

    /**
     * Dispose of this Iterator, and its underlying resources
     */
    public void close() {
        if (rows != null) {
            rows.close();
            rows = null;
        }
        prevRef = null;
    }
}
