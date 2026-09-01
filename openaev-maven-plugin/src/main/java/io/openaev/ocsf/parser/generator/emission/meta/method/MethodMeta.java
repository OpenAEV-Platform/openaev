package io.openaev.ocsf.parser.generator.emission.meta.method;

import io.openaev.ocsf.parser.generator.emission.Emitter;
import io.openaev.ocsf.parser.generator.emission.meta.Modifier;
import io.openaev.ocsf.parser.generator.emission.meta.annotation.AnnotationMeta;
import io.openaev.ocsf.parser.generator.emission.render.Helper;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.Getter;

public class MethodMeta implements Emitter {
  private final Set<AnnotationMeta> annotations = new HashSet<>();
  private final List<ArgumentMeta> arguments = new ArrayList<>();
  private final Set<Class<? extends Throwable>> throwables = new HashSet<>();
  private final Modifier modifier;
  private final String returnType;
  @Getter private final String name;
  private final String body;

  public MethodMeta(Modifier modifier, String returnType, String name, String body) {
    this.modifier = modifier;
    this.returnType = returnType;
    this.name = name;
    this.body = body;
  }

  public MethodMeta withAnnotation(AnnotationMeta meta) {
    annotations.add(meta);
    return this;
  }

  public MethodMeta withArgument(ArgumentMeta meta) {
    arguments.add(meta);
    return this;
  }

  public MethodMeta withThrow(Class<? extends Throwable> throwableClass) {
    throwables.add(throwableClass);
    return this;
  }

  @Override
  public String emit() {
    String render =
        (this.annotations.isEmpty()
                ? ""
                : this.annotations.stream()
                        .map(AnnotationMeta::emit)
                        .collect(Collectors.joining("\n"))
                    + "\n")
            + modifier.getValue()
            + " "
            + returnType
            + " "
            + name
            + "("
            + this.arguments.stream().map(ArgumentMeta::emit).collect(Collectors.joining(", "))
            + ")";
    if (throwables.stream().findAny().isPresent()) {
      render +=
          " throws "
              + throwables.stream().map(Class::getCanonicalName).collect(Collectors.joining(", "));
    }
    render += " {" + "\n" + Helper.indent(1, body.stripTrailing()) + "\n" + "}";

    return render;
  }
}
