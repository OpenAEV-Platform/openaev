package io.openaev.service;

import static io.openaev.config.SessionHelper.currentUser;
import static io.openaev.helper.StreamHelper.fromIterable;
import static io.openaev.helper.StreamHelper.iterableToSet;
import static io.openaev.utils.pagination.PaginationUtils.buildPaginationCriteriaBuilder;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.openaev.context.TenantContext;
import io.openaev.context.TxCtx;
import io.openaev.database.model.*;
import io.openaev.database.repository.*;
import io.openaev.database.specification.InjectSpecification;
import io.openaev.database.specification.SpecificationUtils;
import io.openaev.rest.atomic_testing.form.*;
import io.openaev.rest.exception.ElementNotFoundException;
import io.openaev.rest.inject.form.InjectBulkProcessingInput;
import io.openaev.rest.inject.service.InjectService;
import io.openaev.service.utils.BulkDeleteExecutor;
import io.openaev.telemetry.metric_collectors.ActionMetricCollector;
import io.openaev.telemetry.metric_collectors.CredentialMetricCollector;
import io.openaev.utils.InjectUtils;
import io.openaev.utils.injector_contract.InjectorContractContentUtils;
import io.openaev.utils.mapper.InjectMapper;
import io.openaev.utils.mapper.PayloadMapper;
import io.openaev.utils.pagination.SearchPaginationInput;
import jakarta.annotation.Nullable;
import jakarta.annotation.Resource;
import jakarta.persistence.criteria.Join;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@RequiredArgsConstructor
public class AtomicTestingService {

  @Resource protected ObjectMapper mapper;
  private final InjectMapper injectMapper;
  private final ActionMetricCollector actionMetricCollector;
  private final CredentialMetricCollector credentialMetricCollector;

  private final AssetGroupRepository assetGroupRepository;
  private final AssetRepository assetRepository;
  private final SecretReferenceRepository secretReferenceRepository;
  private final PayloadMapper payloadMapper;
  private final InjectRepository injectRepository;
  private final InjectorContractRepository injectorContractRepository;
  private final UserRepository userRepository;
  private final TeamRepository teamRepository;
  private final TagRepository tagRepository;
  private final DocumentRepository documentRepository;
  private final AssetGroupService assetGroupService;
  private final InjectExpectationService injectExpectationService;
  private final UserService userService;
  // Lazy: PermissionService is built from the inject services.
  private final ObjectProvider<PermissionService> permissionServiceProvider;
  private final InjectSearchService injectSearchService;
  private final InjectService injectService;
  private final InjectDocumentRepository injectDocumentRepository;
  private final InjectUtils injectUtils;
  private final InjectorContractContentUtils injectorContractContentUtils;
  private final BulkDeleteExecutor bulkDeleteExecutor;

  // -- CRUD --

  /**
   * Loads an atomic testing for work with no request scope (background relaunch): kept on the v1
   * ambient tenant, set by the caller before entering a per-tenant transaction.
   */
  private Inject findInject(String injectId) {
    String tenantId = TenantContext.getCurrentTenant();
    return (tenantId != null)
        ? injectRepository
            .findByIdAndTenantId(injectId, tenantId)
            .orElseThrow(ElementNotFoundException::new)
        : injectRepository.findById(injectId).orElseThrow(ElementNotFoundException::new);
  }

  /** Loads an atomic testing inside the request's tenant scope. Throws if not found there. */
  private Inject findInject(TxCtx ctx, String injectId) {
    return injectService.inject(ctx, injectId);
  }

  /** Validates that the atomic testing exists inside the request's tenant scope. */
  public void existsInScope(TxCtx ctx, String injectId) {
    injectService.existsInScope(ctx, injectId);
  }

  public InjectResultOverviewOutput findById(TxCtx ctx, String injectId) {
    Inject inject = injectService.inject(ctx, injectId);
    // Compute dynamic assets for display, in place on the SAME managed AssetGroup instances
    // (AssetGroup.dynamicAssets is @Transient: this mutation is never persisted).
    inject.getAssetGroups().forEach(assetGroupService::computeDynamicAssets);
    return injectMapper.toInjectResultOverviewOutput(inject);
  }

  /** Requires the inject inside the request's tenant scope, then maps its payload if it has one. */
  public StatusPayloadOutput findPayloadOutputByInjectId(TxCtx ctx, String injectId) {
    Inject inject = injectService.inject(ctx, injectId);
    return payloadMapper.getStatusPayloadOutputFromInject(Optional.of(inject));
  }

  /**
   * Creates an atomic testing in {@code tenantId}, or updates the one named by {@code injectId}.
   *
   * @param tenantId the tenant a new atomic testing is written in; not read on an update
   */
  @Transactional
  public InjectResultOverviewOutput createOrUpdate(
      TxCtx ctx, @Nullable String tenantId, AtomicTestingInput input, String injectId) {
    Inject injectToSave = new Inject();
    boolean isCreate = injectId == null;
    if (!isCreate) {
      injectToSave = findInject(ctx, injectId);
    } else {
      injectToSave.setTenant(new Tenant(tenantId));
    }

    // injectors_contracts is tenant-active and a built-in contract id repeats across tenants
    // (#8485): resolve it in the tenant the inject being written here belongs to, never the ambient
    // one (the default tenant on the non-prefixed route). On a create that is the write tenant the
    // API resolved from the request's scope, on an update the tenant of the loaded inject.
    String contractTenantId = isCreate ? tenantId : injectToSave.getTenant().getId();
    InjectorContract injectorContract =
        injectorContractRepository
            .findByContractIdAndTenant(input.getInjectorContract(), contractTenantId)
            .orElseThrow(ElementNotFoundException::new);
    ObjectNode finalContent = input.getContent();
    // Set expectations
    if (isCreate) {
      finalContent = injectorContractContentUtils.setExpectations(injectorContract, finalContent);
    }
    injectToSave.setTitle(input.getTitle());
    injectToSave.setContent(finalContent);
    injectToSave.setInjectorContract(injectorContract);
    injectToSave.setInjector(injectUtils.resolveInjector(input.getInjectorId(), injectorContract));
    injectToSave.setAllTeams(input.isAllTeams());
    injectToSave.setDescription(input.getDescription());
    injectToSave.setDependsDuration(0L);
    injectToSave.setUser(
        userRepository
            .findById(currentUser().getId())
            .orElseThrow(() -> new ElementNotFoundException("Current user not found")));
    injectToSave.setExercise(null);

    // Set dependencies
    injectToSave.setTeams(fromIterable(teamRepository.findAllById(input.getTeams())));
    injectToSave.setTags(iterableToSet(tagRepository.findAllById(input.getTagIds())));
    injectToSave.setAssets(fromIterable(this.assetRepository.findAllById(input.getAssets())));
    injectToSave.setAssetGroups(
        fromIterable(this.assetGroupRepository.findAllById(input.getAssetGroups())));
    Set<String> previousSecretReferenceIds =
        injectToSave.getSecretReferences().stream()
            .map(SecretReference::getId)
            .collect(Collectors.toSet());
    injectToSave.setSecretReferences(
        fromIterable(secretReferenceRepository.findAllById(input.getSecretReferences())));

    injectToSave.getDocuments().clear();

    Inject finalInjectToSave = injectToSave;
    input
        .getDocuments()
        .forEach(
            i -> {
              InjectDocumentId injectDocumentId = new InjectDocumentId();
              injectDocumentId.setInjectId(finalInjectToSave.getId());
              injectDocumentId.setDocumentId(i.getDocumentId());
              InjectDocument injectDocument =
                  injectDocumentRepository.findById(injectDocumentId).orElse(new InjectDocument());
              if (injectDocument.getInject() == null) {
                injectDocument.setCompositeId(injectDocumentId);
                injectDocument.setInject(finalInjectToSave);
                injectDocument.setDocument(
                    documentRepository.findById(i.getDocumentId()).orElseThrow());
              }
              injectDocument.setAttached(i.isAttached());
              finalInjectToSave
                  .getDocuments()
                  .add(
                      injectId == null
                          ? injectDocument
                          : injectDocumentRepository.save(injectDocument));
            });

    if (injectId == null) {
      actionMetricCollector.addAtomicTestingCreatedCount();
    }
    injectToSave = injectRepository.save(injectToSave);
    credentialMetricCollector.addNewlyAttachedCredentialsToInjectMetric(
        finalInjectToSave, previousSecretReferenceIds);
    return injectMapper.toInjectResultOverviewOutput(injectToSave);
  }

  @Transactional
  public InjectResultOverviewOutput updateAtomicTestingTags(
      TxCtx ctx, String injectId, AtomicTestingUpdateTagsInput input) {

    Inject inject = findInject(ctx, injectId);
    inject.setTags(iterableToSet(this.tagRepository.findAllById(input.getTagIds())));

    Inject saved = injectRepository.save(inject);
    return injectMapper.toInjectResultOverviewOutput(saved);
  }

  public void deleteAtomicTesting(TxCtx ctx, String injectId) {
    // Verify the inject exists inside the request's tenant scope before deleting
    findInject(ctx, injectId);
    injectService.delete(injectId);
  }

  /**
   * Bulk delete of atomic testings, either from an explicit list of ids or from a search input
   * (select-all with optional exclusions). The scope is restricted to atomic testings (injects
   * without scenario or simulation) the user is allowed to plan on.
   *
   * @param input the bulk processing input (ids or search input, plus ids to ignore)
   *     <p>Not transactional as a whole: the deletion scope is resolved in a short transaction,
   *     then atomic testings are deleted in small independent chunks (with deadlock retry) tracked
   *     as a massive operation, so per-entity stream events are suppressed in favor of aggregated
   *     progress events.
   * @return the list of deleted inject ids
   */
  public List<String> bulkDelete(final TxCtx ctx, @NotNull final InjectBulkProcessingInput input) {
    // The generic inject specification is unrestricted when no simulation/scenario id is given:
    // constrain it to atomic testings only so a select-all can never touch simulation or scenario
    // injects.
    input.setSimulationOrScenarioId(null);
    List<String> injectIdsToDelete =
        bulkDeleteExecutor.resolveInTransaction(
            ctx,
            () -> {
              Specification<Inject> specification =
                  injectService
                      .getInjectSpecification(ctx, input, Grant.GRANT_TYPE.PLANNER)
                      .and(InjectSpecification.isAtomicTesting());
              return injectRepository.findAll(specification).stream().map(Inject::getId).toList();
            });
    return bulkDeleteExecutor.deleteInChunks(
        ctx, "atomic testings", injectIdsToDelete, ids -> injectService.deleteAllByIds(ctx, ids));
  }

  // -- ACTIONS --

  public InjectResultOverviewOutput duplicate(TxCtx ctx, String id) {
    findInject(ctx, id);
    this.actionMetricCollector.addAtomicTestingCreatedCount();
    return injectService.duplicate(id);
  }

  public InjectResultOverviewOutput launch(TxCtx ctx, String id) {
    findInject(ctx, id);
    return injectService.launch(id);
  }

  /**
   * Relaunch an atomic testing (duplicate + queue new + delete old) and migrate its grants to the
   * new inject. Scheduled relaunches pass {@code checkLaunchable = false} to skip the Enterprise
   * executor gate.
   */
  @Transactional
  public InjectResultOverviewOutput relaunch(String id, boolean checkLaunchable) {
    findInject(id);
    // Relaunching an atomic testing is considered as creating a new one: InjectService moves the
    // grants of the current atomic testing to the new id before it deletes the old inject, whose
    // delete would otherwise take them (#8921).
    return injectService.relaunch(id, checkLaunchable);
  }

  /**
   * The Enterprise executor gate of a user relaunch, checked on what the user can see: the targets
   * they are not cleared for will not be executed, so their executors must not block the launch.
   */
  public void throwIfNotLaunchable(TxCtx ctx, String id) {
    injectService.throwIfInjectNotLaunchable(findInject(ctx, id));
  }

  /**
   * A user relaunch, once {@link #throwIfNotLaunchable} passed: duplicates the atomic testing for
   * the current user and migrates its grants to the new inject. Meant to run in a faithful copy, so
   * the duplicate keeps every target.
   *
   * @return the id of the relaunched atomic testing
   */
  @Transactional
  public String relaunchAsCurrentUser(TxCtx ctx, String id) {
    findInject(ctx, id);
    // The grants move with the relaunch, in InjectService, before the old inject is deleted.
    return injectService.relaunchAsCurrentUser(id);
  }

  /** Bulk update used by the recurring atomic testing job to self-clear outdated recurrences. */
  @Transactional
  public List<Inject> updateInjects(@NotNull final List<Inject> injects) {
    return fromIterable(this.injectRepository.saveAll(injects));
  }

  /** Atomic testing is recurring AND end date is after now (or has no end date). */
  public List<Inject> recurringAtomicTestings(@NotNull final Instant instant) {
    return injectRepository.findAll(
        InjectSpecification.isAtomicTesting()
            .and(InjectSpecification.isRecurring())
            .and(InjectSpecification.recurrenceStopDateAfter(instant)));
  }

  /**
   * Atomic testing is recurring, bounded by an end date, AND started or already ended. Only
   * end-bounded recurrences can ever become outdated (the job's outdated check returns false for a
   * null end date), so unbounded ones are filtered out in SQL instead of being scanned every
   * minute.
   */
  public List<Inject> potentialOutdatedRecurringAtomicTestings(@NotNull final Instant instant) {
    return injectRepository.findAll(
        InjectSpecification.isAtomicTesting()
            .and(InjectSpecification.isRecurring())
            .and(InjectSpecification.hasRecurrenceEnd())
            .and(
                InjectSpecification.recurrenceStartDateBefore(instant)
                    .or(InjectSpecification.recurrenceStopDateBefore(instant))));
  }

  @Transactional
  public InjectResultOverviewOutput updateRecurrence(
      TxCtx ctx, String injectId, InjectRecurrenceInput input) {
    Inject inject = findInject(ctx, injectId);
    // Normalize a blank expression to null so a cleared schedule can never be persisted as an
    // unparseable empty cron that the minutely job would keep selecting (isRecurring checks
    // isNotNull only).
    String recurrence =
        (input.getRecurrence() == null || input.getRecurrence().isBlank())
            ? null
            : input.getRecurrence().trim();
    // Scheduling itself is a Community Edition feature, but the Enterprise executor gate still
    // applies: without it, scheduling would bypass the licence check enforced on manual launches
    // (scheduled executions deliberately skip the gate at run time). A recurrence with a null
    // start date still fires (null start counts as already started), so the gate keys on the
    // normalized recurrence expression; clearing the schedule stays allowed.
    if (recurrence != null) {
      injectService.throwIfInjectNotLaunchable(inject);
      inject.setScheduledBy(
          userRepository
              .findById(currentUser().getId())
              .orElseThrow(ElementNotFoundException::new));
    }
    inject.setRecurrence(recurrence);
    inject.setRecurrenceStart(input.getRecurrenceStart());
    inject.setRecurrenceEnd(input.getRecurrenceEnd());
    Inject saved = injectRepository.save(inject);
    return injectMapper.toInjectResultOverviewOutput(saved);
  }

  // -- PAGINATION --

  /**
   * Search atomic testings with pagination and filtering. Atomic testings are injects that are not
   * part of any scenario or exercise (both fields are null). The search only fetches data according
   * to user permissions via the grant system.
   *
   * @param ctx the request scope
   * @param searchPaginationInput Pagination and filtering parameters
   * @return A paginated list of atomic testing results
   */
  public Page<InjectResultOutput> searchAtomicTestingsForCurrentUser(
      @NotNull final TxCtx ctx, @NotNull final SearchPaginationInput searchPaginationInput) {
    Map<String, Join<Base, Base>> joinMap = new HashMap<>();

    // Atomic testings are injects where scenario and exercise are null. They are also subject to
    // the grant system, which the access right bypasses in the tenants where the user holds it.
    User currentUser = userService.currentUser();

    Specification<Inject> customSpec =
        Specification.<Inject>unrestricted()
            .and(InjectSpecification.isAtomicTesting())
            .and(
                permissionServiceProvider
                    .getObject()
                    .filterInEachTenant(
                        ctx,
                        () ->
                            currentUser.isAdminOrBypass()
                                || currentUser
                                    .getCapabilities()
                                    .contains(Capability.ACCESS_ASSESSMENT),
                        accessRight ->
                            SpecificationUtils.hasGrantAccess(
                                currentUser.getId(),
                                false,
                                accessRight,
                                Grant.GRANT_TYPE.OBSERVER)));

    return buildPaginationCriteriaBuilder(
        (Specification<Inject> specification,
            Specification<Inject> specificationCount,
            Pageable pageable) ->
            injectSearchService.injectResults(
                customSpec.and(specification),
                customSpec.and(specificationCount),
                pageable,
                joinMap),
        searchPaginationInput,
        Inject.class,
        joinMap);
  }
}
