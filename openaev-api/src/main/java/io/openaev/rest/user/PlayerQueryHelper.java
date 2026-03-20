package io.openaev.rest.user;

import static io.openaev.utils.JpaUtils.createJoinArrayAggOnId;
import static io.openaev.utils.JpaUtils.createLeftJoin;

import io.openaev.database.model.Organization;
import io.openaev.database.model.User;
import io.openaev.rest.user.form.player.PlayerOutput;
import jakarta.persistence.Tuple;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.*;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class PlayerQueryHelper {

  private PlayerQueryHelper() {}

  // -- SELECT --

  public static void select(CriteriaBuilder cb, CriteriaQuery<Tuple> cq, Root<User> userRoot) {
    // Array aggregations
    Expression<String[]> tagIdsExpression = createJoinArrayAggOnId(cb, userRoot, "tags");
    Join<User, Organization> organizationJoin = createLeftJoin(userRoot, "organization");

    // Multiselect
    cq.multiselect(
            userRoot.get("id").alias("user_id"),
            userRoot.get("email").alias("user_email"),
            userRoot.get("firstname").alias("user_firstname"),
            userRoot.get("lastname").alias("user_lastname"),
            userRoot.get("country").alias("user_country"),
            userRoot.get("phone").alias("user_phone"),
            userRoot.get("phone2").alias("user_phone2"),
            userRoot.get("pgpKey").alias("user_pgp_key"),
            organizationJoin.get("id").alias("user_organization"),
            tagIdsExpression.alias("user_tags"))
        .distinct(true);

    // Group by
    cq.groupBy(userRoot.get("id"), organizationJoin.get("id"));
  }

  // -- EXECUTION --

  public static List<PlayerOutput> execution(TypedQuery<Tuple> query) {
    return query.getResultList().stream()
        .map(
            tuple ->
                PlayerOutput.builder()
                    .id(tuple.get("user_id", String.class))
                    .email(tuple.get("user_email", String.class))
                    .firstname(tuple.get("user_firstname", String.class))
                    .lastname(tuple.get("user_lastname", String.class))
                    .country(tuple.get("user_country", String.class))
                    .phone(tuple.get("user_phone", String.class))
                    .phone2(tuple.get("user_phone2", String.class))
                    .pgpKey(tuple.get("user_pgp_key", String.class))
                    .organization(tuple.get("user_organization", String.class))
                    .tags(
                        Arrays.stream(tuple.get("user_tags", String[].class))
                            .collect(Collectors.toSet()))
                    .build())
        .toList();
  }
}
