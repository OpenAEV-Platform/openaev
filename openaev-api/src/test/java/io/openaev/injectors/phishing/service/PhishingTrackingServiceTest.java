package io.openaev.injectors.phishing.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.openaev.database.model.BaseInjectExpectation;
import io.openaev.database.model.ContractOutputType;
import io.openaev.database.model.Finding;
import io.openaev.database.model.Inject;
import io.openaev.database.model.PhishingLandingPage;
import io.openaev.database.model.PhishingResult;
import io.openaev.database.model.User;
import io.openaev.database.repository.InjectExpectationRepository;
import io.openaev.database.repository.PhishingResultRepository;
import io.openaev.database.repository.TeamRepository;
import io.openaev.database.repository.UserRepository;
import io.openaev.rest.finding.FindingService;
import java.util.List;
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
      "markClicked should set clickedAt (implying open) and auto-fulfill the MANUAL expectation")
  void markClicked_should_fulfillManualExpectation() {
    // -- ARRANGE --
    PhishingResult result = resultWith(true, true);
    when(phishingResultRepository.findByToken("token-1")).thenReturn(Optional.of(result));
    when(phishingResultRepository.save(any(PhishingResult.class)))
        .thenAnswer(i -> i.getArgument(0));
    BaseInjectExpectation expectation = org.mockito.Mockito.mock(BaseInjectExpectation.class);
    when(expectation.getType()).thenReturn(BaseInjectExpectation.EXPECTATION_TYPE.MANUAL);
    when(expectation.getResults()).thenReturn(List.of());
    when(expectation.getExpectedScore()).thenReturn(100.0);
    when(injectExpectationRepository.findAllByInjectAndPlayer("inject-1", "user-1"))
        .thenReturn(List.of(expectation));

    // -- ACT --
    Optional<PhishingResult> updated =
        phishingTrackingService.markClicked("token-1", "1.2.3.4", "curl/8");

    // -- ASSERT --
    assertTrue(updated.isPresent());
    assertNotNull(updated.get().getClickedAt());
    assertNotNull(updated.get().getOpenedAt());
    verify(expectation).setScore(100.0);
    verify(injectExpectationRepository).save(expectation);
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
        "token-1", "victim@corp.test", "hunter2", "1.2.3.4", "ua");

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
        "token-1", "victim@corp.test", "hunter2", "1.2.3.4", "ua");

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
        "token-1", "victim@corp.test", "hunter2", "1.2.3.4", "ua");

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
    phishingTrackingService.markSubmitted(
        "token-1", "victim@corp.test", "hunter2", "1.2.3.4", "ua");
    phishingTrackingService.markSubmitted(
        "token-1", "victim@corp.test", "hunter2", "1.2.3.4", "ua");

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
}
