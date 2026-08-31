package io.openaev.ocsf.parser.generator.emission.meta.field;

import io.openaev.ocsf.parser.generator.emission.Emitter;
import io.openaev.ocsf.parser.generator.emission.meta.Modifier;
import io.openaev.ocsf.parser.generator.emission.meta.annotation.AnnotationMeta;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.Getter;

public class FieldMeta implements Emitter {
  private final Set<AnnotationMeta> annotations = new HashSet<>();
  private final Modifier modifier;
  private final String type;
  @Getter private final String name;
  private String initialiser;

  public FieldMeta(Modifier modifier, String type, String name) {
    this.modifier = modifier;
    this.type = type;
    this.name = name;
  }

  public FieldMeta withAnnotation(AnnotationMeta annotationMeta) {
    annotations.add(annotationMeta);
    return this;
  }

  public FieldMeta withInitialiser(String initialiser) {
    this.initialiser = initialiser;
    return this;
  }

  @Override
  public String emit() {
    String render =
        this.annotations.stream().map(AnnotationMeta::emit).collect(Collectors.joining("\n"))
            + "\n"
            + modifier.getValue()
            + " "
            + type
            + " "
            + name;

    if (initialiser != null && !initialiser.isBlank()) {
      render += " = " + initialiser;
    }

    return render + ";";
  }
}
