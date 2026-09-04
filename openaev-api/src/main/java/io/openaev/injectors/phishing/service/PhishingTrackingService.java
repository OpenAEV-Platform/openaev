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
import io.openaev.database.model.User;
import io.openaev.database.repository.InjectExpectationRepository;
import io.openaev.database.repository.InjectRepository;
import io.openaev.database.repository.PhishingResultRepository;
import io.openaev.database.repository.StepRepository;
import io.openaev.database.repository.TeamRepository;
import io.openaev.database.repository.UserRepository;
import io.openaev.rest.finding.FindingService;
import io.openaev.service.InjectExpectationUtils;
import io.openaev.service.chaining.StepService;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
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
 * The matching transition then flips the step to a zero score - RED / "fell for it" - the moment
 * the recipient performs it. Because a pre-scored row is no longer {@code score IS NULL}, the
 * generic expiration collector never touches these rows: a recipient who never interacts simply
 * keeps the green "resisted" verdict for good, which is exactly the intended "expired means
 * success" semantics without a bespoke expiration branch.
 *
 * <p><b>Automated probes never score.</b> Mail security gateways detonate every URL and pre-fetch
 * remote images right at delivery (Microsoft Defender Safe Links being the canonical case), so
 * without a gate every simulation against a protected mailbox turns RED within seconds with no
 * human involved. A hit whose user agent matches pure scanning/preview infrastructure, or that
 * lands within seconds of delivery, is excluded from scoring by {@link #isAutomatedProbe}: no
 * timestamps, no flip, no credential capture - the still-green step is only annotated with the
 * probe's forensic origin. Every scored flip also carries the triggering request's IP, user agent,
 * delay and an automation hint (see {@link #buildSourceMetadata}) so an analyst can always audit
 * WHERE a verdict came from.
 *
 * <p>All methods are tenant-scoped: the executor runs inside the inject execution job (tenant
 * filter set), and the public endpoints set the tenant before calling in - {@code HostedPublicApi}
 * resolves it from the token, while the legacy {@code PhishingPublicApi} routes take it from the
 * {@code tenantId} path segment.
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
   * PhishingLandingPageService} (which advertises them in the injector contract) so the contract
   * and the runtime scoring can never drift on which row represents which step.
   *
   * <p>The names are phrased as the RESISTED (green) outcome, matching the inverted phishing
   * polarity: a step is green ("resisted") while the recipient has not performed it and only flips
   * red the moment they do. A recipient who never interacts keeps a row of green "not opened / not
   * clicked / not submitted" verdicts, so the name always reads true for the desired outcome.
   */
  public static final String STEP_OPENED = "Email not opened";

  public static final String STEP_CLICKED = "Link not clicked";
  public static final String STEP_SUBMITTED = "Credentials not submitted";

  private static final Set<String> PHISHING_STEP_NAMES =
      Set.of(STEP_OPENED, STEP_CLICKED, STEP_SUBMITTED);

  /**
   * Keys of the forensic metadata stamped onto a phishing step's result when it flips to RED,
   * surfaced by the results UI so an analyst can see WHERE and WHEN a step was triggered - and, in
   * particular, tell an email security scanner apart from a real recipient. This is why "email
   * opened" / "link clicked" can turn red within seconds of sending with no human involved:
   * Microsoft Defender Safe Links pre-detonates every URL and Exchange/Outlook pre-fetches remote
   * images, so the tracking pixel loads and the landing page is fetched from the mail provider's
   * infrastructure. Shared with the frontend (same string keys) - never change one side alone.
   */
  public static final String SOURCE_IP_METADATA_KEY = "phishing_source_ip";

  public static final String SOURCE_USER_AGENT_METADATA_KEY = "phishing_source_user_agent";
  public static final String SOURCE_DELAY_METADATA_KEY = "phishing_source_delay";
  public static final String SOURCE_AUTOMATION_METADATA_KEY = "phishing_source_automation";

  /**
   * Machine-readable severity of the {@link #SOURCE_AUTOMATION_METADATA_KEY} hint: {@link
   * #AUTOMATION_LEVEL_LIKELY} (pure scanner signature or impossibly-fast hit) or {@link
   * #AUTOMATION_LEVEL_POSSIBLE} (dual-use image proxy that also carries genuine opens). The
   * frontend keys its chip label off this value instead of parsing the human-readable sentence, so
   * the wording of the hint can evolve freely.
   */
  public static final String SOURCE_AUTOMATION_LEVEL_METADATA_KEY =
      "phishing_source_automation_level";

  public static final String AUTOMATION_LEVEL_LIKELY = "likely";
  public static final String AUTOMATION_LEVEL_POSSIBLE = "possible";

  /**
   * Hard caps on the forensic metadata values persisted per step result. Both the user agent and
   * X-Forwarded-For are attacker-controlled request input (a tracking endpoint is public and
   * unauthenticated), so without a cap a hostile client could bloat every flipped step's stored
   * metadata up to the server's header-size limit. Generous enough for any legitimate value - real
   * user agents top out around 300 characters.
   */
  private static final int MAX_USER_AGENT_METADATA_LENGTH = 512;

  private static final int MAX_IP_METADATA_LENGTH = 64;

  /**
   * A hit landing at most this many seconds after the lure was delivered is treated as automated
   * scanning: no human recipient can receive, notice and act on an email within this window, while
   * Microsoft Defender Safe Links and similar gateways detonate every URL right at delivery. Such
   * hits are IGNORED for scoring (the step stays green) and recorded as an annotation instead.
   */
  private static final long AUTOMATION_MAX_DELAY_SECONDS = 10L;

  /**
   * Case-insensitive user-agent substrings of infrastructure that is NEVER a human mail client:
   * email security scanners (Safe Links, Proofpoint, Barracuda...) and link-preview bots (Slack,
   * Teams/Skype, WhatsApp...). A hit from one of these is IGNORED for scoring - the step stays
   * green and the probe is recorded as an annotation on it. Kept intentionally conservative: a
   * signature must identify pure scanning/preview infrastructure, because a false positive here
   * would hide a genuine recipient interaction. Dual-use agents (mail-client image proxies) belong
   * in {@link #PROXY_USER_AGENT_SIGNATURES} instead.
   */
  private static final List<String> SCANNER_USER_AGENT_SIGNATURES =
      List.of(
          "barracuda",
          "proofpoint",
          "mimecast",
          "symantec",
          "forcepoint",
          "trend micro",
          "trendmicro",
          "ironport",
          "fireeye",
          "safelinks",
          "urldefense",
          "bingpreview",
          "skypeuripreview",
          "google-safety",
          "slackbot",
          "facebookexternalhit",
          "whatsapp",
          "telegrambot",
          "twitterbot",
          "linkedinbot",
          "sophos",
          "fortinet",
          "zscaler");

  /**
   * Case-insensitive user-agent substrings of DUAL-USE agents: image proxies and mail-client
   * fetchers (Gmail's image proxy, Outlook desktop's Office fetcher) that carry BOTH genuine
   * recipient opens and provider-side pre-fetches. These cannot be suppressed without hiding real
   * opens, so a hit from one still scores - it is only annotated as possibly automated so the
   * analyst can weigh it.
   */
  private static final List<String> PROXY_USER_AGENT_SIGNATURES =
      List.of("googleimageproxy", "gmailimageproxy", "microsoft office", "office365");

  /** Score of a step the recipient fell for (RED). Full expected score is the GREEN "resisted". */
  private static final double COMPROMISED_SCORE = 0.0;

  /** Result message stamped on a step the recipient never triggered (GREEN). */
  public static final String NO_INTERACTION_MESSAGE = "No phishing interaction detected";

  /**
   * Result message stamped on a step that received an automated probe (mail security scanner /
   * link-preview bot) which was deliberately NOT scored. The step stays green; the annotation and
   * its forensic metadata tell the analyst the lure was scanned, not resisted-in-silence.
   */
  private static final String SCANNER_IGNORED_MESSAGE =
      "Automated scanner activity detected and ignored";

  /** Result message stamped on a team step whose recomputed verdict is RED ("fell for it"). */
  private static final String TEAM_COMPROMISED_MESSAGE = "A team member fell for the phishing";

  /**
   * Result message for a team step that stays GREEN under the "at least one" rule even though some
   * member fell: "no interaction" would be factually wrong there.
   */
  private static final String TEAM_RESISTED_MESSAGE = "At least one team member resisted";

  // Form-field names accepted as the username / password of a submitted credential. Kept broad so a
  // cloned real-world login form (e.g. Microsoft's "loginfmt", not "username") is still captured.
  private static final List<String> USERNAME_KEYS =
      List.of(
          "username",
          "email",
          "user",
          "login",
          "loginfmt",
          "user_name",
          "userid",
          "user_id",
          "identifier",
          "emailaddress",
          "e-mail",
          "account");
  private static final List<String> PASSWORD_KEYS =
      List.of("password", "passwd", "pass", "pwd", "passwordinput", "userpassword");

  private static final SecureRandom SECURE_RANDOM = new SecureRandom();

  private final PhishingResultRepository phishingResultRepository;
  private final InjectExpectationRepository injectExpectationRepository;
  private final InjectRepository injectRepository;
  private final UserRepository userRepository;
  private final TeamRepository teamRepository;
  private final StepRepository stepRepository;
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
   *
   * <p>{@code stepId} is set instead of {@code inject} for a chaining execution: at this point the
   * inject was just created in the still-uncommitted, suspended ambient transaction, so referencing
   * it here (in this own {@code REQUIRES_NEW} transaction) would fail the FK check. The step,
   * unlike the inject, is already persisted, so it is used as the stable reference until {@link
   * #resolveAndBackfillByToken} backfills the real inject once it is committed.
   */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public PhishingResult createResult(
      @NotNull final Inject inject,
      @NotNull final PhishingLandingPage landingPage,
      @NotBlank final String userId,
      final String teamId,
      final String stepId) {
    PhishingResult result = new PhishingResult();
    result.setToken(generateToken());
    if (stepId != null) {
      result.setStep(stepRepository.getReferenceById(stepId));
    } else {
      result.setInject(inject);
    }
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

  /**
   * Resolves a tracking token, backfilling {@link PhishingResult#getInject} from {@link
   * PhishingResult#getStep} when the row was created before its inject was committed (chaining
   * execution, see {@code createResult}). The step's {@code data} JSON carries the inject id once
   * {@code InjectExecutionStep#run} commits it - see {@code InjectExecutionStep#setInjectId}. Once
   * backfilled, {@code inject} is kept as the queryable reference going forward; {@code step} is
   * left untouched (still useful to correlate the producing step attempt).
   */
  public Optional<PhishingResult> resolveAndBackfillByToken(@NotBlank final String token) {
    Optional<PhishingResult> result = phishingResultRepository.findByToken(token);
    result.ifPresent(this::backfillInjectFromStep);
    return result;
  }

  private void backfillInjectFromStep(final PhishingResult result) {
    if (result.getInject() != null || result.getStep() == null) {
      return;
    }
    String data = result.getStep().getData();
    if (data == null) {
      return;
    }
    String injectId = StepService.getField(data, "inject_id");
    if (injectId == null) {
      return;
    }
    // Existence check first: without it, a stale/invalid inject_id from the step data would end
    // up wiping this PhishingResult row entirely (ON DELETE CASCADE on phishing_results_inject_fk
    // is triggered instead of failing safely). Checking first lets us leave the column null
    // instead, so the rest of the tracking update still persists.
    if (injectRepository.existsById(injectId)) {
      result.setInject(injectRepository.getReferenceById(injectId));
    } else {
      log.warn(
          "Inject {} not found yet for step {}, result kept without inject link for now"
              + " (will be retried on next resolveByToken call)",
          injectId,
          result.getStep().getId());
    }

    PhishingResult saved = phishingResultRepository.save(result);

    // If tracking events were recorded before the inject existed, reconcile expectation scoring
    // now.
    if (saved.getSubmittedAt() != null) {
      compromiseSteps(
          saved,
          Set.of(STEP_OPENED, STEP_CLICKED, STEP_SUBMITTED),
          "Submitted data on the phishing page",
          saved.getIp(),
          saved.getUserAgent());
    } else if (saved.getClickedAt() != null) {
      compromiseSteps(
          saved,
          Set.of(STEP_OPENED, STEP_CLICKED),
          "Opened the phishing landing page",
          saved.getIp(),
          saved.getUserAgent());
    } else if (saved.getOpenedAt() != null) {
      compromiseSteps(
          saved,
          Set.of(STEP_OPENED),
          "Opened the phishing email",
          saved.getIp(),
          saved.getUserAgent());
    }
  }

  /**
   * Resolves the owning tenant of a tracking token with no tenant context set. The public landing /
   * tracking endpoints no longer carry the tenant in the URL (the token is globally unique), so the
   * caller uses this to recover and set the tenant before any tenant-filtered work runs.
   */
  public Optional<String> resolveTenantIdByToken(@NotBlank final String token) {
    return phishingResultRepository.findTenantIdByToken(token);
  }

  /**
   * Marks the email as opened (first open wins) and flips the "email opened" step to compromised.
   *
   * <p>A hit identified as an automated probe (scanner user agent, or landing within seconds of
   * delivery) is NOT scored: it neither sets the timestamp nor flips the step - it only annotates
   * the still-green step so the analyst can see the lure was scanned. The recipient's own open
   * later still wins the "first open" transition with a truthful timestamp.
   */
  public Optional<PhishingResult> markOpened(
      @NotBlank final String token, final String ip, final String userAgent) {
    Optional<PhishingResult> optResult = resolveAndBackfillByToken(token);
    return optResult.map(
        result -> {
          if (isAutomatedProbe(result, userAgent)) {
            annotateResistedSteps(result, Set.of(STEP_OPENED), ip, userAgent);
            return result;
          }
          if (result.getOpenedAt() == null) {
            result.setOpenedAt(now());
          }
          applyRequestMetadata(result, ip, userAgent);
          PhishingResult saved = phishingResultRepository.save(result);
          compromiseSteps(saved, Set.of(STEP_OPENED), "Opened the phishing email", ip, userAgent);
          return saved;
        });
  }

  /**
   * Marks the tracking link as clicked and flips the "email opened" and "link clicked" steps to
   * compromised (loading the landing page implies the email was opened).
   *
   * <p>A hit identified as an automated probe (scanner user agent, or landing within seconds of
   * delivery - Safe Links style URL detonation) is NOT scored: no timestamps, no flip, only an
   * annotation on the still-green steps. A genuine click later still scores normally.
   */
  public Optional<PhishingResult> markClicked(
      @NotBlank final String token, final String ip, final String userAgent) {
    Optional<PhishingResult> optResult = resolveAndBackfillByToken(token);
    return optResult.map(
        result -> {
          if (isAutomatedProbe(result, userAgent)) {
            annotateResistedSteps(result, Set.of(STEP_OPENED, STEP_CLICKED), ip, userAgent);
            return result;
          }
          if (result.getOpenedAt() == null) {
            result.setOpenedAt(now());
          }
          if (result.getClickedAt() == null) {
            result.setClickedAt(now());
          }
          applyRequestMetadata(result, ip, userAgent);
          PhishingResult saved = phishingResultRepository.save(result);
          compromiseSteps(
              saved,
              Set.of(STEP_OPENED, STEP_CLICKED),
              "Opened the phishing landing page",
              ip,
              userAgent);
          return saved;
        });
  }

  /**
   * Records submitted data, captures it as a {@code Credentials} finding (when the landing page is
   * configured to capture) and flips all three steps to compromised (a submit implies open +
   * click).
   *
   * <p>A submit identified as an automated probe is NOT scored and NOT captured (see {@link
   * #isAutomatedProbe}): sandbox detonators fill forms with synthetic data.
   *
   * @param fields the raw submitted form fields (field name to value), used both to build the
   *     credential value and as the completeness record
   */
  public Optional<PhishingResult> markSubmitted(
      @NotBlank final String token,
      final Map<String, String> fields,
      final String ip,
      final String userAgent) {
    Optional<PhishingResult> optResult = resolveAndBackfillByToken(token);
    return optResult.map(
        result -> {
          // A sandbox detonator auto-submitting the form with synthetic data must not score
          // the recipient nor pollute findings with fake credentials. A real submit later
          // still wins the first-submit transition and is captured normally.
          if (isAutomatedProbe(result, userAgent)) {
            annotateResistedSteps(
                result, Set.of(STEP_OPENED, STEP_CLICKED, STEP_SUBMITTED), ip, userAgent);
            return result;
          }
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
              "Submitted data on the phishing page",
              ip,
              userAgent);
          return saved;
        });
  }

  /**
   * Decides whether a tracking hit is an automated probe that must be EXCLUDED from scoring, on two
   * high-precision signals: a user agent matching pure scanning/preview infrastructure ({@link
   * #SCANNER_USER_AGENT_SIGNATURES}), or a hit landing within {@link #AUTOMATION_MAX_DELAY_SECONDS}
   * of delivery - the exact behavior of Microsoft Defender Safe Links detonating every URL and
   * pre-fetching images right after the send. Without this gate, every simulation against a
   * Defender-protected mailbox turns red within seconds with zero human involvement, which is a
   * false verdict, not a signal. Dual-use agents (mail-client image proxies) deliberately do NOT
   * suppress - they score with an advisory hint instead - because genuine recipient opens ride
   * through them too.
   */
  private static boolean isAutomatedProbe(final PhishingResult result, final String userAgent) {
    if (userAgent != null && !userAgent.isBlank()) {
      String normalized = userAgent.toLowerCase(Locale.ROOT);
      if (SCANNER_USER_AGENT_SIGNATURES.stream().anyMatch(normalized::contains)) {
        return true;
      }
    }
    Instant sentAt = result.getSentAt();
    if (sentAt != null) {
      long seconds = Duration.between(sentAt, now()).getSeconds();
      return seconds >= 0 && seconds <= AUTOMATION_MAX_DELAY_SECONDS;
    }
    return false;
  }

  /**
   * Records a suppressed automated probe on the recipient's still-green steps: the step keeps its
   * full "resisted" score and only its result is rewritten to say a scanner probed the lure, with
   * the probe's forensic origin (IP / user agent / delay / automation hint) attached. Steps a human
   * already flipped RED are left strictly untouched - their forensic trail belongs to the
   * recipient's own interaction, not to a later bot. Team rows are not recomputed: no player score
   * changed. Repeated probes just refresh the annotation (latest wins).
   */
  private void annotateResistedSteps(
      final PhishingResult result,
      final Set<String> stepNames,
      final String ip,
      final String userAgent) {
    User user = result.getUser();
    Inject inject = result.getInject();
    if (user == null || inject == null) {
      return;
    }
    Map<String, String> sourceMetadata = buildSourceMetadata(result, ip, userAgent);
    injectExpectationRepository.findAllByInjectId(inject.getId()).stream()
        .filter(expectation -> isPlayerRowForUser(expectation, user.getId()))
        .filter(expectation -> matchesSteps(expectation, stepNames))
        .filter(
            expectation ->
                expectation.getScore() != null
                    && expectation.getScore() > COMPROMISED_SCORE
                    && expectation.getScore().equals(expectation.getExpectedScore()))
        .forEach(
            expectation -> {
              InjectExpectationResult annotation =
                  buildForPlayerManualValidation(
                      SCANNER_IGNORED_MESSAGE, expectation.getExpectedScore());
              if (!sourceMetadata.isEmpty()) {
                annotation.setMetadata(sourceMetadata);
              }
              expectation.setResults(List.of(annotation));
              expectation.setUpdatedAt(now());
              injectExpectationRepository.save(expectation);
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
   * username (plus password when capture is enabled). When no field name is recognized - a custom
   * or cloned login form with atypical field names - it falls back to capturing every non-empty
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
   * Flips the named phishing steps to RED (compromised) for the recipient, then re-derives the
   * team-level rows of those steps from their player rows.
   *
   * <p>The team row is NEVER written directly here: a team's verdict must follow the expectation's
   * validation rule ({@code expectationGroup}) exactly like every other human expectation. With the
   * default "all players must validate" rule the team turns RED as soon as one member falls for the
   * phishing; with "at least one player" it stays GREEN until every member does. Writing the team
   * row directly (as before) both ignored that rule and left the team stuck GREEN whenever the
   * recipient's team could not be resolved back on the tracking row - hence a compromised person
   * next to a green team. Deriving it from the same player rows the rest of the platform uses keeps
   * person and team consistent by construction. Idempotent per player step - an already-compromised
   * step keeps its earliest signal.
   */
  private void compromiseSteps(
      final PhishingResult result,
      final Set<String> stepNames,
      final String message,
      final String ip,
      final String userAgent) {
    User user = result.getUser();
    Inject inject = result.getInject();
    if (user == null || inject == null) {
      return;
    }
    // Forensic origin of THIS request (open / click / submit), stamped on the step it flips so the
    // results UI can show where and when it happened - the load-bearing signal for telling a mail
    // security scanner apart from a real recipient. Built from the triggering request's ip / agent
    // (not the row's latest), so a later click keeps the earlier open's distinct origin.
    Map<String, String> sourceMetadata = buildSourceMetadata(result, ip, userAgent);
    // findAllByInjectId returns managed entities: the player flips below mutate the very instances
    // the team recomputation then reads, so it sees the fresh player scores without re-querying.
    List<BaseInjectExpectation> injectExpectations =
        injectExpectationRepository.findAllByInjectId(inject.getId());
    List<BaseInjectExpectation> playerSteps =
        injectExpectations.stream()
            .filter(expectation -> isPlayerRowForUser(expectation, user.getId()))
            .filter(expectation -> matchesSteps(expectation, stepNames))
            .toList();
    playerSteps.forEach(expectation -> markCompromised(expectation, message, sourceMetadata));
    // Only the recipient's own team(s) can have changed: recomputing every team of the inject
    // would rewrite unrelated team rows (result text / updatedAt churn) on each click.
    Set<String> affectedTeamIds =
        playerSteps.stream()
            .map(expectation -> ((TableTopInjectExpectation) expectation).getTeam())
            .filter(Objects::nonNull)
            .map(team -> team.getId())
            .collect(Collectors.toSet());
    if (affectedTeamIds.isEmpty()) {
      return;
    }
    injectExpectations.stream()
        .filter(PhishingTrackingService::isTeamRow)
        .filter(expectation -> matchesSteps(expectation, stepNames))
        .filter(
            expectation ->
                expectation instanceof TableTopInjectExpectation teamRow
                    && teamRow.getTeam() != null
                    && affectedTeamIds.contains(teamRow.getTeam().getId()))
        .forEach(teamRow -> recomputeTeamStepFromPlayers(teamRow, injectExpectations));
  }

  private void markCompromised(
      final BaseInjectExpectation expectation,
      final String message,
      final Map<String, String> sourceMetadata) {
    if (expectation.getScore() != null && expectation.getScore() <= COMPROMISED_SCORE) {
      return;
    }
    InjectExpectationResult result = buildForPlayerManualValidation(message, COMPROMISED_SCORE);
    if (sourceMetadata != null && !sourceMetadata.isEmpty()) {
      result.setMetadata(sourceMetadata);
    }
    expectation.setResults(List.of(result));
    expectation.setScore(COMPROMISED_SCORE);
    expectation.setUpdatedAt(now());
    injectExpectationRepository.save(expectation);
  }

  /**
   * Builds the forensic metadata surfaced on a compromised phishing step: the triggering request's
   * IP and user-agent, a human-readable delay after delivery, and - when the evidence points that
   * way - a "likely automated" hint. Every value is optional (a legacy row may have no {@code
   * sentAt}, a tracking hit may carry no user-agent), so the map only holds what is actually known;
   * an empty map leaves the result unannotated.
   */
  private static Map<String, String> buildSourceMetadata(
      final PhishingResult result, final String ip, final String userAgent) {
    Map<String, String> metadata = new LinkedHashMap<>();
    if (ip != null && !ip.isBlank()) {
      metadata.put(SOURCE_IP_METADATA_KEY, truncate(ip.trim(), MAX_IP_METADATA_LENGTH));
    }
    if (userAgent != null && !userAgent.isBlank()) {
      metadata.put(
          SOURCE_USER_AGENT_METADATA_KEY,
          truncate(userAgent.trim(), MAX_USER_AGENT_METADATA_LENGTH));
    }
    Instant sentAt = result.getSentAt();
    Instant eventAt = now();
    Long delaySeconds = null;
    if (sentAt != null) {
      long seconds = Duration.between(sentAt, eventAt).getSeconds();
      if (seconds >= 0) {
        delaySeconds = seconds;
        metadata.put(SOURCE_DELAY_METADATA_KEY, humanizeDelay(seconds));
      }
    }
    AutomationHint automation = describeAutomation(userAgent, delaySeconds);
    if (automation != null) {
      metadata.put(SOURCE_AUTOMATION_METADATA_KEY, automation.text());
      metadata.put(SOURCE_AUTOMATION_LEVEL_METADATA_KEY, automation.level());
    }
    return metadata;
  }

  /**
   * An automation verdict on a tracking hit: a machine-readable severity ({@link
   * #AUTOMATION_LEVEL_LIKELY} / {@link #AUTOMATION_LEVEL_POSSIBLE}) the UI keys its chip label off,
   * plus the human-readable explanation shown in the chip's tooltip.
   */
  private record AutomationHint(String level, String text) {}

  /**
   * Returns an automation hint when the evidence points to a scanner/bot rather than a human
   * recipient, else {@code null}. On a SUPPRESSED probe's annotation it explains why the hit was
   * ignored; on a scored step it can only come from a dual-use image proxy (pure scanner agents and
   * too-fast hits never reach scoring), where it stays advisory: it never changes the score, a
   * match just helps the analyst weigh the row.
   */
  private static AutomationHint describeAutomation(
      final String userAgent, final Long delaySeconds) {
    if (userAgent != null && !userAgent.isBlank()) {
      String normalized = userAgent.toLowerCase(Locale.ROOT);
      if (SCANNER_USER_AGENT_SIGNATURES.stream().anyMatch(normalized::contains)) {
        return new AutomationHint(
            AUTOMATION_LEVEL_LIKELY,
            "Likely automated - the user agent matches a known email security scanner or link"
                + " preview bot, not a human recipient");
      }
      if (PROXY_USER_AGENT_SIGNATURES.stream().anyMatch(normalized::contains)) {
        return new AutomationHint(
            AUTOMATION_LEVEL_POSSIBLE,
            "Possibly automated - the user agent is a mail-client image proxy, which carries"
                + " both genuine recipient opens and provider-side pre-fetches");
      }
    }
    if (delaySeconds != null && delaySeconds <= AUTOMATION_MAX_DELAY_SECONDS) {
      return new AutomationHint(
          AUTOMATION_LEVEL_LIKELY,
          "Likely automated - triggered within seconds of delivery, too fast for a human"
              + " recipient (typical of a mail security scanner pre-fetching the link or image)");
    }
    return null;
  }

  private static String truncate(final String value, final int maxLength) {
    return value.length() <= maxLength ? value : value.substring(0, maxLength);
  }

  /** Formats an elapsed second count as a compact, human-readable "... after delivery" string. */
  private static String humanizeDelay(final long seconds) {
    if (seconds < 60) {
      return seconds + (seconds == 1 ? " second after delivery" : " seconds after delivery");
    }
    long minutes = seconds / 60;
    if (minutes < 60) {
      return minutes + (minutes == 1 ? " minute after delivery" : " minutes after delivery");
    }
    long hours = minutes / 60;
    if (hours < 24) {
      return hours + (hours == 1 ? " hour after delivery" : " hours after delivery");
    }
    long days = hours / 24;
    return days + (days == 1 ? " day after delivery" : " days after delivery");
  }

  /**
   * Recomputes one team-level phishing step from its player rows, honoring the expectation's
   * validation rule (all-vs-any) and the inverted phishing polarity, via the same {@link
   * InjectExpectationUtils#computeChildrenScore} aggregation used for every human expectation. A
   * team with no player rows, or whose players are all still pending, is left untouched (it keeps
   * its pre-scored GREEN "resisted" verdict).
   */
  private void recomputeTeamStepFromPlayers(
      final BaseInjectExpectation teamRow, final List<BaseInjectExpectation> injectExpectations) {
    if (!(teamRow instanceof TableTopInjectExpectation team) || team.getTeam() == null) {
      return;
    }
    String teamId = team.getTeam().getId();
    List<BaseInjectExpectation> players =
        injectExpectations.stream()
            .filter(
                expectation ->
                    expectation instanceof TableTopInjectExpectation player
                        && player.getUser() != null
                        && player.getTeam() != null
                        && teamId.equals(player.getTeam().getId())
                        && Objects.equals(player.getName(), team.getName()))
            .toList();
    if (players.isEmpty()) {
      return;
    }
    Double score =
        InjectExpectationUtils.computeChildrenScore(
            team.isExpectationGroup(), team.getExpectedScore(), players);
    if (score == null) {
      return;
    }
    boolean resisted = score >= team.getExpectedScore();
    boolean anyCompromised =
        players.stream().anyMatch(p -> p.getScore() != null && p.getScore() <= COMPROMISED_SCORE);
    String message =
        resisted
            ? (anyCompromised ? TEAM_RESISTED_MESSAGE : NO_INTERACTION_MESSAGE)
            : TEAM_COMPROMISED_MESSAGE;
    team.setResults(List.of(buildForTeamManualValidation(message, score)));
    team.setScore(score);
    team.setUpdatedAt(now());
    injectExpectationRepository.save(team);
  }

  private static boolean isPlayerRowForUser(
      final BaseInjectExpectation expectation, final String userId) {
    return expectation instanceof TableTopInjectExpectation tableTop
        && tableTop.getUser() != null
        && userId.equals(tableTop.getUser().getId());
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
