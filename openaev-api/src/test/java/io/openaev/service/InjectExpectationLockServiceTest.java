package io.openaev.service;

import static java.time.Instant.now;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

import io.openaev.database.model.DetectionInjectExpectation;
import io.openaev.database.model.InjectExpectationSignature;
import io.openaev.database.repository.InjectExpectationRepository;
import io.openaev.rest.exception.ElementNotFoundException;
import java.util.ArrayList;
import java.util.List;
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
    expectation.setSignatures(
        new ArrayList<>(
            List.of(new InjectExpectationSignature(expectation, "old-k", "old-v", now()))));
    when(injectExpectationRepository.findById("expectation-id"))
        .thenReturn(Optional.of(expectation));

    InjectExpectationSignature signature =
        new InjectExpectationSignature(expectation, "k", "v", now());
    List<InjectExpectationSignature> signatures = new ArrayList<>();
    signatures.add(signature);
    injectExpectationLockService.applySignaturesForExpectationWithLock(
        "expectation-id", signatures);

    expectation.setSignatures(signatures);
    verify(injectExpectationRepository).save(expectation);
  }

  @Test
  @DisplayName("Should append signatures without clearing when signatures are initialized")
  void givenInitializedExpectation_shouldAppendWithoutClearing() {
    DetectionInjectExpectation expectation = new DetectionInjectExpectation();
    expectation.setId("expectation-id");
    expectation.setSignaturesInitialized(true);
    expectation.setSignatures(
        new ArrayList<>(
            List.of(new InjectExpectationSignature(expectation, "old-k", "old-v", now()))));
    when(injectExpectationRepository.findById("expectation-id"))
        .thenReturn(Optional.of(expectation));

    InjectExpectationSignature signature =
        new InjectExpectationSignature(expectation, "k", "v", now());
    List<InjectExpectationSignature> signatures = new ArrayList<>();
    signatures.add(signature);
    injectExpectationLockService.applySignaturesForExpectationWithLock(
        "expectation-id", signatures);

    expectation.getSignatures().addAll(signatures);
    verify(injectExpectationRepository).save(expectation);
  }

  @Test
  @DisplayName("Should throw when expectation does not exist")
  void givenUnknownExpectation_shouldThrowElementNotFoundException() {
    when(injectExpectationRepository.findById("expectation-id")).thenReturn(Optional.empty());

    DetectionInjectExpectation expectation = new DetectionInjectExpectation();
    expectation.setId("expectation-id");
    expectation.setSignaturesInitialized(true);

    InjectExpectationSignature signature =
        new InjectExpectationSignature(expectation, "k", "v", now());
    List<InjectExpectationSignature> signatures = new ArrayList<>();
    signatures.add(signature);

    assertThrows(
        ElementNotFoundException.class,
        () ->
            injectExpectationLockService.applySignaturesForExpectationWithLock(
                "expectation-id", signatures));

    verify(injectExpectationRepository, never()).save(any());
  }
}
