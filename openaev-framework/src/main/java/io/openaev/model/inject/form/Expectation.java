package io.openaev.model.inject.form;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.database.model.InjectExpectation;
import lombok.Data;

@Data
public class Expectation {

  @JsonProperty("expectation_type")
  private InjectExpectation.EXPECTATION_TYPE type;

  @JsonProperty("expectation_name")
  private String name;

  @JsonProperty("expectation_description")
  private String description;

  @JsonProperty("expectation_score")
  private Double score;

  @JsonProperty("expectation_expectation_group")
  private boolean expectationGroup;

  /** Expiration time in seconds */
  @JsonProperty("expectation_expiration_time")
  private Long expirationTime;
}
