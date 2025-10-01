package io.openaev.database.audit;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

@Getter
public class IndexEvent {
  @JsonProperty("event_id")
  private String id;

  @JsonProperty("event_type")
  private String type;

  public IndexEvent(String type, String id) {
    this.type = type;
    this.id = id;
  }
}
