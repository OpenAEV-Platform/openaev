package io.openaev.engine.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class EsSearch {

  @JsonProperty("base_id")
  @NotBlank
  private String id;

  @JsonProperty("base_entity")
  private String entity;

  @JsonProperty("base_representative")
  private String representative;

  @JsonProperty("base_created_at")
  private String createdAt;

  @JsonProperty("base_updated_at")
  private String updatedAt;

  @JsonProperty("base_score")
  private Double score;
}
