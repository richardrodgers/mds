/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.pack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.dspace.content.Bitstream;
import org.dspace.content.Bundle;

/**
 * PackingFilter exposes methods used by packers
 * to filter packaged content and/or metadata. Current implementation
 * reads data from a packing spec and offers Bundle-name
 * based content filtering, metadata filtering via metadata sets
 * or a metadata view, and 'reference' filtering for bitstreams.
 * A filter will ignore (skip) object content
 * Content Filter syntax:
 * <code>["+"]{BundleName | MDSetName,}+</code>
 * That is, a list of Item bundle names taken to be exclusions, unless
 * preceeded by a '+", where the list is taken to be inclusive.
 *
 * @author richardrodgers
 */
public class PackingFilter {

    private List<String> bundleFilters = new ArrayList<>();
    private boolean bundleExclude = true;
    private List<RefFilter> refFilters = new ArrayList<>();
    private List<String> mdsetFilters = new ArrayList<>();
    private boolean mdsetExclude = true;
    private String mdView;

    public PackingFilter(PackingSpec spec) {
        // Start with content (bundle) filter(s)
        // If our filter list of bundles begins with a '+', then this list
        // specifies all the bundles to *include*. Otherwise all 
        // bundles *except* the listed ones are included
        String bundleSpec = spec.getContentFilter();
        if (bundleSpec != null) {
            if (bundleSpec.startsWith("+")) {
                bundleExclude = false;
                //remove the preceding '+' from our bundle list
                bundleSpec = bundleSpec.substring(1);
            }
            bundleFilters = Arrays.asList(bundleSpec.split(","));
        }
        // Next, parse the metadata spec
        String mdSpec = spec.getMetadataFilter();
        if (mdSpec != null) {
            if (mdSpec.startsWith("=")) {
                mdView = mdSpec.substring(1);
            } else {
                if (mdSpec.startsWith("+")) {
                    mdsetExclude = false;
                    //remove the preceding '+' from our bundle list
                    mdSpec = mdSpec.substring(1);
                }
                mdsetFilters = Arrays.asList(mdSpec.split(","));
            }
        }
        // Finally, look at reference filters
        String refSpec = spec.getReferenceFilter();
        if (refSpec != null) {
            for (String filter : refSpec.split(",")) {
                refFilters.add(new RefFilter(filter));
            }
        }
    }

    /**
     * Returns true if the bundle (and its contents) should
     * be included in the operation, otherwise false
     *
     * @param name the name of the Item bundle
     * @return filtered true if bundle should be included, else false
     */
    public boolean acceptBundle(String name) {
        boolean onList = bundleFilters.contains(name);
        return bundleExclude ? ! onList : onList;
    }

    /**
     * Returns name of metadata view, or null if not defined
     *
     * @return viewName the name of the metadata view, null if undefined
     */
    public String getMdViewName() {
        return mdView;
    }

    /**
     * Returns true if the mdset (schema) should
     * be included in the operation, otherwise false
     *
     * @param name the name of the mdset (schema)
     * @return filtered true if mdset should be included, else false
     */
    public boolean acceptMdSet(String setName) {
        boolean onList = mdsetFilters.contains(setName);
        return mdsetExclude ? ! onList : onList;
    }

    /**
     * Returns the URL of the passed bitstream as a 
     * reference, or null if not filtered by reference.
     * 
     * @param bundle the Item bundle
     * @param bitstream the Bundle's bitstream
     * @return the URL of the bitstream reference or null if not filtered
     */
    public String byReference(Bundle bundle, Bitstream bs) {
        for (RefFilter filter : refFilters) {
            if (filter.bundle.equals(bundle.getName()) &&
                filter.size == bs.getSize()) {
                return filter.url;
            }
        }
        return null;
    }

    private class RefFilter {
        public String bundle;
        public long size;
        public String url;

        public RefFilter(String filter)  {
            String[] parts = filter.split(" ");
            bundle = parts[0];
            size = Long.valueOf(parts[1]);
            url = parts[2];
        }
    }
}
