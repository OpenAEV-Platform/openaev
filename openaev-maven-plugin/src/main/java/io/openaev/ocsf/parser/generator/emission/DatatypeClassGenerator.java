package io.openaev.ocsf.parser.generator.emission;

import com.fasterxml.jackson.databind.JsonNode;
import io.openaev.ocsf.parser.generator.emission.meta.Modifier;
import io.openaev.ocsf.parser.generator.emission.meta.annotation.AnnotationMeta;
import io.openaev.ocsf.parser.generator.emission.meta.cls.ClassMeta;
import io.openaev.ocsf.parser.generator.emission.meta.cls.ExtendMeta;
import io.openaev.ocsf.parser.generator.emission.meta.method.ArgumentMeta;
import io.openaev.ocsf.parser.generator.emission.meta.method.MethodMeta;

public class DatatypeClassGenerator extends ClassGenerator {
  private static final String datatypesPackageName = "io.openaev.ocsf.datatypes";

  @Override
  public ClassMetadata metadata(String name, JsonNode source) {
    return new ClassMetadata(name, compositeOcsfClassName(name), datatypesPackageName, source);
  }

  @Override
  public String emit(ClassMetadata metadata, JsonNode source) {
    String actualType = metadata.ocsfIdentifier();
    if (source.get("type") != null) {
      actualType = source.get("type").asText();
    }

    Class<?> type = mapDatatypeToClass(actualType);

    ClassMeta meta =
        new ClassMeta()
            .withName(compositeOcsfClassName(metadata.ocsfIdentifier()))
            .withPackage(datatypesPackageName)
            .withExtend(new ExtendMeta("BaseType").withGenericTypeArgument(type))
            // ctor
            .withMethod(
                new MethodMeta(
                        Modifier.PUBLIC,
                        compositeOcsfClassName(metadata.ocsfIdentifier()),
                        "",
                        "super(value);")
                    .withArgument(new ArgumentMeta(type, "value")));
    if (source.get("regex") != null) {
      meta.withMethod(
          new MethodMeta(
                  Modifier.PROTECTED,
                  "boolean",
                  "validate",
                  """
                      return getValue().matches("%s");
                      """
                      .formatted(source.get("regex").asText().replace("\n", "")))
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
