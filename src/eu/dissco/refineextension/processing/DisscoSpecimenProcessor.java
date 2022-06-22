package eu.dissco.refineextension.processing;

import java.util.List;
import java.util.Map;
import com.fasterxml.jackson.databind.JsonNode;
import com.google.gson.JsonElement;
import com.google.refine.model.Row;
import net.cnri.cordra.api.CordraException;
import net.cnri.cordra.api.CordraObject;

public class DisscoSpecimenProcessor implements DigitalObjectProcessor {
  protected DisscoSpecimenPostClient disscoSpecimenPostClient;
  public JsonNode columnMapping;
  private Map<Integer, List<String>> colIndicesToModify;

  public DisscoSpecimenProcessor(String authToken, JsonNode columnMapping,
      Map<Integer, List<String>> colIndicesToModify) {
    this.disscoSpecimenPostClient = new DisscoSpecimenPostClient(authToken);
    this.columnMapping = columnMapping;
    this.colIndicesToModify = colIndicesToModify;
  }

  @Override
  public CordraObject createDigitalObjectsRecursive(JsonElement rowToObject, Row row,
      List<String> jsonPathAsList) throws CordraException {
    return this.disscoSpecimenPostClient.create(rowToObject.getAsJsonObject());
  }

  @Override
  public CordraObject updateDigitalObjectsRecursive(JsonElement rowToObject, Row row,
      List<String> jsonPathAsList) throws CordraException {
    return this.disscoSpecimenPostClient.create(rowToObject.getAsJsonObject());
  }

}
