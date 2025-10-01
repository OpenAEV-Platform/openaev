package io.openaev.stix.types;

import com.fasterxml.jackson.databind.JsonNode;
import io.openaev.stix.types.enums.HashingAlgorithms;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class Hashes extends BaseType<Map<HashingAlgorithms, java.lang.String>> {
  public Hashes(Map<HashingAlgorithms, java.lang.String> value) {
    super(value);
  }

  public static Hashes parseHashes(JsonNode node) {
    Map<HashingAlgorithms, String> hashes = new HashMap<>();
    Iterator<Map.Entry<String, JsonNode>> iterator = node.fields();
    while (iterator.hasNext()) {
      Map.Entry<java.lang.String, JsonNode> entry = iterator.next();
      hashes.put(HashingAlgorithms.fromValue(entry.getKey()), entry.getValue().asText());
    }
    return new Hashes(hashes);
  }
}
