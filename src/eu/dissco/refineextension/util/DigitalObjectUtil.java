
package eu.dissco.refineextension.util;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import com.fasterxml.jackson.databind.JsonNode;
import com.google.refine.model.Row;
import com.google.gson.JsonArray;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;

public class DigitalObjectUtil {

  public static JsonObject rowToJsonObject(Row row, JsonNode columnMappingNode,
      boolean includeTypeAndId) {
    JsonObject subSectionObject = new JsonObject();
    JsonObject digitalObject = new JsonObject();
    String mappingType = columnMappingNode.get("mappingType").asText();
    if (includeTypeAndId && mappingType.equals("digitalObject")) {
      digitalObject.addProperty("type", columnMappingNode.get("digitalObjectType").asText());
      String key = "id";
      JsonNode columnMappingNodeInner = columnMappingNode.get(key);
      addAttributeFromRow(columnMappingNodeInner, key, row, digitalObject);

      if (columnMappingNode.has("payloads")) {
        JsonNode columnMappingNodePayloads = columnMappingNode.get("payloads");
        if (columnMappingNodePayloads.has("values")) {
          JsonNode columnMappingNodePayloadsInner = columnMappingNodePayloads.get("values");
          if (columnMappingNodePayloadsInner.isArray()) {
            JsonArray payloads = new JsonArray();
            Iterator<JsonNode> iter = columnMappingNodePayloadsInner.elements();
            while (iter.hasNext()) {
              JsonNode payloadMapping = iter.next();
              JsonObject payload = new JsonObject();
              columnMappingNodeInner = payloadMapping.get("values");
              for (String payloadKey : new String[] {"name", "filename", "mediaType", "path"}) {
                if (columnMappingNodeInner.has(payloadKey)) {
                  addAttributeFromRow(columnMappingNodeInner.get(payloadKey), payloadKey, row,
                      payload);
                }
              }
              payloads.add(payload);
            }
            if (payloads.size() > 0) {
              digitalObject.add("payloads", payloads);
            }
          }
        }
      }
    }
    JsonNode values = columnMappingNode.get("values");
    Iterator<Map.Entry<String, JsonNode>> subSectionIter = values.fields();
    while (subSectionIter.hasNext()) {
      Map.Entry<String, JsonNode> kv = subSectionIter.next();
      String key = kv.getKey();
      JsonNode columnMappingNodeInner = kv.getValue();
      String valueMappingType = columnMappingNodeInner.get("mappingType").asText();
      switch (valueMappingType) {
        case "attribute":
          // then it is an item to map, i.e. should be the index of a column or null
          addAttributeFromRow(columnMappingNodeInner, key, row, subSectionObject);
          break;

        case "compositeAttribute":
        case "digitalObject":
          subSectionObject.add(key, rowToJsonObject(row, columnMappingNodeInner, includeTypeAndId));
          break;
        case "arrayAttribute":
          JsonArray array = new JsonArray();
          Iterator<JsonNode> items = columnMappingNodeInner.get("values").elements();
          while (items.hasNext()) {
            JsonNode columnMappingNodeSubArray = items.next();
            array.add(rowToJsonObject(row, columnMappingNodeSubArray, includeTypeAndId));
          }
          subSectionObject.add(key, array);
          break;
      }
    }

    if (includeTypeAndId && mappingType.equals("digitalObject")) {
      digitalObject.add("content", subSectionObject);
      subSectionObject = digitalObject;
    }
    return subSectionObject;
  }

  public static void addAttributeFromRow(JsonNode columnMappingNode, String key, Row row,
      JsonObject jobject) {
    JsonNode colIndexNode = columnMappingNode.get("mapping");
    if (colIndexNode == null || colIndexNode.isNull()) {
      JsonNode defaultValueNode = columnMappingNode.get("default");
      if (defaultValueNode != null) {
        if (defaultValueNode.isNumber()) {
          jobject.addProperty(key, defaultValueNode.asDouble());
        } else if (defaultValueNode.isTextual()) {
          String defaultValue = defaultValueNode.asText();
          if (defaultValue.length() > 0) {
            jobject.addProperty(key, defaultValue);
          }
        }
      }
    } else {
      if (colIndexNode.isInt()) {
        int colIndex = colIndexNode.asInt();
        Object cellValue = row.getCellValue(colIndex);

        JsonNode generateScopedIdNode = columnMappingNode.get("generateScopedId");
        if ((generateScopedIdNode != null && generateScopedIdNode.asBoolean(false))
            && (cellValue == null
                || (cellValue instanceof String && ((String) cellValue).isEmpty()))) {
          // then we will create a locally scoped ID
          // this creates a 36 digit unique ID
          String uid = UUID.randomUUID().toString();
          // we need only a shorter ID because we have a very narrow scope
          String locallyScopedId = uid.substring(0, 16);
          // To-Do: How to make sure that the ID does not exist in the local scope of the object?
          jobject.addProperty(key, locallyScopedId);
        } else {
          // To-do: handle case if it is other datatype
          if (cellValue instanceof Integer) {
            jobject.addProperty(key, (int) row.getCellValue(colIndex));
          } else if (cellValue instanceof String) {
            jobject.addProperty(key, (String) row.getCellValue(colIndex));
          } else if (cellValue instanceof Float) {
            jobject.addProperty(key, (Float) row.getCellValue(colIndex));
          } else if (cellValue == null) {
            jobject.add(key, JsonNull.INSTANCE);
          }
        }
      }
    }
  }

  public static void setColsToModify(JsonNode columnMappingNode, List<String> jsonPathAsList,
      Map<Integer, List<String>> resultMap) {
    JsonNode values = columnMappingNode.get("values");
    Iterator<Map.Entry<String, JsonNode>> subSectionIter = values.fields();
    while (subSectionIter.hasNext()) {
      Map.Entry<String, JsonNode> kv = subSectionIter.next();
      String key = kv.getKey();
      List<String> jsonPathAsListCopy = new ArrayList<String>();
      for (String item : jsonPathAsList)
        jsonPathAsListCopy.add(item);
      jsonPathAsListCopy.add(key);
      JsonNode columnMappingNodeInner = kv.getValue();
      String valueMappingType = columnMappingNodeInner.get("mappingType").asText();
      switch (valueMappingType) {
        case "attribute":
          // then it is an item to map, i.e. should be the index of a column or null
          JsonNode colIndexNode = columnMappingNodeInner.get("mapping");
          if (colIndexNode != null && !colIndexNode.isNull() && colIndexNode.isInt()) {
            JsonNode generateScopedIdNode = columnMappingNodeInner.get("generateScopedId");
            if (generateScopedIdNode != null && generateScopedIdNode.asBoolean(false)) {
              int colIndex = colIndexNode.asInt();
              resultMap.put(colIndex, jsonPathAsListCopy);
            }
          }
          break;

        case "compositeAttribute":
        case "digitalObject":
          setColsToModify(columnMappingNodeInner, jsonPathAsListCopy, resultMap);
          break;
        case "arrayAttribute":
          JsonNode arrayValues = columnMappingNodeInner.get("values");
          Iterator<JsonNode> items = arrayValues.elements();
          int i = 0;
          while (items.hasNext()) {
            List<String> jsonPathAsListCopy2 = new ArrayList<String>();
            for (String item : jsonPathAsListCopy)
              jsonPathAsListCopy2.add(item);
            jsonPathAsListCopy2.add(String.valueOf(i));
            setColsToModify(items.next(), jsonPathAsListCopy2, resultMap);
            i += 1;
          }
          break;
      }
    }
  }
}
