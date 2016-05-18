package com.sixphere.webscript;

import java.util.HashMap;
import java.util.Map;

import org.alfresco.service.cmr.repository.NodeRef;
import org.springframework.extensions.webscripts.DeclarativeWebScript;
import org.springframework.extensions.webscripts.Status;
import org.springframework.extensions.webscripts.WebScriptRequest;

import com.sixphere.docx4j.Docx4jHelper;

public class ExportMetadataWebScript extends DeclarativeWebScript {

	private Docx4jHelper docx4jHelper;

	@Override
	protected Map<String, Object> executeImpl(WebScriptRequest req, Status status) {
		Map<String, Object> model = new HashMap<String, Object>();
		try {
			NodeRef nodeRef, templateRef;
			String name;
			if (req.getParameter("nodeRef") != null) {
				nodeRef = new NodeRef(req.getParameter("nodeRef"));
			} else {
				throw new Exception("Parameter nodeRef is mandatory.");
			}

			if (req.getParameter("templateRef") != null) {
				templateRef = new NodeRef(req.getParameter("templateRef"));
			} else {
				throw new Exception("Parameter templateRef is mandatory.");
			}

			if (req.getParameter("name") != null) {
				name = req.getParameter("name");
			} else {
				name = docx4jHelper.getDefaultName(nodeRef, templateRef);
			}

			NodeRef data = docx4jHelper.exportMetadata(templateRef, nodeRef, name);

			model.put("result", "ok");
			model.put("data", data);
		} catch (Exception ex) {
			model.put("result", "error");
			model.put("data", "There seems to be a problem. " + ex.getMessage());
			ex.printStackTrace();
		}
		return model;
	}

	public void setDocx4jHelper(Docx4jHelper docx4jHelper) {
		this.docx4jHelper = docx4jHelper;
	}

}
