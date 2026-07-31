package io.openaev.service.autonomous;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import io.openaev.database.model.autonomous.AutonomousObjectiveTemplate;
import io.openaev.database.repository.autonomous.AutonomousObjectiveTemplateRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit test for the objective-template gallery seeding. Focuses on the {@code scopeMode}
 * classification, which the orchestrator relies on to decide (deterministically, on its first
 * cycle) whether an objective needs a specific target the operator must pick.
 */
@ExtendWith(MockitoExtension.class)
class AutonomousObjectiveTemplateServiceTest {

  @Mock private AutonomousObjectiveTemplateRepository repository;

  @InjectMocks private AutonomousObjectiveTemplateService service;

  /** Seed into an empty tenant and return the persisted templates by key. */
  private Map<String, AutonomousObjectiveTemplate> seedAll() {
    List<AutonomousObjectiveTemplate> saved = new ArrayList<>();
    when(repository.existsByKey(anyString())).thenReturn(false);
    when(repository.save(any(AutonomousObjectiveTemplate.class)))
        .thenAnswer(
            invocation -> {
              AutonomousObjectiveTemplate t = invocation.getArgument(0);
              saved.add(t);
              return t;
            });
    when(repository.findByEnabledTrueOrderByOrderAsc()).thenReturn(saved);

    List<AutonomousObjectiveTemplate> result = service.listForCurrentTenant();
    return result.stream().collect(Collectors.toMap(AutonomousObjectiveTemplate::getKey, t -> t));
  }

  @Test
  void seeds_every_builtin_with_a_non_blank_scope_mode() {
    Map<String, AutonomousObjectiveTemplate> byKey = seedAll();

    assertFalse(byKey.isEmpty(), "built-ins should be seeded into an empty tenant");
    for (AutonomousObjectiveTemplate template : byKey.values()) {
      assertTrue(template.isBuiltin(), "seeded templates are built-in");
      String mode = template.getScopeMode();
      assertNotNull(mode, "scopeMode must never be null (DB column is NOT NULL)");
      assertTrue(
          mode.equals("environment") || mode.equals("target"),
          "scopeMode must be one of environment/target, got: " + mode);
    }
  }

  @Test
  void target_dependent_objectives_are_classified_as_target() {
    Map<String, AutonomousObjectiveTemplate> byKey = seedAll();

    // These objectives are meaningless without a specific operator-chosen target, so the
    // orchestrator must resolve/ask for scope before attacking.
    assertEquals("target", byKey.get("crown-jewel-assessment").getScopeMode());
    assertEquals("target", byKey.get("web-app-exploitation").getScopeMode());
  }

  @Test
  void environment_wide_objectives_are_classified_as_environment() {
    Map<String, AutonomousObjectiveTemplate> byKey = seedAll();

    // These operate over the whole authorized scope; no target choice is needed.
    assertEquals("environment", byKey.get("reach-domain-controller").getScopeMode());
    assertEquals("environment", byKey.get("validate-edr-detections").getScopeMode());
    assertEquals("environment", byKey.get("harvest-credentials").getScopeMode());
  }

  @Test
  void seeding_is_idempotent_when_a_key_already_exists() {
    when(repository.existsByKey(anyString())).thenReturn(true);
    when(repository.findByEnabledTrueOrderByOrderAsc()).thenReturn(List.of());

    service.listForCurrentTenant();

    // Nothing is saved when every key already exists for the tenant.
    verify(repository, never()).save(any());
  }

  @Test
  void findByKeyOrNull_returns_null_for_blank_key() {
    assertNull(service.findByKeyOrNull(null));
    assertNull(service.findByKeyOrNull("  "));
    verify(repository, never()).findByKey(anyString());
  }
}
