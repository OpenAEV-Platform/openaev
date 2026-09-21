package io.openaev.service.marking_definition;

import static io.openaev.utils.fixtures.MarkingDefinitionFixture.createDefaultMarkingDefinition;
import static io.openaev.utils.fixtures.MarkingDefinitionFixture.toInput;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.openaev.api.marking_definition.form.MarkingDefinitionInput;
import io.openaev.config.AllTablesWithMarkingIds;
import io.openaev.config.cache.MarkingClearanceCacheManager;
import io.openaev.context.TxCtx;
import io.openaev.database.model.MarkingDefinition;
import io.openaev.database.model.Tenant;
import io.openaev.database.repository.MarkingDefinitionRepository;
import io.openaev.rest.exception.BadRequestException;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Pins the cache-eviction and delete-time scrub contracts of {@link MarkingDefinitionService} —
 * both landed as fixes for a documented-but-unwired gap (task2/tech-design.md §"Every reduction of
 * a clearance must evict", tech-design-option-c.md §3.2). Unit-level and Mockito-based rather than
 * a full {@code @SpringBootTest}: the behaviour under test is "which collaborator gets called, with
 * what", not a real schema/predicate, which {@code AssetMarkingIsolationTest} already covers.
 */
@ExtendWith(MockitoExtension.class)
class MarkingDefinitionServiceTest {

  private static final String TENANT_ID = "tenant-1";

  @Mock private MarkingDefinitionRepository repository;
  @Mock private AllTablesWithMarkingIds allTablesWithMarkingIds;
  @Mock private MarkingClearanceCacheManager markingClearanceCacheManager;
  @Mock private JdbcTemplate jdbcTemplate;

  private MarkingDefinitionService service() {
    return new MarkingDefinitionService(
        repository, allTablesWithMarkingIds, markingClearanceCacheManager, jdbcTemplate);
  }

  private MarkingDefinition existingIn(String tenantId) {
    MarkingDefinition existing = createDefaultMarkingDefinition();
    existing.setId("marking-1");
    existing.setTenant(new Tenant(tenantId));
    return existing;
  }

  @Nested
  @DisplayName("update")
  class Update {

    @Test
    @DisplayName("given_orderChanged_should_evictEveryCachedClearance")
    void given_orderChanged_should_evictEveryCachedClearance() {
      // Arrange
      MarkingDefinition existing = existingIn(TENANT_ID);
      when(repository.findById(existing.getId())).thenReturn(Optional.of(existing));
      when(repository.existsByTypeAndDefinitionAndTenantIdExcludingId(any(), any(), any(), any()))
          .thenReturn(false);
      when(repository.save(existing)).thenReturn(existing);
      MarkingDefinitionInput input =
          new MarkingDefinitionInput(
              existing.getType(),
              existing.getDefinition(),
              existing.getColor(),
              existing.getOrder() + 1);

      // Act
      service().update(TxCtx.forTenant(TENANT_ID), existing.getId(), input);

      // Assert
      verify(markingClearanceCacheManager, times(1)).evictAll();
    }

    @Test
    @DisplayName("given_orderUnchanged_should_notEvictAnyCache")
    void given_orderUnchanged_should_notEvictAnyCache() {
      // Arrange
      MarkingDefinition existing = existingIn(TENANT_ID);
      when(repository.findById(existing.getId())).thenReturn(Optional.of(existing));
      when(repository.existsByTypeAndDefinitionAndTenantIdExcludingId(any(), any(), any(), any()))
          .thenReturn(false);
      when(repository.save(existing)).thenReturn(existing);
      MarkingDefinitionInput input = toInput(existing);

      // Act
      service().update(TxCtx.forTenant(TENANT_ID), existing.getId(), input);

      // Assert
      verify(markingClearanceCacheManager, never()).evictAll();
    }
  }

  @Nested
  @DisplayName("delete")
  class Delete {

    @Test
    @DisplayName("given_definitionInUse_should_scrubEveryMarkedTableAndEvictAll")
    void given_definitionInUse_should_scrubEveryMarkedTableAndEvictAll() {
      // Arrange
      MarkingDefinition existing = existingIn(TENANT_ID);
      when(repository.findById(existing.getId())).thenReturn(Optional.of(existing));
      Set<String> markedTableNames = new LinkedHashSet<>(Set.of("assets", "asset_groups"));
      when(allTablesWithMarkingIds.tableNames()).thenReturn(markedTableNames);

      // Act
      service().delete(TxCtx.forTenant(TENANT_ID), existing.getId());

      // Assert
      verify(repository, times(1)).delete(existing);
      for (String table : markedTableNames) {
        verify(jdbcTemplate, times(1))
            .update(
                eq(
                    "UPDATE "
                        + table
                        + " SET marking_ids = array_remove(marking_ids, ?) WHERE marking_ids @>"
                        + " ARRAY[?]::text[]"),
                eq(existing.getId()),
                eq(existing.getId()));
      }
      verify(markingClearanceCacheManager, times(1)).evictAll();
    }

    @Test
    @DisplayName("given_protectedDefinition_should_throwAndScrubNothing")
    void given_protectedDefinition_should_throwAndScrubNothing() {
      // Arrange
      MarkingDefinition existing = existingIn(TENANT_ID);
      existing.setProtectedDefinition(true);
      when(repository.findById(existing.getId())).thenReturn(Optional.of(existing));

      // Act & Assert
      assertThrows(
          BadRequestException.class,
          () -> service().delete(TxCtx.forTenant(TENANT_ID), existing.getId()));
      verify(repository, never()).delete(any(MarkingDefinition.class));
      verify(jdbcTemplate, never()).update(anyString(), any(Object[].class));
      verify(markingClearanceCacheManager, never()).evictAll();
    }

    @Test
    @DisplayName("given_tableNotInActivationAllowlist_should_stillScrubIt")
    void given_tableNotInActivationAllowlist_should_stillScrubIt() {
      // Arrange - the scrub must use every schema table with marking_ids, not the
      // MARKING-feature/activation-allowlist-narrowed set: a marking write is not feature-gated,
      // so a table can carry marking_ids values while inactive for read filtering, and a definition
      // deleted at that point must not leave a dangling id behind.
      MarkingDefinition existing = existingIn(TENANT_ID);
      when(repository.findById(existing.getId())).thenReturn(Optional.of(existing));
      Set<String> everyMarkedTable = new LinkedHashSet<>(Set.of("assets", "asset_groups"));
      when(allTablesWithMarkingIds.tableNames()).thenReturn(everyMarkedTable);

      // Act
      service().delete(TxCtx.forTenant(TENANT_ID), existing.getId());

      // Assert
      for (String table : everyMarkedTable) {
        verify(jdbcTemplate, times(1))
            .update(
                eq(
                    "UPDATE "
                        + table
                        + " SET marking_ids = array_remove(marking_ids, ?) WHERE marking_ids @>"
                        + " ARRAY[?]::text[]"),
                eq(existing.getId()),
                eq(existing.getId()));
      }
      verify(markingClearanceCacheManager, times(1)).evictAll();
    }

    @Test
    @DisplayName("given_noMarkedTablesInSchema_should_skipScrubButStillEvict")
    void given_noMarkedTablesInSchema_should_skipScrubButStillEvict() {
      // Arrange - no table in the schema has a marking_ids column at all.
      MarkingDefinition existing = existingIn(TENANT_ID);
      when(repository.findById(existing.getId())).thenReturn(Optional.of(existing));
      when(allTablesWithMarkingIds.tableNames()).thenReturn(Set.of());

      // Act
      service().delete(TxCtx.forTenant(TENANT_ID), existing.getId());

      // Assert
      verify(jdbcTemplate, never()).update(anyString(), any(Object[].class));
      verify(markingClearanceCacheManager, times(1)).evictAll();
    }
  }
}
