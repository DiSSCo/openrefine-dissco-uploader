package eu.dissco.refineextension.commands;

import com.google.refine.model.Project;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;

import com.google.refine.browsing.Engine;
import com.google.refine.commands.Command;
import com.google.refine.model.Row;
import com.google.refine.model.Cell;

import com.google.refine.browsing.RowVisitor;
import com.google.refine.browsing.FilteredRows;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.ServletException;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import net.cnri.cordra.api.CordraObject;
import net.cnri.cordra.api.CordraException;

import eu.dissco.refineextension.model.SyncState;
import eu.dissco.refineextension.processing.DigitalSpecimenProcessor;
import eu.dissco.refineextension.schema.DisscoSchema;
import eu.dissco.refineextension.util.DigitalObjectUtil;



// public class PerformNsidrEditsCommand extends EngineDependentCommand {
public class PerformNsidrEditsCommand extends Command {

  private String overlayModelKey = "disscoSchema";

  @Override
  public void doPost(HttpServletRequest request, HttpServletResponse response)
      throws ServletException, IOException {
    // To-Do: make csrf token check a
    try {
      String authToken = request.getParameter("token");
      if (authToken == null) {
        respond(response,
            "{ \"code\" : \"error\", \"message\" : \"No authentication token sent\" }");
        return;
      }
      Project project = getProject(request);
      Engine engine = getEngine(request, project);
      DisscoSchema savedSchema = (DisscoSchema) project.overlayModels.get(overlayModelKey);
      JsonNode columnMapping = savedSchema.getColumnMapping();
      Map<Integer, SyncState> syncStatusForRows = savedSchema.getSyncStatusForRows();
      response.setCharacterEncoding("UTF-8");
      response.setHeader("Content-Type", "application/json");
      FilteredRows filteredRows = engine.getAllFilteredRows();
      filteredRows.accept(project, new MyRowVisitor(columnMapping, syncStatusForRows, authToken));
      project.update(); // To-Do: is this necessary?
      respondJSON(response, new NsidrSyncResult(syncStatusForRows));
    } catch (Exception e) {
      e.printStackTrace();
      respondException(response, e);
    }
  }

  protected static class NsidrSyncResult {

    @JsonProperty("code")
    protected String code;
    @JsonProperty("message")
    protected String message;
    @JsonProperty("results")
    Map<Integer, SyncState> results;

    public NsidrSyncResult(String code, String message) {
      this.code = code;
      this.message = message;
      this.results = null;
    }

    public NsidrSyncResult(Map<Integer, SyncState> results) {
      this.code = "ok";
      this.message = null;
      this.results = results;
    }
  }

  protected class MyRowVisitor implements RowVisitor {

    private JsonNode columnMapping;
    Map<Integer, SyncState> syncStatusForRows;
    private String authToken;

    public MyRowVisitor(JsonNode columnMapping, Map<Integer, SyncState> syncStatusForRows,
        String authToken) {
      this.columnMapping = columnMapping;
      this.syncStatusForRows = syncStatusForRows;
      this.authToken = authToken;
    }

    @Override
    public void start(Project project) {
      ;
    }

    @Override
    public boolean visit(Project project, int rowIndex, Row row) {
      DigitalSpecimenProcessor syncProcessor = new DigitalSpecimenProcessor(authToken, this.columnMapping);
      // To-Do: make this more generic (right now only for a-section
      // objects)
      SyncState syncState = this.syncStatusForRows.get(rowIndex);
      String syncStatus = syncState.getSyncStatus();
      if (syncStatus == "new" || syncStatus == "change") {
        JsonObject contentToUpload = DigitalObjectUtil.rowToJsonObject(row, this.columnMapping, true);

        try {
          List<String> jsonPathAsList = new ArrayList<String>();
          if (syncStatus == "new") {
            System.out
                .println("found new: " + String.valueOf(rowIndex));

           
            CordraObject returnedNewDs = syncProcessor.createDigitalObjectsRecursive((JsonElement) contentToUpload, row, jsonPathAsList);
            // it is ensured that the doi col index is not null and
            // is integer upon schema saving ???
          } else {
            // To-Do: Use json patch here
            
            CordraObject returnedNewDs =
                syncProcessor.updateDigitalObjectsRecursive((JsonElement) contentToUpload, row, jsonPathAsList);
            System.out.println("successfully updated" + returnedNewDs.id);
          }
          this.syncStatusForRows.put(rowIndex, new SyncState("synchronized"));
        } catch (CordraException e) {
          System.out.println("an CordraObjectRepositoryException was thrown!");
          System.out.println(e.getMessage());
          this.syncStatusForRows.put(rowIndex,
              new SyncState("error", "Exception during upload " + e.getMessage()));
          e.printStackTrace();
        }
      }
      // syncProcessor.closeConnection();
      return false;
    }

    @Override
    public void end(Project project) {
      ;
    }
  }
}
