/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.webapi.info;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import com.google.common.primitives.Ints;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.dspace.administer.Component;
import org.dspace.authorize.AuthorizeException;
import org.dspace.core.Context;
import org.dspace.core.Constants;
import org.dspace.content.Bitstream;
import org.dspace.content.BitstreamFormat;
import org.dspace.content.DSpaceObject;
import org.dspace.content.MetadataField;
import org.dspace.content.MetadataSchema;
import org.dspace.content.WorkspaceItem;
import org.dspace.workflow.WorkflowItem;

import org.dspace.webapi.info.domain.AssetsEntity;
import org.dspace.webapi.info.domain.Field;
import org.dspace.webapi.info.domain.FieldsEntity;
import org.dspace.webapi.info.domain.EntityRef;
import org.dspace.webapi.info.domain.Format;
import org.dspace.webapi.info.domain.FormatsEntity;
import org.dspace.webapi.info.domain.Module;
import org.dspace.webapi.info.domain.SystemEntity;
import org.dspace.webapi.info.domain.UsersEntity;
import org.dspace.webapi.info.domain.WorkflowEntity;

/**
 * InfoDao provides domain objects, known as 'entities',
 * representing various information sets.
 *  
 * @author richardrodgers
 */

public class InfoDao {

    private static Logger log = LoggerFactory.getLogger(InfoDao.class);

    public SystemEntity getSystem(Context context) throws SQLException {
        // fetch module components and convert to modules
        List<Module> modules = new ArrayList<>();
        for (Component comp : Component.findAllType(context.getHandle(), 0)) {
            modules.add(new Module(comp.getGroupId(), comp.getArtifactId(), comp.getVersionStr(), new Date(comp.getUpdated().getTime())));
        }
        return new SystemEntity(modules);
    }

    public AssetsEntity getAssets(Context context) throws SQLException {
        return new AssetsEntity(DSpaceObject.count(context, Constants.COMMUNITY),
                                DSpaceObject.count(context, Constants.COLLECTION),
                                DSpaceObject.count(context, Constants.ITEM),
                                DSpaceObject.count(context, Constants.BITSTREAM),
                                Bitstream.getExtent(context));
    }

    public List<EntityRef> getSchemas(Context context) throws SQLException {
        List<MetadataSchema> schemas = MetadataSchema.findAll(context);
        List<EntityRef> refs = new ArrayList<>();
        for (MetadataSchema schema : schemas) {
            refs.add(new EntityRef(schema.getName(), schema.getName(), "metadata"));
        }
        return refs;
    }

    public FieldsEntity getFields(Context context, String key) throws SQLException {
        MetadataSchema schema = null;
        Integer intKey = Ints.tryParse(key);
        if (intKey != null) {
            schema = MetadataSchema.find(context, intKey);
        } else {
            schema = MetadataSchema.findByName(context, key);
        }
        if (schema == null) {
            throw new IllegalArgumentException("No such metadata schema: " + key);
        }
        // get schema fields and add in counts
        List<Field> fields = new ArrayList<>();
        for (MetadataField field : MetadataField.findAllInSchema(context, schema.getSchemaID())) {
            fields.add(new Field(field.getElement(), field.getQualifier(), field.count(context)));
        }
        return new FieldsEntity(schema.getName(), fields);
    }

    public FormatsEntity getFormats(Context context) throws SQLException {
        // fetch formats and get counts for each
        List<Format> formats = new ArrayList<>();
        for (BitstreamFormat bsfmt : BitstreamFormat.findAll(context)) {
            formats.add(new Format(bsfmt.getShortDescription(), bsfmt.getMIMEType(), bsfmt.count()));
        }
        return new FormatsEntity(formats);
    }

    public UsersEntity getUsers(Context context) throws SQLException {
        return new UsersEntity(DSpaceObject.count(context, Constants.EPERSON),
                               DSpaceObject.count(context, Constants.GROUP));
    }

    public WorkflowEntity getWorkflow(Context context) throws SQLException {
        return new WorkflowEntity(WorkspaceItem.count(context),
                                  WorkflowItem.count(context));
    }
}
