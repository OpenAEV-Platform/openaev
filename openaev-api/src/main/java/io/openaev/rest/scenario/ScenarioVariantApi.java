package io.openaev.rest.scenario;

import io.openaev.database.model.ScenarioVariant;
import io.openaev.database.repository.ScenarioVariantRepository;
import io.openaev.rest.exception.ElementNotFoundException;
import io.openaev.rest.helper.RestBehavior;
import io.openaev.rest.scenario.form.ScenarioVariantInput;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class ScenarioVariantApi extends RestBehavior {

  private final ScenarioVariantRepository variantRepository;

  @Operation(summary = "List variants for a scenario (auto-creates defaults if none exist)")
  @GetMapping("/api/scenarios/{scenarioId}/variants")
  @Transactional(rollbackOn = Exception.class)
  public List<ScenarioVariant> getVariants(@PathVariable String scenarioId) {
    List<ScenarioVariant> existing = variantRepository.findByScenarioIdOrderByCreatedAtAsc(scenarioId);
    if (!existing.isEmpty()) {
      return existing;
    }
    // Auto-create default variants for this scenario
    ScenarioVariant productVariant = ScenarioVariant.builder()
        .name("Product Variant")
        .scenarioId(scenarioId)
        .config(java.util.Map.of())
        .active(true)
        .build();
    ScenarioVariant engineeringVariant = ScenarioVariant.builder()
        .name("Engineering Variant")
        .scenarioId(scenarioId)
        .config(java.util.Map.of(
            "scope_allow_list_label", "Initial List",
            "condition_sub_filter_enabled", false))
        .active(false)
        .build();
    variantRepository.save(productVariant);
    variantRepository.save(engineeringVariant);
    return variantRepository.findByScenarioIdOrderByCreatedAtAsc(scenarioId);
  }

  @Operation(summary = "Create a new variant for a scenario")
  @PostMapping("/api/scenarios/{scenarioId}/variants")
  @Transactional(rollbackOn = Exception.class)
  public ScenarioVariant createVariant(
      @PathVariable String scenarioId,
      @Valid @RequestBody ScenarioVariantInput input) {
    ScenarioVariant variant = ScenarioVariant.builder()
        .name(input.getName())
        .scenarioId(scenarioId)
        .config(input.getConfig())
        .active(false)
        .build();
    return variantRepository.save(variant);
  }

  @Operation(summary = "Activate a variant (deactivates all others for this scenario)")
  @PutMapping("/api/scenarios/{scenarioId}/variants/{variantId}/activate")
  @Transactional(rollbackOn = Exception.class)
  public ScenarioVariant activateVariant(
      @PathVariable String scenarioId,
      @PathVariable String variantId) {
    ScenarioVariant variant = variantRepository.findById(variantId)
        .orElseThrow(() -> new ElementNotFoundException("Variant not found: " + variantId));
    variantRepository.deactivateAllByScenarioId(scenarioId);
    variant.setActive(true);
    return variantRepository.save(variant);
  }

  @Operation(summary = "Delete a variant")
  @DeleteMapping("/api/scenarios/{scenarioId}/variants/{variantId}")
  @Transactional(rollbackOn = Exception.class)
  public void deleteVariant(
      @PathVariable String scenarioId,
      @PathVariable String variantId) {
    variantRepository.deleteById(variantId);
  }
}
