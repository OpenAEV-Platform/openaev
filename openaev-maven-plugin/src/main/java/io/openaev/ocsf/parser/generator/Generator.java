package io.openaev.ocsf.parser.generator;

import com.fasterxml.jackson.databind.JsonNode;
import io.openaev.utils.StringUtils;
import java.util.HashMap;
import java.util.Map;

public abstract class Generator {
  protected final StringUtils stringUtils = new StringUtils();
  private final Map<String, ClassMetadata> tracker = new HashMap<>();

  protected abstract ClassMetadata innerEmit(String name, JsonNode source);

  public ClassMetadata emit(String name, JsonNode source) {
    ClassMetadata md = innerEmit(name, source);
    tracker.put(name, md);
    return md;
  }
}
