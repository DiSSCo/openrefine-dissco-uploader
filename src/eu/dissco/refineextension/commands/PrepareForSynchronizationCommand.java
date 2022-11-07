package eu.dissco.refineextension.commands;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.google.refine.browsing.Engine;
import com.google.refine.commands.Command;
import com.google.refine.model.Project;
import com.google.refine.model.Row;

import com.google.refine.browsing.RowVisitor;
import com.google.refine.browsing.FilteredRows;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import javax.servlet.ServletException;

import com.google.gson.JsonObject;

import eu.dissco.refineextension.model.SyncResult;
import eu.dissco.refineextension.model.SyncState;
import eu.dissco.refineextension.processing.SpecimenProcessor;
import eu.dissco.refineextension.schema.DisscoUploadSchema;
import eu.dissco.refineextension.util.DigitalObjectUtil;

public class PrepareForSynchronizationCommand extends Command {
  static final Logger logger = LoggerFactory.getLogger(PrepareForSynchronizationCommand.class);

  @Override
  public void doPost(HttpServletRequest request, HttpServletResponse response)
      throws ServletException, IOException {
    try {
      String authToken = request.getParameter("token");
      if (authToken == null) {
        respond(response,
            "{ \"code\" : \"error\", \"message\" : \"No authentication token sent\" }");
        return;
      }
      Project project = getProject(request);
      Engine engine = getEngine(request, project);
      DisscoUploadSchema savedSchema =
          (DisscoUploadSchema) project.overlayModels.get(DisscoUploadSchema.overlayModelKey);
      String specimenUrl = savedSchema.getSpecimenServerUrl();
      // To-Do check that schema has ColumnMapping
      JsonNode columnMapping = savedSchema.getColumnMapping();
      Map<Integer, SyncState> syncStatusForRows = new HashMap<Integer, SyncState>();
      Map<String, Integer> comparisonSummary = new HashMap<String, Integer>();
      response.setCharacterEncoding("UTF-8");
      response.setHeader("Content-Type", "application/json");
      FilteredRows filteredRows = engine.getAllFilteredRows();
      filteredRows.accept(project, new MyRowVisitor(columnMapping, syncStatusForRows,
          comparisonSummary, authToken, specimenUrl));

      // preserve the pre-sync results for the synchronization command
      savedSchema.setSyncStatusForRows(syncStatusForRows);
      project.overlayModels.put(DisscoUploadSchema.overlayModelKey, savedSchema);

      respondJSON(response, new SyncResult(syncStatusForRows, comparisonSummary));
    } catch (Exception e) {
      e.printStackTrace();
      respondException(response, e);
    }
  }

  protected class MyRowVisitor implements RowVisitor {

    private JsonNode columnMapping;
    private Map<Integer, SyncState> syncStatusForRows;
    private Map<String, Integer> comparisonSummary;
    SpecimenProcessor processorClient;

    public MyRowVisitor(JsonNode columnMapping, Map<Integer, SyncState> syncStatusForRows,
        Map<String, Integer> comparisonSummary, String authToken, String specimenUrl) {
      this.columnMapping = columnMapping;
      this.syncStatusForRows = syncStatusForRows;
      this.processorClient = new SpecimenProcessor(authToken, this.columnMapping, specimenUrl);
      this.comparisonSummary = comparisonSummary;
    }

    @Override
    public void start(Project project) {
      ;
    }

    @Override
    public boolean visit(Project project, int rowIndex, Row row) {
      logger.info("Checking sync state for row " + String.valueOf(rowIndex));
      SyncState rowSyncState;
      JsonObject digitalObjectContent = null;
      String id = "";
      JsonNode idColIndexNode = this.columnMapping.get("id").get("mapping");
      // it is ensured that the doi col index is not null and is integer
      // upon schema saving ???
      int idColIndex = idColIndexNode.asInt();
      id = (String) row.getCellValue(idColIndex);
      if (id != null && !id.equals("null") && id.length() > 0) {
        // digitalObjectContent = processorClient.getDeserializedDigitalObjectContent(id);
        digitalObjectContent = processorClient.getSpecimen(id).content.getAsJsonObject();
        if (digitalObjectContent != null) {
          JsonObject doContentToUpload =
              DigitalObjectUtil.rowToJsonObject(row, this.columnMapping, false);
          ArrayNode differencesPatch = null;
          try {
            differencesPatch =
                processorClient.getDigitalObjectDataDiff(digitalObjectContent, doContentToUpload);
          } catch (IOException e) {
            logger.error("json processing exception!");
            e.printStackTrace();
            rowSyncState = new SyncState("error",
                "Failure during comparison of the local data with the remote Digital Specimen to update. See logs.");
          }
          if (differencesPatch != null && differencesPatch.size() > 0) {
            // JSONs are not equal, Object will be updated
            rowSyncState = new SyncState("change", "", differencesPatch);
            for (int i = 0; i < differencesPatch.size(); i++) {
              JsonNode jsonOperation = differencesPatch.get(i);
              // key is a combination of the operation and the path
              String changeKey = jsonOperation.get("op").asText() + "-" + jsonOperation.get("path").asText();
              this.comparisonSummary.merge(changeKey, 1, Integer::sum);
            }

          } else {
            rowSyncState = new SyncState("synchronized");
          }
        } else {
          rowSyncState = new SyncState("new");
        }
      } else {
        rowSyncState = new SyncState("new");
      }
      this.syncStatusForRows.put(rowIndex, rowSyncState);
      return false;
    }

    @Override
    public void end(Project project) {
      ;
    }
  }
}
