
package eu.dissco.refineextension.processing;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.UUID;

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
import net.cnri.cordra.api.SearchResults;

public class DigitalSpecimenProcessor {

  private NsidrClient nsidrClient;
  private JsonNode columnMapping;


  public DigitalSpecimenProcessor(String authToken, JsonNode columnMapping) {
    this.nsidrClient = new NsidrClient(authToken);
    this.columnMapping = columnMapping;
  }
  
  private void digitalObjectPostProcessing(CordraObject co, List<String> jsonPathAsList) {
    
  }

  private int getMappingByJsonPathAsList(List<String> jsonPathAsList) {
    JsonNode columnMappingNode = this.columnMapping;
    Iterator<String> iter = jsonPathAsList.iterator();
    while (iter.hasNext()) {
      if (columnMappingNode.has("values")) {
        columnMappingNode = columnMappingNode.get("values");
      }
      String value = iter.next();
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
    System.out.println("getDigitalObjectDataDiff started!!!" + contentRemote + contentToUpload);
    ObjectMapper mapper = new ObjectMapper();
    // we need to convert gson JsonObject to jackson JsonNode
    // the resulting diffs will be a patch to the remote content (in order
    // to be the same as the upload content)
    JsonNode target = mapper.readTree(contentToUpload.toString());
    JsonNode source = mapper.readTree(contentRemote.toString());
    final JsonNode patchNode = JsonDiff.asJson(source, target);
    if (patchNode.size() > 0) {
      System.out.println("Found json diff:");
      System.out.println(patchNode.toString());
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
    boolean isArray = rowToObject.isJsonArray();
    if (!isArray) {
      JsonObject ob = rowToObject.getAsJsonObject();
      id = ob.get("id");
      type = ob.get("type");
      contentEl = ob.get("content");
      if (!(id != null && type != null && contentEl != null)) {
        Iterator<Map.Entry<String, JsonElement>> iter = ob.entrySet().iterator();
        while (iter.hasNext()) {
          Map.Entry<String, JsonElement> keyValue = iter.next();

          List<String> jsonPathAsListCopy = new ArrayList<String>();
          for (String item : jsonPathAsList)
            jsonPathAsListCopy.add(item);
          jsonPathAsListCopy.add(keyValue.getKey());

          CordraObject innerObject =
              createDigitalObjectsRecursive(keyValue.getValue(), row, jsonPathAsListCopy);
          if (innerObject != null) {
            ob.addProperty(keyValue.getKey(), innerObject.id);
          }
        }
      } else {
        CordraObject objectToCreate = new CordraObject();
        if (id.isJsonNull()) {
          objectToCreate.id = "";
        } else {
          objectToCreate.id = id.getAsString();
        }
        objectToCreate.type = type.getAsString();
        JsonObject content = contentEl.getAsJsonObject();
        Iterator<Map.Entry<String, JsonElement>> iter = content.entrySet().iterator();
        while (iter.hasNext()) {
          Map.Entry<String, JsonElement> keyValue = iter.next();

          List<String> jsonPathAsListCopy = new ArrayList<String>();
          for (String item : jsonPathAsList)
            jsonPathAsListCopy.add(item);
          jsonPathAsListCopy.add(keyValue.getKey());

          CordraObject innerObject =
              createDigitalObjectsRecursive(keyValue.getValue(), row, jsonPathAsListCopy);
          if (innerObject != null) {
            content.addProperty(keyValue.getKey(), innerObject.id);
          }
        }
        objectToCreate.content = content;
        CordraObject createdDO = this.nsidrClient.create(objectToCreate);
        int idColumnIndex = getMappingByJsonPathAsList(jsonPathAsList);
        row.setCell(idColumnIndex, new Cell(createdDO.id, null));
        return createdDO;
      }
    } else {
      JsonArray ar = rowToObject.getAsJsonArray();
      ListIterator<JsonElement> iter = (ListIterator<JsonElement>) ar.iterator();
      while (iter.hasNext()) {
        int index = iter.nextIndex();

        List<String> jsonPathAsListCopy = new ArrayList<String>();
        for (String item : jsonPathAsList)
          jsonPathAsListCopy.add(item);
        jsonPathAsListCopy.add(String.valueOf(index));

        CordraObject innerObject =
            createDigitalObjectsRecursive(iter.next(), row, jsonPathAsListCopy);
        if (innerObject != null) {
          ar.set(index, new JsonPrimitive(innerObject.id));
        }
      }
    }
    return null;
  }

  public JsonObject getDeserializedDigitalObjectContent(String id) {
    JsonObject content = null;
    if (id.length() > 0) {
      try {
        JsonElement response = this.nsidrClient.performDoipOperationGetRest(id, "getDeserializedContent");
        content = response.getAsJsonObject();
      } catch (CordraException e1) {
        if (e1.getResponseCode() == 401) {
          // if the getDeserializedContent is not implemented get the digital object normally
          try {
            CordraObject co = this.nsidrClient.get(id);
            content = co.content.getAsJsonObject();
          } catch (CordraException e2) {
            System.out.println(
                "an CordraException was thrown in retrieve" + e2.getMessage());
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
    boolean isArray = rowToObject.isJsonArray();
    if (!isArray) {
      JsonObject ob = rowToObject.getAsJsonObject();
      id = ob.get("id");
      type = ob.get("type");
      contentEl = ob.get("content");
      if (!(id != null && type != null && contentEl != null)) {
        Iterator<Map.Entry<String, JsonElement>> iter = ob.entrySet().iterator();
        while (iter.hasNext()) {
          Map.Entry<String, JsonElement> keyValue = iter.next();

          List<String> jsonPathAsListCopy = new ArrayList<String>();
          for (String item : jsonPathAsList)
            jsonPathAsListCopy.add(item);
          jsonPathAsListCopy.add(keyValue.getKey());

          CordraObject innerObject =
              updateDigitalObjectsRecursive(keyValue.getValue(), row, jsonPathAsListCopy);
          if (innerObject != null) {
            ob.addProperty(keyValue.getKey(), innerObject.id);
          }
        }
      } else {
        CordraObject objectToUpdate = new CordraObject();
        objectToUpdate.id = id.getAsString();
        objectToUpdate.type = type.getAsString();
        JsonObject content = contentEl.getAsJsonObject();
        Iterator<Map.Entry<String, JsonElement>> iter = content.entrySet().iterator();
        while (iter.hasNext()) {
          Map.Entry<String, JsonElement> keyValue = iter.next();

          List<String> jsonPathAsListCopy = new ArrayList<String>();
          for (String item : jsonPathAsList)
            jsonPathAsListCopy.add(item);
          jsonPathAsListCopy.add(keyValue.getKey());

          CordraObject innerObject =
              updateDigitalObjectsRecursive(keyValue.getValue(), row, jsonPathAsListCopy);
          if (innerObject != null) {
            content.addProperty(keyValue.getKey(), innerObject.id);
          }
        }
        objectToUpdate.content = content;
        CordraObject updatedDO = this.nsidrClient.update(objectToUpdate);
        return updatedDO;
      }
    } else {
      JsonArray ar = rowToObject.getAsJsonArray();
      Iterator<JsonElement> iter = ar.iterator();
      int index = 0;
      while (iter.hasNext()) {

        List<String> jsonPathAsListCopy = new ArrayList<String>();
        for (String item : jsonPathAsList)
          jsonPathAsListCopy.add(item);
        jsonPathAsListCopy.add(String.valueOf(index));

        CordraObject innerObject =
            updateDigitalObjectsRecursive(iter.next(), row, jsonPathAsListCopy);
        if (innerObject != null) {
          ar.set(index, new JsonPrimitive(innerObject.id));
        }
        index += 1;
      }
    }
    return null;
  }
}
