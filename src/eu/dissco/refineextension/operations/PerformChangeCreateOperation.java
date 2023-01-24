
package eu.dissco.refineextension.operations;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.google.refine.browsing.Engine;
import com.google.refine.browsing.EngineConfig;
import com.google.refine.browsing.FilteredRows;
import com.google.refine.browsing.RowVisitor;
import com.google.refine.history.Change;
import com.google.refine.history.HistoryEntry;
import com.google.refine.model.Cell;
import com.google.refine.model.Project;
import com.google.refine.model.Row;
import com.google.refine.model.changes.CellAtRow;
import com.google.refine.model.changes.ColumnAdditionChange;
import com.google.refine.operations.EngineDependentOperation;
import eu.dissco.refineextension.model.SyncState;
import eu.dissco.refineextension.schema.DisscoUploadSchema;

public class PerformChangeCreateOperation extends EngineDependentOperation {
	final protected String _newColumnName;
	final protected String _changeToPerform;
	final protected int _columnInsertIndex;
	final protected Map<Integer, SyncState> _syncStatusForRows;

	@JsonCreator
	public PerformChangeCreateOperation(@JsonProperty("engineConfig") EngineConfig engineConfig,
			@JsonProperty("project") Project project, @JsonProperty("changeToPerform") String changeToPerform,
			@JsonProperty("newColumnName") String newColumnName,
			@JsonProperty("columnInsertIndex") int columnInsertIndex) {
		super(engineConfig);
		_changeToPerform = changeToPerform;
		_newColumnName = newColumnName;
		_columnInsertIndex = columnInsertIndex;

		DisscoUploadSchema savedSchema = (DisscoUploadSchema) project.overlayModels
				.get(DisscoUploadSchema.overlayModelKey);
		_syncStatusForRows = savedSchema.getSyncStatusForRows();
	}

	@JsonProperty("changeToPerform")
	public String getChangeToPerform() {
		return _changeToPerform;
	}

	@JsonProperty("newColumnName")
	public String getNewColumnName() {
		return _newColumnName;
	}

	@JsonProperty("columnInsertIndex")
	public int getColumnInsertIndex() {
		return _columnInsertIndex;
	}

	@Override
	protected String getBriefDescription(Project project) {
		return "Create column " + _newColumnName + " based on remote data"
				+ " from Digital Specimen repository for rows where applicable.";
	}

	protected String createDescription(List<CellAtRow> cellsAtRows) {
		return "Create new column " + _newColumnName + " by filling " + cellsAtRows.size()
				+ " rows with data from Digital Specimen repository";
	}

	@Override
	protected HistoryEntry createHistoryEntry(Project project, long historyEntryID) throws Exception {
		Engine engine = createEngine(project);

		if (project.columnModel.getColumnByName(_newColumnName) != null) {
			throw new Exception("Another column already named " + _newColumnName);
		}

		List<CellAtRow> cellsAtRows = new ArrayList<CellAtRow>(project.rows.size());

		FilteredRows filteredRows = engine.getAllFilteredRows();
		filteredRows.accept(project, createRowVisitor(cellsAtRows));
		String description = createDescription(cellsAtRows);

		Change change = new ColumnAdditionChange(_newColumnName, _columnInsertIndex, cellsAtRows);
		System.out.println(change.toString());

		return new HistoryEntry(historyEntryID, project, description, this, change);
	}

	protected RowVisitor createRowVisitor(List<CellAtRow> cellsAtRows) throws Exception {
		return new RowVisitor() {
			List<CellAtRow> cellsAtRows;

			public RowVisitor init(List<CellAtRow> cellsAtRows) {
				this.cellsAtRows = cellsAtRows;
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
				System.out.println(_changeToPerform);
				SyncState syncState = _syncStatusForRows.get(rowIndex);
				JsonNode changes = syncState.getChanges();
				System.out.println(changes.toString());
				for (final JsonNode change : changes) {
					String operation = change.get("op").asText();
					String path = change.get("path").asText();
					String key = operation + "*" + path;
					if (key.equals(_changeToPerform)) {
						JsonNode value = change.get("value");
						Cell cell = null;
						if (value.isNumber()) {
							var numberValue = value.numberValue();
							if (numberValue instanceof Integer) {
								cell = new Cell(numberValue.intValue(), null);
							} else if (numberValue instanceof Long) {
								cell = new Cell(numberValue.longValue(), null);
							} else if (numberValue instanceof Float) {
								cell = new Cell(numberValue.floatValue(), null);
							} else if (numberValue instanceof Double) {
								cell = new Cell(numberValue.doubleValue(), null);
							} else if (numberValue instanceof BigDecimal) {
								cell = new Cell(numberValue.doubleValue(), null);
							} else {
								// else is (probably)? a BigDecimal
								System.out.println(numberValue.getClass());
								// TODO: throw error
							}
						} else if (value.isTextual()) {
							cell = new Cell(value.textValue(), null);
						}
						if (cell != null) {
							cellsAtRows.add(new CellAtRow(rowIndex, cell));
						} else {
							System.out.println("value is still null " + value.numberType());
							System.out.println(change.get("value"));

						}
					}
				}
				System.out.println(syncState.getChanges());

				return false;
			}
		}.init(cellsAtRows);
	}
}
