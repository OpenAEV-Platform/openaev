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
import io.openaev.database.model.ManualInjectExpectation;
import io.openaev.database.model.PhishingLandingPage;
import io.openaev.database.model.PhishingResult;
import io.openaev.database.model.User;
import io.openaev.database.repository.InjectExpectationRepository;
import io.openaev.database.repository.PhishingResultRepository;
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
  @Mock private UserRepository userRepository;
  @Mock private TeamRepository teamRepository;
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
   * A player-scoped MANUAL step expectation, pre-scored GREEN (resisted) like the executor does.
   */
  private ManualInjectExpectation resistedStep(final String name) {
    ManualInjectExpectation expectation = new ManualInjectExpectation();
    // A distinct id keeps equals()/hashCode() well-defined when several rows are matched in the
    // same verify() (an id-less expectation dereferences a null id during Mockito's comparison).
    expectation.setId(name);
    expectation.setName(name);
    expectation.setExpectedScore(100.0);
    expectation.setScore(100.0);
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
    when(injectExpectationRepository.findAllByInjectAndPlayer("inject-1", "user-1"))
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
  @DisplayName("markClicked should leave unrelated (non-step) manual expectations untouched")
  void markClicked_should_ignoreNonStepExpectations() {
    // -- ARRANGE --
    PhishingResult result = resultWith(true, true);
    when(phishingResultRepository.findByToken("token-1")).thenReturn(Optional.of(result));
    when(phishingResultRepository.save(any(PhishingResult.class)))
        .thenAnswer(i -> i.getArgument(0));
    ManualInjectExpectation unrelated = resistedStep("Some operator check");
    when(injectExpectationRepository.findAllByInjectAndPlayer("inject-1", "user-1"))
        .thenReturn(List.of(unrelated));

    // -- ACT --
    phishingTrackingService.markClicked("token-1", "1.2.3.4", "curl/8");

    // -- ASSERT --
    assertEquals(100.0, unrelated.getScore(), "a non-step expectation must not be flipped");
    verify(injectExpectationRepository, never()).save(unrelated);
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
    when(injectExpectationRepository.findAllByInjectAndPlayer(anyString(), anyString()))
        .thenReturn(List.of());

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
    when(injectExpectationRepository.findAllByInjectAndPlayer(anyString(), anyString()))
        .thenReturn(List.of());

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
    when(injectExpectationRepository.findAllByInjectAndPlayer(anyString(), anyString()))
        .thenReturn(List.of());

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
    when(injectExpectationRepository.findAllByInjectAndPlayer(anyString(), anyString()))
        .thenReturn(List.of());

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
