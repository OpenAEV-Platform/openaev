package io.openaev.database.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class StatusPayloadDocument {

  @JsonProperty("document_id")
  @NotBlank
  private String id;

  @JsonProperty("document_name")
  @NotBlank
  private String name;

  // Usefully to avoid migration
  @JsonCreator
  public StatusPayloadDocument(
      @JsonProperty("document_id") String id, @JsonProperty("document_name") String name) {
    this.id = id;
    this.name = name;
  }

  public StatusPayloadDocument(Document document) {
    this.id = document.getId();
    this.name = document.getName();
  }
}
