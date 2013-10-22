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
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.dspace.authorize.AuthorizeException;
import org.dspace.content.BoundedIterator;
import org.dspace.content.Collection;
import org.dspace.content.Community;
import org.dspace.content.DSpaceObject;
import org.dspace.content.Item;
import org.dspace.content.MetadataField;
import org.dspace.content.MetadataSchema;
import org.dspace.core.Context;
import org.dspace.curate.ObjectSelector;
import org.dspace.handle.HandleManager;
import org.dspace.storage.rdbms.DatabaseManager;
import org.dspace.storage.rdbms.TableRowIterator;

/**
 * QuerySelector obtains its objects from database query results.
 * Queries may be constructed programmatically or by loading a parameterized
 * query string via the configure() method. This implementation only dispenses
 * Item objects, and has a somewhat constrained type and number of query 
 * parameters, but can nonetheless handle several basic curation use-cases.
 * (* Query syntax EBNF *)
 * query = expr , { "AND" , expr } ;
 * expr = field name | metadata name , oper , value ;
 * field name = characters , { "_" , characters } ;
 * metadata name = characters , "." , characters , [ "." , characters ] ;
 * oper = "=" | "<>" | ">" | "<" | ">=" | "<=" | "BETWEEN" | "LIKE" | "IN" ;
 * value = literal | variable ;
 * literal = "'" , characters , { whitespace , characters } , "'" ;
 * variable = "${" , varname , [ "+" | "-" , number ] , "}" ; 
 * varname = "today" | handle ;
 * (* end syntax EBNF *)
 * Examples:
 * in_archive = '1' AND last_modified > ${today - 7} AND dc.contributor.author = 'Jones'
 * would return installed items authored by Jones modified in the last week
 * owning_collection = ${123456789/2} AND withdrawn = '1'
 * would return all withdrawn items in collection 2
 *
 * @author richardrodgers
 */
public class QuerySelector implements ObjectSelector {

    private static Logger log = LoggerFactory.getLogger(QuerySelector.class);
   
    private Context context = null;
    private String query = null;
    private BoundedIterator<Item> itemIter = null;

    public QuerySelector() {}
    
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
    	setQuery(definition);
    }
    
    @Override
    public DSpaceObject next() {
       	try {
    		if (itemIter == null) {
    			doQuery();
    		}
       		return itemIter.next();
       	} catch (AuthorizeException authE) {
    		log.error("Error executing query: '" + query + "' error: " + authE.getMessage());
    	} catch (SQLException sqlE) {
    		log.error("Error executing query: '" + query + "' error: " + sqlE.getMessage());
    	}
        return null;
    }
    
    @Override
    public boolean hasNext() {
    	try {
    		if (itemIter == null) {
    			doQuery();
    		}
       		return itemIter.hasNext();
       	}
       	catch (AuthorizeException authE)
    	{
    		log.error("Error executing query: '" + query + "' error: " + authE.getMessage());
    	}
    	catch (SQLException sqlE)
    	{
    		log.error("Error executing query: '" + query + "' error: " + sqlE.getMessage());
    	}
    	return false;
    }
    
    @Override
    public void remove() {
    	throw new UnsupportedOperationException("remove() not supported");
    }
    
    public void setQuery(String query) {
    	this.query = query.trim();
    }
    
    private void doQuery() throws AuthorizeException, SQLException {
    	// parse the query string to produce the SQL
    	SqlGenerator sqlGen = new SqlGenerator();
    	sqlGen.parseQuery();
    	List<Object> parameters = sqlGen.getParameters();
    	TableRowIterator rows = null;
    	if (parameters.size() > 0) {
    		rows = DatabaseManager.queryTable(context, "item", sqlGen.getSql(), parameters);
    	} else {
    		rows = DatabaseManager.queryTable(context, "item", sqlGen.getSql());
    	}
    	itemIter = new BoundedIterator<Item>(context, rows);
    }
        
    private class SqlGenerator {

    	private int aliasIdx = 1;
    	private StringBuilder fromSb = new StringBuilder(" FROM item");
    	private StringBuilder whereSb = new StringBuilder(" WHERE");
    	private List<Object> params = new ArrayList<Object>();
    	private int parseIdx = 0;
    	
    	public SqlGenerator() {}
    	
    	public String getSql() {
    		return "SELECT item.*" + fromSb.toString() + whereSb.toString();
    	}
    	
    	public List<Object> getParameters() {
    		return params;
    	}
    	
    	private void parseQuery() throws AuthorizeException, SQLException
    	{
    		int tokenIdx = 0;
    		String token = null;
    		
    		while((token = nextToken()) != null)
    		{
    			switch(tokenIdx % 4)
    			{
    				case 0: // token is an item field name or metadata name
    					doName(token); break;
    				case 2: // token is a value
    					doValue(token); break;
    				case 1: // token is an expression operator - copy it
    				case 3: // token is a boolean connecting expressions - copy it
    					whereSb.append(" ").append(token).append(" "); break;
    				default: break;
    			}
    			++tokenIdx;
    		}
    	}
    	
    	private String nextToken()
    	{
    		// main thing to worry about is internal whitespace in literals
    		int boundary = query.indexOf(" ", parseIdx);
    		String buff = query.substring(parseIdx, boundary);
    		String nextToken = buff;
    		if (buff.charAt(0) == '\'')
    		{
    			// find closing quote
    			int closeIdx = query.indexOf("'", boundary);
    			if (closeIdx > 0)
    			{
    				nextToken = query.substring(parseIdx, closeIdx);
    				parseIdx = closeIdx + 1;
    			}
    			else
    			{
    				log.error("Malformed literal: " + buff);
    				throw new IllegalArgumentException("Malformed literal: " + buff);
    			}
    		}
    		else
    		{
    			parseIdx = boundary + 1;
    		}
    		return nextToken;
    	}
    	   	
    	private void doName(String name) throws AuthorizeException, SQLException
    	{
    		if (name.indexOf(".") > 0)
    		{
    			// it's a metadata field name - decompose it
    			String[] parts = name.split("\\.");
    			String qualifier = (parts.length == 3) ? parts[2] : null;
    			MetadataSchema mds = MetadataSchema.find(context, parts[0]);
    			if (mds == null)
     	        {
    				log.error("No such metadata schema: " + parts[0]);
     	            throw new IllegalArgumentException("No such metadata schema: " + parts[0]);
     	        }
     	        MetadataField mdf = MetadataField.findByElement(context, mds.getSchemaID(), parts[1], qualifier);
     	        if (mdf == null)
     	        {
     	        	log.error("No such metadata field: " + name);
     	            throw new IllegalArgumentException(
     	              "No such metadata field: schema=" + parts[0] + ", element=" + parts[1] + ", qualifier=" + qualifier);
     	        }
     	        // construct the necessary join
     	        fromSb.append(",metadatavalue m").append(aliasIdx);
     	        whereSb.append(" item.item_id = m").append(aliasIdx).append(".item_id");
     	        whereSb.append(" AND m").append(aliasIdx).append(".metadata_field_id = ").append(mdf.getFieldID());
     	        whereSb.append(" AND m").append(aliasIdx).append(".text_value");
     	        ++aliasIdx;
    		}
    		else
    		{
    			// it's an item field - no join
    			whereSb.append(" item.").append(name);
    		}
    	}
    	
       	private void doValue(String value) throws SQLException {
    		if (value.startsWith("${")) {
    			// it's a variable - evaluate it and set as SQL positional parameter
    			whereSb.append("?");
    			int closeIdx = value.indexOf("}");
    			String varExpr = value.substring(2, closeIdx);
    			String varName = varExpr;
    			String oper = null;
    			int adjust = 0;
    			int spaceIdx = varExpr.indexOf(" ");
    			if (spaceIdx > 0)
    			{
    				// need to evaluate expression
    				varName = varExpr.substring(0, spaceIdx);
    				oper = Character.toString(varExpr.charAt(spaceIdx + 1));
    				String adjStr = varExpr.substring(spaceIdx + 2, closeIdx);
    				adjust = Integer.parseInt(adjStr);
    			}
    			if ("today".equals(varName))
    			{
    				long now = System.currentTimeMillis();
    				// make any day adjustments
    				if ("+".equals(oper))
    				{
    					now += adjust * 1000 * 60 * 60 * 24;
    				}
    				else if ("-".equals(oper))
    				{
    					now -= adjust * 1000 * 60 * 60 * 24;
    				}
    				params.add(new Timestamp(now));
    			}
    			else if (varName.indexOf("/") > 0)
    			{
    				// maybe it's a handle?
    				DSpaceObject dso = HandleManager.resolveToObject(context, varName);
    				if (dso != null)
    				{
    					params.add(dso.getID());
    				}
    				else
    				{
    					log.error("Unresolvable handle: '" + varName + "'");
    					throw new IllegalArgumentException("Unresolvable handle: '" + varName + "'");
    				}
    			}
    			else
    			{
    				log.error("Unknown variable name: '" + varName + "'");
    				throw new IllegalArgumentException("Unknown variable name: '" + varName + "'");
    			}
    		}
    		else if (value.startsWith("'"))
    		{
    			// its a literal - just copy in
    			whereSb.append(value);
    		}
    		else
    		{
    			log.error("Invalid value token: '" + value + "'");
    			throw new IllegalArgumentException("Invalid value token: '" + value + "'");
    		}
    	}
    }
}
