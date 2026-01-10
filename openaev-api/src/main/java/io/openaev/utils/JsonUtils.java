package io.openaev.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class JsonUtils {

  private JsonUtils() {}

  public static Object fromJsonNode(JsonNode node, Class<?> desiredClass)
      throws JsonProcessingException {
    return new ObjectMapper().treeToValue(node, desiredClass);
  }
}
