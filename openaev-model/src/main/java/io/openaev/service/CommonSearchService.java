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

@Service
@RequiredArgsConstructor
public class CommonSearchService {

  private final EngineContext searchEngine;

  private static final ConcurrentHashMap<String, PropertySchema> cacheMap =
      new ConcurrentHashMap<>();

  // TODO Test cache
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
