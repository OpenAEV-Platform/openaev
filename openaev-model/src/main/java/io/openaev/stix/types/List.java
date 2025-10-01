package io.openaev.stix.types;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class List<T extends BaseType<?>> extends BaseType<java.util.List<T>> {
  public List(java.util.List<T> value) {
    super(value);
  }

  @Override
  public JsonNode toStix(ObjectMapper mapper) {
    return mapper.valueToTree(this.getValue().stream().map(o -> o.toStix(mapper)).toList());
  }
}
