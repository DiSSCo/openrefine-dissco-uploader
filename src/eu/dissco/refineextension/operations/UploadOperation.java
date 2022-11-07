package eu.dissco.refineextension.operations;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Collectors;
import com.fasterxml.jackson.databind.JsonNode;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.refine.browsing.Engine;
import com.google.refine.browsing.EngineConfig;
import com.google.refine.browsing.RowFilter;
import com.google.refine.browsing.RowVisitor;
import com.google.refine.browsing.facets.Facet;
import com.google.refine.browsing.util.ConjunctiveFilteredRows;
import com.google.refine.model.Project;
import com.google.refine.model.Row;
import com.google.refine.operations.EngineDependentOperation;
import com.google.refine.process.LongRunningProcess;
import com.google.refine.process.Process;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import eu.dissco.refineextension.model.SyncState;
import eu.dissco.refineextension.processing.SpecimenProcessor;
import eu.dissco.refineextension.schema.DisscoUploadSchema;
import eu.dissco.refineextension.util.DigitalObjectUtil;

import eu.dissco.refineextension.schema.DigitalObject;

public class UploadOperation extends EngineDependentOperation {
  static final Logger logger = LoggerFactory.getLogger(UploadOperation.class);

  private String authToken;

  public UploadOperation(EngineConfig engineConfig, Project project, String authToken) {
    super(engineConfig);
    this.authToken = authToken;
  }

  @Override
  protected String getBriefDescription(Project project) {
    return "Uploading data to DiSSCo";
  }

  @Override
  public Process createProcess(Project project, Properties options) throws Exception {
    return new PerformUploadProcess(project, _engineConfig, getBriefDescription(project),
        this.authToken);
  }

  public class PerformUploadProcess extends LongRunningProcess implements Runnable {

    protected Project _project;
    protected String _authToken;
    protected Engine _engine;
    protected EngineConfig _engineConfig;

    protected PerformUploadProcess(Project project, EngineConfig engineConfig, String description,
        String authToken) throws Exception {
      super(description);
      this._project = project;
      this._authToken = authToken;
      this._engineConfig = engineConfig;
      Engine engine = createEngine(project);
      engine.initializeFromConfig(_engineConfig);
      this._engine = engine;
    }

    @Override
    public void run() {
      DisscoUploadSchema savedSchema =
          (DisscoUploadSchema) _project.overlayModels.get(DisscoUploadSchema.overlayModelKey);
      String specimenUrl = savedSchema.getSpecimenServerUrl();
      Map<Integer, SyncState> syncStatusForRows = savedSchema.getSyncStatusForRows();
      JsonNode columnMapping = savedSchema.getColumnMapping();
      int maximumThreadsNum = savedSchema.getNumberOfProcessingThreads();
      int rowsSize = _project.rows.size();
      int batchSize = 30;

      int processedBatched = 0;

      List<Facet> facets = new LinkedList<Facet>();
      facets = _engineConfig.getFacetConfigs().stream().map(c -> c.apply(_project))
          .collect(Collectors.toList());

      ThreadUploadClass[] threads = new ThreadUploadClass[maximumThreadsNum];

      while (processedBatched < rowsSize) {
        for (int i = 0; i < maximumThreadsNum; i++) {
          ThreadUploadClass t = threads[i];
          if (t == null || !t.isAlive()) {
            _progress = processedBatched * 100 / rowsSize;
            int endRow = processedBatched + batchSize;
            if (endRow > rowsSize) {
              endRow = rowsSize;
            }
            ThreadUploadClass tnew = new ThreadUploadClass(columnMapping, syncStatusForRows,
                _authToken, specimenUrl, facets);
            tnew.setStartEndRows(processedBatched, endRow);
            tnew.start();
            threads[i] = tnew;
            processedBatched += batchSize;
          }
        }
      }


      _progress = 100;

      if (!_canceled) {
        UploadOperation.logger.info("process is finished");

        _project.processManager.onDoneProcess(this);
      }
    }

    protected class ThreadUploadClass extends Thread {

      int _startRow;
      int _endRow;
      JsonNode columnMapping;
      Map<Integer, SyncState> syncStatusForRows;
      String authToken;
      String specimenUrl;
      List<Facet> _facets;

      void setStartEndRows(int startRow, int endRow) {
        this._startRow = startRow;
        this._endRow = endRow;
      }

      protected ThreadUploadClass(JsonNode columnMapping, Map<Integer, SyncState> syncStatusForRows,
          String authToken, String specimenUrl, List<Facet> facets) {
        this.columnMapping = columnMapping;
        this.syncStatusForRows = syncStatusForRows;
        this.authToken = authToken;
        this.specimenUrl = specimenUrl;
        this._facets = facets;
      }

      @Override
      public void run() {
        MyConjunctiveFilteredRows filteredRows = createFilteredRows(_facets, _startRow, _endRow);
        filteredRows.accept(_project,
            new MyRowVisitor(columnMapping, syncStatusForRows, authToken, specimenUrl));
      }

      private class MyConjunctiveFilteredRows extends ConjunctiveFilteredRows {
        private int _start;
        private int _end;

        private MyConjunctiveFilteredRows(int start, int end) {
          _start = start;
          _end = end;
        }

        @Override
        public void accept(Project project, RowVisitor visitor) {
          try {
            visitor.start(project);

            for (int rowIndex = _start; rowIndex < _end; rowIndex++) {
              Row row = project.rows.get(rowIndex);
              if (matchRow(project, rowIndex, row)) {
                if (visitRow(project, visitor, rowIndex, row)) {
                  break;
                }
              }
            }
          } finally {
            visitor.end(project);
          }
        }
      }

      private MyConjunctiveFilteredRows createFilteredRows(List<Facet> _facets, int start,
          int end) {
        MyConjunctiveFilteredRows cfr = new MyConjunctiveFilteredRows(start, end);
        for (Facet facet : _facets) {
          RowFilter rowFilter = facet.getRowFilter(_project);
          if (rowFilter != null) {
            cfr.add(rowFilter);
          }
        }
        return cfr;
      }

      protected class MyRowVisitor implements RowVisitor {

        private JsonNode columnMapping;
        Map<Integer, SyncState> syncStatusForRows;
        private String authToken;
        private String specimenUrl;

        public MyRowVisitor(JsonNode columnMapping, Map<Integer, SyncState> syncStatusForRows,
            String authToken, String specimenUrl) {
          this.columnMapping = columnMapping;
          this.syncStatusForRows = syncStatusForRows;
          this.authToken = authToken;
          this.specimenUrl = specimenUrl;
        }

        @Override
        public void start(Project project) {
          ;
        }

        @Override
        public boolean visit(Project project, int rowIndex, Row row) {
          SpecimenProcessor syncProcessor = new SpecimenProcessor(authToken, this.columnMapping, this.specimenUrl);


          SyncState syncState = this.syncStatusForRows.get(rowIndex);
          String syncStatus = syncState.getSyncStatus();
          if (syncStatus == "new" || syncStatus == "change") {
            JsonObject contentToUpload =
                DigitalObjectUtil.rowToJsonObject(row, this.columnMapping, true);
            try {
              List<String> jsonPathAsList = new ArrayList<String>();
              List<SpecimenProcessor.DependentObjectTuple> dependentObjects = new ArrayList<SpecimenProcessor.DependentObjectTuple>();
              if (syncStatus == "new") {
                DigitalObject newDO = syncProcessor.createDigitalObjectsRecursive(
                    (JsonElement) contentToUpload, row, jsonPathAsList, dependentObjects);
                UploadOperation.logger.info("Created new DO: " + newDO.id);
              } else {
                DigitalObject updatedDO = syncProcessor.updateDigitalObjectsRecursive(
                    (JsonElement) contentToUpload, row, jsonPathAsList, dependentObjects);
                UploadOperation.logger.info("Updated new DO: " + updatedDO.id);
              }
              this.syncStatusForRows.put(rowIndex, new SyncState("synchronized"));
            } catch (Exception e) {
              logger.error("an Exception was thrown!");
              logger.error(e.getMessage());
              this.syncStatusForRows.put(rowIndex,
                  new SyncState("error", "Exception during upload " + e.getMessage()));
              e.printStackTrace();
            }
          }
          return false;
        }

        @Override
        public void end(Project project) {
          ;
        }
      }
    }

    @Override
    protected Runnable getRunnable() {
      return this;
    }
  }
}
