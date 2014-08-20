/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.administer;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.Stack;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.dspace.authorize.AuthorizeException;
import org.dspace.content.BitstreamFormat;
import org.dspace.content.MetadataField;
import org.dspace.content.MetadataSchema;
import org.dspace.content.NonUniqueMetadataException;
import org.dspace.core.Context;
import org.dspace.core.LogManager;
import org.dspace.curate.TaskResolver;
import org.dspace.mxres.MetadataSpec;
import org.dspace.mxres.MetadataSpecBuilder;
import org.dspace.mxres.MDFieldSpec;
import org.dspace.mxres.MetadataView;
import org.dspace.mxres.MetadataViewBuilder;
import org.dspace.mxres.MDFieldDisplay;
import org.dspace.mxres.ResourceMap;
import org.dspace.pack.PackingSpec;
import org.dspace.pack.PackingSpecBuilder;

import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/**
 * Loads various mds registries from XML files into the database.
 * Current registries include those for: bitstream formats, metdata types,
 * curation tasks, and system commands.
 * Intended for use as a command-line tool, or programmically from static methods.
 * <P>
 * Example usage:
 * <P>
 * <code>RegistryLoader bitstream-formats.xml</code>
 * <P>
 * @author Robert Tansley
 * @author richardrodgers
 */
public class RegistryLoader {
    // types Loader understands
    enum LoaderType {
        BITSTREAM_FORMATS("dspace-bitstream-types"),
        METADATA_TYPES("dspace-dc-types"),
        CURATION_TASKS("dspace-curation-tasks"),
        COMMANDS("dspace-commands"),
        METADATA_SPECS("dspace-metadata-specs"),
        METADATA_VIEWS("dspace-metadata-views"),
        PACKING_SPECS("dspace-packing-specs"),
        REGISTRY_REFS("dspace-registry-refs");

        private final String element;

        LoaderType(String element) {
            this.element = element;
        }

        public String getElement() {
            return element;
        }
    }

    /** log4j category */
    private static Logger log = LoggerFactory.getLogger(RegistryLoader.class);
    
    private static XPathFactory factory;
    private static XPath xpath;
    
    /**
     * For invoking via the command line
     * 
     * @param args
     *            command-line arguments
     */
    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("Missing registry load file name");
            System.exit(1);
        }
        try (Context context = new Context()) {
            // Can't update registries anonymously, so we need to turn off
            // authorisation
            context.turnOffAuthorisationSystem();
            loadRegistryFile(context, args[0]);
            context.complete();
            System.exit(0);
        } catch (Exception e) {
            log.error("error_loading_registries: " +  e.getMessage());
        }
        System.exit(1);
    }
    
    /**
     * Loads an XML file into the registry. 
     * This will fail if XML document is of an unknown type.
     * 
     * @param context the DSpace context
     * @param filename the full path name of XML file to load
     */
    public static void loadRegistryFile(Context context, String filename)
            throws SQLException, IOException, ParserConfigurationException,
            SAXException, TransformerException, AuthorizeException,
            NonUniqueMetadataException {

        loadRegistryDocument(context, loadXML(filename));
    }
    
    /**
     * Loads an XML document from a URI into the registry. 
     * This will fail if XML document is of an unknown type.
     * 
     * @param context the DSpace context
     * @param uri a resovable reference to the XML document to load
     */
    public static void loadRegistryUri(Context context, String uri)
            throws SQLException, IOException, ParserConfigurationException,
            SAXException, TransformerException, AuthorizeException,
            NonUniqueMetadataException {
    
        loadRegistryDocument(context, loadXMLUri(uri));
    }
    
    /**
     * Loads an XML document into the registry. 
     * This will fail if XML document is of an unknown type.
     * 
     * @param context the DSpace context
     * @param document the XML document to load
     */
    public static void loadRegistryDocument(Context context, Document document)
            throws SQLException, IOException, SAXException, TransformerException,
            AuthorizeException, NonUniqueMetadataException {
    
        String docType = document.getDocumentElement().getTagName();
        boolean canProcess = false;
        for (LoaderType type : LoaderType.values()) {
            if (type.getElement().equals(docType)) {
                // can process it
                canProcess = true;
                if (type.equals(LoaderType.BITSTREAM_FORMATS)) {
                    loadBitstreamFormats(context, document);
                } else if (type.equals(LoaderType.METADATA_TYPES)) {
                    loadDublinCoreTypes(context, document);
                }  else if (type.equals(LoaderType.CURATION_TASKS)) {
                    loadCurationTasks(context, document);
                }  else if (type.equals(LoaderType.COMMANDS)) {
                    loadCommands(context, document);
                }  else if (type.equals(LoaderType.METADATA_SPECS)) {
                    loadMetadataSpecs(context, document);
                }  else if (type.equals(LoaderType.METADATA_VIEWS)) {
                    loadMetadataViews(context, document);
                }  else if (type.equals(LoaderType.PACKING_SPECS)) {
                    loadPackingSpecs(context, document);
                }
            }
        }
        if (! canProcess) {
            throw new IOException("Unknown document type - cannot load");
        }
    }
    
    /**
     * Load Bitstream Format metadata
     * 
     * @param context
     *            DSpace context object
     * @param filename
     *            the filename of the XML file to load
     */
    public static void loadBitstreamFormats(Context context, String filename)
            throws SQLException, IOException, ParserConfigurationException,
            SAXException, TransformerException, AuthorizeException {
    
        loadBitstreamFormats(context, loadXML(filename));
    }

    /**
     * Load Bitstream Format metadata
     * 
     * @param context
     *            DSpace context object
     * @param document
     *            the XML document to load
     */
    public static void loadBitstreamFormats(Context context, Document document)
            throws SQLException, IOException, SAXException,
            TransformerException, AuthorizeException {

        // Get the nodes corresponding to formats
        String path = LoaderType.BITSTREAM_FORMATS.getElement() + "/bitstream-type";
        NodeList typeNodes = xPathFind(document, path);

        // Add each one as a new format to the registry
        for (int i = 0; i < typeNodes.getLength(); i++) {
            Node n = typeNodes.item(i);
            loadFormat(context, n);
        }

        log.info(LogManager.getHeader(context, "load_bitstream_formats",
                "number_loaded=" + typeNodes.getLength()));
    }

    /**
     * Process a node in the bitstream format registry XML file. The node must
     * be a "bitstream-type" node
     * 
     * @param context
     *            DSpace context object
     * @param node
     *            the node in the DOM tree
     */
    private static void loadFormat(Context context, Node node)
            throws SQLException, IOException, TransformerException,
            AuthorizeException {
        // Get the values
        String mimeType = getElementData(node, "mimetype");
        String shortDesc = getElementData(node, "short_description");
        String desc = getElementData(node, "description");

        String supportLevelString = getElementData(node, "support_level");
        int supportLevel = Integer.parseInt(supportLevelString);

        String internalString = getElementData(node, "internal");
        boolean internal = Boolean.valueOf(internalString).booleanValue();

        String[] extensions = getRepeatedElementData(node, "extension");

        // Create the format object
        BitstreamFormat format = BitstreamFormat.create(context);

        // Fill it out with the values
        format.setMIMEType(mimeType);
        format.setShortDescription(shortDesc);
        format.setDescription(desc);
        format.setSupportLevel(supportLevel);
        format.setInternal(internal);
        format.setExtensions(extensions);

        // Write to database
        format.update();
    }
    
    /**
     * Load Dublin Core types
     * 
     * @param context
     *            DSpace context object
     * @param filename
     *            the filename of the XML file to load
     * @throws NonUniqueMetadataException
     */
    public static void loadDublinCoreTypes(Context context, String filename)
            throws SQLException, IOException, ParserConfigurationException,
            SAXException, TransformerException, AuthorizeException,
            NonUniqueMetadataException {
        loadDublinCoreTypes(context, loadXML(filename));
    }

    /**
     * Load Dublin Core types
     * 
     * @param context
     *            DSpace context object
     * @param document
     *            the XML document to load
     * @throws NonUniqueMetadataException
     */
    public static void loadDublinCoreTypes(Context context, Document document)
            throws SQLException, IOException, SAXException,
            TransformerException, AuthorizeException,
            NonUniqueMetadataException {

        // Get the nodes corresponding to schemas
        String path = LoaderType.METADATA_TYPES.getElement() + "/dc-schema";
        NodeList schemaNodes = xPathFind(document, path);

        // Add each schema
        for (int i = 0; i < schemaNodes.getLength(); i++)  {
            Node n = schemaNodes.item(i);
            loadMDSchema(context, n);
        }
        
        // Get the nodes corresponding to fields
        NodeList typeNodes = xPathFind(document, "/dspace-dc-types/dc-type");

        // Add each one as a new field to the schema
        for (int i = 0; i < typeNodes.getLength(); i++) {
            Node n = typeNodes.item(i);
            loadDCType(context, n);
        }

        log.info(LogManager.getHeader(context, "load_dublin_core_types",
                "number_loaded=" + typeNodes.getLength()));
    }

    /**
     * Load Dublin Core Schemas
     * 
     * @param context
     * @param node
     */
    private static void loadMDSchema(Context context, Node node) 
            throws TransformerException, SQLException, AuthorizeException, 
            NonUniqueMetadataException {
        // Get the values
        String shortname = getElementData(node, "name");
        String namespace = getElementData(node, "namespace");

        // Check if the schema exists already
        MetadataSchema schema = MetadataSchema.find(context, shortname);
        if (schema == null) {
            // If not create it.
            schema = new MetadataSchema();
            schema.setNamespace(namespace);
            schema.setName(shortname);
            schema.create(context);
        }
    }
    
    /**
     * Process a node in the bitstream format registry XML file. The node must
     * be a "bitstream-type" node
     * 
     * @param context
     *            DSpace context object
     * @param node
     *            the node in the DOM tree
     * @throws NonUniqueMetadataException
     */
    private static void loadDCType(Context context, Node node)
            throws SQLException, IOException, TransformerException,
            AuthorizeException, NonUniqueMetadataException {
        // Get the values
        String schema = getElementData(node, "schema");
        String element = getElementData(node, "element");
        String qualifier = getElementData(node, "qualifier");
        String scopeNote = getElementData(node, "scope_note");

        // If the schema is not provided default to DC
        if (schema == null) {
            schema = MetadataSchema.DC_SCHEMA;
        }

        // Find the matching schema object
        MetadataSchema schemaObj = MetadataSchema.find(context, schema);
        
        MetadataField field = new MetadataField();
        field.setSchemaID(schemaObj.getSchemaID());
        field.setElement(element);
        field.setQualifier(qualifier);
        field.setScopeNote(scopeNote);
        field.create(context);
    }

    /**
     * Load Metadata Specs
     * 
     * @param context
     *            DSpace context object
     * @param filename
     *            the filename of the XML file to load
     * @throws NonUniqueMetadataException
     */
    public static void loadMetadataSpecs(Context context, String filename)
            throws SQLException, IOException, ParserConfigurationException,
            SAXException, TransformerException, AuthorizeException,
            NonUniqueMetadataException {
        loadMetadataSpecs(context, loadXML(filename));
    }

    /**
     * Load Metadata Specs
     * 
     * @param context
     *            DSpace context object
     * @param document
     *            the XML document to load
     * @throws NonUniqueMetadataException
     */
    public static void loadMetadataSpecs(Context context, Document document)
            throws SQLException, IOException, SAXException,
            TransformerException, AuthorizeException,
            NonUniqueMetadataException {

        // Get the nodes corresponding to views
        String path = LoaderType.METADATA_SPECS.getElement() + "/metadata-spec";
        NodeList specNodes = xPathFind(document, path);
        Map<String, SpecLoader> specMap = new HashMap<>();

        // Add each spec
        for (int i = 0; i < specNodes.getLength(); i++)  {
            Node n = specNodes.item(i);
            SpecLoader sld = loadMDSpec(context, n);
            specMap.put(sld.spec.getDescription(), sld);
        }

        log.info(LogManager.getHeader(context, "load_metadata_specs",
                "number_specs_loaded=" + specNodes.getLength()));
        
        // Get the nodes corresponding to specifications
        String spath = LoaderType.METADATA_SPECS.getElement() + "/specification";
        NodeList spNodes = xPathFind(document, spath);

        // Add each one to indicated spec
        for (int i = 0; i < spNodes.getLength(); i++) {
            Node n = spNodes.item(i);
            loadSpecification(context, n, specMap);
        }

        ResourceMap<MetadataSpec> resMap = new ResourceMap(MetadataSpec.class, context);
        for (String key : specMap.keySet()) {
            SpecLoader sl = specMap.get(key);
            MetadataSpec spec = sl.spec;
            // map the resource
            resMap.addResource(spec.getDescription(), String.valueOf(spec.getID()));
            // add the rule
            resMap.addRule(sl.scope, sl.rule);
            //spec.update();
        }
        // also install a builder for this resource type
        resMap.setBuilder(MetadataSpecBuilder.class.getName());

        log.info(LogManager.getHeader(context, "load_metadata_specs",
                "number_specifications_loaded=" + spNodes.getLength()));
    }

    /**
     * Load Metadata Specs
     * 
     * @param context
     * @param node
     */
    private static SpecLoader loadMDSpec(Context context, Node node) 
            throws TransformerException, SQLException, AuthorizeException, 
            NonUniqueMetadataException {
        // Get the values
        String name = getElementData(node, "name");
        String scope = getElementData(node, "scope");
        String rule = getElementData(node, "rule");

        // Check if the schema exists already
        MetadataSpec mdspec = null; //MetadataSchema.find(context, shortname);
        SpecLoader loader = null;
        if (mdspec == null) {
            // If not create it.
            mdspec = MetadataSpec.create(context);
            mdspec.setDescription(name);
            loader = new SpecLoader();
            loader.spec = mdspec;
            loader.scope = scope;
            loader.rule = rule;
        }

        return loader;
    }

    private static class SpecLoader {
        public MetadataSpec spec;
        public String scope;
        public String rule;
    }

    /**
     * Process a node in the bitstream format registry XML file. The node must
     * be a "bitstream-type" node
     * 
     * @param context
     *            DSpace context object
     * @param node
     *            the node in the DOM tree
     * @throws NonUniqueMetadataException
     */
    private static void loadSpecification(Context context, Node node, Map<String, SpecLoader> specMap)
            throws SQLException, IOException, TransformerException,
            AuthorizeException, NonUniqueMetadataException {
        // Get the values
        String specName = getElementData(node, "spec");
        String schema = getElementData(node, "schema");
        String element = getElementData(node, "element");
        String qualifier = getElementData(node, "qualifier");
        String altname = getElementData(node, "altname");
        String label = getElementData(node, "label");
        String description = getElementData(node, "description");
        String cardinality = getElementData(node, "cardinality");
        String input = getElementData(node, "input");
        String language = getElementData(node, "language");

        // Find the matching view
        SpecLoader sl = specMap.get(specName);
        if (sl != null) {
            MetadataSpec specObj = sl.spec;
            String key = schema + "." + element;
            if (qualifier != null && qualifier.length() > 0) {
                key += "." + qualifier;
            }
            MDFieldSpec mdfs = new MDFieldSpec(key, altname, label, description, cardinality, input, false, language);
            specObj.addFieldSpec(context, mdfs);
        } else {
            log.info(LogManager.getHeader(context, "load_specification",
                "unmatched_spec=" + specName));
        }
    }

    /**
     * Load Metadata Views
     * 
     * @param context
     *            DSpace context object
     * @param filename
     *            the filename of the XML file to load
     * @throws NonUniqueMetadataException
     */
    public static void loadMetadataViews(Context context, String filename)
            throws SQLException, IOException, ParserConfigurationException,
            SAXException, TransformerException, AuthorizeException,
            NonUniqueMetadataException {
        loadMetadataViews(context, loadXML(filename));
    }

    /**
     * Load Metadata Views
     * 
     * @param context
     *            DSpace context object
     * @param document
     *            the XML document to load
     * @throws NonUniqueMetadataException
     */
    public static void loadMetadataViews(Context context, Document document)
            throws SQLException, IOException, SAXException,
            TransformerException, AuthorizeException,
            NonUniqueMetadataException {

        // Get the nodes corresponding to views
        String path = LoaderType.METADATA_VIEWS.getElement() + "/metadata-view";
        NodeList viewNodes = xPathFind(document, path);
        Map<String, ViewLoader> viewMap = new HashMap<>();

        // Add each view
        for (int i = 0; i < viewNodes.getLength(); i++)  {
            Node n = viewNodes.item(i);
            ViewLoader vld = loadMDView(context, n);
            viewMap.put(vld.view.getDescription(), vld);
        }

        log.info(LogManager.getHeader(context, "load_metadata_views",
                "number_views_loaded=" + viewNodes.getLength()));
        
        // Get the nodes corresponding to depictions
        String dpath = LoaderType.METADATA_VIEWS.getElement() + "/depiction";
        NodeList dpNodes = xPathFind(document, dpath);

        // Add each one to indicated view
        for (int i = 0; i < dpNodes.getLength(); i++) {
            Node n = dpNodes.item(i);
            loadDepiction(context, n, viewMap);
        }

        ResourceMap<MetadataView> resMap = new ResourceMap(MetadataView.class, context);
        for (String key : viewMap.keySet()) {
            ViewLoader vl = viewMap.get(key);
            MetadataView view = vl.view;
            // map the resource
            resMap.addResource(view.getDescription(), String.valueOf(view.getID()));
            // add the rule
            resMap.addRule(vl.scope, vl.rule);
            //view.update();
        }
        // also install a builder for this resource type
        resMap.setBuilder(MetadataViewBuilder.class.getName());

        log.info(LogManager.getHeader(context, "load_metadata_views",
                "number_depictions_loaded=" + dpNodes.getLength()));
    }

    /**
     * Load Metadata Views
     * 
     * @param context
     * @param node
     */
    private static ViewLoader loadMDView(Context context, Node node) 
            throws TransformerException, SQLException, AuthorizeException, 
            NonUniqueMetadataException {
        // Get the values
        String name = getElementData(node, "name");
        String scope = getElementData(node, "scope");
        String rule = getElementData(node, "rule");

        // Check if the schema exists already
        MetadataView mdview = null; //MetadataSchema.find(context, shortname);
        ViewLoader loader = null;
        if (mdview == null) {
            // If not create it.
            mdview = MetadataView.create(context);
            mdview.setDescription(name);
            loader = new ViewLoader();
            loader.view = mdview;
            loader.scope = scope;
            loader.rule = rule;
        }

        return loader;
    }

    private static class ViewLoader {
        public MetadataView view;
        public String scope;
        public String rule;
    }

     /**
     * Process a node in the bitstream format registry XML file. The node must
     * be a "bitstream-type" node
     * 
     * @param context
     *            DSpace context object
     * @param node
     *            the node in the DOM tree
     * @throws NonUniqueMetadataException
     */
    private static void loadDepiction(Context context, Node node, Map<String, ViewLoader> viewMap)
            throws SQLException, IOException, TransformerException,
            AuthorizeException, NonUniqueMetadataException {
        // Get the values
        String viewName = getElementData(node, "view");
        String schema = getElementData(node, "schema");
        String element = getElementData(node, "element");
        String qualifier = getElementData(node, "qualifier");
        String altname = getElementData(node, "altname");
        String label = getElementData(node, "label");
        String render = getElementData(node, "render");
        String wrapper = getElementData(node, "wrapper");
        String language = getElementData(node, "language");

        // Find the matching view
        ViewLoader vl = viewMap.get(viewName);
        if (vl != null) {
            MetadataView viewObj = vl.view;
            String key = schema + "." + element;
            if (qualifier != null && qualifier.length() > 0) {
                key += "." + qualifier;
            }
            MDFieldDisplay mdfd = new MDFieldDisplay(key, altname, label, render, wrapper, language);
            viewObj.addViewField(context, mdfd);
        } else {
            log.info(LogManager.getHeader(context, "load_depiction",
                "unmatched_view=" + viewName));
        }
    }

    /**
     * Load Packing Specs
     * 
     * @param context
     *            DSpace context object
     * @param filename
     *            the filename of the XML file to load
     * @throws NonUniqueMetadataException
     */
    public static void loadPackingSpecs(Context context, String filename)
            throws SQLException, IOException, ParserConfigurationException,
            SAXException, TransformerException, AuthorizeException,
            NonUniqueMetadataException {
        loadPackingSpecs(context, loadXML(filename));
    }

    /**
     * Load Packing Specs
     * 
     * @param context
     *            DSpace context object
     * @param document
     *            the XML document to load
     * @throws NonUniqueMetadataException
     */
    public static void loadPackingSpecs(Context context, Document document)
            throws SQLException, IOException, SAXException,
            TransformerException, AuthorizeException,
            NonUniqueMetadataException {

        // Get the nodes corresponding to packing specs
        String path = LoaderType.PACKING_SPECS.getElement() + "/packing-spec";
        NodeList specNodes = xPathFind(document, path);

        // Add each packing spec and wire up as 
        ResourceMap<PackingSpec> resMap = new ResourceMap(PackingSpec.class, context);
        for (int i = 0; i < specNodes.getLength(); i++)  {
            Node n = specNodes.item(i);
            PackingSpec spec = PackingSpec.create(context);
            spec.setName(getElementData(n, "name"));
            spec.setDescription(getElementData(n, "description"));
            spec.setPacker(getElementData(n, "packer"));
            spec.setFormat(getElementData(n, "format"));
            spec.setContentFilter(getElementData(n, "content-filter"));
            spec.setMetadataFilter(getElementData(n, "metadata-filter"));
            spec.setReferenceFilter(getElementData(n, "reference-filter"));
            spec.setMimeType(getElementData(n, "mimetype"));
            spec.setPackageId(getElementData(n, "package-id"));
            spec.update();
             // map the resource
            resMap.addResource(spec.getName(), String.valueOf(spec.getID()));
            // add the rule
            resMap.addRule(getElementData(n, "scope"), getElementData(n, "rule"));
        }
        
        // also install a builder for this resource type
        resMap.setBuilder(PackingSpecBuilder.class.getName());

        log.info(LogManager.getHeader(context, "load_packing_specs",
                "number_specs_loaded=" + specNodes.getLength()));
    }

    /**
     * Load DSpace curation tasks
     * 
     * @param context
     *            DSpace context object
     * @param filename
     *            the filename of the XML file to load
     * @throws NonUniqueMetadataException
     */
    public static void loadCurationTasks(Context context, String filename) 
            throws AuthorizeException, IOException, SAXException,
                   SQLException, ParserConfigurationException, TransformerException {
     
        loadCurationTasks(context, loadXML(filename));
    }

    /**
     * Load DSpace curation tasks
     * 
     * @param context
     *            DSpace context object
     * @param document
     *            the XML document to load
     * @throws NonUniqueMetadataException
     */
    public static void loadCurationTasks(Context context, Document document) 
           throws AuthorizeException, IOException, SAXException,
                  SQLException, TransformerException {

        // Get the nodes corresponding to groups and tasks
        String gPath = LoaderType.CURATION_TASKS.getElement() + "/group";
        NodeList groupNodes = xPathFind(document, gPath);
        
        // Add each one as a new group to the registry
        for (int i = 0; i < groupNodes.getLength(); i++)  {
            Node n = groupNodes.item(i);
            loadCurationGroup(context, n);
        }

        String path = LoaderType.CURATION_TASKS.getElement() + "/task";
        NodeList taskNodes = xPathFind(document, path);
        
        // Add each one as a new task or selector to the registry
        for (int i = 0; i < taskNodes.getLength(); i++)  {
            Node n = taskNodes.item(i);
            loadCurationTask(context, n);
        }
        log.info(LogManager.getHeader(context, "load_curation_tasks",
                "number_loaded=" + taskNodes.getLength()));
    }

    private static void loadCurationGroup(Context context, Node node) 
            throws AuthorizeException, SQLException, TransformerException {
        // Get the values
        String type = getElementData(node, "type");
        String name = getElementData(node, "name");
        String description = getElementData(node, "description");
        String uiAccess = getElementData(node, "ui-access"); 
        String apiAccess = getElementData(node, "api-access"); 
        
        if (TaskResolver.groupExists(context, type, name)) {
            throw new AuthorizeException("Group with name: '" + name + "' already exists");
        }
        
        TaskResolver.addGroup(context, type, name, description, "true".equals(uiAccess), "true".equals(apiAccess));
    }

    private static void loadCurationTask(Context context, Node node) 
            throws AuthorizeException, SQLException, TransformerException {
        // Get the values
        String script = null;
        String name = getElementData(node, "name");
        String description = getElementData(node, "description");
        String type = getElementData(node, "type");
        String impl = getElementData(node, "impl");
        if ("task".equals(type) && ! "java".equals(impl)) {
            // task is a script
            script = getElementData(node, "script");
        }
        String loadAddr = getElementData(node, "load-addr");
        String config = getElementData(node, "config"); 
        String version = getElementData(node, "version");
        String infoUrl = getElementData(node, "info-url"); 
        String group = getElementData(node, "group"); 
        
        if (TaskResolver.canResolveTask(context, name)) {
            throw new AuthorizeException("Task with name: '" + name + "' already exists");
        }
        
        TaskResolver.installTask(context, name, description, type, impl, loadAddr, script, config);

        if (group != null & group.length() > 0) {
            TaskResolver.addGroupMember(context, group, type, name);
        }
    }
    
    /**
     * Load DSpace commands
     * 
     * @param context
     *            DSpace context object
     * @param filename
     *            the filename of the XML file to load
     * @throws NonUniqueMetadataException
     */
    public static void loadCommands(Context context, String filename) 
            throws AuthorizeException, IOException, SAXException,
                   SQLException, ParserConfigurationException, TransformerException {
     
        loadCommands(context, loadXML(filename));
    }
    
    /**
     * Load DSpace commands
     * 
     * @param context
     *            DSpace context object
     * @param document
     *            the XML document to load
     * @throws NonUniqueMetadataException
     */
    public static void loadCommands(Context context, Document document) 
           throws AuthorizeException, IOException, SAXException,
                  SQLException, TransformerException {
        
        // Get the nodes corresponding to commands
        String path = LoaderType.COMMANDS.getElement() + "/command";
        NodeList cmdNodes = xPathFind(document, path);
        
        // Add each one as a new format to the registry
        for (int i = 0; i < cmdNodes.getLength(); i++)  {
            Node n = cmdNodes.item(i);
            loadCommand(context, n);
        }

        log.info(LogManager.getHeader(context, "load_commands",
                "number_loaded=" + cmdNodes.getLength()));
    }
    
    private static void loadCommand(Context context, Node node) 
            throws AuthorizeException, SQLException, TransformerException {
        // Get the values
        String name = getElementData(node, "name");
        String description = getElementData(node, "description");
        
        // Get the nodes corresponding to command steps
        NodeList stepNodes = xPathFind(node, "step");
        
        // Add each one as a new command to the registry
        Stack<Step> stack = new Stack<Step>();
        for (int i = 0; i < stepNodes.getLength(); i++)
        {
            Node n = stepNodes.item(i);            
            String className = getElementData(n, "class");
            StringBuilder sb = new StringBuilder();
            for (String arg : getRepeatedElementData(n, "argument")) {
                sb.append(arg).append(" ");
            }
            boolean noUserArgs = "false".equals(getAttributeData(n, "passuserargs"));
            stack.push(new Step(className, sb.toString().trim(), ! noUserArgs, i));
        }
        // OK - is there a single step only, or a succession chain?
        int successorId = -1;
        Step step = null;
        while (stack.size() > 1) {
            step = stack.pop();
            String cname = name + "-p" + step.index;
            Command cmd = loadUniqueCommand(context, cname, description, step, false, successorId);
            successorId = cmd.getID();
        }
        step = stack.pop();
        loadUniqueCommand(context, name, description, step, true, successorId);
    }
    
    private static Command loadUniqueCommand(Context context, String name, String description,
                                             Step step, boolean launchable, int successorId)
            throws AuthorizeException, SQLException {
    
        // prevent name collisions
        if (Command.findByName(context, name) != null) {
            throw new AuthorizeException("Command with name: '" + name + "' already exists");
        }
        return Command.load(context, name, description, step.className, step.arguments,
                            launchable, step.userArgs, successorId);
    }
    
    private static class Step {
        public String className;
        public String arguments;
        public boolean userArgs;
        public int index;
    
        public Step(String className, String arguments, boolean userArgs, int index) {
            this.className = className;
            this.arguments = arguments;
            this.userArgs = userArgs;
            this.index = index;
        }
    }
    
    /**
     * Load a registry load reference file
     * 
     * @param context
     *            DSpace context object
     * @param filename
     *            the filename of the XML file to load
     * @throws NonUniqueMetadataException
     */
    public static void loadReferences(Context context, String filename) 
            throws AuthorizeException, IOException, SAXException,
                   SQLException, ParserConfigurationException, TransformerException,
                   NonUniqueMetadataException {
     
        loadReferences(context, loadXML(filename));
    }
    
    /**
     * Load DSpace references
     * 
     * @param context
     *            DSpace context object
     * @param document
     *            the XML document to extract references from
     */
    public static void loadReferences(Context context, Document document) 
           throws AuthorizeException, IOException, ParserConfigurationException,
                  SAXException, SQLException, TransformerException,
                  NonUniqueMetadataException {
       
        // Get the nodes corresponding to commands
        String path = LoaderType.REGISTRY_REFS.getElement() + "/reference";
        NodeList cmdNodes = xPathFind(document, path);
        
        // Add each one as a new format to the registry
        for (int i = 0; i < cmdNodes.getLength(); i++)  {
            Node n = cmdNodes.item(i);
            loadRegistryUri(context, getElementData(n, "uri"));
        }

        log.info(LogManager.getHeader(context, "load_references",
                "number_loaded=" + cmdNodes.getLength()));
    }

    // ===================== XML Utility Methods =========================

    /**
     * Load in the XML from file.
     * 
     * @param filename
     *            the filename to load from
     * 
     * @return the DOM representation of the XML file
     */
    public static Document loadXML(String filename) throws IOException,
            ParserConfigurationException, SAXException {
        DocumentBuilder builder = DocumentBuilderFactory.newInstance()
                .newDocumentBuilder();

        return builder.parse(new File(filename));
    }
    
    /**
     * Load in the XML from a URI stream.
     * 
     * @param registryUri
     *            the URI of the document to load
     * 
     * @return the DOM representation of the XML file
     */
    public static Document loadXMLUri(String registryUri) throws IOException,
            ParserConfigurationException, SAXException {
        DocumentBuilder builder = DocumentBuilderFactory.newInstance()
                .newDocumentBuilder();

        return builder.parse(registryUri);
    }

    /**
     * Get the CDATA of a particular element. For example, if the XML document
     * contains:
     * <P>
     * <code>
     * &lt;foo&gt;&lt;mimetype&gt;application/pdf&lt;/mimetype&gt;&lt;/foo&gt;
     * </code>
     * passing this the <code>foo</code> node and <code>mimetype</code> will
     * return <code>application/pdf</code>.
     * </P>
     * Why this isn't a core part of the XML API I do not know...
     * 
     * @param parentElement
     *            the element, whose child element you want the CDATA from
     * @param childName
     *            the name of the element you want the CDATA from
     * 
     * @return the CDATA as a <code>String</code>
     */
    public static String getElementData(Node parentElement, String childName)
            throws TransformerException  {
        // Grab the child node
        Node childNode = xPathFind(parentElement, childName).item(0);

        if (childNode == null) {
            // No child node, so no values
            return null;
        }

        // Get the #text
        Node dataNode = childNode.getFirstChild();

        if (dataNode == null) {
            return null;
        }

        // Get the data
        return dataNode.getNodeValue().trim();
    }
    
    private static String getAttributeData(Node element, String attributeName) {
        NamedNodeMap map = element.getAttributes();
        Node attrNode = map.getNamedItem(attributeName);
        return (attrNode != null) ? attrNode.getNodeValue() : null;
    }

    /**
     * Get repeated CDATA for a particular element. For example, if the XML
     * document contains:
     * <P>
     * <code>
     * &lt;foo&gt;
     *   &lt;bar&gt;val1&lt;/bar&gt;
     *   &lt;bar&gt;val2&lt;/bar&gt;
     * &lt;/foo&gt;
     * </code>
     * passing this the <code>foo</code> node and <code>bar</code> will
     * return <code>val1</code> and <code>val2</code>.
     * </P>
     * Why this also isn't a core part of the XML API I do not know...
     * 
     * @param parentElement
     *            the element, whose child element you want the CDATA from
     * @param childName
     *            the name of the element you want the CDATA from
     * 
     * @return the CDATA as a <code>String</code>
     */
    public static String[] getRepeatedElementData(Node parentElement,
            String childName) throws TransformerException {
        // Grab the child node
        NodeList childNodes = xPathFind(parentElement, childName);

        String[] data = new String[childNodes.getLength()];

        for (int i = 0; i < childNodes.getLength(); i++) {
            // Get the #text node
            Node dataNode = childNodes.item(i).getFirstChild();

            // Get the data
            data[i] = dataNode.getNodeValue().trim();
        }

        return data;
    }
    
    private static NodeList xPathFind(Node element, String match) {
        if (factory == null) {
            factory = XPathFactory.newInstance();
            xpath = factory.newXPath();
        }
        try {
            XPathExpression exp = xpath.compile(match);
            return (NodeList)exp.evaluate(element, XPathConstants.NODESET);
        } catch (Exception e) {
            log.error("Error evaluating expression: '" + match + "'", e);
        }
        return null;
    }
}
