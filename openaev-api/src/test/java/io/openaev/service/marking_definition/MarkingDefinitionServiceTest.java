package io.openaev.service.marking_definition;

import static io.openaev.service.marking_definition.MarkingImportBatch.PARAM_COLOR;
import static io.openaev.service.marking_definition.MarkingImportBatch.PARAM_EARLIER_ROW;
import static io.openaev.service.marking_definition.MarkingImportBatch.PARAM_HIGHEST_ORDER;
import static io.openaev.service.marking_definition.MarkingImportBatch.PARAM_KNOWN_COLOR;
import static io.openaev.service.marking_definition.MarkingImportBatch.PARAM_KNOWN_ORDER;
import static io.openaev.service.marking_definition.MarkingImportBatch.PARAM_MAX_LENGTH;
import static io.openaev.service.marking_definition.MarkingImportBatch.PARAM_ORDER;
import static io.openaev.utils.fixtures.MarkingDefinitionFixture.createDefaultMarkingDefinition;
import static io.openaev.utils.fixtures.MarkingDefinitionFixture.createMarkingDefinition;
import static io.openaev.utils.fixtures.MarkingDefinitionFixture.toInput;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import io.openaev.api.marking_definition.form.MarkingDefinitionInput;
import io.openaev.config.AllTablesWithMarkingIds;
import io.openaev.config.cache.MarkingClearanceCacheManager;
import io.openaev.context.MarkingCtx;
import io.openaev.context.MarkingScopeSupplier;
import io.openaev.context.TxCtx;
import io.openaev.database.model.MarkingDefinition;
import io.openaev.database.model.MarkingDefinitionStixIds;
import io.openaev.database.model.Tenant;
import io.openaev.database.model.User;
import io.openaev.database.repository.MarkingDefinitionRepository;
import io.openaev.rest.exception.BadRequestException;
import io.openaev.rest.exception.ElementNotFoundException;
import io.openaev.rest.exception.FileTooBigException;
import io.openaev.rest.exception.ForbiddenException;
import io.openaev.rest.settings.PreviewFeature;
import io.openaev.service.PreviewFeatureService;
import io.openaev.service.TenantGroupService;
import io.openaev.service.import_report.ImportResult;
import io.openaev.service.import_report.ImportResultRow;
import io.openaev.service.import_report.ImportRowStatus;
import io.openaev.telemetry.metric_collectors.ActionMetricCollector;
import io.openaev.utils.pagination.SearchPaginationInput;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.reactive.function.UnsupportedMediaTypeException;

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
  private static final String USER_ID = "user-1";

  @Mock private MarkingDefinitionRepository repository;
  @Mock private ActionMetricCollector actionMetricCollector;
  @Mock private AllTablesWithMarkingIds allTablesWithMarkingIds;
  @Mock private MarkingClearanceCacheManager markingClearanceCacheManager;
  @Mock private MarkingScopeSupplier markingScopeSupplier;
  @Mock private JdbcTemplate jdbcTemplate;
  @Mock private TenantGroupService tenantGroupService;
  @Mock private PreviewFeatureService previewFeatureService;

  private MarkingDefinitionService service() {
    return new MarkingDefinitionService(
        repository,
        actionMetricCollector,
        allTablesWithMarkingIds,
        markingClearanceCacheManager,
        markingScopeSupplier,
        jdbcTemplate,
        tenantGroupService,
        previewFeatureService);
  }

  @AfterEach
  void clearTransactionSynchronization() {
    // Safety net: a test that asserts the deferred-until-commit behaviour opens a
    // TransactionSynchronizationManager scope manually (it's thread-bound state, not a mock), so a
    // failure mid-test must not leak it into the next test's thread.
    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.clearSynchronization();
    }
  }

  private MarkingDefinition existingIn(String tenantId) {
    MarkingDefinition existing = createDefaultMarkingDefinition();
    existing.setId("marking-1");
    existing.setTenant(new Tenant(tenantId));
    return existing;
  }

  // A bypassing caller resolves to every definition of the tenant: the tests that are not about
  // clearance run as one, so clearance never decides their outcome.
  private void firstAdminClearanceCovers(MarkingDefinition existing) {
    when(markingClearanceCacheManager.findClearance(User.ADMIN_UUID, TENANT_ID))
        .thenReturn(MarkingCtx.forMarkings(List.of(existing.getId())));
  }

  @Nested
  @DisplayName("create")
  class Create {

    @Test
    @DisplayName("given_validInput_should_evictEveryCachedClearance")
    void given_validInput_should_evictEveryCachedClearance() {
      // Arrange - a bypass caller may have cached an "all definitions" set before this call; that
      // cached entry does not contain the new id and must not survive it.
      MarkingDefinitionInput input = new MarkingDefinitionInput("TLP", "TLP:GREEN", "#4CAF50", 20);
      when(repository.existsByTypeAndDefinitionAndTenantIdExcludingId(
              input.type(), input.definition(), TENANT_ID, null))
          .thenReturn(false);
      when(repository.save(any(MarkingDefinition.class)))
          .thenAnswer(invocation -> invocation.getArgument(0));

      // Act
      service().create(input, TENANT_ID, User.ADMIN_UUID);

      // Assert
      verify(markingClearanceCacheManager, times(1)).evictAll();
    }

    @Test
    @DisplayName("given_noActiveTransaction_should_incrementCreatedCountImmediately")
    void given_noActiveTransaction_should_incrementCreatedCountImmediately() {
      // Arrange - no Spring transaction bound to this thread; the deferred hook must fall back to
      // running synchronously rather than silently dropping the metric.
      MarkingDefinitionInput input = new MarkingDefinitionInput("TLP", "TLP:GREEN", "#4CAF50", 20);
      when(repository.existsByTypeAndDefinitionAndTenantIdExcludingId(
              input.type(), input.definition(), TENANT_ID, null))
          .thenReturn(false);
      when(repository.save(any(MarkingDefinition.class)))
          .thenAnswer(invocation -> invocation.getArgument(0));

      // Act
      service().create(input, TENANT_ID, User.ADMIN_UUID);

      // Assert
      verify(actionMetricCollector, times(1)).addMarkingDefinitionCreatedCount();
    }

    @Test
    @DisplayName("given_activeTransactionCommits_should_incrementCreatedCountOnlyAfterCommit")
    void given_activeTransactionCommits_should_incrementCreatedCountOnlyAfterCommit() {
      // Arrange - repository.save may not flush until commit, so the counter must stay unset while
      // the transaction is still open, then apply once commit is signalled.
      TransactionSynchronizationManager.initSynchronization();
      MarkingDefinitionInput input = new MarkingDefinitionInput("TLP", "TLP:GREEN", "#4CAF50", 20);
      when(repository.existsByTypeAndDefinitionAndTenantIdExcludingId(
              input.type(), input.definition(), TENANT_ID, null))
          .thenReturn(false);
      when(repository.save(any(MarkingDefinition.class)))
          .thenAnswer(invocation -> invocation.getArgument(0));

      // Act
      service().create(input, TENANT_ID, User.ADMIN_UUID);
      verify(actionMetricCollector, never()).addMarkingDefinitionCreatedCount();
      TransactionSynchronizationManager.getSynchronizations()
          .forEach(TransactionSynchronization::afterCommit);

      // Assert
      verify(actionMetricCollector, times(1)).addMarkingDefinitionCreatedCount();
    }

    @Test
    @DisplayName("given_activeTransactionRollsBack_should_neverIncrementCreatedCount")
    void given_activeTransactionRollsBack_should_neverIncrementCreatedCount() {
      // Arrange
      TransactionSynchronizationManager.initSynchronization();
      MarkingDefinitionInput input = new MarkingDefinitionInput("TLP", "TLP:GREEN", "#4CAF50", 20);
      when(repository.existsByTypeAndDefinitionAndTenantIdExcludingId(
              input.type(), input.definition(), TENANT_ID, null))
          .thenReturn(false);
      when(repository.save(any(MarkingDefinition.class)))
          .thenAnswer(invocation -> invocation.getArgument(0));

      // Act - simulate rollback: the transaction ends without afterCommit ever firing.
      service().create(input, TENANT_ID, User.ADMIN_UUID);
      TransactionSynchronizationManager.clearSynchronization();

      // Assert
      verify(actionMetricCollector, never()).addMarkingDefinitionCreatedCount();
    }

    @Test
    @DisplayName("given_orderAboveOwnClearance_should_throwForbiddenAndSaveNothing")
    void given_orderAboveOwnClearance_should_throwForbiddenAndSaveNothing() {
      // Arrange - the caller's clearance tops out at order 2 on TLP.
      MarkingDefinition held = existingIn(TENANT_ID);
      held.setOrder(2);
      MarkingDefinitionInput input = new MarkingDefinitionInput("TLP", "TLP:ABOVE", "#4CAF50", 3);
      when(repository.exists(ArgumentMatchers.<Specification<MarkingDefinition>>any()))
          .thenReturn(true);
      when(markingClearanceCacheManager.findClearance(USER_ID, TENANT_ID))
          .thenReturn(MarkingCtx.forMarkings(List.of(held.getId())));
      when(repository.findAll(ArgumentMatchers.<Specification<MarkingDefinition>>any()))
          .thenReturn(List.of(held));

      // Act & Assert
      assertThrows(ForbiddenException.class, () -> service().create(input, TENANT_ID, USER_ID));
      // Refused before the uniqueness lookup: the caller must not learn whether the name exists.
      verify(repository, never())
          .existsByTypeAndDefinitionAndTenantIdExcludingId(any(), any(), any(), any());
      verify(repository, never()).save(any(MarkingDefinition.class));
      verify(markingClearanceCacheManager, never()).evictAll();
    }

    @Test
    @DisplayName("given_noClearance_should_throwForbiddenWithoutQueryingDefinitions")
    void given_noClearance_should_throwForbiddenWithoutQueryingDefinitions() {
      // Arrange
      MarkingDefinitionInput input = new MarkingDefinitionInput("TLP", "TLP:ANY", "#4CAF50", 0);
      when(repository.exists(ArgumentMatchers.<Specification<MarkingDefinition>>any()))
          .thenReturn(true);
      when(markingClearanceCacheManager.findClearance(USER_ID, TENANT_ID))
          .thenReturn(MarkingCtx.none());

      // Act & Assert
      assertThrows(ForbiddenException.class, () -> service().create(input, TENANT_ID, USER_ID));
      verify(repository, never()).findAll(ArgumentMatchers.<Specification<MarkingDefinition>>any());
      verify(repository, never())
          .existsByTypeAndDefinitionAndTenantIdExcludingId(any(), any(), any(), any());
      verify(repository, never()).save(any(MarkingDefinition.class));
    }

    @Test
    @DisplayName("given_typeNotInTenant_should_saveWithoutResolvingClearance")
    void given_typeNotInTenant_should_saveWithoutResolvingClearance() {
      // Arrange - repository.exists defaults to false: the tenant has no definition of this type.
      MarkingDefinitionInput input =
          new MarkingDefinitionInput("NEWTYPE", "NEWTYPE:HIGH", "#4CAF50", 999);
      when(repository.existsByTypeAndDefinitionAndTenantIdExcludingId(
              input.type(), input.definition(), TENANT_ID, null))
          .thenReturn(false);
      when(repository.save(any(MarkingDefinition.class)))
          .thenAnswer(invocation -> invocation.getArgument(0));

      // Act
      MarkingDefinition created = service().create(input, TENANT_ID, USER_ID);

      // Assert
      assertEquals("NEWTYPE", created.getType());
      verify(markingClearanceCacheManager, never()).findClearance(any(), any());
    }
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
      firstAdminClearanceCovers(existing);
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
      service().update(TxCtx.forTenant(TENANT_ID), existing.getId(), input, User.ADMIN_UUID);

      // Assert
      verify(markingClearanceCacheManager, times(1)).evictAll();
    }

    @Test
    @DisplayName("given_orderUnchanged_should_notEvictAnyCache")
    void given_orderUnchanged_should_notEvictAnyCache() {
      // Arrange
      MarkingDefinition existing = existingIn(TENANT_ID);
      when(repository.findById(existing.getId())).thenReturn(Optional.of(existing));
      firstAdminClearanceCovers(existing);
      when(repository.existsByTypeAndDefinitionAndTenantIdExcludingId(any(), any(), any(), any()))
          .thenReturn(false);
      when(repository.save(existing)).thenReturn(existing);
      MarkingDefinitionInput input = toInput(existing);

      // Act
      service().update(TxCtx.forTenant(TENANT_ID), existing.getId(), input, User.ADMIN_UUID);

      // Assert
      verify(markingClearanceCacheManager, never()).evictAll();
    }

    @Test
    @DisplayName("given_definitionAboveOwnClearance_should_throwNotFoundAndSaveNothing")
    void given_definitionAboveOwnClearance_should_throwNotFoundAndSaveNothing() {
      // Arrange - lowering an uncleared definition into the caller's range would grant it to them.
      MarkingDefinition existing = existingIn(TENANT_ID);
      when(repository.findById(existing.getId())).thenReturn(Optional.of(existing));
      when(markingClearanceCacheManager.findClearance(USER_ID, TENANT_ID))
          .thenReturn(MarkingCtx.forMarkings(List.of("another-marking")));
      MarkingDefinitionInput input =
          new MarkingDefinitionInput(
              existing.getType(), existing.getDefinition(), existing.getColor(), 0);

      // Act & Assert
      assertThrows(
          ElementNotFoundException.class,
          () -> service().update(TxCtx.forTenant(TENANT_ID), existing.getId(), input, USER_ID));
      verify(repository, never()).save(any(MarkingDefinition.class));
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
      firstAdminClearanceCovers(existing);
      Set<String> markedTableNames = new LinkedHashSet<>(Set.of("assets", "asset_groups"));
      when(allTablesWithMarkingIds.tableNames()).thenReturn(markedTableNames);

      // Act
      service().delete(TxCtx.forTenant(TENANT_ID), existing.getId(), User.ADMIN_UUID);

      // Assert
      verify(repository, times(1)).delete(existing);
      for (String table : markedTableNames) {
        verify(jdbcTemplate, times(1))
            .update(
                eq(
                    "UPDATE "
                        + table
                        + " SET marking_ids = array_remove(marking_ids, ?) WHERE COALESCE(marking_ids, '{}'::text[])"
                        + " @> ARRAY[?]::text[]"),
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
      firstAdminClearanceCovers(existing);

      // Act & Assert
      assertThrows(
          BadRequestException.class,
          () -> service().delete(TxCtx.forTenant(TENANT_ID), existing.getId(), User.ADMIN_UUID));
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
      firstAdminClearanceCovers(existing);
      Set<String> everyMarkedTable = new LinkedHashSet<>(Set.of("assets", "asset_groups"));
      when(allTablesWithMarkingIds.tableNames()).thenReturn(everyMarkedTable);

      // Act
      service().delete(TxCtx.forTenant(TENANT_ID), existing.getId(), User.ADMIN_UUID);

      // Assert
      for (String table : everyMarkedTable) {
        verify(jdbcTemplate, times(1))
            .update(
                eq(
                    "UPDATE "
                        + table
                        + " SET marking_ids = array_remove(marking_ids, ?) WHERE COALESCE(marking_ids, '{}'::text[])"
                        + " @> ARRAY[?]::text[]"),
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
      firstAdminClearanceCovers(existing);
      when(allTablesWithMarkingIds.tableNames()).thenReturn(Set.of());

      // Act
      service().delete(TxCtx.forTenant(TENANT_ID), existing.getId(), User.ADMIN_UUID);

      // Assert
      verify(jdbcTemplate, never()).update(anyString(), any(Object[].class));
      verify(markingClearanceCacheManager, times(1)).evictAll();
    }

    @Test
    @DisplayName("given_definitionAboveOwnClearance_should_throwNotFoundAndScrubNothing")
    void given_definitionAboveOwnClearance_should_throwNotFoundAndScrubNothing() {
      // Arrange - the scrub would declassify every row the caller cannot see.
      MarkingDefinition existing = existingIn(TENANT_ID);
      when(repository.findById(existing.getId())).thenReturn(Optional.of(existing));
      when(markingClearanceCacheManager.findClearance(USER_ID, TENANT_ID))
          .thenReturn(MarkingCtx.none());

      // Act & Assert
      assertThrows(
          ElementNotFoundException.class,
          () -> service().delete(TxCtx.forTenant(TENANT_ID), existing.getId(), USER_ID));
      verify(repository, never()).delete(any(MarkingDefinition.class));
      verifyNoInteractions(allTablesWithMarkingIds, jdbcTemplate);
      verify(markingClearanceCacheManager, never()).evictAll();
    }
  }

  @Nested
  @DisplayName("isAssigned")
  class IsAssigned {

    private String existsQuery(String table) {
      return "SELECT EXISTS (SELECT 1 FROM "
          + table
          + " WHERE COALESCE(marking_ids, '{}'::text[]) @> ARRAY[?]::text[])";
    }

    private static final String GROUPS_QUERY =
        "SELECT EXISTS (SELECT 1 FROM groups_markings WHERE marking_id = ?)";

    @Test
    @DisplayName("given_idHeldByFirstTable_should_returnTrueWithoutProbingTheRest")
    void given_idHeldByFirstTable_should_returnTrueWithoutProbingTheRest() {
      // Arrange - insertion-ordered so "assets" is deterministically probed first.
      MarkingDefinition existing = existingIn(TENANT_ID);
      when(repository.findById(existing.getId())).thenReturn(Optional.of(existing));
      when(markingScopeSupplier.clearanceFor(any()))
          .thenReturn(MarkingCtx.forMarkings(List.of(existing.getId())));
      when(allTablesWithMarkingIds.tableNames())
          .thenReturn(new LinkedHashSet<>(List.of("assets", "asset_groups")));
      when(jdbcTemplate.queryForObject(existsQuery("assets"), Boolean.class, existing.getId()))
          .thenReturn(true);

      // Act
      boolean assigned = service().isAssigned(TxCtx.forTenant(TENANT_ID), existing.getId());

      // Assert
      assertTrue(assigned);
      verify(jdbcTemplate, never())
          .queryForObject(existsQuery("asset_groups"), Boolean.class, existing.getId());
      verify(jdbcTemplate, never()).queryForObject(GROUPS_QUERY, Boolean.class, existing.getId());
    }

    @Test
    @DisplayName("given_noMarkedTablesInSchema_should_checkOnlyGroupGrants")
    void given_noMarkedTablesInSchema_should_checkOnlyGroupGrants() {
      // Arrange
      MarkingDefinition existing = existingIn(TENANT_ID);
      when(repository.findById(existing.getId())).thenReturn(Optional.of(existing));
      when(markingScopeSupplier.clearanceFor(any()))
          .thenReturn(MarkingCtx.forMarkings(List.of(existing.getId())));
      when(allTablesWithMarkingIds.tableNames()).thenReturn(Set.of());
      when(jdbcTemplate.queryForObject(GROUPS_QUERY, Boolean.class, existing.getId()))
          .thenReturn(false);

      // Act
      boolean assigned = service().isAssigned(TxCtx.forTenant(TENANT_ID), existing.getId());

      // Assert
      assertFalse(assigned);
      verify(jdbcTemplate, times(1)).queryForObject(GROUPS_QUERY, Boolean.class, existing.getId());
      verifyNoMoreInteractions(jdbcTemplate);
    }

    @Test
    @DisplayName("given_definitionOfAnotherTenant_should_throwNotFoundAndProbeNothing")
    void given_definitionOfAnotherTenant_should_throwNotFoundAndProbeNothing() {
      // Arrange - the caller must not learn whether another tenant's definition is in use.
      MarkingDefinition existing = existingIn("tenant-2");
      when(repository.findById(existing.getId())).thenReturn(Optional.of(existing));

      // Act & Assert
      assertThrows(
          ElementNotFoundException.class,
          () -> service().isAssigned(TxCtx.forTenant(TENANT_ID), existing.getId()));
      verifyNoInteractions(allTablesWithMarkingIds, jdbcTemplate);
    }

    @Test
    @DisplayName("given_definitionAboveOwnClearance_should_throwNotFoundAndProbeNothing")
    void given_definitionAboveOwnClearance_should_throwNotFoundAndProbeNothing() {
      // Arrange - search does not reveal it, so whether it is in use must not leak either.
      MarkingDefinition existing = existingIn(TENANT_ID);
      when(repository.findById(existing.getId())).thenReturn(Optional.of(existing));
      when(markingScopeSupplier.clearanceFor(any())).thenReturn(MarkingCtx.none());

      // Act & Assert
      assertThrows(
          ElementNotFoundException.class,
          () -> service().isAssigned(TxCtx.forTenant(TENANT_ID), existing.getId()));
      verifyNoInteractions(allTablesWithMarkingIds, jdbcTemplate);
    }
  }

  @Nested
  @DisplayName("stix id")
  class StixId {

    private void creatable(MarkingDefinitionInput input) {
      when(repository.existsByTypeAndDefinitionAndTenantIdExcludingId(
              input.type(), input.definition(), TENANT_ID, null))
          .thenReturn(false);
      when(repository.save(any(MarkingDefinition.class)))
          .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    @DisplayName("given_newDefinition_should_carryTheIdOpenCtiGivesIt")
    void given_newDefinition_should_carryTheIdOpenCtiGivesIt() {
      // Arrange
      MarkingDefinitionInput input = new MarkingDefinitionInput("tlp", "tlp:amber", "#FFB300", 30);
      creatable(new MarkingDefinitionInput("TLP", "TLP:AMBER", "#FFB300", 30));

      // Act
      MarkingDefinition created = service().create(input, TENANT_ID, User.ADMIN_UUID);

      // Assert
      assertEquals("marking-definition--f88d31f6-486f-44da-b317-01333bde0b82", created.getStixId());
    }

    @Test
    @DisplayName("given_importedStixId_should_keepIt")
    void given_importedStixId_should_keepIt() {
      // Arrange
      MarkingDefinitionInput input = new MarkingDefinitionInput("PAP", "PAP:RED", "#E53935", 40);
      creatable(input);
      String imported = "marking-definition--11111111-2222-4333-8444-555555555555";

      // Act
      MarkingDefinition created = service().create(input, TENANT_ID, User.ADMIN_UUID, imported);

      // Assert
      assertEquals(imported, created.getStixId());
    }

    @Test
    @DisplayName("given_idCarriedByAnotherDefinition_should_createWithoutOne")
    void given_idCarriedByAnotherDefinition_should_createWithoutOne() {
      // Arrange - TLP:WHITE next to TLP:CLEAR: OpenCTI names both with one id.
      MarkingDefinitionInput input = new MarkingDefinitionInput("TLP", "TLP:WHITE", "#FFFFFF", 5);
      creatable(input);
      MarkingDefinition clear = existingIn(TENANT_ID);
      when(repository.findByStixIdAndTenantId(
              "marking-definition--613f2e26-407d-48c7-9eca-b8e91df99dc9", TENANT_ID))
          .thenReturn(Optional.of(clear));

      // Act
      MarkingDefinition created = service().create(input, TENANT_ID, User.ADMIN_UUID);

      // Assert
      assertEquals(null, created.getStixId());
    }

    @Test
    @DisplayName("given_renameOfDerivedId_should_followTheNewDefinition")
    void given_renameOfDerivedId_should_followTheNewDefinition() {
      // Arrange
      MarkingDefinition existing = existingIn(TENANT_ID);
      existing.setType("PAP");
      existing.setDefinition("PAP:GREEN");
      existing.setStixId("marking-definition--89484dde-e3d2-547f-a6c6-d14824429eb1");
      when(repository.findById(existing.getId())).thenReturn(Optional.of(existing));
      firstAdminClearanceCovers(existing);
      when(repository.save(any(MarkingDefinition.class)))
          .thenAnswer(invocation -> invocation.getArgument(0));
      MarkingDefinitionInput input =
          new MarkingDefinitionInput("PAP", "PAP:AMBER", existing.getColor(), existing.getOrder());

      // Act
      MarkingDefinition updated =
          service().update(TxCtx.forTenant(TENANT_ID), existing.getId(), input, User.ADMIN_UUID);

      // Assert
      assertEquals("marking-definition--a6f20d4d-0360-59b6-ba22-3b48707828b1", updated.getStixId());
    }

    @Test
    @DisplayName("given_renameOfImportedId_should_keepIt")
    void given_renameOfImportedId_should_keepIt() {
      // Arrange
      MarkingDefinition existing = existingIn(TENANT_ID);
      String imported = "marking-definition--11111111-2222-4333-8444-555555555555";
      existing.setStixId(imported);
      when(repository.findById(existing.getId())).thenReturn(Optional.of(existing));
      firstAdminClearanceCovers(existing);
      when(repository.save(any(MarkingDefinition.class)))
          .thenAnswer(invocation -> invocation.getArgument(0));
      MarkingDefinitionInput input =
          new MarkingDefinitionInput(
              existing.getType(), "RENAMED:1", existing.getColor(), existing.getOrder());

      // Act
      MarkingDefinition updated =
          service().update(TxCtx.forTenant(TENANT_ID), existing.getId(), input, User.ADMIN_UUID);

      // Assert
      assertEquals(imported, updated.getStixId());
    }
  }

  // -- EXPORT AND IMPORT --

  @Captor private ArgumentCaptor<Iterable<MarkingDefinition>> savedCaptor;

  private MarkingDefinition marking(
      String id, String type, String definition, int order, String color) {
    MarkingDefinition marking = createMarkingDefinition(type, definition, order, color);
    marking.setId(id);
    marking.setTenant(new Tenant(TENANT_ID));
    return marking;
  }

  // The seeded TLP scale of a tenant, as the caller's tenant holds it.
  private final MarkingDefinition tlpClear = marking("clear", "TLP", "TLP:CLEAR", 1, "#ffffff");
  private final MarkingDefinition tlpGreen = marking("green", "TLP", "TLP:GREEN", 2, "#4caf50");
  private final MarkingDefinition tlpAmber = marking("amber", "TLP", "TLP:AMBER", 3, "#ffc107");
  private final MarkingDefinition tlpRed = marking("red", "TLP", "TLP:RED", 4, "#ff0000");

  private void tenantHas(MarkingDefinition... existing) {
    when(repository.findAllByTenantId(TENANT_ID)).thenReturn(List.of(existing));
  }

  private void callerHolds(String... ids) {
    when(markingClearanceCacheManager.findClearance(USER_ID, TENANT_ID))
        .thenReturn(ids.length == 0 ? MarkingCtx.none() : MarkingCtx.forMarkings(List.of(ids)));
  }

  private static MarkingRow row(
      int position, String type, String definition, String color, String order) {
    return new MarkingRow(position, type, definition, color, order);
  }

  private ImportResult importAs(String userId, MarkingRow... rows) {
    return service().importAll(TENANT_ID, userId, List.of(rows));
  }

  private static ImportResultRow at(ImportResult result, int position) {
    return result.rows().get(position - 1);
  }

  private static void assertSkipped(
      ImportResultRow row, MarkingDefinitionImportRowReason reason, Map<String, String> params) {
    assertEquals(ImportRowStatus.SKIPPED, row.status());
    assertEquals(reason.name(), row.reason());
    assertEquals(params, row.params());
  }

  @Nested
  @DisplayName("exportAll")
  class Export {

    @Test
    @DisplayName("given_input_should_listOneTenantSortedByTypeThenOrderWithoutPaging")
    void given_input_should_listOneTenantSortedByTypeThenOrderWithoutPaging() {
      // Arrange
      when(repository.findAll(any(Specification.class), eq(Sort.by("type", "order"))))
          .thenReturn(List.of(tlpClear, tlpGreen));

      // Act
      List<MarkingDefinition> exported =
          service().exportAll(TENANT_ID, new SearchPaginationInput());

      // Assert
      assertEquals(List.of(tlpClear, tlpGreen), exported);
      verify(repository, never()).findAll(any(Specification.class), any(Pageable.class));
    }
  }

  @Nested
  @DisplayName("importAll")
  class Import {

    @Test
    @DisplayName("given_validRows_should_createThemNormalizedAndUnprotectedInOneBatch")
    void given_validRows_should_createThemNormalizedAndUnprotectedInOneBatch() {
      // Arrange
      tenantHas(tlpClear);
      MarkingRow first = row(1, " pap ", "pap:amber", "#ffc107", "3");
      MarkingRow second = row(2, "PAP", "PAP:RED", "#FF0000", "4");

      // Act
      ImportResult result = importAs(User.ADMIN_UUID, first, second);

      // Assert
      assertEquals(2, result.created());
      assertEquals(0, result.skipped());
      assertEquals(List.of("type", "definition", "color", "order"), result.columns());
      verify(repository, times(1)).saveAll(savedCaptor.capture());
      List<MarkingDefinition> saved = new java.util.ArrayList<>();
      savedCaptor.getValue().forEach(saved::add);
      assertEquals(
          List.of("PAP:AMBER", "PAP:RED"),
          saved.stream().map(MarkingDefinition::getDefinition).toList());
      assertTrue(saved.stream().allMatch(marking -> "PAP".equals(marking.getType())));
      assertTrue(saved.stream().noneMatch(MarkingDefinition::getProtectedDefinition));
      assertTrue(saved.stream().allMatch(marking -> TENANT_ID.equals(marking.getTenant().getId())));
    }

    private List<MarkingDefinition> savedMarkings() {
      verify(repository).saveAll(savedCaptor.capture());
      List<MarkingDefinition> saved = new java.util.ArrayList<>();
      savedCaptor.getValue().forEach(saved::add);
      return saved;
    }

    @Test
    @DisplayName("given_validRows_should_carryTheStixIdOpenCtiGivesThem")
    void given_validRows_should_carryTheStixIdOpenCtiGivesThem() {
      // Arrange
      tenantHas(tlpClear);

      // Act
      importAs(
          User.ADMIN_UUID,
          row(1, "pap", "pap:amber", "#ffc107", "3"),
          row(2, "TLP", "TLP:GREEN", "#4caf50", "2"));

      // Assert
      assertEquals(
          List.of(
              MarkingDefinitionStixIds.generate("PAP", "PAP:AMBER"),
              "marking-definition--34098fce-860f-48ae-8e50-ebd3cc5e41da"),
          savedMarkings().stream().map(MarkingDefinition::getStixId).toList());
    }

    @Test
    @DisplayName("given_aStixIdCarriedByTheTenant_should_createWithoutOne")
    void given_aStixIdCarriedByTheTenant_should_createWithoutOne() {
      // Arrange - TLP:WHITE next to TLP:CLEAR: OpenCTI names both with one id.
      tlpClear.setStixId("marking-definition--613f2e26-407d-48c7-9eca-b8e91df99dc9");
      tenantHas(tlpClear);

      // Act
      importAs(User.ADMIN_UUID, row(1, "TLP", "TLP:WHITE", "#ffffff", "5"));

      // Assert
      assertEquals(null, savedMarkings().get(0).getStixId());
    }

    @Test
    @DisplayName("given_aStixIdGivenByAnEarlierRow_should_createWithoutOne")
    void given_aStixIdGivenByAnEarlierRow_should_createWithoutOne() {
      // Arrange
      tenantHas(tlpGreen);

      // Act
      importAs(
          User.ADMIN_UUID,
          row(1, "TLP", "TLP:CLEAR", "#ffffff", "1"),
          row(2, "TLP", "TLP:WHITE", "#ffffff", "5"));

      // Assert
      List<MarkingDefinition> saved = savedMarkings();
      assertEquals(
          "marking-definition--613f2e26-407d-48c7-9eca-b8e91df99dc9", saved.get(0).getStixId());
      assertEquals(null, saved.get(1).getStixId());
    }

    @Test
    @DisplayName("given_createdRows_should_grantEvictAndCountOncePerBatch")
    void given_createdRows_should_grantEvictAndCountOncePerBatch() {
      // Arrange
      tenantHas(tlpClear);

      // Act
      importAs(
          User.ADMIN_UUID,
          row(1, "PAP", "PAP:CLEAR", "#ffffff", "1"),
          row(2, "PAP", "PAP:GREEN", "#4caf50", "2"));

      // Assert
      verify(tenantGroupService, times(1)).grantToAutoNewMarkingGroups(anyCollection());
      verify(markingClearanceCacheManager, times(1)).evictAll();
      verify(actionMetricCollector, times(1)).addMarkingDefinitionCreatedCount(2);
      verify(repository, never()).save(any(MarkingDefinition.class));
    }

    @Test
    @DisplayName("given_nothingToCreate_should_saveGrantAndEvictNothing")
    void given_nothingToCreate_should_saveGrantAndEvictNothing() {
      // Arrange
      tenantHas(tlpAmber);
      callerHolds("amber");

      // Act
      ImportResult result = importAs(USER_ID, row(1, "TLP", "TLP:AMBER", "#ffc107", "3"));

      // Assert
      assertEquals(1, result.ignored());
      verify(repository, never()).saveAll(any());
      verifyNoInteractions(tenantGroupService, actionMetricCollector);
      verify(markingClearanceCacheManager, never()).evictAll();
    }

    @Test
    @DisplayName("given_aMixedFile_should_createTheValidRowsSkipTheOthersAndKeepPositions")
    void given_aMixedFile_should_createTheValidRowsSkipTheOthersAndKeepPositions() {
      // Arrange
      tenantHas(tlpAmber);

      // Act
      ImportResult result =
          importAs(
              User.ADMIN_UUID,
              row(1, "PAP", "PAP:CLEAR", "#ffffff", "1"),
              row(2, "", "PAP:GREEN", "#4caf50", "2"),
              row(3, "TLP", "TLP:AMBER", "#ffc107", "3"),
              row(4, "PAP", "PAP:RED", "#ff0000", "4"));

      // Assert
      assertEquals(2, result.created());
      assertEquals(1, result.skipped());
      assertEquals(1, result.ignored());
      assertEquals(
          List.of(
              ImportRowStatus.CREATED,
              ImportRowStatus.SKIPPED,
              ImportRowStatus.IGNORED,
              ImportRowStatus.CREATED),
          result.rows().stream().map(ImportResultRow::status).toList());
      assertEquals(
          List.of(1, 2, 3, 4), result.rows().stream().map(ImportResultRow::position).toList());
    }

    @Test
    @DisplayName("given_aRow_should_echoItsValuesAsReadByFieldName")
    void given_aRow_should_echoItsValuesAsReadByFieldName() {
      // Arrange
      tenantHas(tlpClear);
      callerHolds("clear");

      // Act
      ImportResult result = importAs(USER_ID, row(1, "TLP", "TLP:X", "not-a-colour", "3"));

      // Assert
      assertEquals(
          Map.of("type", "TLP", "definition", "TLP:X", "color", "not-a-colour", "order", "3"),
          at(result, 1).values());
      assertEquals(
          List.of("type", "definition", "color", "order"),
          List.copyOf(at(result, 1).values().keySet()));
    }

    @Nested
    @DisplayName("row validation, first failure wins")
    class Validation {

      @ParameterizedTest(name = "[{index}] type={0} definition={1} color={2} order={3}")
      @CsvSource(
          value = {
            "'',TLP:X,#ffffff,1",
            "TLP,'',#ffffff,1",
            "TLP,TLP:X,'',1",
            "TLP,TLP:X,#ffffff,''"
          })
      @DisplayName("given_anEmptyValue_should_skipAsMissingField")
      void given_anEmptyValue_should_skipAsMissingField(
          String type, String definition, String color, String order) {
        // Arrange
        tenantHas(tlpClear);
        callerHolds("clear");

        // Act
        ImportResult result = importAs(USER_ID, row(1, type, definition, color, order));

        // Assert
        assertSkipped(at(result, 1), MarkingDefinitionImportRowReason.MISSING_FIELD, Map.of());
      }

      @Test
      @DisplayName("given_aTypeOrDefinitionLongerThanTheColumn_should_skipAsValueTooLongNotThrow")
      void given_aTypeOrDefinitionLongerThanTheColumn_should_skipAsValueTooLongNotThrow() {
        // Arrange
        tenantHas(tlpClear);
        callerHolds("clear");
        String tooLong = "X".repeat(256);

        // Act
        ImportResult result =
            importAs(
                USER_ID,
                row(1, "TLP", tooLong, "#ffffff", "1"),
                row(2, tooLong, "TLP:OK", "#ffffff", "1"));

        // Assert
        Map<String, String> params = Map.of(PARAM_MAX_LENGTH, "255");
        assertSkipped(at(result, 1), MarkingDefinitionImportRowReason.VALUE_TOO_LONG, params);
        assertSkipped(at(result, 2), MarkingDefinitionImportRowReason.VALUE_TOO_LONG, params);
        verify(repository, never()).saveAll(any());
      }

      @ParameterizedTest(name = "{0}")
      @ValueSource(strings = {"red", "#fff", "#ggg000", "ffffff", "#ffffff0"})
      @DisplayName("given_aColourThatIsNotAHexValue_should_skipAsInvalidColor")
      void given_aColourThatIsNotAHexValue_should_skipAsInvalidColor(String color) {
        // Arrange
        tenantHas(tlpClear);
        callerHolds("clear");

        // Act
        ImportResult result = importAs(USER_ID, row(1, "TLP", "TLP:X", color, "1"));

        // Assert
        assertSkipped(at(result, 1), MarkingDefinitionImportRowReason.INVALID_COLOR, Map.of());
      }

      @ParameterizedTest(name = "{0}")
      @ValueSource(strings = {"3.5", "abc", "-1", "1e3", "99999999999", " 3"})
      @DisplayName("given_anOrderThatIsNotAWholeNumber_should_skipAsInvalidOrder")
      void given_anOrderThatIsNotAWholeNumber_should_skipAsInvalidOrder(String order) {
        // Arrange
        tenantHas(tlpClear);
        callerHolds("clear");

        // Act
        ImportResult result = importAs(USER_ID, row(1, "TLP", "TLP:X", "#ffffff", order));

        // Assert
        assertSkipped(at(result, 1), MarkingDefinitionImportRowReason.INVALID_ORDER, Map.of());
      }
    }

    @Nested
    @DisplayName("same type and definition as another marking, never overwritten")
    class SameKey {

      @Test
      @DisplayName("given_anExistingMarkingWithAnotherColor_should_conflictWithTheColors")
      void given_anExistingMarkingWithAnotherColor_should_conflictWithTheColors() {
        // Arrange
        tenantHas(tlpAmber);
        callerHolds("amber");

        // Act
        ImportResult result = importAs(USER_ID, row(1, "tlp", "tlp:amber", "#ff0000", "3"));

        // Assert
        assertSkipped(
            at(result, 1),
            MarkingDefinitionImportRowReason.CONFLICT,
            Map.of(PARAM_COLOR, "#ff0000", PARAM_KNOWN_COLOR, "#ffc107"));
      }

      @Test
      @DisplayName("given_anExistingMarkingWithAnotherOrder_should_conflictWithTheOrders")
      void given_anExistingMarkingWithAnotherOrder_should_conflictWithTheOrders() {
        // Arrange
        tenantHas(tlpAmber);
        callerHolds("amber");

        // Act
        ImportResult result = importAs(USER_ID, row(1, "TLP", "TLP:AMBER", "#ffc107", "9"));

        // Assert
        assertSkipped(
            at(result, 1),
            MarkingDefinitionImportRowReason.CONFLICT,
            Map.of(PARAM_ORDER, "9", PARAM_KNOWN_ORDER, "3"));
      }

      @Test
      @DisplayName("given_aProtectedMarkingWithBothDifferent_should_conflictWithBoth")
      void given_aProtectedMarkingWithBothDifferent_should_conflictWithBoth() {
        // Arrange
        MarkingDefinition protectedAmber = marking("p", "TLP", "TLP:AMBER", 3, "#ffc107");
        protectedAmber.setProtectedDefinition(true);
        tenantHas(protectedAmber);
        callerHolds("p");

        // Act
        ImportResult result = importAs(USER_ID, row(1, "TLP", "TLP:AMBER", "#00ff00", "2"));

        // Assert
        assertSkipped(
            at(result, 1),
            MarkingDefinitionImportRowReason.CONFLICT,
            Map.of(
                PARAM_COLOR, "#00ff00",
                PARAM_KNOWN_COLOR, "#ffc107",
                PARAM_ORDER, "2",
                PARAM_KNOWN_ORDER, "3"));
        verify(repository, never()).save(any(MarkingDefinition.class));
        verify(repository, never()).saveAll(any());
      }

      @Test
      @DisplayName("given_anEarlierRowOfTheFile_should_conflictWithThatRow")
      void given_anEarlierRowOfTheFile_should_conflictWithThatRow() {
        // Arrange
        tenantHas(tlpClear);
        callerHolds("clear");

        // Act
        ImportResult result =
            importAs(
                USER_ID,
                row(1, "PAP", "PAP:AMBER", "#ffc107", "2"),
                row(2, "PAP", "PAP:AMBER", "#ffc107", "5"),
                row(3, "PAP", "PAP:AMBER", "#00ff00", "2"));

        // Assert
        assertEquals(ImportRowStatus.CREATED, at(result, 1).status());
        assertSkipped(
            at(result, 2),
            MarkingDefinitionImportRowReason.CONFLICT,
            Map.of(PARAM_EARLIER_ROW, "1", PARAM_ORDER, "5", PARAM_KNOWN_ORDER, "2"));
        assertSkipped(
            at(result, 3),
            MarkingDefinitionImportRowReason.CONFLICT,
            Map.of(PARAM_EARLIER_ROW, "1", PARAM_COLOR, "#00ff00", PARAM_KNOWN_COLOR, "#ffc107"));
      }

      @Test
      @DisplayName("given_aRowIdenticalToAnExistingOrEarlierRow_should_ignoreItWithNoReason")
      void given_aRowIdenticalToAnExistingOrEarlierRow_should_ignoreItWithNoReason() {
        // Arrange
        tenantHas(tlpAmber);
        callerHolds("amber");

        // Act
        ImportResult result =
            importAs(
                USER_ID,
                row(1, "TLP", "TLP:AMBER", "#FFC107", "3"),
                row(2, "PAP", "PAP:AMBER", "#ffc107", "3"),
                row(3, "PAP", "PAP:AMBER", "#ffc107", "3"));

        // Assert
        assertEquals(1, result.created());
        assertEquals(0, result.skipped());
        assertEquals(2, result.ignored());
        assertEquals(ImportRowStatus.IGNORED, at(result, 1).status());
        assertNull(at(result, 1).reason());
        assertTrue(at(result, 1).params().isEmpty());
        assertEquals(ImportRowStatus.IGNORED, at(result, 3).status());
      }

      @Test
      @DisplayName("given_aKeyOfTheTenantAndAnEarlierRow_should_compareWithTheExistingMarking")
      void given_aKeyOfTheTenantAndAnEarlierRow_should_compareWithTheExistingMarking() {
        // Arrange
        tenantHas(tlpAmber);
        callerHolds("amber");

        // Act
        ImportResult result =
            importAs(
                USER_ID,
                row(1, "TLP", "TLP:AMBER", "#ff0000", "3"),
                row(2, "TLP", "TLP:AMBER", "#ffc107", "3"));

        // Assert
        assertEquals(MarkingDefinitionImportRowReason.CONFLICT.name(), at(result, 1).reason());
        assertEquals(ImportRowStatus.IGNORED, at(result, 2).status());
      }
    }

    @Nested
    @DisplayName("clearance of the caller, as the manual create applies it")
    class Clearance {

      @Test
      @DisplayName("given_aCallerHoldingAmber_should_createUpToAmberAndSkipAboveWithBothOrders")
      void given_aCallerHoldingAmber_should_createUpToAmberAndSkipAboveWithBothOrders() {
        // Arrange
        tenantHas(tlpClear, tlpGreen, tlpAmber, tlpRed);
        callerHolds("clear", "green", "amber");

        // Act
        ImportResult result =
            importAs(
                USER_ID,
                row(1, "TLP", "TLP:CUSTOM3", "#ffffff", "3"),
                row(2, "TLP", "TLP:CUSTOM0", "#ffffff", "0"),
                row(3, "TLP", "TLP:CUSTOM4", "#ffffff", "4"));

        // Assert
        assertEquals(ImportRowStatus.CREATED, at(result, 1).status());
        assertEquals(ImportRowStatus.CREATED, at(result, 2).status());
        assertSkipped(
            at(result, 3),
            MarkingDefinitionImportRowReason.INSUFFICIENT_ACCESS,
            Map.of(PARAM_ORDER, "4", PARAM_HIGHEST_ORDER, "3"));
      }

      @Test
      @DisplayName("given_anExistingTypeTheCallerHoldsNothingOn_should_skipWithNoMarkingOfItsType")
      void given_anExistingTypeTheCallerHoldsNothingOn_should_skipWithNoMarkingOfItsType() {
        // Arrange
        MarkingDefinition papClear = marking("pap-clear", "PAP", "PAP:CLEAR", 1, "#ffffff");
        tenantHas(tlpAmber, papClear);
        callerHolds("amber");

        // Act
        ImportResult result = importAs(USER_ID, row(1, "PAP", "PAP:NEW", "#ffffff", "0"));

        // Assert
        assertSkipped(
            at(result, 1), MarkingDefinitionImportRowReason.INSUFFICIENT_ACCESS, Map.of());
      }

      @Test
      @DisplayName("given_aCallerWithNoGrantAtAll_should_skipEveryRowOfAnExistingType")
      void given_aCallerWithNoGrantAtAll_should_skipEveryRowOfAnExistingType() {
        // Arrange
        tenantHas(tlpClear);
        callerHolds();

        // Act
        ImportResult result =
            importAs(
                USER_ID,
                row(1, "TLP", "TLP:A", "#ffffff", "0"),
                row(2, "TLP", "TLP:B", "#ffffff", "1"));

        // Assert
        assertEquals(2, result.skipped());
        assertEquals(0, result.created());
      }

      @Test
      @DisplayName("given_aNewType_should_createItsFirstRowAndSkipTheNextOneOfThatType")
      void given_aNewType_should_createItsFirstRowAndSkipTheNextOneOfThatType() {
        // Arrange - the clearance is read once: the type created by the file widens nothing.
        tenantHas(tlpAmber);
        callerHolds("amber");

        // Act
        ImportResult result =
            importAs(
                USER_ID,
                row(1, "PAP", "PAP:RED", "#ff0000", "4"),
                row(2, "PAP", "PAP:CLEAR", "#ffffff", "1"));

        // Assert
        assertEquals(ImportRowStatus.CREATED, at(result, 1).status());
        assertSkipped(
            at(result, 2), MarkingDefinitionImportRowReason.INSUFFICIENT_ACCESS, Map.of());
        verify(markingClearanceCacheManager, times(1)).findClearance(USER_ID, TENANT_ID);
      }

      @Test
      @DisplayName("given_theFirstAdmin_should_createEveryRowWithoutReadingAClearance")
      void given_theFirstAdmin_should_createEveryRowWithoutReadingAClearance() {
        // Arrange
        tenantHas(tlpClear, tlpRed);

        // Act
        ImportResult result =
            importAs(User.ADMIN_UUID, row(1, "TLP", "TLP:ABOVE", "#ffffff", "99"));

        // Assert
        assertEquals(1, result.created());
        verify(markingClearanceCacheManager, never()).findClearance(any(), any());
      }

      @Test
      @DisplayName("given_aRowIdenticalToOrConflictingWithAnExistingMarking_should_keepThatOutcome")
      void given_aRowIdenticalToOrConflictingWithAnExistingMarking_should_keepThatOutcome() {
        // Arrange - re-importing an exported file must not flood a limited caller with access
        // errors
        tenantHas(tlpClear, tlpRed);
        callerHolds("clear");

        // Act
        ImportResult result =
            importAs(
                USER_ID,
                row(1, "TLP", "TLP:RED", "#ff0000", "4"),
                row(2, "TLP", "TLP:RED", "#00ff00", "4"));

        // Assert
        assertEquals(ImportRowStatus.IGNORED, at(result, 1).status());
        assertEquals(MarkingDefinitionImportRowReason.CONFLICT.name(), at(result, 2).reason());
      }

      @Test
      @DisplayName("given_aSkippedRow_should_notRegisterItsKeyForALaterRow")
      void given_aSkippedRow_should_notRegisterItsKeyForALaterRow() {
        // Arrange
        tenantHas(tlpClear, tlpGreen);
        callerHolds("clear", "green");

        // Act
        ImportResult result =
            importAs(
                USER_ID,
                row(1, "TLP", "TLP:CUSTOM", "#ffffff", "9"),
                row(2, "TLP", "TLP:CUSTOM", "#ffffff", "2"));

        // Assert
        assertEquals(
            MarkingDefinitionImportRowReason.INSUFFICIENT_ACCESS.name(), at(result, 1).reason());
        assertEquals(ImportRowStatus.CREATED, at(result, 2).status());
      }
    }
  }

  @Nested
  @DisplayName("import preconditions")
  class ImportPreconditions {

    @Test
    @DisplayName("given_theMarkingFlagOff_should_throwNotFound")
    void given_theMarkingFlagOff_should_throwNotFound() {
      // Arrange
      when(previewFeatureService.isFeatureEnabled(PreviewFeature.MARKING)).thenReturn(false);

      // Act & Assert
      assertThrows(ElementNotFoundException.class, () -> service().assertFeatureEnabled());
    }

    @Test
    @DisplayName("given_theMarkingFlagOn_should_notThrow")
    void given_theMarkingFlagOn_should_notThrow() {
      // Arrange
      when(previewFeatureService.isFeatureEnabled(PreviewFeature.MARKING)).thenReturn(true);

      // Act & Assert
      service().assertFeatureEnabled();
    }

    @Test
    @DisplayName("given_aCsvFileUnderTheLimit_should_accept")
    void given_aCsvFileUnderTheLimit_should_accept() {
      // Act & Assert
      service()
          .validateUploadedFile(
              new MockMultipartFile("file", "Markings.CSV", "text/csv", new byte[1024 * 1024]));
    }

    @Test
    @DisplayName("given_anotherExtension_should_refuseAsUnsupportedMediaType")
    void given_anotherExtension_should_refuseAsUnsupportedMediaType() {
      // Act & Assert
      assertThrows(
          UnsupportedMediaTypeException.class,
          () ->
              service()
                  .validateUploadedFile(
                      new MockMultipartFile("file", "markings.txt", "text/plain", new byte[1])));
    }

    @Test
    @DisplayName("given_aFileOverOneMegabyte_should_refuseAsTooBig")
    void given_aFileOverOneMegabyte_should_refuseAsTooBig() {
      // Act & Assert
      assertThrows(
          FileTooBigException.class,
          () ->
              service()
                  .validateUploadedFile(
                      new MockMultipartFile(
                          "file", "markings.csv", "text/csv", new byte[1024 * 1024 + 1])));
    }
  }
}
