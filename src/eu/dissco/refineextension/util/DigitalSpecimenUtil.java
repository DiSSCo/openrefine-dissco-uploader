
package eu.dissco.refineextension.util;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeType;
import com.google.refine.model.Row;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

public class DigitalSpecimenUtil {

  public static JsonObject rowToJsonObject(Row row, JsonNode columnMappingNode,
      String digitalSpecimenId, boolean excludeId) {
    JsonObject subSectionObject = new JsonObject();
    Iterator<Map.Entry<String, JsonNode>> subSectionIter = columnMappingNode.fields();
    while (subSectionIter.hasNext()) {
      Map.Entry<String, JsonNode> kv = subSectionIter.next();
      String key = kv.getKey();
      JsonNode columnMappingNodeSub = kv.getValue();
      JsonNodeType nodeType = columnMappingNodeSub.getNodeType();
      switch (nodeType) {
        case OBJECT:
          subSectionObject.add(key,
              rowToJsonObject(row, columnMappingNodeSub, digitalSpecimenId, excludeId));
          break;
        case ARRAY:
          JsonArray array = new JsonArray();
          Iterator<JsonNode> items = columnMappingNodeSub.elements();
          while (items.hasNext()) {
            JsonNode columnMappingNodeSubArray = items.next();
            array
                .add(rowToJsonObject(row, columnMappingNodeSubArray, digitalSpecimenId, excludeId));
          }
          subSectionObject.add(key, array);
          break;
        default:
          // then it is an item to map, i.e. should be the index of a column or null
          // To-do: handle case if it is other datatype
          JsonNode colIndexNode = kv.getValue();
          if (!colIndexNode.isNull() && colIndexNode.isInt()) {
            int colIndex = colIndexNode.asInt();
            Object cellValue = row.getCellValue(colIndex);
            if (key == "doi") {
              digitalSpecimenId = (String) cellValue;
              // since the ID is handled by Digital Objects outside of
              // the content section
              // we sometimes want to exclude it
              if (excludeId) {
                continue;
              }
              // else rename the "doi" field into "id"
              key = "id";
            }
            if (key == "mediatype") {
              key = "@type";
            }
            if (cellValue instanceof Integer) {
              subSectionObject.addProperty(key, (int) row.getCellValue(colIndex));
            } else if (cellValue instanceof String) {
              subSectionObject.addProperty(key, (String) row.getCellValue(colIndex));
            }
          }
          break;
      }

      // }
    }
    if (columnMappingNode.has("ods:authoritative")) {
      // then it is the DigitalSpecimen json content
      subSectionObject.add("@context", createDefaultContext());
      subSectionObject.addProperty("@type", "ods:DigitalSpecimen");
    }
    if (columnMappingNode.has("ods:mediaObjects")) {
      // then it is the MediaCollection json content
      subSectionObject.add("@context", createDefaultContext());
      subSectionObject.addProperty("@type", "ods:MediaCollection");
      if (!digitalSpecimenId.isEmpty()) {
        subSectionObject.addProperty("ods:digitalSpecimen", digitalSpecimenId);
      }
      // We only create mediaObjects if they are not empty and any of their attributes is non-
      // empty. Otherwise, even though a mapping exists, do not create a mediaObject in the array.
      // Important to inform
      // the user that in this case an empty mediaObject cannot come before a
      // non-empty one in a row.
      JsonArray mediaObjects = subSectionObject.get("ods:mediaObjects").getAsJsonArray();
      List<Integer> deleteIdxs = new ArrayList<Integer>();
      int i = 0;
      Iterator<JsonElement> iter = mediaObjects.iterator();
      while (iter.hasNext()) {
        JsonObject mediaObject = iter.next().getAsJsonObject();
        boolean isEmpty = true;
        Set<Map.Entry<String, JsonElement>> fields = mediaObject.entrySet();
        Iterator<Map.Entry<String, JsonElement>> objectIter = fields.iterator();
        while (objectIter.hasNext()) {
          Map.Entry<String, JsonElement> attribute = objectIter.next();
          JsonElement value = attribute.getValue();
          if (value.isJsonPrimitive()) {
            try {
              String valueString = value.getAsString();
              if (!valueString.isEmpty()) {
                isEmpty = false;
                break;
              }
            } catch (ClassCastException e) {
              System.out.println("ClassCastException");
              if (value != null) {
                isEmpty = false;
                break;
              }
            }
          } else {
            if (!value.isJsonNull()) {
              isEmpty = false;
              break;
            }
          }
        }
        if (isEmpty) {
          deleteIdxs.add(0, i);
        }
        i += 1;
      }
      System.out.println("will remove the following indices:");
      System.out.println(deleteIdxs);
      for (int j : deleteIdxs) {
        mediaObjects.remove(j);
      }
    }
    return subSectionObject;
  }

  public static JsonObject rowToJsonObject(Row row, JsonNode columnMappingSection) {
    return rowToJsonObject(row, columnMappingSection, "", false);
  }

  public static JsonArray createDefaultContext() {
    JsonObject odsObject = new JsonObject();
    odsObject.addProperty("ods", "http://github.com/DiSSCo/openDS/ods-ontology/terms/");
    JsonArray contextArray = new JsonArray();
    contextArray.add(odsObject);
    return contextArray;
  }
}
