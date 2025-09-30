package io.openbas.rest.user;

import static io.openbas.database.criteria.GenericCriteria.countQuery;
import static io.openbas.rest.user.PlayerQueryHelper.execution;
import static io.openbas.rest.user.PlayerQueryHelper.select;
import static io.openbas.utils.pagination.PaginationUtils.buildPaginationCriteriaBuilder;
import static io.openbas.utils.pagination.SortUtilsCriteriaBuilder.toSortCriteriaBuilder;

import io.openbas.database.model.User;
import io.openbas.database.repository.UserRepository;
import io.openbas.rest.user.form.player.PlayerOutput;
import io.openbas.service.UserService;
import io.openbas.utils.pagination.SearchPaginationInput;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Tuple;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.*;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.function.TriFunction;
import org.jetbrains.annotations.NotNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PlayerService {

  @PersistenceContext private EntityManager entityManager;

  private final UserRepository userRepository;
  private final UserService userService;

  public Page<PlayerOutput> playerPagination(@NotNull SearchPaginationInput searchPaginationInput) {
    TriFunction<Specification<User>, Specification<User>, Pageable, Page<PlayerOutput>>
        playersFunction;
    User currentUser = userService.currentUser();
    playersFunction = this::paginate;
    return buildPaginationCriteriaBuilder(playersFunction, searchPaginationInput, User.class);
  }

  // -- PRIVATE --

  private Page<PlayerOutput> paginate(
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
    List<PlayerOutput> players = execution(query);

    // -- Count Query --
    Long total = countQuery(cb, this.entityManager, User.class, specificationCount);

    return new PageImpl<>(players, pageable, total);
  }
}
