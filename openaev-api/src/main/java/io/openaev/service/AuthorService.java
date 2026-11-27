package io.openaev.service;

import io.openaev.database.model.User;
import io.openaev.database.model.author.Authored;
import io.openaev.utils.FilterUtilsJpa;
import io.openaev.utils.author.AuthorJoins;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthorService {

  private final UserService userService;
  @PersistenceContext private EntityManager entityManager;

  /**
   * Makes the user acting now the author, in place of any organization. Returns false, and leaves
   * the author unchanged, when no user is acting (startup, schedulers, collectors).
   */
  public boolean authorByCurrentUser(Authored authored) {
    User user = userService.currentUserOrNull();
    if (user == null) {
      return false;
    }
    authored.setAuthorUser(user);
    authored.setAuthorOrganization(null);
    return true;
  }

  /** Distinct authors of the visible content, as filter options. */
  public <T extends Authored> List<FilterUtilsJpa.Option> options(
      Class<T> authoredType, Specification<T> visible) {
    CriteriaBuilder cb = entityManager.getCriteriaBuilder();
    CriteriaQuery<FilterUtilsJpa.Option> cq = cb.createQuery(FilterUtilsJpa.Option.class);
    Root<T> authoredRoot = cq.from(authoredType);
    AuthorJoins author = AuthorJoins.of(authoredRoot);
    Expression<String> authorId = author.id(cb);
    Predicate visiblePredicate = visible.toPredicate(authoredRoot, cq, cb);
    Predicate hasAuthor = cb.isNotNull(authorId);
    cq.multiselect(authorId, author.name(cb))
        .distinct(true)
        .where(visiblePredicate != null ? cb.and(visiblePredicate, hasAuthor) : hasAuthor);
    return entityManager.createQuery(cq).getResultList();
  }
}
