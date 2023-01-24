package eu.dissco.refineextension.commands;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.fge.jackson.jsonpointer.JsonPointer;
import com.github.fge.jackson.jsonpointer.JsonPointerException;
import com.google.refine.browsing.Engine;
import com.google.refine.commands.Command;
import com.google.refine.model.Cell;
import com.google.refine.model.Project;
import com.google.refine.model.Row;

import com.google.refine.browsing.RowVisitor;
import com.google.refine.browsing.FilteredRows;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
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
import eu.dissco.refineextension.schema.DigitalObject;
import eu.dissco.refineextension.schema.DisscoUploadSchema;
import eu.dissco.refineextension.util.DigitalObjectUtil;

public class PrepareForSynchronizationCommand extends Command {
	static final Logger logger = LoggerFactory.getLogger(PrepareForSynchronizationCommand.class);
	static final List<String> changePathsCannotBeOverwritten = new ArrayList<String>(
			Arrays.asList("/created", "/digitalMediaObjects", "/midsLevel", "/version"));

	@Override
	public void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		try {
			String authToken = request.getParameter("token");
			if (authToken == null) {
				respond(response, "{ \"code\" : \"error\", \"message\" : \"No authentication token sent\" }");
				return;
			}
			Project project = getProject(request);
			Engine engine = getEngine(request, project);
			DisscoUploadSchema savedSchema = (DisscoUploadSchema) project.overlayModels
					.get(DisscoUploadSchema.overlayModelKey);
			String specimenUrl = savedSchema.getSpecimenServerUrl();
			// To-Do check that schema has ColumnMapping
			JsonNode columnMapping = savedSchema.getColumnMapping();
			Map<Integer, SyncState> syncStatusForRows = new HashMap<Integer, SyncState>();
			Map<String, Integer> comparisonSummary = new HashMap<String, Integer>();
			response.setCharacterEncoding("UTF-8");
			response.setHeader("Content-Type", "application/json");
			FilteredRows filteredRows = engine.getAllFilteredRows();
			filteredRows.accept(project,
					new MyRowVisitor(columnMapping, syncStatusForRows, comparisonSummary, authToken, specimenUrl));

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
			if ((id == null || id.equals("null") || id.length() == 0)
					&& this.columnMapping.get("values").has("physicalSpecimenId")) {
				JsonNode physicalSpecimenIdNode = this.columnMapping.get("values").get("physicalSpecimenId");
				if (physicalSpecimenIdNode.has("mapping")) {
					int physicalSpecimenIdColIndex = physicalSpecimenIdNode.get("mapping").asInt();
					String physicalSpecimenId = (String) row.getCellValue(physicalSpecimenIdColIndex);
					String foundHandle = processorClient.searchHandleByPhysicalSpecimenId(physicalSpecimenId);
					if (foundHandle.length() > 0) {
						System.out.println("will set cell");
						row.setCell(idColIndex, new Cell(foundHandle, null));
						id = foundHandle;
					}
				}
			}
			if (id != null && !id.equals("null") && id.length() > 0) {
				// digitalObjectContent =
				// processorClient.getDeserializedDigitalObjectContent(id);
				digitalObjectContent = processorClient.getSpecimen(id).content.getAsJsonObject();
				if (digitalObjectContent != null) {
					JsonObject doContentToUpload = DigitalObjectUtil.rowToJsonObject(row, this.columnMapping, false);
					ArrayNode differencesPatch = null;
					try {
						differencesPatch = processorClient.getDigitalObjectDataDiff(digitalObjectContent,
								doContentToUpload);
					} catch (IOException e) {
						logger.error("json processing exception!");
						e.printStackTrace();
						rowSyncState = new SyncState("error",
								"Failure during comparison of the local data with the remote Digital Specimen to update. See logs.");
					}
					if (differencesPatch != null && differencesPatch.size() > 0) {
						boolean atLeastOneOverwritableChange = false;
						for (int i = 0; i < differencesPatch.size(); i++) {
							JsonNode jsonOperation = differencesPatch.get(i);
							String operation = jsonOperation.get("op").asText();
							String path = jsonOperation.get("path").asText();
							if (!changePathsCannotBeOverwritten.contains(path)) {
								atLeastOneOverwritableChange = true;
							}
							// the "copy" operation is harder to handle, so replace it by an add operation
							ObjectMapper mapper = null;
							if (operation.equals("copy")) {
								if (mapper == null) {
									mapper = new ObjectMapper();
								}
								String fromPath = jsonOperation.get("from").asText();
								JsonPointer ptr;
								try {
									ptr = new JsonPointer(fromPath);

									JsonNode remoteContentAsJsonNode = mapper.readTree(digitalObjectContent.toString());
									final JsonNode value = ptr.get(remoteContentAsJsonNode);
									if (value != null) {
										operation = "add";
										ObjectNode jsonOperationNew = (ObjectNode) jsonOperation;
										jsonOperationNew.put("op", operation);
										if (value.isTextual()) {
											jsonOperationNew.put("value", value.textValue());
										} else if (value.isNumber()) {
											if (value.isBigInteger() || value.isInt()) {
												jsonOperationNew.put("value", value.asLong());
											} else {
												jsonOperationNew.put("value", value.asDouble());
											}
										} else {
											// TODO: throw error/ handle this case
										}
										jsonOperationNew.remove("from");
										differencesPatch.set(i, jsonOperationNew);
									} else {
										// TODO: throw error
									}
								} catch (JsonPointerException | JsonProcessingException e) {
									e.printStackTrace();
									this.syncStatusForRows.put(rowIndex,
											new SyncState("error", "Could not handle json patch 'copy' operation"));
									return false;
								}
							}
							// key is a combination of the operation and the path
							String changeKey = operation + "*" + path;
							this.comparisonSummary.merge(changeKey, 1, Integer::sum);
						}
						if (atLeastOneOverwritableChange) {
							// JSONs are not equal, Object will be updated
							rowSyncState = new SyncState("change", "", differencesPatch);
						} else {
							rowSyncState = new SyncState("synchronized", "", differencesPatch);
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
