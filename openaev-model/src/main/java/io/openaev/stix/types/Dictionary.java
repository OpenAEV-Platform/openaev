package io.openaev.stix.types;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Map;

public class Dictionary extends BaseType<Map<java.lang.String, BaseType<?>>> {
  public Dictionary(Map<java.lang.String, BaseType<?>> value) {
    super(value);
  }

  @Override
  public JsonNode toStix(ObjectMapper mapper) {
    ObjectNode node = mapper.createObjectNode();
    for (Map.Entry<String, BaseType<?>> entry : this.getValue().entrySet()) {
      node.set(entry.getKey(), entry.getValue().toStix(mapper));
    }
    return node;
  }
}
