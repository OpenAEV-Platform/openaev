package io.openaev.service.stix;

import io.openaev.database.model.Asset;
import io.openaev.database.model.Inject;
import io.openaev.database.model.IocValidation;
import io.openaev.database.model.IocValidationIoc;
import io.openaev.database.model.Payload;
import io.openaev.database.repository.IocValidationRepository;
import io.openaev.rest.payload.service.PayloadService;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Refuses to dispatch an IOC validation test that runs anything but what an operator approved. The
 * injects of a validation simulation stay editable after the approval, and the IOC validation
 * payloads can be picked in any inject, while the checks of the planner (public targets, hosts of
 * the platform, egress proxy) hold only for the approved values: an inject runs an IOC validation
 * payload only as the test of an IOC validation of its simulation, with the payload of the approved
 * kind, the arguments whose fingerprint the approval recorded, and only on the endpoints the
 * approval recorded (no asset group, whose members can change after the approval).
 */
@Component
@RequiredArgsConstructor
public class IocValidationDispatchGuard {

  public static final String IOC_VALIDATION_UNAPPROVED_TEST =
      "This IOC validation test was changed after its approval: it no longer runs the payload, the"
          + " arguments or on the endpoints the operator approved, so it is not executed. Reject the"
          + " request and ask for a new validation from OpenCTI.";

  public static final String IOC_VALIDATION_PAYLOAD_OUTSIDE_VALIDATION =
      "This inject runs an IOC validation payload outside of an approved IOC validation, so it is"
          + " not executed: IOC validation payloads only run in the simulation of an IOC validation"
          + " request approved in OpenAEV.";

  private final IocValidationRepository iocValidationRepository;

  /**
   * @param inject the inject about to be dispatched
   * @param payload the payload of its injector contract
   * @throws IllegalStateException when the inject is a test of an IOC validation and differs from
   *     its approved plan, or runs an IOC validation payload without being such a test
   */
  public void refuseUnapprovedTest(Inject inject, Payload payload) {
    Optional<IocValidationIoc> test = validationTest(inject);
    if (test.isPresent()) {
      if (!isApproved(test.get(), inject, payload)) {
        throw new IllegalStateException(IOC_VALIDATION_UNAPPROVED_TEST);
      }
    } else if (PayloadService.isIocValidationPayload(payload)) {
      throw new IllegalStateException(IOC_VALIDATION_PAYLOAD_OUTSIDE_VALIDATION);
    }
  }

  /** The IOC whose test the inject is, in an IOC validation of its simulation. */
  private Optional<IocValidationIoc> validationTest(Inject inject) {
    if (inject.getExercise() == null || inject.getTenant() == null) {
      return Optional.empty();
    }
    for (IocValidation validation :
        iocValidationRepository.findBySimulationIdAndTenantId(
            inject.getExercise().getId(), inject.getTenant().getId())) {
      for (IocValidationIoc ioc : validation.getIocs()) {
        if (ioc.getInjectIds() != null && ioc.getInjectIds().contains(inject.getId())) {
          return Optional.of(ioc);
        }
      }
    }
    return Optional.empty();
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
    return fingerprint.isPresent()
        && fingerprint.get().equals(ioc.getPlanFingerprint())
        && targetsApprovedEndpoints(ioc, inject);
  }

  private static boolean targetsApprovedEndpoints(IocValidationIoc ioc, Inject inject) {
    List<String> approved = ioc.getTargetEndpointIds();
    return approved != null
        && !approved.isEmpty()
        && inject.getAssetGroups().isEmpty()
        && inject.getAssets().stream().map(Asset::getId).allMatch(approved::contains);
  }
}
