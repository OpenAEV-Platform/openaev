package io.openaev.rest.atomic_testing.form;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.openaev.utils.InjectExpectationResultUtils.ExpectationResultsByType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class InjectResultOutput {

  @Schema(description = "Id of inject")
  @JsonProperty("inject_id")
  @NotBlank
  private String id;

  @Schema(description = "Title of inject")
  @JsonProperty("inject_title")
  @NotBlank
  private String title;

  @Schema(description = "Timestamp when the inject was last updated")
  @JsonProperty("inject_updated_at")
  @NotNull
  private Instant updatedAt;

  @Schema(description = "Type of inject")
  @JsonProperty("inject_type")
  private String injectType;

  @Schema(description = "Injector contract")
  @JsonProperty("inject_injector_contract")
  private InjectorContractSimple injectorContract;

  @Schema(description = "Status")
  @JsonProperty("inject_status")
  private InjectStatusSimple status;

  // Disabled injects are skipped by the execution scheduler and therefore never get a status
  // row: expose the flag so the UI can label them "Disabled" instead of the DRAFT fallback.
  @Schema(description = "Whether the inject is enabled (disabled injects are never executed)")
  @JsonProperty("inject_enabled")
  private Boolean enabled = Boolean.TRUE;

  // Cross-scope lists (e.g. "injects played" on an asset) mix atomic testings and simulation
  // injects: the exercise id lets the UI route each row to the right detail page.
  @Schema(description = "Id of the simulation (exercise) this inject belongs to, if any")
  @JsonProperty("inject_exercise")
  private String exerciseId;

  @JsonIgnore private ObjectNode content;
  @JsonIgnore private String[] teamIds;
  @JsonIgnore private String[] assetIds;
  @JsonIgnore private String[] assetGroupIds;

  // -- COMPUTED ATTRIBUTES --

  @Schema(description = "Result of expectations")
  @JsonProperty("inject_expectation_results")
  @NotNull
  private List<ExpectationResultsByType> expectationResultByTypes = new ArrayList<>();

  @JsonProperty("inject_targets")
  private List<TargetSimple> targets = new ArrayList<>();

  @JsonProperty("inject_contract_domains")
  @Schema(description = "Domain of the inject")
  public String[] getDomains() {
    return injectorContract != null ? injectorContract.getDomains() : new String[] {};
  }
}
