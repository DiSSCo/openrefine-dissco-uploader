package eu.dissco.refineextension.commands;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.refine.browsing.Engine;
import com.google.refine.browsing.Engine.Mode;
import com.google.refine.browsing.FilteredRecords;
import com.google.refine.browsing.FilteredRows;
import com.google.refine.browsing.RecordVisitor;
import com.google.refine.browsing.RowVisitor;
import com.google.refine.commands.Command;
import com.google.refine.importing.ImportingJob;
import com.google.refine.importing.ImportingManager;
import com.google.refine.model.Project;
import com.google.refine.model.Record;
import com.google.refine.model.Row;
import com.google.refine.sorting.SortingConfig;
import com.google.refine.sorting.SortingRecordVisitor;
import com.google.refine.sorting.SortingRowVisitor;
import com.google.refine.util.ParsingUtilities;
import com.google.refine.util.Pool;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.ServletException;
import eu.dissco.refineextension.model.SyncState;
import eu.dissco.refineextension.schema.DisscoUploadSchema;


public class GetSynchronizationResultCommand extends Command {
  /**
   * This command accepts both POST and GET. It is not CSRF-protected as it does not incur any state
   * change.
   */

  @Override
  public void doPost(HttpServletRequest request, HttpServletResponse response)
      throws ServletException, IOException {
    internalRespond(request, response);
  }

  @Override
  public void doGet(HttpServletRequest request, HttpServletResponse response)
      throws ServletException, IOException {
    internalRespond(request, response);
  }

  protected void internalRespond(HttpServletRequest request, HttpServletResponse response)
      throws ServletException, IOException {

    try {
      Project project = null;

      // This command also supports retrieving rows for an importing job.
      String importingJobID = request.getParameter("importingJobID");
      if (importingJobID != null) {
        long jobID = Long.parseLong(importingJobID);
        ImportingJob job = ImportingManager.getJob(jobID);
        if (job != null) {
          project = job.project;
        }
      }
      if (project == null) {
        project = getProject(request);
      }

      Engine engine = getEngine(request, project);
      String callback = request.getParameter("callback");

      int start =
          Math.min(project.rows.size(), Math.max(0, getIntegerParameter(request, "start", 0)));
      int limit = Math.min(project.rows.size() - start,
          Math.max(0, getIntegerParameter(request, "limit", 20)));

      Pool pool = new Pool();

      DisscoUploadSchema savedSchema =
          (DisscoUploadSchema) project.overlayModels.get(DisscoUploadSchema.overlayModelKey);
      Map<Integer, SyncState> syncStatusForAllRows = savedSchema.getSyncStatusForRows();

      response.setCharacterEncoding("UTF-8");
      response.setHeader("Content-Type", callback == null ? "application/json" : "text/javascript");

      PrintWriter writer = response.getWriter();
      if (callback != null) {
        writer.write(callback);
        writer.write("(");
      }

      RowWritingVisitor rwv = new RowWritingVisitor(start, limit, syncStatusForAllRows);

      SortingConfig sortingConfig = null;
      try {
        String sortingJson = request.getParameter("sorting");
        if (sortingJson != null) {
          sortingConfig = SortingConfig.reconstruct(sortingJson);
        }
      } catch (IOException e) {
      }

      if (engine.getMode() == Mode.RowBased) {
        FilteredRows filteredRows = engine.getAllFilteredRows();
        RowVisitor visitor = rwv;

        if (sortingConfig != null) {
          SortingRowVisitor srv = new SortingRowVisitor(visitor);

          srv.initializeFromConfig(project, sortingConfig);
          if (srv.hasCriteria()) {
            visitor = srv;
          }
        }
        filteredRows.accept(project, visitor);
      } else {
        FilteredRecords filteredRecords = engine.getFilteredRecords();
        RecordVisitor visitor = rwv;

        if (sortingConfig != null) {
          SortingRecordVisitor srv = new SortingRecordVisitor(visitor);

          srv.initializeFromConfig(project, sortingConfig);
          if (srv.hasCriteria()) {
            visitor = srv;
          }
        }
        filteredRecords.accept(project, visitor);
      }

      CustomSyncResult result = new CustomSyncResult(rwv.syncStatusForFilteredRows, rwv.syncStatusTypeCount);

      ParsingUtilities.defaultWriter.writeValue(writer, result);
      if (callback != null) {
        writer.write(")");
      }

      // metadata refresh for row mode and record mode
      if (project.getMetadata() != null) {
        project.getMetadata().setRowCount(project.rows.size());
      }
    } catch (Exception e) {
      respondException(response, e);
    }
  }
  
  static protected class CustomSyncResult {
    @JsonProperty("code")
    protected String code;
    @JsonProperty("message")
    protected String message;
    @JsonProperty("results")
    Map<Integer, SyncState> results;
    @JsonProperty("stats")
    Map<String, Integer> stats;

    public CustomSyncResult(String code, String message) {
      this.code = code;
      this.message = message;
      this.results = null;
    }

    public CustomSyncResult(Map<Integer, SyncState> results, Map<String, Integer> stats) {
      this.code = "ok";
      this.message = null;
      this.results = results;
      this.stats = stats;
    }
  }

  static protected class RowWritingVisitor implements RowVisitor, RecordVisitor {
    final int start;
    final int limit;
    final Map<Integer, SyncState> syncStatusForAllRows;
    public Map<Integer, SyncState> syncStatusForFilteredRows;

    public int total;
    public Map<String, Integer> syncStatusTypeCount; 

    public RowWritingVisitor(int start, int limit, Map<Integer, SyncState> syncStatusForAllRows) {
      this.start = start;
      this.limit = limit;
      this.syncStatusForAllRows = syncStatusForAllRows;
      this.syncStatusForFilteredRows = new HashMap<Integer, SyncState>();
      this.syncStatusTypeCount = new HashMap<String, Integer>();
    }

    @Override
    public void start(Project project) {
      // nothing to do
    }

    @Override
    public void end(Project project) {
      // nothing to do
    }

    @Override
    public boolean visit(Project project, int rowIndex, Row row) {
      SyncState s = syncStatusForAllRows.get(rowIndex);
      if (total >= start && total < start + limit) {
        syncStatusForFilteredRows.put(rowIndex, s);
      }
      total++;
      String status = s.getSyncStatus();
      if(syncStatusTypeCount.containsKey(status)) {
        int count = syncStatusTypeCount.get(status);
        syncStatusTypeCount.put(status, count + 1);
      } else {
        syncStatusTypeCount.put(status, 0);
      }
      return false;
    }

    @Override
    public boolean visit(Project project, Record record) {
      if (total >= start && total < start + limit) {
        for (int r = record.fromRowIndex; r < record.toRowIndex; r++) {
          syncStatusForFilteredRows.put(r, syncStatusForAllRows.get(r));
        }
      }
      total++;

      return false;
    }

  }
}

