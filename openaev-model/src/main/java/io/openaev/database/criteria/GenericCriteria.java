package io.openaev.database.criteria;

import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.jetbrains.annotations.NotNull;
import org.springframework.data.jpa.domain.Specification;

public class GenericCriteria {

  private GenericCriteria() {}

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
