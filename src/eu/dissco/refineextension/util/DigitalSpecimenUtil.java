
package eu.dissco.refineextension.util;

import java.util.Iterator;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeType;
import com.google.refine.model.Row;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

public class DigitalSpecimenUtil {

    public static JsonObject rowToJsonObject(Row row, JsonNode columnMappingNode, boolean excludeId) {
        JsonObject subSectionObject = new JsonObject();
        Iterator<Map.Entry<String, JsonNode>> subSectionIter = columnMappingNode.fields();
        while (subSectionIter.hasNext()) {
            Map.Entry<String, JsonNode> kv = subSectionIter.next();
            String key = kv.getKey();
            JsonNode columnMappingNodeSub = kv.getValue();
            JsonNodeType nodeType = columnMappingNodeSub.getNodeType();
            switch (nodeType) {
            case OBJECT:
                subSectionObject.add(key, rowToJsonObject(row, columnMappingNodeSub, excludeId));
                break;
            case ARRAY:
                JsonArray array = new JsonArray();
                Iterator<JsonNode> items = columnMappingNodeSub.elements();
                while (items.hasNext()) {
                    JsonNode columnMappingNodeSubArray = items.next();
                    array.add(rowToJsonObject(row, columnMappingNodeSubArray, excludeId));
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
                        // since the ID is handled by Digital Objects outside of
                        // the content section
                        // we sometimes want to exclude it
                        if (excludeId) {
                            continue;
                        }
                        // else rename the "doi" field into "id"
                        key = "id";
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
        return subSectionObject;
    }

    public static JsonObject rowToJsonObject(Row row, JsonNode columnMappingSection) {
        return rowToJsonObject(row, columnMappingSection, false);
    }
}
