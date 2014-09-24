/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */

package org.dspace.sword;

import org.dspace.content.MDValue;
import org.dspace.content.Item;
import org.dspace.core.ConfigurationManager;

import java.util.HashMap;
import java.util.List;
import java.util.Properties;

public class AbstractSimpleDC
{
    protected HashMap<String, String> dcMap = null;
    protected HashMap<String, String> atomMap = null;

    protected void loadMetadataMaps()
    {
        if (this.dcMap == null)
        {
            // we should load our DC map from configuration
            this.dcMap = new HashMap<String, String>();
            Properties props = ConfigurationManager.getProperties("swordv2-server");
            for (Object key : props.keySet())
            {
                String keyString = (String) key;
                if (keyString.startsWith("simpledc."))
                {
                    String k = keyString.substring("simpledc.".length());
                    String v = (String) props.get(key);
                    this.dcMap.put(k, v);
                }
            }
        }

        if (this.atomMap == null)
        {
            this.atomMap = new HashMap<String, String>();
            Properties props = ConfigurationManager.getProperties("swordv2-server");
                for (Object key : props.keySet())
                {
                    String keyString = (String) key;
                    if (keyString.startsWith("atom."))
                    {
                        String k = keyString.substring("atom.".length());
                        String v = (String) props.get(key);
                        this.atomMap.put(k, v);
                    }
                }
        }
    }

    protected SimpleDCMetadata getMetadata(Item item)
    {
        this.loadMetadataMaps();

        SimpleDCMetadata md = new SimpleDCMetadata();
        List<MDValue> all = item.getMetadata(MDValue.ANY, MDValue.ANY, MDValue.ANY, MDValue.ANY);

        for (MDValue mdv : all)
        {
            String valueMatch = mdv.getSchema() + "." + mdv.getElement();
            if (mdv.getQualifier() != null)
            {
                valueMatch += "." + mdv.getQualifier();
            }

            // look for the metadata in the dublin core map
            for (String key : this.dcMap.keySet())
            {
                String value = this.dcMap.get(key);
                if (valueMatch.equals(value))
                {
                    md.addDublinCore(key, mdv.getValue());
                }
            }

            // look for the metadata in the atom map
            for (String key : this.atomMap.keySet())
            {
                String value = this.atomMap.get(key);
                if (valueMatch.equals(value))
                {
                    md.addAtom(key, mdv.getValue());
                }
            }
        }

        return md;
    }
}
