package io.openaev.ocsf.parser.generator.utility;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.ocsf.parser.generator.emission.ClassMetadata;
import io.openaev.ocsf.parser.generator.emission.Emitter;
import io.openaev.ocsf.parser.generator.emission.meta.Modifier;
import io.openaev.ocsf.parser.generator.emission.meta.cls.ClassMeta;
import io.openaev.ocsf.parser.generator.emission.meta.field.FieldMeta;
import io.openaev.ocsf.parser.generator.emission.meta.method.ArgumentMeta;
import io.openaev.ocsf.parser.generator.emission.meta.method.MethodMeta;
import java.util.Map;

public class OcsfConverterEmitter implements Emitter {
  private final Map<String, ClassMetadata> tracker;
  private final String packageName;

  public OcsfConverterEmitter(Map<String, ClassMetadata> tracker, String packageName) {
    this.tracker = tracker;
    this.packageName = packageName;
  }

  @Override
  public String emit() {
    // converter
    ClassMeta converterBodyMeta =
        new ClassMeta()
            .withImport(ObjectMapper.class.getCanonicalName())
            .withName("OcsfConverter")
            .withPackage(packageName)
            .withField(
                new FieldMeta(Modifier.PRIVATE, ObjectMapper.class.getTypeName(), "mapper")
                    .withInitialiser("new ObjectMapper()"));
    for (ClassMetadata md : tracker.values()) {
      switch (md.dimension()) {
        case SINGLE_OBJECT, SINGLE_CLASS ->
            converterBodyMeta =
                converterBodyMeta.withMethod(
                    new MethodMeta(
                            Modifier.PUBLIC,
                            md.fullyQualifiedClassName(),
                            "to" + md.className(),
                            "return mapper.treeToValue(node, "
                                + md.fullyQualifiedClassName()
                                + ".class);")
                        .withArgument(new ArgumentMeta(JsonNode.class, "node"))
                        .withThrow(JsonProcessingException.class));
      }
    }

    return converterBodyMeta.emit();
  }
}
