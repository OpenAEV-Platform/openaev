package io.openaev.api.notification_trigger;

import io.openaev.database.model.Base;
import io.openaev.database.model.Group;
import io.openaev.database.model.NotificationTrigger;
import io.openaev.database.model.Notifier;
import io.openaev.database.model.User;
import io.openaev.database.repository.GroupRepository;
import io.openaev.database.repository.NotificationTriggerRepository;
import io.openaev.database.repository.NotifierRepository;
import io.openaev.database.repository.UserRepository;
import io.openaev.rest.exception.ElementNotFoundException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class NotificationTriggerMapper {

  private final NotifierRepository notifierRepository;
  private final NotificationTriggerRepository notificationTriggerRepository;
  private final UserRepository userRepository;
  private final GroupRepository groupRepository;

  /**
   * @param tenantId the tenant the trigger is written into, resolved from the request scope by
   *     {@code TenantWriteScopeResolver}. Child triggers are looked up inside it rather than under
   *     the request's read scope: the scope can hold several tenants while the row belongs to one,
   *     and composing a digest with another tenant's trigger would replay that tenant's outbox.
   */
  public NotificationTrigger toNotificationTrigger(
      final String tenantId, final NotificationTriggerInput input) {
    NotificationTrigger trigger = new NotificationTrigger();
    trigger.setName(input.getName());
    trigger.setType(input.getType());
    trigger.setEnabled(input.isEnabled());
    trigger.setWatchedResourceType(input.getResourceType());
    trigger.setEventTypes(input.getEventTypes());
    trigger.setFilters(input.getFilters());
    trigger.setInstanceId(input.getInstanceId());
    trigger.setPeriod(input.getPeriod());
    trigger.setTriggerTime(input.getTriggerTime());
    // notifiers is v2-active since #7864, so the inspector scopes this lookup. The endpoint
    // resolves tenantForWrite first and refuses a multi-tenant scope with a 400, so the scope
    // reaching here always names exactly the tenant the trigger is written into.
    trigger.setNotifiers(
        resolveAll(input.getNotifierIds(), notifierRepository::findAllById, "Notifier"));
    trigger.setChildTriggers(
        resolveAll(
            input.getChildTriggerIds(),
            ids -> notificationTriggerRepository.findAllByIdInAndTenantId(ids, tenantId),
            "Notification trigger"));
    trigger.setRecipientUsers(
        resolveAll(input.getRecipientUserIds(), userRepository::findAllById, "User"));
    trigger.setRecipientGroups(
        resolveAll(input.getRecipientGroupIds(), groupRepository::findAllById, "Group"));
    return trigger;
  }

  public NotificationTriggerOutput toNotificationTriggerOutput(final NotificationTrigger trigger) {
    return NotificationTriggerOutput.builder()
        .id(trigger.getId())
        .name(trigger.getName())
        .type(trigger.getType())
        .enabled(trigger.isEnabled())
        .resourceType(trigger.getWatchedResourceType())
        .eventTypes(trigger.getEventTypes())
        .filters(trigger.getFilters())
        .instanceId(trigger.getInstanceId())
        .period(trigger.getPeriod())
        .triggerTime(trigger.getTriggerTime())
        .childTriggerIds(
            trigger.getChildTriggers().stream().map(NotificationTrigger::getId).toList())
        .notifierIds(trigger.getNotifiers().stream().map(Notifier::getId).toList())
        .recipientUserIds(trigger.getRecipientUsers().stream().map(User::getId).toList())
        .recipientGroupIds(trigger.getRecipientGroups().stream().map(Group::getId).toList())
        .ownerId(trigger.getOwner().getId())
        .createdAt(trigger.getCreatedAt())
        .updatedAt(trigger.getUpdatedAt())
        .build();
  }

  /**
   * Resolves the requested ids with a single batched query and fails when any id is unknown (or,
   * for tenant-scoped lookups, belongs to another tenant). Results are collected into mutable
   * ArrayLists: Hibernate's merge clears and refills the incoming collections, which fails on
   * immutable lists.
   */
  private <T extends Base> List<T> resolveAll(
      List<String> ids, Function<Set<String>, Iterable<T>> batchLookup, String entityLabel) {
    if (ids == null || ids.isEmpty()) {
      return new ArrayList<>();
    }
    Set<String> uniqueIds = new LinkedHashSet<>(ids);
    List<T> found = new ArrayList<>();
    batchLookup.apply(uniqueIds).forEach(found::add);
    if (found.size() != uniqueIds.size()) {
      Set<String> foundIds = found.stream().map(Base::getId).collect(Collectors.toSet());
      String missing =
          uniqueIds.stream().filter(id -> !foundIds.contains(id)).collect(Collectors.joining(", "));
      throw new ElementNotFoundException(entityLabel + " not found: " + missing);
    }
    return found;
  }
}
