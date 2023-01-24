package eu.dissco.refineextension.processing;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.github.fge.jsonpatch.diff.JsonDiff;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.google.refine.model.Cell;
import com.google.refine.model.Row;

import eu.dissco.refineextension.schema.DigitalObject;
import eu.dissco.refineextension.util.DigitalObjectUtil;

public class SpecimenProcessor {
  static final Logger logger = LoggerFactory.getLogger(SpecimenProcessor.class);
  protected SpecimenPostClient specimenPostClient;
  protected SpecimenGetClient specimenGetClient;
  public JsonNode columnMapping;
  private String disscoSpecimenGetEndpoint;

  public SpecimenProcessor(String authToken, JsonNode columnMapping,
      String disscoSpecimenGetEndpoint) {
    this.specimenPostClient = new SpecimenPostClient(authToken);
    this.disscoSpecimenGetEndpoint = disscoSpecimenGetEndpoint;
    this.specimenGetClient = new SpecimenGetClient(authToken, disscoSpecimenGetEndpoint);
    this.columnMapping = columnMapping;
  }

  public DigitalObject createDigitalObjectsRecursive(JsonElement rowToObject, Row row,
      List<String> jsonPathAsList, List<DependentObjectTuple> dependentObjects) {
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
        processUploadObjectPart(ob, jsonPathAsList, row, "create", dependentObjects);
      } else {
        DigitalObject objectToCreate = new DigitalObject();
        if (id.isJsonNull()) {
          objectToCreate.id = "";
        } else {
          objectToCreate.id = id.getAsString();
        }
        objectToCreate.type = type.getAsString();
        if (ob.has("payloads")) {

        }
        JsonObject content = contentEl.getAsJsonObject();
        processUploadObjectPart(content, jsonPathAsList, row, "create", dependentObjects);
        objectToCreate.content = content;
        DigitalObject createdDO = this.specimenPostClient.upsert(objectToCreate);
        int idColumnIndex = getIdMappingByJsonPathAsList(jsonPathAsList);
        row.setCell(idColumnIndex, new Cell(createdDO.id, null));
        createDependentObjects(createdDO.id, dependentObjects, row);
        return createdDO;
      }
    } else if (rowToObject.isJsonArray()) {
      uploadProcessArrayPart(rowToObject, jsonPathAsList, row, "create", dependentObjects);
    }
    return null;
  }

  private void processUploadObjectPart(JsonObject objectPart, List<String> jsonPathAsList, Row row,
      String uploadAction, List<DependentObjectTuple> dependentObjects) {
    Iterator<Map.Entry<String, JsonElement>> iter = objectPart.entrySet().iterator();
    List<String> keysToRemove = new ArrayList<String>();
    while (iter.hasNext()) {
      Map.Entry<String, JsonElement> keyValue = iter.next();
      JsonElement part = keyValue.getValue();

      List<String> jsonPathAsListCopy = this.shallowCloneList(jsonPathAsList);
      jsonPathAsListCopy.add(keyValue.getKey());

      if (part.isJsonObject()) {
        JsonObject ob = part.getAsJsonObject();
        JsonElement id = ob.get("id");
        JsonElement type = ob.get("type");
        JsonElement contentEl = ob.get("content");
        if (!(id != null && type != null && contentEl != null)) {
          processUploadObjectPart(ob, jsonPathAsListCopy, row, "create", dependentObjects);
        } else {
          dependentObjects.add(new DependentObjectTuple(jsonPathAsListCopy, ob.getAsJsonObject()));
          keysToRemove.add(keyValue.getKey());
        }
      } else if (part.isJsonArray()) {
        uploadProcessArrayPart(part, jsonPathAsListCopy, row, "create", dependentObjects);
      }
    }
    Iterator<String> keysIter = keysToRemove.iterator();
    while (keysIter.hasNext()) {
      objectPart.remove(keysIter.next());
    }
  }

  private void uploadProcessArrayPart(JsonElement objectPart, List<String> jsonPathAsList, Row row,
      String uploadAction, List<DependentObjectTuple> dependentObjects) {
    JsonArray ar = objectPart.getAsJsonArray();
    Iterator<JsonElement> iter = ar.iterator();
    int index = 0;
    List<Integer> iDsToRemove = new ArrayList<Integer>();
    while (iter.hasNext()) {
      List<String> jsonPathAsListCopy = this.shallowCloneList(jsonPathAsList);
      jsonPathAsListCopy.add(String.valueOf(index));
      JsonElement part = iter.next();
      if (part.isJsonObject()) {
        JsonObject ob = part.getAsJsonObject();
        JsonElement id = ob.get("id");
        JsonElement type = ob.get("type");
        JsonElement contentEl = ob.get("content");
        if (!(id != null && type != null && contentEl != null)) {
          processUploadObjectPart(ob, jsonPathAsListCopy, row, "create", dependentObjects);
        } else {
          dependentObjects.add(new DependentObjectTuple(jsonPathAsListCopy, ob.getAsJsonObject()));
          iDsToRemove.add(index);
        }
      } else if (part.isJsonArray()) {
        uploadProcessArrayPart(part, jsonPathAsListCopy, row, "create", dependentObjects);
      }
      index += 1;
    }
    Iterator<Integer> iDsIter = iDsToRemove.iterator();
    while (iDsIter.hasNext()) {
      ar.remove(iDsIter.next());
    }
  }

  public DigitalObject updateDigitalObjectsRecursive(JsonElement rowToObject, Row row,
      List<String> jsonPathAsList, List<DependentObjectTuple> dependentObjects) {
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
        processUploadObjectPart(ob, jsonPathAsList, row, "update", dependentObjects);
      } else {
        if (id.isJsonNull() || (id.isJsonPrimitive() && id.getAsString().length() == 0)) {
          return createDigitalObjectsRecursive(rowToObject, row, jsonPathAsList, dependentObjects);
        } else {
          DigitalObject objectToUpdate = new DigitalObject();
          objectToUpdate.id = id.getAsString();
          objectToUpdate.type = type.getAsString();
          JsonObject content = contentEl.getAsJsonObject();
          processUploadObjectPart(content, jsonPathAsList, row, "update", dependentObjects);
          objectToUpdate.content = content;
          DigitalObject updatedDO = this.specimenPostClient.upsert(objectToUpdate);
          return updatedDO;
        }
      }
    } else if (rowToObject.isJsonArray()) {
      uploadProcessArrayPart(rowToObject, jsonPathAsList, row, "update", dependentObjects);
    }
    return null;
  }

  private void createDependentObjects(String parentId, List<DependentObjectTuple> objectList,
      Row row) {
    List<DependentObjectTuple> newDependentObjects = new ArrayList<DependentObjectTuple>();
    Iterator<DependentObjectTuple> iter = objectList.iterator();
    while (iter.hasNext()) {
      DependentObjectTuple tuple = iter.next();

      JsonNode columnMappingNode = this.columnMapping;
      Iterator<String> pathIter = tuple.dependentObjectJsonPathAsList.iterator();
      while (pathIter.hasNext()) {
        String subPath = pathIter.next();
        JsonNode values = columnMappingNode.get("values");
        // To-do: handle when path not exists
        if (columnMappingNode.get("mappingType").asText().equals("arrayAttribute")) {
          columnMappingNode = values.get(Integer.parseInt(subPath));
        } else {
          columnMappingNode = values.get(subPath);
        }
      }

      JsonObject rowToObject = DigitalObjectUtil.rowToJsonObject(row, columnMappingNode, true);
      JsonObject content = rowToObject.get("content").getAsJsonObject();
      processUploadObjectPart(content, tuple.dependentObjectJsonPathAsList, row, "create",
          newDependentObjects);
      DigitalObject objectToCreate =
          new DigitalObject(rowToObject.get("type").getAsString(), content);
      DigitalObject createdDO = this.specimenPostClient.upsert(objectToCreate);
      int idColumnIndex = getIdMappingByJsonPathAsList(tuple.dependentObjectJsonPathAsList);
      row.setCell(idColumnIndex, new Cell(createdDO.id, null));
    }
  }


  private List<String> shallowCloneList(List<String> l) {
    List<String> clone = new ArrayList<String>();
    for (String item : l)
      clone.add(item);
    return clone;
  }

  public DigitalObject getSpecimen(String id) {
    try {
      return this.specimenGetClient.get(id);
    } catch (IOException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
      return null;
    }
  }
  
  public String searchHandleByPhysicalSpecimenId(String physicalSpecimenId) {
	    try {
	      return this.specimenGetClient.searchHandleByPhysicalSpecimenId(physicalSpecimenId);
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

  public ArrayNode getDigitalObjectDataDiff(JsonObject contentRemote, JsonObject contentToUpload)
      throws IOException {
    ObjectMapper mapper = new ObjectMapper();
    // we need to convert gson JsonObject to jackson JsonNode
    // the resulting diffs will be a patch to the remote content (in order
    // to be the same as the upload content)
    if(contentToUpload.has("digitalMediaObjects")) {
      JsonArray mediaObjectsLocal = contentToUpload.get("digitalMediaObjects").getAsJsonArray();
      for(int i=0; i<mediaObjectsLocal.size(); i++) {
        JsonObject mediaObject = mediaObjectsLocal.get(i).getAsJsonObject();
        if(mediaObject.has("physicalSpecimenId")) {
          mediaObject.remove("physicalSpecimenId");
        }
        if(mediaObject.has("idType")) {
          mediaObject.remove("idType");
        }
      }
    }
    
    if(contentRemote.has("digitalMediaObjects")) {
      JsonArray mediaObjectsLocal = contentRemote.get("digitalMediaObjects").getAsJsonArray();
      for(int i=0; i<mediaObjectsLocal.size(); i++) {
        JsonObject mediaObject = mediaObjectsLocal.get(i).getAsJsonObject();
        if(mediaObject.has("digitalSpecimenId")) {
          mediaObject.remove("digitalSpecimenId");
        }
      }
    }
    JsonNode target = mapper.readTree(contentRemote.toString());
    System.out.println("localToUpload: ");
    System.out.println(contentToUpload.toString());
    System.out.println("remote: ");
    System.out.println(contentRemote.toString());
    JsonNode source = mapper.readTree(contentToUpload.toString());
    JsonNode patches = JsonDiff.asJson(source, target);
    ArrayNode patchesCleaned = mapper.createArrayNode();
    
    for (int i = 0; i < patches.size(); i++) {
      JsonNode patchNode = patches.get(i);
      if (patchNode.get("op").asText().equals("add")) {
        String path = patchNode.get("path").asText();
        JsonNode valueLocal = source.at(path);
        JsonNode valueRemote = target.at(path);
        // remove properties which have been created at server-side but value is null, like datasetId
        if ((valueRemote.isNull() && valueLocal.isMissingNode())) {
          continue;
        }
      }
      patchesCleaned.add(patchNode);
    }

    if (patches.size() > 0) {
    logger.info("Found json diff2:");
    logger.info(patchesCleaned.toString());
    }
    return patchesCleaned;
  }

  public class DependentObjectTuple {
    public final List<String> dependentObjectJsonPathAsList;
    public final JsonObject dependentObject;

    public DependentObjectTuple(List<String> dependentObjectJsonPathAsList,
        JsonObject dependentObject) {
      this.dependentObjectJsonPathAsList = dependentObjectJsonPathAsList;
      this.dependentObject = dependentObject;
    }
  }
}
