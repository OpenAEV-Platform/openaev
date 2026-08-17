package io.openaev.ocsf.parser.generator;

import com.fasterxml.jackson.databind.JsonNode;
import io.openaev.ocsf.parser.generator.emission.meta.Modifier;
import io.openaev.ocsf.parser.generator.emission.meta.annotation.AnnotationMeta;
import io.openaev.ocsf.parser.generator.emission.meta.cls.ClassMeta;
import io.openaev.ocsf.parser.generator.emission.meta.cls.ExtendMeta;
import io.openaev.ocsf.parser.generator.emission.meta.method.ArgumentMeta;
import io.openaev.ocsf.parser.generator.emission.meta.method.MethodMeta;
import io.openaev.ocsf.parser.schema.SchemaDimension;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
    String actualType = name;
    if (source.get("type") != null) {
      actualType = source.get("type").asText();
    }

    Class<?> type = mapDatatypeToClass(actualType);

    ClassMeta meta =
        new ClassMeta()
            .withName(compositeOcsfClassName(name))
            .withPackage(datatypesPackageName)
            .withExtend(new ExtendMeta("BaseType").withGenericTypeArgument(type))
            // ctor
            .withMethod(
                new MethodMeta(Modifier.PUBLIC, compositeOcsfClassName(name), "", "super(value);")
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
    return new ClassMetadata(name, compositeOcsfClassName(name), datatypesPackageName);
  }

  private String compositeOcsfClassName(String name) {
    return "OcsfDatatype" + snakeToPascal(name);
  }

  private String snakeToPascal(String snake) {
    StringBuilder sb = new StringBuilder();
    Matcher firstChar = Pattern.compile("(^\\w|_\\w)").matcher(snake);
    while (firstChar.find()) {
      firstChar.appendReplacement(sb, firstChar.group(1).replace("_", "").toUpperCase());
    }
    firstChar.appendTail(sb);
    return sb.toString();
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
