package io.openaev.ocsf.schema;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.ocsf.parsing.OcsfSerialisable;

@JsonInclude(JsonInclude.Include.NON_NULL)
public abstract class OcsfClass implements OcsfSerialisable {

  @Override
  public JsonNode toOcsf(ObjectMapper mapper) {
    return mapper.valueToTree(this);
  }
}
