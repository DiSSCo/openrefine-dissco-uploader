package eu.dissco.refineextension.commands;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;

import com.google.refine.commands.Command;
import com.google.refine.model.Project;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.ServletException;

import com.google.refine.util.ParsingUtilities;

import eu.dissco.refineextension.model.SyncState;
import eu.dissco.refineextension.schema.CordraUploadSchema;

public class SaveSchemaCommand extends Command {

  @Override
  public void doPost(HttpServletRequest request, HttpServletResponse response)
      throws ServletException, IOException {
    // To-Do: make csrf token check
    try {
      Project project = getProject(request);
      String columnMappingString = request.getParameter("columnMapping");
      if (columnMappingString == null) {
        respond(response, "{ \"code\" : \"error\", \"message\" : \"No columnMapping specified\" }");
        return;
      }
      JsonNode columnMapping = ParsingUtilities.evaluateJsonStringToObjectNode(columnMappingString);
      JsonNode idNode = columnMapping.get("id");
      if (idNode == null || idNode.isNull()) {
        respond(response,
            "{ \"code\" : \"error\", \"message\" : \"A mapping for the id field is obligatory\" }");
        return;
      }
      JsonNode idMapping = idNode.get("mapping");
      if (idMapping == null || idMapping.isNull()) {
        respond(response,
            "{ \"code\" : \"error\", \"message\" : \"A mapping for the id field is obligatory\" }");
        return;
      }
      if (!idMapping.isInt()) {
        respond(response,
            "{ \"code\" : \"error\", \"message\" : \"The doi column index must be an integer\" }");
        return;
      }
      CordraUploadSchema schema =
          (CordraUploadSchema) project.overlayModels.get(CordraUploadSchema.overlayModelKey);
      if (schema == null) {
        schema = new CordraUploadSchema();
      }
      Map<Integer, SyncState> syncStatusForRows = new HashMap<Integer, SyncState>();
      schema.setSyncStatusForRows(syncStatusForRows);
      schema.setColumnMapping(columnMapping);
      project.overlayModels.put(CordraUploadSchema.overlayModelKey, schema);

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

