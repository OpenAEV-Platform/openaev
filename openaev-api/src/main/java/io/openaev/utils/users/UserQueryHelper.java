package io.openaev.utils.users;

import static io.openaev.api.users.dto.UserOutput.*;
import static io.openaev.utils.JpaUtils.arrayAggOnField;
import static io.openaev.utils.JpaUtils.arrayAggOnId;
import static io.openaev.utils.JpaUtils.createLeftJoin;

import io.openaev.api.users.dto.UserOutput;
import io.openaev.database.model.Organization;
import io.openaev.database.model.Tenant;
import io.openaev.database.model.User;
import jakarta.persistence.Tuple;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.*;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.hibernate.query.criteria.HibernateCriteriaBuilder;

public class UserQueryHelper {

  private UserQueryHelper() {}

  // -- SELECT --

  private static final String QUERY_ALIAS_TENANT_NAMES = "query_tenant_names";

  public static void select(CriteriaBuilder cb, CriteriaQuery<Tuple> cq, Root<User> userRoot) {
    // Joins
    Join<User, ?> tagJoin = createLeftJoin(userRoot, "tags");
    Join<User, Organization> organizationJoin = createLeftJoin(userRoot, "organization");
    Join<User, Tenant> tenantJoin = createLeftJoin(userRoot, "tenants");

    // Array aggregations
    HibernateCriteriaBuilder hcb = (HibernateCriteriaBuilder) cb;
    Expression<String[]> tagIdsExpression = arrayAggOnId(hcb, tagJoin);
    Expression<String[]> tenantIdsExpression = arrayAggOnId(hcb, tenantJoin);
    Expression<String[]> tenantNamesExpression = arrayAggOnField(hcb, tenantJoin, "name");

    cq.multiselect(
        userRoot.get("id").alias(ALIAS_ID),
        userRoot.get("email").alias(ALIAS_EMAIL),
        userRoot.get("firstname").alias(ALIAS_FIRSTNAME),
        userRoot.get("lastname").alias(ALIAS_LASTNAME),
        userRoot.get("password").alias("user_password"),
        userRoot.get("pgpKey").alias(ALIAS_PGP_KEY),
        userRoot.get("phone").alias(ALIAS_PHONE),
        userRoot.get("phone2").alias(ALIAS_PHONE2),
        organizationJoin.get("id").alias(ALIAS_ORGANIZATION_ID),
        organizationJoin.get("name").alias(ALIAS_ORGANIZATION_NAME),
        tagIdsExpression.alias(ALIAS_TAGS),
        userRoot.get("admin").alias(ALIAS_ADMIN),
        tenantIdsExpression.alias(ALIAS_TENANTS),
        tenantNamesExpression.alias(QUERY_ALIAS_TENANT_NAMES));

    cq.groupBy(userRoot.get("id"), organizationJoin.get("id"));
  }

  // -- EXECUTION --

  /** Executes query without tenant info (for tenant-scoped APIs). */
  public static List<UserOutput> execution(TypedQuery<Tuple> query) {
    return execution(query, false);
  }

  /** Executes query with tenant info (for platform-scoped APIs). */
  public static List<UserOutput> executionWithTenants(TypedQuery<Tuple> query) {
    return execution(query, true);
  }

  private static List<UserOutput> execution(TypedQuery<Tuple> query, boolean includeTenants) {
    return query.getResultList().stream()
        .map(
            tuple -> {
              String pgpKey = tuple.get(ALIAS_PGP_KEY, String.class);
              Set<String> tagIds =
                  Arrays.stream(tuple.get(ALIAS_TAGS, String[].class)).collect(Collectors.toSet());
              List<UserOutput.UserTenantOutput> tenants = null;
              if (includeTenants) {
                String[] tenantIds = tuple.get(ALIAS_TENANTS, String[].class);
                String[] tenantNames = tuple.get(QUERY_ALIAS_TENANT_NAMES, String[].class);
                tenants =
                    IntStream.range(0, tenantIds.length)
                        .mapToObj(
                            i ->
                                new UserOutput.UserTenantOutput(
                                    tenantIds[i], i < tenantNames.length ? tenantNames[i] : null))
                        .distinct()
                        .toList();
              }
              return new UserOutput(
                  tuple.get(ALIAS_ID, String.class),
                  tuple.get(ALIAS_EMAIL, String.class),
                  tuple.get(ALIAS_FIRSTNAME, String.class),
                  tuple.get(ALIAS_LASTNAME, String.class),
                  pgpKey,
                  tuple.get(ALIAS_PHONE, String.class),
                  tuple.get(ALIAS_PHONE2, String.class),
                  tuple.get(ALIAS_ORGANIZATION_ID, String.class),
                  tuple.get(ALIAS_ORGANIZATION_NAME, String.class),
                  tagIds,
                  tuple.get(ALIAS_ADMIN, Boolean.class),
                  tenants);
            })
        .toList();
  }
}
