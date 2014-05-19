/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.pack;

import java.io.InputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.sql.SQLException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.dspace.authorize.AuthorizeException;
import org.dspace.content.DSpaceObject;
import org.dspace.core.Context;
import org.dspace.mxres.ResourceMap;

/**
 * Packager constructs packages given content objects and packaging specifications,
 * or the reverse: constructs objects given packages.
 * Bagit-based ("bagit") package formats are supported.
 *
 * @author richardrodgers
 */
public class Packager {

    private static Logger log = LoggerFactory.getLogger(Packager.class);

    /**
     * Returns a package of a DSpaceObject, given a scope.
     * Scope will allow selection of object-relative packing spec from a ResourceMap lookup.
     *
     */
    public static Path toPackage(DSpaceObject dso, String scope, Path packDir) throws AuthorizeException, IOException, SQLException {
        // look up the spec resource mapped to this object
        try (Context context = new Context()) {
            return toPackage(context, dso, scope, packDir);
        }
    }

    /**
     * Returns a package of a DSpaceObject, given a scope.
     * Scope will allow selection of object-relative packing spec from a ResourceMap lookup.
     *
     */
    public static Path toPackage(Context context, DSpaceObject dso, String scope, Path packDir) throws AuthorizeException, IOException, SQLException {
        // look up the spec resource mapped to this object
        PackingSpec spec = (PackingSpec)new ResourceMap(PackingSpec.class, context).findResource(dso, scope);
        return toPackage(dso, spec, packDir);
    }

    /**
     * Returns a package of a DSpaceObject, using the passed packing spec.
     *
     */
    public static Path toPackage(DSpaceObject dso, PackingSpec spec, Path packDir) throws AuthorizeException, IOException, SQLException {
        if (dso != null && spec != null) {
            try {
                Packer packer = (Packer)Class.forName(spec.getPacker()).newInstance();
                packer.setPackingSpec(spec);
                return packer.pack(dso, packDir);
            } catch (ClassNotFoundException cfne) {
                log.error("No such packer: " + spec.getPacker());
                throw new IOException("No such packer: " + spec.getPacker());
            } catch (InstantiationException | IllegalAccessException ie) {
                log.error("Cannot instantiate packer: " + spec.getPacker());
                throw new IOException("Cannot instantiate packer: " + spec.getPacker());
            } catch (Exception e) {
                log.error("Error packing object: " + e.getMessage());
                throw e;
            }
        } else {
            log.info("DSpaceObject or spec is null");
        }
        return null;
    }

    /**
     * Unpacks a package into passed DSpaceObject, given a scope.
     * Scope will allow selection of an oject-relative packing spec from a ResourceMap lookup.
     *
     */
    public static void fromPackage(Context context, DSpaceObject dso, String scope, Path archFile) throws AuthorizeException, IOException, SQLException {
        // look up the spec resource mapped to this object
        PackingSpec spec = (PackingSpec)new ResourceMap(PackingSpec.class, context).findResource(dso, scope);
        fromPackage(dso, spec, archFile);
    }

    /**
     * Unpacks a package into passed DSpaceObject, using the passed packing spec.
     *
     */
    public static void fromPackage(DSpaceObject dso, PackingSpec spec, Path archFile) throws AuthorizeException, IOException, SQLException {
        if (dso != null && spec != null) {
            try {
                Packer packer = (Packer)Class.forName(spec.getPacker()).newInstance();
                packer.setPackingSpec(spec);
                packer.unpack(dso, archFile);
            } catch (ClassNotFoundException e) {
                log.error("No such packer: " + spec.getPacker());
                throw new IOException("No such packer: " + spec.getPacker());
            } catch (InstantiationException | IllegalAccessException ie) {
                log.error("Cannot instantiate packer: " + spec.getPacker());
                throw new IOException("Cannot instantiate packer: " + spec.getPacker());
            } catch (Exception e) {
                log.error("Error packing object: " + e.getMessage());
                throw e;
            }
        } else {
            log.info("DSpaceObject or spec is null");
        }
    }

    /**
     * Unpacks a package stream into passed DSpaceObject, given a scope.
     * Scope will allow selection of an oject-relative packing spec from a ResourceMap lookup.
     *
     */
    public static void fromPackageStream(Context context, DSpaceObject dso, String scope, InputStream archStream) throws AuthorizeException, IOException, SQLException {
        // look up the spec resource mapped to this object
        PackingSpec spec = (PackingSpec)new ResourceMap(PackingSpec.class, context).findResource(dso, scope);
        fromPackageStream(dso, spec, archStream);
    }

    /**
     * Unpacks a package into passed DSpaceObject, using the passed packing spec.
     *
     */
    public static void fromPackageStream(DSpaceObject dso, PackingSpec spec, InputStream archStream) throws AuthorizeException, IOException, SQLException {
        if (dso != null && spec != null) {
            try {
                Packer packer = (Packer)Class.forName(spec.getPacker()).newInstance();
                packer.setPackingSpec(spec);
                packer.unpack(dso, archStream);
            } catch (ClassNotFoundException e) {
                log.error("No such packer: " + spec.getPacker());
                throw new IOException("No such packer: " + spec.getPacker());
            } catch (InstantiationException | IllegalAccessException ie) {
                log.error("Cannot instantiate packer: " + spec.getPacker());
                throw new IOException("Cannot instantiate packer: " + spec.getPacker());
            } catch (Exception e) {
                log.error("Error unpacking stream: " + e.getMessage());
                throw e;
            }
        } else {
            log.info("DSpaceObject or spec is null");
        }
    }
}
