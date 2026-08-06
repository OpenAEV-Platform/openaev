package io.openaev.api.autonomous.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/**
 * A finding-driven trigger for a chained step - the sanctioned way to build an attack path that
 * draws itself. Instead of hard-wiring one step after another with {@code parent_step_template_id},
 * a step declares WHAT finding it reacts to ({@code filters}) and WHICH finding values it consumes
 * as inputs ({@code mappings}). The engine then readies it, once per matching finding, against
 * every target that finding pointed at - so a single seed scan fans out onto every
 * host/port/credential it discovers, exactly like a hand-built chained scenario.
 *
 * <p>A step with NO trigger and NO parent is a SEED: it readies immediately against the run scope
 * (e.g. an Nmap sweep). Its output parser emits findings that trigger the finding-driven steps.
 */
@Getter
@Setter
@Schema(description = "A finding-driven trigger: react to findings and consume their values")
public class AutonomousStepTrigger {

  @JsonProperty("event_name")
  @Schema(
      description =
          "Short, human-readable name for the EVENT this trigger represents - the discovery it"
              + " fires on, phrased as an operator would read it (e.g. \"SMB service exposed\","
              + " \"Valid credentials found\", \"Open web port discovered\"). It becomes the event"
              + " node's title in the Logic graph. When omitted, a readable name is derived from"
              + " the filters so the event is never shown as \"Untitled event\".")
  private String eventName;

  @JsonProperty("match")
  @Schema(
      description =
          "How to combine multiple filters: AND (all must hold) or OR (any). Defaults to AND.")
  private String match;

  @JsonProperty("filters")
  @Schema(
      description =
          "The predicates that make this step fire. Empty means: fire as soon as any of the"
              + " mapped key_types is present in the finding pool.")
  private List<AutonomousTriggerFilter> filters = new ArrayList<>();

  @JsonProperty("mappings")
  @Schema(
      description =
          "Which finding values to bind into this step's inject inputs (GLOBAL mappers). This is"
              + " how the step attacks what upstream steps discovered.")
  private List<AutonomousInputMapping> mappings = new ArrayList<>();
}
