/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.admin;

import java.io.FileOutputStream;
import java.io.IOException;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;

import org.dspace.content.MetadataField;
import org.dspace.content.MetadataSchema;
import org.dspace.core.Context;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

/**
 * @author Graham Triggs
 *
 * This class creates an xml document as passed in the arguments and
 * from the metadata schemas for the repository.
 * 
 * The form of the XML is as follows
 * 
 * <metadata-schemas>
 *   <schema>
 *     <name>dc</name>
 *     <namespace>http://dublincore.org/documents/dcmi-terms/</namespace>
 *   </schema>
 * </metadata-schemas>
 */
public class MetadataExporter {
	// output file 
	@Option(name="-f", usage="output xml file for registry", required=true)
	private String file;
	// schema
	@Option(name="-s", usage="the name of the schema to export")
	private String schema;
	
	// constructor
	private MetadataExporter() {}
	
    /**
     * @param args
     * @throws SAXException 
     * @throws IOException 
     * @throws SQLException 
     */
    public static void main(String[] args)
        throws SQLException, IOException, XMLStreamException {
        MetadataExporter me = new MetadataExporter();
        CmdLineParser parser = new CmdLineParser(me);
        try {
        	parser.parseArgument(args);
        	saveRegistry(me.file, me.schema);
        	System.exit(0);
        } catch (CmdLineException clE) {
        	System.err.println(clE.getMessage());
        	parser.printUsage(System.err);
        }
        System.exit(1);
    }

    public static void saveRegistry(String file, String schema) 
        throws SQLException, IOException, XMLStreamException {
        // create a context
        Context context = new Context(Context.READ_ONLY);
        context.turnOffAuthorisationSystem();

        XMLOutputFactory factory = XMLOutputFactory.newInstance();
        XMLStreamWriter writer = factory.createXMLStreamWriter(new FileOutputStream(file), "UTF-8");
        writer.writeStartDocument("UTF-8", "1.0");
        writer.writeStartElement("dspace-dc-types");
        
        // Save the schema definition(s)
        saveSchema(context, writer, schema);

        List<MetadataField> mdFields = null;
        // If a single schema has been specified
        if (schema != null && !"".equals(schema)) {
            // Get the id of that schema
            MetadataSchema mdSchema = MetadataSchema.find(context, schema);
            checkNotNull(mdSchema, "no schema to export");
            // Get the metadata fields only for the specified schema
            mdFields = MetadataField.findAllInSchema(context, mdSchema.getSchemaID());
        } else {
            // Get the metadata fields for all the schemas
            mdFields = MetadataField.findAll(context);
        }
        
        // Output the metadata fields
        for (MetadataField mdField : mdFields) {
            saveType(context, writer, mdField);
        }

        writer.writeEndDocument();
        writer.close();
        
        // abort the context, as we shouldn't have changed it!!
        context.abort();
    }
    
    /**
     * Serialize the schema registry. If the parameter 'schema' is null or empty, save all schemas
     * @param context
     * @param xmlSerializer
     * @param schema
     * @throws SQLException
     * @throws SAXException
     */
    public static void saveSchema(Context context, XMLStreamWriter writer, String schema) 
           throws SQLException, XMLStreamException {
        if (schema != null && !"".equals(schema)) {
            // Find a single named schema
            MetadataSchema mdSchema = MetadataSchema.find(context, schema);
            saveSchema(writer, mdSchema);
        } else {
            // Find all schemas
            List<MetadataSchema> mdSchemas = MetadataSchema.findAll(context);     
            for (MetadataSchema mdSchema : mdSchemas){
                saveSchema(writer, mdSchema);
            }
        }
    }
    
    /**
     * Serialize a single schema (namespace) registry entry
     * 
     * @param xmlSerializer
     * @param mdSchema
     * @throws SAXException
     */
    private static void saveSchema(XMLStreamWriter writer, MetadataSchema mdSchema) 
        throws XMLStreamException  {
        // If we haven't got a schema, it's an error
        checkNotNull(mdSchema, "no schema to export");
        
        String name      = mdSchema.getName();
        String namespace = mdSchema.getNamespace();
        
        if (name == null || "".equals(name))  {
            System.out.println("name is null, skipping");
            return;
        }

        if (namespace == null || "".equals(namespace)) {
            System.out.println("namespace is null, skipping");
            return;
        }

        // Output the parent tag
        writer.writeStartElement("dc-schema");
        
        // Output the schema name
        writeElement(writer, "name", name);
       
        // Output the schema namespace
        writeElement(writer, "namespace", namespace);

        // close dc-schema
        writer.writeEndElement();
    }
    
    /**
     * Serialize a single metadata field registry entry to xml
     * 
     * @param context
     * @param xmlSerializer
     * @param mdField
     * @throws SAXException
     * @throws RegistryExportException
     * @throws SQLException
     * @throws IOException 
     */
    private static void saveType(Context context, XMLStreamWriter writer, MetadataField mdField)
        throws SQLException, IOException, XMLStreamException  {
        // If we haven't been given a field, it's an error
        checkNotNull(mdField, "no field to export");
        
        // Get the data from the metadata field
        String schemaName = getSchemaName(context, mdField);
        String element = mdField.getElement();
        String qualifier = mdField.getQualifier();
        String scopeNote = mdField.getScopeNote();

        // We must have a schema and element
        checkArgument(schemaName != null && element != null, "incomplete field information");

        // Output the parent tag
        writer.writeStartElement("dc-type");

        // Output the schema name
        writeElement(writer, "schema", schemaName);

        // Output the element
        writeElement(writer, "element", element);

        // Output the qualifier, if present
        if (qualifier != null) {
            writeElement(writer, "qualifier", qualifier);
        } else {
            writer.writeComment("unqualified");
        }
        
        // Output the scope note, if present
        if (scopeNote != null) {
            writeElement(writer, "scope_note", scopeNote);
        } else {
            writer.writeComment("no scope note");
        }
        
        // close dc-type
        writer.writeEndElement();
    }

    private static void writeElement(XMLStreamWriter writer, String name, String value) throws XMLStreamException {
        writer.writeStartElement(name);
        writer.writeCharacters(value);
        writer.writeEndElement();
    }
    
    /**
     * Helper method to retrieve a schema name for the field.
     * Caches the name after looking up the id.
     */
    static Map<Integer, String> schemaMap = new HashMap<>();
    private static String getSchemaName(Context context, MetadataField mdField) throws SQLException {
        // Get name from cache
        String name = schemaMap.get(Integer.valueOf(mdField.getSchemaID()));

        if (name == null) {
            // Name not retrieved before, so get the schema now
            MetadataSchema mdSchema = MetadataSchema.find(context, mdField.getSchemaID());
            checkNotNull(mdSchema, "Can't get schema name for field");
            name = mdSchema.getName();
            schemaMap.put(Integer.valueOf(mdSchema.getSchemaID()), name);
        }
        return name;
    }
}
