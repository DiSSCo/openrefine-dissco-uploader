
package eu.dissco.refineextension.commands;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;

import com.google.refine.commands.Command;
import com.google.refine.model.Project;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.ServletException;

import com.google.refine.util.ParsingUtilities;

import eu.dissco.refineextension.model.SyncState;
import eu.dissco.refineextension.schema.DisscoSchema;

public class SaveSchemaCommand extends Command {

    private String overlayModelKey = "disscoSchema";

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
            JsonNode doi = columnMapping.get("doi");
            if(doi == null || doi.isNull()) {
                respond(response, "{ \"code\" : \"error\", \"message\" : \"A mapping for the doi field is obligatory\" }");
                return;
            } else if (!doi.isInt()) {
                respond(response, "{ \"code\" : \"error\", \"message\" : \"The doi column index must be an integer\" }");
                return;
            }
            List<String> resultArray = new ArrayList<String>();
            Map<Integer, SyncState> syncStatusForRows = new HashMap<Integer, SyncState>();
            DisscoSchema schema = new DisscoSchema(columnMapping, syncStatusForRows);
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
