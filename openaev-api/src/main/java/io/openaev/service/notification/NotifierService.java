package io.openaev.service.notification;

import static io.openaev.utils.pagination.PaginationUtils.buildPaginationJPA;

import io.openaev.context.TenantContext;
import io.openaev.database.model.NotificationTriggerEventType;
import io.openaev.database.model.NotificationTriggerType;
import io.openaev.database.model.Notifier;
import io.openaev.database.model.NotifierType;
import io.openaev.database.model.ResourceType;
import io.openaev.database.model.Tenant;
import io.openaev.database.repository.NotifierRepository;
import io.openaev.notification.engine.NotificationContent;
import io.openaev.notification.engine.NotificationDispatchService;
import io.openaev.notification.engine.ResolvedNotificationTrigger;
import io.openaev.notification.engine.ResolvedNotifier;
import io.openaev.rest.exception.ElementNotFoundException;
import io.openaev.service.UserService;
import io.openaev.utils.pagination.SearchPaginationInput;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
public class NotifierService {

  public static final String BUILT_IN_UI_NAME = "User interface";
  public static final String BUILT_IN_EMAIL_NAME = "Default mailer";

  private final NotifierRepository notifierRepository;
  private final NotificationDispatchService notificationDispatchService;
  private final UserService userService;

  public Optional<Notifier> findById(@NotBlank final String id) {
    return notifierRepository.findByIdAndTenantId(id, TenantContext.getCurrentTenant());
  }

  @Transactional
  public List<Notifier> findAll() {
    ensureBuiltInNotifiers(TenantContext.getCurrentTenant());
    return notifierRepository.findAllByTenantId(TenantContext.getCurrentTenant());
  }

  @Transactional
  public Page<Notifier> search(@NotNull final SearchPaginationInput searchPaginationInput) {
    ensureBuiltInNotifiers(TenantContext.getCurrentTenant());
    return buildPaginationJPA(notifierRepository::findAll, searchPaginationInput, Notifier.class);
  }

  @Transactional
  public Notifier create(@NotNull final Notifier notifier) {
    validateConfiguration(notifier.getType(), notifier.getConfiguration());
    notifier.setBuiltIn(false);
    return notifierRepository.save(notifier);
  }

  @Transactional
  public Notifier update(@NotBlank final String id, @NotNull final Notifier input) {
    Notifier notifier =
        findById(id).orElseThrow(() -> new ElementNotFoundException("Notifier not found: " + id));
    if (notifier.isBuiltIn()) {
      throw new IllegalArgumentException("Built-in notifiers cannot be modified");
    }
    validateConfiguration(input.getType(), input.getConfiguration());
    notifier.setName(input.getName());
    notifier.setDescription(input.getDescription());
    notifier.setType(input.getType());
    notifier.setConfiguration(input.getConfiguration());
    return notifierRepository.save(notifier);
  }

  @Transactional
  public void delete(@NotBlank final String id) {
    Notifier notifier =
        findById(id).orElseThrow(() -> new ElementNotFoundException("Notifier not found: " + id));
    if (notifier.isBuiltIn()) {
      throw new IllegalArgumentException("Built-in notifiers cannot be deleted");
    }
    notifierRepository.deleteById(id);
  }

  /**
   * Sends a canned sample notification through the real dispatch pipeline to the current user,
   * mirroring OpenCTI's notifier test endpoint.
   */
  @Transactional
  public void test(@NotBlank final String id) {
    Notifier notifier =
        findById(id).orElseThrow(() -> new ElementNotFoundException("Notifier not found: " + id));
    String userId = userService.currentUser().getId();
    ResolvedNotificationTrigger sampleTrigger =
        new ResolvedNotificationTrigger(
            "test",
            "Sample notification",
            NotificationTriggerType.LIVE,
            ResourceType.SCENARIO,
            Set.of(NotificationTriggerEventType.CREATE),
            null,
            null,
            null,
            null,
            List.of(),
            TenantContext.getCurrentTenant(),
            List.of(userId),
            List.of(ResolvedNotifier.from(notifier)));
    NotificationContent.Group group =
        new NotificationContent.Group(
            "Sample notification",
            List.of(
                new NotificationContent.Event(
                    NotificationTriggerEventType.CREATE,
                    "[scenario] Sample scenario created",
                    ResourceType.SCENARIO,
                    "sample-scenario-id")));
    notificationDispatchService.dispatch(
        sampleTrigger, NotificationTriggerType.LIVE, List.of(userId), List.of(group));
  }

  /** Idempotently creates the built-in UI and email notifiers for the given tenant. */
  @Transactional
  public void ensureBuiltInNotifiers(@NotBlank final String tenantId) {
    if (notifierRepository
        .findFirstByTenantIdAndTypeAndBuiltInTrue(tenantId, NotifierType.UI)
        .isEmpty()) {
      Notifier ui = new Notifier();
      ui.setName(BUILT_IN_UI_NAME);
      ui.setDescription("Built-in in-app notifier");
      ui.setType(NotifierType.UI);
      ui.setBuiltIn(true);
      ui.setTenant(new Tenant(tenantId));
      notifierRepository.save(ui);
    }
    if (notifierRepository
        .findFirstByTenantIdAndTypeAndBuiltInTrue(tenantId, NotifierType.EMAIL)
        .isEmpty()) {
      Notifier email = new Notifier();
      email.setName(BUILT_IN_EMAIL_NAME);
      email.setDescription("Built-in email notifier");
      email.setType(NotifierType.EMAIL);
      email.setBuiltIn(true);
      email.setTenant(new Tenant(tenantId));
      notifierRepository.save(email);
    }
  }

  private void validateConfiguration(NotifierType type, Map<String, Object> configuration) {
    if (type == NotifierType.WEBHOOK) {
      Object url = configuration != null ? configuration.get("url") : null;
      if (!(url instanceof String urlValue) || urlValue.isBlank()) {
        throw new IllegalArgumentException("Webhook notifiers require a url in configuration");
      }
      String scheme = URI.create(urlValue).getScheme();
      if (scheme == null
          || (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme))) {
        throw new IllegalArgumentException("Webhook notifier url must be http(s)");
      }
    }
  }
}
