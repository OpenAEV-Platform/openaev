package io.openaev.rest.atomic_testing.form;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.openaev.utils.InjectExpectationResultUtils.ExpectationResultsByType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
@Builder
public class InjectResultOverviewOutput {

  @Schema(description = "Id of inject")
  @JsonProperty("inject_id")
  @NotBlank
  private String id;

  @Schema(description = "Title of inject")
  @JsonProperty("inject_title")
  @NotBlank
  private String title;

  @Schema(description = "Description of inject")
  @JsonProperty("inject_description")
  private String description;

  @Schema(description = "Content of inject")
  @JsonProperty("inject_content")
  private ObjectNode content;

  @Schema(description = "Type of inject")
  @JsonProperty("inject_type")
  private String type;

  @Schema(description = "Tags")
  @JsonProperty("injects_tags")
  private List<String> tagIds;

  @Schema(description = "Documents")
  @JsonProperty("injects_documents")
  private List<String> documentIds;

  @Schema(description = "Full contract")
  @JsonProperty("inject_injector_contract")
  private AtomicInjectorContractOutput injectorContract;

  @Schema(description = "status")
  @JsonProperty("inject_status")
  private InjectStatusSimple status;

  @Schema(description = "Expectations")
  @JsonProperty("inject_expectations")
  private List<InjectExpectationSimple> expectations;

  @Schema(description = "Kill chain phases")
  @JsonProperty("inject_kill_chain_phases")
  private List<KillChainPhaseSimple> killChainPhases;

  @Schema(description = "Tags")
  @JsonProperty("inject_tags")
  private Set<String> tags;

  @Schema(description = "Indicates whether the inject is ready for use")
  @JsonProperty("inject_ready")
  private boolean isReady;

  @Schema(description = "Timestamp when the inject was last updated")
  @JsonProperty("inject_updated_at")
  private Instant updatedAt;

  @Schema(description = "Recurrence cron expression for scheduled relaunch")
  @JsonProperty("inject_recurrence")
  private String recurrence;

  @Schema(description = "Start date of the recurrence scheduling")
  @JsonProperty("inject_recurrence_start")
  private Instant recurrenceStart;

  @Schema(description = "End date of the recurrence scheduling")
  @JsonProperty("inject_recurrence_end")
  private Instant recurrenceEnd;

  // -- COMPUTED ATTRIBUTES --

  @Builder.Default
  @Schema(description = "Result of expectations")
  @JsonProperty("inject_expectation_results")
  @NotNull
  private List<ExpectationResultsByType> expectationResultByTypes = new ArrayList<>();
}
