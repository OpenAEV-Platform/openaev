package io.openaev.database.model;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.annotation.Queryable;
import io.swagger.v3.oas.annotations.media.DiscriminatorMapping;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Id;
import jakarta.validation.constraints.NotBlank;
import java.util.Set;
import lombok.Data;

@Data
@Schema(
    discriminatorProperty = "target_type",
    oneOf = {
      AssetGroupTarget.class,
      TeamTarget.class,
      EndpointTarget.class,
      AgentTarget.class,
      PlayerTarget.class
    },
    discriminatorMapping = {
      @DiscriminatorMapping(value = "ASSETS_GROUPS", schema = AssetGroupTarget.class),
      @DiscriminatorMapping(value = "ASSETS", schema = EndpointTarget.class),
      @DiscriminatorMapping(value = "TEAMS", schema = TeamTarget.class),
      @DiscriminatorMapping(value = "PLAYERS", schema = PlayerTarget.class),
      @DiscriminatorMapping(value = "AGENT", schema = AgentTarget.class),
    })
@JsonInclude(NON_NULL)
public abstract class InjectTarget {

  @Id
  @NotBlank
  @JsonProperty("target_id")
  private String id;

  @JsonProperty("target_tags")
  @Queryable(filterable = true, searchable = true, sortable = true, dynamicValues = true)
  private Set<String> tags;

  @JsonProperty("target_type")
  private String targetType;

  // use this property to convey the correct icon client-side
  @JsonProperty("target_subtype")
  protected abstract String getTargetSubtype();

  @JsonProperty("target_detection_status")
  private InjectExpectation.EXPECTATION_STATUS targetDetectionStatus;

  @JsonProperty("target_prevention_status")
  private InjectExpectation.EXPECTATION_STATUS targetPreventionStatus;

  @JsonProperty("target_vulnerability_status")
  private InjectExpectation.EXPECTATION_STATUS targetVulnerabilityStatus;

  @JsonProperty("target_human_response_status")
  private InjectExpectation.EXPECTATION_STATUS targetHumanResponseStatus;

  @JsonProperty("target_execution_status")
  private InjectExpectation.EXPECTATION_STATUS targetExecutionStatus =
      InjectExpectation.EXPECTATION_STATUS.UNKNOWN;
}
