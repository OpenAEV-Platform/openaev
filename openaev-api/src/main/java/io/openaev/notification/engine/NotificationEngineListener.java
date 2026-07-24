package io.openaev.notification.engine;

import static io.openaev.database.audit.ModelBaseListener.DATA_DELETE;
import static io.openaev.database.audit.ModelBaseListener.DATA_PERSIST;
import static io.openaev.database.audit.ModelBaseListener.DATA_UPDATE;

import com.fasterxml.jackson.databind.JsonNode;
import io.openaev.database.audit.BaseEvent;
import io.openaev.database.model.Base;
import io.openaev.database.model.DualScopeBase;
import io.openaev.database.model.NotificationTriggerEventType;
import io.openaev.database.model.Tenant;
import io.openaev.database.model.TenantBase;
import io.openaev.database.model.TenantIdBase;
import io.openaev.database.model.User;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Entry point of the notifications engine: consumes the platform-wide entity lifecycle events
 * published by {@code ModelBaseListener} (the same bus feeding SSE) and forwards catalog-relevant
 * events to the {@link NotificationEngineService} after commit.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class NotificationEngineListener {

  private static final Map<String, NotificationTriggerEventType> EVENT_TYPE_MAPPING =
      Map.of(
          DATA_PERSIST, NotificationTriggerEventType.CREATE,
          DATA_UPDATE, NotificationTriggerEventType.UPDATE,
          DATA_DELETE, NotificationTriggerEventType.DELETE);

  private final NotificationEngineService notificationEngineService;

  @Async("notificationEngineExecutor")
  @TransactionalEventListener
  public void onEntityEvent(BaseEvent event) {
    try {
      NotificationTriggerEventType eventType = EVENT_TYPE_MAPPING.get(event.getType());
      if (eventType == null || event.getInstance() == null) {
        return;
      }
      Optional<NotificationResourceCatalog> entry =
          NotificationResourceCatalog.fromEntity(event.getInstance());
      if (entry.isEmpty()) {
        return;
      }
      String entityId = event.getInstance().getId();
      String tenantId = resolveTenantId(event.getInstance());
      String label = resolveLabel(event);
      notificationEngineService.handleEvent(entry.get(), entityId, tenantId, eventType, label);
    } catch (Exception e) {
      log.error("Notification engine failed to process entity event", e);
    }
  }

  private String resolveTenantId(Base instance) {
    if (instance instanceof TenantBase tenantScoped) {
      Tenant tenant = tenantScoped.getTenant();
      return tenant != null ? tenant.getId() : null;
    }
    if (instance instanceof DualScopeBase dualScope) {
      Tenant tenant = dualScope.getTenant();
      return tenant != null ? tenant.getId() : null;
    }
    if (instance instanceof TenantIdBase tenantIdScoped) {
      return tenantIdScoped.getTenantId();
    }
    return null;
  }

  /**
   * Extracts a human-readable label from the event's serialized entity snapshot: first {@code
   * *_name} / {@code *_title} / {@code *_email} property, falling back to the entity id.
   */
  private String resolveLabel(BaseEvent event) {
    if (event.getInstance() instanceof User user) {
      return user.getNameOrEmail();
    }
    JsonNode data = event.getInstanceData();
    if (data != null && data.isObject()) {
      for (String suffix : new String[] {"_name", "_title", "_subject", "_email", "_external_id"}) {
        Iterator<String> fields = data.fieldNames();
        while (fields.hasNext()) {
          String field = fields.next();
          JsonNode value = data.get(field);
          if (field.endsWith(suffix)
              && value != null
              && value.isTextual()
              && !value.asText().isBlank()) {
            return value.asText();
          }
        }
      }
    }
    return event.getInstance().getId();
  }
}
