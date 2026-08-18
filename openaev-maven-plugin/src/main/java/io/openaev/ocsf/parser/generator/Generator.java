package io.openaev.ocsf.parser.generator;

import io.openaev.ocsf.parser.generator.emission.ClassClassGenerator;
import io.openaev.ocsf.parser.generator.emission.ClassMetadata;
import io.openaev.ocsf.parser.generator.emission.DatatypeClassGenerator;
import io.openaev.ocsf.parser.generator.emission.ObjectClassGenerator;
import io.openaev.ocsf.parser.schema.SchemaSource;
import io.openaev.ocsf.parser.schema.source.Source;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class Generator {
  private final ClassClassGenerator classClassGenerator = new ClassClassGenerator();
  private final ObjectClassGenerator objectClassGenerator = new ObjectClassGenerator();
  private final DatatypeClassGenerator datatypeClassGenerator = new DatatypeClassGenerator();
  private final Map<String, ClassMetadata> tracker = new HashMap<>();
  private final SchemaSource schemaSource;

  public Generator(SchemaSource schemaSource) {
    this.schemaSource = schemaSource;
  }

  public void generate() throws IOException {
    for (Source src : schemaSource.getSources()) {
      switch (src.getDimension()) {
        case SINGLE_OBJECT ->
            tracker.put(src.getName(), objectClassGenerator.metadata(src.getName(), src.get()));
        case SINGLE_CLASS ->
            tracker.put(src.getName(), classClassGenerator.metadata(src.getName(), src.get()));
        case DATATYPES ->
            src.get()
                .propertyStream()
                .forEach(
                    prop ->
                        tracker.put(
                            prop.getKey(),
                            datatypeClassGenerator.metadata(prop.getKey(), prop.getValue())));
      }
    }
  }
}
