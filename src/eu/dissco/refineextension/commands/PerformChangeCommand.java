
package eu.dissco.refineextension.commands;

import com.google.refine.model.AbstractOperation;
import com.google.refine.model.Project;
import com.google.refine.browsing.EngineConfig;
import com.google.refine.commands.EngineDependentCommand;

import javax.servlet.http.HttpServletRequest;

import eu.dissco.refineextension.operations.PerformChangeOperation;

public class PerformChangeCommand extends EngineDependentCommand {

	@Override
	protected AbstractOperation createOperation(Project project, HttpServletRequest request, EngineConfig engineConfig)
			throws Exception {

		String changeToPerform = request.getParameter("changeToPerform");
		int cellIndex = Integer.valueOf(request.getParameter("cellIndex"));
		return new PerformChangeOperation(engineConfig, project, changeToPerform, cellIndex);
	}
}
