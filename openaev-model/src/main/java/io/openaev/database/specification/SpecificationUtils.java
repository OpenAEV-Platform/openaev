package io.openaev.database.specification;

import io.openaev.database.model.*;
import jakarta.persistence.criteria.*;
import jakarta.validation.constraints.NotBlank;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public class SpecificationUtils {

  private SpecificationUtils() {}

  /**
   * Full Text Search with several properties instead of just one
   *
   * @param searchTerm the search term
   * @param properties the properties to check
   */
  public static <T extends Base> Specification<T> fullTextSearch(
      @NotBlank final String searchTerm, @NotBlank final List<String> properties) {
    return (root, query, cb) -> {
      List<Predicate> listOfPredicates = new ArrayList<>();
      for (String property : properties) {
        Expression<Double> tsVector =
            cb.function("to_tsvector", Double.class, cb.literal("simple"), root.get(property));
        Expression<Double> tsQuery =
            cb.function("to_tsquery", Double.class, cb.literal("simple"), cb.literal(searchTerm));
        Expression<Double> rank = cb.function("ts_rank", Double.class, tsVector, tsQuery);
        query.orderBy(cb.desc(rank));
        listOfPredicates.add(cb.greaterThan(rank, 0.01));
      }

      return cb.or(listOfPredicates.toArray(new Predicate[0]));
    };
  }

  /**
   * Specification to filter entities based on user grants. Usage:
   * myResourceSearchSpecification.and(SpecificationUtils.hasGrantAccess(grantsFilterName, userId,
   * isAdmin)) Only works for entities that have the @Grantable annotation, return cb.conjunction()
   * otherwise.
   *
   * @param userId ID of the user performing the search
   * @param isAdminOrBypass true id the user performing the search has admin privileges
   * @param hasCapaForClass true id the user has the capa for the specific class to bypass grants
   * @param grantType the minimum grant type required to access the resource
   */
  public static <T extends Base> Specification<T> hasGrantAccess(
      final String userId,
      final boolean isAdminOrBypass,
      boolean hasCapaForClass,
      Grant.GRANT_TYPE grantType) {
    return (root, query, cb) -> {
      Class<?> entityClass = root.getJavaType();

      // If the user is an admin or there is no grant system associated with T
      if (isAdminOrBypass
          || !GrantableBase.class.isAssignableFrom(entityClass)
          || hasCapaForClass) {
        return cb.conjunction(); // Always true
      }

      List<Grant.GRANT_TYPE> allowedGrantTypes = grantType.andHigher();
      Grant.GRANT_RESOURCE_TYPE resourceType = GrantableBase.getGrantResourceType(entityClass);

      // If this entity is an Inject (atomic testing), grants can target:
      //  - the inject itself (atomic testing id)
      //  - OR the linked scenario id
      //  - OR the linked exercise/simulation id
      //
      // So expand the set of resource types we accept in the grant subquery.
      List<Grant.GRANT_RESOURCE_TYPE> resourceTypes = new ArrayList<>();
      if (resourceType == Grant.GRANT_RESOURCE_TYPE.ATOMIC_TESTING) {
        // add the atomic-type itself
        resourceTypes.add(resourceType);
        // add parent-level resource types that can also grant access
        // adjust names if your model uses SIMULATION vs EXERCISE naming
        resourceTypes.add(Grant.GRANT_RESOURCE_TYPE.SCENARIO);
        resourceTypes.add(Grant.GRANT_RESOURCE_TYPE.SIMULATION);
      } else {
        resourceTypes.add(resourceType);
      }

      // Add grant filtering
      // Create subquery to find all resource IDs the user can access
      Subquery<String> accessibleResources = query.subquery(String.class);
      // We'll query from grants table
      Root<Grant> grantTable = accessibleResources.from(Grant.class);
      // Join to groups table (Grant has a 'group' field of type Group)
      Join<Grant, Group> groupTable = grantTable.join("group");
      // Join to users (Group has a 'users' collection)
      Join<Group, User> userTable = groupTable.join("users");
      // Join to the resource table (Grant class has a '{resource}' field of type ResourceType)
      accessibleResources.select(grantTable.get("resourceId"));
      // WHERE the user in the join matches our userId AND grant name is of the given value
      accessibleResources.where(
          cb.and(
              cb.equal(userTable.get("id"), userId),
              grantTable.get("grantResourceType").in(resourceTypes),
              grantTable.get("name").in(allowedGrantTypes)));

      // Special handling for Inject: check its own id OR its scenario.id OR its exercise.id
      if (Inject.class.isAssignableFrom(entityClass)) {
        Predicate byInjectId = root.get("id").in(accessibleResources);
        Predicate byScenarioId = root.get("scenario").get("id").in(accessibleResources);
        Predicate byExerciseId = root.get("exercise").get("id").in(accessibleResources);
        return cb.or(byInjectId, byScenarioId, byExerciseId);
      }

      // Default: entity id must be in accessible resources
      return root.get("id").in(accessibleResources);
    };
  }

  /**
   * Creates a subquery that returns all resource IDs of a specific type that the user has access to
   * through grants. Can be used to get injects belonging to a scenario or simulation where the user
   * is granted, for example.
   *
   * @param query The parent criteria query used to create the subquery
   * @param cb The criteria builder for constructing query predicates
   * @param userId The ID of the user whose accessible resources are being queried
   * @param resourceType The type of resource to filter by (e.g., "SCENARIO", "EXERCISE")
   * @param allowedGrantTypes List of grant types that provide access (e.g., OBSERVER and higher)
   * @return A subquery that selects resource IDs the user can access
   */
  public static Subquery<String> accessibleResourcesSubquery(
      CriteriaQuery<?> query,
      CriteriaBuilder cb,
      String userId,
      Grant.GRANT_RESOURCE_TYPE resourceType,
      List<Grant.GRANT_TYPE> allowedGrantTypes) {

    Subquery<String> accessibleResources = query.subquery(String.class);
    Root<Grant> grantTable = accessibleResources.from(Grant.class);
    Join<Grant, Group> groupTable = grantTable.join("group");
    Join<Group, User> userTable = groupTable.join("users");

    // Select resourceId directly since we're not joining to the resource table
    accessibleResources.select(grantTable.get("resourceId"));

    // WHERE user matches AND resourceType matches AND grant type is allowed
    accessibleResources.where(
        cb.and(
            cb.equal(userTable.get("id"), userId),
            cb.equal(grantTable.get("grantResourceType"), resourceType),
            grantTable.get("name").in(allowedGrantTypes)));

    return accessibleResources;
  }

  public static <T extends GrantableBase>
      BiFunction<Specification<T>, Pageable, Page<T>> withGrantFilter(
          JpaSpecificationExecutor<T> repo,
          Grant.GRANT_TYPE grantType,
          String userId,
          boolean isAdminOrBypass,
          boolean hasCapaForClass) {
    return (spec, pageable) -> {
      Specification<T> grantSpec =
          SpecificationUtils.hasGrantAccess(userId, isAdminOrBypass, hasCapaForClass, grantType);
      return repo.findAll(spec == null ? grantSpec : spec.and(grantSpec), pageable);
    };
  }

  public static <T extends Base> Specification<T> hasIdIn(List<String> ids) {
    return (root, query, cb) -> root.get("id").in(ids);
  }
}
