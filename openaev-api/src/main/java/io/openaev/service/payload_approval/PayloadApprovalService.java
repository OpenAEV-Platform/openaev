package io.openaev.service.payload_approval;

import io.openaev.database.model.Action;
import io.openaev.database.model.InjectorContract;
import io.openaev.database.model.Payload;
import io.openaev.database.model.Payload.PAYLOAD_APPROVAL_STATUS;
import io.openaev.database.model.PayloadApproval;
import io.openaev.database.model.PayloadApproval.ORIGIN;
import io.openaev.database.model.PayloadApprovalNotification;
import io.openaev.database.model.ResourceType;
import io.openaev.database.model.User;
import io.openaev.database.repository.InjectorContractRepository;
import io.openaev.database.repository.PayloadApprovalRepository;
import io.openaev.notification.content.ContentNotificationEvents.PayloadVersionAdded;
import io.openaev.rest.exception.BadRequestException;
import io.openaev.service.PermissionService;
import io.openaev.service.payload.PayloadVersionService;
import io.openaev.utils.SemanticVersion;
import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

/**
 * Approval of payload versions. A version never changes once created (ADR-018), so its status is
 * decided once, when it is created, then by an approver; each change is recorded in its approval
 * history.
 *
 * <ul>
 *   <li>A version from a trusted source (see {@link PayloadTrustPolicy}) is approved on arrival.
 *   <li>A version created, duplicated, imported or saved by a holder of "Approve content" is
 *       auto-approved, and the history records it as automatic with the author as approver.
 *   <li>The same version from anyone else, or from a collector, is PENDING.
 *   <li>A built-in version created by the platform itself is APPROVED.
 *   <li>A new version supersedes the older pending versions of the same payload: they can no longer
 *       be approved, and their authors are told.
 *   <li>Only a PENDING version can be approved or rejected, by a holder of "Approve content"; a
 *       rejection needs a reason.
 * </ul>
 *
 * <p>Injects stay on the version they run: the approver of a version is the one asked whether the
 * injects of the version it replaces move to it (see {@link #moveOffer}).
 */
@Service
@RequiredArgsConstructor
public class PayloadApprovalService {

  public static final int MAX_COMMENT_LENGTH = 2000;

  static final String AUTO_APPROVED_COMMENT = "Auto-approved: the author holds Approve content";
  static final String SYSTEM_COMMENT = "Built-in payload created by the platform";
  static final String TRUSTED_SOURCE_COMMENT = "Approved on arrival: from a trusted source";
  static final String SUPERSEDED_COMMENT = "Replaced by version %s before a decision";

  private final PayloadApprovalRepository payloadApprovalRepository;
  private final PayloadApprovalNotificationService payloadApprovalNotificationService;
  private final PayloadVersionService payloadVersionService;
  private final InjectorContractRepository injectorContractRepository;
  private final PayloadTrustPolicy payloadTrustPolicy;
  private final ApplicationEventPublisher eventPublisher;

  /**
   * The approved version that replaces another one, the injects and chained steps still running
   * that one, and its action: what the approver is asked to move.
   */
  public record MoveOffer(Payload from, InjectorContract fromContract, int usages) {}

  /** Whether the user may approve payload content ("Approve content", admin or tenant bypass). */
  public static boolean canApprove(@Nullable final User user) {
    return user != null
        && PermissionService.holdsCapability(user, ResourceType.THREAT_ARSENAL, Action.APPROVE);
  }

  /**
   * Decides the status of a version that was just created. A version is decided once: a second call
   * for the same version does nothing.
   *
   * @param version the saved version (managed: the status is flushed with the transaction)
   * @param actor the user who wrote it, {@code null} for a platform or collector write
   * @param origin what wrote it
   */
  public void onWrite(
      @NotNull final Payload version, @Nullable final User actor, @NotNull final ORIGIN origin) {
    if (payloadApprovalRepository.existsByPayloadId(version.getId())) {
      return;
    }
    // Approvers are notified when a payload starts waiting, not again for each newer version.
    boolean alreadyWaiting = !olderPending(version).isEmpty();
    if (payloadTrustPolicy.isTrusted(version)) {
      apply(
          version,
          PAYLOAD_APPROVAL_STATUS.APPROVED,
          ORIGIN.CATALOG,
          true,
          null,
          TRUSTED_SOURCE_COMMENT);
    } else {
      switch (origin) {
        case SYSTEM -> approveAsSystem(version);
        case COLLECTOR, CATALOG -> pending(version, null, origin, alreadyWaiting);
        case CREATE, DUPLICATE, IMPORT, UPDATE -> {
          if (canApprove(actor)) {
            apply(
                version,
                PAYLOAD_APPROVAL_STATUS.APPROVED,
                origin,
                true,
                actor,
                AUTO_APPROVED_COMMENT);
          } else if (actor == null && origin == ORIGIN.CREATE) {
            // Created outside any user request (platform datapacks): built-in content.
            approveAsSystem(version);
          } else {
            pending(version, actor, origin, alreadyWaiting);
          }
        }
        default -> throw new IllegalArgumentException("Not a write origin: " + origin);
      }
    }
    supersedeOlderPending(version, actor);
    payloadVersionService.refreshLatest(version.getIdentity());
  }

  /**
   * Approves a version created by the platform before built-in payloads were approved: still
   * pending, it is the platform's own content.
   */
  public void approveAsSystem(@NotNull final Payload version) {
    apply(version, PAYLOAD_APPROVAL_STATUS.APPROVED, ORIGIN.SYSTEM, true, null, SYSTEM_COMMENT);
    payloadVersionService.refreshLatest(version.getIdentity());
  }

  /**
   * Approves a pending version. Its injects stay where they are: see {@link #moveOffer}.
   *
   * @param version the version, loaded with a row lock
   */
  public PayloadApproval approve(
      @NotNull final Payload version, @NotNull final User decider, @Nullable final String comment) {
    requirePending(version);
    User author = pendingAuthor(version);
    PayloadApproval approval =
        apply(
            version,
            PAYLOAD_APPROVAL_STATUS.APPROVED,
            ORIGIN.APPROVE,
            false,
            decider,
            bounded(blankToNull(comment)));
    payloadApprovalNotificationService.recordOutcome(
        version,
        PayloadApprovalNotification.EVENT.APPROVED,
        decider,
        author,
        hasOtherApprovedVersion(version),
        null);
    payloadVersionService.refreshLatest(version.getIdentity());
    // Now available: scenarios still running an older version are told (see the content
    // notifications), as for a version approved when created.
    eventPublisher.publishEvent(
        new PayloadVersionAdded(version.getTenant().getId(), version.getId()));
    return approval;
  }

  /** Rejects a pending version. The reason is mandatory and shown to the author. */
  public PayloadApproval reject(
      @NotNull final Payload version, @NotNull final User decider, @Nullable final String reason) {
    requirePending(version);
    String trimmed = blankToNull(reason);
    if (trimmed == null) {
      throw new BadRequestException("A reason is required to reject a payload.");
    }
    String boundedReason = bounded(trimmed);
    User author = pendingAuthor(version);
    PayloadApproval rejection =
        apply(
            version,
            PAYLOAD_APPROVAL_STATUS.REJECTED,
            ORIGIN.REJECT,
            false,
            decider,
            boundedReason);
    payloadApprovalNotificationService.recordOutcome(
        version,
        PayloadApprovalNotification.EVENT.REJECTED,
        decider,
        author,
        hasOtherApprovedVersion(version),
        boundedReason);
    return rejection;
  }

  /**
   * What to offer to whoever made this version approved: moving the injects of the approved version
   * it replaces (the highest approved one below it). Empty when the version is not approved or
   * replaces none.
   */
  public Optional<MoveOffer> moveOffer(@NotNull final Payload version) {
    if (version.getApprovalStatus() != PAYLOAD_APPROVAL_STATUS.APPROVED) {
      return Optional.empty();
    }
    return payloadVersionService.findVersions(version.getIdentity()).stream()
        .filter(v -> v.getApprovalStatus() == PAYLOAD_APPROVAL_STATUS.APPROVED)
        .filter(v -> SemanticVersion.ORDER.compare(v.getVersion(), version.getVersion()) < 0)
        .max(Comparator.comparing(Payload::getVersion, SemanticVersion.ORDER))
        .map(
            from -> {
              Optional<InjectorContract> contract =
                  injectorContractRepository.findInjectorContractByPayload(from);
              int usages = contract.map(payloadVersionService::countTemplateUsages).orElse(0);
              return new MoveOffer(from, contract.orElse(null), usages);
            });
  }

  // Who sent the version to pending (told the outcome): null for a collector.
  User pendingAuthor(Payload version) {
    return payloadApprovalRepository
        .findFirstByPayloadIdAndStatusOrderByCreatedAtDesc(
            version.getId(), PAYLOAD_APPROVAL_STATUS.PENDING)
        .map(PayloadApproval::getActor)
        .orElse(null);
  }

  /** The approval history of a version, newest first. */
  public List<PayloadApproval> history(@NotNull final String payloadId) {
    return payloadApprovalRepository.findByPayloadIdOrderByCreatedAtDesc(payloadId);
  }

  /** The latest entry of the approval history of a version. */
  public Optional<PayloadApproval> latest(@NotNull final String payloadId) {
    return payloadApprovalRepository.findFirstByPayloadIdOrderByCreatedAtDesc(payloadId);
  }

  private void pending(Payload version, User actor, ORIGIN origin, boolean alreadyWaiting) {
    apply(version, PAYLOAD_APPROVAL_STATUS.PENDING, origin, true, actor, null);
    if (!alreadyWaiting) {
      payloadApprovalNotificationService.recordPending(
          version, actor, hasOtherApprovedVersion(version));
    }
  }

  // The pending versions of the same payload below this one.
  private List<Payload> olderPending(Payload version) {
    return payloadVersionService.findVersions(version.getIdentity()).stream()
        .filter(v -> !v.getId().equals(version.getId()))
        .filter(v -> v.getApprovalStatus() == PAYLOAD_APPROVAL_STATUS.PENDING)
        .filter(v -> SemanticVersion.ORDER.compare(v.getVersion(), version.getVersion()) < 0)
        .toList();
  }

  private void supersedeOlderPending(Payload version, User actor) {
    for (Payload older : olderPending(version)) {
      User author = pendingAuthor(older);
      apply(
          older,
          PAYLOAD_APPROVAL_STATUS.SUPERSEDED,
          ORIGIN.SUPERSEDE,
          true,
          actor,
          SUPERSEDED_COMMENT.formatted(version.getVersion()));
      payloadApprovalNotificationService.recordOutcome(
          older,
          PayloadApprovalNotification.EVENT.SUPERSEDED,
          actor,
          author,
          hasOtherApprovedVersion(older),
          null);
    }
  }

  private boolean hasOtherApprovedVersion(Payload version) {
    return payloadVersionService.findVersions(version.getIdentity()).stream()
        .anyMatch(
            v ->
                !v.getId().equals(version.getId())
                    && v.getApprovalStatus() == PAYLOAD_APPROVAL_STATUS.APPROVED);
  }

  private PayloadApproval apply(
      Payload version,
      PAYLOAD_APPROVAL_STATUS status,
      ORIGIN origin,
      boolean automatic,
      User actor,
      String comment) {
    version.setApprovalStatus(status);
    PayloadApproval entry = new PayloadApproval();
    entry.setTenant(version.getTenant());
    entry.setPayload(version);
    entry.setStatus(status);
    entry.setOrigin(origin);
    entry.setAutomatic(automatic);
    entry.setActor(actor);
    entry.setActorName(actor != null ? actor.getNameOrEmail() : null);
    entry.setComment(comment);
    return payloadApprovalRepository.save(entry);
  }

  private static void requirePending(Payload version) {
    if (version.getApprovalStatus() != PAYLOAD_APPROVAL_STATUS.PENDING) {
      throw new BadRequestException(
          "Only a pending version can be approved or rejected; this one is "
              + version.getApprovalStatus()
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
