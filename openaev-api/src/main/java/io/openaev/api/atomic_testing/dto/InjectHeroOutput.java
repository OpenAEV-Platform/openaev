package io.openaev.api.atomic_testing.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.rest.atomic_testing.form.AtomicInjectorContractOutput;
import io.openaev.rest.atomic_testing.form.InjectStatusSimple;
import io.openaev.rest.atomic_testing.form.KillChainPhaseSimple;
import io.openaev.rest.atomic_testing.form.UserSimple;
import io.openaev.utils.InjectExpectationResultUtils.ExpectationResultsByType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

/** Fields read by the atomic testing / simulation inject detail header (InjectHero.tsx). */
@Setter
@Getter
@Builder
public class InjectHeroOutput {

  @Schema(description = "Id of inject")
  @JsonProperty("inject_id")
  @NotBlank
  private String id;

  @Schema(description = "Title of inject")
  @JsonProperty("inject_title")
  @NotBlank
  private String title;

  @Schema(description = "Type of inject")
  @JsonProperty("inject_type")
  private String type;

  @Schema(description = "status")
  @JsonProperty("inject_status")
  private InjectStatusSimple status;

  @Schema(description = "Recurrence cron expression for scheduled relaunch")
  @JsonProperty("inject_recurrence")
  private String recurrence;

  @Schema(description = "Start date of the recurrence scheduling")
  @JsonProperty("inject_recurrence_start")
  private Instant recurrenceStart;

  @Schema(description = "End date of the recurrence scheduling")
  @JsonProperty("inject_recurrence_end")
  private Instant recurrenceEnd;

  @Schema(description = "Full contract")
  @JsonProperty("inject_injector_contract")
  private AtomicInjectorContractOutput injectorContract;

  @Schema(description = "Kill chain phases")
  @JsonProperty("inject_kill_chain_phases")
  private List<KillChainPhaseSimple> killChainPhases;

  @Schema(description = "Tags")
  @JsonProperty("injects_tags")
  private List<String> tagIds;

  @Schema(description = "User who launched this inject")
  @JsonProperty("inject_launched_by")
  private UserSimple launchedBy;

  @Builder.Default
  @Schema(description = "Result of expectations")
  @JsonProperty("inject_expectation_results")
  @NotNull
  private List<ExpectationResultsByType> expectationResultByTypes = new ArrayList<>();
}
