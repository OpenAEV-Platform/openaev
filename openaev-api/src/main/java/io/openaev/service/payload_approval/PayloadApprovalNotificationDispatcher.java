package io.openaev.service.payload_approval;

import io.openaev.database.model.Capability;
import io.openaev.database.model.InjectorContract;
import io.openaev.database.model.NotificationPreferenceType;
import io.openaev.database.model.NotificationTriggerEventType;
import io.openaev.database.model.NotificationTriggerType;
import io.openaev.database.model.NotifierType;
import io.openaev.database.model.Payload;
import io.openaev.database.model.PayloadApprovalNotification;
import io.openaev.database.model.PayloadApprovalNotification.EVENT;
import io.openaev.database.model.ResourceType;
import io.openaev.database.model.UserNotificationPreference;
import io.openaev.database.repository.InjectorContractRepository;
import io.openaev.database.repository.UserNotificationPreferenceRepository;
import io.openaev.database.repository.UserRepository;
import io.openaev.notification.engine.NotificationContent;
import io.openaev.notification.engine.NotificationDispatchService;
import io.openaev.notification.engine.ResolvedNotificationTrigger;
import io.openaev.notification.engine.ResolvedNotifier;
import io.openaev.scheduler.TenantScopedJobRunner;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

/**
 * Sends the payload approval notifications recorded by {@link PayloadApprovalNotificationService}
 * (Task 6), run every minute by the job: what is still to send is grouped per tenant and per
 * recipient (at most one notification per type and per user per run) and sent through the
 * notification center: in-app and, when the user opted in, email.
 *
 * <p>Pending events go to the tenant members whose tenant roles explicitly hold <i>Approve
 * content</i> (not platform admins, not Bypass, never the actor); outcome events go to the author
 * of the payload or version.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PayloadApprovalNotificationDispatcher {

  static final String REQUESTS_NAME = "Payloads pending approval";
  static final String OUTCOMES_NAME = "Approval of your payloads";
  private static final String EMAIL_TEMPLATE_PATH =
      "classpath:email/payload_approval_notification_en.html";
  private static final String EMAIL_SUBJECT = "[OpenAEV] ${notification_name}";

  private final PayloadApprovalNotificationService recorder;
  private final UserRepository userRepository;
  private final UserNotificationPreferenceRepository preferenceRepository;
  private final InjectorContractRepository injectorContractRepository;
  private final NotificationDispatchService notificationDispatchService;
  private final TenantScopedJobRunner tenantScopedJobRunner;
  private final ResourceLoader resourceLoader;

  private volatile String cachedEmailTemplate;

  // -- DISPATCH (job, every minute) --

  /** Sends what is still to send, tenant by tenant; a failing tenant does not stop the others. */
  public void dispatchPending() {
    List<String> tenantIds =
        tenantScopedJobRunner.supplyAcrossTenants(recorder::tenantIdsToDispatch);
    for (String tenantId : tenantIds) {
      try {
        tenantScopedJobRunner.runInTenant(tenantId, () -> dispatchTenant(tenantId));
      } catch (Exception e) {
        log.error("Payload approval notifications of tenant {} could not be sent", tenantId, e);
      }
    }
  }

  // Runs inside the tenant-scoped transaction opened by the job runner.
  void dispatchTenant(String tenantId) {
    List<PayloadApprovalNotification> toSend = recorder.toDispatch();
    if (toSend.isEmpty()) {
      return;
    }
    List<String> approverIds =
        toSend.stream().anyMatch(n -> n.getEvent().forApprovers())
            ? userRepository.findIdsOfTenantMembersExplicitlyHolding(
                tenantId, Capability.APPROVE_THREAT_ARSENALS.name())
            : List.of();
    Map<String, String> actionIds = new HashMap<>();
    Map<String, List<NotificationContent.Event>> requests = new LinkedHashMap<>();
    Map<String, List<NotificationContent.Event>> outcomes = new LinkedHashMap<>();
    for (PayloadApprovalNotification notification : toSend) {
      Payload payload = notification.getPayload();
      String actionId = actionIds.computeIfAbsent(payload.getId(), id -> actionIdOf(payload));
      NotificationContent.Event event =
          new NotificationContent.Event(
              operationOf(notification.getEvent()),
              messageOf(notification),
              ResourceType.THREAT_ARSENAL,
              actionId);
      if (notification.getEvent().forApprovers()) {
        String actorId = notification.getActor() != null ? notification.getActor().getId() : null;
        approverIds.stream()
            .filter(approverId -> !approverId.equals(actorId))
            .forEach(
                approverId ->
                    requests.computeIfAbsent(approverId, k -> new ArrayList<>()).add(event));
      } else if (notification.getRecipient() != null) {
        outcomes
            .computeIfAbsent(notification.getRecipient().getId(), k -> new ArrayList<>())
            .add(event);
      }
    }
    send(tenantId, NotificationPreferenceType.PAYLOAD_APPROVAL_REQUESTS, requests);
    send(tenantId, NotificationPreferenceType.PAYLOAD_APPROVAL_OUTCOMES, outcomes);
    recorder.markDispatched(toSend, Instant.now());
  }

  private void send(
      String tenantId,
      NotificationPreferenceType type,
      Map<String, List<NotificationContent.Event>> eventsByUser) {
    if (eventsByUser.isEmpty()) {
      return;
    }
    Map<String, UserNotificationPreference> preferences =
        preferenceRepository.findByUserIdInAndType(eventsByUser.keySet(), type).stream()
            .collect(Collectors.toMap(p -> p.getUser().getId(), Function.identity()));
    String name =
        type == NotificationPreferenceType.PAYLOAD_APPROVAL_REQUESTS
            ? REQUESTS_NAME
            : OUTCOMES_NAME;
    eventsByUser.forEach(
        (userId, events) -> {
          Optional<UserNotificationPreference> preference =
              Optional.ofNullable(preferences.get(userId));
          List<ResolvedNotifier> notifiers = new ArrayList<>();
          if (preference
              .map(UserNotificationPreference::isInApp)
              .orElse(UserNotificationPreference.DEFAULT_IN_APP)) {
            notifiers.add(
                new ResolvedNotifier(
                    "payload-approval-ui", "User interface", NotifierType.UI, Map.of()));
          }
          if (preference
              .map(UserNotificationPreference::isEmail)
              .orElse(UserNotificationPreference.DEFAULT_EMAIL)) {
            notifiers.add(
                new ResolvedNotifier(
                    "payload-approval-email",
                    "Default mailer",
                    NotifierType.EMAIL,
                    Map.of("subject", EMAIL_SUBJECT, "template", emailTemplate())));
          }
          if (notifiers.isEmpty()) {
            return;
          }
          ResolvedNotificationTrigger trigger =
              new ResolvedNotificationTrigger(
                  "payload-approval-" + type.name().toLowerCase(),
                  name,
                  NotificationTriggerType.LIVE,
                  ResourceType.THREAT_ARSENAL,
                  Set.of(),
                  null,
                  null,
                  null,
                  null,
                  List.of(),
                  tenantId,
                  List.of(userId),
                  notifiers);
          NotificationContent.Group group =
              new NotificationContent.Group(groupTitle(type, events.size()), events);
          notificationDispatchService.dispatch(
              trigger, NotificationTriggerType.LIVE, List.of(userId), List.of(group));
        });
  }

  // -- CONTENT --

  // The Threat Arsenal opens an action by its injector contract id; null when the contract is gone.
  private String actionIdOf(Payload payload) {
    return injectorContractRepository
        .findInjectorContractByPayload(payload)
        .map(InjectorContract::getId)
        .orElse(null);
  }

  static NotificationTriggerEventType operationOf(EVENT event) {
    return switch (event) {
      case PAYLOAD_PENDING, VERSION_PENDING -> NotificationTriggerEventType.APPROVAL_PENDING;
      case APPROVED -> NotificationTriggerEventType.APPROVED;
      case REJECTED -> NotificationTriggerEventType.REJECTED;
      case SUPERSEDED -> NotificationTriggerEventType.SUPERSEDED;
    };
  }

  static String groupTitle(NotificationPreferenceType type, int count) {
    if (type == NotificationPreferenceType.PAYLOAD_APPROVAL_OUTCOMES) {
      return count == 1 ? "1 update on your payloads" : count + " updates on your payloads";
    }
    return count == 1 ? "1 payload pending approval" : count + " payloads pending approval";
  }

  static String messageOf(PayloadApprovalNotification notification) {
    String payload = "\"" + notification.getPayloadName() + "\"";
    Integer version = notification.getVersionNumber();
    String actor = notification.getActorName();
    return switch (notification.getEvent()) {
      case PAYLOAD_PENDING ->
          "Payload "
              + payload
              + " is pending approval"
              + (actor != null ? " (submitted by " + actor + ")" : " (from a collector)");
      case VERSION_PENDING ->
          "New version v"
              + version
              + " of "
              + payload
              + " is pending approval"
              + (actor != null ? " (submitted by " + actor + ")" : " (from a collector)");
      case APPROVED -> yours(payload, version) + " was approved" + by(actor);
      case REJECTED ->
          yours(payload, version)
              + " was rejected"
              + by(actor)
              + (notification.getComment() != null ? ": " + notification.getComment() : "");
      case SUPERSEDED ->
          yours(payload, version)
              + " was replaced by a newer version"
              + (actor != null ? " (by " + actor + ")" : " (from a collector)");
    };
  }

  private static String yours(String payload, Integer version) {
    return version != null
        ? "Your version v" + version + " of " + payload
        : "Your payload " + payload;
  }

  private static String by(String actor) {
    return actor != null ? " by " + actor : "";
  }

  // The classpath template is static: read it once (racing threads would just read it twice).
  private String emailTemplate() {
    String template = cachedEmailTemplate;
    if (template != null) {
      return template;
    }
    try (InputStream inputStream =
        resourceLoader.getResource(EMAIL_TEMPLATE_PATH).getInputStream()) {
      template = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
      cachedEmailTemplate = template;
      return template;
    } catch (IOException e) {
      throw new IllegalStateException("Failed to read the payload approval email template", e);
    }
  }
}
