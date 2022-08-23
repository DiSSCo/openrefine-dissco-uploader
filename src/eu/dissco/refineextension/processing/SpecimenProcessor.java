package eu.dissco.refineextension.processing;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.fge.jsonpatch.diff.JsonDiff;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.google.refine.model.Cell;
import com.google.refine.model.Row;

import eu.dissco.refineextension.schema.DigitalObject;

public class SpecimenProcessor {
  static final Logger logger = LoggerFactory.getLogger(SpecimenProcessor.class);
  protected SpecimenPostClient specimenPostClient;
  protected SpecimenGetClient specimenGetClient;
  public JsonNode columnMapping;
  private Map<Integer, List<String>> colIndicesToModify;
  private String disscoSpecimenGetEndpoint;

  public SpecimenProcessor(String authToken, JsonNode columnMapping,
      Map<Integer, List<String>> colIndicesToModify, String disscoSpecimenGetEndpoint) {
    this.specimenPostClient = new SpecimenPostClient(authToken);
    this.disscoSpecimenGetEndpoint = disscoSpecimenGetEndpoint;
    this.specimenGetClient = new SpecimenGetClient(authToken, disscoSpecimenGetEndpoint);
    this.columnMapping = columnMapping;
    this.colIndicesToModify = colIndicesToModify;
  }

  public DigitalObject createDigitalObjectsRecursive(JsonElement rowToObject, Row row,
      List<String> jsonPathAsList) {
    DigitalObject createdObject = this.specimenPostClient.create(rowToObject.getAsJsonObject());
    int idColumnIndex = getIdMappingByJsonPathAsList(jsonPathAsList);
    row.setCell(idColumnIndex, new Cell(createdObject.id, null));
    System.out.println("Set cell " + String.valueOf(idColumnIndex));
    return createdObject;
  }

  public DigitalObject updateDigitalObjectsRecursive(JsonElement rowToObject, Row row,
      List<String> jsonPathAsList) {
    return this.specimenPostClient.create(rowToObject.getAsJsonObject());
  }
  
  private void digitalObjectPostProcessing(DigitalObject co, Row row,
      List<String> objectJsonPathAsList) {
    Iterator<Map.Entry<Integer, List<String>>> mapIter =
        this.colIndicesToModify.entrySet().iterator();
    while (mapIter.hasNext()) {
      JsonElement contentJsonElement = co.content;
      List<String> objectJsonPathAsListClone = this.shallowCloneList(objectJsonPathAsList);
      Map.Entry<Integer, List<String>> keyValue = mapIter.next();
      List<String> toModifyJsonPathAsList = keyValue.getValue();
      Iterator<String> pathIter = toModifyJsonPathAsList.iterator();
      boolean pathNotFound = false;
      while (pathIter.hasNext()) {
        String subPath = pathIter.next();
        if (objectJsonPathAsListClone.size() > 0) {
          if (objectJsonPathAsListClone.get(0).equals(subPath)) {
            objectJsonPathAsListClone.remove(0);
          } else {
            pathNotFound = true;
            break;
          }
        } else {
          if (contentJsonElement.isJsonArray()) {
            try {
              int idx = Integer.parseInt(subPath);
              contentJsonElement = contentJsonElement.getAsJsonArray().get(idx);
            } catch (NumberFormatException e) {
              pathNotFound = true;
              break;
            }
          } else {
            try {
              JsonObject ob = contentJsonElement.getAsJsonObject();
              if (ob.has(subPath)) {
                contentJsonElement = ob.get(subPath);
              } else {
                pathNotFound = true;
                break;
              }
            } catch (IllegalStateException e) {
              pathNotFound = true;
              break;
            }
          }
        }
      }
      if (!pathNotFound) {
        if (contentJsonElement.isJsonPrimitive()) {
          JsonPrimitive jsonValue = contentJsonElement.getAsJsonPrimitive();
          int colIndex = keyValue.getKey();
          if (jsonValue.isString()) {
            row.setCell(colIndex, new Cell(jsonValue.getAsString(), null));
          } else if (jsonValue.isNumber()) {
            row.setCell(colIndex, new Cell(jsonValue.getAsDouble(), null));
          }
        }
      }
    }
  }
  
  private List<String> shallowCloneList(List<String> l) {
    List<String> clone = new ArrayList<String>();
    for (String item : l)
      clone.add(item);
    return clone;
  }

  public DigitalObject getSpecimen(String id){
    try {
      return this.specimenGetClient.get(id);
    } catch (IOException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
      return null;
    }
  }

  private int getIdMappingByJsonPathAsList(List<String> jsonPathAsList) {
    JsonNode columnMappingNode = this.columnMapping;
    Iterator<String> iter = jsonPathAsList.iterator();
    while (iter.hasNext()) {
      String value = iter.next();
      if (value.equals("content")) {
        value = iter.next();
      }
      if (columnMappingNode.has("values")) {
        columnMappingNode = columnMappingNode.get("values");
      }
      try {
        columnMappingNode = columnMappingNode.get(Integer.parseInt(value));
      } catch (NumberFormatException e) {
        columnMappingNode = columnMappingNode.get(value);
      }
    }
    JsonNode idMappingNode = columnMappingNode.get("id").get("mapping");
    return idMappingNode.asInt();
  }

  public JsonNode getDigitalObjectDataDiff(JsonObject contentRemote, JsonObject contentToUpload)
      throws IOException {
    ObjectMapper mapper = new ObjectMapper();
    // we need to convert gson JsonObject to jackson JsonNode
    // the resulting diffs will be a patch to the remote content (in order
    // to be the same as the upload content)
    JsonNode target = mapper.readTree(contentToUpload.toString());
    JsonNode source = mapper.readTree(contentRemote.toString());
    final JsonNode patchNode = JsonDiff.asJson(source, target);
    if (patchNode.size() > 0) {
      logger.info("Found json diff:");
      logger.info(patchNode.toString());
    }
    return patchNode;
  }
  
  

}
