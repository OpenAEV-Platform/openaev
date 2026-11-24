package io.openaev.notification.payload;

import static io.openaev.database.model.NotificationTriggerEventType.PAYLOAD_REVOKED;
import static io.openaev.database.model.NotificationTriggerEventType.REVOCATION_LIFTED;
import static io.openaev.database.model.NotificationTriggerEventType.REVOKED;
import static io.openaev.notification.content.ContentNotification.count;
import static io.openaev.notification.content.ContentNotification.named;

import io.openaev.database.model.Payload;
import io.openaev.database.model.payload.PayloadRevocationChanged;
import io.openaev.notification.content.ContentNotification;
import io.openaev.notification.content.ContentNotifier;
import io.openaev.rest.payload.service.PayloadService;
import io.openaev.service.payload.PayloadVersionService;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/** Tells the tenant a payload version is revoked or lifted, and which scenarios it stops. */
@Component
@RequiredArgsConstructor
public class PayloadRevocationNotificationListener {

  private final ContentNotifier contentNotifier;
  private final PayloadService payloadService;
  private final PayloadVersionService payloadVersionService;

  @Async("notificationEngineExecutor")
  @TransactionalEventListener(fallbackExecution = true)
  public void onRevocationChanged(PayloadRevocationChanged event) {
    contentNotifier.send(event.tenantId(), () -> notifications(event));
  }

  private List<ContentNotification> notifications(PayloadRevocationChanged event) {
    Payload payload = payloadService.payload(event.tenantId(), event.payloadId());
    if (!event.revoked()) {
      return List.of(
          ContentNotification.payload(payload, REVOCATION_LIFTED, ": revocation lifted"));
    }
    String revoked = event.byCatalogue() ? "revoked by Filigran" : "revoked: " + event.reason();
    List<ContentNotification> notifications = new ArrayList<>();
    notifications.add(ContentNotification.payload(payload, REVOKED, " " + revoked));
    notifications.addAll(
        ContentNotification.scenarios(
            payloadVersionService.scenarioUsages(event.tenantId(), List.of(payload)),
            PAYLOAD_REVOKED,
            injects ->
                "%s %s, %s can no longer run"
                    .formatted(named(payload), revoked, count(injects, "inject"))));
    return notifications;
  }
}
