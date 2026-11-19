package io.openaev.utils.author;

import io.openaev.database.model.Organization;
import io.openaev.database.model.User;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.From;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;

/** The author of authored content in a criteria query: a user or an organization. */
public record AuthorJoins(Join<?, User> user, Join<?, Organization> organization) {

  public static AuthorJoins of(From<?, ?> authored) {
    return new AuthorJoins(
        authored.join("author", JoinType.LEFT).join("user", JoinType.LEFT),
        authored.join("author", JoinType.LEFT).join("organization", JoinType.LEFT));
  }

  /** A user's full name, or its email when the name is incomplete. */
  public static Expression<String> userName(CriteriaBuilder cb, Join<?, User> user) {
    Expression<String> firstname = user.get("firstname");
    Expression<String> lastname = user.get("lastname");
    Predicate hasFullName =
        cb.and(
            cb.isNotNull(firstname),
            cb.notEqual(cb.trim(firstname), ""),
            cb.isNotNull(lastname),
            cb.notEqual(cb.trim(lastname), ""));
    return cb.<String>selectCase()
        .when(hasFullName, cb.concat(cb.concat(firstname, " "), lastname))
        .otherwise(user.<String>get("email"));
  }

  public Expression<String> id(CriteriaBuilder cb) {
    return cb.<String>coalesce().value(user.<String>get("id")).value(organization.get("id"));
  }

  public Expression<String> name(CriteriaBuilder cb) {
    return cb.<String>coalesce().value(userName(cb, user)).value(organization.<String>get("name"));
  }
}
