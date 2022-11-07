package eu.dissco.refineextension.schema;

import com.google.gson.JsonObject;

public class DigitalObject {
  public String id;
  public String type;
  public JsonObject content;
  
  public DigitalObject() {
  }
  
  public DigitalObject(String type, JsonObject content) {
    this.type = type;
    this.content = content;    
  }

}
