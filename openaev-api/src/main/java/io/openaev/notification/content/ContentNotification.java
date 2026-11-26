package io.openaev.notification.content;

import io.openaev.api.threat_arsenal.dto.PayloadUsageOutput;
import io.openaev.database.model.NotificationTriggerEventType;
import io.openaev.database.model.Payload;
import io.openaev.notification.engine.NotificationResourceCatalog;
import java.util.List;
import java.util.Map;
import java.util.function.IntFunction;

/** A catalogue content event, with its message, ready for the notification engine. */
public record ContentNotification(
    NotificationResourceCatalog entry,
    String entityId,
    NotificationTriggerEventType eventType,
    String message) {

  public static ContentNotification payload(
      Payload payload, NotificationTriggerEventType eventType, String what) {
    return new ContentNotification(
        NotificationResourceCatalog.PAYLOAD,
        payload.getId(),
        eventType,
        "[payload] " + named(payload) + what);
  }

  /** One per scenario, its message told from how many of its injects and steps are concerned. */
  public static List<ContentNotification> scenarios(
      Map<String, List<PayloadUsageOutput>> usagesByScenario,
      NotificationTriggerEventType eventType,
      IntFunction<String> what) {
    return usagesByScenario.values().stream()
        .map(
            usages ->
                new ContentNotification(
                    NotificationResourceCatalog.SCENARIO,
                    usages.getFirst().scenarioId(),
                    eventType,
                    "[scenario] "
                        + usages.getFirst().scenarioName()
                        + ": "
                        + what.apply(usages.size())))
        .toList();
  }

  public static String named(Payload payload) {
    return payload.getName() + " v" + payload.getVersion();
  }

  public static String count(long count, String noun) {
    return count + " " + noun + (count == 1 ? "" : "s");
  }
}
