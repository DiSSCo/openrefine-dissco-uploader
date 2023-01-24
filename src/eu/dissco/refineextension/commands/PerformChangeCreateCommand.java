
package eu.dissco.refineextension.commands;

import com.google.refine.model.AbstractOperation;
import com.google.refine.model.Project;
import com.google.refine.browsing.EngineConfig;
import com.google.refine.commands.EngineDependentCommand;

import javax.servlet.http.HttpServletRequest;

import eu.dissco.refineextension.operations.PerformChangeCreateOperation;

public class PerformChangeCreateCommand extends EngineDependentCommand {

	@Override
	protected AbstractOperation createOperation(Project project, HttpServletRequest request, EngineConfig engineConfig)
			throws Exception {

		String changeToPerform = request.getParameter("changeToPerform");
		String newColumnName = request.getParameter("newColumnName");
		int columnInsertIndex = Integer.valueOf(request.getParameter("columnInsertIndex"));
		return new PerformChangeCreateOperation(engineConfig, project, changeToPerform, newColumnName, columnInsertIndex);
	}
}


