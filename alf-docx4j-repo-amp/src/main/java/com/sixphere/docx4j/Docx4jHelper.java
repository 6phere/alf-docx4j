package com.sixphere.docx4j;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;

import org.alfresco.model.ContentModel;
import org.alfresco.service.ServiceRegistry;
import org.alfresco.service.cmr.repository.ContentData;
import org.alfresco.service.cmr.repository.ContentIOException;
import org.alfresco.service.cmr.repository.ContentReader;
import org.alfresco.service.cmr.repository.ContentWriter;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.cmr.repository.StoreRef;
import org.alfresco.service.cmr.search.ResultSet;
import org.alfresco.service.cmr.search.SearchParameters;
import org.alfresco.service.namespace.QName;
import org.docx4j.openpackaging.exceptions.Docx4JException;
import org.docx4j.openpackaging.packages.SpreadsheetMLPackage;
import org.docx4j.openpackaging.packages.WordprocessingMLPackage;
import org.docx4j.openpackaging.parts.PartName;
import org.docx4j.openpackaging.parts.SpreadsheetML.JaxbSmlPart;
import org.docx4j.wml.ContentAccessor;
import org.docx4j.wml.Text;

/**
 * Helper class with docx4j functionalities
 * 
 * @author SIXPHERE Technologies
 * @version 1.0
 */
public class Docx4jHelper {

	private ServiceRegistry serviceRegistry;

	/**
	 * Get the list of objects of a docx4j document depending of the type to
	 * search
	 * 
	 * @param document.
	 *            Docx4j document to get the objects
	 * @param toSearch.
	 *            Class to search inside the document
	 * @return
	 */
	private static List<Object> getAllElementFromObject(Object document, Class<?> toSearch) {
		List<Object> result = new ArrayList<Object>();
		if (document instanceof JAXBElement)
			document = ((JAXBElement<?>) document).getValue();

		if (document.getClass().equals(toSearch))
			result.add(document);
		else if (document instanceof ContentAccessor) {
			List<?> children = ((ContentAccessor) document).getContent();
			for (Object child : children) {
				result.addAll(getAllElementFromObject(child, toSearch));
			}

		}
		return result;
	}

	/**
	 * Create an copy instance of the template and map the metadata of the node
	 * into it.
	 * 
	 * @param templateRef.
	 *            NodeRef of the template
	 * @param nodeRef.
	 *            NodeRef of the node
	 * @param name.
	 *            Name of the instance
	 * @return NodeRef of the instance
	 * @throws Exception
	 */
	public NodeRef exportMetadata(NodeRef templateRef, NodeRef nodeRef, String name) throws Exception {
		NodeRef instance = getTemplateInstance(templateRef,
				this.serviceRegistry.getNodeService().getPrimaryParent(nodeRef).getParentRef(), name);

		String templateExtension = getExtension(templateRef);

		switch (templateExtension) {
		case "docx":
			printWordDocumentByMapping(instance, getMetadata(nodeRef, true));
			return instance;
		case "xlsx":
			printSpreadSheetByMapping(instance, getMetadata(nodeRef, false));
			return instance;
		default:
			throw new Exception("Template mimetype " + templateExtension + " is not supported.");
		}
	}

	/**
	 * Get default name of a new instance of the template.
	 * 
	 * @param nodeRef.
	 *            NodeRef to get its name
	 * @param templateRef.
	 *            NodeRef of the template
	 * @return
	 */
	public String getDefaultName(NodeRef nodeRef, NodeRef templateRef) {
		NodeService nodeService = serviceRegistry.getNodeService();
		String nodeName = nodeService.getProperty(nodeRef, ContentModel.PROP_NAME).toString();
		String nodeExtension = "." + getExtension(nodeRef);
		String templateExtension = "." + getExtension(templateRef);
		return nodeName.replace(nodeExtension, "") + "-" + (new Date()).toString().replace(":", "-")
				+ templateExtension;
	}

	/**
	 * Get NodeRef of default template, depending of type of the node.
	 * 
	 * @param nodeRef.
	 *            NodeRef to get its type
	 * @return
	 * @throws Exception 
	 */
	public NodeRef getDefaultTemplateRef(NodeRef nodeRef) throws Exception {
		String typeName = serviceRegistry.getNodeService().getType(nodeRef).getLocalName();
		String defaultTemplateName = typeName + "-template";

		String query = "SELECT cm.cmis:objectId FROM cmis:document as cm WHERE CONTAINS(cm,'cmis:name:" + defaultTemplateName + "')";
		SearchParameters searchParameters = new SearchParameters();
		searchParameters.addStore(StoreRef.STORE_REF_WORKSPACE_SPACESSTORE);
		searchParameters.setQuery(query);
		searchParameters.setLanguage("cmis-alfresco");
		ResultSet templates = serviceRegistry.getSearchService().query(searchParameters);

		if (templates.length() > 0) {
			return templates.getNodeRef(0);
		} else {
			throw new Exception("No templates found named as " + defaultTemplateName);
		}
	}

	/**
	 * Get the extension of the node depends on its mimetype.
	 * 
	 * @param nodeRef.
	 *            NodeRef of the node
	 * @return
	 */
	public String getExtension(NodeRef nodeRef) {
		ContentData contentData = (ContentData) this.serviceRegistry.getNodeService().getProperty(nodeRef,
				ContentModel.PROP_CONTENT);
		return this.serviceRegistry.getMimetypeService().getExtension(contentData.getMimetype());
	}

	/**
	 * Get metadata properties of the node.
	 * 
	 * @param nodeRef.
	 *            NodeRef to get its metadata
	 * @param includeBruckets.
	 *            Include ${} into properties names
	 * @return
	 */
	private Map<String, String> getMetadata(NodeRef nodeRef, Boolean includeBruckets) {
		HashMap<String, String> mapping = new HashMap<String, String>();
		NodeService nodeService = serviceRegistry.getNodeService();
		Map<QName, Serializable> properties = nodeService.getProperties(nodeRef);
		for (QName property : properties.keySet()) {
			String variableName = includeBruckets ? "${" + property.getLocalName() + "}" : property.getLocalName();
			String value = properties.get(property) == null ? "" : properties.get(property).toString();
			mapping.put(variableName, value);
		}
		return mapping;
	}

	/**
	 * Get NodeRef of a copy instance of the templateRef, child of the
	 * targetRef.
	 * 
	 * @param templateRef.
	 *            NodeRef of the template node
	 * @param targetRef.
	 *            NodeRef of the parent target of the instance
	 * @param name.
	 *            Name of the instance @return. NodeRef of the instance
	 */
	public NodeRef getTemplateInstance(NodeRef templateRef, NodeRef targetRef, String name) {
		NodeRef instance = this.serviceRegistry.getCopyService().copy(templateRef, targetRef,
				org.alfresco.model.ContentModel.ASSOC_CONTAINS, org.alfresco.model.ContentModel.ASSOC_ORIGINAL);
		this.serviceRegistry.getNodeService().setProperty(instance, org.alfresco.model.ContentModel.PROP_NAME, name);
		return instance;
	}

	/**
	 * Substitute mapping variables into the xlsx4j document.
	 * 
	 * @param nodeRef.
	 *            NodeRef of the xlsx4j document
	 * @param mappings.
	 *            Map of name-value of all variables to substitute
	 * @throws Docx4JException
	 * @throws ContentIOException
	 * @throws JAXBException
	 */
	@SuppressWarnings({ "rawtypes", "unchecked" })
	public void printSpreadSheetByMapping(NodeRef nodeRef, Map<String, String> mapping)
			throws ContentIOException, Docx4JException, JAXBException {
		ContentReader reader = this.serviceRegistry.getContentService().getReader(nodeRef, ContentModel.PROP_CONTENT);
		SpreadsheetMLPackage spread = (SpreadsheetMLPackage) SpreadsheetMLPackage.load(reader.getContentInputStream());

		JaxbSmlPart wsVariables = (JaxbSmlPart) spread.getParts().get(new PartName("/xl/sharedStrings.xml"));

		wsVariables.variableReplace((HashMap<String, String>) mapping);

		ContentWriter writer = this.serviceRegistry.getContentService().getWriter(nodeRef, ContentModel.PROP_CONTENT,
				true);
		spread.save(writer.getContentOutputStream());
	}

	/**
	 * Substitute mapping variables into the docx4j document.
	 * 
	 * @param nodeRef.
	 *            NodeRef of the docx4j document
	 * @param mappings.
	 *            Map of name-value of all variables to substitute
	 * @throws Docx4JException
	 * @throws ContentIOException
	 */
	@SuppressWarnings("deprecation")
	public void printWordDocumentByMapping(NodeRef nodeRef, Map<String, String> mapping)
			throws ContentIOException, Docx4JException {
		ContentReader reader = this.serviceRegistry.getContentService().getReader(nodeRef, ContentModel.PROP_CONTENT);

		WordprocessingMLPackage document = WordprocessingMLPackage.load(reader.getContentInputStream());
		List<Object> texts = getAllElementFromObject(document.getMainDocumentPart(), Text.class);
		texts.addAll(getAllElementFromObject(document.getHeaderFooterPolicy().getDefaultHeader(), Text.class));
		replacePlaceholder(texts, mapping);

		ContentWriter writer = this.serviceRegistry.getContentService().getWriter(nodeRef, ContentModel.PROP_CONTENT,
				true);
		document.save(writer.getContentOutputStream());
	}

	/**
	 * Replace all texts of a docx4j document with a mapped variable
	 * 
	 * @param texts.
	 *            Texts of the document
	 * @param mapping.
	 *            Map of name-value of all variables to substitute
	 */
	private void replacePlaceholder(List<Object> texts, Map<String, String> mapping) {
		for (Object text : texts) {
			Text textElement = (Text) text;
			if (textElement.getValue().startsWith("${")) {
				if (mapping.containsKey(textElement.getValue())) {
					textElement.setValue(mapping.get(textElement.getValue()));
				} else {
					textElement.setValue("");
				}
			}
		}
	}

	public void setServiceRegistry(ServiceRegistry serviceRegistry) {
		this.serviceRegistry = serviceRegistry;
	}
}
