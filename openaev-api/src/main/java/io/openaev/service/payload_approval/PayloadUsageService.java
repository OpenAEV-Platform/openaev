package io.openaev.service.payload_approval;

import io.openaev.database.model.Action;
import io.openaev.database.model.ExerciseStatus;
import io.openaev.database.model.Payload;
import io.openaev.database.model.Payload.PAYLOAD_APPROVAL_STATUS;
import io.openaev.database.model.ResourceType;
import io.openaev.database.model.User;
import io.openaev.database.raw.RawPayloadUsageItem;
import io.openaev.database.repository.InjectRepository;
import io.openaev.service.PermissionService;
import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

/**
 * Where a payload is used, to warn before an approval change blocks launches: rejecting it, or an
 * edit that sends an approved payload back to pending.
 */
@Service
@RequiredArgsConstructor
public class PayloadUsageService {

  /**
   * Simulations a non-approved payload still blocks: those that can still be started or will still
   * run (scheduled, including "draft" ones without a start date, running or paused) and in which an
   * inject using the payload has not run yet. Finished and canceled simulations cannot run again,
   * and a running one whose injects using the payload already ran is not affected.
   */
  static final List<ExerciseStatus> SIMULATIONS_TO_RUN =
      List.of(ExerciseStatus.SCHEDULED, ExerciseStatus.RUNNING, ExerciseStatus.PAUSED);

  /**
   * Names listed per type: enough for the warning dialogs, capped so a payload used in hundreds of
   * items stays fast (the counts are always exact).
   */
  static final int NAMES_LIMIT = 20;

  private final InjectRepository injectRepository;

  /**
   * The usage of a payload. Names are listed only for the resource types the viewer can read
   * (atomic testings, scenarios, simulations); otherwise only the counts are returned.
   */
  public PayloadUsage usage(@NotNull final String payloadId, @Nullable final User viewer) {
    Pageable firstNames = PageRequest.of(0, NAMES_LIMIT);
    return new PayloadUsage(
        injectRepository.countAtomicTestingsByPayloadId(payloadId),
        injectRepository.countScenariosByPayloadId(payloadId),
        injectRepository.countSimulationsByPayloadIdAndStatusIn(payloadId, SIMULATIONS_TO_RUN),
        namesIfReadable(
            viewer,
            ResourceType.ATOMIC_TESTING,
            () -> injectRepository.findAtomicTestingsByPayloadId(payloadId, firstNames)),
        namesIfReadable(
            viewer,
            ResourceType.SCENARIO,
            () -> injectRepository.findScenariosByPayloadId(payloadId, firstNames)),
        namesIfReadable(
            viewer,
            ResourceType.SIMULATION,
            () ->
                injectRepository.findSimulationsByPayloadIdAndStatusIn(
                    payloadId, SIMULATIONS_TO_RUN, firstNames)));
  }

  /**
   * The usage an edit by {@code actor} could block, read before the edit is applied: present only
   * when the payload is approved, the actor cannot approve (so a content change sends it back to
   * pending) and the payload is used. Whether the content actually changes is checked after the
   * edit, with {@link #refuseUnconfirmedSendBackToPending}.
   */
  public @Nullable PayloadUsage usageAtRiskOfEdit(
      @NotNull final Payload payload, @Nullable final User actor) {
    if (payload.getApprovalStatus() != PAYLOAD_APPROVAL_STATUS.APPROVED
        || PayloadApprovalService.canApprove(actor)) {
      return null;
    }
    PayloadUsage usage = usage(payload.getId(), actor);
    return usage.isUsed() ? usage : null;
  }

  /**
   * Refuses an unconfirmed edit that changes the executable content of an approved payload in use,
   * so the caller can warn first. Throwing rolls the edit back: nothing is written.
   */
  public void refuseUnconfirmedSendBackToPending(
      @Nullable final PayloadUsage usageAtRisk,
      @NotNull final String fingerprintBefore,
      @NotNull final Payload editedPayload) {
    if (usageAtRisk != null && !fingerprintBefore.equals(PayloadFingerprint.of(editedPayload))) {
      throw new PayloadApprovalImpactException(usageAtRisk);
    }
  }

  private static @Nullable List<RawPayloadUsageItem> namesIfReadable(
      @Nullable User viewer, ResourceType resourceType, Supplier<List<RawPayloadUsageItem>> names) {
    if (viewer == null || !PermissionService.holdsCapability(viewer, resourceType, Action.READ)) {
      return null;
    }
    return names.get();
  }
}
