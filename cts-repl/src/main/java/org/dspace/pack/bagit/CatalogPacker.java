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
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import edu.mit.lib.bagit.Bag;
import edu.mit.lib.bagit.Filler;
import edu.mit.lib.bagit.Loader;

import org.dspace.pack.Packer;
import static org.dspace.pack.PackerFactory.*;

/**
 * CatalogPacker packs and unpacks Object catalogs in Bagit format. These
 * catalogs are typically used as deletion 'receipts' - i.e. records of what
 * was deleted.
 *
 * @author richardrodgers
 */
public class CatalogPacker implements Packer {

    private String objectId = null;
    private String ownerId = null;
    private List<String> members = null;

    public CatalogPacker(String objectId) {
        this.objectId = objectId;
    }
    
    public CatalogPacker(String objectId, String ownerId, List<String> members) {
        this.objectId = objectId;
        this.ownerId = ownerId;
        this.members = members;
    }

    public String getOwnerId() {
        return ownerId;
    }

    public List<String> getMembers() {
        return members;
    }

    @Override
    public File pack(File packDir) throws IOException {
        Filler filler = new Filler(packDir);
        // set base object properties
        filler.metadata(BAG_TYPE, "MAN");

        filler.property("data/object", OBJECT_TYPE, "deletion");
        filler.property("data/object", OBJECT_ID, objectId);
        
        if (ownerId != null) {
            filler.property("data/object", OWNER_ID, ownerId);
        }
        filler.property("data/object", CREATE_TS,
                        String.valueOf(System.currentTimeMillis()));
        // just serialize member list if non-empty
        if (members.size() > 0) {
            BagUtils.FlatWriter fwriter = BagUtils.flatWriter("members");
            for (String member : members) {
                fwriter.writeLine(member);
            }
            fwriter.close();
        }
        return filler.toPackage();
    }

    @Override
    public void unpack(File archive) throws IOException {
        if (archive == null) {
            throw new IOException("Missing archive for catalog: " + objectId);
        }
        Bag bag = new Loader(archive).load();
        // just populate the member list
        ownerId = bag.property("data/object", OWNER_ID).get(0);
        members = new ArrayList<String>();
        BagUtils.FlatReader reader = BagUtils.flatReader(bag.payloadStream("members"));
        if (reader != null) {
            String member = null;
            while ((member = reader.readLine()) != null) {
                members.add(member);
            }
            reader.close();
        }
    }

    @Override
    public long size(String method) {
        // not currently implemented
        return 0L;
    }

    @Override
    public void setContentFilter(String filter)  {
       // no-op
    }

    @Override
    public void setReferenceFilter(String filter) {
        throw new UnsupportedOperationException("Not supported yet.");
    }
}
