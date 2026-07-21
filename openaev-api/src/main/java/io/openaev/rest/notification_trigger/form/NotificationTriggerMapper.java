package io.openaev.rest.notification_trigger.form;

import io.openaev.context.TenantContext;
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
import java.util.List;
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

  public NotificationTrigger toNotificationTrigger(final NotificationTriggerInput input) {
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
    trigger.setNotifiers(resolveNotifiers(input.getNotifierIds()));
    trigger.setChildTriggers(resolveChildTriggers(input.getChildTriggerIds()));
    trigger.setRecipientUsers(resolveUsers(input.getRecipientUserIds()));
    trigger.setRecipientGroups(resolveGroups(input.getRecipientGroupIds()));
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

  // All association lists are collected into mutable ArrayLists: Hibernate's merge clears and
  // refills the incoming collections, which fails on the immutable lists of Stream.toList().

  private List<Notifier> resolveNotifiers(List<String> ids) {
    return safeIds(ids).stream()
        .map(
            id ->
                notifierRepository
                    .findByIdAndTenantId(id, TenantContext.getCurrentTenant())
                    .orElseThrow(() -> new ElementNotFoundException("Notifier not found: " + id)))
        .collect(Collectors.toCollection(ArrayList::new));
  }

  private List<NotificationTrigger> resolveChildTriggers(List<String> ids) {
    return safeIds(ids).stream()
        .map(
            id ->
                notificationTriggerRepository
                    .findByIdAndTenantId(id, TenantContext.getCurrentTenant())
                    .orElseThrow(
                        () ->
                            new ElementNotFoundException("Notification trigger not found: " + id)))
        .collect(Collectors.toCollection(ArrayList::new));
  }

  private List<User> resolveUsers(List<String> ids) {
    return safeIds(ids).stream()
        .map(
            id ->
                userRepository
                    .findById(id)
                    .orElseThrow(() -> new ElementNotFoundException("User not found: " + id)))
        .collect(Collectors.toCollection(ArrayList::new));
  }

  private List<Group> resolveGroups(List<String> ids) {
    return safeIds(ids).stream()
        .map(
            id ->
                groupRepository
                    .findById(id)
                    .orElseThrow(() -> new ElementNotFoundException("Group not found: " + id)))
        .collect(Collectors.toCollection(ArrayList::new));
  }

  private List<String> safeIds(List<String> ids) {
    return ids != null ? ids : List.of();
  }
}
