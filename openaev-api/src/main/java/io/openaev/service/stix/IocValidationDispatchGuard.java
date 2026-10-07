package io.openaev.service.stix;

import io.openaev.database.model.Asset;
import io.openaev.database.model.Command;
import io.openaev.database.model.Inject;
import io.openaev.database.model.IocValidation;
import io.openaev.database.model.IocValidationIoc;
import io.openaev.database.model.Payload;
import io.openaev.database.repository.IocValidationRepository;
import io.openaev.rest.payload.service.PayloadService;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.hibernate.Hibernate;
import org.springframework.stereotype.Component;

/**
 * Refuses to run anything but what an operator approved. The injects of a validation simulation
 * stay editable after the approval, and the IOC validation payloads can be picked in any inject,
 * while the checks of the planner (public targets, hosts of the platform, egress proxy) hold only
 * for the approved values: the simulation of an IOC validation runs only the tests of the
 * validation, and an inject runs an IOC validation payload only as such a test, with exactly the
 * payload the approval recorded for it, still on its template, the arguments whose fingerprint the
 * approval recorded, and exactly the endpoints the approval recorded for it (no asset group, whose
 * members can change after the approval).
 */
@Component
@RequiredArgsConstructor
public class IocValidationDispatchGuard {

  public static final String IOC_VALIDATION_UNAPPROVED_TEST =
      "This IOC validation test was changed after its approval: it no longer runs the payload, the"
          + " arguments or on the endpoints the operator approved, so it is not executed. Reject the"
          + " request and ask for a new validation from OpenCTI.";

  public static final String IOC_VALIDATION_INJECT_NOT_APPROVED =
      "This inject was added to the simulation of an IOC validation after its approval, so it is"
          + " not executed: the simulation of an IOC validation runs only the tests the operator"
          + " approved.";

  public static final String IOC_VALIDATION_PAYLOAD_OUTSIDE_VALIDATION =
      "This inject runs an IOC validation payload outside of an approved IOC validation, so it is"
          + " not executed: IOC validation payloads only run in the simulation of an IOC validation"
          + " request approved in OpenAEV.";

  private final IocValidationRepository iocValidationRepository;

  /**
   * The one approval boundary of every execution path: the executor calls it before any status
   * change or dispatch to an injector, and the payload endpoint of the implants before it serves
   * the command.
   *
   * @param inject the inject about to be executed
   * @param payload the payload of its injector contract, {@code null} for a contract without one
   * @throws IllegalStateException when the payload is an IOC validation payload edited since its
   *     template, when the inject is a test of an IOC validation and differs from its approved
   *     plan, belongs to the simulation of an IOC validation without being one of its tests, or
   *     runs an IOC validation payload without being such a test
   */
  public void refuseUnapprovedExecution(Inject inject, Payload payload) {
    refuseEditedIocValidationPayload(payload);
    List<IocValidation> validations = validationsOf(inject);
    Optional<IocValidationIoc> test = testOf(validations, inject);
    if (test.isPresent()) {
      if (!isApproved(test.get(), inject, payload)) {
        throw new IllegalStateException(IOC_VALIDATION_UNAPPROVED_TEST);
      }
    } else if (!validations.isEmpty()) {
      throw new IllegalStateException(IOC_VALIDATION_INJECT_NOT_APPROVED);
    } else if (PayloadService.isIocValidationPayload(payload)) {
      throw new IllegalStateException(IOC_VALIDATION_PAYLOAD_OUTSIDE_VALIDATION);
    }
  }

  /**
   * An inject approved before an upgrade can still point at an IOC validation file-drop payload of
   * an earlier version, which wrote directly in the temp directory: it is refused until a new
   * approval brings the payload to the current template.
   */
  static void refuseOutdatedIocValidationFileDrop(Payload payload) {
    if (PayloadService.isIocValidationFileDropPayload(payload)
        && !(Hibernate.unproxy(payload) instanceof Command fileDrop
            && PayloadService.isCurrentIocValidationFileDropTemplate(fileDrop))) {
      throw new IllegalStateException(PayloadService.IOC_VALIDATION_OUTDATED_FILE_DROP);
    }
  }

  /**
   * An approval brings every IOC validation payload it uses to its template, but the payloads stay
   * editable until the inject runs: one that differs from its template by then would run something
   * the operator never approved, so it is refused.
   */
  static void refuseEditedIocValidationPayload(Payload payload) {
    refuseOutdatedIocValidationFileDrop(payload);
    if (PayloadService.isIocValidationPayload(payload)
        && !PayloadService.isCurrentIocValidationTemplate(payload)) {
      throw new IllegalStateException(PayloadService.IOC_VALIDATION_EDITED_PAYLOAD);
    }
  }

  /** The IOC validations whose simulation the inject belongs to. */
  private List<IocValidation> validationsOf(Inject inject) {
    if (inject.getExercise() == null || inject.getTenant() == null) {
      return List.of();
    }
    return iocValidationRepository.findBySimulationIdAndTenantId(
        inject.getExercise().getId(), inject.getTenant().getId());
  }

  /** The IOC whose test the inject is, in one of these validations. */
  private static Optional<IocValidationIoc> testOf(List<IocValidation> validations, Inject inject) {
    for (IocValidation validation : validations) {
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
        || !runsApprovedPayload(ioc, inject, payload)
        || PayloadService.iocValidationKind(payload).filter(ioc.getTestKind()::equals).isEmpty()) {
      return false;
    }
    Optional<String> fingerprint =
        IocValidationPlanner.fingerprintOf(ioc.getTestKind(), inject.getContent());
    return fingerprint.isPresent()
        && fingerprint.get().equals(ioc.getPlanFingerprint())
        && targetsApprovedEndpoints(ioc, inject);
  }

  private static boolean runsApprovedPayload(IocValidationIoc ioc, Inject inject, Payload payload) {
    String approved =
        ioc.getInjectPayloads() == null ? null : ioc.getInjectPayloads().get(inject.getId());
    return approved != null && payload != null && approved.equals(payload.getId());
  }

  private static boolean targetsApprovedEndpoints(IocValidationIoc ioc, Inject inject) {
    List<String> approved =
        ioc.getInjectTargets() == null ? null : ioc.getInjectTargets().get(inject.getId());
    if (approved == null || approved.isEmpty() || !inject.getAssetGroups().isEmpty()) {
      return false;
    }
    Set<String> targeted =
        inject.getAssets().stream().map(Asset::getId).collect(Collectors.toSet());
    return targeted.equals(Set.copyOf(approved));
  }
}
