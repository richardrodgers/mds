/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.sword;

import org.dspace.authorize.AuthorizeException;
import org.dspace.content.Collection;
import org.dspace.content.MDValue;
import org.dspace.content.DSpaceObject;
import org.dspace.content.Item;
import org.dspace.content.WorkspaceItem;
import org.dspace.core.ConfigurationManager;
import org.dspace.core.Context;
import org.dspace.core.Utils;
import org.swordapp.server.Deposit;
import org.swordapp.server.SwordAuthException;
import org.swordapp.server.SwordEntry;
import org.swordapp.server.SwordError;
import org.swordapp.server.SwordServerException;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

public class SimpleDCEntryIngester extends AbstractSimpleDC implements SwordEntryIngester
{
	public SimpleDCEntryIngester()
    {
        this.loadMetadataMaps();
    }

	public DepositResult ingest(Context context, Deposit deposit, DSpaceObject dso, VerboseDescription verboseDescription)
            throws DSpaceSwordException, SwordError, SwordAuthException, SwordServerException
    {
        return this.ingest(context, deposit, dso, verboseDescription, null, false);
    }

	public DepositResult ingest(Context context, Deposit deposit, DSpaceObject dso, VerboseDescription verboseDescription, DepositResult result, boolean replace)
            throws DSpaceSwordException, SwordError, SwordAuthException, SwordServerException
    {
		if (dso instanceof Collection)
        {
            return this.ingestToCollection(context, deposit, (Collection) dso, verboseDescription, result);
        }
		else if (dso instanceof Item)
        {
            return this.ingestToItem(context, deposit, (Item) dso, verboseDescription, result, replace);
        }
		return null;
	}

	public DepositResult ingestToItem(Context context, Deposit deposit, Item item, VerboseDescription verboseDescription, DepositResult result, boolean replace)
            throws DSpaceSwordException, SwordError, SwordAuthException, SwordServerException
    {
		try
		{
			if (result == null)
			{
				result = new DepositResult();
			}
			result.setItem(item);

			// clean out any existing item metadata which is allowed to be replaced
            if (replace)
            {
                this.removeMetadata(item);
            }

			// add the metadata to the item
			this.addMetadataToItem(deposit, item);

			// update the item metadata to inclue the current time as
			// the updated date
			this.setUpdatedDate(item, verboseDescription);

			// in order to write these changes, we need to bypass the
			// authorisation briefly, because although the user may be
			// able to add stuff to the repository, they may not have
			// WRITE permissions on the archive.
			boolean ignore = context.ignoreAuthorization();
			context.setIgnoreAuthorization(true);
			item.update();
			context.setIgnoreAuthorization(ignore);

			verboseDescription.append("Update successful");

			result.setItem(item);
			result.setTreatment(this.getTreatment());

			return result;
		}
		catch (SQLException e)
		{
			throw new DSpaceSwordException(e);
		}
		catch (AuthorizeException e)
		{
			throw new DSpaceSwordException(e);
		}
	}

    private void removeMetadata(Item item)
            throws DSpaceSwordException
    {
        String raw = ConfigurationManager.getProperty("swordv2-server", "metadata.replaceable");
        String[] parts = raw.split(",");
        for (String part : parts)
        {
            MDValue mdv = this.makeDCValue(part.trim(), null);
            item.clearMetadata(mdv.getSchema(), mdv.getElement(), mdv.getQualifier(), MDValue.ANY);
        }
    }

    private void addUniqueMetadata(MDValue mdv, Item item)
    {
        String qual = mdv.getQualifier();
        if (qual == null)
        {
            qual = MDValue.ANY;
        }

        String lang = mdv.getLanguage();
        if (lang == null)
        {
            lang = MDValue.ANY;
        }
        List<MDValue> existing = item.getMetadata(mdv.getSchema(), mdv.getElement(), qual, lang);
        for (MDValue mdValue : existing)
        {
            // FIXME: probably we want to be slightly more careful about qualifiers and languages
            //
            // if the submitted value is already attached to the item, just skip it
            if (mdValue.getValue().equals(mdv.getValue()))
            {
                return;
            }
        }

        // if we get to here, go on and add the metadata
        item.addMetadata(mdv.getSchema(), mdv.getElement(), mdv.getQualifier(), mdv.getLanguage(), mdv.getValue());
    }

	private void addMetadataToItem(Deposit deposit, Item item)
			throws DSpaceSwordException
	{
		// now, go through and get the metadata from the EntryPart and put it in DSpace
		SwordEntry se = deposit.getSwordEntry();

		// first do the standard atom terms (which may get overridden later)
		String title = se.getTitle();
		String summary = se.getSummary();
		if (title != null)
		{
			String titleField = this.dcMap.get("title");
			if (titleField != null)
			{
				MDValue dcv = this.makeDCValue(titleField, title);
                this.addUniqueMetadata(dcv, item);
			}
		}
		if (summary != null)
		{
			String abstractField = this.dcMap.get("abstract");
			if (abstractField != null)
			{
				MDValue dcv = this.makeDCValue(abstractField, summary);
                this.addUniqueMetadata(dcv, item);
			}
		}

		Map<String, List<String>> dc = se.getDublinCore();
		for (String term : dc.keySet())
		{
			String dsTerm = this.dcMap.get(term);
			if (dsTerm == null)
			{
				// ignore anything we don't understand
				continue;
			}

			// now add all the metadata terms
            MDValue mdv = this.makeDCValue(dsTerm, null);
			for (String value : dc.get(term))
			{
                this.addUniqueMetadata(new MDValue(mdv.getSchema(), mdv.getElement(), mdv.getQualifier(), mdv.getLanguage(), value), item);
			}
		}
	}

	public DepositResult ingestToCollection(Context context, Deposit deposit, Collection collection, VerboseDescription verboseDescription, DepositResult result)
            throws DSpaceSwordException, SwordError, SwordAuthException, SwordServerException
    {
        try
		{
			// decide whether we have a new item or an existing one
            Item item = null;
            WorkspaceItem wsi = null;
            if (result != null)
            {
                item = result.getItem();
            }
            else
            {
                result = new DepositResult();
            }
            if (item == null)
            {
                // simple zip ingester uses the item template, since there is no native metadata
                wsi = WorkspaceItem.create(context, collection, true);
                item = wsi.getItem();
            }

            // add the metadata to the item
			this.addMetadataToItem(deposit, item);

			// update the item metadata to inclue the current time as
			// the updated date
			this.setUpdatedDate(item, verboseDescription);

			// DSpace ignores the slug value as suggested identifier, but
			// it does store it in the metadata
			this.setSlug(item, deposit.getSlug(), verboseDescription);

			// in order to write these changes, we need to bypass the
			// authorisation briefly, because although the user may be
			// able to add stuff to the repository, they may not have
			// WRITE permissions on the archive.
			boolean ignore = context.ignoreAuthorization();
			context.setIgnoreAuthorization(true);
			item.update();
			context.setIgnoreAuthorization(ignore);

			verboseDescription.append("Ingest successful");
			verboseDescription.append("Item created with internal identifier: " + item.getID());

			result.setItem(item);
			result.setTreatment(this.getTreatment());

			return result;
		}
		catch (AuthorizeException e)
		{
			throw new SwordAuthException(e);
		}
		catch (SQLException e)
		{
			throw new DSpaceSwordException(e);
		}
		catch (IOException e)
		{
			throw new DSpaceSwordException(e);
		}
    }

    public MDValue makeDCValue(String field, String value)
            throws DSpaceSwordException
    {
        String[] bits = field.split("\\.");
        if (bits.length < 2 || bits.length > 3)
        {
            throw new DSpaceSwordException("invalid DC value: " + field);
        }
        String schema = bits[0];
        String element = bits[1];
        String qualifier = null;
        if (bits.length == 3)
        {
            qualifier = bits[2];
        }
        return new MDValue(schema, element, qualifier, null, value);
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

		MDValue dc = this.makeDCValue(field, null);
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

		MDValue dc = this.makeDCValue(field, null);
		item.clearMetadata(dc.getSchema(), dc.getElement(), dc.getQualifier(), MDValue.ANY);
		item.addMetadata(dc.getSchema(), dc.getElement(), dc.getQualifier(), null, slugVal);

		verboseDescription.append("Slug value set in response where available");
	}

    /**
	 * The human readable description of the treatment this ingester has
	 * put the deposit through
	 *
	 * @return
	 * @throws DSpaceSwordException
	 */
	private String getTreatment() throws DSpaceSwordException
	{
		return "A metadata only item has been created";
	}
}
