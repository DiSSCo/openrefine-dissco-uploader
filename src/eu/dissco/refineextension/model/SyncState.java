package eu.dissco.refineextension.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.JsonNode;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonTypeInfo(use = JsonTypeInfo.Id.NONE)
public class SyncState {
    @JsonProperty("syncStatus")
    protected String syncStatus;

    @JsonProperty("errorMessage")
    protected String errorMessage;

    @JsonProperty("changes")
    JsonNode changes;

    /**
     * Constructor.
     */
    public SyncState() {

    }

    public SyncState(@JsonProperty("syncStatus") String syncStatus) {
        this.syncStatus = syncStatus;
    }

    public SyncState(@JsonProperty("syncStatus") String syncStatus,
            @JsonProperty("errorMessage") String errorMessage) {
        this.syncStatus = syncStatus;
        this.errorMessage = errorMessage;
    }
    
    @JsonCreator
    public SyncState(@JsonProperty("syncStatus") String syncStatus,
            @JsonProperty("errorMessage") String errorMessage,
            @JsonProperty("changes") JsonNode changes) {
        this.syncStatus = syncStatus;
        this.errorMessage = errorMessage;
        this.changes = changes;
    }

    @JsonProperty("syncStatus")
    public String getSyncStatus() {
        return syncStatus;
    }
    @JsonProperty("errorMessage")
    public String getErrorMessage() {
        return errorMessage;
    }
    
    @JsonProperty("changes")
    public JsonNode getChanges() {
        return changes;
    }
}