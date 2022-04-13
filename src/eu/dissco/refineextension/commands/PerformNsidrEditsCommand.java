package eu.dissco.refineextension.commands;

import com.google.refine.model.AbstractOperation;
import com.google.refine.model.Project;


import com.google.refine.browsing.EngineConfig;
import com.google.refine.commands.EngineDependentCommand;


import javax.servlet.http.HttpServletRequest;

import eu.dissco.refineextension.operations.UploadToCordraOperation;
public class PerformNsidrEditsCommand extends EngineDependentCommand {
  
  @Override
  protected AbstractOperation createOperation(Project project, HttpServletRequest request, EngineConfig engineConfig)
      throws Exception {

    String authToken = request.getParameter("token");
    /*if (authToken == null) {
      respond(response,
          "{ \"code\" : \"error\", \"message\" : \"No authentication token sent\" }");
      return;
    }*/
    
  return new UploadToCordraOperation(engineConfig, project, authToken);
  }
}
