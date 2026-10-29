package io.openaev.service.autonomous;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.config.TenantWriteScopeResolver;
import io.openaev.context.TxCtx;
import io.openaev.database.model.autonomous.AutonomousObjectiveTemplate;
import io.openaev.database.repository.autonomous.AutonomousObjectiveTemplateRepository;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit test for the objective-template gallery seeding. Focuses on the {@code scopeMode}
 * classification, which the orchestrator relies on to decide (deterministically, on its first
 * cycle) whether an objective needs a specific target the operator must pick.
 *
 * <p>Tenant isolation and write attribution are not provable here, since mocks do not run the
 * statement inspector: they are covered on the real stack by {@code
 * AutonomousObjectiveTemplateHttpIsolationTest}.
 */
@ExtendWith(MockitoExtension.class)
class AutonomousObjectiveTemplateServiceTest {

  private static final String TENANT = "11111111-1111-1111-1111-111111111111";

  @Mock private AutonomousObjectiveTemplateRepository repository;

  @Mock private TenantWriteScopeResolver writeScopeResolver;

  @InjectMocks private AutonomousObjectiveTemplateService service;

  /** Seed into an empty tenant and return the persisted templates by key. */
  private Map<String, AutonomousObjectiveTemplate> seedAll() {
    List<AutonomousObjectiveTemplate> saved = new ArrayList<>();
    // Empty tenant: no built-in exists yet, so every one is materialised. The seed reads the keys
    // in one query before the loop, so no statement inside it can auto-flush a pending insert.
    when(repository.findByKeyIn(anyCollection())).thenReturn(List.of());
    when(repository.save(any(AutonomousObjectiveTemplate.class)))
        .thenAnswer(
            invocation -> {
              AutonomousObjectiveTemplate t = invocation.getArgument(0);
              saved.add(t);
              return t;
            });
    when(repository.findByEnabledTrueOrderByOrderAsc()).thenReturn(saved);
    when(writeScopeResolver.tenantForWrite(any(), any())).thenReturn(TENANT);

    List<AutonomousObjectiveTemplate> result = service.listForScope(TxCtx.forTenant(TENANT));
    return result.stream().collect(Collectors.toMap(AutonomousObjectiveTemplate::getKey, t -> t));
  }

  @Test
  void seeds_every_builtin_with_a_non_blank_scope_mode() {
    Map<String, AutonomousObjectiveTemplate> byKey = seedAll();

    assertFalse(byKey.isEmpty(), "built-ins should be seeded into an empty tenant");
    for (AutonomousObjectiveTemplate template : byKey.values()) {
      assertTrue(template.isBuiltin(), "seeded templates are built-in");
      assertEquals(
          TENANT,
          template.getTenant().getId(),
          "the seed attributes the tenant explicitly; the entity listener is gone");
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
  void seeding_is_idempotent_when_builtins_already_match() {
    // First pass: seed into an empty tenant to capture the canonical built-ins (correct scope
    // modes and builtin=true), then replay them as the already-persisted state.
    Map<String, AutonomousObjectiveTemplate> seeded = seedAll();
    reset(repository);

    // Every key already exists AND already matches the code definition, so the scope-mode sync
    // finds nothing to change and nothing is saved.
    when(repository.findByKeyIn(anyCollection())).thenReturn(new ArrayList<>(seeded.values()));
    when(repository.findByEnabledTrueOrderByOrderAsc())
        .thenReturn(new ArrayList<>(seeded.values()));
    when(writeScopeResolver.tenantForWrite(any(), any())).thenReturn(TENANT);

    service.listForScope(TxCtx.forTenant(TENANT));

    verify(repository, never()).save(any());
  }

  @Test
  void every_builtin_label_and_description_has_a_frontend_translation_key() throws IOException {
    // The frontend renders t(label) / t(description); the i18n checker only sees literal t('...')
    // calls, so a built-in renamed here would silently fall back to English in every locale.
    Path englishCatalog =
        Stream.of(
                Path.of("..", "openaev-front", "src", "utils", "lang", "en.json"),
                Path.of("openaev-front", "src", "utils", "lang", "en.json"))
            .filter(Files::exists)
            .findFirst()
            .orElseThrow(
                () -> new AssertionError("openaev-front/src/utils/lang/en.json not found"));
    JsonNode english = new ObjectMapper().readTree(englishCatalog.toFile());

    List<String> missing = new ArrayList<>();
    for (AutonomousObjectiveTemplate template : seedAll().values()) {
      for (String text : Arrays.asList(template.getLabel(), template.getDescription())) {
        if (text != null && !english.has(text)) {
          missing.add(text);
        }
      }
    }

    assertTrue(
        missing.isEmpty(),
        "Add these built-in objective texts to openaev-front/src/utils/lang/*.json: " + missing);
  }

  @Test
  void findByKeyOrNull_returns_null_for_blank_key() {
    assertNull(service.findByKeyOrNull(null));
    assertNull(service.findByKeyOrNull("  "));
    verify(repository, never()).findByKey(anyString());
  }
}
