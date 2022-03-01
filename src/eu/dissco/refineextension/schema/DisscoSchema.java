package eu.dissco.refineextension.schema;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.refine.model.OverlayModel;
import com.google.refine.model.Project;

import com.fasterxml.jackson.databind.JsonNode;

import eu.dissco.refineextension.model.SyncState;

@JsonIgnoreProperties(ignoreUnknown = true)
public class DisscoSchema implements OverlayModel {

  @JsonProperty("cordraServerUrl")
  protected String cordraServerUrl;

  @JsonProperty("authServerUrl")
  protected String authServerUrl;

  @JsonProperty("authRealm")
  protected String authRealm;

  @JsonProperty("authClientId")
  protected String authClientId;

  @JsonProperty("columnMapping")
  protected JsonNode columnMapping;

  @JsonProperty("syncStatusForRows")
  protected Map<Integer, SyncState> syncStatusForRows;

  /**
   * Constructor.
   */
  public DisscoSchema() {

  }

  /**
   * Constructor for deserialization via Jackson
   */
  @JsonCreator
  public DisscoSchema(@JsonProperty("cordraServerUrl") String cordraServerUrl,
      @JsonProperty("authServerUrl") String authServerUrl,
      @JsonProperty("authRealm") String authRealm,
      @JsonProperty("authClientId") String authClientId,
      @JsonProperty("columnMapping") JsonNode columnMapping,
      @JsonProperty("syncStatusForRows") Map<Integer, SyncState> syncStatusForRows) {
    this.cordraServerUrl = cordraServerUrl;
    this.authServerUrl = authServerUrl;
    this.authRealm = authRealm;
    this.authClientId = authClientId;
    this.columnMapping = columnMapping;
    this.syncStatusForRows = syncStatusForRows;
  }

  @JsonProperty("cordraServerUrl")
  protected String getCordraServerUrl() {
    return cordraServerUrl;
  }

  @JsonProperty("authServerUrl")
  protected String getAuthServerUrl() {
    return authServerUrl;
  }

  @JsonProperty("columnMapping")
  public JsonNode getColumnMapping() {
    return columnMapping;
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
