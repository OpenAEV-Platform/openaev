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
 * <p>There are two ways to place a step in the path, and the finding-driven one is strongly
 * preferred because it makes the attack path draw ITSELF from what actually executes:
 *
 * <ul>
 *   <li><b>Finding-driven ({@code trigger})</b> - the step declares which finding it reacts to and
 *       which finding values it consumes. The engine readies it, once per matching finding, against
 *       every target that finding pointed at. A single seed scan therefore fans out onto every host
 *       / port / credential it discovers - exactly like a hand-built chained scenario. A step with
 *       NEITHER a trigger nor a parent is a SEED: it readies immediately against the run scope and
 *       its output parser emits the findings that drive everything downstream.
 *   <li><b>Ordering-only ({@code parentStepTemplateId})</b> - a plain {@code DEPEND_ON}: the step
 *       readies once the parent has executed. Use this only when a step genuinely just needs to run
 *       after another and does not consume its findings; do NOT model the whole path as a linear
 *       chain of DEPEND_ONs (that hard-draws the path instead of letting findings shape it).
 * </ul>
 *
 * Both may be combined (a trigger AND a parent).
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
          "Optional step template id this step depends on (DEPEND_ON), for pure ordering. Null / "
              + "omitted for a seed or a finding-driven step. Prefer 'trigger' over this.")
  private String parentStepTemplateId;

  @Valid
  @JsonProperty("trigger")
  @Schema(
      description =
          "Optional finding-driven trigger: the finding(s) this step reacts to and the finding"
              + " values it consumes as inputs. Preferred over parent_step_template_id - it lets"
              + " the attack path draw itself. Omit for a seed step (recon that runs first).")
  private AutonomousStepTrigger trigger;
}
