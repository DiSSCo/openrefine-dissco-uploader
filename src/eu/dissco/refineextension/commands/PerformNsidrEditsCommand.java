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
import java.util.Iterator;
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
import eu.dissco.refineextension.util.DigitalSpecimenUtil;



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
      System.out.println("heyho received token " + authToken);
      Project project = getProject(request);
      Engine engine = getEngine(request, project);
      DisscoSchema savedSchema = (DisscoSchema) project.overlayModels.get(overlayModelKey);
      System.out.println("found saved DisscoSchema");
      System.out.println(savedSchema);
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
      System.out.println("visit " + String.valueOf(rowIndex));
      DigitalSpecimenProcessor syncProcessor = new DigitalSpecimenProcessor(authToken);
      // To-Do: make this more generic (right now only for a-section
      // objects)
      SyncState syncState = this.syncStatusForRows.get(rowIndex);
      String syncStatus = syncState.getSyncStatus();
      if (syncStatus == "new" || syncStatus == "change") {
        JsonObject content = DigitalSpecimenUtil.rowToJsonObject(row, this.columnMapping, "", true);
        CordraObject ds = new CordraObject();
        ds.setContent(content);
        ds.type = "ODStypeV0.2";

        try {
          if (syncStatus == "new") {
            System.out
                .println("found new: " + String.valueOf(rowIndex) + content.get("ods:authoritative")
                    .getAsJsonObject().get("ods:curatedObjectID").getAsString());

            CordraObject returnedNewDs = syncProcessor.createDigitalSpecimen(ds);
            System.out.println("successfully created" + returnedNewDs.id);
            // it is ensured that the doi col index is not null and
            // is integer upon schema saving
            JsonNode doiColIndexNode = this.columnMapping.get("doi");
            int doiColumnIndex = doiColIndexNode.asInt();
            row.setCell(doiColumnIndex, new Cell(returnedNewDs.id, null));
            // if new media objects were created, set the ID for every one
            if (this.columnMapping.has("ods:mediaCollection")) {
              JsonNode mediaCollectionEl = this.columnMapping.get("ods:mediaCollection");
              if (mediaCollectionEl.isObject() && mediaCollectionEl.has("ods:mediaObjects")) {
                JsonNode mediaObjects = mediaCollectionEl.get("ods:mediaObjects");
                if (mediaObjects.isArray() && mediaObjects.size() > 0) {
                  Iterator<JsonNode> iter = mediaObjects.elements();


                  JsonObject createdContent = returnedNewDs.content.getAsJsonObject();
                  JsonElement createdMediaCollectionEl = createdContent.get("ods:mediaCollection");
                  JsonArray mediaCollection = createdMediaCollectionEl.getAsJsonObject()
                      .get("ods:mediaObjects").getAsJsonArray();

                  int i = 0;
                  while (iter.hasNext()) {
                    JsonNode mediaObject = iter.next();
                    int columnIndex = mediaObject.get("ods:mediaId").asInt();

                    // find the ID of the created mediaObject
                    JsonObject createdMediaObject = mediaCollection.get(i).getAsJsonObject();
                    String mediaId = createdMediaObject.get("ods:mediaId").getAsString();
                    row.setCell(columnIndex, new Cell(mediaId, null));
                    i += 1;
                    System.out.println("updated row col!");
                  }
                }
              }
            }
          } else {
            // To-Do: Use json patch here
            JsonNode doi = this.columnMapping.get("doi");
            int colIndex = doi.asInt();
            ds.id = (String) row.getCellValue(colIndex);
            System.out.println((String) row.getCellValue(colIndex));
            CordraObject returnedNewDs =
                syncProcessor.updateDigitalSpecimen(ds, syncState.getChanges());
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
