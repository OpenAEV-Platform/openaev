package io.openaev.notification.content;

import io.openaev.database.model.NotificationTriggerEventType;
import io.openaev.database.model.Payload;
import io.openaev.database.raw.RawScenarioUsageCount;
import io.openaev.notification.engine.NotificationResourceCatalog;
import java.util.List;
import java.util.function.LongFunction;

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
      List<RawScenarioUsageCount> scenarioUsages,
      NotificationTriggerEventType eventType,
      LongFunction<String> what) {
    return scenarioUsages.stream()
        .map(
            scenario ->
                new ContentNotification(
                    NotificationResourceCatalog.SCENARIO,
                    scenario.getScenarioId(),
                    eventType,
                    "[scenario] "
                        + scenario.getScenarioName()
                        + ": "
                        + what.apply(scenario.getUsageCount())))
        .toList();
  }

  public static String named(Payload payload) {
    return payload.getName() + " v" + payload.getVersion();
  }

  public static String count(long count, String noun) {
    return count + " " + noun + (count == 1 ? "" : "s");
  }
}
