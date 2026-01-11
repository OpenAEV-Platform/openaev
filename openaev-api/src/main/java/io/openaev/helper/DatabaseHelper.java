package io.openaev.helper;

import static java.util.Optional.ofNullable;
import static org.springframework.util.StringUtils.hasLength;

import io.openaev.database.model.Base;
import io.openaev.rest.exception.ElementNotFoundException;
import java.util.Optional;
import org.springframework.data.repository.CrudRepository;

/**
 * Helper class for common database operations.
 *
 * <p>Provides utility methods for managing entity relationships during update operations,
 * simplifying the logic for handling optional and required relations.
 *
 * <p>This is a utility class and cannot be instantiated.
 *
 * @see io.openaev.database.model.Base
 */
public final class DatabaseHelper {

  private DatabaseHelper() {}

  /**
   * Updates an entity relationship, handling the case where the relation may change or be removed.
   *
   * <p>If a new relation ID is provided, attempts to find the entity. If the ID matches the current
   * relation, returns the current entity. If no ID is provided, returns null (removing the
   * relation).
   *
   * @param <T> the type of the related entity
   * @param inputRelationId the ID of the new related entity (may be null or empty)
   * @param current the current related entity (may be null)
   * @param repository the repository to find the entity in
   * @return the updated relation entity, or null if no relation
   */
  public static <T> T updateRelation(
      String inputRelationId, Base current, CrudRepository<T, String> repository) {
    if (hasLength(inputRelationId)) {
      String currentGroupId = ofNullable(current).map(Base::getId).orElse(null);
      if (!inputRelationId.equals(currentGroupId)) {
        Optional<T> existingEntity = repository.findById(inputRelationId);
        return existingEntity.orElse(null);
      }
      // noinspection unchecked
      return (T) current;
    }
    return null;
  }

  /**
   * Resolves an optional entity relation by ID.
   *
   * <p>Attempts to find an entity by ID if provided. Returns null if no ID is provided or if the
   * entity is not found.
   *
   * @param <T> the type of the related entity
   * @param inputRelationId the ID to look up (may be null or empty)
   * @param repository the repository to search in
   * @return the found entity, or null if not found or no ID provided
   */
  public static <T> T resolveOptionalRelation(
      String inputRelationId, CrudRepository<T, String> repository) {
    if (hasLength(inputRelationId)) {
      Optional<T> existingEntity = repository.findById(inputRelationId);
      return existingEntity.orElse(null);
    }
    return null;
  }

  /**
   * Resolves a required entity relation by ID.
   *
   * <p>Finds an entity by ID, throwing an exception if not found. Use this method when the relation
   * is mandatory and must exist.
   *
   * @param <T> the type of the related entity
   * @param inputRelationId the ID to look up
   * @param repository the repository to search in
   * @return the found entity
   * @throws ElementNotFoundException if the entity is not found
   */
  public static <T> T resolveRelation(
      String inputRelationId, CrudRepository<T, String> repository) {
    return repository.findById(inputRelationId).orElseThrow(ElementNotFoundException::new);
  }
}
