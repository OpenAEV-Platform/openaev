package io.openaev.utils;

import static org.springframework.util.StringUtils.hasText;

import io.openaev.database.model.Base;
import io.openaev.schema.PropertySchema;
import jakarta.annotation.Nullable;
import jakarta.persistence.criteria.*;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;
import org.hibernate.metamodel.model.domain.ManagedDomainType;
import org.hibernate.metamodel.model.domain.PersistentAttribute;
import org.hibernate.query.criteria.HibernateCriteriaBuilder;
import org.hibernate.query.criteria.JpaFrom;
import org.hibernate.query.sqm.tree.from.SqmFrom;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.CollectionUtils;

/**
 * Utility class for JPA operations including path resolution, join management, and specification
 * building.
 *
 * <p>This class provides helper methods for:
 *
 * <ul>
 *   <li>Computing JPA paths from property schemas
 *   <li>Managing joins for nested properties
 *   <li>Building "in" and "not in" specifications
 *   <li>Array aggregation for relationship queries (PostgreSQL-specific)
 * </ul>
 *
 * <p>Path resolution supports:
 *
 * <ul>
 *   <li>Simple paths (e.g., "name")
 *   <li>Nested paths with automatic join creation (e.g., "user.organization.name")
 *   <li>Join table relationships
 * </ul>
 *
 * <p><b>Note:</b> Some methods use PostgreSQL-specific functions (e.g., array_agg) and may not work
 * with other databases.
 *
 * @see FilterUtilsJpa for building filter specifications
 */
public final class JpaUtils {

  private JpaUtils() {
    // Utility class - prevent instantiation
  }

  /**
   * Returns {@code from}, treated as the subtype declaring {@code attribute} when only a subtype of
   * its entity declares it. Hibernate 6 resolved such attributes from the supertype path
   * implicitly; Hibernate 7 requires the treat, and {@code SchemaUtils#schemaWithSubtypes} offers
   * them (e.g. {@code AiAttack.category} when searching payloads, or {@code Endpoint.agents} when
   * joining an inject's assets).
   */
  @SuppressWarnings({"unchecked", "rawtypes"})
  public static From<?, ?> declaringFrom(
      @NotNull final From<?, ?> from, @NotNull final String attribute) {
    // A root's or a join's referenced path source is the entity type it ranges over.
    if (from instanceof SqmFrom<?, ?> sqmFrom
        && sqmFrom.getReferencedPathSource().getPathType() instanceof ManagedDomainType<?> type
        && type.findAttribute(attribute) == null) {
      PersistentAttribute<?, ?> subTypeAttribute = type.findSubTypesAttribute(attribute);
      if (subTypeAttribute != null) {
        return ((JpaFrom) sqmFrom).treatAs(subTypeAttribute.getDeclaringType().getJavaType());
      }
    }
    return from;
  }

  private static <U> Path<U> computePath(
      @NotNull final From<?, ?> from, @NotNull final String key) {
    String[] jsonPaths = key.split("\\.");

    // Deep path -> use join
    if (jsonPaths.length > 1) {
      From<?, ?> currentFrom = from;
      for (int i = 0; i < jsonPaths.length - 1; i++) {
        currentFrom = declaringFrom(currentFrom, jsonPaths[i]).join(jsonPaths[i], JoinType.LEFT);
      }
      // Last path part -> use get
      String last = jsonPaths[jsonPaths.length - 1];
      return declaringFrom(currentFrom, last).get(last);
    }

    // Simple path -> use get
    else if (jsonPaths.length == 1) {
      return declaringFrom(from, jsonPaths[0]).get(jsonPaths[0]);
    }

    return null;
  }

  public static <T, U> Expression<U> toPath(
      @NotNull final PropertySchema propertySchema,
      @NotNull final Root<T> root,
      @NotNull final Map<String, Join<Base, Base>> joinMap) {
    // Path
    if (hasText(propertySchema.getPath())) {
      if (joinMap.isEmpty()) {
        return computePath(root, propertySchema.getPath());
      }

      String existingPath = propertySchema.getPath();
      Join<Base, Base> existingJoin = null;
      String existingKey = null;

      // Compute existing join
      while (hasText(existingPath)) {
        if (joinMap.containsKey(existingPath)) {
          existingJoin = joinMap.get(existingPath);
          existingKey = existingPath;
          break;
        }
        // Nothing found -> exit
        int lastDotIndex = existingPath.lastIndexOf(".");
        if (lastDotIndex == -1) {
          break;
        }
        existingPath = existingPath.substring(0, existingPath.lastIndexOf("."));
      }

      // If existing join in joinMap
      if (existingJoin != null) {
        // If equals to key -> return it
        if (existingKey.equals(propertySchema.getPath())) {
          return (Expression<U>) existingJoin;
          // If not, compute the remaining path and return it
        } else {
          String remainingPath = propertySchema.getPath().substring(existingKey.length() + 1);
          return computePath(existingJoin, remainingPath);
        }
      }

      return computePath(root, propertySchema.getPath());
    }
    // Join
    if (propertySchema.getJoinTable() != null) {
      PropertySchema.JoinTable joinTable = propertySchema.getJoinTable();
      return declaringFrom(root, joinTable.getJoinOn())
          .join(joinTable.getJoinOn(), JoinType.LEFT)
          .get("id");
    } else {
      return declaringFrom(root, propertySchema.getName()).get(propertySchema.getName());
    }
  }

  // -- FUNCTION --

  public static <T, U> Expression<String[]> arrayAggOnId(
      @NotNull final HibernateCriteriaBuilder cb, @NotNull final Join<T, U> join) {
    return arrayAggOnField(cb, join, "id");
  }

  public static <T, U> Expression<String[]> arrayAggOnField(
      @NotNull final HibernateCriteriaBuilder cb,
      @NotNull final Join<T, U> join,
      @NotNull final String field) {
    Expression<String> nullString = cb.nullLiteral(String.class);
    Expression<String[]> arr = cb.arrayAgg(null, join.get(field));
    return cb.arrayRemove(arr, nullString);
  }

  // -- JOIN --

  public static <X, Y> Join<X, Y> createLeftJoin(Root<X> root, String attributeName) {
    return root.join(attributeName, JoinType.LEFT);
  }

  public static <X, Y> Expression<String[]> createJoinArrayAggOnId(
      CriteriaBuilder cb, Root<X> root, String attributeName) {
    Join<X, Y> join = createLeftJoin(root, attributeName);
    return arrayAggOnId((HibernateCriteriaBuilder) cb, join);
  }

  public static <X, Y> Expression<String[]> createJoinArrayAggOnIdForJoin(
      CriteriaBuilder cb, From<?, ?> from, String attributeName) {

    Join<X, Y> join = from.join(attributeName, JoinType.LEFT);

    return arrayAggOnId((HibernateCriteriaBuilder) cb, join);
  }

  /**
   * Create a "in" specification for searches
   *
   * @param fieldName the JPA field on which the in rule is based
   * @param inValues the values to include in the search for given field
   * @param <T> the data type of the specification (usually a JPA entity)
   * @return the built JPA Specification
   */
  public static <T> Specification<T> computeIn(
      @Nullable final String fieldName, @Nullable final List<String> inValues) {
    if (!hasText(fieldName) || CollectionUtils.isEmpty(inValues)) {
      return Specification.unrestricted();
    }
    return (root, query, cb) -> root.get(fieldName).in(inValues);
  }

  /**
   * Create a "not in" specification for searches
   *
   * @param fieldName the JPA field on which the exclusion rule is based
   * @param excludedValues the values to exclude from the search in given field
   * @param <T> the data type of the specification (usually a JPA entity)
   * @return the built JPA Specification
   */
  public static <T> Specification<T> computeNotIn(
      @Nullable final String fieldName, @Nullable final List<String> excludedValues) {
    return Specification.not(computeIn(fieldName, excludedValues));
  }
}
