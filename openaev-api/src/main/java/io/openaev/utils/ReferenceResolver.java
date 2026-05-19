package io.openaev.utils;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityNotFoundException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/** Resolves a list of entity IDs into JPA proxy references with a single COUNT validation query. */
@Component
@RequiredArgsConstructor
public class ReferenceResolver {

  private final EntityManager entityManager;

  /** Resolves IDs into JPA proxy references after validating existence. */
  public <T> Set<T> resolve(
      List<String> ids, Class<T> entityClass, Function<Set<String>, Long> countByIdIn) {
    if (ids == null || ids.isEmpty()) return new HashSet<>();
    Set<String> uniqueIds = ids.stream().filter(StringUtils::hasText).collect(Collectors.toSet());
    if (uniqueIds.isEmpty()) return new HashSet<>();
    long found = countByIdIn.apply(uniqueIds);
    if (found != uniqueIds.size()) {
      throw new EntityNotFoundException(
          "One or more " + entityClass.getSimpleName() + " not found in: " + uniqueIds);
    }
    return uniqueIds.stream()
        .map(id -> entityManager.getReference(entityClass, id))
        .collect(Collectors.toSet());
  }

  /** Resolves ID into JPA proxy references after validating existence. */
  public <T> T resolve(String id, Class<T> entityClass) {
    if (!StringUtils.hasText(id)) return null;
    return entityManager.getReference(entityClass, id);
  }
}
