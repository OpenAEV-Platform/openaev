package io.openaev.utils.users;

import static io.openaev.utils.JpaUtils.createJoinArrayAggOnId;
import static io.openaev.utils.JpaUtils.createLeftJoin;

import io.openaev.api.users.dto.UserOutput;
import io.openaev.database.model.Organization;
import io.openaev.database.model.User;
import jakarta.persistence.Tuple;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.*;

import java.util.*;
import java.util.stream.Collectors;

public class UserQueryHelper {

  private UserQueryHelper() {}

  // -- SELECT --

  public static void select(CriteriaBuilder cb, CriteriaQuery<Tuple> cq, Root<User> userRoot) {
    // Joins
    Join<User, Organization> organizationJoin = createLeftJoin(userRoot, "organization");

    // Array aggregations
    Expression<String[]> tagIdsExpression = createJoinArrayAggOnId(cb, userRoot, "tags");

    // Boolean expressions for sensitive fields
    Expression<Boolean> hasPasswordExpression = cb.selectCase()
        .when(cb.and(cb.isNotNull(userRoot.get("password")), cb.notEqual(cb.trim(userRoot.get("password")), "")), true)
        .otherwise(false)
        .as(Boolean.class);

    Expression<Boolean> hasPgpKeyExpression = cb.selectCase()
        .when(cb.and(cb.isNotNull(userRoot.get("pgpKey")), cb.notEqual(cb.trim(userRoot.get("pgpKey")), "")), true)
        .otherwise(false)
        .as(Boolean.class);

    // Multiselect
    cq.multiselect(
            userRoot.get("id").alias("user_id"),
            userRoot.get("email").alias("user_email"),
            userRoot.get("firstname").alias("user_firstname"),
            userRoot.get("lastname").alias("user_lastname"),
            hasPasswordExpression.alias("user_has_password"),
            hasPgpKeyExpression.alias("user_has_pgp_key"),
            organizationJoin.get("id").alias("user_organization_id"),
            organizationJoin.get("name").alias("user_organization_name"),
            tagIdsExpression.alias("user_tags"))
        .distinct(true);

    // Group by
    cq.groupBy(userRoot.get("id"), organizationJoin.get("id"));
  }

  // -- EXECUTION --

  public static List<UserOutput> execution(TypedQuery<Tuple> query) {
    return query.getResultList().stream()
        .map(
            tuple -> {
              String[] tagArray = tuple.get("user_tags", String[].class);
              Set<String> tagIds =
                  tagArray != null
                      ? Arrays.stream(tagArray).filter(Objects::nonNull).collect(Collectors.toSet())
                      : Collections.emptySet();

              return new UserOutput(
                  tuple.get("user_id", String.class),
                  tuple.get("user_email", String.class),
                  tuple.get("user_firstname", String.class),
                  tuple.get("user_lastname", String.class),
                  null,
                  null,
                  tuple.get("user_organization_id", String.class),
                  tuple.get("user_organization_name", String.class),
                  tagIds,
                  Boolean.TRUE.equals(tuple.get("user_has_password", Boolean.class)),
                  Boolean.TRUE.equals(tuple.get("user_has_pgp_key", Boolean.class)));
            })
        .toList();
  }
}



