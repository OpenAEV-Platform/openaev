package io.openaev.helper;

import static java.util.Optional.ofNullable;
import static org.springframework.util.StringUtils.hasLength;

import io.openaev.database.model.Base;
import io.openaev.rest.exception.ElementNotFoundException;
import java.util.Optional;
import org.springframework.data.repository.CrudRepository;

public class DatabaseHelper {

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

  public static <T> T resolveOptionalRelation(
      String inputRelationId, CrudRepository<T, String> repository) {
    if (hasLength(inputRelationId)) {
      Optional<T> existingEntity = repository.findById(inputRelationId);
      return existingEntity.orElse(null);
    }
    return null;
  }

  public static <T> T resolveRelation(
      String inputRelationId, CrudRepository<T, String> repository) {
    return repository.findById(inputRelationId).orElseThrow(ElementNotFoundException::new);
  }
}
