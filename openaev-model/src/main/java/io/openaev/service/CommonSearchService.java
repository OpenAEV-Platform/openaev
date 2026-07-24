package io.openaev.service;

import io.openaev.engine.EngineContext;
import io.openaev.engine.EsModel;
import io.openaev.schema.PropertySchema;
import io.openaev.schema.SchemaUtils;
import java.lang.reflect.Field;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Service for common search operations across indexed entities.
 *
 * <p>This service provides access to the consolidated schema information for all indexable
 * entities, which is used by the search and filter system to validate and execute queries.
 *
 * @see PropertySchema
 * @see SchemaUtils
 */
@Service
@RequiredArgsConstructor
public class CommonSearchService {

  private final EngineContext searchEngine;

  /** Cache for property schemas, keyed by property name. */
  private static final ConcurrentHashMap<String, PropertySchema> cacheMap =
      new ConcurrentHashMap<>();

  /**
   * Cache for the denormalized {@code base_*_side} field names across all indexed models.
   * Instance-level (the service is a singleton bean) so tests with narrowed model sets stay
   * isolated. Null until the first computation; an empty scan result is cached too, so models are
   * never rescanned (benign race: concurrent first calls compute the same immutable snapshot).
   */
  private volatile Set<String> sideFieldNamesCache;

  /**
   * Returns the consolidated indexing schema for all searchable entities.
   *
   * <p>This method aggregates the filterable properties from all indexed entity models and caches
   * the result for subsequent calls. The schema is used by the filter system to validate filter
   * keys and determine available operators.
   *
   * @return a map of property name to PropertySchema for all filterable properties
   */
  public Map<String, PropertySchema> getIndexingSchema() {
    if (!cacheMap.isEmpty()) {
      return cacheMap;
    }
    Set<PropertySchema> properties =
        searchEngine.getModels().stream()
            .flatMap(
                model -> {
                  try {
                    return SchemaUtils.schemaWithSubtypes(model.getModel()).stream();
                  } catch (ClassNotFoundException e) {
                    throw new RuntimeException(e);
                  }
                })
            .filter(PropertySchema::isFilterable)
            .collect(Collectors.toSet());
    properties.forEach(p -> cacheMap.putIfAbsent(p.getName(), p));
    return cacheMap;
  }

  /**
   * Returns the name of every denormalized {@code base_*_side} field declared across the indexed
   * entity models (superclasses included). Documents are serialized straight from these classes, so
   * the Java field names are the exact engine field names.
   *
   * <p>Used by the bulk-delete side-reference cleanup to target concrete fields with one {@code
   * terms} clause each, instead of a {@code query_string} field wildcard whose per-id-per-field
   * expansion blows past the engine's boolean clause limit on large cascades.
   *
   * @return the sorted set of side field names (e.g. {@code base_tags_side})
   */
  public Set<String> getSideFieldNames() {
    Set<String> cached = sideFieldNamesCache;
    if (cached != null) {
      return cached;
    }
    Set<String> names = new TreeSet<>();
    for (EsModel<?> model : searchEngine.getModels()) {
      for (Class<?> clazz = model.getModel();
          clazz != null && clazz != Object.class;
          clazz = clazz.getSuperclass()) {
        for (Field field : clazz.getDeclaredFields()) {
          String name = field.getName();
          if (name.startsWith("base_") && name.endsWith("_side")) {
            names.add(name);
          }
        }
      }
    }
    Set<String> snapshot = Collections.unmodifiableSet(names);
    sideFieldNamesCache = snapshot;
    return snapshot;
  }
}
