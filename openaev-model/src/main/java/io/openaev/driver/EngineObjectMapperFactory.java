package io.openaev.driver;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/**
 * Factory for the shared {@link ObjectMapper} used by search-engine drivers (Elasticsearch,
 * OpenSearch) and any component that needs identical serialization settings.
 */
public class EngineObjectMapperFactory {

  private EngineObjectMapperFactory() {}

  /**
   * Creates an ObjectMapper configured for search-engine document serialization: ISO-8601 dates,
   * lenient deserialization, and all fields included.
   */
  public static ObjectMapper create() {
    ObjectMapper mapper = new ObjectMapper();
    mapper.registerModule(new JavaTimeModule());
    mapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
    mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    mapper.setSerializationInclusion(JsonInclude.Include.ALWAYS);
    return mapper;
  }
}
