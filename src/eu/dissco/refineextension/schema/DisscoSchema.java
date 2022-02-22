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
    public DisscoSchema(@JsonProperty("columnMapping") JsonNode columnMapping,
            @JsonProperty("syncStatusForRows") Map<Integer, SyncState> syncStatusForRows) {
        this.columnMapping = columnMapping;
        this.syncStatusForRows = syncStatusForRows;
    }

    @JsonProperty("columnMapping")
    public JsonNode getColumnMapping() {
        return columnMapping;
    }

    @JsonProperty("syncStatusForRows")
    public Map<Integer, SyncState> getSyncStatusForRows() {
        return syncStatusForRows;
    }

    @Override
    public void onBeforeSave(Project project) {
        System.out.println("ON before save!");
    }

    @Override
    public void onAfterSave(Project project) {
        System.out.println("ON after save!");}

    @Override
    public void dispose(Project project) {
        System.out.println("ON dispose save!");}

}
