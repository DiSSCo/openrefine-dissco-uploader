
package eu.dissco.refineextension.operations;

import java.io.Serializable;
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
import com.google.refine.browsing.FilteredRows;
import com.google.refine.browsing.RowFilter;
import com.google.refine.browsing.RowVisitor;
import com.google.refine.browsing.facets.Facet;
import com.google.refine.browsing.util.ConjunctiveFilteredRows;
import com.google.refine.model.Cell;
import com.google.refine.model.Project;
import com.google.refine.model.Row;
import com.google.refine.model.changes.CellAtRow;
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

public class PerformChangeOperation extends EngineDependentOperation {
	static final Logger logger = LoggerFactory.getLogger(UploadOperation.class);

	private String changeToPerform;
	private int _cellIndex;

	public PerformChangeOperation(EngineConfig engineConfig, Project project, String changeToPerform, int cellIndex) {
		super(engineConfig);
		this.changeToPerform = changeToPerform;
		this._cellIndex = cellIndex;
	}

	@Override
	protected String getBriefDescription(Project project) {
		return "Update local dataset by resolving registered change.";
	}

	@Override
	public Process createProcess(Project project, Properties options) throws Exception {
		return new PerformUploadProcess(project, _engineConfig, getBriefDescription(project), this.changeToPerform);
	}

	public class PerformUploadProcess extends LongRunningProcess implements Runnable {

		protected Project _project;
		protected String _changeToPerform;
		protected Engine _engine;
		protected EngineConfig _engineConfig;
		Map<Integer, SyncState> _syncStatusForRows;

		protected PerformUploadProcess(Project project, EngineConfig engineConfig, String description,
				String changeToPerform) throws Exception {
			super(description);
			this._project = project;
			this._changeToPerform = changeToPerform;
			this._engineConfig = engineConfig;
			Engine engine = createEngine(project);
			engine.initializeFromConfig(_engineConfig);
			this._engine = engine;
		}

		@Override
		public void run() {
			DisscoUploadSchema savedSchema = (DisscoUploadSchema) _project.overlayModels
					.get(DisscoUploadSchema.overlayModelKey);
			_syncStatusForRows = savedSchema.getSyncStatusForRows();

			try {
				FilteredRows filteredRows = _engine.getAllFilteredRows();
				filteredRows.accept(_project, createRowVisitor(_project));

			} catch (Exception e) {
				System.out.println("eeeeeeeeeeeeeeeeee");
				e.printStackTrace();
			}
			_progress = 100;

			if (!_canceled) {
				UploadOperation.logger.info("process is finished");

				_project.processManager.onDoneProcess(this);
			}
		}

		protected RowVisitor createRowVisitor(Project project) throws Exception {

			return new RowVisitor() {
				public RowVisitor init() {
					return this;
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
					System.out.println("visit!!! " + String.valueOf(rowIndex));
					System.out.println(changeToPerform);
					SyncState syncState = _syncStatusForRows.get(rowIndex);
					JsonNode changes = syncState.getChanges();
					for (final JsonNode change : changes) {
						String operation = change.get("op").asText();
						String path = change.get("path").asText();
						String key = operation + "*" + path;
						if (key.equals(_changeToPerform)) {
							row.setCell(_cellIndex, new Cell((Serializable) change.get("value"), null));
						}
					}
					System.out.println(syncState.getChanges());

					return false;
				}
			}.init();
		}

		@Override
		protected Runnable getRunnable() {
			return this;
		}
	}

}
