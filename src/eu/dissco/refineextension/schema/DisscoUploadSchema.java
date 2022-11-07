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
public class DisscoUploadSchema implements OverlayModel {

  public static String overlayModelKey = "disscoUploadSchema";

  @JsonProperty("specimenServerUrl")
  protected String specimenServerUrl = "https://sandbox.dissco.tech/api/v1/specimen";

  @JsonProperty("authServerUrl")
  protected String authServerUrl = "https://login-demo.dissco.eu/auth";

  @JsonProperty("authRealm")
  protected String authRealm = "dissco";

  @JsonProperty("authClientId")
  protected String authClientId = "openrefine-demo";

  @JsonProperty("numberOfProcessingThreads")
  protected int numberOfProcessingThreads = 1;

  @JsonProperty("columnMapping")
  protected JsonNode columnMapping;

  @JsonProperty("syncStatusForRows")
  protected Map<Integer, SyncState> syncStatusForRows;

  /**
   * Constructor.
   */
  public DisscoUploadSchema() {

  }

  /**
   * Constructor for deserialization via Jackson
   */
  @JsonCreator
  public DisscoUploadSchema(@JsonProperty("specimenServerUrl") String specimenServerUrl,
      @JsonProperty("authServerUrl") String authServerUrl,
      @JsonProperty("authRealm") String authRealm,
      @JsonProperty("authClientId") String authClientId,
      @JsonProperty("numberOfProcessingThreads") int numberOfProcessingThreads,
      @JsonProperty("columnMapping") JsonNode columnMapping,
      @JsonProperty("syncStatusForRows") Map<Integer, SyncState> syncStatusForRows) {
    this.specimenServerUrl = specimenServerUrl;
    this.authServerUrl = authServerUrl;
    this.authRealm = authRealm;
    this.authClientId = authClientId;
    this.columnMapping = columnMapping;
    this.syncStatusForRows = syncStatusForRows;
    this.numberOfProcessingThreads = numberOfProcessingThreads;
  }

  @JsonProperty("specimenServerUrl")
  public String getSpecimenServerUrl() {
    return specimenServerUrl;
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

  public void setSpecimenServerUrl(String specimenServerUrl) {
    this.specimenServerUrl = specimenServerUrl;
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
