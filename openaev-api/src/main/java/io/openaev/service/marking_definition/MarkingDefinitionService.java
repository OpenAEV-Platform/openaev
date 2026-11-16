package io.openaev.service.marking_definition;

import static io.openaev.database.specification.MarkingDefinitionSpecification.tenantAndClearanceSpecification;
import static io.openaev.database.specification.MarkingDefinitionSpecification.tenantSpecification;
import static io.openaev.database.specification.MarkingDefinitionSpecification.typeSpecification;
import static io.openaev.service.marking.MarkingEscalationValidator.assertCanDefineMarking;
import static io.openaev.utils.pagination.PaginationUtils.buildPaginationJPA;

import io.openaev.annotation.AllowRawJdbc;
import io.openaev.api.marking_definition.MarkingDefinitionMapper;
import io.openaev.api.marking_definition.form.MarkingDefinitionInput;
import io.openaev.config.AllTablesWithMarkingIds;
import io.openaev.config.cache.MarkingClearanceCacheManager;
import io.openaev.context.MarkingCtx;
import io.openaev.context.MarkingScopeSupplier;
import io.openaev.context.TxCtx;
import io.openaev.database.model.MarkingDefinition;
import io.openaev.database.model.Tenant;
import io.openaev.database.model.User;
import io.openaev.database.repository.MarkingDefinitionRepository;
import io.openaev.rest.exception.BadRequestException;
import io.openaev.rest.exception.ElementNotFoundException;
import io.openaev.service.TenantGroupService;
import io.openaev.telemetry.metric_collectors.ActionMetricCollector;
import io.openaev.utils.TxCtxScopeUtils;
import io.openaev.utils.pagination.SearchPaginationInput;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
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
  private final MarkingScopeSupplier markingScopeSupplier;
  private final JdbcTemplate jdbcTemplate;
  private final TenantGroupService tenantGroupService;

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
   * Lists the marking definitions of the tenants in scope narrowed to what the caller is cleared
   * for - the same clearance {@link io.openaev.service.marking.MarkingEscalationValidator} enforces
   * when an assignment is actually attempted (cumulative per type: holding {@code TLP:AMBER} also
   * clears {@code TLP:GREEN} and {@code TLP:CLEAR}, see {@link
   * io.openaev.config.MarkingScopeResolver}). Serves both the assignment picker, which then only
   * offers what a submission is allowed to include, and the marking filter, where a definition
   * above the caller's clearance could only match rows they cannot see.
   *
   * <p>The clearance is {@link MarkingScopeSupplier#clearanceFor}, the union of the caller's
   * clearance in each tenant in scope. A definition belongs to exactly one tenant, so this is the
   * same set as resolving each tenant on its own. An assignment needs a single-tenant scope, in
   * which the union is that tenant's clearance.
   *
   * @param ctx transaction context containing tenant scope
   * @return marking definitions the caller is cleared for, ascending by order
   */
  @Transactional(readOnly = true)
  public List<MarkingDefinition> listAssignable(@NotNull TxCtx ctx) {
    Set<String> tenantIds = TxCtxScopeUtils.tenantIdsFromHTTPCtx(ctx);
    Optional<Specification<MarkingDefinition>> specificationOpt =
        tenantAndClearanceSpecification(tenantIds, markingScopeSupplier.clearanceFor(ctx));
    return specificationOpt
        .map(
            markingDefinitionSpecification ->
                repository.findAll(markingDefinitionSpecification, Sort.by("order").ascending()))
        .orElseGet(List::of);
  }

  /**
   * Tells whether a marking definition is currently assigned, i.e. whether its id appears in the
   * {@code marking_ids} array of any row of any marked table, or granted to a group through {@code
   * groups_markings}.
   *
   * <p>A definition the caller is not cleared for is not found, as it is not revealed by {@link
   * #search} either.
   *
   * @param ctx transaction context containing tenant scope
   * @param markingDefinitionId identifier of the marking definition
   * @return {@code true} if at least one marked entity carries the definition or at least one group
   *     is granted it
   */
  @Transactional(readOnly = true)
  public boolean isAssigned(@NotNull TxCtx ctx, @NotBlank String markingDefinitionId) {
    MarkingDefinition existing = findByIdOrThrow(ctx, markingDefinitionId);
    assertClearedOrThrow(existing, markingScopeSupplier.clearanceFor(ctx));
    for (String table : allTablesWithMarkingIds.tableNames()) {
      Boolean assigned =
          jdbcTemplate.queryForObject(
              "SELECT EXISTS (SELECT 1 FROM "
                  + table
                  + " WHERE COALESCE(marking_ids, '{}'::text[]) @> ARRAY[?]::text[])",
              Boolean.class,
              markingDefinitionId);
      if (Boolean.TRUE.equals(assigned)) {
        return true;
      }
    }
    return Boolean.TRUE.equals(
        jdbcTemplate.queryForObject(
            "SELECT EXISTS (SELECT 1 FROM groups_markings WHERE marking_id = ?)",
            Boolean.class,
            markingDefinitionId));
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

  // Not found rather than forbidden: search does not reveal a definition above the caller's
  // clearance either, so a by-id call must not confirm it exists.
  private static void assertClearedOrThrow(MarkingDefinition definition, MarkingCtx clearance) {
    boolean cleared =
        switch (clearance) {
          case MarkingCtx.None ignored -> false;
          case MarkingCtx.Restricted restricted ->
              restricted.markingIds().contains(definition.getId());
          // Same defensive reading as tenantAndClearanceSpecification.
          case MarkingCtx.All ignored -> true;
        };
    if (!cleared) {
      throw new ElementNotFoundException("Marking definition not found");
    }
  }

  private boolean typeExists(String tenantId, String type) {
    return repository.exists(tenantSpecification(Set.of(tenantId)).and(typeSpecification(type)));
  }

  private void assertWithinOwnClearance(String tenantId, String type, int order, String userId) {
    // The first admin holds every level, including those above a type's current top - which a
    // resolved set of today's definitions cannot express.
    if (User.ADMIN_UUID.equals(userId)) {
      return;
    }
    MarkingCtx clearance = markingClearanceCacheManager.findClearance(userId, tenantId);
    Optional<Specification<MarkingDefinition>> heldOpt =
        tenantAndClearanceSpecification(Set.of(tenantId), clearance);
    assertCanDefineMarking(
        heldOpt
            .map(
                markingDefinitionSpecification ->
                    repository.findAll(markingDefinitionSpecification.and(typeSpecification(type))))
            .orElseGet(List::of),
        type,
        order);
  }

  // -- CREATE --

  /**
   * Creates a marking definition for a tenant after duplicate checks.
   *
   * <p>🔴 Evicts every cached clearance, for the same reason as an order change (see {@link
   * #update}): {@link MarkingClearanceCacheManager#findClearance} caches the <i>whole resolved
   * set</i>. The first admin's cached entry was resolved against the tenant's definitions as they
   * stood before this call, so it does not contain the new id — without the evict, they stay unable
   * to assign or read it until the TTL expires, even though they should see everything.
   *
   * <p>Refused above the caller's own clearance on its type, see {@link
   * io.openaev.service.marking.MarkingEscalationValidator#assertCanDefineMarking}, except for the
   * first definition of a type the tenant does not have yet: a new scale marks no row and clears no
   * group, so placing it discloses nothing.
   *
   * @param input create payload
   * @param tenantId tenant that owns the new row
   * @param userId the current user, whose clearance bounds the order
   * @return persisted marking definition
   */
  public MarkingDefinition create(
      @NotNull MarkingDefinitionInput input, @NotBlank String tenantId, @NotBlank String userId) {
    String type = normalize(input.type());
    String definition = normalize(input.definition());
    if (typeExists(tenantId, type)) {
      assertWithinOwnClearance(tenantId, type, input.order(), userId);
    }
    validateUniqueOrThrow(type, definition, tenantId, null);
    MarkingDefinition entity = MarkingDefinitionMapper.fromInput(input);
    entity.setType(type);
    entity.setDefinition(definition);
    entity.setProtectedDefinition(false);
    entity.setTenant(new Tenant(tenantId));
    MarkingDefinition saved = repository.save(entity);
    tenantGroupService.grantToAutoNewMarkingGroups(saved);
    markingClearanceCacheManager.evictAll();
    runAfterCommit(actionMetricCollector::addMarkingDefinitionCreatedCount);
    return saved;
  }

  // -- UPDATE --

  /**
   * Updates mutable fields of a marking definition while preserving immutable type and protection.
   *
   * <p>A definition above the caller's clearance is not found, and a new order may not take it
   * above that clearance either: lowering an uncleared definition into the caller's range would
   * grant them every row it marks, and raising one of theirs above it would lock them out of it.
   *
   * @param ctx transaction context containing tenant scope
   * @param markingDefinitionId identifier of the marking definition
   * @param input update payload
   * @param userId the current user, whose clearance bounds the definitions they may change
   * @return updated marking definition
   */
  public MarkingDefinition update(
      @NotNull TxCtx ctx,
      @NotBlank String markingDefinitionId,
      @NotNull MarkingDefinitionInput input,
      @NotBlank String userId) {
    MarkingDefinition existing = findByIdOrThrow(ctx, markingDefinitionId);
    String tenantId = existing.getTenant().getId();
    assertClearedOrThrow(existing, markingClearanceCacheManager.findClearance(userId, tenantId));
    if (Boolean.TRUE.equals(existing.getProtectedDefinition())) {
      throw new BadRequestException("Protected marking definitions cannot be updated");
    }
    String type = normalize(input.type());
    String definition = normalize(input.definition());
    if (!Objects.equals(existing.getType(), type)) {
      throw new BadRequestException("Marking definition type is immutable");
    }
    boolean orderChanged = !Objects.equals(existing.getOrder(), input.order());
    // Before the duplicate check: a duplicate-name 400 on a forbidden order would reveal that a
    // definition above the caller's clearance carries that name.
    if (orderChanged) {
      assertWithinOwnClearance(tenantId, type, input.order(), userId);
    }
    validateUniqueOrThrow(type, definition, tenantId, existing.getId());
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
   * <p>A definition above the caller's clearance is not found: the scrub would declassify every row
   * it marks, and a caller cannot declassify what they cannot see.
   *
   * @param ctx transaction context containing tenant scope
   * @param markingDefinitionId identifier of the marking definition
   * @param userId the current user, whose clearance bounds the definitions they may delete
   */
  public void delete(
      @NotNull TxCtx ctx, @NotBlank String markingDefinitionId, @NotBlank String userId) {
    MarkingDefinition existing = findByIdOrThrow(ctx, markingDefinitionId);
    assertClearedOrThrow(
        existing, markingClearanceCacheManager.findClearance(userId, existing.getTenant().getId()));
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
              + " SET marking_ids = array_remove(marking_ids, ?) WHERE COALESCE(marking_ids, '{}'::text[]) @> ARRAY[?]::text[]",
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
}
