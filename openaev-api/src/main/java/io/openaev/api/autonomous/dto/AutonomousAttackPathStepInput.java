package io.openaev.api.autonomous.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.rest.inject.form.InjectInput;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * A single chained step the XTM One orchestrator appends to a live autonomous attack path. The
 * inject is wrapped as an {@code INJECT_EXECUTION} step template on the run's simulation workflow
 * so it executes through the chaining engine and renders in the animated attack-path map - the only
 * sanctioned way for the AI to build the path (standalone injects and atomic tests are denied to
 * the orchestrator because they never populate the map projection).
 *
 * <p>When {@code parentStepTemplateId} is set, a {@code DEPEND_ON} condition is attached so this
 * step only readies once the parent has executed, giving the kill chain its ordering. A root step
 * (no parent) readies immediately against the run's scope on the next evaluation.
 */
@Getter
@Setter
@Schema(description = "A chained inject step appended to a live autonomous attack path")
public class AutonomousAttackPathStepInput {

  // No field-level @Schema on purpose: a described $ref is wrapped in allOf by springdoc, which
  // makes the generated api-types shape non-obvious. The InjectInput type is self-descriptive and
  // the class-level description already explains the wrapping.
  @NotNull
  @Valid
  @JsonProperty("inject")
  private InjectInput inject;

  @JsonProperty("parent_step_template_id")
  @Schema(
      description =
          "Optional step template id this step depends on (DEPEND_ON). Null / omitted for a root "
              + "step that readies immediately.")
  private String parentStepTemplateId;
}
