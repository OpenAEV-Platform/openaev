package io.openaev.service;

import static io.openaev.utils.ExpectationSignatureUtils.mergeExpectationSignatures;

import io.openaev.aop.lock.Lock;
import io.openaev.aop.lock.LockResourceType;
import io.openaev.database.model.BaseInjectExpectation;
import io.openaev.database.model.InjectExpectationSignature;
import io.openaev.database.repository.InjectExpectationRepository;
import io.openaev.rest.exception.ElementNotFoundException;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class InjectExpectationLockService {

  private final InjectExpectationRepository injectExpectationRepository;

  @Lock(type = LockResourceType.INJECT_EXPECTATION, key = "#expectationId")
  @Transactional
  public void applySignaturesForExpectationWithLock(
      @NotBlank String expectationId, @NotNull List<InjectExpectationSignature> signatures) {
    BaseInjectExpectation expectation =
        this.injectExpectationRepository
            .findById(expectationId)
            .orElseThrow(ElementNotFoundException::new);

    if (!expectation.isSignaturesInitialized()) {
      expectation.getSignatures().clear();
      expectation.getSignatures().addAll(signatures);
    } else {
      // mergeExpectationSignatures returns the de-duplicated union of the existing and the new
      // signatures, so it must REPLACE the collection - appending it onto the still-populated
      // collection would re-add every existing signature and enqueue duplicate composite ids
      // (NonUniqueObjectException on flush). orphanRemoval makes clear()+addAll the safe pattern.
      List<InjectExpectationSignature> mergedList =
          mergeExpectationSignatures(expectation.getSignatures(), signatures);
      expectation.getSignatures().clear();
      expectation.getSignatures().addAll(mergedList);
    }
    expectation.setSignaturesInitialized(true);
    injectExpectationRepository.save(expectation);
  }
}
