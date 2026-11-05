package io.openaev.telemetry.metric_collectors;

import static io.openaev.helper.CryptoHelper.hashWithSHA256;
import static io.openaev.telemetry.metric_collectors.MetricRegistry.collectAndReset;
import static io.openaev.telemetry.metric_collectors.MetricRegistry.runAfterCommit;
import static io.opentelemetry.api.common.AttributeKey.stringKey;

import io.openaev.context.TenantScopedTransaction;
import io.openaev.context.TxCtx;
import io.openaev.database.repository.CredentialSecretReferenceRepository;
import io.openaev.rest.exception.CredentialResolutionException;
import io.opentelemetry.api.common.Attributes;
import jakarta.annotation.PostConstruct;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Telemetry on credentials: which credential types users attach to injects, per injector, and how
 * credential resolutions end (secret returned, or refused by error code). The user and the
 * credential are exported as hashes, never as ids, and no credential name or secret is collected.
 * The counters are in memory and reset at each export.
 *
 * <p>The credential count reads the repository directly, not through {@code CredentialService}:
 * that service feeds the resolution counters, so depending on it would create a bean cycle. The
 * count is platform-wide: without the {@code TxCtx.allTenants()} scope, the v2-active {@code
 * secret_references} table would read as empty.
 */
@Service
@RequiredArgsConstructor
public class CredentialMetricCollector {

  private static final String ATTRIBUTE_USER_HASH = "user_hash";
  private static final String ATTRIBUTE_CREDENTIAL_HASH = "credential_hash";
  private static final String ATTRIBUTE_CREDENTIAL_TYPE = "credential_type";
  private static final String ATTRIBUTE_INJECTOR_TYPE = "injector_type";
  private static final String ATTRIBUTE_ERROR_CODE = "error_code";

  private final MetricRegistry metricRegistry;
  private final TenantScopedTransaction tenantTx;
  private final CredentialSecretReferenceRepository credentialSecretReferenceRepository;

  private final Map<Attributes, AtomicLong> credentialUsages = new ConcurrentHashMap<>();
  private final AtomicLong resolutionSuccesses = new AtomicLong();
  private final Map<Attributes, AtomicLong> resolutionFailuresByErrorCode =
      new ConcurrentHashMap<>();

  @PostConstruct
  public void init() {
    metricRegistry.registerGauge(
        "total_credentials_count", "Number of credentials", this::countCredentials);
    metricRegistry.registerMultiGauge(
        "credential_usages_count",
        "Credentials attached to an inject since the last export, by hashed user, hashed"
            + " credential, credential type and injector type",
        this::collectCredentialUsages);
    metricRegistry.registerGauge(
        "credential_resolution_successes_count",
        "Credential resolutions that returned the secret since the last export",
        this::collectResolutionSuccesses);
    metricRegistry.registerMultiGauge(
        "credential_resolution_failures_count",
        "Credential resolutions refused since the last export, broken down by error code",
        this::collectResolutionFailures);
  }

  // -- COUNT --

  /**
   * Counts the credentials of every tenant.
   *
   * @return the number of credentials on the platform
   */
  long countCredentials() {
    // Explicit type witness: execute() is overloaded on Supplier and Runnable
    return tenantTx.<Long>execute(
        TxCtx.allTenants(), credentialSecretReferenceRepository::globalCount);
  }

  // -- RESOLUTIONS --

  /** Counts a credential resolution that returned the secret. */
  public void addResolutionSuccess() {
    resolutionSuccesses.incrementAndGet();
  }

  /**
   * Counts a credential resolution refused with the given error code.
   *
   * @param errorCode the error code the resolution was refused with
   */
  public void addResolutionFailure(CredentialResolutionException.ErrorCode errorCode) {
    resolutionFailuresByErrorCode
        .computeIfAbsent(
            Attributes.of(stringKey(ATTRIBUTE_ERROR_CODE), errorCode.name()),
            key -> new AtomicLong())
        .incrementAndGet();
  }

  /**
   * Returns the successful resolutions counted since the last call, and resets them.
   *
   * @return the successful resolution count
   */
  public long collectResolutionSuccesses() {
    return resolutionSuccesses.getAndSet(0);
  }

  /**
   * Returns the refused resolutions counted since the last call, by error code, and resets them.
   *
   * @return the refused resolution count per error code
   */
  public Map<Attributes, Long> collectResolutionFailures() {
    return collectAndReset(resolutionFailuresByErrorCode);
  }

  // -- USAGES --

  /**
   * Counts a credential a user attached to an inject, once the transaction that saves the inject
   * commits. The user and credential ids are hashed: they are never exported, and the hashes still
   * tell distinct users and credentials apart.
   *
   * @param userId the user who attached the credential
   * @param credentialId the attached credential
   * @param credentialType the type of the attached credential
   * @param injectorType the type of the inject's injector, {@code unknown} when it has none
   */
  public void addCredentialUsage(
      String userId, String credentialId, String credentialType, String injectorType) {
    Attributes attributes =
        Attributes.of(
            stringKey(ATTRIBUTE_USER_HASH), hashWithSHA256(userId),
            stringKey(ATTRIBUTE_CREDENTIAL_HASH), hashWithSHA256(credentialId),
            stringKey(ATTRIBUTE_CREDENTIAL_TYPE), MetricRegistry.normalizeLabel(credentialType),
            stringKey(ATTRIBUTE_INJECTOR_TYPE), MetricRegistry.normalizeLabel(injectorType));
    runAfterCommit(
        () ->
            credentialUsages
                .computeIfAbsent(attributes, key -> new AtomicLong())
                .incrementAndGet());
  }

  /**
   * Returns the credential attachments counted since the last call, and resets them.
   *
   * @return the attachment count per hashed user, hashed credential, credential type and injector
   *     type
   */
  public Map<Attributes, Long> collectCredentialUsages() {
    return collectAndReset(credentialUsages);
  }
}
