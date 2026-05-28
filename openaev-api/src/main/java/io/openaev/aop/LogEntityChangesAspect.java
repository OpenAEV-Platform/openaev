package io.openaev.aop;

import io.openaev.database.model.Base;
import io.openaev.service.LogService;
import jakarta.persistence.EntityManager;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.hibernate.Session;
import org.hibernate.StatelessSession;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Aspect that logs before/after state of entities modified through repository operations (save,
 * saveAll, delete, deleteAll, deleteById) on repositories annotated with {@link LogEntityChanges}.
 *
 * <p>Snapshot logic (before/after) lives here. Log formatting and metadata enrichment are delegated
 * to {@link LogService#logEntityChangeEvent}.
 */
@Aspect
@Component
@ConditionalOnProperty(
    name = "logging.entity-changes.enabled",
    havingValue = "true",
    matchIfMissing = false)
public class LogEntityChangesAspect {

  private final EntityManager entityManager;
  private final LogService logService;

  public LogEntityChangesAspect(EntityManager entityManager, LogService logService) {
    this.entityManager = entityManager;
    this.logService = logService;
  }

  /** Intercepts save/saveAll on annotated repositories. */
  @Around(
      "execution(* org.springframework.data.repository.CrudRepository+.save(..)) "
          + "&& this(bean)")
  public Object aroundSave(ProceedingJoinPoint joinPoint, Object bean) throws Throwable {
    if (!isAnnotatedRepository(bean)) {
      return joinPoint.proceed();
    }
    Object entity = joinPoint.getArgs()[0];
    Object before = snapshotEntity(entity);
    Object result = joinPoint.proceed();
    // Snapshot after via StatelessSession so Jackson doesn't touch managed Hibernate proxies.
    logService.logEntityChangeEvent(
        repositoryName(bean), "save", Level.WARNING, before, snapshotEntity(result), null);
    return result;
  }

  /** Intercepts saveAll on annotated repositories. */
  @Around(
      "execution(* org.springframework.data.repository.CrudRepository+.saveAll(..)) "
          + "&& this(bean)")
  public Object aroundSaveAll(ProceedingJoinPoint joinPoint, Object bean) throws Throwable {
    if (!isAnnotatedRepository(bean)) {
      return joinPoint.proceed();
    }
    Object before = snapshotCollection(joinPoint.getArgs()[0]);
    Object result = joinPoint.proceed();
    logService.logEntityChangeEvent(
        repositoryName(bean), "saveAll", Level.WARNING, before, snapshotCollection(result), null);
    return result;
  }

  /** Intercepts delete on annotated repositories. */
  @Around(
      "execution(* org.springframework.data.repository.CrudRepository+.delete(..)) "
          + "&& this(bean)")
  public Object aroundDelete(ProceedingJoinPoint joinPoint, Object bean) throws Throwable {
    if (!isAnnotatedRepository(bean)) {
      return joinPoint.proceed();
    }
    // Load a clean copy via StatelessSession BEFORE deletion — accessing the raw entity after
    // delete() triggers lazy-collection loading on a REMOVED entity, which can cause
    // TransientObjectException at commit (e.g. bidirectional User↔Group back-references).
    Object before = snapshotEntity(joinPoint.getArgs()[0]);
    Object result = joinPoint.proceed();
    logService.logEntityChangeEvent(
        repositoryName(bean), "delete", Level.WARNING, before, null, null);
    return result;
  }

  /** Intercepts deleteAll on annotated repositories. */
  @Around(
      "execution(* org.springframework.data.repository.CrudRepository+.deleteAll(..)) "
          + "&& this(bean)")
  public Object aroundDeleteAll(ProceedingJoinPoint joinPoint, Object bean) throws Throwable {
    if (!isAnnotatedRepository(bean)) {
      return joinPoint.proceed();
    }
    // Same reasoning as aroundDelete: snapshot via StatelessSession before the deletions.
    Object entities = joinPoint.getArgs().length > 0 ? joinPoint.getArgs()[0] : null;
    Object before = entities != null ? snapshotCollection(entities) : "ALL";
    Object result = joinPoint.proceed();
    logService.logEntityChangeEvent(
        repositoryName(bean), "deleteAll", Level.WARNING, before, null, null);
    return result;
  }

  /** Intercepts deleteById on annotated repositories. */
  @Around(
      "execution(* org.springframework.data.repository.CrudRepository+.deleteById(..)) "
          + "&& this(bean)")
  public Object aroundDeleteById(ProceedingJoinPoint joinPoint, Object bean) throws Throwable {
    if (!isAnnotatedRepository(bean)) {
      return joinPoint.proceed();
    }
    Object id = joinPoint.getArgs()[0];
    logService.logEntityChangeEvent(
        repositoryName(bean), "deleteById", Level.WARNING, String.valueOf(id), null, null);
    return joinPoint.proceed();
  }

  /** Intercepts repository methods annotated with {@link AuditModifying}. */
  @Around("@annotation(auditModifying)")
  public Object aroundModifying(ProceedingJoinPoint joinPoint, AuditModifying auditModifying)
      throws Throwable {
    AuditTarget[] targets = auditModifying.value();

    List<TargetSnapshot> beforeSnapshots = new ArrayList<>();
    for (AuditTarget target : targets) {
      Object ids = resolveParameter(joinPoint, target.paramName());
      List<String> idList = toIdList(ids);
      beforeSnapshots.add(
          new TargetSnapshot(
              target.entityType(), idList, snapshotByIds(target.entityType(), idList)));
    }

    Object result = joinPoint.proceed();

    entityManager.flush();
    entityManager.clear();

    String methodName = ((MethodSignature) joinPoint.getSignature()).getMethod().getName();
    String repoName =
        ((MethodSignature) joinPoint.getSignature()).getDeclaringType().getSimpleName();

    for (TargetSnapshot snapshot : beforeSnapshots) {
      Map<String, Object> extra = new LinkedHashMap<>();
      extra.put("entity_type", snapshot.entityType.getSimpleName());
      extra.put("ids", snapshot.ids);
      logService.logEntityChangeEvent(
          repoName,
          methodName,
          Level.WARNING,
          snapshot.before,
          snapshotByIds(snapshot.entityType, snapshot.ids),
          extra);
    }
    return result;
  }

  private record TargetSnapshot(Class<?> entityType, List<String> ids, Object before) {}

  /** Resolves a method parameter value by its name. */
  private Object resolveParameter(ProceedingJoinPoint joinPoint, String paramName) {
    MethodSignature signature = (MethodSignature) joinPoint.getSignature();
    Parameter[] parameters = signature.getMethod().getParameters();
    Object[] args = joinPoint.getArgs();
    for (int i = 0; i < parameters.length; i++) {
      if (parameters[i].getName().equals(paramName)) {
        return args[i];
      }
    }
    String[] parameterNames = signature.getParameterNames();
    if (parameterNames != null) {
      for (int i = 0; i < parameterNames.length; i++) {
        if (parameterNames[i].equals(paramName)) {
          return args[i];
        }
      }
    }
    throw new IllegalArgumentException(
        "@AuditModifying paramName '" + paramName + "' not found in method parameters");
  }

  private List<String> toIdList(Object ids) {
    if (ids instanceof Collection<?> collection) {
      return collection.stream().map(Object::toString).toList();
    }
    return List.of(ids.toString());
  }

  private Object snapshotByIds(Class<?> entityType, List<String> ids) {
    try (StatelessSession stateless =
        entityManager.unwrap(Session.class).getSessionFactory().openStatelessSession()) {
      List<Object> snapshots = new ArrayList<>();
      for (String id : ids) {
        Object existing = stateless.get(entityType, id);
        snapshots.add(existing != null ? existing : "[NOT_FOUND: " + id + "]");
      }
      return snapshots;
    } catch (Exception e) {
      return "[SNAPSHOT_ERROR: " + e.getMessage() + "]";
    }
  }

  /**
   * Loads an entity snapshot via a {@link StatelessSession}, completely isolated from the main
   * Hibernate session. Used for both before-state (bypasses L1 cache) and after-state (avoids
   * touching managed proxies that could trigger unwanted lazy loading or cascading).
   */
  private Object snapshotEntity(Object entity) {
    try {
      if (entity instanceof Base baseEntity && baseEntity.getId() != null) {
        try (StatelessSession stateless =
            entityManager.unwrap(Session.class).getSessionFactory().openStatelessSession()) {
          Object existing = stateless.get(entity.getClass(), baseEntity.getId());
          if (existing != null) {
            return existing;
          }
        }
      }
      return "[NEW]";
    } catch (Exception e) {
      return "[SNAPSHOT_ERROR: " + e.getMessage() + "]";
    }
  }

  /** Snapshots each entity in a collection via {@link #snapshotEntity}. */
  private Object snapshotCollection(Object entities) {
    if (entities instanceof Collection<?> collection) {
      List<Object> snapshots = new ArrayList<>();
      for (Object entity : collection) {
        snapshots.add(snapshotEntity(entity));
      }
      return snapshots;
    }
    return "[UNKNOWN_TYPE]";
  }

  /**
   * Returns true if the repository bean (or any of its interfaces) is annotated with {@link
   * LogEntityChanges}.
   */
  private boolean isAnnotatedRepository(Object bean) {
    for (Class<?> iface : bean.getClass().getInterfaces()) {
      if (iface.isAnnotationPresent(LogEntityChanges.class)) {
        return true;
      }
    }
    return bean.getClass().isAnnotationPresent(LogEntityChanges.class);
  }

  /**
   * Returns the simple name of the {@link LogEntityChanges}-annotated repository interface. Spring
   * Data creates JDK dynamic proxies whose runtime class name is not meaningful ({@code $Proxy123})
   * — the actual name lives on the interface.
   */
  private String repositoryName(Object bean) {
    for (Class<?> iface : bean.getClass().getInterfaces()) {
      if (iface.isAnnotationPresent(LogEntityChanges.class)) {
        return iface.getSimpleName();
      }
    }
    return bean.getClass().getSimpleName();
  }
}
