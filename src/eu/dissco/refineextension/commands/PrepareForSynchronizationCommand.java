package eu.dissco.refineextension.commands;

import com.fasterxml.jackson.databind.JsonNode;

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

import net.cnri.cordra.api.CordraException;
import eu.dissco.refineextension.model.SyncResult;
import eu.dissco.refineextension.model.SyncState;
import eu.dissco.refineextension.processing.DigitalObjectProcessor;
import eu.dissco.refineextension.schema.CordraUploadSchema;
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
      CordraUploadSchema savedSchema =
          (CordraUploadSchema) project.overlayModels.get(CordraUploadSchema.overlayModelKey);
      String cordraUrl = savedSchema.getCordraServerUrl();
      // To-Do check that schema haas ColumnMapping
      JsonNode columnMapping = savedSchema.getColumnMapping();
      Map<Integer, SyncState> syncStatusForRows = new HashMap<Integer, SyncState>();
      response.setCharacterEncoding("UTF-8");
      response.setHeader("Content-Type", "application/json");
      FilteredRows filteredRows = engine.getAllFilteredRows();
      filteredRows.accept(project, new MyRowVisitor(columnMapping, syncStatusForRows, authToken, cordraUrl));

      // preserve the pre-sync results for the synchronization command
      savedSchema.setSyncStatusForRows(syncStatusForRows);
      project.overlayModels.put(CordraUploadSchema.overlayModelKey, savedSchema);

      respondJSON(response, new SyncResult(syncStatusForRows));
    } catch (Exception e) {
      e.printStackTrace();
      respondException(response, e);
    }
  }

  protected class MyRowVisitor implements RowVisitor {

    private JsonNode columnMapping;
    private Map<Integer, SyncState> syncStatusForRows;
    DigitalObjectProcessor processorClient;

    public MyRowVisitor(JsonNode columnMapping, Map<Integer, SyncState> syncStatusForRows,
        String authToken, String cordraUrl) {
      this.columnMapping = columnMapping;
      this.syncStatusForRows = syncStatusForRows;
      this.processorClient = new DigitalObjectProcessor(authToken, cordraUrl, this.columnMapping, null);
    }

    @Override
    public void start(Project project) {
      ;
    }

    @Override
    public boolean visit(Project project, int rowIndex, Row row) {
      logger.info("Checking sync state for row " + String.valueOf(rowIndex));
      SyncState rowSyncState = new SyncState();
      JsonObject digitalObjectContent = null;
      String id = "";
      JsonNode idColIndexNode = this.columnMapping.get("id").get("mapping");
      // it is ensured that the doi col index is not null and is integer
      // upon schema saving ???
      int idColIndex = idColIndexNode.asInt();
      id = (String) row.getCellValue(idColIndex);
      digitalObjectContent = processorClient.getDeserializedDigitalObjectContent(id);
      if (digitalObjectContent != null) {
        JsonObject doContentToUpload =
            DigitalObjectUtil.rowToJsonObject(row, this.columnMapping, false);
        JsonNode differencesPatch = null;
        try {
          differencesPatch =
              processorClient.getDigitalObjectDataDiff(digitalObjectContent, doContentToUpload);
        } catch (IOException | CordraException e) {
          logger.error("json processing exception!");
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
