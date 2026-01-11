package io.openaev.helper;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.util.StdDateFormat;
import com.fasterxml.jackson.datatype.hibernate6.Hibernate6Module;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/**
 * Helper class for creating pre-configured Jackson ObjectMapper instances.
 *
 * <p>Provides factory methods for creating ObjectMapper instances configured with OpenAEV-specific
 * settings, including support for Hibernate lazy loading, Java 8 time types, and optional values.
 *
 * <p>This is a utility class and cannot be instantiated.
 */
public final class ObjectMapperHelper {

  private ObjectMapperHelper() {}

  /**
   * Creates a new ObjectMapper configured for OpenAEV JSON processing.
   *
   * <p>The mapper is configured with:
   *
   * <ul>
   *   <li>Lenient deserialization (ignores unknown properties)
   *   <li>ISO-8601 date format with timezone colon
   *   <li>Hibernate 6 module for lazy loading support
   *   <li>JDK 8 module for Optional support
   *   <li>Java Time module for java.time.* types
   * </ul>
   *
   * @return a configured ObjectMapper instance
   */
  public static ObjectMapper openAEVJsonMapper() {
    ObjectMapper mapper = new ObjectMapper();
    mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    mapper.setDateFormat(new StdDateFormat().withColonInTimeZone(true));
    mapper.registerModule(new Hibernate6Module());
    mapper.registerModule(new Jdk8Module());
    mapper.registerModule(new JavaTimeModule());
    return mapper;
  }
}
