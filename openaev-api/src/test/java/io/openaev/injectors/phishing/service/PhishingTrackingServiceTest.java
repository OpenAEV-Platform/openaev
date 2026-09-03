package io.openaev.injectors.phishing.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.openaev.database.model.ContractOutputType;
import io.openaev.database.model.Finding;
import io.openaev.database.model.Inject;
import io.openaev.database.model.InjectExpectationResult;
import io.openaev.database.model.ManualInjectExpectation;
import io.openaev.database.model.PhishingLandingPage;
import io.openaev.database.model.PhishingResult;
import io.openaev.database.model.Team;
import io.openaev.database.model.User;
import io.openaev.database.repository.InjectExpectationRepository;
import io.openaev.database.repository.InjectRepository;
import io.openaev.database.repository.PhishingResultRepository;
import io.openaev.database.repository.StepRepository;
import io.openaev.database.repository.TeamRepository;
import io.openaev.database.repository.UserRepository;
import io.openaev.rest.finding.FindingService;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("Phishing tracking service tests")
class PhishingTrackingServiceTest {

  @Mock private PhishingResultRepository phishingResultRepository;
  @Mock private InjectExpectationRepository injectExpectationRepository;
  @Mock private InjectRepository injectRepository;
  @Mock private UserRepository userRepository;
  @Mock private TeamRepository teamRepository;
  @Mock private StepRepository stepRepository;
  @Mock private FindingService findingService;

  @InjectMocks private PhishingTrackingService phishingTrackingService;

  private PhishingResult resultWith(final boolean capture, final boolean capturePasswords) {
    User user = new User();
    user.setId("user-1");
    Inject inject = new Inject();
    inject.setId("inject-1");
    PhishingLandingPage landingPage = new PhishingLandingPage();
    landingPage.setName("Login page");
    landingPage.setCaptureSubmittedData(capture);
    landingPage.setCapturePasswords(capturePasswords);
    PhishingResult result = new PhishingResult();
    result.setToken("token-1");
    result.setUser(user);
    result.setInject(inject);
    result.setLandingPage(landingPage);
    return result;
  }

  /**
   * A player-scoped MANUAL step expectation for the tracked recipient ({@code user-1}), pre-scored
   * GREEN (resisted) like the executor does. Bound to {@code team-1} so team-level derivation can
   * find it.
   */
  private ManualInjectExpectation resistedStep(final String name) {
    return playerStep(name, "user-1", "team-1", 100.0, false);
  }

  /** A player-scoped MANUAL step row bound to a user + team, with an explicit score and rule. */
  private ManualInjectExpectation playerStep(
      final String name,
      final String userId,
      final String teamId,
      final double score,
      final boolean expectationGroup) {
    ManualInjectExpectation expectation = teamStep(name, teamId, score, expectationGroup);
    // A distinct id keeps equals()/hashCode() well-defined when several rows are matched in the
    // same verify() (an id-less expectation dereferences a null id during Mockito's comparison).
    expectation.setId(name + ":" + userId);
    User user = new User();
    user.setId(userId);
    expectation.setUser(user);
    return expectation;
  }

  /** A team-level MANUAL step row (no user) bound to a team, with an explicit score and rule. */
  private ManualInjectExpectation teamStep(
      final String name, final String teamId, final double score, final boolean expectationGroup) {
    ManualInjectExpectation expectation = new ManualInjectExpectation();
    expectation.setId(name + ":team:" + teamId);
    expectation.setName(name);
    expectation.setExpectedScore(100.0);
    expectation.setScore(score);
    expectation.setExpectationGroup(expectationGroup);
    Team team = new Team();
    team.setId(teamId);
    expectation.setTeam(team);
    return expectation;
  }

  @Test
  @DisplayName("generateToken should produce unguessable, URL-safe, unique tokens")
  void generateToken_should_produceUrlSafeUniqueTokens() {
    String a = PhishingTrackingService.generateToken();
    String b = PhishingTrackingService.generateToken();
    assertNotNull(a);
    assertNotEquals(a, b);
    assertTrue(a.matches("[A-Za-z0-9_-]+"), "token must be URL-safe base64 without padding");
  }

  @Test
  @DisplayName(
      "initializeExpectationsAsResisted should pre-score every phishing step to its expected score")
  void initializeExpectationsAsResisted_should_preScoreStepsGreen() {
    // -- ARRANGE --
    ManualInjectExpectation opened = new ManualInjectExpectation();
    opened.setName(PhishingTrackingService.STEP_OPENED);
    opened.setUser(new User());
    opened.setExpectedScore(100.0);
    when(injectExpectationRepository.findAllByInjectId("inject-1")).thenReturn(List.of(opened));

    // -- ACT --
    phishingTrackingService.initializeExpectationsAsResisted("inject-1");

    // -- ASSERT --
    assertEquals(100.0, opened.getScore(), "a never-interacted step must stay GREEN (resisted)");
    assertNotNull(opened.getResults());
    verify(injectExpectationRepository).save(opened);
  }

  @Test
  @DisplayName("markClicked should flip the opened and clicked steps to compromised (RED)")
  void markClicked_should_compromiseOpenedAndClickedSteps() {
    // -- ARRANGE --
    PhishingResult result = resultWith(true, true);
    when(phishingResultRepository.findByToken("token-1")).thenReturn(Optional.of(result));
    when(phishingResultRepository.save(any(PhishingResult.class)))
        .thenAnswer(i -> i.getArgument(0));
    ManualInjectExpectation opened = resistedStep(PhishingTrackingService.STEP_OPENED);
    ManualInjectExpectation clicked = resistedStep(PhishingTrackingService.STEP_CLICKED);
    when(injectExpectationRepository.findAllByInjectId("inject-1"))
        .thenReturn(List.of(opened, clicked));

    // -- ACT --
    Optional<PhishingResult> updated =
        phishingTrackingService.markClicked("token-1", "1.2.3.4", "curl/8");

    // -- ASSERT --
    assertTrue(updated.isPresent());
    assertNotNull(updated.get().getClickedAt());
    assertNotNull(updated.get().getOpenedAt());
    assertEquals(0.0, clicked.getScore(), "a followed link must flip the clicked step to RED");
    assertEquals(
        0.0, opened.getScore(), "a followed link implies an open, so the opened step flips too");
    verify(injectExpectationRepository).save(clicked);
    verify(injectExpectationRepository).save(opened);
  }

  @Test
  @DisplayName("markClicked should stamp the triggering request's IP and user agent on the step")
  void markClicked_should_stampSourceIpAndUserAgentMetadata() {
    // -- ARRANGE --
    PhishingResult result = resultWith(true, true);
    when(phishingResultRepository.findByToken("token-1")).thenReturn(Optional.of(result));
    when(phishingResultRepository.save(any(PhishingResult.class)))
        .thenAnswer(i -> i.getArgument(0));
    ManualInjectExpectation clicked = resistedStep(PhishingTrackingService.STEP_CLICKED);
    when(injectExpectationRepository.findAllByInjectId("inject-1")).thenReturn(List.of(clicked));

    // -- ACT -- a human-looking browser agent, no sentAt on the row (so no delay/automation).
    phishingTrackingService.markClicked(
        "token-1", "203.0.113.7", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/120");

    // -- ASSERT -- the compromised step carries the forensic origin of THIS request.
    Map<String, String> metadata = clicked.getResults().get(0).getMetadata();
    assertNotNull(metadata, "a compromised step must carry forensic metadata");
    assertEquals("203.0.113.7", metadata.get(PhishingTrackingService.SOURCE_IP_METADATA_KEY));
    assertEquals(
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/120",
        metadata.get(PhishingTrackingService.SOURCE_USER_AGENT_METADATA_KEY));
    assertNull(
        metadata.get(PhishingTrackingService.SOURCE_AUTOMATION_METADATA_KEY),
        "a human browser agent with no timing signal must not be flagged automated");
  }

  @Test
  @DisplayName(
      "markOpened should NOT score a known email-scanner user agent, only annotate the green step")
  void markOpened_should_suppressScannerUserAgent() {
    // -- ARRANGE --
    PhishingResult result = resultWith(false, false);
    when(phishingResultRepository.findByToken("token-1")).thenReturn(Optional.of(result));
    ManualInjectExpectation opened = resistedStep(PhishingTrackingService.STEP_OPENED);
    when(injectExpectationRepository.findAllByInjectId("inject-1")).thenReturn(List.of(opened));

    // -- ACT -- Microsoft Safe Links pre-detonation user agent (BingPreview family).
    Optional<PhishingResult> updated =
        phishingTrackingService.markOpened(
            "token-1", "40.94.2.10", "Mozilla/5.0 (compatible; BingPreview/1.0b)");

    // -- ASSERT -- the step stays GREEN and untimestamped; the probe is only an annotation.
    assertTrue(updated.isPresent());
    assertNull(updated.get().getOpenedAt(), "a scanner probe must not win the first-open slot");
    assertEquals(100.0, opened.getScore(), "a scanner probe must never flip the step to RED");
    Map<String, String> metadata = opened.getResults().get(0).getMetadata();
    assertNotNull(metadata, "the ignored probe must leave a forensic annotation");
    assertEquals("40.94.2.10", metadata.get(PhishingTrackingService.SOURCE_IP_METADATA_KEY));
    assertNotNull(
        metadata.get(PhishingTrackingService.SOURCE_AUTOMATION_METADATA_KEY),
        "the annotation must say why the hit was treated as automated");
    assertEquals(
        PhishingTrackingService.AUTOMATION_LEVEL_LIKELY,
        metadata.get(PhishingTrackingService.SOURCE_AUTOMATION_LEVEL_METADATA_KEY),
        "a pure scanner signature must carry the machine-readable 'likely' level");
    verifyNoInteractions(findingService);
  }

  @Test
  @DisplayName("markOpened should still score a genuine open after a suppressed scanner probe")
  void markOpened_should_scoreGenuineOpenAfterSuppressedProbe() {
    // -- ARRANGE --
    PhishingResult result = resultWith(false, false);
    when(phishingResultRepository.findByToken("token-1")).thenReturn(Optional.of(result));
    when(phishingResultRepository.save(any(PhishingResult.class)))
        .thenAnswer(i -> i.getArgument(0));
    ManualInjectExpectation opened = resistedStep(PhishingTrackingService.STEP_OPENED);
    when(injectExpectationRepository.findAllByInjectId("inject-1")).thenReturn(List.of(opened));

    // -- ACT -- the scanner probes first (suppressed annotation), the recipient opens later.
    phishingTrackingService.markOpened(
        "token-1", "40.94.2.10", "Mozilla/5.0 (compatible; BingPreview/1.0b)");
    Optional<PhishingResult> updated =
        phishingTrackingService.markOpened(
            "token-1", "203.0.113.7", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/120");

    // -- ASSERT -- the annotation never poisons the step: the genuine open still flips it RED
    // with the recipient's own forensic origin, not the scanner's.
    assertTrue(updated.isPresent());
    assertNotNull(updated.get().getOpenedAt(), "the genuine open must win the first-open slot");
    assertEquals(0.0, opened.getScore(), "the genuine open must still flip the step to RED");
    Map<String, String> metadata = opened.getResults().get(0).getMetadata();
    assertNotNull(metadata);
    assertEquals(
        "203.0.113.7",
        metadata.get(PhishingTrackingService.SOURCE_IP_METADATA_KEY),
        "the flip must carry the recipient's origin, not the earlier probe's");
  }

  @Test
  @DisplayName("a scanner probe must not rewrite the forensics of a step already flipped RED")
  void markOpened_should_leaveCompromisedStepUntouchedOnLaterProbe() {
    // -- ARRANGE -- the recipient already fell for the step; its result carries their origin.
    PhishingResult result = resultWith(false, false);
    when(phishingResultRepository.findByToken("token-1")).thenReturn(Optional.of(result));
    when(phishingResultRepository.save(any(PhishingResult.class)))
        .thenAnswer(i -> i.getArgument(0));
    ManualInjectExpectation opened = resistedStep(PhishingTrackingService.STEP_OPENED);
    when(injectExpectationRepository.findAllByInjectId("inject-1")).thenReturn(List.of(opened));
    phishingTrackingService.markOpened(
        "token-1", "203.0.113.7", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/120");
    List<InjectExpectationResult> flippedResults = opened.getResults();

    // -- ACT -- a scanner probes the lure afterwards.
    phishingTrackingService.markOpened(
        "token-1", "40.94.2.10", "Mozilla/5.0 (compatible; BingPreview/1.0b)");

    // -- ASSERT -- the RED step keeps the recipient's forensic trail, byte for byte.
    assertEquals(0.0, opened.getScore(), "the step must stay RED");
    assertEquals(
        flippedResults,
        opened.getResults(),
        "a later probe must not rewrite a compromised step's results");
    assertEquals(
        "203.0.113.7",
        opened
            .getResults()
            .get(0)
            .getMetadata()
            .get(PhishingTrackingService.SOURCE_IP_METADATA_KEY),
        "the recipient's own origin must survive the later probe");
  }

  @Test
  @DisplayName("buildSourceMetadata should cap attacker-controlled header values")
  void markClicked_should_truncateOversizedUserAgentMetadata() {
    // -- ARRANGE --
    PhishingResult result = resultWith(false, false);
    when(phishingResultRepository.findByToken("token-1")).thenReturn(Optional.of(result));
    when(phishingResultRepository.save(any(PhishingResult.class)))
        .thenAnswer(i -> i.getArgument(0));
    ManualInjectExpectation clicked = resistedStep(PhishingTrackingService.STEP_CLICKED);
    when(injectExpectationRepository.findAllByInjectId("inject-1")).thenReturn(List.of(clicked));

    // -- ACT -- a hostile client sends a multi-kilobyte User-Agent header.
    phishingTrackingService.markClicked("token-1", "1.2.3.4", "A".repeat(5000));

    // -- ASSERT -- the stored forensic value is capped, not the raw header.
    String storedAgent =
        clicked
            .getResults()
            .get(0)
            .getMetadata()
            .get(PhishingTrackingService.SOURCE_USER_AGENT_METADATA_KEY);
    assertNotNull(storedAgent);
    assertEquals(512, storedAgent.length(), "the stored user agent must be capped at 512 chars");
  }

  @Test
  @DisplayName("markOpened should NOT score a hit landing within seconds of delivery")
  void markOpened_should_suppressFastHit() {
    // -- ARRANGE -- the lure was 'delivered' now, so any hit is within the automation window.
    PhishingResult result = resultWith(false, false);
    result.setSentAt(java.time.Instant.now());
    when(phishingResultRepository.findByToken("token-1")).thenReturn(Optional.of(result));
    ManualInjectExpectation opened = resistedStep(PhishingTrackingService.STEP_OPENED);
    when(injectExpectationRepository.findAllByInjectId("inject-1")).thenReturn(List.of(opened));

    // -- ACT -- a human-looking agent, but impossibly fast after delivery (Safe Links
    // detonation pattern: the gateway fetches with a browser-like agent seconds after send).
    phishingTrackingService.markOpened(
        "token-1", "203.0.113.9", "Mozilla/5.0 (Macintosh; Intel Mac OS X) Safari/17");

    // -- ASSERT -- timing alone suppresses; the annotation carries the delay and the reason.
    assertEquals(100.0, opened.getScore(), "a too-fast hit must never flip the step to RED");
    Map<String, String> metadata = opened.getResults().get(0).getMetadata();
    assertNotNull(metadata);
    assertNotNull(
        metadata.get(PhishingTrackingService.SOURCE_DELAY_METADATA_KEY),
        "the annotation must carry the human-readable delay after delivery");
    assertNotNull(
        metadata.get(PhishingTrackingService.SOURCE_AUTOMATION_METADATA_KEY),
        "the annotation must say why the hit was treated as automated");
  }

  @Test
  @DisplayName(
      "markOpened should still score a dual-use image proxy agent, with an advisory hint only")
  void markOpened_should_scoreImageProxyWithAdvisoryHint() {
    // -- ARRANGE -- no sentAt on the row: the delay signal is unavailable, like a legacy row.
    PhishingResult result = resultWith(false, false);
    when(phishingResultRepository.findByToken("token-1")).thenReturn(Optional.of(result));
    when(phishingResultRepository.save(any(PhishingResult.class)))
        .thenAnswer(i -> i.getArgument(0));
    ManualInjectExpectation opened = resistedStep(PhishingTrackingService.STEP_OPENED);
    when(injectExpectationRepository.findAllByInjectId("inject-1")).thenReturn(List.of(opened));

    // -- ACT -- Gmail image proxy: genuine recipient opens ride through it too, so it must
    // score (suppressing it would hide every real Gmail open) but carry the advisory hint.
    phishingTrackingService.markOpened(
        "token-1", "66.102.8.1", "Mozilla/5.0 (Windows NT 5.1; de) via ggpht.com GoogleImageProxy");

    // -- ASSERT --
    assertEquals(0.0, opened.getScore(), "a dual-use proxy open must still score the step");
    Map<String, String> metadata = opened.getResults().get(0).getMetadata();
    assertNotNull(metadata);
    assertNotNull(
        metadata.get(PhishingTrackingService.SOURCE_AUTOMATION_METADATA_KEY),
        "a dual-use proxy open must carry the advisory automation hint");
    assertEquals(
        PhishingTrackingService.AUTOMATION_LEVEL_POSSIBLE,
        metadata.get(PhishingTrackingService.SOURCE_AUTOMATION_LEVEL_METADATA_KEY),
        "a dual-use proxy must carry the machine-readable 'possible' level, not 'likely'");
  }

  @Test
  @DisplayName("markSubmitted should NOT capture credentials submitted by a sandbox detonator")
  void markSubmitted_should_suppressScannerSubmit() {
    // -- ARRANGE --
    PhishingResult result = resultWith(true, true);
    when(phishingResultRepository.findByToken("token-1")).thenReturn(Optional.of(result));
    ManualInjectExpectation submitted = resistedStep(PhishingTrackingService.STEP_SUBMITTED);
    when(injectExpectationRepository.findAllByInjectId("inject-1")).thenReturn(List.of(submitted));

    // -- ACT -- a URL-detonation sandbox auto-filling the landing form with synthetic data.
    Optional<PhishingResult> updated =
        phishingTrackingService.markSubmitted(
            "token-1",
            Map.of("username", "synthetic@detonation.test", "password", "fake"),
            "40.94.2.11",
            "Mozilla/5.0 (compatible; MSIE SafeLinks detonation)");

    // -- ASSERT -- no verdict, no timestamps, no fake Credentials finding.
    assertTrue(updated.isPresent());
    assertNull(updated.get().getSubmittedAt(), "a detonator submit must not win first-submit");
    assertEquals(100.0, submitted.getScore(), "a detonator submit must never flip the step");
    verifyNoInteractions(findingService);
  }

  @Test
  @DisplayName("markClicked should leave unrelated (non-step) manual expectations untouched")
  void markClicked_should_ignoreNonStepExpectations() {
    // -- ARRANGE --
    PhishingResult result = resultWith(true, true);
    when(phishingResultRepository.findByToken("token-1")).thenReturn(Optional.of(result));
    when(phishingResultRepository.save(any(PhishingResult.class)))
        .thenAnswer(i -> i.getArgument(0));
    ManualInjectExpectation unrelated = resistedStep("Some operator check");
    when(injectExpectationRepository.findAllByInjectId("inject-1")).thenReturn(List.of(unrelated));

    // -- ACT --
    phishingTrackingService.markClicked("token-1", "1.2.3.4", "curl/8");

    // -- ASSERT --
    assertEquals(100.0, unrelated.getScore(), "a non-step expectation must not be flipped");
    verify(injectExpectationRepository, never()).save(unrelated);
  }

  @Test
  @DisplayName(
      "markClicked should turn the team RED when the rule is 'all must validate' and one member"
          + " falls")
  void markClicked_should_failTeamUnderAllValidateRule() {
    // -- ARRANGE -- one team, one player (the recipient); "all must validate" (expectationGroup
    // false), both rows start GREEN (resisted).
    PhishingResult result = resultWith(false, false);
    when(phishingResultRepository.findByToken("token-1")).thenReturn(Optional.of(result));
    when(phishingResultRepository.save(any(PhishingResult.class)))
        .thenAnswer(i -> i.getArgument(0));
    ManualInjectExpectation playerClicked =
        playerStep(PhishingTrackingService.STEP_CLICKED, "user-1", "team-1", 100.0, false);
    ManualInjectExpectation teamClicked =
        teamStep(PhishingTrackingService.STEP_CLICKED, "team-1", 100.0, false);
    when(injectExpectationRepository.findAllByInjectId("inject-1"))
        .thenReturn(List.of(playerClicked, teamClicked));

    // -- ACT --
    phishingTrackingService.markClicked("token-1", "1.2.3.4", "curl/8");

    // -- ASSERT -- person RED and team RED (a compromised member fails the "all must validate"
    // team), never a red person next to a green team.
    assertEquals(0.0, playerClicked.getScore(), "the recipient who clicked must be RED");
    assertEquals(
        0.0,
        teamClicked.getScore(),
        "under 'all must validate' one fallen member turns the whole team RED");
    verify(injectExpectationRepository).save(teamClicked);
  }

  @Test
  @DisplayName(
      "markClicked should keep the team GREEN under 'at least one' rule while another member"
          + " resisted")
  void markClicked_should_keepTeamGreenUnderAnyValidateRule() {
    // -- ARRANGE -- one team, two players; "at least one" rule (expectationGroup true). Only the
    // recipient (user-1) clicks; user-2 keeps resisting.
    PhishingResult result = resultWith(false, false);
    when(phishingResultRepository.findByToken("token-1")).thenReturn(Optional.of(result));
    when(phishingResultRepository.save(any(PhishingResult.class)))
        .thenAnswer(i -> i.getArgument(0));
    ManualInjectExpectation recipient =
        playerStep(PhishingTrackingService.STEP_CLICKED, "user-1", "team-1", 100.0, true);
    ManualInjectExpectation teammate =
        playerStep(PhishingTrackingService.STEP_CLICKED, "user-2", "team-1", 100.0, true);
    ManualInjectExpectation teamClicked =
        teamStep(PhishingTrackingService.STEP_CLICKED, "team-1", 100.0, true);
    when(injectExpectationRepository.findAllByInjectId("inject-1"))
        .thenReturn(List.of(recipient, teammate, teamClicked));

    // -- ACT --
    phishingTrackingService.markClicked("token-1", "1.2.3.4", "curl/8");

    // -- ASSERT -- recipient RED, teammate untouched, team still GREEN (at least one resisted).
    assertEquals(0.0, recipient.getScore(), "the recipient who clicked must be RED");
    assertEquals(100.0, teammate.getScore(), "a member who did not click is untouched");
    assertEquals(
        100.0,
        teamClicked.getScore(),
        "under 'at least one' the team stays GREEN while any member resisted");
  }

  @Test
  @DisplayName("markClicked should not rewrite the team rows of teams the recipient is not in")
  void markClicked_should_leaveUnrelatedTeamRowsUntouched() {
    // -- ARRANGE -- the recipient (user-1) is in team-1; team-2 has its own member and team row.
    PhishingResult result = resultWith(false, false);
    when(phishingResultRepository.findByToken("token-1")).thenReturn(Optional.of(result));
    when(phishingResultRepository.save(any(PhishingResult.class)))
        .thenAnswer(i -> i.getArgument(0));
    ManualInjectExpectation recipient =
        playerStep(PhishingTrackingService.STEP_CLICKED, "user-1", "team-1", 100.0, false);
    ManualInjectExpectation teamClicked =
        teamStep(PhishingTrackingService.STEP_CLICKED, "team-1", 100.0, false);
    ManualInjectExpectation otherMember =
        playerStep(PhishingTrackingService.STEP_CLICKED, "user-2", "team-2", 100.0, false);
    ManualInjectExpectation otherTeamClicked =
        teamStep(PhishingTrackingService.STEP_CLICKED, "team-2", 100.0, false);
    when(injectExpectationRepository.findAllByInjectId("inject-1"))
        .thenReturn(List.of(recipient, teamClicked, otherMember, otherTeamClicked));

    // -- ACT --
    phishingTrackingService.markClicked("token-1", "1.2.3.4", "curl/8");

    // -- ASSERT -- only the recipient's team is recomputed; team-2's rows see no write at all.
    assertEquals(0.0, teamClicked.getScore(), "the recipient's team must be recomputed");
    assertEquals(100.0, otherMember.getScore(), "another team's member is untouched");
    assertEquals(100.0, otherTeamClicked.getScore(), "another team's row keeps its score");
    verify(injectExpectationRepository, never()).save(otherMember);
    verify(injectExpectationRepository, never()).save(otherTeamClicked);
  }

  @Test
  @DisplayName(
      "markSubmitted should capture credentials as a Credentials finding when capture is on")
  void markSubmitted_should_createCredentialsFinding() {
    // -- ARRANGE --
    PhishingResult result = resultWith(true, true);
    when(phishingResultRepository.findByToken("token-1")).thenReturn(Optional.of(result));
    when(phishingResultRepository.save(any(PhishingResult.class)))
        .thenAnswer(i -> i.getArgument(0));
    when(injectExpectationRepository.findAllByInjectId(anyString())).thenReturn(List.of());

    // -- ACT --
    phishingTrackingService.markSubmitted(
        "token-1", Map.of("username", "victim@corp.test", "password", "hunter2"), "1.2.3.4", "ua");

    // -- ASSERT --
    ArgumentCaptor<List<Finding>> captor = ArgumentCaptor.forClass(List.class);
    verify(findingService).createFindings(captor.capture(), eq("inject-1"));
    Finding finding = captor.getValue().get(0);
    assertEquals(ContractOutputType.Credentials, finding.getType());
    assertTrue(finding.getValue().contains("victim@corp.test"));
    assertTrue(finding.getValue().contains("hunter2"), "password should be captured when enabled");
  }

  @Test
  @DisplayName(
      "markSubmitted should not create a finding when the landing page does not capture data")
  void markSubmitted_should_notCaptureWhenDisabled() {
    // -- ARRANGE --
    PhishingResult result = resultWith(false, false);
    when(phishingResultRepository.findByToken("token-1")).thenReturn(Optional.of(result));
    when(phishingResultRepository.save(any(PhishingResult.class)))
        .thenAnswer(i -> i.getArgument(0));
    when(injectExpectationRepository.findAllByInjectId(anyString())).thenReturn(List.of());

    // -- ACT --
    phishingTrackingService.markSubmitted(
        "token-1", Map.of("username", "victim@corp.test", "password", "hunter2"), "1.2.3.4", "ua");

    // -- ASSERT --
    verify(findingService, never()).createFindings(anyList(), anyString());
  }

  @Test
  @DisplayName("markSubmitted should omit the password when capturePasswords is off")
  void markSubmitted_should_omitPasswordWhenDisabled() {
    // -- ARRANGE --
    PhishingResult result = resultWith(true, false);
    when(phishingResultRepository.findByToken("token-1")).thenReturn(Optional.of(result));
    when(phishingResultRepository.save(any(PhishingResult.class)))
        .thenAnswer(i -> i.getArgument(0));
    when(injectExpectationRepository.findAllByInjectId(anyString())).thenReturn(List.of());

    // -- ACT --
    phishingTrackingService.markSubmitted(
        "token-1", Map.of("username", "victim@corp.test", "password", "hunter2"), "1.2.3.4", "ua");

    // -- ASSERT --
    ArgumentCaptor<List<Finding>> captor = ArgumentCaptor.forClass(List.class);
    verify(findingService).createFindings(captor.capture(), eq("inject-1"));
    Finding finding = captor.getValue().get(0);
    assertEquals("victim@corp.test", finding.getValue());
  }

  @Test
  @DisplayName("markSubmitted should capture credentials only once across repeated submits")
  void markSubmitted_should_beIdempotentOnRepeatedSubmit() {
    // -- ARRANGE --
    PhishingResult result = resultWith(true, true);
    when(phishingResultRepository.findByToken("token-1")).thenReturn(Optional.of(result));
    when(phishingResultRepository.save(any(PhishingResult.class)))
        .thenAnswer(i -> i.getArgument(0));
    when(injectExpectationRepository.findAllByInjectId(anyString())).thenReturn(List.of());

    // -- ACT --
    Map<String, String> fields = Map.of("username", "victim@corp.test", "password", "hunter2");
    phishingTrackingService.markSubmitted("token-1", fields, "1.2.3.4", "ua");
    phishingTrackingService.markSubmitted("token-1", fields, "1.2.3.4", "ua");

    // -- ASSERT --
    verify(findingService, org.mockito.Mockito.times(1)).createFindings(anyList(), anyString());
  }

  @Test
  @DisplayName("markOpened should be a no-op for an unknown token")
  void markOpened_should_returnEmptyForUnknownToken() {
    when(phishingResultRepository.findByToken("nope")).thenReturn(Optional.empty());
    Optional<PhishingResult> updated = phishingTrackingService.markOpened("nope", "1.2.3.4", "ua");
    assertTrue(updated.isEmpty());
    verifyNoInteractions(findingService);
  }

  @Test
  @DisplayName(
      "buildCredentialValue should recognize an atypical login field name (cloned real form)")
  void buildCredentialValue_should_recognizeAtypicalLoginField() {
    String value =
        PhishingTrackingService.buildCredentialValue(
            Map.of("loginfmt", "victim@corp.test", "passwd", "hunter2"), true);
    assertEquals("victim@corp.test / hunter2", value);
  }

  @Test
  @DisplayName("buildCredentialValue should fall back to every submitted field when none is known")
  void buildCredentialValue_should_fallBackToAllFields() {
    String value =
        PhishingTrackingService.buildCredentialValue(Map.of("employee_ref", "A-42"), true);
    assertEquals("employee_ref=A-42", value);
  }

  @Test
  @DisplayName("buildCredentialValue should return null when there is nothing to capture")
  void buildCredentialValue_should_returnNullWhenEmpty() {
    assertNull(PhishingTrackingService.buildCredentialValue(Map.of(), true));
  }
}
