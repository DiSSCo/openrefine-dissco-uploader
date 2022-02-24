package eu.dissco.refineextension.commands;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;

import com.google.refine.browsing.Engine;
import com.google.refine.commands.Command;
import com.google.refine.model.Cell;
import com.google.refine.model.Project;
import com.google.refine.model.Row;

import com.google.refine.browsing.RowVisitor;
import com.google.refine.browsing.FilteredRows;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

// import java.util.StringBuilder;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.ServletException;

import com.google.gson.JsonObject;

import net.cnri.cordra.api.CordraObject;
import net.cnri.cordra.api.CordraException;

import eu.dissco.refineextension.model.SyncState;
import eu.dissco.refineextension.processing.DigitalSpecimenProcessor;
import eu.dissco.refineextension.schema.DisscoSchema;
import eu.dissco.refineextension.util.DigitalSpecimenUtil;

public class PrepareForSynchronizationCommand extends Command {

  private String overlayModelKey = "disscoSchema";

  @Override
  public void doPost(HttpServletRequest request, HttpServletResponse response)
      throws ServletException, IOException {
    // To-Do: make csrf token check
    try {
      String authToken = request.getParameter("token");
      if (authToken == null) {
        respond(response,
            "{ \"code\" : \"error\", \"message\" : \"No authentication token sent\" }");
        return;
      }
      Project project = getProject(request);
      Engine engine = getEngine(request, project);
      DisscoSchema savedSchema = (DisscoSchema) project.overlayModels.get("disscoSchema");
      System.out.println("the schema: ");
      System.out.println(savedSchema);
      System.out.println(savedSchema.getColumnMapping());
      // To-Do check that schema haas ColumnMapping
      JsonNode columnMapping = savedSchema.getColumnMapping();
      // Map syncStatusForRows = savedSchema.getSyncStatusForRows();
      Map<Integer, SyncState> syncStatusForRows = new HashMap<Integer, SyncState>();
      response.setCharacterEncoding("UTF-8");
      response.setHeader("Content-Type", "application/json");
      FilteredRows filteredRows = engine.getAllFilteredRows();
      filteredRows.accept(project, new MyRowVisitor(columnMapping, syncStatusForRows, authToken));

      // preserve the pre-sync results for the synchronization command
      DisscoSchema newSchema = new DisscoSchema(columnMapping, syncStatusForRows);
      project.overlayModels.put(overlayModelKey, newSchema);
      System.out.println("WOw hey yeah saved new schema, new!");

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
    private Map<Integer, SyncState> syncStatusForRows;
    DigitalSpecimenProcessor processorClient;

    public MyRowVisitor(JsonNode columnMapping, Map<Integer, SyncState> syncStatusForRows,
        String authToken) {
      this.columnMapping = columnMapping;
      this.syncStatusForRows = syncStatusForRows;
      this.processorClient = new DigitalSpecimenProcessor(authToken);
    }

    @Override
    public void start(Project project) {
      ;
    }

    @Override
    public boolean visit(Project project, int rowIndex, Row row) {
      System.out.println("visiting " + String.valueOf(rowIndex));
      SyncState rowSyncState = new SyncState();
      CordraObject ds = null;
      String curatedObjectID = "";
      String doi = "";
      JsonNode doiColIndexNode = this.columnMapping.get("doi");
      // it is ensured that the doi col index is not null and is integer
      // upon schema saving
      int doiColIndex = doiColIndexNode.asInt();
      doi = (String) row.getCellValue(doiColIndex);
      ds = processorClient.findRemoteDigitalSpecimenById(doi);
      if (ds == null) {
        JsonNode authoritativeNode = this.columnMapping.get("ods:authoritative");
        System.out.println(authoritativeNode);
        if (!authoritativeNode.isNull()) {
          JsonNode curatedObjectIdNode = authoritativeNode.get("ods:curatedObjectID");
          if (!curatedObjectIdNode.isNull() && curatedObjectIdNode.isInt()) {
            int curatedObjectIdIndex = curatedObjectIdNode.asInt();
            curatedObjectID = (String) row.getCellValue(curatedObjectIdIndex);
            ds = processorClient.findRemoteDigitalSpecimenByCuratedObjectID(curatedObjectID);
          }
        }
      }
      if (ds != null) {
        if (doi.length() == 0) {
          // when we found an object set the doi in the data
          row.setCell(doiColIndex, new Cell(ds.id, null));
        }
        JsonObject dsContentToUpload =
            DigitalSpecimenUtil.rowToJsonObject(row, this.columnMapping, "", true);
        JsonNode differencesPatch = null;
        try {
          differencesPatch = processorClient.getDigitalSpecimenDataDiff(ds, dsContentToUpload);
        } catch (IOException | CordraException e) {
          System.out.println("json processing exception!");
          e.printStackTrace();
          rowSyncState = new SyncState("error",
              "Failure during comparison of the local data with the remote Digital Specimen to update. See logs.");
        }
        if (differencesPatch != null && differencesPatch.size() > 0) {
          // JSONs are not equal, Object will be updated
          rowSyncState = new SyncState("change", "", differencesPatch);
        } else {
          rowSyncState = new SyncState("synchronized");
        }
      } else if (curatedObjectID.length() == 0 && doi.length() == 0) {
        rowSyncState = new SyncState("error", "missing doi or curatedObjectID");
      } else {
        rowSyncState = new SyncState("new");
      }
      System.out.println("sync state: " + rowSyncState.getSyncStatus());
      this.syncStatusForRows.put(rowIndex, rowSyncState);
      return false;
    }

    @Override
    public void end(Project project) {
      ;
    }
  }
}
