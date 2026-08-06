package io.openaev.ocsf.parser.generator;

import com.fasterxml.jackson.databind.JsonNode;
import io.openaev.ocsf.parser.schema.SchemaDimension;
import java.util.HashMap;
import java.util.Map;

public class Generator {
  private static final String datatypesPackageName = "io.openaev.ocsf.datatypes";
  private final Map<String, ClassMetadata> tracker = new HashMap<>();

  public ClassMetadata emit(SchemaDimension dimension, String name, JsonNode source) {
    ClassMetadata md =
        switch (dimension) {
          case DATATYPES -> emitDatatypeClass(name, source);
          default ->
              throw new UnsupportedOperationException(
                  "Cannot emit code for dimension %s".formatted(dimension.name()));
        };
    tracker.put(name, md);
    return md;
  }

  private ClassMetadata emitDatatypeClass(String name, JsonNode source) {
    return new ClassMetadata(name, "Ocsf" + name, datatypesPackageName);
  }
}
