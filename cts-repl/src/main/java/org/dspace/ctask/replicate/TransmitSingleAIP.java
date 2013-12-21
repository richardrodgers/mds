/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */

package org.dspace.ctask.replicate;

import org.dspace.curate.Distributive;

/**
 * TransmitSingleAIP task creates an AIP suitable for replication, and forwards
 * it to the replication system for transmission (upload).
 * <P>
 * This class is a companion of TransmitAIP, and differs only in that it
 * inhibits container iteration (distribution to members) when invoked upon
 * a container object.
 * <P>
 * This task is primarily for usage via the ReplicateConsumer, as it needs to 
 * interact with a single object at a time.
 * <P>
 * The type of AIP produced is based on the 'packer.pkgtype' setting
 * in 'replicate.cfg'. See the org.dspace.pack.PackerFactory for more info.
 * Companion of TransmitAIP, that inhibits container iteration
 * 
 * @author richardrodgers
 * @see TransmitAIP
 * @see PackerFactory
 * @see ReplicateConsumer
 */
@Distributive
public class TransmitSingleAIP extends TransmitAIP {}
