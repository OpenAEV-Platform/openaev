package io.openaev.service.marking_definition;

import static io.openaev.utils.pagination.PaginationUtils.buildPaginationJPA;

import io.openaev.annotation.AllowRawJdbc;
import io.openaev.api.marking_definition.MarkingDefinitionMapper;
import io.openaev.api.marking_definition.form.MarkingDefinitionInput;
import io.openaev.config.AllTablesWithMarkingIds;
import io.openaev.config.cache.MarkingClearanceCacheManager;
import io.openaev.context.MarkingCtx;
import io.openaev.context.TxCtx;
import io.openaev.database.model.MarkingDefinition;
import io.openaev.database.model.Tenant;
import io.openaev.database.repository.MarkingDefinitionRepository;
import io.openaev.rest.exception.BadRequestException;
import io.openaev.rest.exception.ElementNotFoundException;
import io.openaev.telemetry.metric_collectors.ActionMetricCollector;
import io.openaev.utils.TxCtxScopeUtils;
import io.openaev.utils.pagination.SearchPaginationInput;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
@RequiredArgsConstructor
@Transactional(rollbackFor = Exception.class)
@AllowRawJdbc(
    reason =
        "delete-time scrub of marking_ids arrays across every marking-active table (§3.2 of"
            + " tech-design-option-c.md): the table names come from MarkedTables at runtime, so this"
            + " cannot be expressed through JPA/Specification. Safe without an explicit tenant filter:"
            + " a marking definition belongs to exactly one tenant, so its id can never appear in"
            + " another tenant's rows in the first place - the id itself is what scopes the update.")
public class MarkingDefinitionService {

  private final MarkingDefinitionRepository repository;
  private final ActionMetricCollector actionMetricCollector;
  private final AllTablesWithMarkingIds allTablesWithMarkingIds;
  private final MarkingClearanceCacheManager markingClearanceCacheManager;
  private final JdbcTemplate jdbcTemplate;

  // -- SEARCH --

  /**
   * Searches marking definitions within the current tenant scope.
   *
   * @param ctx transaction context containing tenant scope
   * @param searchPaginationInput pagination and filter criteria
   * @return page of matching marking definitions
   */
  @Transactional(readOnly = true)
  public Page<MarkingDefinition> search(
      @NotNull TxCtx ctx, @NotNull SearchPaginationInput searchPaginationInput) {
    Set<String> tenantIds = TxCtxScopeUtils.tenantIdsFromHTTPCtx(ctx);
    return buildPaginationJPA(
        (specification, pageable) -> findAllByTenantIds(tenantIds, specification, pageable),
        searchPaginationInput,
        MarkingDefinition.class);
  }

  // -- READ --

  /**
   * Lists all marking definitions visible in the current tenant scope.
   *
   * @param ctx transaction context containing tenant scope
   * @return marking definitions visible to the caller
   */
  @Transactional(readOnly = true)
  public List<MarkingDefinition> list(@NotNull TxCtx ctx) {
    return listInternal(ctx);
  }

  // Non-transactional body shared by every @Transactional entry point that needs "every
  // definition in scope" - an intra-class call to a @Transactional method bypasses the Spring
  // proxy (self-invocation), so neither the transaction nor tenant-scope activation runs for the
  // inner call. list() and listAssignable() both call this instead of calling each other.
  private List<MarkingDefinition> listInternal(@NotNull TxCtx ctx) {
    Set<String> tenantIds = TxCtxScopeUtils.tenantIdsFromHTTPCtx(ctx);
    if (tenantIds.isEmpty()) {
      return List.of();
    }
    return repository.findAll(tenantSpecification(tenantIds), Sort.by("order").ascending());
  }

  /**
   * Lists the tenant's marking definitions narrowed to what the caller is cleared to assign - the
   * same clearance {@link io.openaev.service.marking.MarkingEscalationValidator} enforces when an
   * assignment is actually attempted (cumulative per type: holding {@code TLP:AMBER} also clears
   * {@code TLP:GREEN} and {@code TLP:CLEAR}, see {@link io.openaev.config.MarkingScopeResolver}).
   * Meant for populating an assignment picker with only the options a submission would actually be
   * allowed to include, instead of offering every definition and rejecting the ones the caller
   * doesn't hold at submit time.
   *
   * @param ctx transaction context containing tenant scope
   * @param tenantId the single tenant whose definitions and clearance are being read
   * @param userId the caller, whose clearance narrows the result
   * @param bypass whether the caller is an admin/bypass identity - resolves to every tenant id
   * @return marking definitions the caller may assign, ascending by order
   */
  @Transactional(readOnly = true)
  public List<MarkingDefinition> listAssignable(
      @NotNull TxCtx ctx, @NotBlank String tenantId, @NotBlank String userId, boolean bypass) {
    List<MarkingDefinition> all = listInternal(ctx);
    MarkingCtx clearance = markingClearanceCacheManager.findClearance(userId, tenantId, bypass);
    return switch (clearance) {
      case MarkingCtx.None ignored -> List.of();
      case MarkingCtx.Restricted restricted -> {
        Set<String> held = Set.copyOf(restricted.markingIds());
        yield all.stream().filter(definition -> held.contains(definition.getId())).toList();
      }
      // All is an unresolved, background-only intention (see MarkingCtx's javadoc) that
      // findClearance never actually returns on the HTTP path - bypass resolves to a Restricted
      // set of every tenant id instead. Handled defensively as "sees everything" to keep this
      // switch exhaustive without assuming that invariant holds forever.
      case MarkingCtx.All ignored -> all;
    };
  }

  private MarkingDefinition findByIdOrThrow(
      @NotNull TxCtx ctx, @NotBlank String markingDefinitionId) {
    MarkingDefinition markingDefinition =
        repository
            .findById(markingDefinitionId)
            .orElseThrow(() -> new ElementNotFoundException("Marking definition not found"));
    Set<String> tenantIds = TxCtxScopeUtils.tenantIdsFromHTTPCtx(ctx);
    if (!tenantIds.contains(markingDefinition.getTenant().getId())) {
      throw new ElementNotFoundException("Marking definition not found");
    }
    return markingDefinition;
  }

  // -- CREATE --

  /**
   * Creates a marking definition for a tenant after duplicate checks.
   *
   * <p>🔴 Evicts every cached clearance, for the same reason as an order change (see {@link
   * #update}): {@link MarkingClearanceCacheManager#findClearance} caches the <i>whole resolved
   * set</i>, bypass included. A cached bypass entry was resolved against the tenant's definitions
   * as they stood before this call, so it does not contain the new id — without the evict, a bypass
   * caller who warmed their cache before creating this definition stays unable to assign or read it
   * until the TTL expires, even though bypass should see everything unconditionally.
   *
   * @param input create payload
   * @param tenantId tenant that owns the new row
   * @return persisted marking definition
   */
  public MarkingDefinition create(
      @NotNull MarkingDefinitionInput input, @NotBlank String tenantId) {
    String type = normalize(input.type());
    String definition = normalize(input.definition());
    validateUniqueOrThrow(type, definition, tenantId, null);
    MarkingDefinition entity = MarkingDefinitionMapper.fromInput(input);
    entity.setType(type);
    entity.setDefinition(definition);
    entity.setProtectedDefinition(false);
    entity.setTenant(new Tenant(tenantId));
    MarkingDefinition saved = repository.save(entity);
    markingClearanceCacheManager.evictAll();
    runAfterCommit(actionMetricCollector::addMarkingDefinitionCreatedCount);
    return saved;
  }

  // -- UPDATE --

  /**
   * Updates mutable fields of a marking definition while preserving immutable type and protection.
   *
   * @param ctx transaction context containing tenant scope
   * @param markingDefinitionId identifier of the marking definition
   * @param input update payload
   * @return updated marking definition
   */
  public MarkingDefinition update(
      @NotNull TxCtx ctx,
      @NotBlank String markingDefinitionId,
      @NotNull MarkingDefinitionInput input) {
    MarkingDefinition existing = findByIdOrThrow(ctx, markingDefinitionId);
    if (Boolean.TRUE.equals(existing.getProtectedDefinition())) {
      throw new BadRequestException("Protected marking definitions cannot be updated");
    }
    String type = normalize(input.type());
    String definition = normalize(input.definition());
    if (!Objects.equals(existing.getType(), type)) {
      throw new BadRequestException("Marking definition type is immutable");
    }
    validateUniqueOrThrow(type, definition, existing.getTenant().getId(), existing.getId());
    boolean orderChanged = !Objects.equals(existing.getOrder(), input.order());
    existing.setDefinition(definition);
    existing.setColor(input.color());
    existing.setOrder(input.order());
    MarkingDefinition saved = repository.save(existing);
    if (orderChanged) {
      // MarkingScopeResolver expands ordinality from `order`, per type: a clearance cached before
      // this change may now resolve to a different id set for every user holding a grant on this
      // type, not just this definition's own id - narrow eviction cannot express that, so this is
      // the one case that always pays for evictAll().
      markingClearanceCacheManager.evictAll();
    }
    runAfterCommit(actionMetricCollector::addMarkingDefinitionUpdatedCount);
    return saved;
  }

  // -- DELETE --

  /**
   * Deletes a marking definition when it is not protected.
   *
   * <p>Option 2 has no FK on {@code marking_ids} (tech-design-option-c.md §3.2), so nothing
   * cascades into the arrays: {@code groups_markings} grants are removed by the schema's {@code ON
   * DELETE CASCADE}, but the deleted id would otherwise survive inside every row's array forever,
   * hiding those rows from the entire platform with no error and no way back through the API. The
   * PO decision is that a definition must stay hard-deletable even while still assigned, so the
   * scrub below - not archiving - is the chosen mitigation.
   *
   * @param ctx transaction context containing tenant scope
   * @param markingDefinitionId identifier of the marking definition
   */
  public void delete(@NotNull TxCtx ctx, @NotBlank String markingDefinitionId) {
    MarkingDefinition existing = findByIdOrThrow(ctx, markingDefinitionId);
    if (Boolean.TRUE.equals(existing.getProtectedDefinition())) {
      throw new BadRequestException("Protected marking definitions cannot be deleted");
    }
    repository.delete(existing);
    scrubMarkingIds(markingDefinitionId);
    // groups_markings grants are already gone via the FK cascade; a clearance derived from them
    // before the delete is still cached and would otherwise keep granting access to this id.
    markingClearanceCacheManager.evictAll();
  }

  /**
   * Removes {@code markingDefinitionId} from the {@code marking_ids} array of every table the
   * schema marks — not just the currently active ones.
   *
   * <p>Schema-driven off {@link AllTablesWithMarkingIds}, deliberately <b>not</b> the
   * allowlist-narrowed {@link io.openaev.config.MarkedTables} the statement inspector filters
   * against: a marking write is not feature-gated, so a table can carry {@code marking_ids} values
   * while inactive, and this id must not survive as a dangling entry that resurfaces the moment the
   * table is later activated.
   *
   * <p>The {@code @>} containment guard (tech-design-option-c.md §3.2, mitigation 2) lets the GIN
   * index select candidate rows; without it every row of every marked table would be rewritten
   * regardless of whether it holds the id.
   */
  private void scrubMarkingIds(String markingDefinitionId) {
    for (String table : allTablesWithMarkingIds.tableNames()) {
      jdbcTemplate.update(
          "UPDATE "
              + table
              + " SET marking_ids = array_remove(marking_ids, ?) WHERE marking_ids @> ARRAY[?]::text[]",
          markingDefinitionId,
          markingDefinitionId);
    }
  }

  /**
   * Defers {@code action} until the surrounding transaction commits, matching {@link
   * io.openaev.service.tenants.TenantService}'s pattern for post-commit side effects: {@code
   * repository.save} may not flush until commit, so running the metric increment eagerly would
   * record a creation/update even if a later step (mapping, constraint, or commit failure) rolls
   * the transaction back. Falls back to running immediately when no transaction is active (e.g.
   * direct unit invocation outside a Spring transaction).
   */
  private void runAfterCommit(Runnable action) {
    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.registerSynchronization(
          new TransactionSynchronization() {
            @Override
            public void afterCommit() {
              action.run();
            }
          });
    } else {
      action.run();
    }
  }

  private void validateUniqueOrThrow(
      String type, String definition, String tenantId, String ignoredId) {
    boolean duplicateExists =
        repository.existsByTypeAndDefinitionAndTenantIdExcludingId(
            type, definition, tenantId, ignoredId);
    if (duplicateExists) {
      throw new BadRequestException(
          "A marking definition with the same type and definition already exists");
    }
  }

  /**
   * Canonicalizes to upper-case so {@code type} is never compared case-sensitively downstream:
   * {@link io.openaev.config.MarkingScopeResolver} groups a caller's clearance by {@code type} in a
   * plain {@code HashMap}.
   */
  private static String normalize(String value) {
    return value == null ? null : value.trim().toUpperCase(Locale.ROOT);
  }

  private Page<MarkingDefinition> findAllByTenantIds(
      Set<String> tenantIds,
      Specification<MarkingDefinition> specification,
      org.springframework.data.domain.Pageable pageable) {
    if (tenantIds.isEmpty()) {
      return Page.empty(pageable);
    }
    return repository.findAll(tenantSpecification(tenantIds).and(specification), pageable);
  }

  private Specification<MarkingDefinition> tenantSpecification(Set<String> tenantIds) {
    return (root, query, criteriaBuilder) -> root.get("tenant").get("id").in(tenantIds);
  }
}
