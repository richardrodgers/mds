/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.ctask.replicate;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import edu.mit.lib.bagit.Bag;
import edu.mit.lib.bagit.Filler;
import edu.mit.lib.bagit.Loader;

import org.dspace.pack.bagit.BagUtils;
import static org.dspace.pack.bagit.BagUtils.*;

/**
 * BagItCatalog packs and unpacks Object catalogs in Bagit format. These
 * catalogs are typically used as deletion 'receipts' - i.e. records of what
 * was deleted.
 *
 * @author richardrodgers
 */
public class BagItCatalog {

    private String objectId = null;
    private String ownerId = null;
    private List<String> members = null;

    public BagItCatalog(String objectId) {
        this.objectId = objectId;
    }
    
    public BagItCatalog(String objectId, String ownerId, List<String> members) {
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

    public Path pack(Path packDir) throws IOException {
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

    public void unpack(Path archive) throws IOException {
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
}
