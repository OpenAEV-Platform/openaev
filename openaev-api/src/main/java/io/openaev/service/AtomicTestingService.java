package io.openaev.service;

import static io.openaev.config.SessionHelper.currentUser;
import static io.openaev.helper.StreamHelper.fromIterable;
import static io.openaev.helper.StreamHelper.iterableToSet;
import static io.openaev.utils.pagination.PaginationUtils.buildPaginationCriteriaBuilder;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.openaev.context.TenantContext;
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
import io.openaev.utils.InjectUtils;
import io.openaev.utils.injector_contract.InjectorContractContentUtils;
import io.openaev.utils.mapper.InjectMapper;
import io.openaev.utils.mapper.PayloadMapper;
import io.openaev.utils.pagination.SearchPaginationInput;
import jakarta.annotation.Resource;
import jakarta.persistence.criteria.Join;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

  private final AssetGroupRepository assetGroupRepository;
  private final AssetRepository assetRepository;
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
  private final InjectSearchService injectSearchService;
  private final InjectService injectService;
  private final GrantService grantService;
  private final InjectDocumentRepository injectDocumentRepository;
  private final InjectUtils injectUtils;
  private final InjectorContractContentUtils injectorContractContentUtils;
  private final BulkDeleteExecutor bulkDeleteExecutor;

  // -- CRUD --

  private Inject findInject(String injectId) {
    String tenantId = TenantContext.getCurrentTenant();
    return (tenantId != null)
        ? injectRepository
            .findByIdAndTenantId(injectId, tenantId)
            .orElseThrow(ElementNotFoundException::new)
        : injectRepository.findById(injectId).orElseThrow(ElementNotFoundException::new);
  }

  public InjectResultOverviewOutput findById(String injectId) {
    String tenantId = TenantContext.getCurrentTenant();
    Optional<Inject> injectOpt =
        (tenantId != null)
            ? injectRepository.findByIdAndTenantId(injectId, tenantId)
            : injectRepository.findWithStatusById(injectId);

    if (injectOpt.isPresent()) {
      Inject inject = injectOpt.get();
      List<AssetGroup> computedAssetGroup =
          inject.getAssetGroups().stream().map(assetGroupService::computeDynamicAssets).toList();
      inject.getAssetGroups().clear();
      inject.getAssetGroups().addAll(computedAssetGroup);
    }
    return injectOpt
        .map(injectMapper::toInjectResultOverviewOutput)
        .orElseThrow(ElementNotFoundException::new);
  }

  public StatusPayloadOutput findPayloadOutputByInjectId(String injectId) {
    String tenantId = TenantContext.getCurrentTenant();
    Optional<Inject> inject =
        (tenantId != null)
            ? injectRepository.findByIdAndTenantId(injectId, tenantId)
            : injectRepository.findById(injectId);
    return payloadMapper.getStatusPayloadOutputFromInject(inject);
  }

  @Transactional
  public InjectResultOverviewOutput createOrUpdate(AtomicTestingInput input, String injectId) {
    Inject injectToSave = new Inject();
    if (injectId != null) {
      injectToSave = findInject(injectId);
    }

    InjectorContract injectorContract =
        injectorContractRepository
            .findById(input.getInjectorContract())
            .orElseThrow(ElementNotFoundException::new);
    ObjectNode finalContent = input.getContent();
    // Set expectations
    if (injectId == null) {
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
    return injectMapper.toInjectResultOverviewOutput(injectToSave);
  }

  @Transactional
  public InjectResultOverviewOutput updateAtomicTestingTags(
      String injectId, AtomicTestingUpdateTagsInput input) {

    Inject inject = findInject(injectId);
    inject.setTags(iterableToSet(this.tagRepository.findAllById(input.getTagIds())));

    Inject saved = injectRepository.save(inject);
    return injectMapper.toInjectResultOverviewOutput(saved);
  }

  public void deleteAtomicTesting(String injectId) {
    // Verify the inject exists and belongs to the current tenant before deleting
    findInject(injectId);
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
  public List<String> bulkDelete(@NotNull final InjectBulkProcessingInput input) {
    // The generic inject specification is unrestricted when no simulation/scenario id is given:
    // constrain it to atomic testings only so a select-all can never touch simulation or scenario
    // injects.
    input.setSimulationOrScenarioId(null);
    List<String> injectIdsToDelete =
        bulkDeleteExecutor.resolveInTransaction(
            () -> {
              Specification<Inject> specification =
                  injectService
                      .getInjectSpecification(input, Grant.GRANT_TYPE.PLANNER)
                      .and(InjectSpecification.isAtomicTesting());
              return injectRepository.findAll(specification).stream().map(Inject::getId).toList();
            });
    return bulkDeleteExecutor.deleteInChunks(
        "atomic testings", injectIdsToDelete, injectService::deleteAllByIds);
  }

  // -- ACTIONS --

  public InjectResultOverviewOutput duplicate(String id) {
    findInject(id);
    this.actionMetricCollector.addAtomicTestingCreatedCount();
    return injectService.duplicate(id);
  }

  public InjectResultOverviewOutput launch(String id) {
    findInject(id);
    return injectService.launch(id);
  }

  @Transactional
  public InjectResultOverviewOutput relaunch(String id) {
    return relaunch(id, true);
  }

  /**
   * Relaunch an atomic testing (duplicate + queue new + delete old) and migrate its grants to the
   * new inject. Scheduled relaunches pass {@code checkLaunchable = false} to skip the Enterprise
   * executor gate.
   */
  @Transactional
  public InjectResultOverviewOutput relaunch(String id, boolean checkLaunchable) {
    findInject(id);
    // Relaunching an atomic testing is considered as creating a new one.
    // Therefore, any grants created on the current atomic testing will have to be updated with the
    // new ID
    InjectResultOverviewOutput relaunched = injectService.relaunch(id, checkLaunchable);
    grantService.updateGrantsForNewResource(
        id, relaunched.getId(), Grant.GRANT_RESOURCE_TYPE.ATOMIC_TESTING);
    return relaunched;
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

  /** Atomic testing is recurring AND start date is before now OR stop date is before now. */
  public List<Inject> potentialOutdatedRecurringAtomicTestings(@NotNull final Instant instant) {
    return injectRepository.findAll(
        InjectSpecification.isAtomicTesting()
            .and(InjectSpecification.isRecurring())
            .and(
                InjectSpecification.recurrenceStartDateBefore(instant)
                    .or(InjectSpecification.recurrenceStopDateBefore(instant))));
  }

  @Transactional
  public InjectResultOverviewOutput updateRecurrence(String injectId, InjectRecurrenceInput input) {
    Inject inject = findInject(injectId);
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
   * @param searchPaginationInput Pagination and filtering parameters
   * @return A paginated list of atomic testing results
   */
  public Page<InjectResultOutput> searchAtomicTestingsForCurrentUser(
      @NotNull final SearchPaginationInput searchPaginationInput) {
    Map<String, Join<Base, Base>> joinMap = new HashMap<>();

    // Atomic testings are injects where scenario and exercise are null. They are also subject to
    // the grant system.
    User currentUser = userService.currentUser();

    Specification<Inject> customSpec =
        Specification.<Inject>unrestricted()
            .and(InjectSpecification.isAtomicTesting())
            .and(
                SpecificationUtils.hasGrantAccess(
                    currentUser.getId(),
                    currentUser.isAdminOrBypass(),
                    currentUser.getCapabilities().contains(Capability.ACCESS_ASSESSMENT),
                    Grant.GRANT_TYPE.OBSERVER));

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
