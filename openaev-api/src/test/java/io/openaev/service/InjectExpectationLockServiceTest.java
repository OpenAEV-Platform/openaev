package io.openaev.service;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.openaev.database.model.DetectionInjectExpectation;
import io.openaev.database.repository.InjectExpectationRepository;
import io.openaev.rest.exception.ElementNotFoundException;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InjectExpectationLockServiceTest {

  @Mock private InjectExpectationRepository injectExpectationRepository;

  @InjectMocks private InjectExpectationLockService injectExpectationLockService;

  @Test
  @DisplayName("Should clear signatures before append when signatures are not initialized")
  void givenUninitializedExpectation_shouldClearThenAppend() {
    DetectionInjectExpectation expectation = new DetectionInjectExpectation();
    expectation.setId("expectation-id");
    expectation.setSignaturesInitialized(false);
    when(injectExpectationRepository.findById("expectation-id"))
        .thenReturn(Optional.of(expectation));

    injectExpectationLockService.applySignaturesForExpectationWithLock(
        "expectation-id", "[{\"type\":\"k\",\"value\":\"v\"}]");

    verify(injectExpectationRepository).clearSignaturesAndMarkInitialized("expectation-id");
    verify(injectExpectationRepository)
        .appendSignatures("expectation-id", "[{\"type\":\"k\",\"value\":\"v\"}]");
  }

  @Test
  @DisplayName("Should append signatures without clearing when signatures are initialized")
  void givenInitializedExpectation_shouldAppendWithoutClearing() {
    DetectionInjectExpectation expectation = new DetectionInjectExpectation();
    expectation.setId("expectation-id");
    expectation.setSignaturesInitialized(true);
    when(injectExpectationRepository.findById("expectation-id"))
        .thenReturn(Optional.of(expectation));

    injectExpectationLockService.applySignaturesForExpectationWithLock(
        "expectation-id", "[{\"type\":\"k\",\"value\":\"v\"}]");

    verify(injectExpectationRepository, never())
        .clearSignaturesAndMarkInitialized("expectation-id");
    verify(injectExpectationRepository)
        .appendSignatures("expectation-id", "[{\"type\":\"k\",\"value\":\"v\"}]");
  }

  @Test
  @DisplayName("Should throw when expectation does not exist")
  void givenUnknownExpectation_shouldThrowElementNotFoundException() {
    when(injectExpectationRepository.findById("expectation-id")).thenReturn(Optional.empty());

    assertThrows(
        ElementNotFoundException.class,
        () ->
            injectExpectationLockService.applySignaturesForExpectationWithLock(
                "expectation-id", "[{\"type\":\"k\",\"value\":\"v\"}]"));

    verify(injectExpectationRepository, never())
        .appendSignatures("expectation-id", "[{\"type\":\"k\",\"value\":\"v\"}]");
  }
}
