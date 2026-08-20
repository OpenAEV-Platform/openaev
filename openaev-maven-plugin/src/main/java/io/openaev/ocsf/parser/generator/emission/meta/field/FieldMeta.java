package io.openaev.ocsf.parser.generator.emission.meta.field;

import io.openaev.ocsf.parser.generator.emission.Emitter;
import io.openaev.ocsf.parser.generator.emission.meta.Modifier;
import io.openaev.ocsf.parser.generator.emission.meta.annotation.AnnotationMeta;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

public class FieldMeta implements Emitter {
  private final Set<AnnotationMeta> annotations = new HashSet<>();
  private final Modifier modifier;
  private final String type;
  private final String name;

  public FieldMeta(Modifier modifier, String type, String name) {
    this.modifier = modifier;
    this.type = type;
    this.name = name;
  }

  public FieldMeta withAnnotation(AnnotationMeta annotationMeta) {
    annotations.add(annotationMeta);
    return this;
  }

  @Override
  public String emit() {
    return this.annotations.stream().map(AnnotationMeta::emit).collect(Collectors.joining("\n"))
        + "\n"
        + modifier.getValue()
        + " "
        + type
        + " "
        + name
        + ";";
  }
}
