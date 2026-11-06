package io.openaev.service.marking_definition;

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
import io.openaev.utils.FilterUtilsJpa;
import io.openaev.utils.TxCtxScopeUtils;
import io.openaev.utils.pagination.SearchPaginationInput;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.Collection;
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
  private final MarkingScopeSupplier markingScopeSupplier;
  private final JdbcTemplate jdbcTemplate;
  private final TenantGroupService tenantGroupService;

  // -- SEARCH --

  /**
   * Searches the marking definitions the caller is cleared for within the current tenant scope.
   *
   * <p>Narrowed to the caller's resolved clearance (cumulative per type, unioned across the tenants
   * in scope, see {@link MarkingScopeSupplier#clearanceFor}) - the same set that decides which
   * marked rows they can read - so a definition the caller holds no clearance for is not revealed.
   * Only the first admin resolves to every definition of the tenants in scope.
   *
   * @param ctx transaction context containing tenant scope
   * @param searchPaginationInput pagination and filter criteria
   * @return page of matching marking definitions the caller is cleared for
   */
  @Transactional(readOnly = true)
  public Page<MarkingDefinition> search(
      @NotNull TxCtx ctx, @NotNull SearchPaginationInput searchPaginationInput) {
    Set<String> tenantIds = TxCtxScopeUtils.tenantIdsFromHTTPCtx(ctx);
    MarkingCtx clearance = markingScopeSupplier.clearanceFor(ctx);
    return buildPaginationJPA(
        (specification, pageable) ->
            findAllByTenantIdsAndClearance(tenantIds, clearance, specification, pageable),
        searchPaginationInput,
        MarkingDefinition.class);
  }

  // -- READ --

  /**
   * Lists the tenant's marking definitions narrowed to what the caller is cleared to assign - the
   * same clearance {@link io.openaev.service.marking.MarkingEscalationValidator} enforces when an
   * assignment is actually attempted (cumulative per type: holding {@code TLP:AMBER} also clears
   * {@code TLP:GREEN} and {@code TLP:CLEAR}, see {@link io.openaev.config.MarkingScopeResolver}).
   * Meant for populating an assignment picker with only the options a submission would actually be
   * allowed to include, instead of offering every definition and rejecting the ones the caller
   * doesn't hold at submit time.
   *
   * <p>Deliberately the <b>write</b> clearance, not {@link MarkingScopeSupplier#clearanceFor}: the
   * read clearance unions every tenant in scope, which would offer definitions of another tenant
   * the assignment then rejects.
   *
   * @param ctx transaction context containing tenant scope
   * @param tenantId the single tenant whose definitions and clearance are being read
   * @param userId the current user identifier, whose clearance narrows the result
   * @return marking definitions the caller may assign, ascending by order
   */
  @Transactional(readOnly = true)
  public List<MarkingDefinition> listAssignable(
      @NotNull TxCtx ctx, @NotBlank String tenantId, @NotBlank String userId) {
    // Scoped to the one tenant the clearance was resolved for, as the assignment itself is.
    MarkingCtx clearance = markingClearanceCacheManager.findClearance(userId, tenantId);
    Specification<MarkingDefinition> specification =
        tenantAndClearanceSpecification(Set.of(tenantId), clearance);
    if (specification == null) {
      return List.of();
    }
    return repository.findAll(specification, Sort.by("order").ascending());
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
    Specification<MarkingDefinition> held =
        tenantAndClearanceSpecification(Set.of(tenantId), clearance);
    assertCanDefineMarking(
        held == null ? List.of() : repository.findAll(held.and(typeSpecification(type))),
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
    validateUniqueOrThrow(type, definition, tenantId, existing.getId());
    boolean orderChanged = !Objects.equals(existing.getOrder(), input.order());
    if (orderChanged) {
      assertWithinOwnClearance(tenantId, type, input.order(), userId);
    }
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

  // -- OPTIONS --

  /**
   * Resolves marking definitions in the current tenant scope by id, for labeling filter values
   * already selected elsewhere (e.g. an asset's {@code marking_ids} array).
   *
   * @param ctx transaction context containing tenant scope
   * @param ids marking definition ids to resolve
   * @return one option per id found, label formatted as {@code "{type}:{definition}"}
   */
  @Transactional(readOnly = true)
  public List<FilterUtilsJpa.Option> optionsById(@NotNull TxCtx ctx, @NotNull List<String> ids) {
    Set<String> tenantIds = TxCtxScopeUtils.tenantIdsFromHTTPCtx(ctx);
    MarkingCtx clearance = markingScopeSupplier.clearanceFor(ctx);
    Specification<MarkingDefinition> scope = tenantAndClearanceSpecification(tenantIds, clearance);
    if (tenantIds.isEmpty() || ids.isEmpty() || scope == null) {
      return List.of();
    }
    return repository.findAll(scope.and(idsSpecification(ids))).stream()
        .map(MarkingDefinitionService::toOption)
        .toList();
  }

  /**
   * Searches marking definitions in the current tenant scope by a free-text match on type or
   * definition, for populating a filter's dynamic value picker.
   *
   * @param ctx transaction context containing tenant scope
   * @param searchText optional substring to match (case-insensitive) against type or definition
   * @return matching options, ascending by {@code order}
   */
  @Transactional(readOnly = true)
  public List<FilterUtilsJpa.Option> optionsByName(@NotNull TxCtx ctx, String searchText) {
    Set<String> tenantIds = TxCtxScopeUtils.tenantIdsFromHTTPCtx(ctx);
    MarkingCtx clearance = markingScopeSupplier.clearanceFor(ctx);
    Specification<MarkingDefinition> specification =
        tenantAndClearanceSpecification(tenantIds, clearance);
    if (tenantIds.isEmpty() || specification == null) {
      return List.of();
    }

    if (searchText != null && !searchText.isBlank()) {
      specification = specification.and(byNameSpecification(searchText));
    }
    return repository.findAll(specification, Sort.by("order").ascending()).stream()
        .map(MarkingDefinitionService::toOption)
        .toList();
  }

  private static Specification<MarkingDefinition> idsSpecification(Collection<String> ids) {
    return (root, query, criteriaBuilder) -> root.get("id").in(ids);
  }

  private static Specification<MarkingDefinition> typeSpecification(String type) {
    return (root, query, criteriaBuilder) -> criteriaBuilder.equal(root.get("type"), type);
  }

  private static Specification<MarkingDefinition> byNameSpecification(String searchText) {
    String pattern = "%" + searchText.trim().toLowerCase(Locale.ROOT) + "%";
    return (root, query, criteriaBuilder) ->
        criteriaBuilder.or(
            criteriaBuilder.like(criteriaBuilder.lower(root.get("type")), pattern),
            criteriaBuilder.like(criteriaBuilder.lower(root.get("definition")), pattern));
  }

  private static FilterUtilsJpa.Option toOption(MarkingDefinition definition) {
    String type = definition.getType();
    String value = definition.getDefinition();
    String label = value.startsWith(type + ":") ? value : type + ":" + value;
    return new FilterUtilsJpa.Option(definition.getId(), label);
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

  private Page<MarkingDefinition> findAllByTenantIdsAndClearance(
      Set<String> tenantIds,
      MarkingCtx clearance,
      Specification<MarkingDefinition> specification,
      org.springframework.data.domain.Pageable pageable) {
    Specification<MarkingDefinition> scope = tenantAndClearanceSpecification(tenantIds, clearance);
    if (tenantIds.isEmpty() || scope == null) {
      return Page.empty(pageable);
    }
    return repository.findAll(scope.and(specification), pageable);
  }

  /**
   * Gives a specification to find marking definitions based on tenants and correct clearance of the
   * caller.
   *
   * @param tenantIds the tenants identifiers to restrict the search
   * @param clearance the clearance to resolve through
   * @return null when the clearance grants nothing: the caller short-circuits to an empty result
   *     instead of querying.
   */
  private Specification<MarkingDefinition> tenantAndClearanceSpecification(
      Set<String> tenantIds, MarkingCtx clearance) {
    return switch (clearance) {
      case MarkingCtx.None ignored -> null;
      case MarkingCtx.Restricted restricted ->
          tenantSpecification(tenantIds).and(idsSpecification(restricted.markingIds()));
      // An unresolved background-only intention that neither MarkingScopeSupplier#clearanceFor nor
      // MarkingClearanceCacheManager#findClearance returns on the HTTP path: handled as "sees
      // everything" to keep the switch exhaustive.
      case MarkingCtx.All ignored -> tenantSpecification(tenantIds);
    };
  }

  private Specification<MarkingDefinition> tenantSpecification(Set<String> tenantIds) {
    return (root, query, criteriaBuilder) -> root.get("tenant").get("id").in(tenantIds);
  }
}
