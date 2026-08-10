package io.openaev.injectors.phishing.service;

import static io.openaev.utils.inject_expectation_result.ExpectationResultBuilder.buildForPlayerManualValidation;
import static io.openaev.utils.inject_expectation_result.ExpectationResultBuilder.buildForTeamManualValidation;
import static java.time.Instant.now;

import io.openaev.database.model.BaseInjectExpectation;
import io.openaev.database.model.ContractOutputType;
import io.openaev.database.model.Finding;
import io.openaev.database.model.Inject;
import io.openaev.database.model.InjectExpectationResult;
import io.openaev.database.model.PhishingLandingPage;
import io.openaev.database.model.PhishingResult;
import io.openaev.database.model.TableTopInjectExpectation;
import io.openaev.database.model.Team;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Encapsulates the per-recipient tracking lifecycle of the internal phishing injector: creation of
 * the opaque tracking token when the lure email is sent, and the open/click/submit transitions
 * driven by the public tracking endpoints. Also owns the two side effects those transitions carry -
 * scoring the recipient's phishing-awareness expectations and turning submitted data into a {@code
 * Credentials} {@link Finding}.
 *
 * <p><b>Expectation polarity is inverted for phishing.</b> A phishing expectation measures whether
 * the recipient RESISTED a step (opening the lure, following the link, submitting data). Resisting
 * is the desired outcome, so each of the three step expectations is pre-scored to its full expected
 * score at send time - GREEN / "resisted" - by {@link #initializeExpectationsAsResisted(String)}.
 * The matching transition then flips the step to a zero score - RED / "fell for it" - the moment the
 * recipient performs it. Because a pre-scored row is no longer {@code score IS NULL}, the generic
 * expiration collector never touches these rows: a recipient who never interacts simply keeps the
 * green "resisted" verdict for good, which is exactly the intended "expired means success" semantics
 * without a bespoke expiration branch.
 *
 * <p>All methods are tenant-scoped: the executor runs inside the inject execution job (tenant
 * filter set), and the public endpoints set the tenant from the token before calling in.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class PhishingTrackingService {

  /** Finding field used for captured credentials (dedup key together with type + value). */
  public static final String CREDENTIALS_FIELD = "phishing_credentials";

  /**
   * Names of the three phishing-awareness expectation steps. Shared with {@code
   * PhishingLandingPageService} (which advertises them in the injector contract) so the contract and
   * the runtime scoring can never drift on which row represents which step.
   */
  public static final String STEP_OPENED = "Email opened";

  public static final String STEP_CLICKED = "Phishing link clicked";
  public static final String STEP_SUBMITTED = "Credentials submitted";

  private static final Set<String> PHISHING_STEP_NAMES =
      Set.of(STEP_OPENED, STEP_CLICKED, STEP_SUBMITTED);

  /** Score of a step the recipient fell for (RED). Full expected score is the GREEN "resisted". */
  private static final double COMPROMISED_SCORE = 0.0;

  /** Result message stamped on a step the recipient never triggered (GREEN). */
  private static final String NO_INTERACTION_MESSAGE = "No phishing interaction detected";

  // Form-field names accepted as the username / password of a submitted credential. Kept broad so a
  // cloned real-world login form (e.g. Microsoft's "loginfmt", not "username") is still captured.
  private static final List<String> USERNAME_KEYS =
      List.of(
          "username", "email", "user", "login", "loginfmt", "user_name", "userid", "user_id",
          "identifier", "emailaddress", "e-mail", "account");
  private static final List<String> PASSWORD_KEYS =
      List.of("password", "passwd", "pass", "pwd", "passwordinput", "userpassword");

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

  /**
   * Pre-scores every phishing-awareness expectation of the inject to its full expected score
   * (GREEN, "resisted"). Called once by the executor right after the expectations are built, before
   * any lure email is sent, so a recipient starts out having resisted every step. A step is only
   * flipped to RED later, when the recipient actually performs it. Idempotent: a step that already
   * carries a result (pre-scored or flipped) is left untouched.
   */
  public void initializeExpectationsAsResisted(@NotBlank final String injectId) {
    injectExpectationRepository.findAllByInjectId(injectId).stream()
        .filter(PhishingTrackingService::isPhishingStep)
        .filter(expectation -> hasNoResults(expectation.getResults()))
        .forEach(
            expectation -> {
              boolean team = isTeamRow(expectation);
              InjectExpectationResult result =
                  team
                      ? buildForTeamManualValidation(
                          NO_INTERACTION_MESSAGE, expectation.getExpectedScore())
                      : buildForPlayerManualValidation(
                          NO_INTERACTION_MESSAGE, expectation.getExpectedScore());
              expectation.setResults(List.of(result));
              expectation.setScore(expectation.getExpectedScore());
              expectation.setUpdatedAt(now());
              injectExpectationRepository.save(expectation);
            });
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

  /** Marks the email as opened (first open wins) and flips the "email opened" step to compromised. */
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
              PhishingResult saved = phishingResultRepository.save(result);
              compromiseSteps(saved, Set.of(STEP_OPENED), "Opened the phishing email");
              return saved;
            });
  }

  /**
   * Marks the tracking link as clicked and flips the "email opened" and "link clicked" steps to
   * compromised (loading the landing page implies the email was opened).
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
              compromiseSteps(
                  saved, Set.of(STEP_OPENED, STEP_CLICKED), "Opened the phishing landing page");
              return saved;
            });
  }

  /**
   * Records submitted data, captures it as a {@code Credentials} finding (when the landing page is
   * configured to capture) and flips all three steps to compromised (a submit implies open + click).
   *
   * @param fields the raw submitted form fields (field name to value), used both to build the
   *     credential value and as the completeness record
   */
  public Optional<PhishingResult> markSubmitted(
      @NotBlank final String token,
      final Map<String, String> fields,
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
                captureCredentials(result, fields);
              }
              PhishingResult saved = phishingResultRepository.save(result);
              compromiseSteps(
                  saved,
                  Set.of(STEP_OPENED, STEP_CLICKED, STEP_SUBMITTED),
                  "Submitted data on the phishing page");
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

  private void captureCredentials(final PhishingResult result, final Map<String, String> fields) {
    PhishingLandingPage landingPage = result.getLandingPage();
    if (landingPage == null || !landingPage.isCaptureSubmittedData()) {
      return;
    }
    Inject inject = result.getInject();
    if (inject == null || fields == null || fields.isEmpty()) {
      return;
    }
    String value = buildCredentialValue(fields, landingPage.isCapturePasswords());
    if (value == null || value.isBlank()) {
      return;
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
   * Builds the credential value stored on the {@code Credentials} finding. Prefers a recognized
   * username (plus password when capture is enabled). When no field name is recognized - a custom or
   * cloned login form with atypical field names - it falls back to capturing every non-empty
   * submitted field so a genuine submission is never silently dropped. Returns {@code null} when
   * there is nothing to capture.
   */
  static String buildCredentialValue(
      final Map<String, String> fields, final boolean capturePasswords) {
    Map<String, String> lowerKeyed = lowerKeyed(fields);
    String username = firstNonBlank(lowerKeyed, USERNAME_KEYS);
    String password = firstNonBlank(lowerKeyed, PASSWORD_KEYS);
    if (username != null) {
      return capturePasswords && password != null && !password.isBlank()
          ? username + " / " + password
          : username;
    }
    String joined =
        fields.entrySet().stream()
            .filter(entry -> entry.getValue() != null && !entry.getValue().isBlank())
            .filter(entry -> capturePasswords || !isPasswordKey(entry.getKey()))
            .map(entry -> entry.getKey() + "=" + entry.getValue())
            .collect(Collectors.joining("; "));
    return joined.isBlank() ? null : joined;
  }

  private static Map<String, String> lowerKeyed(final Map<String, String> fields) {
    Map<String, String> lowerKeyed = new LinkedHashMap<>();
    fields.forEach(
        (key, value) -> {
          if (key != null) {
            lowerKeyed.putIfAbsent(key.toLowerCase(Locale.ROOT), value);
          }
        });
    return lowerKeyed;
  }

  private static String firstNonBlank(
      final Map<String, String> lowerKeyed, final List<String> keys) {
    for (String key : keys) {
      String value = lowerKeyed.get(key);
      if (value != null && !value.isBlank()) {
        return value;
      }
    }
    return null;
  }

  private static boolean isPasswordKey(final String key) {
    return key != null && PASSWORD_KEYS.contains(key.toLowerCase(Locale.ROOT));
  }

  /**
   * Flips the named phishing steps to RED (compromised) for the recipient and their team. The
   * team-level row is flipped too: a team has fallen for the phishing as soon as any one of its
   * members does. Idempotent per step - an already-compromised step keeps its earliest signal.
   */
  private void compromiseSteps(
      final PhishingResult result, final Set<String> stepNames, final String message) {
    User user = result.getUser();
    Inject inject = result.getInject();
    if (user == null || inject == null) {
      return;
    }
    injectExpectationRepository.findAllByInjectAndPlayer(inject.getId(), user.getId()).stream()
        .filter(expectation -> matchesSteps(expectation, stepNames))
        .forEach(expectation -> markCompromised(expectation, message, false));
    Team team = result.getTeam();
    if (team != null) {
      injectExpectationRepository.findAllByInjectAndTeam(inject.getId(), team.getId()).stream()
          .filter(expectation -> matchesSteps(expectation, stepNames))
          .forEach(expectation -> markCompromised(expectation, message, true));
    }
  }

  private void markCompromised(
      final BaseInjectExpectation expectation, final String message, final boolean team) {
    if (expectation.getScore() != null && expectation.getScore() <= COMPROMISED_SCORE) {
      return;
    }
    InjectExpectationResult result =
        team
            ? buildForTeamManualValidation(message, COMPROMISED_SCORE)
            : buildForPlayerManualValidation(message, COMPROMISED_SCORE);
    expectation.setResults(List.of(result));
    expectation.setScore(COMPROMISED_SCORE);
    expectation.setUpdatedAt(now());
    injectExpectationRepository.save(expectation);
  }

  private static boolean matchesSteps(
      final BaseInjectExpectation expectation, final Set<String> stepNames) {
    return expectation.getType() == BaseInjectExpectation.EXPECTATION_TYPE.MANUAL
        && expectation.getName() != null
        && stepNames.contains(expectation.getName());
  }

  private static boolean isPhishingStep(final BaseInjectExpectation expectation) {
    return matchesSteps(expectation, PHISHING_STEP_NAMES);
  }

  private static boolean isTeamRow(final BaseInjectExpectation expectation) {
    return expectation instanceof TableTopInjectExpectation tableTop && tableTop.getUser() == null;
  }

  private boolean hasNoResults(final List<InjectExpectationResult> results) {
    return results == null
        || results.isEmpty()
        || results.stream().noneMatch(r -> r.getResult() != null && !r.getResult().isBlank());
  }
}
