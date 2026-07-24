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
import io.openaev.notification.engine.NotificationTriggerCacheService;
import io.openaev.notification.engine.ResolvedNotificationTrigger;
import io.openaev.notification.engine.ResolvedNotifier;
import io.openaev.notification.engine.WebhookTargetValidator;
import io.openaev.rest.exception.ElementNotFoundException;
import io.openaev.service.UserService;
import io.openaev.utils.pagination.SearchPaginationInput;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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
  private final NotificationTriggerCacheService triggerCacheService;
  private final WebhookTargetValidator webhookTargetValidator;

  public Optional<Notifier> findById(@NotBlank final String id) {
    return notifierRepository.findByIdAndTenantId(id, TenantContext.getCurrentTenant());
  }

  @Transactional
  public List<Notifier> findAll() {
    seedBuiltInNotifiers(TenantContext.getCurrentTenant());
    return notifierRepository.findAllByTenantId(TenantContext.getCurrentTenant());
  }

  @Transactional
  public Page<Notifier> search(@NotNull final SearchPaginationInput searchPaginationInput) {
    seedBuiltInNotifiers(TenantContext.getCurrentTenant());
    return buildPaginationJPA(notifierRepository::findAll, searchPaginationInput, Notifier.class);
  }

  @Transactional
  public Notifier create(@NotNull final Notifier notifier) {
    requireCustomizableType(notifier.getType());
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
    requireCustomizableType(input.getType());
    validateConfiguration(input.getType(), input.getConfiguration());
    notifier.setName(input.getName());
    notifier.setDescription(input.getDescription());
    notifier.setType(input.getType());
    notifier.setConfiguration(input.getConfiguration());
    Notifier saved = notifierRepository.save(notifier);
    // Live triggers cache resolved notifier configurations: drop them once the change is visible
    triggerCacheService.invalidateAfterCommit();
    return saved;
  }

  @Transactional
  public void delete(@NotBlank final String id) {
    Notifier notifier =
        findById(id).orElseThrow(() -> new ElementNotFoundException("Notifier not found: " + id));
    if (notifier.isBuiltIn()) {
      throw new IllegalArgumentException("Built-in notifiers cannot be deleted");
    }
    notifierRepository.deleteById(id);
    triggerCacheService.invalidateAfterCommit();
  }

  /**
   * Sends a canned sample notification through the real dispatch pipeline to the current user,
   * mirroring OpenCTI's notifier test endpoint. Deliberately not transactional: webhook/email
   * dispatch is blocking I/O and must not hold a database connection for its duration.
   */
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
    seedBuiltInNotifiers(tenantId);
  }

  // Non-transactional worker so transactional methods of this class can share the logic without
  // a @Transactional self-invocation (which would bypass the Spring proxy).
  private void seedBuiltInNotifiers(final String tenantId) {
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

  // The UI notifier is built-in only (aligned with OpenCTI): custom notifiers are restricted to
  // email and webhook types.
  private void requireCustomizableType(NotifierType type) {
    if (type == NotifierType.UI) {
      throw new IllegalArgumentException(
          "The user interface notifier is built-in: custom notifiers must use the email or webhook type");
    }
  }

  private void validateConfiguration(NotifierType type, Map<String, Object> configuration) {
    if (type == NotifierType.WEBHOOK) {
      Object url = configuration != null ? configuration.get("url") : null;
      if (!(url instanceof String urlValue) || urlValue.isBlank()) {
        throw new IllegalArgumentException("Webhook notifiers require a url in configuration");
      }
      webhookTargetValidator.validateUrl(urlValue);
      Object verb = configuration.get("verb");
      if (verb instanceof String verbValue) {
        webhookTargetValidator.validateVerb(verbValue);
      }
    }
  }
}
