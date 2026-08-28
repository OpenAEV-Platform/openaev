package io.openaev.notification.engine;

import io.openaev.database.model.Filters;
import io.openaev.database.model.NotificationTrigger;
import io.openaev.database.model.NotificationTriggerEventType;
import io.openaev.database.model.NotificationTriggerPeriod;
import io.openaev.database.model.NotificationTriggerType;
import io.openaev.database.model.ResourceType;
import io.openaev.database.model.User;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Detached, session-safe view of a {@link NotificationTrigger} with recipients fanned out to user
 * ids and notifiers resolved. Built inside a transaction (lazy collections initialized) and safely
 * usable from async engine threads afterwards.
 */
public record ResolvedNotificationTrigger(
    String id,
    String name,
    NotificationTriggerType type,
    ResourceType watchedResourceType,
    Set<NotificationTriggerEventType> eventTypes,
    Filters.FilterGroup filters,
    String instanceId,
    NotificationTriggerPeriod period,
    String triggerTime,
    List<String> childTriggerIds,
    String tenantId,
    List<String> recipientUserIds,
    List<ResolvedNotifier> notifiers) {

  public static ResolvedNotificationTrigger from(NotificationTrigger trigger) {
    // Recipients: owner by default, plus explicitly targeted users and group members
    Set<String> userIds = new LinkedHashSet<>();
    if (trigger.getRecipientUsers().isEmpty() && trigger.getRecipientGroups().isEmpty()) {
      userIds.add(trigger.getOwner().getId());
    } else {
      trigger.getRecipientUsers().forEach(user -> userIds.add(user.getId()));
      trigger.getRecipientGroups().stream()
          .flatMap(group -> group.getUsers().stream())
          .map(User::getId)
          .forEach(userIds::add);
    }
    return new ResolvedNotificationTrigger(
        trigger.getId(),
        trigger.getName(),
        trigger.getType(),
        trigger.getWatchedResourceType(),
        Set.copyOf(trigger.getEventTypes() != null ? trigger.getEventTypes() : List.of()),
        trigger.getFilters(),
        trigger.getInstanceId(),
        trigger.getPeriod(),
        trigger.getTriggerTime(),
        trigger.getChildTriggers().stream().map(NotificationTrigger::getId).toList(),
        trigger.getTenant().getId(),
        new ArrayList<>(userIds),
        trigger.getNotifiers().stream().map(ResolvedNotifier::from).toList());
  }
}
