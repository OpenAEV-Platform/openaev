package io.openaev.rest.atomic_testing.form;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.database.model.InjectExpectation;
import io.openaev.database.model.InjectExpectationResult;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@JsonInclude(NON_NULL)
@Builder
@Schema(description = "Represents a single inject expectation with agent name")
public class InjectExpectationAgentOutput {

  @NotNull
  @JsonProperty("inject_expectation_type")
  private InjectExpectation.EXPECTATION_TYPE type;

  @NotBlank
  @JsonProperty("inject_expectation_id")
  private String id;

  @JsonProperty("inject_expectation_name")
  private String name;

  @JsonProperty("inject_expectation_results")
  private List<InjectExpectationResult> results;

  @JsonProperty("inject_expectation_score")
  private Double score;

  @JsonProperty("inject_expectation_status")
  public InjectExpectation.EXPECTATION_STATUS status;

  @JsonProperty("inject_expiration_time")
  @NotNull
  private Long expirationTime;

  @JsonProperty("inject_expectation_created_at")
  private Instant createdAt;

  @JsonProperty("inject_expectation_group")
  private boolean expectationGroup;

  @JsonProperty("inject_expectation_asset")
  private String assetId; // id

  @JsonProperty("inject_expectation_agent")
  private String agentId; // id

  @JsonProperty("inject_expectation_agent_name")
  private String agentName;
}
