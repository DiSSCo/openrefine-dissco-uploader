
package eu.dissco.refineextension.commands;

import com.fasterxml.jackson.annotation.JsonProperty;

import com.google.refine.commands.Command;
import com.google.refine.model.Project;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.ServletException;


import eu.dissco.refineextension.model.SyncState;
import eu.dissco.refineextension.schema.DisscoSchema;

public class SaveConnectionCommand extends Command {

  private String overlayModelKey = "disscoSchema";

  @Override
  public void doPost(HttpServletRequest request, HttpServletResponse response)
      throws ServletException, IOException {
    // To-Do: make csrf token check
    try {
      Project project = getProject(request);
      String[] requiredKeys = {"cordraServerUrl", "authServerUrl", "authRealm", "authClientId"};
      for (int i = 0; i < requiredKeys.length; i++) {
        String key = requiredKeys[i];
        String value = request.getParameter(key);
        if (value == null || value.isEmpty()) {
          respond(response,
              "{ \"code\" : \"error\", \"message\" : \"Missing required value:" + key + "\" }");
          return;
        }
      }

      DisscoSchema schema = (DisscoSchema) project.overlayModels.get(overlayModelKey);
      if (schema == null) {
        schema = new DisscoSchema();
      }
      schema.setCordraServerUrl(request.getParameter("cordraServerUrl"));
      schema.setAuthServerUrl(request.getParameter("authServerUrl"));
      schema.setAuthRealm(request.getParameter("authRealm"));
      schema.setAuthClientId(request.getParameter("authClientId"));
      project.overlayModels.put(overlayModelKey, schema);

      response.setCharacterEncoding("UTF-8");
      response.setHeader("Content-Type", "application/json");
      respondJSON(response, new SaveSchemaResult("ok", "schema saved"));
    } catch (Exception e) {
      respondException(response, e);
    }
  }

  protected static class SaveSchemaResult {

    @JsonProperty("code")
    protected String code;
    @JsonProperty("message")
    protected String message;

    public SaveSchemaResult(String code, String message) {
      this.code = code;
      this.message = message;
    }
  }
}
