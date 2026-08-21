package io.openaev.ocsf.schema;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.ocsf.parsing.OcsfSerialisable;

public abstract class OcsfObject implements OcsfSerialisable {

  @Override
  public JsonNode toOcsf(ObjectMapper mapper) {
    return mapper.valueToTree(this);
  }
}
