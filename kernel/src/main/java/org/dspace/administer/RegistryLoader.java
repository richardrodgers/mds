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

import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/**
 * Loads the bitstream format, Dublin Core type, or Command registries into the database.
 * Intended for use as a command-line tool, or programmically from static methods
 * <P>
 * Example usage:
 * <P>
 * <code>RegistryLoader bitstream-formats.xml</code>
 * <P>
 * @author Robert Tansley
 * @author richardrodgers
 */
public class RegistryLoader
{
	// types Loader understands
	enum LoaderType {
		BITSTREAM_FORMATS("dspace-bitstream-types"),
		METADATA_TYPES("dspace-dc-types"),
		COMMANDS("dspace-commands"),
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
    public static void main(String[] args) throws Exception
    {
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
    			}  else if (type.equals(LoaderType.COMMANDS)) {
    				loadCommands(context, document);
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
            AuthorizeException
    {
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
            NonUniqueMetadataException
    {
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
            AuthorizeException, NonUniqueMetadataException
    {
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
            ParserConfigurationException, SAXException
    {
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
            ParserConfigurationException, SAXException
    {
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
            throws TransformerException
    {
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
            String childName) throws TransformerException
    {
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
