package io.openaev.service.notification;

import static io.openaev.utils.pagination.PaginationUtils.buildPaginationJPA;

import io.openaev.context.TenantContext;
import io.openaev.database.model.NotificationTrigger;
import io.openaev.database.model.NotificationTriggerEventType;
import io.openaev.database.model.NotificationTriggerPeriod;
import io.openaev.database.model.NotificationTriggerType;
import io.openaev.database.model.ResourceType;
import io.openaev.database.model.User;
import io.openaev.database.repository.NotificationTriggerRepository;
import io.openaev.notification.engine.NotificationResourceCatalog;
import io.openaev.notification.engine.NotificationTriggerCacheService;
import io.openaev.rest.exception.ElementNotFoundException;
import io.openaev.service.UserService;
import io.openaev.utils.pagination.SearchPaginationInput;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.Optional;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
public class NotificationTriggerService {

  private static final String HOUR_MINUTE = "([01]\\d|2[0-3]):[0-5]\\d";
  private static final Pattern DAY_TIME = Pattern.compile("^" + HOUR_MINUTE + "$");
  private static final Pattern WEEK_TIME = Pattern.compile("^[1-7]-" + HOUR_MINUTE + "$");
  private static final Pattern MONTH_TIME =
      Pattern.compile("^([1-9]|[12]\\d|3[01])-" + HOUR_MINUTE + "$");

  private final NotificationTriggerRepository notificationTriggerRepository;
  private final NotificationTriggerCacheService triggerCacheService;
  private final UserService userService;

  // readOnly transaction keeps the session open for the lazy owner access in canAccess
  @Transactional(readOnly = true)
  public Optional<NotificationTrigger> findById(@NotBlank final String id) {
    return notificationTriggerRepository
        .findByIdAndTenantId(id, TenantContext.getCurrentTenant())
        .filter(this::canAccess);
  }

  @Transactional
  public Page<NotificationTrigger> search(
      @NotNull final SearchPaginationInput searchPaginationInput) {
    // Self-service view: every user (including admins) only sees their own triggers,
    // aligned with the OpenCTI profile triggers page
    User currentUser = userService.currentUser();
    Specification<NotificationTrigger> ownerSpec =
        (root, query, cb) -> cb.equal(root.get("owner").get("id"), currentUser.getId());
    return buildPaginationJPA(
        (specification, pageable) ->
            notificationTriggerRepository.findAll(ownerSpec.and(specification), pageable),
        searchPaginationInput,
        NotificationTrigger.class);
  }

  @Transactional
  public NotificationTrigger create(@NotNull final NotificationTrigger trigger) {
    User currentUser = userService.currentUser();
    trigger.setOwner(currentUser);
    enforceRecipientTargeting(trigger, currentUser);
    validate(trigger);
    NotificationTrigger saved = notificationTriggerRepository.save(trigger);
    triggerCacheService.invalidateAfterCommit();
    return saved;
  }

  @Transactional
  public NotificationTrigger update(
      @NotBlank final String id, @NotNull final NotificationTrigger input) {
    NotificationTrigger trigger = requireOwnedTrigger(id, "Notification trigger not found: " + id);
    User currentUser = userService.currentUser();
    trigger.setName(input.getName());
    trigger.setEnabled(input.isEnabled());
    trigger.setWatchedResourceType(input.getWatchedResourceType());
    trigger.setEventTypes(input.getEventTypes());
    trigger.setFilters(input.getFilters());
    trigger.setInstanceId(input.getInstanceId());
    trigger.setPeriod(input.getPeriod());
    trigger.setTriggerTime(input.getTriggerTime());
    trigger.setChildTriggers(input.getChildTriggers());
    trigger.setNotifiers(input.getNotifiers());
    trigger.setRecipientUsers(input.getRecipientUsers());
    trigger.setRecipientGroups(input.getRecipientGroups());
    enforceRecipientTargeting(trigger, currentUser);
    validate(trigger);
    NotificationTrigger saved = notificationTriggerRepository.save(trigger);
    triggerCacheService.invalidateAfterCommit();
    return saved;
  }

  @Transactional
  public void delete(@NotBlank final String id) {
    requireOwnedTrigger(id, "Notification trigger not found: " + id);
    notificationTriggerRepository.deleteById(id);
    triggerCacheService.invalidateAfterCommit();
  }

  // -- HELPERS --

  private NotificationTrigger requireOwnedTrigger(String id, String notFoundMessage) {
    NotificationTrigger trigger =
        notificationTriggerRepository
            .findByIdAndTenantId(id, TenantContext.getCurrentTenant())
            .orElseThrow(() -> new ElementNotFoundException(notFoundMessage));
    if (!canAccess(trigger)) {
      // Do not disclose the existence of another user's trigger
      throw new ElementNotFoundException(notFoundMessage);
    }
    return trigger;
  }

  // Strictly per-user: triggers are self-service resources, only their owner can read or
  // manage them (no admin bypass, aligned with the OpenCTI profile triggers behavior)
  private boolean canAccess(NotificationTrigger trigger) {
    User currentUser = userService.currentUser();
    return trigger.getOwner().getId().equals(currentUser.getId());
  }

  /** Only administrators can target other users or groups as recipients. */
  private void enforceRecipientTargeting(NotificationTrigger trigger, User currentUser) {
    boolean targetsOthers =
        !trigger.getRecipientGroups().isEmpty()
            || trigger.getRecipientUsers().stream()
                .anyMatch(user -> !user.getId().equals(currentUser.getId()));
    if (targetsOthers && !currentUser.isAdmin()) {
      throw new IllegalArgumentException(
          "Only administrators can target other users or groups as notification recipients");
    }
  }

  private void validate(NotificationTrigger trigger) {
    if (trigger.getType() == NotificationTriggerType.LIVE) {
      if (trigger.getWatchedResourceType() == null
          || NotificationResourceCatalog.fromResourceType(trigger.getWatchedResourceType())
              .isEmpty()) {
        throw new IllegalArgumentException(
            "Live notification triggers require a supported resource type");
      }
      if (trigger.getEventTypes() == null || trigger.getEventTypes().isEmpty()) {
        throw new IllegalArgumentException(
            "Live notification triggers require at least one event type");
      }
      // Score degradation is a scenario-only semantic event
      if (trigger.getEventTypes().contains(NotificationTriggerEventType.SCORE_DEGRADATION)
          && trigger.getWatchedResourceType() != ResourceType.SCENARIO) {
        throw new IllegalArgumentException(
            "The score degradation event type is only supported for scenarios");
      }
    } else if (trigger.getType() == NotificationTriggerType.DIGEST) {
      if (trigger.getPeriod() == null) {
        throw new IllegalArgumentException("Digest notification triggers require a period");
      }
      if (trigger.getChildTriggers() == null || trigger.getChildTriggers().isEmpty()) {
        throw new IllegalArgumentException(
            "Digest notification triggers require at least one composed trigger");
      }
      validateTriggerTime(trigger.getPeriod(), trigger.getTriggerTime());
    }
    if (trigger.getNotifiers() == null || trigger.getNotifiers().isEmpty()) {
      throw new IllegalArgumentException("Notification triggers require at least one notifier");
    }
  }

  private void validateTriggerTime(NotificationTriggerPeriod period, String triggerTime) {
    boolean valid =
        switch (period) {
          case HOUR -> true; // fires on the hour, no trigger time needed
          case DAY -> triggerTime != null && DAY_TIME.matcher(triggerTime).matches();
          case WEEK -> triggerTime != null && WEEK_TIME.matcher(triggerTime).matches();
          case MONTH -> triggerTime != null && MONTH_TIME.matcher(triggerTime).matches();
        };
    if (!valid) {
      throw new IllegalArgumentException(
          "Invalid trigger time for period " + period + ": " + triggerTime);
    }
  }
}
