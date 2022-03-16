package eu.dissco.refineextension.model;

import java.util.Map;
import com.fasterxml.jackson.annotation.JsonProperty;

public class SyncResult {
  @JsonProperty("code")
  protected String code;
  @JsonProperty("message")
  protected String message;
  @JsonProperty("results")
  Map<Integer, SyncState> results;

  public SyncResult(String code, String message) {
    this.code = code;
    this.message = message;
    this.results = null;
  }

  public SyncResult(Map<Integer, SyncState> results) {
    this.code = "ok";
    this.message = null;
    this.results = results;
  }
}

