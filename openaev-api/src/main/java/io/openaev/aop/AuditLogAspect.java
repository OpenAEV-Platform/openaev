package io.openaev.aop;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.openaev.database.model.Action;
import io.openaev.database.model.Base;
import io.openaev.database.model.ResourceType;
import io.openaev.service.audit.AuditLogService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.lang.annotation.Annotation;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.SimpleEvaluationContext;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * AOP aspect that intercepts {@link AccessControl}-annotated controller methods to produce audit
 * log events for CRUD operations.
 *
 * <p>Runs <b>after</b> {@link AccessControlAspect} (which uses {@code @Before}) — the RBAC check
 * has already passed when this aspect's {@code @Around} advice executes.
 *
 * <p>Phase 1: delegates to {@link AuditLogService} for console-only output.
 */
@Aspect
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
@RequiredArgsConstructor
@Slf4j
public class AuditLogAspect {

  private static final Map<ResourceType, Class<?>> ENTITY_CLASS_MAP = buildEntityClassMap();

  private final AuditLogService auditLogService;
  private final ObjectMapper objectMapper;

  @PersistenceContext private EntityManager entityManager;

  private final ExpressionParser parser = new SpelExpressionParser();

  @Value("${openaev.audit-logs.enabled:true}")
  private boolean enabled;

  @Value("${openaev.audit-logs.log-reads:false}")
  private boolean logReads;

  @Around("@annotation(accessControl)")
  public Object auditAround(ProceedingJoinPoint joinPoint, AccessControl accessControl)
      throws Throwable {
    if (!enabled) {
      return joinPoint.proceed();
    }

    Action action = accessControl.actionPerformed();
    ResourceType resourceType = accessControl.resourceType();

    // Skip actions we don't audit
    if (shouldSkip(action)) {
      return joinPoint.proceed();
    }

    String eventScope = mapEventScope(action);
    String resourceId = resolveResourceId(joinPoint, accessControl);

    // For updates/deletes: pre-fetch entity state before the mutation
    JsonNode oldEntitySnapshot = null;
    String entityName = null;
    if (("update".equals(eventScope) || "delete".equals(eventScope))
        && !resourceId.isEmpty()) {
      oldEntitySnapshot = snapshotEntity(resourceType, resourceId);
      entityName = extractNameFromSnapshot(oldEntitySnapshot);
    }

    // Capture the input DTO for create/update
    JsonNode inputNode = null;
    if ("create".equals(eventScope) || "update".equals(eventScope)) {
      Object requestBody = findRequestBody(joinPoint);
      if (requestBody != null) {
        inputNode = objectMapper.valueToTree(requestBody);
      }
    }

    // Execute the business operation
    Object result;
    String eventStatus;
    try {
      result = joinPoint.proceed();
      eventStatus = "success";
    } catch (Throwable ex) {
      eventStatus = "error";
      // Still log the audit event, then re-throw
      logMutationSafely(
          eventScope, eventStatus, resourceType, resourceId, inputNode, null, entityName);
      throw ex;
    }

    // For creates, extract the entity ID and name from the return value
    if ("create".equals(eventScope) && result instanceof Base createdEntity) {
      resourceId = createdEntity.getId();
      entityName = extractEntityName(createdEntity);
    }

    // For updates: compute diff between old and new values
    JsonNode diffInput = null;
    JsonNode diffOldValue = null;
    if ("update".equals(eventScope) && oldEntitySnapshot != null && inputNode != null) {
      DiffResult diff = computeDiff(oldEntitySnapshot, inputNode);
      diffInput = diff.newValues();
      diffOldValue = diff.oldValues();
      // If no changes detected, still log the full input
      if (diffInput == null || diffInput.isEmpty()) {
        diffInput = inputNode;
        diffOldValue = null;
      }
    } else if ("create".equals(eventScope)) {
      diffInput = inputNode;
    }

    // Extract name from result if not already set
    if (entityName == null && result instanceof Base baseResult) {
      entityName = extractEntityName(baseResult);
    }

    logMutationSafely(
        eventScope, eventStatus, resourceType, resourceId, diffInput, diffOldValue, entityName);

    return result;
  }

  // -- Helpers --

  private boolean shouldSkip(Action action) {
    return switch (action) {
      case CREATE, WRITE, DELETE -> false;
      case READ, SEARCH -> !logReads;
      default -> true; // SKIP_RBAC, DUPLICATE, LAUNCH, PROCESS
    };
  }

  private String mapEventScope(Action action) {
    return switch (action) {
      case CREATE -> "create";
      case WRITE -> "update";
      case DELETE -> "delete";
      case READ, SEARCH -> "read";
      default -> "unknown";
    };
  }

  /** Resolves the resource ID from the annotation's SpEL expression. */
  private String resolveResourceId(ProceedingJoinPoint joinPoint, AccessControl accessControl) {
    if (accessControl.resourceId().isEmpty()) {
      return "";
    }
    try {
      MethodSignature signature = (MethodSignature) joinPoint.getSignature();
      String[] paramNames = signature.getParameterNames();
      Object[] args = joinPoint.getArgs();

      EvaluationContext ctx = SimpleEvaluationContext.forReadOnlyDataBinding().build();
      if (paramNames != null) {
        for (int i = 0; i < paramNames.length; i++) {
          ctx.setVariable(paramNames[i], args[i]);
        }
      }
      Object value = parser.parseExpression(accessControl.resourceId()).getValue(ctx);
      return value != null ? value.toString() : "";
    } catch (Exception e) {
      log.debug("[AUDIT] Failed to resolve resourceId SpEL: {}", e.getMessage());
      return "";
    }
  }

  /** Finds the first method argument annotated with {@code @RequestBody}. */
  private Object findRequestBody(ProceedingJoinPoint joinPoint) {
    try {
      MethodSignature signature = (MethodSignature) joinPoint.getSignature();
      Annotation[][] paramAnnotations = signature.getMethod().getParameterAnnotations();
      Object[] args = joinPoint.getArgs();
      for (int i = 0; i < paramAnnotations.length; i++) {
        for (Annotation ann : paramAnnotations[i]) {
          if (ann instanceof RequestBody) {
            return args[i];
          }
        }
      }
    } catch (Exception e) {
      log.debug("[AUDIT] Failed to find @RequestBody argument: {}", e.getMessage());
    }
    return null;
  }

  /**
   * Snapshots the current entity state from the database before mutation. Returns a JsonNode of the
   * entity serialized via Jackson, or null if not found.
   */
  private JsonNode snapshotEntity(ResourceType resourceType, String entityId) {
    try {
      Class<?> entityClass = ENTITY_CLASS_MAP.get(resourceType);
      if (entityClass == null) {
        log.debug("[AUDIT] No entity class mapped for ResourceType: {}", resourceType);
        return null;
      }
      Object entity = entityManager.find(entityClass, entityId);
      if (entity == null) {
        return null;
      }
      return objectMapper.valueToTree(entity);
    } catch (Exception e) {
      log.debug("[AUDIT] Failed to snapshot entity {}/{}: {}", resourceType, entityId, e.getMessage());
      return null;
    }
  }

  /** Extracts a human-readable name from a Base entity via reflection. */
  private String extractEntityName(Base entity) {
    try {
      var method = entity.getClass().getMethod("getName");
      Object name = method.invoke(entity);
      return name != null ? name.toString() : null;
    } catch (NoSuchMethodException e) {
      // Entity has no getName() — fall back to ID
      return entity.getId();
    } catch (Exception e) {
      return entity.getId();
    }
  }

  /** Extracts a name from a snapshotted JSON node. */
  private String extractNameFromSnapshot(JsonNode snapshot) {
    if (snapshot == null) {
      return null;
    }
    // Try common name fields in order of precedence
    for (String field :
        new String[] {
          "scenario_name", "exercise_name", "inject_title", "user_firstname", "name"
        }) {
      JsonNode node = snapshot.get(field);
      if (node != null && node.isTextual()) {
        return node.asText();
      }
    }
    return null;
  }

  /**
   * Computes a diff between the old entity snapshot and the new input DTO. Returns only the fields
   * that actually changed.
   */
  private DiffResult computeDiff(JsonNode oldSnapshot, JsonNode newInput) {
    ObjectNode changedNew = objectMapper.createObjectNode();
    ObjectNode changedOld = objectMapper.createObjectNode();

    for (Map.Entry<String, JsonNode> entry : newInput.properties()) {
      String fieldName = entry.getKey();
      JsonNode newValue = entry.getValue();
      JsonNode oldValue = oldSnapshot.get(fieldName);

      // If the old snapshot doesn't have this field, or the values differ, include it
      if (oldValue == null || !oldValue.equals(newValue)) {
        changedNew.set(fieldName, newValue);
        if (oldValue != null) {
          changedOld.set(fieldName, oldValue);
        }
      }
    }

    if (changedNew.isEmpty()) {
      return new DiffResult(null, null);
    }
    return new DiffResult(changedNew, changedOld.isEmpty() ? null : changedOld);
  }

  private record DiffResult(JsonNode newValues, JsonNode oldValues) {}

  /** Wraps the audit service call in try/catch — audit must never break the business flow. */
  private void logMutationSafely(
      String eventScope,
      String eventStatus,
      ResourceType resourceType,
      String entityId,
      JsonNode input,
      JsonNode oldValue,
      String entityName) {
    try {
      auditLogService.logMutationEvent(
          eventScope, eventStatus, resourceType, entityId, input, oldValue, entityName);
    } catch (Exception e) {
      log.warn("[AUDIT] Audit logging failed (non-blocking): {}", e.getMessage(), e);
    }
  }

  /**
   * Builds the ResourceType → JPA entity class mapping. Only includes entity types that have a
   * direct 1:1 JPA entity. This map is used for {@code EntityManager.find()} pre-fetch.
   */
  private static Map<ResourceType, Class<?>> buildEntityClassMap() {
    try {
      return Map.ofEntries(
          Map.entry(ResourceType.SCENARIO, Class.forName("io.openaev.database.model.Scenario")),
          Map.entry(ResourceType.SIMULATION, Class.forName("io.openaev.database.model.Exercise")),
          Map.entry(ResourceType.USER, Class.forName("io.openaev.database.model.User")),
          Map.entry(ResourceType.TEAM, Class.forName("io.openaev.database.model.Team")),
          Map.entry(ResourceType.INJECT, Class.forName("io.openaev.database.model.Inject")),
          Map.entry(ResourceType.DOCUMENT, Class.forName("io.openaev.database.model.Document")),
          Map.entry(ResourceType.TAG, Class.forName("io.openaev.database.model.Tag")),
          Map.entry(ResourceType.CHANNEL, Class.forName("io.openaev.database.model.Channel")),
          Map.entry(ResourceType.CHALLENGE, Class.forName("io.openaev.database.model.Challenge")),
          Map.entry(ResourceType.PAYLOAD, Class.forName("io.openaev.database.model.Payload")),
          Map.entry(
              ResourceType.ASSET_GROUP,
              Class.forName("io.openaev.database.model.AssetGroup")),
          Map.entry(ResourceType.OBJECTIVE, Class.forName("io.openaev.database.model.Objective")),
          Map.entry(
              ResourceType.ORGANIZATION,
              Class.forName("io.openaev.database.model.Organization")),
          Map.entry(
              ResourceType.KILL_CHAIN_PHASE,
              Class.forName("io.openaev.database.model.KillChainPhase")),
          Map.entry(
              ResourceType.ATTACK_PATTERN,
              Class.forName("io.openaev.database.model.AttackPattern")));
    } catch (ClassNotFoundException e) {
      log.error("[AUDIT] Failed to build entity class map: {}", e.getMessage(), e);
      return Map.of();
    }
  }
}

