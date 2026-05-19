package io.openaev.aop.audit_log;

import com.fasterxml.jackson.databind.JsonNode;
import io.openaev.aop.AccessControl;
import io.openaev.aop.AccessControlAspect;
import io.openaev.database.model.Base;
import io.openaev.database.model.ResourceType;
import io.openaev.service.LogService;
import io.openaev.utils.HttpReqRespUtils;
import io.openaev.utils.ResourceManagerUtils;
import jakarta.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * AOP aspect that intercepts {@link AccessControl}-annotated controller methods to produce audit
 * log events for CRUD operations.
 *
 * <p>Runs <b>after</b> {@link AccessControlAspect} (which uses {@code @Before}) — the RBAC check
 * has already passed when this aspect's {@code @Around} advice executes.
 *
 * <p>Phase 1: delegates to {@link LogService} for console-only output.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AccessControlAuditLogger {

  @Value("${openaev.audit-logs.stop-the-world:false}")
  private boolean stw;

  private final ResourceManagerUtils resourceManagerUtils;

  private final LogService logService;

  @Async("accessControlAuditLoggerExecutor")
  public CompletableFuture<Boolean> auditEvent(
      String eventScope,
      String eventStatus,
      ResourceType resourceType,
      String resourceId,
      String sourceId,
      ResourceType entityType,
      String entityId,
      String entityName,
      JsonNode entitySnapshot,
      JsonNode inputNode,
      Object result,
      String logUUID) {
    // -- Post-execution: apply child-resource reclassification --
    String parentId = null;
    boolean isHttpDelete = isHttpDelete();

    // if result is not a list or a Base class, then get it from the DB
    if (!isHttpDelete
        && entitySnapshot != null
        && entityId != null
        && entityType != null
        && ("update".equals(eventScope)
            || "create".equals(eventScope)
            || "duplicate".equals(eventScope))
        && !(result instanceof Collection<?> || result instanceof Base)) {
      result = resourceManagerUtils.snapshotResourceEntity(entityType, entityId);
    }

    if (entitySnapshot != null && isHttpDelete) {
      // Case 1: entity deletion — detected before proceed, entity now gone
      eventScope = "delete";
      parentId = resourceId;
      resourceType = entityType;
      resourceId = entityId;
      entityName = entityName != null ? entityName : entityId;
    } else if ("update".equals(eventScope) && result instanceof Base resultEntity) {
      Class<?> expectedParentClass = ResourceManagerUtils.getClassByResource(resourceType);

      if (expectedParentClass != null && !expectedParentClass.isInstance(resultEntity)) {
        parentId = resourceId;
        resourceId = resultEntity.getId();
        entityName = extractEntityName(resultEntity);
        ResourceType resultType = ResourceManagerUtils.getResourceByClass(resultEntity.getClass());

        if (resultType != null) {
          resourceType = resultType;
        }

        if (entitySnapshot != null) {
          // Case 2: Child update — child existed before proceed (pre-snapshot available for diff)
          eventScope = "update";
        } else {
          // Case 3: Child creation — no child ID in path variables, new entity
          eventScope = "create";
        }
      }
    }

    if ("create".equals(eventScope) && parentId == null && result instanceof Base createdEntity) {
      resourceId = createdEntity.getId();
      entityName = extractEntityName(createdEntity);
    }
    // For duplicates: extract the new entity ID and name from the return value,
    // and record the source entity ID so the audit trail links back to the original.
    else if ("duplicate".equals(eventScope) && result instanceof Base duplicatedEntity) {
      resourceId = duplicatedEntity.getId();
      entityName = extractEntityName(duplicatedEntity);
    }
    // Case 4: Bulk child creation — Action.WRITE on a parent that returns a Collection of child
    // entities (e.g. POST /scenarios/{id}/injects/bulk). Log one create event per child.
    else if ("update".equals(eventScope)
        && result instanceof Collection<?> collection
        && !collection.isEmpty()) {
      List<Base> childEntities =
          collection.stream().filter(Base.class::isInstance).map(Base.class::cast).toList();

      if (!childEntities.isEmpty()) {
        Class<?> expectedParentClass = ResourceManagerUtils.getClassByResource(resourceType);

        // Verify these are truly child entities, not the parent type itself
        if (expectedParentClass != null
            && !expectedParentClass.isInstance(childEntities.getFirst())) {
          ResourceType childType =
              ResourceManagerUtils.getResourceByClass(childEntities.getFirst().getClass());
          // Build per-child input nodes from the request body array (if available)
          List<JsonNode> perChildInputs = List.of();

          if (inputNode != null
              && inputNode.isArray()
              && inputNode.size() == childEntities.size()) {
            perChildInputs = new java.util.ArrayList<>();

            for (JsonNode element : inputNode) {
              perChildInputs.add(element);
            }
          }

          List<CompletableFuture<Boolean>> futures = new ArrayList<>();

          for (int i = 0; i < childEntities.size(); i++) {
            Base child = childEntities.get(i);
            JsonNode childInput = i < perChildInputs.size() ? perChildInputs.get(i) : null;
            String childId = child.getId();
            String childName = extractEntityName(child);

            CompletableFuture<Boolean> subFuture =
                logMutationEvent(
                    "create",
                    eventStatus,
                    childType != null ? childType : resourceType,
                    childId,
                    childInput,
                    null,
                    childName,
                    resourceId,
                    null,
                    logUUID);

            futures.add(subFuture);
          }

          CompletableFuture<Void> all =
              CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
          return all.thenApply(
              v -> futures.stream().map(CompletableFuture::join).allMatch(Boolean::booleanValue));
        }
      }
    }

    // Extract name from result if not already set
    if (entityName == null && result instanceof Base baseResult) {
      entityName = extractEntityName(baseResult);
    }

    return logMutationEventIfDifferentSnapshots(
        eventScope,
        eventStatus,
        resourceType,
        resourceId,
        inputNode,
        entitySnapshot,
        entityName,
        parentId,
        sourceId,
        logUUID);
  }

  public void prepareLogFailure() {
    if (stw) {
      // TODO AUDIT: implements the stop the world
    } else {
      log.warn("[AUDIT] Audit logging failed, but continuing without blocking the operation.");
    }
  }

  /** Wraps the audit service call in try/catch — audit must never break the business flow. */
  @Async("accessControlAuditLoggerExecutor")
  public CompletableFuture<Boolean> logMutationEventIfDifferentSnapshots(
      String eventScope,
      String eventStatus,
      ResourceType resourceType,
      String entityId,
      JsonNode newSnapshot,
      JsonNode oldSnapshot,
      String entityName,
      String parentId,
      String sourceId,
      String logUUID) {
    boolean status = false;

    try {
      status =
          logService.logMutationEventIfDifferentSnapshots(
              eventScope,
              eventStatus,
              resourceType,
              entityId,
              newSnapshot,
              oldSnapshot,
              entityName,
              parentId,
              sourceId,
              Level.WARNING,
              LogService.AuditLogType.AUDIT,
              logUUID);
    } catch (Exception e) {
      log.warn("[AUDIT] Audit logging failed (non-blocking): {}", e.getMessage(), e);
    }

    if (!status) {
      prepareLogFailure();
    }

    return CompletableFuture.completedFuture(status);
  }

  /** Wraps the audit service call in try/catch — audit must never break the business flow. */
  @Async("accessControlAuditLoggerExecutor")
  public CompletableFuture<Boolean> logAuthEvent(
      String eventScope, String eventStatus, String provider, String reason, String logUUID) {
    boolean status = false;

    try {
      status =
          logService.logAuthEvent(
              eventScope,
              eventStatus,
              provider,
              reason,
              Level.WARNING,
              LogService.AuditLogType.AUDIT,
              logUUID);
    } catch (Exception e) {
      log.warn("[AUDIT] Audit auth logging failed (non-blocking): {}", e.getMessage(), e);
    }

    if (!status) {
      prepareLogFailure();
    }

    return CompletableFuture.completedFuture(status);
  }

  /** Wraps the audit service call in try/catch — audit must never break the business flow. */
  @Async("accessControlAuditLoggerExecutor")
  public CompletableFuture<Boolean> logMutationEvent(
      String eventScope,
      String eventStatus,
      ResourceType resourceType,
      String entityId,
      JsonNode input,
      JsonNode oldValue,
      String entityName,
      String parentId,
      String sourceId,
      String logUUID) {
    boolean status = false;

    try {
      status =
          logService.logMutationEvent(
              eventScope,
              eventStatus,
              resourceType,
              entityId,
              input,
              oldValue,
              entityName,
              parentId,
              sourceId,
              Level.WARNING,
              LogService.AuditLogType.AUDIT,
              logUUID);
    } catch (Exception e) {
      log.warn("[AUDIT] Audit logging failed (non-blocking): {}", e.getMessage(), e);
    }

    if (!status) {
      prepareLogFailure();
    }

    return CompletableFuture.completedFuture(status);
  }

  /** Extracts a human-readable name from a Base entity via reflection. */
  private String extractEntityName(Base entity) {
    // Try common name accessors in order of precedence
    for (String methodName : new String[] {"getName", "getTitle"}) {
      try {
        var method = entity.getClass().getMethod(methodName);
        Object value = method.invoke(entity);
        if (value != null) {
          return value.toString();
        }
      } catch (NoSuchMethodException e) {
        // This accessor doesn't exist — try the next one
      } catch (Exception e) {
        return entity.getId();
      }
    }
    return entity.getId();
  }

  /** Returns {@code true} if the current HTTP request method is DELETE. */
  private boolean isHttpDelete() {
    HttpServletRequest request = HttpReqRespUtils.getCurrentRequest();
    String method = HttpReqRespUtils.extractMethod(request);
    return method != null && "DELETE".equalsIgnoreCase(method);
  }
}
