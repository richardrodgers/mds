/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.webapi.registry;

import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.skife.jdbi.v2.Handle;
import org.skife.jdbi.v2.StatementContext;
import org.skife.jdbi.v2.tweak.ResultSetMapper;
import org.skife.jdbi.v2.util.StringMapper;

import org.dspace.authorize.AuthorizeException;
import org.dspace.authorize.AuthorizeManager;
import org.dspace.content.Bitstream;
import org.dspace.content.BitstreamFormat;
import org.dspace.content.DSpaceObject;
import org.dspace.content.MetadataSchema;
import org.dspace.content.MetadataField;
import org.dspace.content.NonUniqueMetadataException;
import org.dspace.core.ConfigurationManager;
import org.dspace.core.Context;

import org.dspace.webapi.registry.domain.EntityRef;
import org.dspace.webapi.registry.domain.SchemaEntity;
import org.dspace.webapi.registry.domain.FieldEntity;
import org.dspace.webapi.registry.domain.FormatEntity;

/**
 * RegistryDao provides registry domain objects to the service.
 *  
 * @author richardrodgers
 */

public class RegistryDao {

    public SchemaEntity getSchema(Context context, int id) throws SQLException {
        MetadataSchema schema = MetadataSchema.find(context, id);
        if (schema == null) {
            throw new IllegalArgumentException("No such metadata schema: " + id);
        }
        return new SchemaEntity(schema);
    }

    public SchemaEntity findSchema(Context context, String key) throws AuthorizeException, SQLException {
        MetadataSchema schema = MetadataSchema.findByName(context, key);
        if (schema == null) {
            throw new IllegalArgumentException("No metadata schema for key: " + key);
        }
        return new SchemaEntity(schema);
    }

    public List<EntityRef> findSchemas(Context context, String query) throws SQLException {
        List<MetadataSchema> schemas = MetadataSchema.findAll(context);
        List<EntityRef> refs = new ArrayList<>();
        for (MetadataSchema schema : schemas) {
            refs.add(new EntityRef(schema.getName(), schema.getSchemaID(), "schema"));
        }
        return refs;
    }

    public List<EntityRef> getSchemas(Context context) throws SQLException {
        List<MetadataSchema> schemas = MetadataSchema.findAll(context);
        List<EntityRef> refs = new ArrayList<>();
        for (MetadataSchema schema : schemas) {
            refs.add(new EntityRef(schema.getName(), schema.getSchemaID(), "schema"));
        }
        return refs;
    }

    public SchemaEntity createSchema(Context context, SchemaEntity entity) throws AuthorizeException, SQLException {
        MetadataSchema schema = new MetadataSchema(entity.getNamespace(), entity.getName());
        try {
            schema.create(context);
            schema.update(context);
        } catch (NonUniqueMetadataException nuExp) {}
        return new SchemaEntity(schema);
    }

    public SchemaEntity updateSchema(Context context, int id, SchemaEntity entity) throws AuthorizeException, SQLException {
        MetadataSchema schema = MetadataSchema.find(context, id);
        if (schema == null) {
            throw new IllegalArgumentException("No such metadata schema: " + id);
        }
        try {
            entity.sync(schema);
            schema.update(context);
         } catch (NonUniqueMetadataException nuExp) {}
        return new SchemaEntity(schema);
    }

    public SchemaEntity removeSchema(Context context, int id) throws AuthorizeException, SQLException {
        MetadataSchema schema = MetadataSchema.find(context, id);
        if (schema == null) {
            throw new IllegalArgumentException("No such metadata schema: " + id);
        }
        SchemaEntity entity = new SchemaEntity(schema);
        schema.delete(context);
        return entity;
    }

    public FieldEntity getField(Context context, int id) throws SQLException {
        MetadataField field = MetadataField.find(context, id);
        if (field == null) {
            throw new IllegalArgumentException("No such metadata field: " + id);
        }
        return new FieldEntity(field);
    }

    public List<EntityRef> getFields(Context context) throws SQLException {
        List<MetadataField> fields = MetadataField.findAll(context);
        List<EntityRef> refs = new ArrayList<>();
        for (MetadataField field : fields) {
            refs.add(new EntityRef(field.getElement(), field.getFieldID(), "field"));
        }
        return refs;
    }

    public FieldEntity createField(FieldEntity entity) throws AuthorizeException, IOException, NonUniqueMetadataException, SQLException {
        Context ctx = new Context();
        ctx.turnOffAuthorisationSystem();
        MetadataSchema schema = MetadataSchema.find(ctx, entity.getSchemaId());
        MetadataField field = new MetadataField(schema, entity.getElement(), entity.getQualifier(), entity.getScopeNote());
        field.create(ctx);
        field.update(ctx);
        ctx.complete();
        return new FieldEntity(field);
    }

    public FieldEntity updateField(Context context, int id, FieldEntity entity) throws AuthorizeException, IOException, NonUniqueMetadataException, SQLException {
        MetadataField field = MetadataField.find(context, id);
        if (field == null) {
            throw new IllegalArgumentException("No such metadata field: " + id);
        }
        entity.sync(field);
        field.update(context);
        return new FieldEntity(field);
    }

    public FieldEntity removeField(Context context, int id) throws AuthorizeException, SQLException {
        MetadataField field = MetadataField.find(context, id);
        if (field == null) {
            throw new IllegalArgumentException("No such metadata field: " + id);
        }
        FieldEntity entity = new FieldEntity(field);
        field.delete(context);
        return entity;
    }

    public FormatEntity getFormat(Context context, int id) throws SQLException {
        BitstreamFormat format = BitstreamFormat.find(context, id);
        if (format == null) {
            throw new IllegalArgumentException("No such bitstream format: " + id);
        }
        return new FormatEntity(format);
    }

    public FormatEntity createFormat(FormatEntity entity) throws AuthorizeException, SQLException {
        Context ctx = new Context();
        ctx.turnOffAuthorisationSystem();
        BitstreamFormat format = BitstreamFormat.create(ctx);
        entity.sync(format);
        format.update();
        ctx.complete();
        return new FormatEntity(format);
    }

    public FormatEntity updateFormat(Context context, int id, FormatEntity entity) throws AuthorizeException, SQLException {
        BitstreamFormat format = BitstreamFormat.find(context, id);
        if (format == null) {
            throw new IllegalArgumentException("No such bitstream format: " + id);
        }
        entity.sync(format);
        format.update();
        return new FormatEntity(format);
    }

    public FormatEntity removeFormat(Context context, int id) throws AuthorizeException, SQLException {
        BitstreamFormat format = BitstreamFormat.find(context, id);
        if (format == null) {
            throw new IllegalArgumentException("No such bitstream format: " + id);
        }
        FormatEntity entity = new FormatEntity(format);
        format.delete();
        return entity;
    }

    public List<EntityRef> getReferences(int id, String sourceType, String targetType) throws SQLException {
        List<EntityRef> refList = new ArrayList<>();
        Context ctx = new Context();
        switch (sourceType) {
            case "schema" : getFields(refList, id, ctx); break;
            default: break;
        }
        ctx.complete();
        return refList;
    }

    private void getFields(List<EntityRef> refList, int id, Context ctx) throws SQLException {
        List<MetadataField> fields = MetadataField.findAllInSchema(ctx, id);
        for (MetadataField field : fields) {
            refList.add(new EntityRef(field.getElement(), field.getFieldID(), "field"));
        }
    }
}
