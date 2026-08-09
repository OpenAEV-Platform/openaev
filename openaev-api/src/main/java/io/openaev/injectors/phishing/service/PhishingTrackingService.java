package io.openaev.injectors.phishing.service;

import static io.openaev.utils.inject_expectation_result.ExpectationResultBuilder.buildForPlayerManualValidation;
import static java.time.Instant.now;

import io.openaev.database.model.BaseInjectExpectation;
import io.openaev.database.model.ContractOutputType;
import io.openaev.database.model.Finding;
import io.openaev.database.model.Inject;
import io.openaev.database.model.InjectExpectationResult;
import io.openaev.database.model.PhishingLandingPage;
import io.openaev.database.model.PhishingResult;
import io.openaev.database.model.User;
import io.openaev.database.repository.InjectExpectationRepository;
import io.openaev.database.repository.PhishingResultRepository;
import io.openaev.database.repository.TeamRepository;
import io.openaev.database.repository.UserRepository;
import io.openaev.rest.finding.FindingService;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Encapsulates the per-recipient tracking lifecycle of the internal phishing injector: creation of
 * the opaque tracking token when the lure email is sent, and the open/click/submit transitions
 * driven by the public tracking endpoints. Also owns the two side effects those transitions carry -
 * auto-fulfilling the recipient's MANUAL inject expectation (mirroring the article auto-fulfill
 * pattern in {@code ChannelService.validateArticles}) and turning submitted data into a {@code
 * Credentials} {@link Finding}.
 *
 * <p>All methods are tenant-scoped: the executor runs inside the inject execution job (tenant
 * filter set), and the public endpoints set the tenant from the {@code tenantId} path segment
 * before calling in.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class PhishingTrackingService {

  /** Finding field used for captured credentials (dedup key together with type + value). */
  public static final String CREDENTIALS_FIELD = "phishing_credentials";

  private static final SecureRandom SECURE_RANDOM = new SecureRandom();

  private final PhishingResultRepository phishingResultRepository;
  private final InjectExpectationRepository injectExpectationRepository;
  private final UserRepository userRepository;
  private final TeamRepository teamRepository;
  private final FindingService findingService;

  /** Generates a URL-safe, unguessable per-recipient tracking token. */
  public static String generateToken() {
    byte[] buffer = new byte[24];
    SECURE_RANDOM.nextBytes(buffer);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(buffer);
  }

  /**
   * Creates and persists the tracking row for one recipient at send time. The token is embedded in
   * the lure email's tracking pixel and click link.
   *
   * <p>Runs in its OWN committed transaction ({@code REQUIRES_NEW}), independent of the inject
   * execution transaction that drives the send loop. This is load-bearing, not an optimization: the
   * phishing injector performs an irreversible side effect (a real email leaves the platform)
   * inside that execution transaction. If this per-recipient write instead joined it and failed a
   * constraint, the failure would surface only at the execution transaction's commit - AFTER every
   * lure email was already sent - roll the whole execution back to its pre-run {@code QUEUING}
   * status, and the minutely {@code InjectsExecutionJob} would re-select and re-execute the inject,
   * re-sending the emails every minute (an unbounded loop that gets the sender blacklisted). By
   * committing on its own here, a tracking-write failure rolls back only this row: the executor's
   * per-recipient catch skips that single recipient, the execution transaction is never poisoned,
   * and the inject reaches a terminal status. Committing before the trackable link is published
   * also guarantees an early recipient's open/click always finds a row to fulfill.
   *
   * <p>The {@code inject} / {@code landingPage} associations are set from the passed entities and
   * {@code user} / {@code team} from reference proxies: this row is a fresh insert with no cascade,
   * so Hibernate only needs their FK ids and never touches the suspended outer persistence context.
   */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public PhishingResult createResult(
      @NotNull final Inject inject,
      @NotNull final PhishingLandingPage landingPage,
      @NotBlank final String userId,
      final String teamId) {
    PhishingResult result = new PhishingResult();
    result.setToken(generateToken());
    result.setInject(inject);
    result.setLandingPage(landingPage);
    // FK-only association: a reference proxy avoids an N+1 SELECT per recipient at send time.
    result.setUser(userRepository.getReferenceById(userId));
    if (teamId != null) {
      result.setTeam(teamRepository.getReferenceById(teamId));
    }
    result.setSentAt(now());
    return phishingResultRepository.save(result);
  }

  public Optional<PhishingResult> resolveByToken(@NotBlank final String token) {
    return phishingResultRepository.findByToken(token);
  }

  /**
   * Resolves the owning tenant of a tracking token with no tenant context set. The public landing /
   * tracking endpoints no longer carry the tenant in the URL (the token is globally unique), so the
   * caller uses this to recover and set the tenant before any tenant-filtered work runs.
   */
  public Optional<String> resolveTenantIdByToken(@NotBlank final String token) {
    return phishingResultRepository.findTenantIdByToken(token);
  }

  /** Marks the email as opened (first open wins) and records request metadata. */
  public Optional<PhishingResult> markOpened(
      @NotBlank final String token, final String ip, final String userAgent) {
    return phishingResultRepository
        .findByToken(token)
        .map(
            result -> {
              if (result.getOpenedAt() == null) {
                result.setOpenedAt(now());
              }
              applyRequestMetadata(result, ip, userAgent);
              return phishingResultRepository.save(result);
            });
  }

  /**
   * Marks the tracking link as clicked and auto-fulfills the recipient's MANUAL expectation
   * (clicking the lure is the "fell for phishing" signal). An open is implied by a click.
   */
  public Optional<PhishingResult> markClicked(
      @NotBlank final String token, final String ip, final String userAgent) {
    return phishingResultRepository
        .findByToken(token)
        .map(
            result -> {
              if (result.getOpenedAt() == null) {
                result.setOpenedAt(now());
              }
              if (result.getClickedAt() == null) {
                result.setClickedAt(now());
              }
              applyRequestMetadata(result, ip, userAgent);
              PhishingResult saved = phishingResultRepository.save(result);
              fulfillManualExpectation(saved, "Clicked the phishing link");
              return saved;
            });
  }

  /**
   * Records submitted data, captures it as a {@code Credentials} finding (when the landing page is
   * configured to capture) and fulfills the recipient's MANUAL expectation.
   */
  public Optional<PhishingResult> markSubmitted(
      @NotBlank final String token,
      final String username,
      final String password,
      final String ip,
      final String userAgent) {
    return phishingResultRepository
        .findByToken(token)
        .map(
            result -> {
              Instant timestamp = now();
              if (result.getOpenedAt() == null) {
                result.setOpenedAt(timestamp);
              }
              if (result.getClickedAt() == null) {
                result.setClickedAt(timestamp);
              }
              // Capture only on the request that wins the submit transition. A repeat or concurrent
              // POST (a victim double-submitting) would otherwise re-insert the same Credentials
              // finding, break its unique constraint at flush and roll back the tracking write.
              boolean firstSubmit = result.getSubmittedAt() == null;
              if (firstSubmit) {
                result.setSubmittedAt(timestamp);
              }
              applyRequestMetadata(result, ip, userAgent);
              if (firstSubmit) {
                captureCredentials(result, username, password);
              }
              PhishingResult saved = phishingResultRepository.save(result);
              if (firstSubmit) {
                fulfillManualExpectation(saved, "Submitted data on the phishing page");
              }
              return saved;
            });
  }

  private void applyRequestMetadata(
      final PhishingResult result, final String ip, final String userAgent) {
    if (ip != null && !ip.isBlank()) {
      result.setIp(ip);
    }
    if (userAgent != null && !userAgent.isBlank()) {
      result.setUserAgent(userAgent);
    }
  }

  private void captureCredentials(
      final PhishingResult result, final String username, final String password) {
    PhishingLandingPage landingPage = result.getLandingPage();
    if (landingPage == null || !landingPage.isCaptureSubmittedData()) {
      return;
    }
    if (username == null || username.isBlank()) {
      return;
    }
    Inject inject = result.getInject();
    if (inject == null) {
      return;
    }
    String value = username;
    if (landingPage.isCapturePasswords() && password != null && !password.isBlank()) {
      value = username + " / " + password;
    }
    Finding finding = new Finding();
    finding.setType(ContractOutputType.Credentials);
    finding.setField(CREDENTIALS_FIELD);
    finding.setValue(value);
    finding.setName(landingPage.getName());
    if (result.getUser() != null) {
      finding.setUsers(List.of(result.getUser()));
    }
    if (result.getTeam() != null) {
      finding.setTeams(List.of(result.getTeam()));
    }
    try {
      findingService.createFindings(List.of(finding), inject.getId());
      result.setFinding(finding);
    } catch (Exception e) {
      log.error("Failed to create phishing credentials finding: {}", e.getMessage(), e);
    }
  }

  /**
   * Sets the MANUAL expectation of this recipient to its expected score (idempotent: only the first
   * signal fills it). Mirrors {@code ChannelService.validateArticles} auto-fulfillment.
   */
  private void fulfillManualExpectation(final PhishingResult result, final String message) {
    User user = result.getUser();
    Inject inject = result.getInject();
    if (user == null || inject == null) {
      return;
    }
    List<BaseInjectExpectation> expectations =
        injectExpectationRepository.findAllByInjectAndPlayer(inject.getId(), user.getId());
    expectations.stream()
        .filter(
            expectation -> expectation.getType() == BaseInjectExpectation.EXPECTATION_TYPE.MANUAL)
        .filter(expectation -> hasNoResults(expectation.getResults()))
        .forEach(
            expectation -> {
              expectation.setResults(
                  List.of(buildForPlayerManualValidation(message, expectation.getExpectedScore())));
              expectation.setScore(expectation.getExpectedScore());
              expectation.setUpdatedAt(now());
              injectExpectationRepository.save(expectation);
            });
  }

  private boolean hasNoResults(final List<InjectExpectationResult> results) {
    return results == null
        || results.isEmpty()
        || results.stream().noneMatch(r -> r.getResult() != null && !r.getResult().isBlank());
  }
}
