package com.sixphere.action;

import java.util.Date;
import java.util.List;

import org.alfresco.repo.action.ActionImpl;
import org.alfresco.repo.action.executer.ActionExecuterAbstractBase;
import org.alfresco.service.cmr.action.Action;
import org.alfresco.service.cmr.action.ActionStatus;
import org.alfresco.service.cmr.action.ParameterDefinition;
import org.alfresco.service.cmr.repository.NodeRef;

import com.sixphere.docx4j.Docx4jHelper;

public class ExportMetadata extends ActionExecuterAbstractBase {

	private Docx4jHelper docx4jHelper;

	public void setDocx4jHelper(Docx4jHelper docx4jHelper) {
		this.docx4jHelper = docx4jHelper;
	}

	@Override
	protected void addParameterDefinitions(List<ParameterDefinition> paramList) {

	}

	@Override
	protected void executeImpl(Action action, NodeRef actionedUponNodeRef) {
		try {
			NodeRef templateRef = docx4jHelper.getDefaultTemplateRef(actionedUponNodeRef);
			docx4jHelper.exportMetadata(templateRef, actionedUponNodeRef,
					docx4jHelper.getDefaultName(actionedUponNodeRef, templateRef));
		} catch (Exception e) {
			e.printStackTrace();
			((ActionImpl)action).setExecutionEndDate(new Date());
			((ActionImpl)action).setExecutionStatus(ActionStatus.Failed);
			((ActionImpl)action).setExecutionFailureMessage("There seems to be a problem. " + e.getMessage());
			throw new RuntimeException(e);
		}
	}
}
