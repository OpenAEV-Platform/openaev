package io.openaev.stix.parsing;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public interface StixSerialisable {
  JsonNode toStix(ObjectMapper mapper);
}
