package io.openaev.ocsf.parser.generator.emission;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
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
import lombok.Getter;

public class ObjectClassGenerator extends ClassGenerator {
  @Override
  public ClassMetadata metadata(
      Version version,
      String name,
      JsonNode source,
      OcsfSchemaExtension extension,
      String ocsfClassUid) {
    return new ClassMetadata(
        name,
        ocsfClassUid,
        SchemaDimension.SINGLE_OBJECT,
        extension,
        compositeOcsfClassName(name),
        stringUtils.toVersionedPackage(version, SCHEMA_PACKAGE_NAME, "objects"),
        stringUtils.toVersionedPackage(version, SCHEMA_PACKAGE_NAME),
        source);
  }

  @Override
  public String emit(ClassMetadata metadata, DictionaryHelper helper) throws IOException {
    ClassMeta meta =
        new ClassMeta()
            .withAnnotation(new AnnotationMeta(Getter.class))
            .withImport(SCHEMA_PACKAGE_NAME + ".OcsfObject")
            .withName(compositeOcsfClassName(metadata.ocsfIdentifier()))
            .withPackage(metadata.classPackage())
            .withExtend(new ExtendMeta("OcsfObject"));

    JsonNode sourceNode = metadata.source();
    for (Map.Entry<String, JsonNode> attr : sourceNode.get("attributes").properties()) {
      Optional<OcsfSchemaExtension> extension =
          attr.getValue().has("extension")
              ? OcsfSchemaExtension.fromString(attr.getValue().get("extension").asText())
              : Optional.empty();
      String fieldType = helper.findClassNameFromOcsfAttribute(attr.getKey(), extension);
      FieldMeta fm =
          new FieldMeta(
                  Modifier.PRIVATE, fieldType, stringUtils.snakeToCamel(attr.getKey()) + "Field")
              .withAnnotation(
                  new AnnotationMeta(JsonProperty.class).withAttribute("value", attr.getKey()));
      if (fieldType.contains("OcsfDatatypeJsonT")) {
        fm.withAnnotation(
            new AnnotationMeta(JsonDeserialize.class)
                .withLiteralAttribute(
                    "using", metadata.schemaPackage() + ".ObjectNodeDeserialiser.class"));
      }
      meta = meta.withField(fm);
    }

    return meta.emit();
  }

  private String compositeOcsfClassName(String name) {
    return "OcsfObject" + stringUtils.snakeToPascal(name);
  }
}
