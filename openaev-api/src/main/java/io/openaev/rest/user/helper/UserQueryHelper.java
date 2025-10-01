package io.openaev.rest.user.helper;

import static io.openaev.utils.JpaUtils.createJoinArrayAggOnId;
import static io.openaev.utils.JpaUtils.createLeftJoin;

import io.openaev.database.model.Organization;
import io.openaev.database.model.User;
import io.openaev.rest.user.form.user.UserOutput;
import jakarta.persistence.Tuple;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.*;
import java.util.List;
import java.util.Set;

public class UserQueryHelper {

  private UserQueryHelper() {}

  // -- SELECT --

  public static void select(CriteriaBuilder cb, CriteriaQuery<Tuple> cq, Root<User> userRoot) {
    // Joins
    Join<User, Organization> organizationJoin = createLeftJoin(userRoot, "organization");
    // Array aggregations
    Expression<String[]> tagIdsExpression = createJoinArrayAggOnId(cb, userRoot, "tags");

    // Multiselect
    cq.multiselect(
            userRoot.get("id").alias("user_id"),
            userRoot.get("firstname").alias("user_firstname"),
            userRoot.get("lastname").alias("user_lastname"),
            userRoot.get("email").alias("user_email"),
            userRoot.get("admin").alias("user_admin"),
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
            tuple ->
                UserOutput.builder()
                    .id(tuple.get("user_id", String.class))
                    .firstname(tuple.get("user_firstname", String.class))
                    .lastname(tuple.get("user_lastname", String.class))
                    .email(tuple.get("user_email", String.class))
                    .admin(tuple.get("user_admin", boolean.class))
                    .organizationName(tuple.get("user_organization_name", String.class))
                    .tags(Set.of((tuple.get("user_tags", String[].class))))
                    .build())
        .toList();
  }
}
