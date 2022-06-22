package eu.dissco.refineextension.processing;

import java.util.List;
import com.google.gson.JsonElement;
import com.google.refine.model.Row;
import net.cnri.cordra.api.CordraException;
import net.cnri.cordra.api.CordraObject;

public interface DigitalObjectProcessor{
  public CordraObject createDigitalObjectsRecursive(JsonElement rowToObject, Row row,
      List<String> jsonPathAsList) throws CordraException ;
  
  public CordraObject updateDigitalObjectsRecursive(JsonElement rowToObject, Row row,
      List<String> jsonPathAsList) throws CordraException ;
}
