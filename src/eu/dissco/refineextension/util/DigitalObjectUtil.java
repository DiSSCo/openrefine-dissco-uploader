
package eu.dissco.refineextension.util;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import com.fasterxml.jackson.databind.JsonNode;
import com.google.refine.model.Row;
import eu.dissco.refineextension.schema.DigitalObject;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
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
          JsonObject relatedDigitalObject = rowToJsonObject(row, columnMappingNodeInner, includeTypeAndId);
          if(relatedDigitalObject != null) {
            subSectionObject.add(key, relatedDigitalObject);
          }
          break;
        case "arrayAttribute":
          JsonArray array = new JsonArray();
          Iterator<JsonNode> items = columnMappingNodeInner.get("values").elements();
          while (items.hasNext()) {
            JsonNode columnMappingNodeArrayItem = items.next();
            if(columnMappingNodeArrayItem.get("mappingType").asText().equals("attribute")) {
              addAttributeFromRow(columnMappingNodeArrayItem, null, row, array);
            } else {
              JsonObject subObject = rowToJsonObject(row, columnMappingNodeArrayItem, includeTypeAndId);
              if(subObject != null) {
                array.add(subObject);
              }
            }
          }
          subSectionObject.add(key, array);
          break;
      }
    }

    if(mappingType.equals("digitalObject")) {
      if(subSectionObject.size() == 0) {
        // then it has no content, only type & id
        return null;
      }
      if (includeTypeAndId) {
        digitalObject.add("content", subSectionObject);
        subSectionObject = digitalObject;
      } else {
        subSectionObject.addProperty("type", columnMappingNode.get("digitalObjectType").asText());
        String key = "id";
        JsonNode columnMappingNodeInner = columnMappingNode.get(key);
        addAttributeFromRow(columnMappingNodeInner, key, row, subSectionObject);
      }
    }
    return subSectionObject;
  }

  public static void addAttributeFromRow(JsonNode columnMappingNode, String key, Row row,
      JsonElement jElement) {
    JsonNode colIndexNode = columnMappingNode.get("mapping");
    if (colIndexNode == null || colIndexNode.isNull()) {
      JsonNode defaultValueNode = columnMappingNode.get("default");
      if (defaultValueNode != null) {
        if (defaultValueNode.isNumber()) {
          addAttributeToJsonElement(defaultValueNode.asDouble(), jElement, key);
        } else if (defaultValueNode.isTextual()) {
          String defaultValue = defaultValueNode.asText();
          if (defaultValue.length() > 0) {
            addAttributeToJsonElement(defaultValue, jElement, key);
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
          addAttributeToJsonElement(locallyScopedId, jElement, key);
        } else {
          // To-do: handle case if it is other datatype
          addAttributeToJsonElement(cellValue, jElement, key);
        }
      }
    }
  }
  
  public static void addAttributeToJsonElement(Object value, JsonElement jElement, String key) {
    if(jElement.isJsonObject()) {
      JsonObject jobject = jElement.getAsJsonObject();
      if (value instanceof Integer) {
        jobject.addProperty(key, (int) value);
      } else if (value instanceof String) {
        jobject.addProperty(key, (String) value);
      } else if (value instanceof Float) {
        jobject.addProperty(key, (Float) value);
      } else if (value == null) {
        // do not add the attribute unless it is for the ID column which might be null before creation
        if(key.equals("id")) {
          jobject.add(key, JsonNull.INSTANCE);
        }
      }
    } else if(jElement.isJsonArray()) {
      JsonArray array = jElement.getAsJsonArray();
      if (value instanceof Integer) {
        array.add((int) value);
      } else if (value instanceof String) {
        array.add((String) value);
      } else if (value instanceof Float) {
        array.add((Float) value);
      }
        // do not add the attribute when it is null
    }
  }
}
