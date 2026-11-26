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
    return toNotificationTrigger(tenantId, input, Set.of());
  }

  /**
   * Same as {@link #toNotificationTrigger(String, NotificationTriggerInput)}, for an update of
   * {@code triggerId}: a recipient group id the trigger already carries is kept even when it names
   * a platform group, grandfathered from before groups activated under this guard; only a NEWLY
   * added recipient group id is refused for naming a platform group or one of another tenant.
   * Without this, re-submitting the input a client already holds (the ordinary PUT shape) would
   * refuse the whole update for a trigger that predates the guard.
   */
  public NotificationTrigger toNotificationTriggerForUpdate(
      final String tenantId, final String triggerId, final NotificationTriggerInput input) {
    Set<String> alreadyLinkedGroupIds =
        notificationTriggerRepository
            .findById(triggerId)
            .map(
                existing ->
                    existing.getRecipientGroups().stream()
                        .map(Group::getId)
                        .collect(Collectors.toSet()))
            .orElse(Set.of());
    return toNotificationTrigger(tenantId, input, alreadyLinkedGroupIds);
  }

  private NotificationTrigger toNotificationTrigger(
      final String tenantId,
      final NotificationTriggerInput input,
      final Set<String> alreadyLinkedGroupIds) {
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
    // Notifiers resolve in the tenant the trigger is written into, as the child triggers do.
    trigger.setNotifiers(
        resolveAll(
            input.getNotifierIds(),
            ids -> notifierRepository.findAllByIdInTenant(ids, tenantId),
            "Notifier"));
    trigger.setChildTriggers(
        resolveAll(
            input.getChildTriggerIds(),
            ids -> notificationTriggerRepository.findAllByIdInAndTenantId(ids, tenantId),
            "Notification trigger"));
    trigger.setRecipientUsers(
        resolveAll(input.getRecipientUserIds(), userRepository::findAllById, "User"));
    trigger.setRecipientGroups(
        resolveRecipientGroups(input.getRecipientGroupIds(), tenantId, alreadyLinkedGroupIds));
    return trigger;
  }

  /**
   * A newly requested recipient group resolves in the tenant the trigger is written into, never a
   * group of another tenant of the scope nor a platform group; one already linked before this call
   * (see {@link #toNotificationTriggerForUpdate}) is kept as is, whatever its tenant.
   */
  private List<Group> resolveRecipientGroups(
      List<String> ids, String tenantId, Set<String> alreadyLinkedGroupIds) {
    if (ids == null || ids.isEmpty()) {
      return new ArrayList<>();
    }
    Set<String> uniqueIds = new LinkedHashSet<>(ids);
    Set<String> keptIds =
        uniqueIds.stream().filter(alreadyLinkedGroupIds::contains).collect(Collectors.toSet());
    Set<String> newIds =
        uniqueIds.stream().filter(id -> !keptIds.contains(id)).collect(Collectors.toSet());
    List<Group> resolved = new ArrayList<>();
    if (!keptIds.isEmpty()) {
      groupRepository.findAllById(keptIds).forEach(resolved::add);
    }
    if (!newIds.isEmpty()) {
      resolved.addAll(
          resolveAll(
              List.copyOf(newIds), i -> groupRepository.findAllByIdInTenant(i, tenantId), "Group"));
    }
    return resolved;
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
