/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.curate.selector;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Iterator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.dspace.content.Collection;
import org.dspace.content.Community;
import org.dspace.content.DSpaceObject;
import org.dspace.core.Constants;
import org.dspace.core.Context;
import org.dspace.curate.ObjectSelector;
import org.dspace.handle.HandleManager;
import org.dspace.search.DSQuery;
import org.dspace.search.QueryArgs;
import org.dspace.search.QueryResults;

/**
 * SearchSelector obtains its objects from search results.
 * Queries may be constructed programmatically or by loading a parameterized
 * query string via configure() method. Container scope can be applied, but
 * search parameters pertaining to ordering are irrelevant, as are paging ones.
 * Also note that SearchSelector normalizes/filters search results to one
 * DSpaceObject type to prevent inadvertent task performance on containers.
 * (* Definition syntax EBNF *)
 * definition = object type , ":" , search terms , [ "&scope=" , container id ] ;
 * object type = "ITEM" | "COLLECTION" | "COMMUNITY" ;
 * container id = digits , "/" , digits ;
 * (* end definition syntax EBNF *)
 * Example:
 * ITEM:Abelson algorithm&scope=123456789/4
 *
 * @author richardrodgers
 */
public class SearchSelector implements ObjectSelector {

    private static Logger log = LoggerFactory.getLogger(SearchSelector.class);   
    
    private Context context = null;
    private String scope = null;
    private int type = 0;
    private String query = null;
    private QueryResults qResults = null;
    private int read = 0;

    public SearchSelector() {}
    
    @Override
    public Context getContext() {
    	return context;
    }
    
    @Override
    public void setContext(Context context) {
    	this.context = context;
    }
    
    @Override
    public void configure(String definition) {
    	// parse definition. Expected syntax: <type>:<search query>[&scope=<containerId>]
    	int typeIdx = definition.indexOf(":");
    	if (typeIdx > 0) {
    		type = Constants.getTypeID(definition.substring(0, typeIdx).toUpperCase());
    	} else {
    		log.error("Malformed definition: missing object type");
    		throw new UnsupportedOperationException("missing object type");
    	}
    	int scopeIdx = definition.indexOf("&scope=");
    	if (scopeIdx > 0) {
    		// TODO: should validate this value
    		setScope(definition.substring(scopeIdx + 7));
    		setQuery(definition.substring(typeIdx + 1, scopeIdx));
    	} else {
    		setQuery(definition.substring(typeIdx + 1));
    	}
    }
    
    @Override
    public DSpaceObject next() {
    	DSpaceObject dso = null;
  		try {
  			if (qResults == null) {
       			doSearch();
  			}
  	    	// return next normalized type found
  	    	while (read < qResults.getHitCount()) {
  	    		if (type == qResults.getHitTypes().get(read)) {
  	    			dso = HandleManager.resolveToObject(context, qResults.getHitHandles().get(read++));
  	    			break;
  	    		}
  	    		++read;
  	    	}
  		} catch (IOException ioE) { 
       		log.error("Error executing query: '" + query + "' error: " + ioE.getMessage());
    	} catch (SQLException sqlE) {
  			log.error("Error executing query: '" + query + "' error: " + sqlE.getMessage());
  		}
       	return dso;
    }
    
    @Override
    public boolean hasNext() {
    	try {
    		if (qResults == null) {
       			doSearch();
    		}
       	} catch (IOException ioE) {
    		log.error("Error executing query: '" + query + "' error: " + ioE.getMessage());
    	} catch (SQLException sqlE) {
    		log.error("Error executing query: '" + query + "' error: " + sqlE.getMessage());
    	}
    	// return true for next normalized type found
    	while (read < qResults.getHitCount()) {
    		if (type == qResults.getHitTypes().get(read)) {
    			return true;
    		}
    		++read;
    	}
    	return false;
    }
    
    @Override
    public void remove() {
    	throw new UnsupportedOperationException("remove() not supported");
    }
    
    public void setScope(String scope) {
    	this.scope = scope;
    }
    
    public void setType(int type) {
    	this.type = type;
    }
    
    public void setQuery(String query) {
    	this.query = query;
    }
    
    private void doSearch() throws IOException, SQLException {
        // If there is a scope parameter, attempt to dereference it
        // failure will only result in its being ignored
        DSpaceObject container = (scope != null) ? HandleManager.resolveToObject(context, scope) : null;

        // Build log information
        String logInfo = "";

        QueryArgs qArgs = new QueryArgs();
        qArgs.setQuery(query);

        // Perform the search
        if (container == null) {
        	qResults = DSQuery.doQuery(context, qArgs);
        } else if (container instanceof Collection) {
            logInfo = "collection_id=" + container.getID() + ",";
            qResults = DSQuery.doQuery(context, qArgs, (Collection)container);
        } else if (container instanceof Community) {
            logInfo = "community_id=" + container.getID() + ",";
            qResults = DSQuery.doQuery(context, qArgs, (Community)container);
        } else {
            throw new IllegalStateException("Invalid container for search context");
        }
        log.info(logInfo);
    }
}
