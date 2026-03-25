package io.openaev.api.chaining.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.database.model.ConditionStep;
import jakarta.persistence.CascadeType;
import jakarta.persistence.FetchType;
import jakarta.persistence.OneToMany;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Nested output DTO for a single condition inside an event. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ConditionOutput {
  @JsonProperty("condition_id")
  private String id;

  @JsonProperty("key_type")
  private String keyType;

  @JsonProperty("type")
  private String type;

  @JsonProperty("value")
  private String value;

  @JsonProperty("condition_parent_id")
  private String conditionParentId;

  @OneToMany(
      fetch = FetchType.LAZY,
      mappedBy = "step",
      cascade = CascadeType.ALL,
      orphanRemoval = true)
  @JsonIgnore
  private List<ConditionStep> conditionSteps = new ArrayList<>();
}
