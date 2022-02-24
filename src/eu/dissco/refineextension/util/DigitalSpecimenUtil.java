
package eu.dissco.refineextension.util;

import java.util.Iterator;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeType;
import com.google.refine.model.Row;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

public class DigitalSpecimenUtil {

    public static JsonObject rowToJsonObject(Row row, JsonNode columnMappingNode, String digitalSpecimenId, boolean excludeId) {
        JsonObject subSectionObject = new JsonObject();
        Iterator<Map.Entry<String, JsonNode>> subSectionIter = columnMappingNode.fields();
        while (subSectionIter.hasNext()) {
            Map.Entry<String, JsonNode> kv = subSectionIter.next();
            String key = kv.getKey();
            JsonNode columnMappingNodeSub = kv.getValue();
            JsonNodeType nodeType = columnMappingNodeSub.getNodeType();
            switch (nodeType) {
            case OBJECT:
                subSectionObject.add(key, rowToJsonObject(row, columnMappingNodeSub, digitalSpecimenId, excludeId));
                break;
            case ARRAY:
                JsonArray array = new JsonArray();
                Iterator<JsonNode> items = columnMappingNodeSub.elements();
                while (items.hasNext()) {
                    JsonNode columnMappingNodeSubArray = items.next();
                    array.add(rowToJsonObject(row, columnMappingNodeSubArray, digitalSpecimenId, excludeId));
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
                    if(key == "mediatype") {
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
        if(columnMappingNode.has("ods:authoritative")) {
          // then it is the DigitalSpecimen json content
          subSectionObject.add("@context", createDefaultContext());
          subSectionObject.addProperty("@type", "ods:DigitalSpecimen");
        }
        if(columnMappingNode.has("ods:mediaObjects")) {
          // then it is the MediaCollection json content
          subSectionObject.add("@context", createDefaultContext());
          subSectionObject.addProperty("@type", "ods:MediaCollection");
          if(!digitalSpecimenId.isEmpty()) {
            subSectionObject.addProperty("ods:digitalSpecimen", digitalSpecimenId);
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
