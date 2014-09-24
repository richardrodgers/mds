/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.sword;

import org.dspace.authorize.AuthorizeException;
import org.dspace.content.Bitstream;
import org.dspace.content.BitstreamFormat;
import org.dspace.content.Bundle;
import org.dspace.content.Collection;
import org.dspace.content.MDValue;
import org.dspace.content.DSpaceObject;
import org.dspace.content.Item;
import org.dspace.core.ConfigurationManager;
import org.dspace.core.Context;
import org.dspace.core.Utils;
import org.swordapp.server.Deposit;
import org.swordapp.server.SwordAuthException;
import org.swordapp.server.SwordError;
import org.swordapp.server.SwordServerException;

import java.io.IOException;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.StringTokenizer;

public abstract class AbstractSwordContentIngester implements SwordContentIngester
{
    public DepositResult ingest(Context context, Deposit deposit, DSpaceObject dso, VerboseDescription verboseDescription)
            throws DSpaceSwordException, SwordError, SwordAuthException, SwordServerException
    {
        return this.ingest(context, deposit, dso, verboseDescription, null);
    }

    public DepositResult ingest(Context context, Deposit deposit, DSpaceObject dso, VerboseDescription verboseDescription, DepositResult result)
            throws DSpaceSwordException, SwordError, SwordAuthException, SwordServerException
    {
        if (dso instanceof Collection)
        {
            return this.ingestToCollection(context, deposit, (Collection) dso, verboseDescription, result);
        }
        else if (dso instanceof Item)
        {
            return this.ingestToItem(context, deposit, (Item) dso, verboseDescription, result);
        }
        return null;
    }

    public abstract DepositResult ingestToCollection(Context context, Deposit deposit, Collection collection, VerboseDescription verboseDescription, DepositResult result)
    			throws DSpaceSwordException, SwordError, SwordAuthException, SwordServerException;

    public abstract DepositResult ingestToItem(Context context, Deposit deposit, Item item, VerboseDescription verboseDescription, DepositResult result)
    			throws DSpaceSwordException, SwordError, SwordAuthException, SwordServerException;

	protected BitstreamFormat getFormat(Context context, String fileName)
			throws SQLException
	{
		String fext = null;
		int lastDot = fileName.lastIndexOf(".");
		if (lastDot > -1)
		{
			fext = fileName.substring(lastDot + 1);
		}

		if (fext == null)
		{
			return null;
		}

		for (BitstreamFormat format : BitstreamFormat.findAll(context))
		{
			String[] extensions = format.getExtensions();
			for (String ext : extensions)
			{
				if (ext.equals(fext))
				{
					return format;
				}
			}
		}
		return null;
	}
	
	/**
	 * Add the current date to the item metadata.  This looks up
	 * the field in which to store this metadata in the configuration
	 * sword.updated.field
	 *
	 * @param item
	 * @throws DSpaceSwordException
	 */
	protected void setUpdatedDate(Item item, VerboseDescription verboseDescription)
			throws DSpaceSwordException
	{
		String field = ConfigurationManager.getProperty("swordv2-server", "updated.field");
		if (field == null || "".equals(field))
		{
			throw new DSpaceSwordException("No configuration, or configuration is invalid for: sword.updated.field");
		}

		MDValue dc = this.configToDC(field, null);
		item.clearMetadata(dc.getSchema(), dc.getElement(), dc.getQualifier(), MDValue.ANY);
		item.addMetadata(dc.getSchema(), dc.getElement(), dc.getQualifier(), null, Utils.asISO8601(new Date()));

		verboseDescription.append("Updated date added to response from item metadata where available");
	}

	/**
	 * Store the given slug value (which is used for suggested identifiers,
	 * and which DSpace ignores) in the item metadata.  This looks up the
	 * field in which to store this metadata in the configuration
	 * sword.slug.field
	 *
	 * @param item
	 * @param slugVal
	 * @throws DSpaceSwordException
	 */
	protected void setSlug(Item item, String slugVal, VerboseDescription verboseDescription)
			throws DSpaceSwordException
	{
		// if there isn't a slug value, don't set it
		if (slugVal == null)
		{
			return;
		}

		String field = ConfigurationManager.getProperty("swordv2-server", "slug.field");
		if (field == null || "".equals(field))
		{
			throw new DSpaceSwordException("No configuration, or configuration is invalid for: sword.slug.field");
		}

		MDValue dc = this.configToDC(field, null);
		item.clearMetadata(dc.getSchema(), dc.getElement(), dc.getQualifier(), MDValue.ANY);
		item.addMetadata(dc.getSchema(), dc.getElement(), dc.getQualifier(), null, slugVal);

		verboseDescription.append("Slug value set in response where available");
	}

	/**
	 * Utility method to turn given metadata fields of the form
	 * schema.element.qualifier into MDValue objects which can be
	 * used to access metadata in items.
	 *
	 * The def parameter should be null, * or "" depending on how
	 * you intend to use the MDValue object.
	 *
	 * @param config
	 * @param def
	 */
	protected MDValue configToDC(String config, String def)
	{
		String schema = def;
		String element = def;
		String qualifier = def;

		StringTokenizer stz = new StringTokenizer(config, ".");
		schema = stz.nextToken();
		element = stz.nextToken();
		if (stz.hasMoreTokens())
		{
			qualifier = stz.nextToken();
		}

		return new MDValue(schema, element, qualifier, null, null);
	}
}
