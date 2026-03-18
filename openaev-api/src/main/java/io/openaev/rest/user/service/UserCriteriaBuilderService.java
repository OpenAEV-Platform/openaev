package io.openaev.rest.user.service;

import static io.openaev.database.criteria.GenericCriteria.countQuery;
import static io.openaev.rest.user.helper.UserQueryHelper.execution;
import static io.openaev.rest.user.helper.UserQueryHelper.select;
import static io.openaev.utils.pagination.PaginationUtils.buildPaginationCriteriaBuilder;
import static io.openaev.utils.pagination.SortUtilsCriteriaBuilder.toSortCriteriaBuilder;

import io.openaev.api.users.dto.UserOutput;
import io.openaev.database.model.User;
import io.openaev.rest.exception.ElementNotFoundException;
import io.openaev.utils.pagination.SearchPaginationInput;
import io.openaev.utils.users.UserQueryHelper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Tuple;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Service
public class UserCriteriaBuilderService {

  @PersistenceContext private EntityManager entityManager;

  public Page<UserOutput> userPagination(@NotNull SearchPaginationInput searchPaginationInput) {
    return buildPaginationCriteriaBuilder(this::paginate, searchPaginationInput, User.class);
  }

  public Page<UserOutput> userPagination(
      @NotNull SearchPaginationInput searchPaginationInput,
      @NotNull Specification<User> additionalSpec) {
    return buildPaginationCriteriaBuilder(
        (spec, specCount, pageable) ->
            paginate(
                spec != null ? spec.and(additionalSpec) : additionalSpec,
                specCount != null ? specCount.and(additionalSpec) : additionalSpec,
                pageable),
        searchPaginationInput,
        User.class);
  }

  public UserOutput findById(@NotBlank String userId) {
    return findById(userId, null);
  }

  public UserOutput findById(@NotBlank String userId, Specification<User> additionalSpec) {
    CriteriaBuilder cb = this.entityManager.getCriteriaBuilder();
    CriteriaQuery<Tuple> cq = cb.createTupleQuery();
    Root<User> root = cq.from(User.class);
    UserQueryHelper.select(cb, cq, root);

    Predicate idPredicate = cb.equal(root.get("id"), userId);
    if (additionalSpec != null) {
      Predicate additionalPredicate = additionalSpec.toPredicate(root, cq, cb);
      cq.where(
          additionalPredicate != null ? cb.and(idPredicate, additionalPredicate) : idPredicate);
    } else {
      cq.where(idPredicate);
    }

    List<UserOutput> results = execution(entityManager.createQuery(cq));
    if (results.isEmpty()) {
      throw new ElementNotFoundException("User not found with id: " + userId);
    }
    return results.getFirst();
  }

  public List<UserOutput> find(Specification<User> specification) {
    CriteriaBuilder cb = this.entityManager.getCriteriaBuilder();

    CriteriaQuery<Tuple> cq = cb.createTupleQuery();
    Root<User> root = cq.from(User.class);
    select(cb, cq, root);

    if (specification != null) {
      Predicate predicate = specification.toPredicate(root, cq, cb);
      if (predicate != null) {
        cq.where(predicate);
      }
    }

    TypedQuery<Tuple> query = entityManager.createQuery(cq);
    return execution(query);
  }

  // -- PRIVATE --

  private Page<UserOutput> paginate(
      Specification<User> specification,
      Specification<User> specificationCount,
      Pageable pageable) {
    CriteriaBuilder cb = this.entityManager.getCriteriaBuilder();

    CriteriaQuery<Tuple> cq = cb.createTupleQuery();
    Root<User> userRoot = cq.from(User.class);
    select(cb, cq, userRoot);

    // -- Specification --
    if (specification != null) {
      Predicate predicate = specification.toPredicate(userRoot, cq, cb);
      if (predicate != null) {
        cq.where(predicate);
      }
    }

    // -- Sorting --
    List<Order> orders = toSortCriteriaBuilder(cb, userRoot, pageable.getSort());
    cq.orderBy(orders);

    // Type Query
    TypedQuery<Tuple> query = entityManager.createQuery(cq);

    // -- Pagination --
    query.setFirstResult((int) pageable.getOffset());
    query.setMaxResults(pageable.getPageSize());

    // -- EXECUTION --
    List<UserOutput> users = execution(query);

    // -- Count Query --
    Long total = countQuery(cb, this.entityManager, User.class, specificationCount);

    return new PageImpl<>(users, pageable, total);
  }
}
