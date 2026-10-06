package io.openaev.service.stix;

import io.openaev.database.model.Inject;
import io.openaev.database.model.IocValidation;
import io.openaev.database.model.IocValidationIoc;
import io.openaev.database.model.Payload;
import io.openaev.database.repository.IocValidationRepository;
import io.openaev.rest.payload.service.PayloadService;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Refuses to dispatch a test of an IOC validation whose inject no longer runs what the operator
 * approved. The injects of a validation simulation stay editable after the approval, while the
 * checks of the planner (public targets, hosts of the platform, egress proxy) hold only for the
 * approved values: each test inject must still run an IOC validation payload, with the arguments
 * whose fingerprint the approval recorded.
 */
@Component
@RequiredArgsConstructor
public class IocValidationDispatchGuard {

  public static final String IOC_VALIDATION_UNAPPROVED_TEST =
      "This IOC validation test was changed after its approval: it no longer runs the payload or the"
          + " arguments the operator approved, so it is not executed. Reject the request and ask for"
          + " a new validation from OpenCTI.";

  private final IocValidationRepository iocValidationRepository;

  /**
   * @param inject the inject about to be dispatched
   * @param payload the payload of its injector contract
   * @throws IllegalStateException when the inject is a test of an IOC validation and differs from
   *     its approved plan
   */
  public void refuseUnapprovedTest(Inject inject, Payload payload) {
    if (inject.getExercise() == null || inject.getTenant() == null) {
      return;
    }
    for (IocValidation validation :
        iocValidationRepository.findBySimulationIdAndTenantId(
            inject.getExercise().getId(), inject.getTenant().getId())) {
      for (IocValidationIoc ioc : validation.getIocs()) {
        if (ioc.getInjectIds() != null && ioc.getInjectIds().contains(inject.getId())) {
          if (!isApproved(ioc, inject, payload)) {
            throw new IllegalStateException(IOC_VALIDATION_UNAPPROVED_TEST);
          }
          return;
        }
      }
    }
  }

  private static boolean isApproved(IocValidationIoc ioc, Inject inject, Payload payload) {
    // Another IOC validation payload would read other arguments of the content
    if (ioc.getTestKind() == null
        || ioc.getPlanFingerprint() == null
        || PayloadService.iocValidationKind(payload).filter(ioc.getTestKind()::equals).isEmpty()) {
      return false;
    }
    Optional<String> fingerprint =
        IocValidationPlanner.fingerprintOf(ioc.getTestKind(), inject.getContent());
    return fingerprint.isPresent() && fingerprint.get().equals(ioc.getPlanFingerprint());
  }
}
