package io.openbas.stix.parsing;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public interface StixSerialisable {
  JsonNode toStix(ObjectMapper mapper);
}
