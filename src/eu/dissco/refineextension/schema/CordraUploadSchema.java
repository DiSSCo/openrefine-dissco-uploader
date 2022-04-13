package eu.dissco.refineextension.schema;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.refine.model.OverlayModel;
import com.google.refine.model.Project;

import com.fasterxml.jackson.databind.JsonNode;

import eu.dissco.refineextension.model.SyncState;

@JsonIgnoreProperties(ignoreUnknown = true)
public class CordraUploadSchema implements OverlayModel {
  
  public static String overlayModelKey = "cordraUploadSchema";

  @JsonProperty("cordraServerUrl")
  protected String cordraServerUrl;

  @JsonProperty("authServerUrl")
  protected String authServerUrl;

  @JsonProperty("authRealm")
  protected String authRealm;

  @JsonProperty("authClientId")
  protected String authClientId;
  
  @JsonProperty("numberOfProcessingThreads")
  protected int numberOfProcessingThreads = 1;

  @JsonProperty("columnMapping")
  protected JsonNode columnMapping;

  @JsonProperty("syncStatusForRows")
  protected Map<Integer, SyncState> syncStatusForRows;

  /**
   * Constructor.
   */
  public CordraUploadSchema() {

  }

  /**
   * Constructor for deserialization via Jackson
   */
  @JsonCreator
  public CordraUploadSchema(@JsonProperty("cordraServerUrl") String cordraServerUrl,
      @JsonProperty("authServerUrl") String authServerUrl,
      @JsonProperty("authRealm") String authRealm,
      @JsonProperty("authClientId") String authClientId,
      @JsonProperty("numberOfProcessingThreads") int numberOfProcessingThreads,
      @JsonProperty("columnMapping") JsonNode columnMapping,
      @JsonProperty("syncStatusForRows") Map<Integer, SyncState> syncStatusForRows) {
    this.cordraServerUrl = cordraServerUrl;
    this.authServerUrl = authServerUrl;
    this.authRealm = authRealm;
    this.authClientId = authClientId;
    this.columnMapping = columnMapping;
    this.syncStatusForRows = syncStatusForRows;
    this.numberOfProcessingThreads = numberOfProcessingThreads;
  }

  @JsonProperty("cordraServerUrl")
  public String getCordraServerUrl() {
    return cordraServerUrl;
  }

  @JsonProperty("authServerUrl")
  public String getAuthServerUrl() {
    return authServerUrl;
  }

  @JsonProperty("columnMapping")
  public JsonNode getColumnMapping() {
    return columnMapping;
  }
  
  @JsonProperty("numberOfProcessingThreads")
  public int getNumberOfProcessingThreads() {
    return this.numberOfProcessingThreads;
  }

  @JsonProperty("syncStatusForRows")
  public Map<Integer, SyncState> getSyncStatusForRows() {
    return syncStatusForRows;
  }

  public void setCordraServerUrl(String cordraServerUrl) {
    this.cordraServerUrl = cordraServerUrl;
  }

  public void setAuthServerUrl(String authServerUrl) {
    this.authServerUrl = authServerUrl;
  }

  public void setAuthRealm(String authRealm) {
    this.authRealm = authRealm;
  }

  public void setAuthClientId(String authClientId) {
    this.authClientId = authClientId;
  }
  
  public void setNumberOfProcessingThreads(int numberOfProcessingThreads) {
    this.numberOfProcessingThreads = numberOfProcessingThreads;
  }

  public void setColumnMapping(JsonNode columnMapping) {
    this.columnMapping = columnMapping;
  }

  public void setSyncStatusForRows(Map<Integer, SyncState> syncStatusForRows) {
    this.syncStatusForRows = syncStatusForRows;
  }

  @Override
  public void onBeforeSave(Project project) {
    System.out.println("ON before save!");
  }

  @Override
  public void onAfterSave(Project project) {
    System.out.println("ON after save!");
  }

  @Override
  public void dispose(Project project) {
    System.out.println("ON dispose save!");
  }

}
