package io.openaev.service.stix;

import static java.util.stream.Collectors.toCollection;
import static java.util.stream.Collectors.toMap;
import static java.util.stream.Collectors.toSet;

import io.openaev.database.model.AttackPattern;
import io.openaev.database.model.BaseInjectExpectation;
import io.openaev.database.model.ExecutionStatus;
import io.openaev.database.model.Exercise;
import io.openaev.database.model.Inject;
import io.openaev.database.model.InjectExpectationResult;
import io.openaev.database.model.SecurityCoverage;
import io.openaev.database.model.SecurityCoverageHuntValidation;
import io.openaev.database.model.SecurityCoverageHuntValidation.Status;
import io.openaev.database.model.SecurityPlatform;
import io.openaev.database.repository.SecurityCoverageHuntValidationRepository;
import io.openaev.opencti.client.mutations.ValidateHuntFromEmulation;
import io.openaev.opencti.connectors.service.OpenCTIConnectorService;
import io.openaev.opencti.errors.ConnectorError;
import io.openaev.rest.exercise.service.ExerciseService;
import io.openaev.service.AssetService;
import io.openaev.telemetry.metric_collectors.ResultsMetricCollector;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * OpenCTI hunt validation loop: once an inject emulating an ATT&CK technique has a computed verdict
 * from a security platform, OpenCTI is asked ({@code huntValidateFromEmulation}) to run its hunts
 * covering that technique on that platform over the emulation window, so the hunts prove whether
 * they catch it.
 *
 * <p>Built as an outbox ({@code security_coverage_hunt_validations}) in three steps, so that no
 * pooled connection is ever held across an OpenCTI call and the coverage job is never slowed down
 * by OpenCTI:
 *
 * <ol>
 *   <li>{@link #planForSimulation}: the security coverage job, right after the simulation coverage
 *       was pushed, plans one row per new (inject, technique, security platform) triple. DB only,
 *       inside the caller's tenant-scoped transaction.
 *   <li>{@link #collectDueRequests} then {@link #send}: the delivery job reads the due rows in a
 *       short transaction (giving up those older than the maximum age), then calls OpenCTI with no
 *       transaction open.
 *   <li>{@link #recordOutcomes}: the delivery job records each outcome in a second short
 *       transaction; a refused delivery is retried with an exponential backoff until the attempts
 *       run out, one that could not reach OpenCTI is postponed, and both are given up once older
 *       than the maximum age.
 * </ol>
 *
 * Failures are logged by the jobs and never propagate to the coverage computation or push.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SecurityCoverageHuntValidationService {

  /** Security platform types an OpenCTI hunt connector can be bound to. */
  static final Set<SecurityPlatform.SECURITY_PLATFORM_TYPE> HUNTABLE_PLATFORM_TYPES =
      EnumSet.of(
          SecurityPlatform.SECURITY_PLATFORM_TYPE.EDR,
          SecurityPlatform.SECURITY_PLATFORM_TYPE.XDR,
          SecurityPlatform.SECURITY_PLATFORM_TYPE.SIEM,
          SecurityPlatform.SECURITY_PLATFORM_TYPE.SOAR,
          SecurityPlatform.SECURITY_PLATFORM_TYPE.NDR,
          SecurityPlatform.SECURITY_PLATFORM_TYPE.ISPM);

  /** Expectations whose security platform verdict means the platform telemetry was searched. */
  static final Set<BaseInjectExpectation.EXPECTATION_TYPE> HUNTED_EXPECTATION_TYPES =
      EnumSet.of(
          BaseInjectExpectation.EXPECTATION_TYPE.DETECTION,
          BaseInjectExpectation.EXPECTATION_TYPE.PREVENTION);

  /** Inject statuses for which the payload ran on at least one target. */
  static final Set<ExecutionStatus> EXECUTED_STATUSES =
      EnumSet.of(ExecutionStatus.EXECUTED, ExecutionStatus.PARTIAL);

  static final Duration RETRY_BASE_DELAY = Duration.ofMinutes(5);
  static final Duration RETRY_MAX_DELAY = Duration.ofHours(6);
  static final int MAX_ERROR_LENGTH = 1024;

  /** OpenCTI refuses a window whose start is not strictly before its end. */
  static final Duration MIN_WINDOW_LENGTH = Duration.ofMinutes(1);

  /**
   * Time one tenant may spend starting OpenCTI calls per run, so a slow OpenCTI never holds the
   * delivery of the other tenants for a whole batch of request timeouts.
   */
  static final Duration TENANT_SEND_BUDGET = Duration.ofMinutes(1);

  /** Part of the claim of a delivery beyond its send budget and request timeout. */
  static final Duration CLAIM_LEASE_MARGIN = Duration.ofMinutes(2);

  private final SecurityCoverageHuntValidationConfig config;
  private final SecurityCoverageHuntValidationRepository huntValidationRepository;
  private final ExerciseService exerciseService;
  private final AssetService assetService;
  private final OpenCTIConnectorService openCTIConnectorService;
  private final ResultsMetricCollector resultsMetricCollector;

  /** Whether the loop is enabled ({@code openaev.security-coverage.hunt-validation.enabled}). */
  public boolean isEnabled() {
    return config.isEnabled();
  }

  // -- UPDATE --

  /**
   * Claims the validations of the current tenant scope due for delivery, oldest first and bounded
   * by the batch size, materialized so they can be sent once the transaction is closed.
   *
   * <p>The claim is atomic across OpenAEV instances: the due rows are read with {@code FOR UPDATE
   * SKIP LOCKED}, and each claimed row gets its {@code next_attempt_at} moved to the end of the
   * claim ({@link #claimLease()}) before the transaction commits, so no other delivery reads it
   * again while this one sends it. The end of the claim travels with the request and identifies it
   * when the outcome is recorded ({@link #recordOutcomes}). A delivery that stops mid-way leaves
   * its rows due again once the claim ends.
   *
   * <p>A due validation older than the maximum age is given up here, before any OpenCTI call, so it
   * is never sent late. Every pending validation becomes due within {@link #RETRY_MAX_DELAY}, so
   * this check reaches all of them.
   *
   * <p>Must run inside a tenant-scoped transaction: the table is tenant-active, an unscoped read
   * returns nothing.
   *
   * @param now the delivery time
   * @return the claimed validations still worth sending
   */
  public List<HuntValidationRequest> collectDueRequests(Instant now) {
    requireActiveTransaction("collectDueRequests");
    List<SecurityCoverageHuntValidation> due =
        huntValidationRepository.findDueForUpdateSkipLocked(
            Status.PENDING, now, PageRequest.of(0, config.getBatchSize()));
    if (due.isEmpty()) {
      return List.of();
    }
    // Millisecond precision: the stored value must compare equal to the one the request carries
    Instant leaseUntil = now.plus(claimLease()).truncatedTo(ChronoUnit.MILLIS);
    String reason = "Not delivered within " + config.getMaxAge();
    for (SecurityCoverageHuntValidation validation : due) {
      if (isExpired(validation, now)) {
        validation.setStatus(Status.FAILED);
        validation.setLastError(
            validation.getLastError() == null
                ? reason
                : StringUtils.abbreviate(
                    reason + "; last error: " + validation.getLastError(), MAX_ERROR_LENGTH));
      } else {
        validation.setNextAttemptAt(leaseUntil);
      }
    }
    huntValidationRepository.saveAll(due);
    return due.stream()
        .filter(validation -> validation.getStatus() == Status.PENDING)
        .map(HuntValidationRequest::from)
        .toList();
  }

  /**
   * How long a delivery holds the validations it claimed: the send budget plus one request timeout
   * plus a margin for the outcome transaction and clock differences between instances, so a running
   * delivery never outlives its claim.
   */
  Duration claimLease() {
    return TENANT_SEND_BUDGET.plus(config.getRequestTimeout()).plus(CLAIM_LEASE_MARGIN);
  }

  // -- CREATE --

  /**
   * Plans the hunt validations of a simulation whose security coverage was just pushed to OpenCTI:
   * one row per (inject, ATT&CK technique, security platform) triple not planned yet, so a rerun of
   * the coverage job never plans a triple twice.
   *
   * <p>A triple is planned once its inject has run ({@link #executionWindow}) and the security
   * platform has a computed verdict on every detection / prevention expectation it answers for the
   * inject ({@link #readySecurityPlatformIds}): the platform telemetry has then been searched (or
   * the expectation expired), so the hunts are run on settled data. Platforms not ready yet are
   * planned by a later run, as each expectation update makes the coverage job run again.
   *
   * <p>Must run inside a tenant-scoped transaction with the simulation tenant as the current
   * tenant. Database only, no network call.
   *
   * @param simulationId the simulation whose coverage was pushed
   * @return the number of validations planned by this call
   */
  public int planForSimulation(String simulationId) {
    requireActiveTransaction("planForSimulation");
    Exercise simulation = exerciseService.exercise(simulationId);
    SecurityCoverage coverage = simulation.getSecurityCoverage();
    if (coverage == null || StringUtils.isBlank(coverage.getExternalId())) {
      return 0;
    }

    Duration padding = config.getWindowPadding();
    List<InjectPlan> plans = new ArrayList<>();
    for (Inject inject : simulation.getInjects()) {
      Optional<ExecutionWindow> window = executionWindow(inject, padding);
      if (window.isEmpty()) {
        continue;
      }
      Set<String> techniqueIds = techniqueIds(inject);
      Set<String> platformIds = readySecurityPlatformIds(inject);
      if (!techniqueIds.isEmpty() && !platformIds.isEmpty()) {
        plans.add(new InjectPlan(inject.getId(), techniqueIds, platformIds, window.get()));
      }
    }
    if (plans.isEmpty()) {
      return 0;
    }

    Set<String> platformIds =
        plans.stream().flatMap(plan -> plan.platformIds().stream()).collect(toSet());
    Map<String, SecurityPlatform> huntablePlatforms =
        assetService.securityPlatformsByIds(platformIds).stream()
            .filter(
                platform -> HUNTABLE_PLATFORM_TYPES.contains(platform.getSecurityPlatformType()))
            .collect(toMap(SecurityPlatform::getId, Function.identity()));
    if (huntablePlatforms.isEmpty()) {
      return 0;
    }

    Set<ValidationKey> planned =
        huntValidationRepository
            .findAllByInjectIdIn(plans.stream().map(InjectPlan::injectId).toList())
            .stream()
            .map(ValidationKey::of)
            .collect(toCollection(LinkedHashSet::new));
    Instant now = Instant.now();
    List<SecurityCoverageHuntValidation> validations = new ArrayList<>();
    for (InjectPlan plan : plans) {
      for (String techniqueId : plan.techniqueIds()) {
        for (String platformId : plan.platformIds()) {
          SecurityPlatform platform = huntablePlatforms.get(platformId);
          if (platform != null
              && planned.add(new ValidationKey(plan.injectId(), techniqueId, platformId))) {
            validations.add(newValidation(simulation, coverage, plan, techniqueId, platform, now));
          }
        }
      }
    }
    huntValidationRepository.saveAll(validations);
    return validations.size();
  }

  // -- UPDATE --

  /**
   * Sends due validations to the tenant's OpenCTI, one {@code huntValidateFromEmulation} call each,
   * through the tenant's security coverage connector and bounded by the request timeout.
   *
   * <p>Must run with no transaction open, so that no pooled connection waits on OpenCTI. Stops at
   * the first call that cannot reach OpenCTI (or whose connector is not registered): that request
   * and the rest of the batch are reported unreachable without being sent, so they are postponed
   * without costing an attempt instead of hammering a host that is down.
   *
   * <p>No call is started once {@link #TENANT_SEND_BUDGET} is spent: the requests left are reported
   * deferred, so their claim is released and they are due again at the next run.
   *
   * @param tenantId the tenant whose OpenCTI connection is used
   * @param requests the due validations, from {@link #collectDueRequests}
   * @return one outcome per request
   */
  public List<HuntValidationOutcome> send(String tenantId, List<HuntValidationRequest> requests) {
    return send(tenantId, requests, Instant.now().plus(TENANT_SEND_BUDGET));
  }

  /**
   * {@link #send(String, List)} with an explicit deadline after which no call is started.
   *
   * @param tenantId the tenant whose OpenCTI connection is used
   * @param requests the due validations, from {@link #collectDueRequests}
   * @param deadline the instant after which the requests left are not tried
   * @return one outcome per request
   */
  List<HuntValidationOutcome> send(
      String tenantId, List<HuntValidationRequest> requests, Instant deadline) {
    if (TransactionSynchronizationManager.isActualTransactionActive()) {
      throw new IllegalStateException(
          "send() calls OpenCTI and must not run inside a transaction: the pooled connection would"
              + " be held for the whole HTTP call");
    }
    List<HuntValidationOutcome> outcomes = new ArrayList<>();
    for (int index = 0; index < requests.size(); index++) {
      if (!Instant.now().isBefore(deadline)) {
        log.debug(
            "OpenCTI hunt validation budget spent for tenant {}: {} request(s) left for the next run",
            tenantId,
            requests.size() - index);
        requests
            .subList(index, requests.size())
            .forEach(left -> outcomes.add(HuntValidationOutcome.deferred(left)));
        break;
      }
      HuntValidationRequest request = requests.get(index);
      try {
        ValidateHuntFromEmulation.HuntValidation validation =
            openCTIConnectorService.validateHuntFromEmulation(
                tenantId, request.toInput(), config.getRequestTimeout());
        int runsCount = validation.getRuns() == null ? 0 : validation.getRuns().size();
        outcomes.add(
            HuntValidationOutcome.accepted(request, validation.getHuntsCount(), runsCount));
        log.debug(
            "OpenCTI started {} hunt run(s) ({} hunt(s)) for technique {} of inject {} on security"
                + " platform {}",
            runsCount,
            validation.getHuntsCount(),
            request.techniqueId(),
            request.injectId(),
            request.securityPlatformName());
      } catch (IOException e) {
        String error = describe(e);
        requests
            .subList(index, requests.size())
            .forEach(pending -> outcomes.add(HuntValidationOutcome.unreachable(pending, error)));
        break;
      } catch (ConnectorError | RuntimeException e) {
        outcomes.add(HuntValidationOutcome.refused(request, describe(e)));
      }
    }
    resultsMetricCollector.recordCoverageHuntValidationsSent(
        outcomes.stream().filter(HuntValidationOutcome::validated).count());
    return outcomes;
  }

  /**
   * Records the outcomes of a delivery run:
   *
   * <ul>
   *   <li>an accepted validation is never sent again;
   *   <li>a refused one is retried after an exponential backoff ({@link #retryDelay}) until it
   *       reaches the maximum number of attempts, then given up;
   *   <li>one that could not reach OpenCTI is postponed without spending an attempt: an outage says
   *       nothing about the validation, and must not exhaust the attempts of every row it lasts;
   *   <li>one that was not tried is released: due again at the next run, no attempt spent.
   * </ul>
   *
   * A refused or unreachable validation is given up anyway once it is older than the maximum age,
   * so no outage keeps a validation retried forever.
   *
   * <p>The validations are read again with a row lock, so concurrent records are serialized and
   * each sees what the previous one wrote. An acceptance is always recorded unless the validation
   * is already accepted: OpenCTI started the hunt runs, no concurrent failure can undo that. Any
   * other outcome is recorded only while the claim it was sent under still holds ({@link
   * HuntValidationOutcome#appliesTo}): if that claim ended and another delivery took the
   * validation, the other delivery records its own outcome.
   *
   * <p>Must run inside a tenant-scoped transaction.
   *
   * @param outcomes the outcomes returned by {@link #send}
   * @param now the delivery time
   */
  public void recordOutcomes(List<HuntValidationOutcome> outcomes, Instant now) {
    requireActiveTransaction("recordOutcomes");
    if (outcomes.isEmpty()) {
      return;
    }
    Map<String, HuntValidationOutcome> outcomesById =
        outcomes.stream()
            .collect(
                toMap(
                    HuntValidationOutcome::validationId,
                    Function.identity(),
                    (first, second) -> second));
    List<SecurityCoverageHuntValidation> validations =
        huntValidationRepository.findAllByIdForUpdate(outcomesById.keySet()).stream()
            .filter(validation -> outcomesById.get(validation.getId()).appliesTo(validation))
            .toList();
    for (SecurityCoverageHuntValidation validation : validations) {
      HuntValidationOutcome outcome = outcomesById.get(validation.getId());
      switch (outcome.kind()) {
        case VALIDATED -> {
          validation.setAttempts(validation.getAttempts() + 1);
          validation.setStatus(Status.VALIDATED);
          validation.setHuntsCount(outcome.huntsCount());
          validation.setRunsCount(outcome.runsCount());
          validation.setValidatedAt(now);
          validation.setLastError(null);
        }
        case REFUSED -> {
          validation.setAttempts(validation.getAttempts() + 1);
          validation.setLastError(StringUtils.abbreviate(outcome.error(), MAX_ERROR_LENGTH));
          if (validation.getAttempts() >= config.getMaxAttempts() || isExpired(validation, now)) {
            validation.setStatus(Status.FAILED);
          } else {
            validation.setNextAttemptAt(now.plus(retryDelay(validation.getAttempts())));
          }
        }
        case UNREACHABLE -> {
          validation.setLastError(StringUtils.abbreviate(outcome.error(), MAX_ERROR_LENGTH));
          if (isExpired(validation, now)) {
            validation.setStatus(Status.FAILED);
          } else {
            validation.setNextAttemptAt(now.plus(RETRY_BASE_DELAY));
          }
        }
        case DEFERRED -> validation.setNextAttemptAt(now);
      }
    }
    huntValidationRepository.saveAll(validations);
  }

  private boolean isExpired(SecurityCoverageHuntValidation validation, Instant now) {
    Instant plannedAt = validation.getCreatedAt();
    return plannedAt != null && !plannedAt.plus(config.getMaxAge()).isAfter(now);
  }

  // -- PLANNING RULES --

  /**
   * The execution window of an inject that ran ({@link #EXECUTED_STATUSES}): from the time it was
   * sent to the time it completed, widened by {@code padding} on both sides, and never shorter than
   * {@link #MIN_WINDOW_LENGTH}. Empty for an inject that did not run, or ran without a recorded
   * start.
   */
  static Optional<ExecutionWindow> executionWindow(Inject inject, Duration padding) {
    return inject
        .getStatus()
        .filter(status -> EXECUTED_STATUSES.contains(status.getName()))
        .filter(status -> status.getTrackingSentDate() != null)
        .map(
            status -> {
              Instant sent = status.getTrackingSentDate();
              Instant ended =
                  status.getTrackingEndDate() == null || status.getTrackingEndDate().isBefore(sent)
                      ? sent
                      : status.getTrackingEndDate();
              Instant start = sent.minus(padding);
              Instant end = ended.plus(padding);
              Instant minimumEnd = start.plus(MIN_WINDOW_LENGTH);
              return new ExecutionWindow(start, end.isBefore(minimumEnd) ? minimumEnd : end);
            });
  }

  /** The ATT&CK external ids of the techniques the inject contract emulates, sorted. */
  static Set<String> techniqueIds(Inject inject) {
    return inject.getAttackPatterns().stream()
        .map(AttackPattern::getExternalId)
        .filter(StringUtils::isNotBlank)
        .map(String::trim)
        .collect(toCollection(TreeSet::new));
  }

  /**
   * The security platforms (expectation result {@code sourceAssetId}, the same source the coverage
   * bundle derives its platforms from) whose verdict is computed on every detection / prevention
   * expectation of the inject they answer: a result with a score, including the failure score an
   * expiration writes. A platform with one pending result left is not ready.
   */
  static Set<String> readySecurityPlatformIds(Inject inject) {
    Map<String, Boolean> readiness = new LinkedHashMap<>();
    for (BaseInjectExpectation expectation : inject.getExpectations()) {
      if (!HUNTED_EXPECTATION_TYPES.contains(expectation.getType())
          || expectation.getResults() == null) {
        continue;
      }
      for (InjectExpectationResult result : expectation.getResults()) {
        if (StringUtils.isNotBlank(result.getSourceAssetId())) {
          readiness.merge(
              result.getSourceAssetId(), result.getScore() != null, Boolean::logicalAnd);
        }
      }
    }
    return readiness.entrySet().stream()
        .filter(Map.Entry::getValue)
        .map(Map.Entry::getKey)
        .collect(toCollection(LinkedHashSet::new));
  }

  /** 5 minutes after the first failure, doubling on each further one, capped at 6 hours. */
  static Duration retryDelay(int attempts) {
    Duration delay = RETRY_BASE_DELAY.multipliedBy(1L << Math.min(Math.max(attempts - 1, 0), 16));
    return delay.compareTo(RETRY_MAX_DELAY) > 0 ? RETRY_MAX_DELAY : delay;
  }

  // -- PRIVATE --

  private static SecurityCoverageHuntValidation newValidation(
      Exercise simulation,
      SecurityCoverage coverage,
      InjectPlan plan,
      String techniqueId,
      SecurityPlatform platform,
      Instant now) {
    SecurityCoverageHuntValidation validation = new SecurityCoverageHuntValidation();
    validation.setTenant(simulation.getTenant());
    validation.setInjectId(plan.injectId());
    validation.setTechniqueId(techniqueId);
    validation.setSecurityPlatformId(platform.getId());
    validation.setSecurityPlatformName(platform.getName());
    validation.setCoverageExternalId(coverage.getExternalId());
    validation.setWindowStart(plan.window().start());
    validation.setWindowEnd(plan.window().end());
    validation.setNextAttemptAt(now);
    return validation;
  }

  /** One line: the message, followed by the cause's when it adds something (a refused connect). */
  static String describe(Exception e) {
    String message = StringUtils.defaultIfBlank(e.getMessage(), e.getClass().getSimpleName());
    Throwable cause = e.getCause();
    if (cause != null) {
      String causeMessage =
          StringUtils.defaultIfBlank(cause.getMessage(), cause.getClass().getSimpleName());
      if (!message.contains(causeMessage)) {
        message = message + ": " + causeMessage;
      }
    }
    return StringUtils.normalizeSpace(message);
  }

  private static void requireActiveTransaction(String operation) {
    if (!TransactionSynchronizationManager.isActualTransactionActive()) {
      throw new IllegalStateException(
          operation
              + "() must run inside a tenant-scoped transaction (TenantScopedTransaction):"
              + " security_coverage_hunt_validations is tenant-active, an unscoped access reads"
              + " and writes nothing");
    }
  }

  /** The emulation window of an inject, padding included. */
  record ExecutionWindow(Instant start, Instant end) {}

  private record InjectPlan(
      String injectId, Set<String> techniqueIds, Set<String> platformIds, ExecutionWindow window) {}

  private record ValidationKey(String injectId, String techniqueId, String securityPlatformId) {
    static ValidationKey of(SecurityCoverageHuntValidation validation) {
      return new ValidationKey(
          validation.getInjectId(),
          validation.getTechniqueId(),
          validation.getSecurityPlatformId());
    }
  }

  /**
   * A validation claimed for delivery, detached from its transaction. {@code leaseUntil} is the end
   * of the claim, stored as the validation's next attempt time while the claim holds.
   */
  public record HuntValidationRequest(
      String id,
      String injectId,
      String techniqueId,
      String securityPlatformId,
      String securityPlatformName,
      String coverageExternalId,
      Instant windowStart,
      Instant windowEnd,
      Instant leaseUntil) {

    static HuntValidationRequest from(SecurityCoverageHuntValidation validation) {
      return new HuntValidationRequest(
          validation.getId(),
          validation.getInjectId(),
          validation.getTechniqueId(),
          validation.getSecurityPlatformId(),
          validation.getSecurityPlatformName(),
          validation.getCoverageExternalId(),
          validation.getWindowStart(),
          validation.getWindowEnd(),
          validation.getNextAttemptAt());
    }

    /**
     * The OpenCTI input. The platform travels both as the STIX id the coverage bundles gave it
     * (derived from its name) and as its name, which OpenCTI falls back to when it does not know
     * the id.
     */
    ValidateHuntFromEmulation.Input toInput() {
      return new ValidateHuntFromEmulation.Input(
          techniqueId,
          SecurityPlatform.stixIdentityId(securityPlatformName),
          securityPlatformName,
          injectId,
          windowStart.toString(),
          windowEnd.toString(),
          coverageExternalId);
    }
  }

  /** What came of one validation request, with the end of the claim it was sent under. */
  public record HuntValidationOutcome(
      String validationId,
      Kind kind,
      Integer huntsCount,
      Integer runsCount,
      String error,
      Instant leaseUntil) {

    public enum Kind {
      /** OpenCTI accepted the validation and started its hunt runs. */
      VALIDATED,
      /** OpenCTI answered with an error, or no hunt validation: spends an attempt. */
      REFUSED,
      /** OpenCTI could not be reached or answered a server error: postponed, no attempt spent. */
      UNREACHABLE,
      /** Not tried, the send budget of the run was spent: released, no attempt spent. */
      DEFERRED
    }

    public boolean validated() {
      return kind == Kind.VALIDATED;
    }

    /**
     * Whether this outcome may be recorded on the validation as stored now: an acceptance unless
     * the validation is already accepted, any other outcome only while the validation is pending
     * under the claim the request was sent with.
     */
    boolean appliesTo(SecurityCoverageHuntValidation validation) {
      if (kind == Kind.VALIDATED) {
        return validation.getStatus() != Status.VALIDATED;
      }
      return validation.getStatus() == Status.PENDING
          && leaseUntil != null
          && leaseUntil.equals(validation.getNextAttemptAt());
    }

    static HuntValidationOutcome accepted(
        HuntValidationRequest request, int huntsCount, int runsCount) {
      return new HuntValidationOutcome(
          request.id(), Kind.VALIDATED, huntsCount, runsCount, null, request.leaseUntil());
    }

    static HuntValidationOutcome refused(HuntValidationRequest request, String error) {
      return new HuntValidationOutcome(
          request.id(), Kind.REFUSED, null, null, error, request.leaseUntil());
    }

    static HuntValidationOutcome unreachable(HuntValidationRequest request, String error) {
      return new HuntValidationOutcome(
          request.id(), Kind.UNREACHABLE, null, null, error, request.leaseUntil());
    }

    static HuntValidationOutcome deferred(HuntValidationRequest request) {
      return new HuntValidationOutcome(
          request.id(),
          Kind.DEFERRED,
          null,
          null,
          "Not tried: the delivery budget of the run was spent",
          request.leaseUntil());
    }
  }
}
