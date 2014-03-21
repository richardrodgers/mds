/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.pack.bagit;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;

import edu.mit.lib.bagit.Bag;
import edu.mit.lib.bagit.Filler;
import edu.mit.lib.bagit.Loader;

import org.dspace.authorize.AuthorizeException;
import org.dspace.content.Bitstream;
import org.dspace.content.BoundedIterator;
import org.dspace.content.Collection;
import org.dspace.content.Community;

import org.dspace.pack.Packer;
import org.dspace.pack.PackerFactory;

import static org.dspace.pack.PackerFactory.*;

/**
 * CommunityPacker Packs and unpacks Community AIPs in Bagit format.
 *
 * @author richardrodgers
 */
public class CommunityPacker implements Packer {
   
    private Community community = null;
    private String archFmt = null;
    
    public CommunityPacker(Community community, String archFmt)  {
        this.community = community;
        this.archFmt = archFmt;
    }

    public Community getCommunity() {
        return community;
    }

    public void setCommunity(Community community)  {
        this.community = community;
    }

    @Override
    public File pack(File packDir) throws AuthorizeException, SQLException, IOException {
        Filler filler = new Filler(packDir.toPath());
        // set base object properties
        filler.metadata(BAG_TYPE, "AIP");

        filler.property("data/object", OBJECT_TYPE, "community");
        filler.property("data/object", OBJECT_ID, community.getHandle());
        
        Community parent = community.getParentCommunity();
        if (parent != null) {
            filler.property("data/object", OWNER_ID, parent.getHandle());
        }
        // then metadata
        BagUtils.writeMetadata(community, filler.payloadStream("metadata.xml"));

        // also add logo if it exists
        Bitstream logo = community.getLogo();
        if (logo != null) {
            filler.payload("logo", logo.retrieve());
        }
        return filler.toPackage(archFmt).toFile();
    }

    @Override
    public void unpack(File archive) throws AuthorizeException, IOException, SQLException {
        if (archive == null) {
            throw new IOException("Missing archive for community: " + community.getHandle());
        }
        Bag bag = new Loader(archive.toPath()).load();
        // add the metadata
        BagUtils.readMetadata(community, bag.payloadStream("metadata.xml"));
        // also install logo or set to null
        community.setLogo(bag.payloadStream("logo"));
        // now write data back to DB
        community.update();
    }

    @Override
    public long size(String method) throws SQLException {
        long size = 0L;
        // logo size, if present
        Bitstream logo = community.getLogo();
        if (logo != null) {
            size += logo.getSize();
        }
        // proceed to children, unless 'norecurse' set
        if (! "norecurse".equals(method)) {
            BoundedIterator<Community> commIter = community.getSubcommunities();
            while (commIter.hasNext()) {
                size += PackerFactory.instance(commIter.next()).size(method);
            }
            BoundedIterator<Collection> collIter = community.getCollections();
            while (collIter.hasNext()) {
                size += PackerFactory.instance(collIter.next()).size(method);
            }
        }
        return size;
    }

    @Override
    public void setContentFilter(String filter) {
        // no-op
    }

    @Override
    public void setReferenceFilter(String filter) {
        throw new UnsupportedOperationException("Not supported yet.");
    }
}
