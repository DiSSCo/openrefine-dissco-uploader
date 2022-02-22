package eu.dissco.refineextension.commands;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import com.google.refine.browsing.Engine;
import com.google.refine.commands.Command;
import com.google.refine.model.Project;
import com.google.refine.model.Row;

import com.google.refine.browsing.RowVisitor;
import com.google.refine.browsing.FilteredRows;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// import java.util.StringBuilder;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.ServletException;

import com.google.gson.JsonObject;

import net.dona.doip.client.DigitalObject;
import net.dona.doip.client.PasswordAuthenticationInfo;
import net.dona.doip.client.ServiceInfo;
import net.dona.doip.client.TokenAuthenticationInfo;
import net.dona.doip.client.DoipException;
import net.dona.doip.client.QueryParams;
import net.dona.doip.client.SearchResults;
import net.dona.doip.client.DoipClient;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.fge.jsonpatch.diff.JsonDiff;

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
                respond(response, "{ \"code\" : \"error\", \"message\" : \"No authentication token sent\" }");
                return;
            }
            Project project = getProject(request);
            Engine engine = getEngine(request, project);
            DisscoSchema savedSchema = (DisscoSchema) project.overlayModels.get("disscoSchema");
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
        private DigitalSpecimenProcessor client;

        public MyRowVisitor(JsonNode columnMapping, Map<Integer, SyncState> syncStatusForRows, String authToken) {
            this.columnMapping = columnMapping;
            this.syncStatusForRows = syncStatusForRows;
            this.client = new DigitalSpecimenProcessor(authToken);
        }

        @Override
        public void start(Project project) {
            ;
        }

        @Override
        public boolean visit(Project project, int rowIndex, Row row) {
            DigitalObject ds = null;
            String curatedObjectID = "";
            String doi = "";
            JsonNode doiColIndexNode = this.columnMapping.get("doi");
            // it is ensured that the doi col index is not null and is integer
            // upon schema saving
            int doiColIndex = doiColIndexNode.asInt();
            doi = (String) row.getCellValue(doiColIndex);
            System.out.println("found doi : " + doi);
            ds = this.client.findRemoteDigitalSpecimenById(doi);
            if (ds == null) {
                JsonNode authoritativeNode = this.columnMapping.get("ods:authoritative");
                if (!authoritativeNode.isNull()) {
                    JsonNode curatedObjectIdNode = authoritativeNode.get("ods:curatedObjectID");
                    if (!curatedObjectIdNode.isNull() && curatedObjectIdNode.isInt()) {
                        int curatedObjectIdIndex = doiColIndexNode.asInt();
                        curatedObjectID = (String) row.getCellValue(curatedObjectIdIndex);
                        ds = this.client.findRemoteDigitalSpecimenByCuratedObjectID(curatedObjectID);
                    }
                }
            }
            if (ds != null) {
                //try {
                JsonObject dsContentToUpload = DigitalSpecimenUtil.rowToJsonObject(row, this.columnMapping, true);
                JsonNode differencesPatch = null;
                try {
                    differencesPatch = this.client.getDigitalSpecimenDataDiff(ds, dsContentToUpload);
                } catch (IOException | DoipException e) {
                    System.out.println("json processing exception!");
                    e.printStackTrace();
                    this.syncStatusForRows.put(rowIndex, new SyncState("error", "Failure during comparison of the local data with the remote Digital Specimen to update. See logs."));
                }
                if (differencesPatch != null && differencesPatch.size() > 0) {
                    // JSONs are not equal, Object will be updated
                    this.syncStatusForRows.put(rowIndex, new SyncState("change", "", differencesPatch));
                } else {
                    this.syncStatusForRows.put(rowIndex, new SyncState("synchronized"));
                }
            } else if (curatedObjectID.length() == 0 && doi.length() == 0) {
                this.syncStatusForRows.put(rowIndex, new SyncState("error", "missing doi or curatedObjectID"));
            } else {
                // System.out.println("Setting nsidrSyncStatus");
                // row.nsidrSyncStatus = "new";
                this.syncStatusForRows.put(rowIndex, new SyncState("new"));
            }
            return false;
        }

        @Override
        public void end(Project project) {
            ;
        }
    }
}
