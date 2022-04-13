
package eu.dissco.refineextension.processing;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.fge.jsonpatch.diff.JsonDiff;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.google.refine.model.Cell;
import com.google.refine.model.Row;
import net.cnri.cordra.api.CordraException;
import net.cnri.cordra.api.CordraObject;
import net.cnri.cordra.api.Payload;

public class DigitalObjectProcessor {
  final static Logger logger = LoggerFactory.getLogger("DigitalObjectProcessor");
  private CordraClient nsidrClient;
  private JsonNode columnMapping;
  private Map<Integer, List<String>> colIndicesToModify;


  public DigitalObjectProcessor(String authToken, String cordraUrl, JsonNode columnMapping,
      Map<Integer, List<String>> colIndicesToModify) {
    this.nsidrClient = new CordraClient(cordraUrl, authToken);
    this.columnMapping = columnMapping;
    this.colIndicesToModify = colIndicesToModify;
  }

  private void digitalObjectPostProcessing(CordraObject co, Row row,
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
      throws CordraException, IOException {
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

  public CordraObject createDigitalObjectsRecursive(JsonElement rowToObject, Row row,
      List<String> jsonPathAsList) throws CordraException {
    // To-Do: refactoring required to reduce code duplication
    if (rowToObject.isJsonPrimitive()) {
      return null;
    }
    JsonElement id, type, contentEl;
    if (rowToObject.isJsonObject()) {
      JsonObject ob = rowToObject.getAsJsonObject();
      id = ob.get("id");
      type = ob.get("type");
      contentEl = ob.get("content");
      if (!(id != null && type != null && contentEl != null)) {
        processUploadObjectPart(ob, jsonPathAsList, row, "create");
      } else {
        CordraObject objectToCreate = new CordraObject();
        if (id.isJsonNull()) {
          objectToCreate.id = "";
        } else {
          objectToCreate.id = id.getAsString();
        }
        objectToCreate.type = type.getAsString();
        if (ob.has("payloads")) {
          JsonArray payloads = ob.get("payloads").getAsJsonArray();
          Iterator<JsonElement> payloadsIter = payloads.iterator();
          while (payloadsIter.hasNext()) {
            JsonObject payloadOb = payloadsIter.next().getAsJsonObject();
            Payload payload = new Payload();
            try {
              String name = payloadOb.get("name").getAsString();
              InputStream in = new FileInputStream(payloadOb.get("path").getAsString());
              payload.setInputStream(in);
              String filename = name;
              String mediaType = null;
              if (payloadOb.has("filename")) {
                filename = payloadOb.get("filename").getAsString();
              }
              if (payloadOb.has("mediaType")) {
                mediaType = payloadOb.get("mediaType").getAsString();
              }
              objectToCreate.addPayload(name, filename, mediaType, in);
            } catch (FileNotFoundException e) {
              e.printStackTrace();
            }
          }
        }
        JsonObject content = contentEl.getAsJsonObject();
        processUploadObjectPart(content, jsonPathAsList, row, "create");
        objectToCreate.content = content;
        CordraObject createdDO = this.nsidrClient.create(objectToCreate);
        int idColumnIndex = getIdMappingByJsonPathAsList(jsonPathAsList);
        row.setCell(idColumnIndex, new Cell(createdDO.id, null));
        digitalObjectPostProcessing(createdDO, row, jsonPathAsList);
        return createdDO;
      }
    } else if (rowToObject.isJsonArray()) {
      uploadProcessArrayPart(rowToObject, jsonPathAsList, row, "create");
    }
    return null;
  }

  public JsonObject getDeserializedDigitalObjectContent(String id) {
    JsonObject content = null;
    if (id != null && id.length() > 0) {
      try {
        JsonElement response = this.nsidrClient.performDoipOperationGetRest(id, "getDeserialized");
        content = response.getAsJsonObject();
      } catch (CordraException e1) {
        int code = e1.getResponseCode();
        if (code == 401 || code == 404) {
          // if the getDeserialized is not implemented get the digital object normally
          try {
            CordraObject co = this.nsidrClient.get(id);
            if(co != null) {
              content = co.content.getAsJsonObject();
            }
          } catch (CordraException e2) {
            logger.error("An CordraException was thrown in retrieve" + e2.getMessage());
            e2.printStackTrace();
          }
        }
      }
    }
    return content;
  }

  public CordraObject updateDigitalObjectsRecursive(JsonElement rowToObject, Row row,
      List<String> jsonPathAsList) throws CordraException {
    // To-Do: refactoring required to reduce code duplication
    if (rowToObject.isJsonPrimitive()) {
      return null;
    }
    JsonElement id, type, contentEl;
    if (rowToObject.isJsonObject()) {
      JsonObject ob = rowToObject.getAsJsonObject();
      id = ob.get("id");
      type = ob.get("type");
      contentEl = ob.get("content");
      if (!(id != null && type != null && contentEl != null)) {
        processUploadObjectPart(ob, jsonPathAsList, row, "update");
      } else {
        CordraObject objectToUpdate = new CordraObject();
        objectToUpdate.id = id.getAsString();
        objectToUpdate.type = type.getAsString();
        JsonObject content = contentEl.getAsJsonObject();
        processUploadObjectPart(content, jsonPathAsList, row, "update");
        objectToUpdate.content = content;
        CordraObject updatedDO = this.nsidrClient.update(objectToUpdate);
        digitalObjectPostProcessing(updatedDO, row, jsonPathAsList);
        return updatedDO;
      }
    } else if (rowToObject.isJsonArray()) {
      uploadProcessArrayPart(rowToObject, jsonPathAsList, row, "update");
    }
    return null;
  }

  private void processUploadObjectPart(JsonObject objectPart, List<String> jsonPathAsList, Row row,
      String uploadAction) throws CordraException {
    Iterator<Map.Entry<String, JsonElement>> iter = objectPart.entrySet().iterator();
    while (iter.hasNext()) {
      Map.Entry<String, JsonElement> keyValue = iter.next();

      List<String> jsonPathAsListCopy = new ArrayList<String>();
      for (String item : jsonPathAsList)
        jsonPathAsListCopy.add(item);
      jsonPathAsListCopy.add(keyValue.getKey());

      CordraObject innerObject = null;
      if (uploadAction.equals("create")) {
        innerObject = createDigitalObjectsRecursive(keyValue.getValue(), row, jsonPathAsListCopy);
      } else if (uploadAction.equals("update")) {
        innerObject = updateDigitalObjectsRecursive(keyValue.getValue(), row, jsonPathAsListCopy);
      }
      if (innerObject != null) {
        objectPart.addProperty(keyValue.getKey(), innerObject.id);
      }
    }
  }

  private void uploadProcessArrayPart(JsonElement objectPart, List<String> jsonPathAsList, Row row,
      String uploadAction) throws CordraException {
    JsonArray ar = objectPart.getAsJsonArray();
    Iterator<JsonElement> iter = ar.iterator();
    int index = 0;
    while (iter.hasNext()) {
      List<String> jsonPathAsListCopy = this.shallowCloneList(jsonPathAsList);
      jsonPathAsListCopy.add(String.valueOf(index));

      CordraObject innerObject = null;
      if (uploadAction.equals("create")) {
        innerObject = createDigitalObjectsRecursive(iter.next(), row, jsonPathAsListCopy);
      } else if (uploadAction.equals("update")) {
        innerObject = updateDigitalObjectsRecursive(iter.next(), row, jsonPathAsListCopy);
      }
      if (innerObject != null) {
        ar.set(index, new JsonPrimitive(innerObject.id));
      }
      index += 1;
    }
  }

  private List<String> shallowCloneList(List<String> l) {
    List<String> clone = new ArrayList<String>();
    for (String item : l)
      clone.add(item);
    return clone;
  }
}
