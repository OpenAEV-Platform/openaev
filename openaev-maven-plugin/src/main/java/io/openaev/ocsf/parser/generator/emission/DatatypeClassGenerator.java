package io.openaev.ocsf.parser.generator.emission;

import com.fasterxml.jackson.databind.JsonNode;
import io.openaev.ocsf.parser.generator.emission.meta.Modifier;
import io.openaev.ocsf.parser.generator.emission.meta.annotation.AnnotationMeta;
import io.openaev.ocsf.parser.generator.emission.meta.cls.ClassMeta;
import io.openaev.ocsf.parser.generator.emission.meta.cls.ExtendMeta;
import io.openaev.ocsf.parser.generator.emission.meta.method.ArgumentMeta;
import io.openaev.ocsf.parser.generator.emission.meta.method.MethodMeta;
import io.openaev.ocsf.parser.schema.SchemaDimension;
import io.openaev.ocsf.parser.schema.Version;

public class DatatypeClassGenerator extends ClassGenerator {
  @Override
  public ClassMetadata metadata(Version version, String name, JsonNode source) {
    return new ClassMetadata(
        name,
        SchemaDimension.DATATYPES,
        compositeOcsfClassName(name),
        stringUtils.toVersionedPackage(version, SCHEMA_PACKAGE_NAME, "datatypes"),
        source);
  }

  @Override
  public String emit(ClassMetadata metadata) {
    String actualType = metadata.ocsfIdentifier();
    if (metadata.source().get("type") != null) {
      actualType = metadata.source().get("type").asText();
    }

    Class<?> type = mapDatatypeToClass(actualType);

    ClassMeta meta =
        new ClassMeta()
            .withImport(SCHEMA_PACKAGE_NAME + ".BaseType")
            .withName(compositeOcsfClassName(metadata.ocsfIdentifier()))
            .withPackage(metadata.classPackage())
            .withExtend(new ExtendMeta("BaseType").withGenericTypeArgument(type))
            // ctor
            .withMethod(
                new MethodMeta(
                        Modifier.PUBLIC,
                        compositeOcsfClassName(metadata.ocsfIdentifier()),
                        "",
                        "super(value);")
                    .withArgument(new ArgumentMeta(type, "value")));
    if (metadata.source().get("regex") != null) {
      meta.withMethod(
          new MethodMeta(
                  Modifier.PROTECTED,
                  "boolean",
                  "validate",
                  """
                      return getValue().matches("%s");
                      """
                      .formatted(metadata.source().get("regex").asText().replace("\\", "\\\\")))
              .withAnnotation(new AnnotationMeta(Override.class)));
    }

    return meta.emit();
  }

  private String compositeOcsfClassName(String name) {
    return "OcsfDatatype" + stringUtils.snakeToPascal(name);
  }

  private Class<?> mapDatatypeToClass(String datatype) {
    return switch (datatype) {
      case "boolean_t" -> Boolean.class;
      case "float_t" -> Float.class;
      case "integer_t" -> Integer.class;
      case "json_t" -> JsonNode.class;
      case "long_t" -> Long.class;
      case "string_t" -> String.class;
      default ->
          throw new IllegalArgumentException(
              "Cannot convert datatype %s into a java type.".formatted(datatype));
    };
  }
}
