package io.openaev.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.helper.ObjectMapperHelper;

public class JsonUtils {

  private JsonUtils() {}

  private static final ObjectMapper MAPPER = ObjectMapperHelper.openAEVJsonMapper();

  public static Object fromJsonNode(JsonNode node, Class<?> desiredClass)
      throws JsonProcessingException {
    return MAPPER.treeToValue(node, desiredClass);
  }
}
