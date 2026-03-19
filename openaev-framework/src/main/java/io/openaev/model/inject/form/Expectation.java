package io.openaev.model.inject.form;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.database.model.InjectExpectation;
import lombok.Data;

/**
 * Form model representing expectation configuration from user input.
 *
 * <p>This class captures expectation parameters as provided by users through the injection form. It
 * is used as input to create the appropriate expectation instances (Detection, Prevention, Manual,
 * etc.).
 *
 * <p>This is a data transfer object (DTO) that is deserialized from JSON form submissions and then
 * used to construct domain expectation objects.
 *
 * @see io.openaev.model.Expectation
 * @see io.openaev.model.expectation.DetectionExpectation
 * @see io.openaev.model.expectation.PreventionExpectation
 * @see io.openaev.expectation.ExpectationBuilderService
 */
@Data
public class Expectation {

  /** The type of expectation to create. */
  @JsonProperty("expectation_type")
  private InjectExpectation.EXPECTATION_TYPE type;

  /** Display name for this expectation. */
  @JsonProperty("expectation_name")
  private String name;

  /** Detailed description of the expectation. */
  @JsonProperty("expectation_description")
  private String description;

  /** The score value when this expectation is fulfilled (0-100). */
  @JsonProperty("expectation_score")
  private Double score;

  /** Whether this expectation should be evaluated as part of a group. */
  @JsonProperty("expectation_expectation_group")
  private boolean expectationGroup;

  /** Time in seconds after which this expectation automatically expires. */
  @JsonProperty("expectation_expiration_time")
  private Long expirationTime;
}
