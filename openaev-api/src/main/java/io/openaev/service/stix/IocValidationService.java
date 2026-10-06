package io.openaev.service.stix;

import static io.openaev.helper.UrlHelper.buildFrontSimulationUrl;
import static io.openaev.helper.UrlHelper.buildTenantUrl;
import static io.openaev.rest.payload.service.PayloadService.IOC_VALIDATION_POSIX_EXECUTOR;
import static io.openaev.rest.payload.service.PayloadService.IOC_VALIDATION_RUN_KEY;
import static io.openaev.rest.payload.service.PayloadService.IOC_VALIDATION_WINDOWS_EXECUTOR;
import static io.openaev.utils.pagination.PaginationUtils.buildPaginationJPA;
import static org.apache.commons.lang3.StringUtils.stripEnd;

import com.fasterxml.jackson.databind.node.ObjectNode;
import io.openaev.aop.audit_log.AuditEvent;
import io.openaev.aop.audit_log.AuditEventOrigin;
import io.openaev.aop.audit_log.AuditEventScope;
import io.openaev.aop.audit_log.AuditLogger;
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
import io.openaev.utils.AgentUtils;
import io.openaev.utils.pagination.SearchPaginationInput;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
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

  /**
   * Statuses whose results are pushed to OpenCTI; IocValidationRepository spells them as literals.
   */
  static final Set<IocValidationStatus> RESULT_STATUSES =
      EnumSet.of(
          IocValidationStatus.COMPLETED, IocValidationStatus.PARTIAL, IocValidationStatus.FAILED);

  private static final int MAX_SCENARIO_NAME_LENGTH = 255;
  // The results message stays well within the 5000 characters OpenCTI accepts for a status message
  static final int MAX_ERROR_REASONS_IN_MESSAGE = 5;
  static final int MAX_ERROR_REASON_LENGTH = 300;
  static final int OUTBOX_PAGE_SIZE = 100;

  private final AtomicReference<String> runningResultsCursor = new AtomicReference<>("");
  private final AtomicReference<String> resultsPushCursor = new AtomicReference<>("");
  private final AtomicReference<String> lifecycleSyncCursor = new AtomicReference<>("");

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
  private final Optional<AuditLogger> auditLogger;

  @PersistenceContext private EntityManager entityManager;

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

  /**
   * The host names the intake of a request posted by OpenCTI resolves (the hosts of its HTTP HEAD
   * tests and the host names of the platform), to resolve them before {@link #receiveRequest} opens
   * its transaction. Reads only the IOC validation settings of the tenant.
   *
   * @throws BundleValidationError when the bundle does not follow the contract
   */
  public Set<String> hostNames(String tenantId, String stixJson, String entityId)
      throws BundleValidationError {
    Set<String> names =
        new LinkedHashSet<>(
            IocValidationPlanner.urlHostNames(
                bundleParser.parse(stixJson, entityId).iocs().stream()
                    .map(IocValidationService::toIoc)
                    .toList()));
    names.addAll(platformHostNames(tenantId));
    return names;
  }

  /**
   * The host names the approval of a validation resolves (the hosts of its HTTP HEAD tests and the
   * host names of the platform), to resolve them before {@link #approve} opens its transaction;
   * empty when it no longer awaits approval.
   *
   * @throws ElementNotFoundException when it does not exist or belongs to another tenant
   */
  @Transactional(readOnly = true)
  public Set<String> hostNames(TxCtx ctx, @NotBlank final String id) {
    IocValidation validation =
        iocValidationRepository
            .findById(id)
            .orElseThrow(() -> new ElementNotFoundException("IOC validation not found"));
    if (validation.getStatus() != IocValidationStatus.AWAITING_APPROVAL) {
      return Set.of();
    }
    Set<String> names =
        new LinkedHashSet<>(IocValidationPlanner.urlHostNames(validation.getIocs()));
    names.addAll(platformHostNames(singleTenant(ctx)));
    return names;
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
   * re-dispatching it) return the existing record instead of duplicating it, also when delivered
   * concurrently to several API nodes: the intake of a request is serialised by a
   * transaction-scoped advisory lock.
   *
   * @param ctx single-tenant scope the request is attributed to
   * @param stixJson the bundle of the CTI event
   * @param entityId the OpenCTI request internal id of the CTI event
   * @param hostAnswers the DNS answers for {@link #hostNames(String, String, String)}, gathered
   *     before this transaction
   * @throws BundleValidationError when the bundle does not follow the contract
   */
  @Transactional(rollbackFor = Exception.class)
  public IocValidation receiveRequest(
      TxCtx ctx, String stixJson, String entityId, IocValidationHostAnswers hostAnswers)
      throws BundleValidationError {
    String tenantId = singleTenant(ctx);
    IocValidationRequest request = bundleParser.parse(stixJson, entityId);
    iocValidationRepository.lockRequestIntake(intakeLockKey(tenantId, request.requestId()));
    Optional<IocValidation> existing =
        iocValidationRepository.findByExternalIdAndTenantId(request.requestId(), tenantId);
    if (existing.isPresent()) {
      log.info(
          "IOC validation request {} already received for tenant {}, keeping the existing record",
          request.requestId(),
          tenantId);
      return existing.get();
    }

    IocValidationSettings settings = decisionSettings(tenantId, hostAnswers.resolver());
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
                .map(
                    pair ->
                        toPair(
                            pair, request.platformNamesByRef(), request.deploymentCreatedByRef()))
                .toList()));
    validation.setOpenctiUrl(openCtiUrl(tenantId, request.requestId()));
    IocValidationPlanner.apply(validation.getIocs(), settings, hostAnswers.resolver());
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
    auditRefusals(saved, saved.getIocs(), "intake");
    return saved;
  }

  // -- UPDATE --

  /** What the approval of a validation would start now (see {@link #approvalPreview}). */
  public record ApprovalPreview(IocValidation validation, String fingerprint, String blocker) {}

  /**
   * The settings and DNS answers an approval plans with, what the operator was shown, and why it
   * cannot run (null when it can).
   */
  private record ApprovalPlan(
      IocValidationSettings settings,
      IocValidationPlanner.HostResolver resolved,
      List<Boolean> shownRefusals,
      String blocker) {}

  /** Where the tests of an approval run, or why nothing can run (null when they can). */
  private record ApprovalTargets(
      AssetGroup assetGroup, List<Endpoint> runnableEndpoints, String blocker) {

    static ApprovalTargets blocked(String blocker) {
      return new ApprovalTargets(null, List.of(), blocker);
    }
  }

  /**
   * What the approval of a waiting validation would start now, planned by the code of the approval
   * (current safety settings, DNS answers and security platforms) without starting or recording
   * anything. The operator confirms this preview, and the approval runs only if it plans the same
   * (see {@link #approvalFingerprint}).
   *
   * @param hostAnswers the DNS answers for {@link #hostNames(TxCtx, String)}, gathered before this
   *     transaction
   * @throws BadRequestException when the request is no longer awaiting approval
   */
  @Transactional(readOnly = true)
  public ApprovalPreview approvalPreview(
      TxCtx ctx, @NotBlank final String id, @NotNull final IocValidationHostAnswers hostAnswers) {
    String tenantId = singleTenant(ctx);
    IocValidation validation =
        iocValidationRepository
            .findById(id)
            .orElseThrow(() -> new ElementNotFoundException("IOC validation not found"));
    requireAwaitingApproval(validation);
    // The plan changes the IOCs, kinds and pairs it is given, and with open-in-view the session
    // outlives this read-only transaction: a later write on it would flush the plan, so the
    // preview plans on the validation detached from the session
    entityManager.detach(validation);
    ApprovalPlan plan = planApproval(validation, tenantId, hostAnswers);
    String blocker =
        plan.blocker() != null
            ? plan.blocker()
            : approvalTargets(validation, plan.settings(), tenantId).blocker();
    return new ApprovalPreview(validation, approvalFingerprint(validation), blocker);
  }

  /**
   * Approves a waiting validation: re-applies the current safety settings (a setting narrowed since
   * the request arrived drops a test, a setting widened since never adds one), builds the
   * validation scenario and launches its simulation, provided that it plans what the operator
   * confirmed.
   *
   * @param previewFingerprint the fingerprint of the {@link #approvalPreview} the operator
   *     confirmed
   * @throws BadRequestException when the request is no longer awaiting approval, when a test shown
   *     to the operator would now run with other arguments, when the approval now plans other tests
   *     or security platforms than the confirmed preview, or when nothing can run (no allowed test,
   *     no asset group, no endpoint with an active agent)
   */
  @Transactional(rollbackFor = Exception.class)
  public IocValidation approve(
      TxCtx ctx,
      @NotBlank final String id,
      @NotNull final User decider,
      @NotNull final IocValidationHostAnswers hostAnswers,
      @NotBlank final String previewFingerprint) {
    String tenantId = singleTenant(ctx);
    IocValidation validation = lockedIocValidation(id);
    requireAwaitingApproval(validation);

    ApprovalPlan plan = planApproval(validation, tenantId, hostAnswers);
    if (plan.blocker() != null) {
      throw new BadRequestException(plan.blocker());
    }
    if (!approvalFingerprint(validation).equals(previewFingerprint)) {
      throw new BadRequestException(
          "The tests or security platforms of this approval changed since it was shown (IOC"
              + " validation settings, DNS answers or security platforms). Review them again,"
              + " then approve.");
    }
    IocValidationSettings settings = plan.settings();
    IocValidationPlanner.HostResolver resolved = plan.resolved();
    List<Boolean> shownRefusals = plan.shownRefusals();
    ApprovalTargets targets = approvalTargets(validation, settings, tenantId);
    if (targets.blocker() != null) {
      throw new BadRequestException(targets.blocker());
    }
    AssetGroup assetGroup = targets.assetGroup();
    List<Endpoint> runnableEndpoints = targets.runnableEndpoints();

    // Scenarios, injects and simulations are still v1 tables: TenantBaseListener stamps them from
    // TenantContext, which only the /api/tenants/{tenantId}/ route sets. Bridging the resolved
    // tenant keeps every row of the validation in the request tenant, as for security coverage.
    String previousTenant =
        TenantContext.hasCurrentTenant() ? TenantContext.getCurrentTenant() : null;
    TenantContext.setCurrentTenant(tenantId);
    try {
      Scenario scenario = createScenario(ctx, validation);
      Set<Inject> injects =
          createInjects(
              ctx,
              validation,
              settings,
              resolved,
              scenario,
              assetGroup,
              executorsOf(runnableEndpoints));
      if (injects.isEmpty()) {
        throw new BadRequestException(
            "Nothing can run: no endpoint of the asset group '%s' runs a platform the planned tests support"
                .formatted(assetGroup.getName()));
      }
      scenario.setInjects(injects);
      scenarioService.throwIfScenarioNotLaunchable(scenario);
      ScenarioToExerciseService.ScenarioSimulation launched =
          scenarioToExerciseService.toSimulation(
              scenario,
              Instant.now().truncatedTo(ChronoUnit.MINUTES).plus(1, ChronoUnit.MINUTES),
              true);
      Exercise simulation = launched.simulation();
      trackSimulationInjects(validation, launched.simulationInjectIdsByScenarioInjectId());

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
      List<IocValidationIoc> refusedAtApproval = new ArrayList<>();
      for (int index = 0; index < saved.getIocs().size(); index++) {
        if (saved.getIocs().get(index).isRefused() && !shownRefusals.get(index)) {
          refusedAtApproval.add(saved.getIocs().get(index));
        }
      }
      auditRefusals(saved, refusedAtApproval, "approval");
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
   * Plans the approval of a validation with the current safety settings and the given DNS answers,
   * on the validation itself: the test of each IOC, its plan fingerprint, and the OpenAEV security
   * platform of each pair. It cannot run when a test shown to the operator would now run with other
   * arguments.
   */
  private ApprovalPlan planApproval(
      IocValidation validation, String tenantId, IocValidationHostAnswers hostAnswers) {
    IocValidationSettings settings = decisionSettings(tenantId, hostAnswers.resolver());
    // The approval starts at most what the operator was shown: a setting narrowed since the request
    // arrived drops a test, a setting widened since never turns a skipped IOC into a test.
    List<IocValidationIoc> iocs = validation.getIocs();
    List<IocValidationTestKind> shownKinds =
        iocs.stream().map(IocValidationIoc::getTestKind).toList();
    List<String> shownMessages = iocs.stream().map(IocValidationIoc::getMessage).toList();
    List<Boolean> shownRefusals = iocs.stream().map(IocValidationIoc::isRefused).toList();
    List<String> shownFingerprints =
        iocs.stream().map(IocValidationIoc::getPlanFingerprint).toList();
    IocValidationPlanner.HostResolver resolved =
        IocValidationPlanner.apply(iocs, settings, hostAnswers.resolver());
    for (int index = 0; index < iocs.size(); index++) {
      IocValidationIoc ioc = iocs.get(index);
      String shownFingerprint = shownFingerprints.get(index);
      if (shownFingerprint != null
          && ioc.getPlanFingerprint() != null
          && !shownFingerprint.equals(ioc.getPlanFingerprint())) {
        return new ApprovalPlan(
            settings,
            resolved,
            shownRefusals,
            "The test planned for '%s' changed with the IOC validation settings since the request was shown. Reject the request and ask for a new validation from OpenCTI."
                .formatted(ioc.getValue()));
      }
      if (shownKinds.get(index) == null) {
        ioc.setRefused(shownRefusals.get(index));
        ioc.setMessage(
            ioc.getTestKind() == null || shownRefusals.get(index)
                ? shownMessages.get(index)
                : "Not run: skipped when OpenAEV received the request; a new request from OpenCTI"
                    + " runs it under the current IOC validation settings");
        ioc.setTestKind(null);
      }
    }
    validation.setAllowedTestKinds(sortedKinds(settings.allowedTestKinds()));
    matchSecurityPlatforms(validation, tenantId);
    return new ApprovalPlan(settings, resolved, shownRefusals, null);
  }

  /**
   * The asset group the tests of a planned approval run on, and its endpoints with an active agent;
   * or why nothing can run: no test left, no asset group, no endpoint, no endpoint with an active
   * agent.
   */
  private ApprovalTargets approvalTargets(
      IocValidation validation, IocValidationSettings settings, String tenantId) {
    if (validation.getIocs().stream().noneMatch(ioc -> ioc.getTestKind() != null)) {
      return ApprovalTargets.blocked(
          "Nothing can run: every IOC of this request was skipped when it was received or is no"
              + " longer allowed by the IOC validation settings. Reject the request and ask for a"
              + " new validation from OpenCTI.");
    }
    if (!settings.hasAssetGroup()) {
      return ApprovalTargets.blocked(
          "Choose the asset group that runs the validation tests in the IOC validation settings"
              + " before approving");
    }
    AssetGroup assetGroup;
    try {
      assetGroup = assetGroupService.tenantAssetGroup(tenantId, settings.assetGroupId());
    } catch (ElementNotFoundException e) {
      return ApprovalTargets.blocked(
          "The IOC validation asset group no longer exists: choose another one in the settings");
    }
    List<Endpoint> endpoints =
        assetGroupService
            .assetsFromAssetGroupMap(List.of(assetGroup))
            .getOrDefault(assetGroup, List.of());
    if (endpoints.isEmpty()) {
      return ApprovalTargets.blocked(
          "The IOC validation asset group '%s' contains no endpoint to run the tests on"
              .formatted(assetGroup.getName()));
    }
    List<Endpoint> runnableEndpoints =
        endpoints.stream().filter(IocValidationService::hasRunnableAgent).toList();
    if (runnableEndpoints.isEmpty()) {
      return ApprovalTargets.blocked(
          ("No endpoint of the asset group '%s' has an active agent: start an agent or choose another"
                  + " asset group in Settings > Customization > IOC validation, then approve again.")
              .formatted(assetGroup.getName()));
    }
    return new ApprovalTargets(assetGroup, runnableEndpoints, null);
  }

  /**
   * Fingerprint of what the approval of a planned validation starts: the test of each IOC with its
   * plan fingerprint (kind and arguments), and the OpenAEV security platform of each pair of the
   * IOCs that run. An approval runs only with the fingerprint of the preview the operator
   * confirmed.
   */
  static String approvalFingerprint(IocValidation validation) {
    StringBuilder canonical = new StringBuilder("v1");
    Set<String> planned = new HashSet<>();
    for (IocValidationIoc ioc : validation.getIocs()) {
      canonical
          .append("\nioc\t")
          .append(ioc.getIndicatorRef())
          .append('\t')
          .append(ioc.getTestKind() == null ? "" : ioc.getTestKind().name())
          .append('\t')
          .append(Objects.toString(ioc.getPlanFingerprint(), ""));
      if (ioc.getTestKind() != null) {
        planned.add(ioc.getIndicatorRef());
      }
    }
    validation.getPairs().stream()
        .filter(pair -> planned.contains(pair.getIndicatorRef()))
        .map(
            pair ->
                "\npair\t%s\t%s\t%s"
                    .formatted(
                        pair.getIndicatorRef(),
                        pair.getPlatformRef(),
                        Objects.toString(pair.getSecurityPlatformId(), "")))
        .sorted()
        .forEach(canonical::append);
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256")
                  .digest(canonical.toString().getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 is not available", e);
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

  /**
   * Evaluates a bounded page of the running validations of every tenant, one short transaction
   * each; the next run continues with the following page.
   */
  public void computeRunningResults() {
    for (IocValidationRef ref :
        nextOutboxPage(runningResultsCursor, iocValidationRepository::findRunningRefs)) {
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
    IocValidation validation = iocValidationRepository.findByIdForUpdate(id).orElse(null);
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

  /**
   * One bounded page of an outbox, from where the previous run stopped; the walk starts over once a
   * page comes back short, so every pending validation is visited in turn and a validation that
   * keeps failing stays for a later run without holding the others back.
   */
  private List<IocValidationRef> nextOutboxPage(
      AtomicReference<String> cursor, BiFunction<String, Pageable, List<IocValidationRef>> query) {
    List<IocValidationRef> page =
        allTenants(() -> query.apply(cursor.get(), PageRequest.of(0, OUTBOX_PAGE_SIZE)));
    cursor.set(page.size() < OUTBOX_PAGE_SIZE ? "" : page.getLast().getId());
    return page;
  }

  /**
   * Pushes the result bundles of a bounded page of finished validations OpenCTI has not received
   * yet. OpenCTI unreachable ends the run: the next one resumes from the validation that failed.
   */
  public void pushPendingResults() {
    for (IocValidationRef ref :
        nextOutboxPage(
            resultsPushCursor, iocValidationRepository::findRefsWithPendingResultsPush)) {
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
        markResultsPushed(tenantId, ref.getId(), Instant.now());
      } catch (IOException e) {
        log.warn(
            "OpenCTI unreachable while pushing the results of IOC validation {} for tenant {}"
                + " (resumed from it on the next run): {}",
            ref.getId(),
            tenantId,
            e.getMessage());
        resultsPushCursor.set(ref.getId());
        return;
      } catch (ConnectorError e) {
        log.warn(
            "Could not push the results of IOC validation {} to OpenCTI for tenant {} (retried on"
                + " a later run): {}",
            ref.getId(),
            tenantId,
            e.getMessage());
      } catch (Exception e) {
        log.error("Could not push the results of IOC validation {}", ref.getId(), e);
      }
    }
  }

  /**
   * Reports to OpenCTI the statuses of a bounded page of validations it has not acknowledged yet.
   * OpenCTI unreachable ends the run: the next one resumes from the validation that failed.
   */
  public void syncPendingLifecycles() {
    for (IocValidationRef ref :
        nextOutboxPage(
            lifecycleSyncCursor, iocValidationRepository::findRefsWithPendingLifecycleSync)) {
      try {
        reportLifecycle(ref.getTenantId(), ref.getId());
      } catch (IOException e) {
        log.warn(
            "OpenCTI unreachable while reporting the status of IOC validation {} for tenant {}"
                + " (resumed from it on the next run): {}",
            ref.getId(),
            ref.getTenantId(),
            e.getMessage());
        lifecycleSyncCursor.set(ref.getId());
        return;
      } catch (ConnectorError e) {
        log.warn(
            "Could not report the status of IOC validation {} to OpenCTI for tenant {} (retried on"
                + " a later run): {}",
            ref.getId(),
            ref.getTenantId(),
            e.getMessage());
      } catch (Exception e) {
        log.error("Could not report the status of IOC validation {}", ref.getId(), e);
      }
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
      return reportLifecycle(tenantId, id);
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

  private boolean reportLifecycle(String tenantId, String id) throws ConnectorError, IOException {
    Optional<IocValidationRequestStatusUpdate> update =
        inTenant(tenantId, () -> iocValidationRepository.findById(id).flatMap(this::statusUpdate));
    if (update.isEmpty()) {
      return false;
    }
    if (!isIocValidationConnectorRegistered(tenantId)) {
      log.debug("IOC validation connector of tenant {} not registered yet, status kept", tenantId);
      return false;
    }
    openCTIConnectorService.updateIocValidationRequestStatus(update.get(), tenantId);
    IocValidationStatus reported =
        IocValidationStatus.valueOf(update.get().getStatus().toUpperCase(Locale.ROOT));
    markSynced(tenantId, id, reported);
    return true;
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

  /**
   * Records that OpenCTI received the result bundle, in its own short transaction. The row is
   * loaded with its lock, as by every other write of a validation: the entity is saved whole, so an
   * unlocked snapshot could write back the state another transaction is changing.
   */
  void markResultsPushed(String tenantId, String id, Instant now) {
    inTenant(
        tenantId,
        () ->
            iocValidationRepository
                .findByIdForUpdate(id)
                .ifPresent(
                    validation -> {
                      validation.setResultsPushedAt(now);
                      iocValidationRepository.save(validation);
                    }));
  }

  /**
   * Records the status OpenCTI acknowledged, in its own short transaction, under the row lock (see
   * {@link #markResultsPushed}). A status that moved on meanwhile stays pending and is reported on
   * the next run.
   */
  void markSynced(String tenantId, String id, IocValidationStatus reported) {
    inTenant(
        tenantId,
        () ->
            iocValidationRepository
                .findByIdForUpdate(id)
                .ifPresent(
                    validation -> {
                      validation.setLifecycleSyncedStatus(reported);
                      iocValidationRepository.save(validation);
                    }));
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

  /**
   * The settings an intake or an approval decides with: the tenant settings and the hosts of the
   * platform with the addresses their names resolve to, which no test may target.
   *
   * @param resolver the DNS answers gathered before the transaction, the platform host names
   *     included (see {@link #hostNames(String, String, String)})
   */
  private IocValidationSettings decisionSettings(
      String tenantId, IocValidationPlanner.HostResolver resolver) {
    IocValidationSettings settings = settingsService.settings(tenantId);
    Set<String> platformHosts = platformHosts(tenantId, settings);
    return settings.withPlatformHosts(
        IocValidationPlanner.withPlatformAddresses(platformHosts, resolver),
        IocValidationPlanner.unansweredPlatformHostNames(platformHosts, resolver));
  }

  private Set<String> platformHostNames(String tenantId) {
    return IocValidationPlanner.platformHostNames(
        platformHosts(tenantId, settingsService.settings(tenantId)));
  }

  /** The hosts of OpenAEV, of the OpenCTI the tenant is connected to and of its egress proxy. */
  private Set<String> platformHosts(String tenantId, IocValidationSettings settings) {
    List<String> urls = new ArrayList<>();
    urls.add(openAEVConfig.getBaseUrl());
    urls.add(openAEVConfig.getBaseUrlForAgent());
    urls.add(settings.httpProxyUrl());
    openCTIConnectorService
        .getIocValidationConnector(tenantId)
        .ifPresent(
            connector -> {
              urls.add(connector.getUrl());
              urls.add(connector.getApiUrl());
            });
    return IocValidationPlanner.platformHosts(urls);
  }

  /**
   * Records every refused IOC value in the application log and, when audit logging is enabled, in
   * the audit log: a refused value is a value from a feed that could have reached a payload
   * command.
   */
  private void auditRefusals(IocValidation validation, List<IocValidationIoc> iocs, String stage) {
    for (IocValidationIoc ioc : iocs) {
      if (!ioc.isRefused()) {
        continue;
      }
      log.warn(
          "IOC validation {}: the value of indicator {} was refused at {}: {}",
          validation.getId(),
          ioc.getIndicatorRef(),
          stage,
          ioc.getMessage());
      auditLogger.ifPresent(
          logger -> {
            LinkedHashMap<String, Object> contextData = new LinkedHashMap<>();
            contextData.put("ioc_validation_external_id", validation.getExternalId());
            contextData.put("indicator_ref", ioc.getIndicatorRef());
            contextData.put("observable_type", ioc.getObservableType());
            contextData.put(
                "requested_test_kind",
                ioc.getRequestedTestKind() == null ? null : ioc.getRequestedTestKind().name());
            contextData.put("stage", stage);
            contextData.put("reason", ioc.getMessage());
            logger.logEvent(
                AuditEvent.builder()
                    .eventType(EventType.EXECUTION)
                    .eventScope(AuditEventScope.IOC_VALUE_REFUSED)
                    .eventStatus(EventStatus.WARNING)
                    .resourceType(ResourceType.IOC_VALIDATION)
                    .resourceId(validation.getId())
                    .message(ioc.getMessage())
                    .contextData(contextData)
                    .origin(AuditEventOrigin.SYSTEM)
                    .build());
          });
    }
  }

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
    return injectExpectationService.findTechnicalLeafExpectationsByInjectIds(injectIds).stream()
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
      IocValidationPlanner.HostResolver resolved,
      Scenario scenario,
      AssetGroup assetGroup,
      List<String> executors) {
    Set<Tag> tags = validationTags(ctx);
    Set<Inject> injects = new HashSet<>();
    // Payload creation takes transaction-scoped locks: resolving every payload once, kind by kind
    // in
    // enum order, takes them in the same order in every approval, whatever the order of the IOCs.
    Map<IocValidationTestKind, List<Payload>> payloadsByKind =
        new EnumMap<>(IocValidationTestKind.class);
    validation.getIocs().stream()
        .map(IocValidationIoc::getTestKind)
        .filter(Objects::nonNull)
        .collect(Collectors.toCollection(() -> EnumSet.noneOf(IocValidationTestKind.class)))
        .forEach(kind -> payloadsByKind.put(kind, payloadsFor(ctx, kind, executors)));
    for (IocValidationIoc ioc : validation.getIocs()) {
      ioc.setInjectIds(new ArrayList<>());
      if (ioc.getTestKind() == null) {
        continue;
      }
      // The DNS answers of the approval: the inject runs exactly the plan that was just checked
      IocValidationPlanner.Plan plan = IocValidationPlanner.plan(ioc, settings, resolved);
      if (!plan.runnable() || plan.testKind() != ioc.getTestKind()) {
        ioc.setTestKind(null);
        ioc.setRefused(plan.refused());
        ioc.setMessage(plan.message());
        continue;
      }
      List<Payload> payloads = payloadsByKind.get(plan.testKind());
      if (payloads.isEmpty()) {
        ioc.setTestKind(null);
        ioc.setMessage(
            "Not run: no endpoint of the asset group runs Windows, Linux or macOS for this test (%s)"
                .formatted(plan.testKind().label()));
        continue;
      }
      // What the injects carry: the dispatch refuses an inject whose arguments differ from it
      IocValidationPlanner.Plan approved = approvedPlan(plan);
      ioc.setPlanFingerprint(IocValidationPlanner.fingerprint(approved));
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
        approved.arguments().forEach(content::put);
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

  /**
   * Points every IOC at the simulation injects copied from its scenario injects: the simulation
   * injects get new ids, and the expectations the outcomes are read from belong to them only.
   */
  static void trackSimulationInjects(
      IocValidation validation, Map<String, String> simulationInjectIdsByScenarioInjectId) {
    for (IocValidationIoc ioc : validation.getIocs()) {
      if (ioc.getInjectIds() == null) {
        continue;
      }
      ioc.setInjectIds(
          ioc.getInjectIds().stream()
              .map(
                  scenarioInjectId -> {
                    String simulationInjectId =
                        simulationInjectIdsByScenarioInjectId.get(scenarioInjectId);
                    if (simulationInjectId == null) {
                      throw new IllegalStateException(
                          "The validation simulation has no copy of the scenario inject %s"
                              .formatted(scenarioInjectId));
                    }
                    return simulationInjectId;
                  })
              .collect(Collectors.toCollection(ArrayList::new)));
    }
    validation.setIocs(new ArrayList<>(validation.getIocs()));
  }

  /**
   * The plan the injects of an IOC carry, whose fingerprint the approval records. A file drop also
   * gets a run seed of its own, shared by its injects, from which the server names the temporary
   * directory of each inject with the id of that inject (see {@link
   * PayloadService#iocValidationExecutionContent}): the fingerprint covers it, so an edited seed is
   * refused like any other argument.
   */
  static IocValidationPlanner.Plan approvedPlan(IocValidationPlanner.Plan plan) {
    if (plan.testKind() != IocValidationTestKind.FILE_DROP) {
      return plan;
    }
    Map<String, String> arguments = new HashMap<>(plan.arguments());
    arguments.put(IOC_VALIDATION_RUN_KEY, UUID.randomUUID().toString().replace("-", ""));
    return new IocValidationPlanner.Plan(plan.testKind(), Map.copyOf(arguments), plan.message());
  }

  private List<Payload> payloadsFor(TxCtx ctx, IocValidationTestKind kind, List<String> executors) {
    if (kind == IocValidationTestKind.DNS_RESOLUTION) {
      return List.of(payloadService.getIocValidationDnsResolutionPayload(ctx));
    }
    return executors.stream()
        .<Payload>map(
            executor -> payloadService.getIocValidationCommandPayload(ctx, kind, executor))
        .toList();
  }

  /**
   * Whether an endpoint has an active primary agent, the only agents the command injectors run a
   * test on ({@link AgentUtils#getActiveAgents}).
   */
  static boolean hasRunnableAgent(Endpoint endpoint) {
    List<Agent> agents = endpoint.getAgents();
    return agents != null
        && agents.stream().anyMatch(agent -> AgentUtils.isPrimaryAgent(agent) && agent.isActive());
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

  /**
   * Message of the OpenCTI work acknowledgement once a request is recorded. A replay returns the
   * existing validation, so the message states its actual lifecycle status.
   */
  public static String intakeAcknowledgement(IocValidationStatus status) {
    return switch (status) {
      case AWAITING_APPROVAL -> "IOC validation request recorded, awaiting approval in OpenAEV";
      case RUNNING ->
          "IOC validation request already recorded, the validation is running in OpenAEV";
      case COMPLETED ->
          "IOC validation request already recorded, the validation is completed in OpenAEV";
      case PARTIAL ->
          "IOC validation request already recorded, the validation is partially completed in"
              + " OpenAEV";
      case FAILED -> "IOC validation request already recorded, the validation failed in OpenAEV";
      case REJECTED ->
          "IOC validation request already recorded, the validation was rejected in OpenAEV";
    };
  }

  /** Advisory lock key of the intake of one OpenCTI request in one tenant. */
  static long intakeLockKey(String tenantId, String requestId) {
    UUID key =
        UUID.nameUUIDFromBytes(
            ("ioc-validation-intake:" + tenantId + ":" + requestId)
                .getBytes(StandardCharsets.UTF_8));
    return key.getMostSignificantBits() ^ key.getLeastSignificantBits();
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
      IocValidationRequest.Pair requested,
      Map<String, String> platformNames,
      Map<String, Instant> deploymentCreated) {
    IocValidationPair pair = new IocValidationPair();
    pair.setIndicatorRef(requested.indicatorRef());
    pair.setPlatformRef(requested.platformRef());
    pair.setDeployedOnRef(requested.deployedOnRef());
    pair.setDeployedOnCreatedAt(deploymentCreated.get(requested.deployedOnRef()));
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
    long refused = validation.getIocs().stream().filter(IocValidationIoc::isRefused).count();
    long skipped = validation.getIocs().size() - runnable - refused;
    String message =
        "Waiting for approval in OpenAEV: %d %s to test"
            .formatted(runnable, runnable == 1 ? "IOC" : "IOCs");
    if (skipped > 0) {
      message += ", %d skipped by the safety settings".formatted(skipped);
    }
    if (refused > 0) {
      message += ", %d refused by the value checks".formatted(refused);
    }
    return message;
  }

  /**
   * The results message sent to OpenCTI with the final status: the counts, then the distinct
   * reasons of the error outcomes, so OpenCTI tells why a pair could not be evaluated. The
   * deployment's {@code error_message} is not used for them: it is the error of the deployment
   * itself, written by the stream connector of the security platform.
   */
  static String resultsMessage(IocValidation validation) {
    String counts =
        "Prevented %d, detected %d, missed %d, error %d (of %d indicator-platform pairs)"
            .formatted(
                validation.getPreventedCount(),
                validation.getDetectedCount(),
                validation.getMissedCount(),
                validation.getErrorCount(),
                validation.getPairsCount());
    List<String> reasons =
        validation.getPairs().stream()
            .filter(pair -> pair.getOutcome() == IocValidationOutcome.ERROR)
            .map(IocValidationPair::getOutcomeReason)
            .filter(reason -> reason != null && !reason.isBlank())
            .map(
                reason ->
                    reason.length() > MAX_ERROR_REASON_LENGTH
                        ? reason.substring(0, MAX_ERROR_REASON_LENGTH)
                        : reason)
            .distinct()
            .limit(MAX_ERROR_REASONS_IN_MESSAGE)
            .toList();
    return reasons.isEmpty() ? counts : counts + ". Errors: " + String.join("; ", reasons);
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
