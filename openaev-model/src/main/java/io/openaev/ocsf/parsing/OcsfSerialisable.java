package io.openaev.ocsf.parsing;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public interface OcsfSerialisable {
  JsonNode toOcsf(ObjectMapper mapper);
}
