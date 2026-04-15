package io.openaev.aop;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.openaev.database.model.Action;
import io.openaev.database.model.Base;
import io.openaev.database.model.ResourceType;
import io.openaev.service.audit.AuditLogService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.servlet.http.HttpServletRequest;
import java.lang.annotation.Annotation;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

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

  /** Reverse lookup: JPA entity class → ResourceType (built from {@link #ENTITY_CLASS_MAP}). */
  private static final Map<Class<?>, ResourceType> REVERSE_ENTITY_CLASS_MAP =
      buildReverseEntityClassMap();

  /**
   * Requests from automated clients are excluded from audit logging. Matches User-Agent headers
   * like {@code openaev-agent/2.3.0} (endpoint agents doing heartbeats/job polling) and {@code
   * pyoaev/2.2.1} (Python client used by collectors such as Atomic Red Team, AWS Resources, etc.).
   */
  private static final Pattern AGENT_USER_AGENT_PATTERN =
      Pattern.compile("^(openaev-agent|pyoaev)/", Pattern.CASE_INSENSITIVE);

  /**
   * Request URI prefixes for machine-to-machine endpoints that should be excluded from audit
   * logging. XTM Composer calls (health checks, status updates, log pushes) happen frequently and
   * are not user-initiated actions.
   */
  private static final String XTM_COMPOSER_URI_PREFIX = "/api/xtm-composer";

  /**
   * DTO metadata fields that are never actual entity attributes. Skipped during diff computation.
   * {@code type} is a Jackson polymorphic type discriminator present in many input DTOs.
   */
  private static final Set<String> DIFF_SKIP_FIELDS = Set.of("type");

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

    // Skip automated requests — not user-initiated actions
    if (isAutomatedRequest()) {
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

    // -- Pre-execution: child-resource detection --
    // If Action.WRITE on a parent, scan for child entity IDs in other @PathVariable params.
    // This covers child create (no child found), update (child found), and delete (HTTP DELETE).
    // Must happen BEFORE proceed() so we can snapshot the child before it's modified/deleted.
    ChildResourceInfo childInfo = null;
    if ("update".equals(eventScope)) {
      childInfo = detectChildResource(joinPoint, resourceId);
    }

    // For updates/deletes/status_change: pre-fetch entity state before the mutation
    // For duplicates: snapshot source entity to capture its name for the audit message
    JsonNode oldEntitySnapshot = null;
    String entityName = null;
    String sourceId = null;
    if (childInfo != null) {
      // Child operation: snapshot the child, not the parent
      oldEntitySnapshot = childInfo.snapshot();
      entityName = extractNameFromSnapshot(oldEntitySnapshot);
    } else if (("update".equals(eventScope)
            || "delete".equals(eventScope)
            || "status_change".equals(eventScope))
        && !resourceId.isEmpty()) {
      oldEntitySnapshot = snapshotEntity(resourceType, resourceId);
      entityName = extractNameFromSnapshot(oldEntitySnapshot);
    } else if ("duplicate".equals(eventScope) && !resourceId.isEmpty()) {
      // For duplicates: remember the source entity ID and resolve its name
      sourceId = resourceId;
      JsonNode sourceSnapshot = snapshotEntity(resourceType, resourceId);
      entityName = extractNameFromSnapshot(sourceSnapshot);
    }

    // Capture the input DTO for create/update/status_change
    JsonNode inputNode = null;
    if ("create".equals(eventScope)
        || "update".equals(eventScope)
        || "status_change".equals(eventScope)) {
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
          eventScope,
          eventStatus,
          resourceType,
          resourceId,
          inputNode,
          null,
          entityName,
          null,
          sourceId);
      throw ex;
    }

    // -- Post-execution: apply child-resource reclassification --
    String parentId = null;

    if (childInfo != null && isHttpDelete()) {
      // Case 1: Child deletion — detected before proceed, entity now gone
      parentId = resourceId;
      eventScope = "delete";
      resourceType = childInfo.resourceType();
      resourceId = childInfo.entityId();
      entityName = entityName != null ? entityName : childInfo.entityId();
    } else if ("update".equals(eventScope) && result instanceof Base resultEntity) {
      Class<?> expectedParentClass = ENTITY_CLASS_MAP.get(resourceType);
      if (expectedParentClass != null && !expectedParentClass.isInstance(resultEntity)) {
        parentId = resourceId;
        ResourceType childType = REVERSE_ENTITY_CLASS_MAP.get(resultEntity.getClass());
        if (childType != null) {
          resourceType = childType;
        }
        resourceId = resultEntity.getId();
        entityName = extractEntityName(resultEntity);

        if (childInfo != null) {
          // Case 2: Child update — child existed before proceed (pre-snapshot available for diff)
          eventScope = "update";
        } else {
          // Case 3: Child creation — no child ID in path variables, new entity
          eventScope = "create";
          oldEntitySnapshot = null;
        }
      }
    }

    // Case 4: Bulk child creation — Action.WRITE on a parent that returns a Collection of child
    // entities (e.g. POST /scenarios/{id}/injects/bulk). Log one create event per child.
    if ("update".equals(eventScope)
        && result instanceof Collection<?> collection
        && !collection.isEmpty()) {
      List<Base> childEntities =
          collection.stream().filter(Base.class::isInstance).map(Base.class::cast).toList();
      if (!childEntities.isEmpty()) {
        Class<?> expectedParentClass = ENTITY_CLASS_MAP.get(resourceType);
        // Verify these are truly child entities, not the parent type itself
        if (expectedParentClass != null
            && !expectedParentClass.isInstance(childEntities.getFirst())) {
          ResourceType childType =
              REVERSE_ENTITY_CLASS_MAP.get(childEntities.getFirst().getClass());
          // Build per-child input nodes from the request body array (if available)
          List<JsonNode> perChildInputs = List.of();
          if (inputNode != null
              && inputNode.isArray()
              && inputNode.size() == childEntities.size()) {
            perChildInputs = new java.util.ArrayList<>();
            for (JsonNode element : inputNode) {
              perChildInputs.add(stripInsignificantValues(element));
            }
          }
          for (int i = 0; i < childEntities.size(); i++) {
            Base child = childEntities.get(i);
            JsonNode childInput = i < perChildInputs.size() ? perChildInputs.get(i) : null;
            logMutationSafely(
                "create",
                eventStatus,
                childType != null ? childType : resourceType,
                child.getId(),
                childInput,
                null,
                extractEntityName(child),
                resourceId,
                null);
          }
          return result;
        }
      }
    }
    if ("create".equals(eventScope) && parentId == null && result instanceof Base createdEntity) {
      resourceId = createdEntity.getId();
      entityName = extractEntityName(createdEntity);
    }

    // For duplicates: extract the new entity ID and name from the return value,
    // and record the source entity ID so the audit trail links back to the original.
    if ("duplicate".equals(eventScope) && result instanceof Base duplicatedEntity) {
      resourceId = duplicatedEntity.getId();
      entityName = extractEntityName(duplicatedEntity);
    }

    // For updates: compute diff between old and new values
    JsonNode diffInput = null;
    JsonNode diffOldValue = null;
    if ("update".equals(eventScope) && oldEntitySnapshot != null && inputNode != null) {
      DiffResult diff = computeDiff(oldEntitySnapshot, inputNode);
      diffInput = diff.newValues();
      diffOldValue = diff.oldValues();
      if (diffInput == null || diffInput.isEmpty()) {
        // No meaningful changes detected — skip the audit event (no-op update)
        return result;
      }
    } else if ("update".equals(eventScope) && inputNode != null) {
      // No old snapshot available — log input as-is (without diff computation)
      diffInput = stripInsignificantValues(inputNode);
    } else if ("status_change".equals(eventScope)) {
      // For status changes with a request body (exercise status, scenario recurrence):
      // reuse the diff engine to extract only the changed fields and their old values.
      // For instant launch (no request body): synthesize a minimal input, no old_value.
      if (inputNode != null && oldEntitySnapshot != null) {
        DiffResult diff = computeDiff(oldEntitySnapshot, inputNode);
        diffInput = diff.newValues() != null ? diff.newValues() : inputNode;
        diffOldValue = diff.oldValues();
      } else if (inputNode != null) {
        diffInput = inputNode;
      } else {
        // No request body (e.g. POST /scenarios/{id}/exercise/running)
        ObjectNode syntheticInput = objectMapper.createObjectNode();
        syntheticInput.put("action", "launch");
        diffInput = syntheticInput;
      }
    } else if ("create".equals(eventScope)) {
      diffInput = inputNode != null ? stripInsignificantValues(inputNode) : null;
    }

    // Extract name from result if not already set
    if (entityName == null && result instanceof Base baseResult) {
      entityName = extractEntityName(baseResult);
    }

    logMutationSafely(
        eventScope,
        eventStatus,
        resourceType,
        resourceId,
        diffInput,
        diffOldValue,
        entityName,
        parentId,
        sourceId);

    return result;
  }

  // -- Helpers --

  /**
   * Returns {@code true} if the current request originates from an automated client. This covers:
   *
   * <ul>
   *   <li>OpenAEV endpoint agents ({@code openaev-agent/...}) — heartbeats, job polling
   *   <li>Python client ({@code pyoaev/...}) — collectors (Atomic Red Team, AWS Resources, etc.)
   *   <li>XTM Composer callbacks — health checks, status updates, log pushes
   * </ul>
   *
   * These automated calls happen frequently and would flood the audit log with noise.
   */
  private boolean isAutomatedRequest() {
    try {
      ServletRequestAttributes attrs =
          (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
      if (attrs == null) {
        return false;
      }
      HttpServletRequest request = attrs.getRequest();

      // Check User-Agent for known automated clients
      String userAgent = request.getHeader("User-Agent");
      if (userAgent != null && AGENT_USER_AGENT_PATTERN.matcher(userAgent).find()) {
        return true;
      }

      // Check request URI for machine-to-machine endpoints
      String requestUri = request.getRequestURI();
      return requestUri != null && requestUri.startsWith(XTM_COMPOSER_URI_PREFIX);
    } catch (Exception e) {
      return false;
    }
  }

  /** Returns {@code true} if the current HTTP request method is DELETE. */
  private boolean isHttpDelete() {
    HttpServletRequest request = getCurrentRequest();
    return request != null && "DELETE".equalsIgnoreCase(request.getMethod());
  }

  /** Returns the current HTTP request, or null if not in a request context. */
  private HttpServletRequest getCurrentRequest() {
    try {
      ServletRequestAttributes attrs =
          (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
      return attrs != null ? attrs.getRequest() : null;
    } catch (Exception e) {
      return null;
    }
  }

  /**
   * Detects a child-resource by scanning {@code @PathVariable} parameters for IDs that are not the
   * parent's resourceId. For each candidate, tries {@code EntityManager.find()} against the entity
   * class map until a match is found. Returns the child info with a pre-deletion snapshot, or null.
   */
  private ChildResourceInfo detectChildResource(
      ProceedingJoinPoint joinPoint, String parentResourceId) {
    try {
      MethodSignature sig = (MethodSignature) joinPoint.getSignature();
      Annotation[][] paramAnnotations = sig.getMethod().getParameterAnnotations();
      Object[] args = joinPoint.getArgs();

      for (int i = 0; i < paramAnnotations.length; i++) {
        for (Annotation ann : paramAnnotations[i]) {
          if (ann instanceof PathVariable) {
            String paramValue = args[i] != null ? args[i].toString() : null;
            if (paramValue != null && !paramValue.equals(parentResourceId)) {
              // Non-parent path variable — try to find the entity
              for (Map.Entry<ResourceType, Class<?>> entry : ENTITY_CLASS_MAP.entrySet()) {
                try {
                  Object entity = entityManager.find(entry.getValue(), paramValue);
                  if (entity != null) {
                    JsonNode snapshot = objectMapper.valueToTree(entity);
                    return new ChildResourceInfo(entry.getKey(), paramValue, snapshot);
                  }
                } catch (Exception e) {
                  // Wrong entity type for this ID — continue
                }
              }
            }
          }
        }
      }
    } catch (Exception e) {
      log.debug("[AUDIT] Failed to detect child resource: {}", e.getMessage());
    }
    return null;
  }

  /** Info about a child resource detected before deletion. */
  private record ChildResourceInfo(ResourceType resourceType, String entityId, JsonNode snapshot) {}

  private boolean shouldSkip(Action action) {
    return switch (action) {
      case CREATE, WRITE, DELETE, LAUNCH, DUPLICATE -> false;
      case READ, SEARCH -> !logReads;
      default -> true; // SKIP_RBAC, PROCESS
    };
  }

  private String mapEventScope(Action action) {
    return switch (action) {
      case CREATE -> "create";
      case WRITE -> "update";
      case DELETE -> "delete";
      case LAUNCH -> "status_change";
      case DUPLICATE -> "duplicate";
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
      log.debug(
          "[AUDIT] Failed to snapshot entity {}/{}: {}", resourceType, entityId, e.getMessage());
      return null;
    }
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

  /** Extracts a name from a snapshotted JSON node. */
  private String extractNameFromSnapshot(JsonNode snapshot) {
    if (snapshot == null) {
      return null;
    }
    // Try common name fields in order of precedence
    for (String field :
        new String[] {
          "scenario_name",
          "exercise_name",
          "inject_title",
          "user_firstname",
          "name",
          "role_name",
          "group_name"
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
   * that actually changed. Handles JPA relation fields gracefully: when the input sends a scalar ID
   * but the old snapshot has a full object, the object is flattened to its ID for comparison.
   */
  private DiffResult computeDiff(JsonNode oldSnapshot, JsonNode newInput) {
    ObjectNode changedNew = objectMapper.createObjectNode();
    ObjectNode changedOld = objectMapper.createObjectNode();

    for (Map.Entry<String, JsonNode> entry : newInput.properties()) {
      String fieldName = entry.getKey();
      JsonNode newValue = entry.getValue();
      JsonNode oldValue = oldSnapshot.get(fieldName);

      // Skip null input values — in REST convention, null means "not provided" (the service
      // ignores it), not "clear this field". Including them causes false positives for
      // server-managed fields (e.g. inject_injector, resolved from the contract server-side).
      if (newValue == null || newValue.isNull()) {
        continue;
      }

      // Skip DTO metadata fields that are never actual entity attributes
      if (DIFF_SKIP_FIELDS.contains(fieldName)) {
        continue;
      }

      // Handle JPA relation fields: input sends a scalar ID, old snapshot has a full object.
      // Flatten the old object to its ID for comparison and storage.
      if (oldValue != null && oldValue.isObject() && !newValue.isObject()) {
        JsonNode oldId = extractIdFromRelation(oldValue);
        if (oldId != null) {
          if (oldId.equals(newValue)) {
            continue; // Same ID — field didn't change
          }
          // Different ID — record the flattened old value
          changedNew.set(fieldName, newValue);
          changedOld.set(fieldName, oldId);
          continue;
        }
      }

      // Standard comparison for non-relation fields — uses semantic equality that normalises
      // numeric types (100 == 100.0), treats null ≈ empty arrays, and recurses into nested
      // objects/arrays so that insignificant serialisation differences are ignored.
      if (oldValue == null || !semanticEquals(oldValue, newValue)) {
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

  /**
   * Deep semantic equality for two {@link JsonNode} values. Unlike {@link JsonNode#equals}, this
   * method:
   *
   * <ul>
   *   <li>Treats numeric values as equal when their {@code doubleValue()} matches (so {@code 100}
   *       == {@code 100.0}).
   *   <li>Considers {@code null} and an empty array {@code []} as equivalent (common when JPA
   *       serialises an empty collection as {@code []} but the DTO omits it or sends {@code null}).
   *   <li>Considers {@code null} and an empty object {@code {}} as equivalent.
   *   <li>Recurses into objects and arrays applying the same rules at every nesting level.
   * </ul>
   */
  private static boolean semanticEquals(JsonNode a, JsonNode b) {
    // Normalise null/missing nodes
    boolean aEmpty = isEffectivelyEmpty(a);
    boolean bEmpty = isEffectivelyEmpty(b);
    if (aEmpty && bEmpty) {
      return true;
    }
    if (aEmpty || bEmpty) {
      return false;
    }

    // Both are numeric — compare by numeric value (handles int vs double, e.g. 100 vs 100.0)
    if (a.isNumber() && b.isNumber()) {
      return Double.compare(a.doubleValue(), b.doubleValue()) == 0;
    }

    // Both are objects — compare field-by-field
    if (a.isObject() && b.isObject()) {
      ObjectNode objA = (ObjectNode) a;
      ObjectNode objB = (ObjectNode) b;
      // Check all fields in A exist and match in B
      for (var entry : objA.properties()) {
        if (!semanticEquals(entry.getValue(), objB.get(entry.getKey()))) {
          return false;
        }
      }
      // Check B doesn't have extra non-empty fields
      for (var entry : objB.properties()) {
        if (objA.get(entry.getKey()) == null && !isEffectivelyEmpty(entry.getValue())) {
          return false;
        }
      }
      return true;
    }

    // Both are arrays — compare element-by-element
    if (a.isArray() && b.isArray()) {
      ArrayNode arrA = (ArrayNode) a;
      ArrayNode arrB = (ArrayNode) b;
      if (arrA.size() != arrB.size()) {
        return false;
      }
      for (int i = 0; i < arrA.size(); i++) {
        if (!semanticEquals(arrA.get(i), arrB.get(i))) {
          return false;
        }
      }
      return true;
    }

    // Fallback: delegate to Jackson's strict equals (covers strings, booleans, etc.)
    return a.equals(b);
  }

  /**
   * Returns {@code true} when the node is semantically empty: {@code null}, a Jackson {@code
   * NullNode}, an empty array, or an empty object.
   */
  private static boolean isEffectivelyEmpty(JsonNode node) {
    if (node == null || node.isNull() || node.isMissingNode()) {
      return true;
    }
    if (node.isArray() && node.isEmpty()) {
      return true;
    }
    if (node.isObject() && node.isEmpty()) {
      return true;
    }
    return false;
  }

  /**
   * Returns {@code true} when a value is insignificant for audit-logging purposes: {@code null},
   * empty strings, empty arrays, empty objects, or zero-valued numbers that represent "not set".
   * Used by {@link #stripInsignificantValues} to remove noise from create-event inputs.
   */
  private static boolean isInsignificantValue(JsonNode node) {
    if (isEffectivelyEmpty(node)) {
      return true;
    }
    // Empty string (e.g. inject_description: "")
    if (node.isTextual() && node.asText().isEmpty()) {
      return true;
    }
    // Boolean false (default value for most boolean fields)
    if (node.isBoolean() && !node.asBoolean()) {
      return true;
    }
    return false;
  }

  /**
   * Recursively strips insignificant values (nulls, empty strings, empty arrays, false booleans)
   * from a {@link JsonNode} tree. Also removes fields listed in {@link #DIFF_SKIP_FIELDS}. This
   * keeps create-event audit entries concise — only meaningful, non-default values are logged.
   *
   * <p>For arrays, each element is cleaned recursively (but elements are never removed, to preserve
   * positional semantics). For objects, fields whose cleaned value is insignificant are dropped.
   *
   * @return a new, cleaned copy of the tree — the original is never mutated
   */
  private JsonNode stripInsignificantValues(JsonNode node) {
    if (node == null || node.isNull()) {
      return node;
    }

    if (node.isObject()) {
      ObjectNode cleaned = objectMapper.createObjectNode();
      for (var entry : node.properties()) {
        String fieldName = entry.getKey();
        // Skip DTO metadata fields (same set as diff computation)
        if (DIFF_SKIP_FIELDS.contains(fieldName)) {
          continue;
        }
        JsonNode value = entry.getValue();
        JsonNode cleanedValue = stripInsignificantValues(value);
        if (!isInsignificantValue(cleanedValue)) {
          cleaned.set(fieldName, cleanedValue);
        }
      }
      return cleaned;
    }

    if (node.isArray()) {
      ArrayNode cleaned = objectMapper.createArrayNode();
      for (JsonNode element : node) {
        cleaned.add(stripInsignificantValues(element));
      }
      return cleaned;
    }

    // Scalars (string, number, boolean) — return as-is; caller decides significance
    return node;
  }

  /**
   * Extracts the ID from a serialized JPA relation object. Looks for common ID field patterns:
   * {@code *_id} fields (e.g. {@code injector_contract_id}, {@code payload_id}) or plain {@code
   * id}.
   */
  private JsonNode extractIdFromRelation(JsonNode objectNode) {
    // First try fields ending with "_id" (JPA naming convention: injector_contract_id, etc.)
    for (Map.Entry<String, JsonNode> field : objectNode.properties()) {
      if (field.getKey().endsWith("_id") && field.getValue().isTextual()) {
        return field.getValue();
      }
    }
    // Fallback: plain "id"
    JsonNode idNode = objectNode.get("id");
    if (idNode != null && idNode.isTextual()) {
      return idNode;
    }
    return null;
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
      String entityName,
      String parentId,
      String sourceId) {
    try {
      auditLogService.logMutationEvent(
          eventScope,
          eventStatus,
          resourceType,
          entityId,
          input,
          oldValue,
          entityName,
          parentId,
          sourceId);
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
              ResourceType.ASSET_GROUP, Class.forName("io.openaev.database.model.AssetGroup")),
          Map.entry(ResourceType.OBJECTIVE, Class.forName("io.openaev.database.model.Objective")),
          Map.entry(
              ResourceType.ORGANIZATION, Class.forName("io.openaev.database.model.Organization")),
          Map.entry(
              ResourceType.KILL_CHAIN_PHASE,
              Class.forName("io.openaev.database.model.KillChainPhase")),
          Map.entry(
              ResourceType.ATTACK_PATTERN,
              Class.forName("io.openaev.database.model.AttackPattern")),
          Map.entry(ResourceType.USER_GROUP, Class.forName("io.openaev.database.model.Group")),
          Map.entry(ResourceType.GROUP_ROLE, Class.forName("io.openaev.database.model.Role")));
    } catch (ClassNotFoundException e) {
      log.error("[AUDIT] Failed to build entity class map: {}", e.getMessage(), e);
      return Map.of();
    }
  }

  /** Builds the reverse mapping: JPA entity class → ResourceType. */
  private static Map<Class<?>, ResourceType> buildReverseEntityClassMap() {
    Map<Class<?>, ResourceType> reverse = new java.util.HashMap<>();
    ENTITY_CLASS_MAP.forEach((resourceType, clazz) -> reverse.put(clazz, resourceType));
    return Map.copyOf(reverse);
  }
}
