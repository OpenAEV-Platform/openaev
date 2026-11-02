package io.openaev.service.payload_approval;

import io.openaev.database.model.Payload;
import io.openaev.database.model.PayloadApprovalNotification;
import io.openaev.database.model.PayloadApprovalNotification.EVENT;
import io.openaev.database.model.User;
import io.openaev.database.repository.PayloadApprovalNotificationRepository;
import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Records payload approval changes to notify (Task 6): the approval services call {@code record*}
 * in the same transaction as the change, which writes one {@link PayloadApprovalNotification}
 * (outbox). Nothing is lost when the change commits, nothing is sent when it rolls back. {@link
 * PayloadApprovalNotificationDispatcher} sends them every minute.
 *
 * <p>Kept apart from the dispatcher on purpose: sending reaches the mailer, which depends on the
 * inject and payload services that call this recorder (a bean cycle).
 */
@Service
@RequiredArgsConstructor
public class PayloadApprovalNotificationService {

  private final PayloadApprovalNotificationRepository notificationRepository;

  // -- RECORD (same transaction as the approval change) --

  /**
   * A payload, or a new version of an approved payload, is waiting for approval: the approvers will
   * be notified, except the author.
   *
   * @param versionNumber the pending version, {@code null} for a payload never approved
   */
  public void recordPending(
      @NotNull final Payload payload,
      @Nullable final User author,
      @Nullable final Integer versionNumber) {
    PayloadApprovalNotification notification =
        newNotification(
            payload, versionNumber == null ? EVENT.PAYLOAD_PENDING : EVENT.VERSION_PENDING);
    notification.setActor(author);
    notification.setActorName(author != null ? author.getNameOrEmail() : null);
    notification.setVersionNumber(versionNumber);
    notificationRepository.save(notification);
  }

  /**
   * The author's payload or version was approved, rejected or superseded. Nothing is recorded when
   * there is no author (collector, platform) or when the author made the change themselves.
   *
   * @param actor who decided (approve, reject) or whose change superseded the version
   * @param versionNumber the version, {@code null} for a payload never approved
   * @param reason the reason of a rejection
   */
  public void recordOutcome(
      @NotNull final Payload payload,
      @NotNull final EVENT event,
      @Nullable final User actor,
      @Nullable final User author,
      @Nullable final Integer versionNumber,
      @Nullable final String reason) {
    if (event.forApprovers()) {
      throw new IllegalArgumentException("Not an outcome event: " + event);
    }
    if (author == null || (actor != null && author.getId().equals(actor.getId()))) {
      return;
    }
    PayloadApprovalNotification notification = newNotification(payload, event);
    notification.setActor(actor);
    notification.setActorName(actor != null ? actor.getNameOrEmail() : null);
    notification.setRecipient(author);
    notification.setVersionNumber(versionNumber);
    notification.setComment(reason);
    notificationRepository.save(notification);
  }

  private static PayloadApprovalNotification newNotification(Payload payload, EVENT event) {
    PayloadApprovalNotification notification = new PayloadApprovalNotification();
    notification.setTenant(payload.getTenant());
    notification.setPayload(payload);
    notification.setPayloadName(payload.getName());
    notification.setEvent(event);
    return notification;
  }

  // -- DISPATCH SIDE (only this class touches the outbox repository) --

  /** The tenants with notifications still to send (call under a cross-tenant scope). */
  List<String> tenantIdsToDispatch() {
    return notificationRepository.findTenantIdsToDispatch();
  }

  /** The scoped tenant's notifications still to send, oldest first. */
  List<PayloadApprovalNotification> toDispatch() {
    return notificationRepository.findByDispatchedAtIsNullOrderByCreatedAtAsc();
  }

  /** Marks notifications as sent. */
  void markDispatched(List<PayloadApprovalNotification> notifications, Instant at) {
    notifications.forEach(notification -> notification.setDispatchedAt(at));
    notificationRepository.saveAll(notifications);
  }
}
