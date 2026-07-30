package io.openaev.notification.engine;

import static io.openaev.utils.FilterUtilsJpa.computeFilterGroupJpa;

import io.openaev.database.model.Base;
import io.openaev.database.model.Filters;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Evaluates whether an entity event matches a live trigger.
 *
 * <p>Filter groups are evaluated by re-checking the database: the trigger's stored {@link
 * Filters.FilterGroup} is converted to the exact same JPA Specification the {@code /search}
 * endpoints use, combined with an {@code id =} predicate, and tested for existence. This reuses
 * OpenAEV's filter semantics one-for-one (including the frontend filter builder).
 *
 * <p>Consequence for DELETE events: the row is gone at evaluation time, so filtered triggers do not
 * fire on deletions - only unfiltered and instance triggers do (documented v1 limitation).
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class NotificationMatchingService {

  private final EntityManager entityManager;

  /**
   * Returns true when the trigger matches the given entity. Must run with the caller's {@code
   * TenantContext} set to the trigger's tenant so the Hibernate tenant filter scopes the re-check.
   */
  @Transactional(readOnly = true)
  public boolean matches(
      ResolvedNotificationTrigger trigger, NotificationResourceCatalog entry, String entityId) {
    // Instance triggers match on the exact entity id, no filter evaluation
    if (trigger.instanceId() != null && !trigger.instanceId().isBlank()) {
      return trigger.instanceId().equals(entityId);
    }
    if (Filters.isEmptyFilterGroup(trigger.filters())) {
      return true;
    }
    try {
      return existsMatching(entry.getEntityClass(), trigger.filters(), entityId);
    } catch (Exception e) {
      log.warn(
          "Notification trigger {} filter evaluation failed for {} {}: {}",
          trigger.id(),
          entry.getResourceType(),
          entityId,
          e.getMessage());
      return false;
    }
  }

  private <T extends Base> boolean existsMatching(
      Class<T> entityClass, Filters.FilterGroup filters, String entityId) {
    CriteriaBuilder cb = entityManager.getCriteriaBuilder();
    CriteriaQuery<Long> query = cb.createQuery(Long.class);
    Root<T> root = query.from(entityClass);
    Specification<T> specification = computeFilterGroupJpa(filters);
    Predicate filterPredicate = specification.toPredicate(root, query, cb);
    String idAttribute =
        entityManager.getMetamodel().entity(entityClass).getId(String.class).getName();
    Predicate idPredicate = cb.equal(root.get(idAttribute), entityId);
    query.select(cb.count(root)).where(cb.and(filterPredicate, idPredicate));
    return entityManager.createQuery(query).getSingleResult() > 0;
  }
}
