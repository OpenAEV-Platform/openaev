package io.openaev.service;

import io.openaev.engine.EngineContext;
import io.openaev.schema.PropertySchema;
import io.openaev.schema.SchemaUtils;
import java.util.Map;
import java.util.Set;
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
}
