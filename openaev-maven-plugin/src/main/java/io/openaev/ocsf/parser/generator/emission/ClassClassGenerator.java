package io.openaev.ocsf.parser.generator.emission;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import io.openaev.ocsf.parser.client.url.OcsfSchemaExtension;
import io.openaev.ocsf.parser.generator.emission.meta.Modifier;
import io.openaev.ocsf.parser.generator.emission.meta.annotation.AnnotationMeta;
import io.openaev.ocsf.parser.generator.emission.meta.cls.ClassMeta;
import io.openaev.ocsf.parser.generator.emission.meta.cls.ExtendMeta;
import io.openaev.ocsf.parser.generator.emission.meta.field.FieldMeta;
import io.openaev.ocsf.parser.schema.SchemaDimension;
import io.openaev.ocsf.parser.schema.Version;
import io.openaev.utils.DictionaryHelper;
import java.io.IOException;
import java.util.Map;
import java.util.Optional;

public class ClassClassGenerator extends ClassGenerator {
  @Override
  public ClassMetadata metadata(
      Version version, String name, JsonNode source, OcsfSchemaExtension extension) {
    return new ClassMetadata(
        name,
        SchemaDimension.SINGLE_CLASS,
        extension,
        compositeOcsfClassName(name),
        stringUtils.toVersionedPackage(version, SCHEMA_PACKAGE_NAME, "classes"),
        source);
  }

  @Override
  public String emit(ClassMetadata metadata, DictionaryHelper helper) throws IOException {
    ClassMeta meta =
        new ClassMeta()
            .withImport(SCHEMA_PACKAGE_NAME + ".OcsfClass")
            .withName(compositeOcsfClassName(metadata.ocsfIdentifier()))
            .withPackage(metadata.classPackage())
            .withExtend(new ExtendMeta("OcsfClass"));

    JsonNode sourceNode = metadata.source();
    for (Map.Entry<String, JsonNode> attr : sourceNode.get("attributes").properties()) {
      Optional<OcsfSchemaExtension> extension =
          attr.getValue().has("extension")
              ? OcsfSchemaExtension.fromString(attr.getValue().get("extension").asText())
              : Optional.empty();
      meta =
          meta.withField(
              new FieldMeta(
                      Modifier.PRIVATE,
                      helper.findClassNameFromOcsfAttribute(attr.getKey(), extension),
                      stringUtils.snakeToCamel(attr.getKey()) + "Field")
                  .withAnnotation(
                      new AnnotationMeta(JsonProperty.class)
                          .withAttribute("value", attr.getKey())));
    }

    return meta.emit();
  }

  private String compositeOcsfClassName(String name) {
    return "OcsfClass" + stringUtils.snakeToPascal(name);
  }
}
