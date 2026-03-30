package io.openaev.utils.pagination;

import static io.openaev.database.criteria.GenericCriteria.countQuery;
import static io.openaev.utils.pagination.SortUtilsCriteriaBuilder.toSortCriteriaBuilder;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.*;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

/** Generic paginated query using CriteriaBuilder with Tuple projection. */
public class CriteriaBuilderPagination {

  private CriteriaBuilderPagination() {}

  /** Executes a paginated CriteriaBuilder query with Tuple projection. */
  public static <T, U> Page<U> paginate(
      @NotNull final EntityManager entityManager,
      @NotNull final Class<T> entityClass,
      Specification<T> specification,
      Specification<T> specificationCount,
      @NotNull final Pageable pageable,
      @NotNull final BiConsumer<CriteriaQuery<Tuple>, Root<T>> selectFn,
      @NotNull final Function<TypedQuery<Tuple>, List<U>> executionFn) {
    CriteriaBuilder cb = entityManager.getCriteriaBuilder();

    CriteriaQuery<Tuple> cq = cb.createTupleQuery();
    Root<T> root = cq.from(entityClass);
    selectFn.accept(cq, root);

    // -- Specification --
    if (specification != null) {
      Predicate predicate = specification.toPredicate(root, cq, cb);
      if (predicate != null) {
        cq.where(predicate);
      }
    }

    // -- Sorting --
    List<Order> orders = toSortCriteriaBuilder(cb, root, pageable.getSort());
    cq.orderBy(orders);

    // -- Query --
    TypedQuery<Tuple> query = entityManager.createQuery(cq);
    query.setFirstResult((int) pageable.getOffset());
    query.setMaxResults(pageable.getPageSize());

    // -- Execution --
    List<U> results = executionFn.apply(query);

    // -- Count --
    Long total = countQuery(cb, entityManager, entityClass, specificationCount);

    return new PageImpl<>(results, pageable, total);
  }
}
