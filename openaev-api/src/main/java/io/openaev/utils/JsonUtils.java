package io.openaev.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.BooleanNode;
import com.fasterxml.jackson.databind.node.IntNode;
import com.fasterxml.jackson.databind.node.TextNode;

public class JsonUtils {
  public static JsonNode toJsonNode(String strValue) throws JsonProcessingException {
    return new TextNode(strValue);
  }

  public static JsonNode toJsonNode(int intValue) throws JsonProcessingException {
    return new IntNode(intValue);
  }

  public static JsonNode toJsonNode(boolean boolValue) throws JsonProcessingException {
    return boolValue ? BooleanNode.TRUE : BooleanNode.FALSE;
  }

  public static Object fromJsonNode(JsonNode node, Class<?> desiredClass)
      throws JsonProcessingException {
    return new ObjectMapper().treeToValue(node, desiredClass);
  }
}
