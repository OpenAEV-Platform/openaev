package io.openaev.service.payload_approval;

import io.openaev.database.model.Action;
import io.openaev.database.model.Payload;
import io.openaev.database.model.Payload.PAYLOAD_APPROVAL_STATUS;
import io.openaev.database.model.PayloadApproval;
import io.openaev.database.model.PayloadApproval.ORIGIN;
import io.openaev.database.model.PayloadApprovalNotification;
import io.openaev.database.model.PayloadVersion;
import io.openaev.database.model.ResourceType;
import io.openaev.database.model.User;
import io.openaev.database.repository.PayloadApprovalRepository;
import io.openaev.rest.exception.BadRequestException;
import io.openaev.service.PermissionService;
import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Approval of Threat Arsenal payloads: decides the status of every payload write and records each
 * change in the approval history.
 *
 * <ul>
 *   <li>A create, duplicate, import or content edit by a holder of "Approve content" is
 *       auto-approved, and the history records it as automatic with the author as approver.
 *   <li>The same write by anyone else makes the payload PENDING, including from REJECTED.
 *   <li>An edit that leaves the executable content unchanged (see {@link PayloadFingerprint}) keeps
 *       the status.
 *   <li>A collector write is PENDING when the payload is new or its content changed, unchanged
 *       otherwise.
 *   <li>A built-in payload created by the platform itself is APPROVED.
 *   <li>Only a PENDING payload can be approved or rejected, by a holder of "Approve content"; the
 *       approval is bound to the content fingerprint the approver saw, a rejection needs a reason.
 * </ul>
 *
 * <p>An executable edit of an APPROVED payload by anyone else does not reach this service: {@link
 * PayloadPendingVersionService} holds it back as a pending version, and the payload stays APPROVED.
 */
@Service
@RequiredArgsConstructor
public class PayloadApprovalService {

  public static final int MAX_COMMENT_LENGTH = 2000;

  static final String AUTO_APPROVED_COMMENT = "Auto-approved: the author holds Approve content";
  static final String SYSTEM_COMMENT = "Built-in payload created by the platform";

  private final PayloadApprovalRepository payloadApprovalRepository;
  private final PayloadApprovalNotificationService payloadApprovalNotificationService;

  /** Whether the user may approve payload content ("Approve content", admin or tenant bypass). */
  public static boolean canApprove(@Nullable final User user) {
    return user != null
        && PermissionService.holdsCapability(user, ResourceType.THREAT_ARSENAL, Action.APPROVE);
  }

  /**
   * Decides and records the status of a payload that was just saved.
   *
   * @param payload the saved payload (managed: the status is flushed with the transaction)
   * @param actor the user who wrote it, {@code null} for a platform or collector write
   * @param origin what wrote it
   * @param fingerprintBefore the fingerprint before an edit, {@code null} for a new payload
   */
  public void onWrite(
      @NotNull final Payload payload,
      @Nullable final User actor,
      @NotNull final ORIGIN origin,
      @Nullable final String fingerprintBefore) {
    String fingerprint = PayloadFingerprint.of(payload);
    if (fingerprintBefore != null && fingerprintBefore.equals(fingerprint)) {
      // Cosmetic edit or identical re-sync: what runs did not change, neither does the status.
      return;
    }
    // Approvers are notified when a payload becomes pending, not again while it stays pending.
    boolean wasPending =
        fingerprintBefore != null && payload.getApprovalStatus() == PAYLOAD_APPROVAL_STATUS.PENDING;
    switch (origin) {
      case SYSTEM ->
          apply(payload, PAYLOAD_APPROVAL_STATUS.APPROVED, origin, true, null, SYSTEM_COMMENT);
      case COLLECTOR -> {
        apply(payload, PAYLOAD_APPROVAL_STATUS.PENDING, origin, true, null, null);
        if (!wasPending) {
          payloadApprovalNotificationService.recordPending(payload, null, null);
        }
      }
      case CREATE, DUPLICATE, IMPORT, UPDATE -> {
        if (canApprove(actor)) {
          apply(
              payload,
              PAYLOAD_APPROVAL_STATUS.APPROVED,
              origin,
              true,
              actor,
              AUTO_APPROVED_COMMENT);
        } else if (actor == null && origin == ORIGIN.CREATE) {
          // Created outside any user request (platform datapacks): built-in content.
          apply(
              payload, PAYLOAD_APPROVAL_STATUS.APPROVED, ORIGIN.SYSTEM, true, null, SYSTEM_COMMENT);
        } else {
          apply(payload, PAYLOAD_APPROVAL_STATUS.PENDING, origin, true, actor, null);
          if (!wasPending) {
            payloadApprovalNotificationService.recordPending(payload, actor, null);
          }
        }
      }
      default -> throw new IllegalArgumentException("Not a write origin: " + origin);
    }
  }

  /**
   * Approves a pending payload, provided its content is still the one the approver saw.
   *
   * @param payload the payload, loaded with a row lock
   * @param shownFingerprint the fingerprint of the content displayed to the approver
   */
  public PayloadApproval approve(
      @NotNull final Payload payload,
      @NotNull final User decider,
      @NotNull final String shownFingerprint,
      @Nullable final String comment) {
    requirePending(payload);
    String fingerprint = PayloadFingerprint.of(payload);
    if (!fingerprint.equals(shownFingerprint)) {
      throw new BadRequestException(
          "The content of this payload changed since it was shown. Review it again, then approve.");
    }
    User author = pendingAuthor(payload);
    PayloadApproval approval =
        apply(
            payload,
            PAYLOAD_APPROVAL_STATUS.APPROVED,
            ORIGIN.APPROVE,
            false,
            decider,
            bounded(blankToNull(comment)));
    payloadApprovalNotificationService.recordOutcome(
        payload, PayloadApprovalNotification.EVENT.APPROVED, decider, author, null, null);
    return approval;
  }

  /** Rejects a pending payload. The reason is mandatory and shown to the author. */
  public PayloadApproval reject(
      @NotNull final Payload payload, @NotNull final User decider, @Nullable final String reason) {
    requirePending(payload);
    String trimmed = blankToNull(reason);
    if (trimmed == null) {
      throw new BadRequestException("A reason is required to reject a payload.");
    }
    String boundedReason = bounded(trimmed);
    User author = pendingAuthor(payload);
    PayloadApproval rejection =
        apply(
            payload,
            PAYLOAD_APPROVAL_STATUS.REJECTED,
            ORIGIN.REJECT,
            false,
            decider,
            boundedReason);
    payloadApprovalNotificationService.recordOutcome(
        payload, PayloadApprovalNotification.EVENT.REJECTED, decider, author, null, boundedReason);
    return rejection;
  }

  // Who sent the payload to pending (told the outcome): null for a collector.
  User pendingAuthor(Payload payload) {
    return payloadApprovalRepository
        .findFirstByPayloadIdAndStatusOrderByCreatedAtDesc(
            payload.getId(), PAYLOAD_APPROVAL_STATUS.PENDING)
        .map(PayloadApproval::getActor)
        .orElse(null);
  }

  /**
   * Records the approval of a pending version whose content was just applied to the payload (see
   * {@link PayloadPendingVersionService#approve}).
   */
  PayloadApproval recordVersionApproval(
      @NotNull final Payload payload,
      @NotNull final User decider,
      @Nullable final String comment,
      @NotNull final PayloadVersion version) {
    return apply(
        payload,
        PAYLOAD_APPROVAL_STATUS.APPROVED,
        ORIGIN.APPROVE,
        false,
        decider,
        comment,
        version);
  }

  /** The approval history of a payload, newest first. */
  public List<PayloadApproval> history(@NotNull final String payloadId) {
    return payloadApprovalRepository.findByPayloadIdOrderByCreatedAtDesc(payloadId);
  }

  /**
   * How many different executable contents of a payload were approved: a version count that also
   * covers approvals made outside versions (a pending payload approved again, older history).
   */
  public int approvedContentCount(@NotNull final String payloadId) {
    return (int) payloadApprovalRepository.countApprovedContents(payloadId);
  }

  /** The latest entry of the approval history of a payload. */
  public Optional<PayloadApproval> latest(@NotNull final String payloadId) {
    return payloadApprovalRepository.findFirstByPayloadIdOrderByCreatedAtDesc(payloadId);
  }

  private PayloadApproval apply(
      Payload payload,
      PAYLOAD_APPROVAL_STATUS status,
      ORIGIN origin,
      boolean automatic,
      User actor,
      String comment) {
    return apply(payload, status, origin, automatic, actor, comment, null);
  }

  private PayloadApproval apply(
      Payload payload,
      PAYLOAD_APPROVAL_STATUS status,
      ORIGIN origin,
      boolean automatic,
      User actor,
      String comment,
      PayloadVersion version) {
    String fingerprint = PayloadFingerprint.of(payload);
    payload.setApprovalStatus(status);
    payload.setApprovedFingerprint(status == PAYLOAD_APPROVAL_STATUS.APPROVED ? fingerprint : null);

    PayloadApproval entry = new PayloadApproval();
    entry.setTenant(payload.getTenant());
    entry.setPayload(payload);
    entry.setStatus(status);
    entry.setOrigin(origin);
    entry.setAutomatic(automatic);
    entry.setActor(actor);
    entry.setActorName(actor != null ? actor.getNameOrEmail() : null);
    entry.setComment(comment);
    entry.setFingerprint(fingerprint);
    entry.setVersion(version);
    return payloadApprovalRepository.save(entry);
  }

  private static void requirePending(Payload payload) {
    if (payload.getApprovalStatus() != PAYLOAD_APPROVAL_STATUS.PENDING) {
      throw new BadRequestException(
          "Only a pending payload can be approved or rejected; this one is "
              + payload.getApprovalStatus()
              + ".");
    }
  }

  static String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }

  static String bounded(String value) {
    if (value != null && value.length() > MAX_COMMENT_LENGTH) {
      throw new BadRequestException(
          "The comment is limited to " + MAX_COMMENT_LENGTH + " characters.");
    }
    return value;
  }
}
