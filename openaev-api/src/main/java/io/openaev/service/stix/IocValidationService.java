package io.openaev.service.stix;

import static io.openaev.helper.UrlHelper.buildFrontSimulationUrl;
import static io.openaev.helper.UrlHelper.buildTenantUrl;
import static io.openaev.rest.payload.service.PayloadService.IOC_VALIDATION_POSIX_EXECUTOR;
import static io.openaev.rest.payload.service.PayloadService.IOC_VALIDATION_WINDOWS_EXECUTOR;
import static io.openaev.utils.pagination.PaginationUtils.buildPaginationJPA;
import static org.apache.commons.lang3.StringUtils.stripEnd;

import com.fasterxml.jackson.databind.node.ObjectNode;
import io.openaev.config.OpenAEVConfig;
import io.openaev.context.TenantContext;
import io.openaev.context.TenantScopedTransaction;
import io.openaev.context.TxCtx;
import io.openaev.database.model.*;
import io.openaev.database.repository.IocValidationRepository;
import io.openaev.database.repository.IocValidationRepository.IocValidationRef;
import io.openaev.opencti.client.mutations.IocValidationRequestStatusUpdate;
import io.openaev.opencti.connectors.ConnectorBase;
import io.openaev.opencti.connectors.service.OpenCTIConnectorService;
import io.openaev.opencti.errors.ConnectorError;
import io.openaev.rest.exception.BadRequestException;
import io.openaev.rest.exception.ElementNotFoundException;
import io.openaev.rest.exercise.service.ExerciseService;
import io.openaev.rest.inject.service.InjectService;
import io.openaev.rest.injector_contract.InjectorContractService;
import io.openaev.rest.payload.service.PayloadService;
import io.openaev.rest.tag.TagService;
import io.openaev.service.AssetGroupService;
import io.openaev.service.AssetService;
import io.openaev.service.InjectExpectationService;
import io.openaev.service.ScenarioToExerciseService;
import io.openaev.service.scenario.ScenarioService;
import io.openaev.service.stix.error.BundleValidationError;
import io.openaev.stix.objects.Bundle;
import io.openaev.utils.pagination.SearchPaginationInput;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Dissemination assurance: validates that indicators OpenCTI disseminated to security platforms are
 * actually detected or prevented, with benign tests built from each IOC.
 *
 * <p>Lifecycle: OpenCTI posts a request ({@link #receiveRequest}), which waits for an operator
 * decision; nothing ever runs without an explicit {@link #approve}. The approval builds one
 * scenario with one benign inject per IOC (and per executor family for command tests) on the
 * configured asset group, and launches it. The background job then evaluates every (indicator,
 * security platform) pair from the platform's expectation results ({@link #computeRunningResults}),
 * pushes the result bundle ({@link #pushPendingResults}) and reports each status change to OpenCTI
 * ({@link #syncPendingLifecycles}).
 *
 * <p>OpenCTI is never called inside a database transaction: the status OpenCTI acknowledged is an
 * outbox marker on the row, and every network call happens between two short transactions.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IocValidationService {

  /** A running validation is closed after this delay even if the simulation never ends. */
  static final Duration MAX_RUNNING_DURATION = Duration.ofDays(7);

  static final Set<IocValidationStatus> RESULT_STATUSES =
      EnumSet.of(
          IocValidationStatus.COMPLETED, IocValidationStatus.PARTIAL, IocValidationStatus.FAILED);

  private static final int MAX_SCENARIO_NAME_LENGTH = 255;

  private final IocValidationRepository iocValidationRepository;
  private final IocValidationBundleParser bundleParser;
  private final IocValidationSettingsService settingsService;
  private final AssetService assetService;
  private final AssetGroupService assetGroupService;
  private final PayloadService payloadService;
  private final InjectorContractService injectorContractService;
  private final InjectService injectService;
  private final InjectExpectationService injectExpectationService;
  private final ScenarioService scenarioService;
  private final ScenarioToExerciseService scenarioToExerciseService;
  private final ExerciseService exerciseService;
  private final TagService tagService;
  private final OpenCTIConnectorService openCTIConnectorService;
  private final OpenAEVConfig openAEVConfig;
  private final TenantScopedTransaction tenantTx;

  // -- SEARCH --

  /** Paginated search over the validations of the request scope. */
  @Transactional(readOnly = true)
  public Page<IocValidation> search(@NotNull final SearchPaginationInput input) {
    return buildPaginationJPA(
        (Specification<IocValidation> specification, Pageable pageable) ->
            iocValidationRepository.findAll(specification, pageable),
        input,
        IocValidation.class);
  }

  /**
   * One validation of the request scope.
   *
   * @throws ElementNotFoundException when it does not exist or belongs to another tenant
   */
  @Transactional(readOnly = true)
  public IocValidation iocValidation(@NotBlank final String id) {
    return iocValidationRepository
        .findById(id)
        .orElseThrow(() -> new ElementNotFoundException("IOC validation not found"));
  }

  private IocValidation lockedIocValidation(@NotBlank final String id) {
    return iocValidationRepository
        .findByIdForUpdate(id)
        .orElseThrow(() -> new ElementNotFoundException("IOC validation not found"));
  }

  // -- CREATE --

  /**
   * Records an IOC validation request posted by OpenCTI, in status {@link
   * IocValidationStatus#AWAITING_APPROVAL}. The tenant allow-list is applied as a preview: the
   * skipped IOCs and why are visible before anyone approves. Replays of the same request (OpenCTI
   * re-dispatching it) return the existing record instead of duplicating it.
   *
   * @param ctx single-tenant scope the request is attributed to
   * @param stixJson the bundle of the CTI event
   * @param entityId the OpenCTI request internal id of the CTI event
   * @throws BundleValidationError when the bundle does not follow the contract
   */
  @Transactional(rollbackFor = Exception.class)
  public IocValidation receiveRequest(TxCtx ctx, String stixJson, String entityId)
      throws BundleValidationError {
    String tenantId = singleTenant(ctx);
    IocValidationRequest request = bundleParser.parse(stixJson, entityId);
    Optional<IocValidation> existing =
        iocValidationRepository.findByExternalIdAndTenantId(request.requestId(), tenantId);
    if (existing.isPresent()) {
      log.info(
          "IOC validation request {} already received for tenant {}, keeping the existing record",
          request.requestId(),
          tenantId);
      return existing.get();
    }

    IocValidationSettings settings = settingsService.settings(tenantId);
    IocValidation validation = new IocValidation();
    validation.setTenant(new Tenant(tenantId));
    validation.setExternalId(request.requestId());
    validation.setName(request.name());
    validation.setDescription(request.description());
    validation.setRequestedBy(request.requestedBy());
    validation.setStatus(IocValidationStatus.AWAITING_APPROVAL);
    validation.setRequestedTestKinds(requestedTestKinds(request));
    validation.setAllowedTestKinds(sortedKinds(settings.allowedTestKinds()));
    validation.setIocs(
        new ArrayList<>(request.iocs().stream().map(IocValidationService::toIoc).toList()));
    validation.setPairs(
        new ArrayList<>(
            request.pairs().stream()
                .map(pair -> toPair(pair, request.platformNamesByRef()))
                .toList()));
    validation.setOpenctiUrl(openCtiUrl(tenantId, request.requestId()));
    IocValidationPlanner.apply(validation.getIocs(), settings);
    matchSecurityPlatforms(validation, tenantId);
    validation.setStatusMessage(intakeMessage(validation));
    validation.refreshCounters();
    IocValidation saved = iocValidationRepository.save(validation);
    log.info(
        "IOC validation request {} recorded for tenant {} ({} IOCs, {} pairs), awaiting approval",
        saved.getExternalId(),
        tenantId,
        saved.getIocsCount(),
        saved.getPairsCount());
    return saved;
  }

  // -- UPDATE --

  /**
   * Approves a waiting validation: re-applies the current safety settings (a setting narrowed since
   * the request arrived wins), builds the validation scenario and launches its simulation.
   *
   * @throws BadRequestException when the request is no longer awaiting approval, or when nothing
   *     can run (no allowed test, no asset group, no endpoint)
   */
  @Transactional(rollbackFor = Exception.class)
  public IocValidation approve(TxCtx ctx, @NotBlank final String id, @NotNull final User decider) {
    String tenantId = singleTenant(ctx);
    IocValidation validation = lockedIocValidation(id);
    requireAwaitingApproval(validation);

    IocValidationSettings settings = settingsService.settings(tenantId);
    IocValidationPlanner.apply(validation.getIocs(), settings);
    validation.setAllowedTestKinds(sortedKinds(settings.allowedTestKinds()));
    matchSecurityPlatforms(validation, tenantId);
    if (validation.getIocs().stream().noneMatch(ioc -> ioc.getTestKind() != null)) {
      throw new BadRequestException(
          "Nothing can run: every IOC of this request is skipped by the IOC validation settings."
              + " Reject the request, or adjust the settings and approve again.");
    }
    AssetGroup assetGroup = requireAssetGroup(settings);
    List<Endpoint> endpoints =
        assetGroupService
            .assetsFromAssetGroupMap(List.of(assetGroup))
            .getOrDefault(assetGroup, List.of());
    if (endpoints.isEmpty()) {
      throw new BadRequestException(
          "The IOC validation asset group '%s' contains no endpoint to run the tests on"
              .formatted(assetGroup.getName()));
    }

    // Scenarios, injects and simulations are still v1 tables: TenantBaseListener stamps them from
    // TenantContext, which only the /api/tenants/{tenantId}/ route sets. Bridging the resolved
    // tenant keeps every row of the validation in the request tenant, as for security coverage.
    String previousTenant =
        TenantContext.hasCurrentTenant() ? TenantContext.getCurrentTenant() : null;
    TenantContext.setCurrentTenant(tenantId);
    try {
      Scenario scenario = createScenario(ctx, validation);
      Set<Inject> injects =
          createInjects(ctx, validation, settings, scenario, assetGroup, executorsOf(endpoints));
      if (injects.isEmpty()) {
        throw new BadRequestException(
            "Nothing can run: no endpoint of the asset group '%s' runs a platform the planned tests support"
                .formatted(assetGroup.getName()));
      }
      scenario.setInjects(injects);
      scenarioService.throwIfScenarioNotLaunchable(scenario);
      Exercise simulation =
          scenarioToExerciseService.toExercise(
              scenario,
              Instant.now().truncatedTo(ChronoUnit.MINUTES).plus(1, ChronoUnit.MINUTES),
              true);

      Instant now = Instant.now();
      validation.setScenarioId(scenario.getId());
      validation.setSimulationId(simulation.getId());
      validation.setStatus(IocValidationStatus.RUNNING);
      validation.setStatusMessage(
          "Approved by %s: the validation simulation runs %d benign injects"
              .formatted(decider.getName(), injects.size()));
      validation.setDecidedById(decider.getId());
      validation.setDecidedByName(decider.getName());
      validation.setDecidedAt(now);
      validation.refreshCounters();
      IocValidation saved = iocValidationRepository.save(validation);
      log.info(
          "IOC validation {} approved by {}: scenario {} and simulation {} created with {} injects",
          saved.getId(),
          decider.getId(),
          scenario.getId(),
          simulation.getId(),
          injects.size());
      return saved;
    } finally {
      if (previousTenant == null) {
        TenantContext.clearCurrentTenant();
      } else {
        TenantContext.setCurrentTenant(previousTenant);
      }
    }
  }

  /**
   * Rejects a waiting validation: nothing runs and OpenCTI is told why.
   *
   * @param reason optional reason reported to OpenCTI
   * @throws BadRequestException when the request is no longer awaiting approval
   */
  @Transactional(rollbackFor = Exception.class)
  public IocValidation reject(
      TxCtx ctx, @NotBlank final String id, final String reason, @NotNull final User decider) {
    singleTenant(ctx);
    IocValidation validation = lockedIocValidation(id);
    requireAwaitingApproval(validation);
    Instant now = Instant.now();
    String trimmedReason = reason == null ? "" : reason.trim();
    validation.setStatus(IocValidationStatus.REJECTED);
    validation.setStatusMessage(
        trimmedReason.isEmpty()
            ? "Rejected in OpenAEV by %s".formatted(decider.getName())
            : "Rejected in OpenAEV by %s: %s".formatted(decider.getName(), trimmedReason));
    validation.setDecidedById(decider.getId());
    validation.setDecidedByName(decider.getName());
    validation.setDecidedAt(now);
    validation.setCompletedAt(now);
    log.info("IOC validation {} rejected by {}", validation.getId(), decider.getId());
    return iocValidationRepository.save(validation);
  }

  // -- RESULTS (background job) --

  /** Evaluates every running validation of every tenant, one short transaction each. */
  public void computeRunningResults() {
    for (IocValidationRef ref :
        allTenants(
            () ->
                iocValidationRepository.findRefsByStatusIn(List.of(IocValidationStatus.RUNNING)))) {
      try {
        inTenant(ref.getTenantId(), () -> computeResults(ref.getId(), Instant.now()));
      } catch (Exception e) {
        log.error(
            "Could not compute the results of IOC validation {} for tenant {}",
            ref.getId(),
            ref.getTenantId(),
            e);
      }
    }
  }

  /**
   * Evaluates the pending pairs of one running validation and closes it once every pair has an
   * outcome. Runs inside the tenant transaction opened by the caller.
   *
   * @return whether the validation reached a final status
   */
  boolean computeResults(String id, Instant now) {
    IocValidation validation = iocValidationRepository.findById(id).orElse(null);
    if (validation == null || validation.getStatus() != IocValidationStatus.RUNNING) {
      return false;
    }
    Exercise simulation = findSimulation(validation.getSimulationId());
    boolean simulationGone = simulation == null;
    boolean finalizing =
        simulationGone
            || simulation.getStatus() == ExerciseStatus.FINISHED
            || simulation.getStatus() == ExerciseStatus.CANCELED
            || (validation.getDecidedAt() != null
                && validation.getDecidedAt().plus(MAX_RUNNING_DURATION).isBefore(now));

    Map<String, List<IocValidationIoc>> iocsByIndicator =
        validation.getIocs().stream()
            .collect(Collectors.groupingBy(IocValidationIoc::getIndicatorRef));
    Map<String, List<BaseInjectExpectation>> expectationsByInject =
        simulationGone ? Map.of() : expectationsByInject(validation);

    boolean changed = false;
    for (IocValidationPair pair : validation.getPairs()) {
      if (pair.getOutcome() != null) {
        continue;
      }
      Optional<IocValidationOutcomes.Evaluation> evaluation =
          evaluatePair(
              pair,
              iocsByIndicator.getOrDefault(pair.getIndicatorRef(), List.of()),
              expectationsByInject,
              finalizing,
              simulationGone);
      if (evaluation.isPresent()) {
        pair.setOutcome(evaluation.get().outcome());
        pair.setOutcomeReason(evaluation.get().reason());
        pair.setEvaluatedAt(now);
        changed = true;
      }
    }

    boolean done = validation.getPairs().stream().allMatch(pair -> pair.getOutcome() != null);
    if (done) {
      validation.setStatus(IocValidationOutcomes.finalStatus(validation.getPairs()));
      validation.setCompletedAt(now);
      changed = true;
    }
    if (changed) {
      validation.refreshCounters();
      if (done) {
        validation.setStatusMessage(resultsMessage(validation));
      }
      // The pairs are a JSON column: a new list makes the change visible to dirty checking.
      validation.setPairs(new ArrayList<>(validation.getPairs()));
      iocValidationRepository.save(validation);
    }
    return done;
  }

  /** Pushes the result bundle of every finished validation OpenCTI has not received yet. */
  public void pushPendingResults() {
    for (IocValidationRef ref :
        allTenants(() -> iocValidationRepository.findRefsWithPendingResultsPush(RESULT_STATUSES))) {
      String tenantId = ref.getTenantId();
      try {
        Optional<Bundle> bundle =
            inTenant(
                tenantId,
                () ->
                    iocValidationRepository
                        .findById(ref.getId())
                        .map(
                            validation ->
                                IocValidationResultBundle.build(validation, Instant.now())));
        if (bundle.isEmpty()) {
          continue;
        }
        if (!isIocValidationConnectorRegistered(tenantId)) {
          log.debug(
              "IOC validation connector of tenant {} not registered yet, results kept", tenantId);
          continue;
        }
        openCTIConnectorService.pushIocValidationStixBundle(bundle.get(), tenantId);
        inTenant(tenantId, () -> markResultsPushed(ref.getId(), Instant.now()));
      } catch (ConnectorError | IOException e) {
        log.warn(
            "Could not push the results of IOC validation {} to OpenCTI for tenant {} (retried on"
                + " the next run): {}",
            ref.getId(),
            tenantId,
            e.getMessage());
      } catch (Exception e) {
        log.error("Could not push the results of IOC validation {}", ref.getId(), e);
      }
    }
  }

  /** Reports to OpenCTI every status it has not acknowledged yet. */
  public void syncPendingLifecycles() {
    for (IocValidationRef ref :
        allTenants(iocValidationRepository::findRefsWithPendingLifecycleSync)) {
      syncLifecycle(ref.getTenantId(), ref.getId());
    }
  }

  /**
   * Reports the current status of one validation to OpenCTI, outside any transaction. A final
   * result status is only reported once its result bundle was pushed, so OpenCTI never closes a
   * request before it holds the outcomes. Failures are logged and retried by the job.
   *
   * @return whether OpenCTI acknowledged a status
   */
  public boolean syncLifecycle(String tenantId, String id) {
    try {
      Optional<IocValidationRequestStatusUpdate> update =
          inTenant(
              tenantId, () -> iocValidationRepository.findById(id).flatMap(this::statusUpdate));
      if (update.isEmpty()) {
        return false;
      }
      if (!isIocValidationConnectorRegistered(tenantId)) {
        log.debug(
            "IOC validation connector of tenant {} not registered yet, status kept", tenantId);
        return false;
      }
      openCTIConnectorService.updateIocValidationRequestStatus(update.get(), tenantId);
      IocValidationStatus reported =
          IocValidationStatus.valueOf(update.get().getStatus().toUpperCase(Locale.ROOT));
      inTenant(tenantId, () -> markSynced(id, reported));
      return true;
    } catch (ConnectorError | IOException e) {
      log.warn(
          "Could not report the status of IOC validation {} to OpenCTI for tenant {} (retried on"
              + " the next run): {}",
          id,
          tenantId,
          e.getMessage());
    } catch (Exception e) {
      log.error("Could not report the status of IOC validation {}", id, e);
    }
    return false;
  }

  /** The status update OpenCTI has not acknowledged yet, if any. */
  Optional<IocValidationRequestStatusUpdate> statusUpdate(IocValidation validation) {
    IocValidationStatus status = validation.getStatus();
    if (status == validation.getLifecycleSyncedStatus()) {
      return Optional.empty();
    }
    if (RESULT_STATUSES.contains(status) && validation.getResultsPushedAt() == null) {
      return Optional.empty();
    }
    IocValidationRequestStatusUpdate.IocValidationRequestStatusUpdateBuilder update =
        IocValidationRequestStatusUpdate.builder()
            .requestId(validation.getExternalId())
            .status(status.toOpenCti())
            .message(validation.getStatusMessage());
    String tenantId = validation.getTenant().getId();
    if (validation.getScenarioId() != null) {
      update.scenarioId(validation.getScenarioId());
    }
    if (validation.getSimulationId() != null) {
      update.simulationId(validation.getSimulationId());
      update.externalUri(
          buildFrontSimulationUrl(
              openAEVConfig.getBaseUrl(), tenantId, validation.getSimulationId()));
    } else {
      update.externalUri(frontValidationUrl(tenantId, validation.getId()));
    }
    return Optional.of(update.build());
  }

  private void markResultsPushed(String id, Instant now) {
    iocValidationRepository
        .findById(id)
        .ifPresent(
            validation -> {
              validation.setResultsPushedAt(now);
              iocValidationRepository.save(validation);
            });
  }

  private void markSynced(String id, IocValidationStatus reported) {
    iocValidationRepository
        .findById(id)
        .ifPresent(
            validation -> {
              validation.setLifecycleSyncedStatus(reported);
              iocValidationRepository.save(validation);
            });
  }

  // -- OPTIONS --

  /** Whether the IOC validation connector of the tenant is configured and registered in OpenCTI. */
  public boolean isIocValidationConnectorRegistered(String tenantId) {
    return openCTIConnectorService
        .getIocValidationConnector(tenantId)
        .map(ConnectorBase::isRegistered)
        .orElse(false);
  }

  /** Whether an OpenCTI connection is configured for the tenant. */
  public boolean isOpenCtiConfigured(String tenantId) {
    return openCTIConnectorService.getIocValidationConnector(tenantId).isPresent();
  }

  // -- INTERNAL --

  private Optional<IocValidationOutcomes.Evaluation> evaluatePair(
      IocValidationPair pair,
      List<IocValidationIoc> iocs,
      Map<String, List<BaseInjectExpectation>> expectationsByInject,
      boolean finalizing,
      boolean simulationGone) {
    if (iocs.isEmpty()) {
      return Optional.of(error("OpenCTI sent no IOC for this indicator"));
    }
    List<IocValidationIoc> runnable =
        iocs.stream().filter(ioc -> ioc.getTestKind() != null).toList();
    if (runnable.isEmpty()) {
      return Optional.of(
          error(
              iocs.stream()
                  .map(IocValidationIoc::getMessage)
                  .filter(Objects::nonNull)
                  .distinct()
                  .collect(Collectors.joining("; "))));
    }
    if (pair.getSecurityPlatformId() == null) {
      return Optional.of(
          error(
              "No OpenAEV security platform is named '%s': create it, or rename it, so its"
                      .formatted(Objects.toString(pair.getPlatformName(), pair.getPlatformRef()))
                  + " detection and prevention results can be collected"));
    }
    if (simulationGone) {
      return Optional.of(
          error("The validation simulation was deleted before its results were evaluated"));
    }
    List<BaseInjectExpectation> expectations =
        runnable.stream()
            .flatMap(ioc -> ioc.getInjectIds().stream())
            .flatMap(injectId -> expectationsByInject.getOrDefault(injectId, List.of()).stream())
            .toList();
    return IocValidationOutcomes.evaluate(expectations, pair.getSecurityPlatformId(), finalizing);
  }

  private static IocValidationOutcomes.Evaluation error(String reason) {
    return new IocValidationOutcomes.Evaluation(IocValidationOutcome.ERROR, reason);
  }

  private Map<String, List<BaseInjectExpectation>> expectationsByInject(IocValidation validation) {
    Set<String> injectIds =
        validation.getIocs().stream()
            .flatMap(ioc -> ioc.getInjectIds().stream())
            .collect(Collectors.toSet());
    return injectExpectationService.findPrimaryExpectationsByInjectIds(injectIds).stream()
        .filter(expectation -> expectation.getInject() != null)
        .collect(Collectors.groupingBy(expectation -> expectation.getInject().getId()));
  }

  private Exercise findSimulation(String simulationId) {
    if (simulationId == null) {
      return null;
    }
    try {
      return exerciseService.findById(simulationId);
    } catch (ElementNotFoundException e) {
      return null;
    }
  }

  private Scenario createScenario(TxCtx ctx, IocValidation validation) {
    Scenario scenario = new Scenario();
    scenario.setName(
        truncate("IOC validation - " + validation.getName(), MAX_SCENARIO_NAME_LENGTH));
    scenario.setDescription(scenarioDescription(validation));
    scenario.setCategory(IocValidation.SCENARIO_CATEGORY);
    scenario.setMainFocus(Scenario.MAIN_FOCUS_INCIDENT_RESPONSE);
    scenario.setSeverity(Scenario.SEVERITY.low);
    scenario.setExternalReference(validation.getExternalId());
    scenario.setExternalUrl(validation.getOpenctiUrl());
    scenario.setTags(validationTags(ctx));
    return scenarioService.createScenario(scenario);
  }

  private Set<Tag> validationTags(TxCtx ctx) {
    return tagService.findOrCreateTagsFromNames(
        ctx, new HashSet<>(Set.of(IocValidation.SCENARIO_TAG, Tag.OPENCTI_TAG_NAME)));
  }

  private Set<Inject> createInjects(
      TxCtx ctx,
      IocValidation validation,
      IocValidationSettings settings,
      Scenario scenario,
      AssetGroup assetGroup,
      List<String> executors) {
    Set<Tag> tags = validationTags(ctx);
    Set<Inject> injects = new HashSet<>();
    for (IocValidationIoc ioc : validation.getIocs()) {
      ioc.setInjectIds(new ArrayList<>());
      IocValidationPlanner.Plan plan = IocValidationPlanner.plan(ioc, settings);
      if (!plan.runnable()) {
        continue;
      }
      List<Payload> payloads = payloadsFor(ctx, plan.testKind(), executors);
      if (payloads.isEmpty()) {
        ioc.setTestKind(null);
        ioc.setMessage(
            "Not run: no endpoint of the asset group runs Windows, Linux or macOS for the %s test"
                .formatted(plan.testKind().toStix()));
        continue;
      }
      for (Payload payload : payloads) {
        InjectorContract contract =
            injectorContractService
                .injectorContractByPayload(payload)
                .orElseThrow(
                    () ->
                        new IllegalStateException(
                            "The IOC validation payload %s has no injector contract"
                                .formatted(payload.getId())));
        Inject inject =
            injectService.buildInject(
                contract, injectTitle(ioc, payload), injectDescription(ioc), true);
        ObjectNode content = inject.getContent();
        plan.arguments().forEach(content::put);
        inject.setContent(content);
        inject.setScenario(scenario);
        inject.setAssetGroups(new ArrayList<>(List.of(assetGroup)));
        inject.setTags(new HashSet<>(tags));
        Inject saved = injectService.createInject(inject);
        ioc.getInjectIds().add(saved.getId());
        injects.add(saved);
      }
    }
    // The IOCs are a JSON column: a new list makes the inject ids visible to dirty checking.
    validation.setIocs(new ArrayList<>(validation.getIocs()));
    return injects;
  }

  private List<Payload> payloadsFor(TxCtx ctx, IocValidationTestKind kind, List<String> executors) {
    if (kind == IocValidationTestKind.DNS_RESOLUTION) {
      return List.of(payloadService.getDynamicDnsResolutionPayload(ctx));
    }
    return executors.stream()
        .<Payload>map(
            executor -> payloadService.getIocValidationCommandPayload(ctx, kind, executor))
        .toList();
  }

  /**
   * Executor families present among the endpoints: {@code psh} for Windows, {@code sh} otherwise.
   */
  static List<String> executorsOf(Collection<Endpoint> endpoints) {
    List<String> executors = new ArrayList<>();
    boolean windows =
        endpoints.stream()
            .anyMatch(endpoint -> endpoint.getPlatform() == Endpoint.PLATFORM_TYPE.Windows);
    boolean posix =
        endpoints.stream()
            .anyMatch(
                endpoint ->
                    endpoint.getPlatform() == Endpoint.PLATFORM_TYPE.Linux
                        || endpoint.getPlatform() == Endpoint.PLATFORM_TYPE.MacOS);
    if (windows) {
      executors.add(IOC_VALIDATION_WINDOWS_EXECUTOR);
    }
    if (posix) {
      executors.add(IOC_VALIDATION_POSIX_EXECUTOR);
    }
    return executors;
  }

  private AssetGroup requireAssetGroup(IocValidationSettings settings) {
    if (!settings.hasAssetGroup()) {
      throw new BadRequestException(
          "Choose the asset group that runs the validation tests in the IOC validation settings"
              + " before approving");
    }
    try {
      return assetGroupService.assetGroup(settings.assetGroupId());
    } catch (ElementNotFoundException e) {
      throw new BadRequestException(
          "The IOC validation asset group no longer exists: choose another one in the settings");
    }
  }

  private static void requireAwaitingApproval(IocValidation validation) {
    if (validation.getStatus() != IocValidationStatus.AWAITING_APPROVAL) {
      throw new BadRequestException(
          "This IOC validation is %s and no longer awaits a decision"
              .formatted(validation.getStatus().toOpenCti().replace('_', ' ')));
    }
  }

  /** Matches each pair's OpenCTI security platform to an OpenAEV security platform by name. */
  private void matchSecurityPlatforms(IocValidation validation, String tenantId) {
    Map<String, Optional<String>> matched = new HashMap<>();
    for (IocValidationPair pair : validation.getPairs()) {
      String name = pair.getPlatformName();
      if (name == null || name.isBlank()) {
        pair.setSecurityPlatformId(null);
        continue;
      }
      pair.setSecurityPlatformId(
          matched
              .computeIfAbsent(
                  name.trim().toLowerCase(Locale.ROOT),
                  key -> assetService.securityPlatformByName(name, tenantId).map(Asset::getId))
              .orElse(null));
    }
    validation.setPairs(new ArrayList<>(validation.getPairs()));
  }

  private static IocValidationIoc toIoc(IocValidationRequest.Ioc requested) {
    IocValidationIoc ioc = new IocValidationIoc();
    ioc.setIndicatorRef(requested.indicatorRef());
    ioc.setIndicatorName(requested.indicatorName());
    ioc.setObservableType(requested.observableType());
    ioc.setValue(requested.value());
    ioc.setRequestedTestKind(requested.testKind());
    ioc.setFileName(requested.fileName());
    ioc.setHashes(new LinkedHashMap<>(requested.hashes()));
    return ioc;
  }

  private static IocValidationPair toPair(
      IocValidationRequest.Pair requested, Map<String, String> platformNames) {
    IocValidationPair pair = new IocValidationPair();
    pair.setIndicatorRef(requested.indicatorRef());
    pair.setPlatformRef(requested.platformRef());
    pair.setDeployedOnRef(requested.deployedOnRef());
    pair.setPlatformName(platformNames.get(requested.platformRef()));
    return pair;
  }

  private static List<IocValidationTestKind> requestedTestKinds(IocValidationRequest request) {
    Set<IocValidationTestKind> kinds = EnumSet.noneOf(IocValidationTestKind.class);
    kinds.addAll(request.testKinds());
    request.iocs().forEach(ioc -> kinds.add(ioc.testKind()));
    return sortedKinds(kinds);
  }

  private static List<IocValidationTestKind> sortedKinds(Collection<IocValidationTestKind> kinds) {
    return new ArrayList<>(kinds.stream().sorted().toList());
  }

  private String openCtiUrl(String tenantId, String requestId) {
    return openCTIConnectorService
        .getIocValidationConnector(tenantId)
        .map(ConnectorBase::getUrl)
        .filter(url -> url != null && !url.isBlank())
        .map(url -> stripEnd(url, "/") + "/dashboard/id/" + requestId)
        .orElse(null);
  }

  private String frontValidationUrl(String tenantId, String validationId) {
    return buildTenantUrl(openAEVConfig.getBaseUrl(), tenantId)
        + "/admin/atomic_testings/ioc_validations/"
        + validationId;
  }

  private static String intakeMessage(IocValidation validation) {
    long runnable = validation.getIocs().stream().filter(ioc -> ioc.getTestKind() != null).count();
    long skipped = validation.getIocs().size() - runnable;
    return "Waiting for approval in OpenAEV: %d IOC(s) to test, %d skipped by the safety settings"
        .formatted(runnable, skipped);
  }

  private static String resultsMessage(IocValidation validation) {
    return "Prevented %d, detected %d, missed %d, error %d (of %d indicator-platform pairs)"
        .formatted(
            validation.getPreventedCount(),
            validation.getDetectedCount(),
            validation.getMissedCount(),
            validation.getErrorCount(),
            validation.getPairsCount());
  }

  private static String scenarioDescription(IocValidation validation) {
    StringBuilder description = new StringBuilder();
    description
        .append("Benign IOC validation requested from OpenCTI")
        .append(validation.getRequestedBy() == null ? "" : " by " + validation.getRequestedBy())
        .append(
            ". Each inject runs a harmless test built from one indicator (DNS resolution, a TCP")
        .append(
            " connect closed at once, an HTTP HEAD through the egress proxy, a benign surrogate")
        .append(" file or log line); nothing is downloaded or executed from the indicators.");
    if (validation.getDescription() != null) {
      description.append("\n\n").append(validation.getDescription());
    }
    return description.toString();
  }

  private static String injectTitle(IocValidationIoc ioc, Payload payload) {
    String kind =
        switch (ioc.getTestKind()) {
          case DNS_RESOLUTION -> "Resolve";
          case NETWORK_TRAFFIC -> "Connect to";
          case HTTP_HEAD -> "HTTP HEAD";
          case FILE_DROP -> "Drop surrogate";
          case LOG_INJECTION -> "Log";
        };
    String executor = payload instanceof Command command ? " (" + command.getExecutor() + ")" : "";
    return truncate(
        "[IOC] %s %s%s".formatted(kind, ioc.getValue(), executor), MAX_SCENARIO_NAME_LENGTH);
  }

  private static String injectDescription(IocValidationIoc ioc) {
    String indicator =
        ioc.getIndicatorName() == null ? ioc.getIndicatorRef() : ioc.getIndicatorName();
    String description =
        "Benign %s test of the OpenCTI indicator %s (%s)"
            .formatted(ioc.getTestKind().toStix(), indicator, ioc.getObservableType());
    return ioc.getMessage() == null ? description : description + ". " + ioc.getMessage();
  }

  private static String truncate(String value, int maxLength) {
    return value.length() <= maxLength ? value : value.substring(0, maxLength);
  }

  private static String singleTenant(TxCtx ctx) {
    return switch (ctx) {
      case TxCtx.Restricted restricted when restricted.tenantIds().size() == 1 ->
          restricted.tenantIds().get(0);
      default ->
          throw new IllegalStateException(
              "An IOC validation is attributed to exactly one tenant: the caller must resolve a"
                  + " single-tenant scope");
    };
  }

  private <T> T allTenants(Supplier<T> work) {
    return tenantTx.execute(TxCtx.allTenants(), work);
  }

  private <T> T inTenant(String tenantId, Supplier<T> work) {
    String previousTenant =
        TenantContext.hasCurrentTenant() ? TenantContext.getCurrentTenant() : null;
    TenantContext.setCurrentTenant(tenantId);
    try {
      return tenantTx.execute(TxCtx.forTenant(tenantId), work);
    } finally {
      if (previousTenant == null) {
        TenantContext.clearCurrentTenant();
      } else {
        TenantContext.setCurrentTenant(previousTenant);
      }
    }
  }

  private void inTenant(String tenantId, Runnable work) {
    inTenant(
        tenantId,
        () -> {
          work.run();
          return null;
        });
  }
}
