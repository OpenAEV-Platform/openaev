package io.openaev.service;

import io.openaev.aop.lock.Lock;
import io.openaev.aop.lock.LockResourceType;
import io.openaev.database.model.InjectExpectation;
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
      expectation.getSignatures().addAll(signatures);
    }
    expectation.setSignaturesInitialized(true);
    injectExpectationRepository.save(expectation);
  }
}
