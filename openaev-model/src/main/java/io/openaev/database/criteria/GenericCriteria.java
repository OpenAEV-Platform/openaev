package io.openaev.database.criteria;

import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.jetbrains.annotations.NotNull;
import org.springframework.data.jpa.domain.Specification;

/**
 * Utility class for building generic JPA Criteria queries.
 *
 * <p>This class provides reusable methods for constructing JPA Criteria API queries that can work
 * with any entity type. It simplifies common query patterns such as counting entities with optional
 * filtering specifications.
 *
 * @see Specification
 */
public class GenericCriteria {

  private GenericCriteria() {
    // Utility class - prevent instantiation
  }

  /**
   * Executes a count query for entities matching the given specification.
   *
   * <p>This method creates a JPA Criteria count query with {@code DISTINCT} counting to ensure
   * accurate results when joins are involved.
   *
   * @param <T> the entity type
   * @param cb the JPA CriteriaBuilder instance
   * @param entityManager the JPA EntityManager for query execution
   * @param entityClass the entity class to count
   * @param specification optional filtering specification (may be null for unfiltered count)
   * @return the count of distinct entities matching the specification
   */
  public static <T> Long countQuery(
      @NotNull final CriteriaBuilder cb,
      @NotNull final EntityManager entityManager,
      @NotNull final Class<T> entityClass,
      Specification<T> specification) {
    CriteriaQuery<Long> countQuery = cb.createQuery(Long.class);
    Root<T> countRoot = countQuery.from(entityClass);
    countQuery.select(cb.countDistinct(countRoot));
    if (specification != null) {
      Predicate predicate = specification.toPredicate(countRoot, countQuery, cb);
      if (predicate != null) {
        countQuery.where(predicate);
      }
    }
    return entityManager.createQuery(countQuery).getSingleResult();
  }
}
