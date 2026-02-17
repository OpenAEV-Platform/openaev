package io.openaev.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.helper.ObjectMapperHelper;

/**
 * Utility class for JSON serialization and deserialization operations.
 *
 * <p>Provides convenience methods for working with Jackson's JSON processing capabilities using the
 * OpenAEV-configured ObjectMapper instance.
 *
 * <p>This is a utility class and cannot be instantiated.
 *
 * @see io.openaev.helper.ObjectMapperHelper
 */
public class JsonUtils {

  private JsonUtils() {}

  /** Shared ObjectMapper instance configured for OpenAEV JSON processing. */
  private static final ObjectMapper MAPPER = ObjectMapperHelper.openAEVJsonMapper();

  /**
   * Converts a Jackson JsonNode to an object of the specified type.
   *
   * <p>Uses the OpenAEV-configured ObjectMapper to deserialize the JSON tree structure into the
   * target class.
   *
   * @param node the JSON node to convert
   * @param desiredClass the target class type for deserialization
   * @return an instance of the specified class populated from the JSON node
   * @throws JsonProcessingException if the JSON cannot be processed or converted to the target type
   */
  public static Object fromJsonNode(JsonNode node, Class<?> desiredClass)
      throws JsonProcessingException {
    return MAPPER.treeToValue(node, desiredClass);
  }
}
