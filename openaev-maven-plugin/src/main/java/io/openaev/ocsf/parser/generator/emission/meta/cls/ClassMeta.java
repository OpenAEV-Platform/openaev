package io.openaev.ocsf.parser.generator.emission.meta.cls;

import io.openaev.ocsf.parser.generator.emission.Emitter;
import io.openaev.ocsf.parser.generator.emission.meta.annotation.AnnotationMeta;
import io.openaev.ocsf.parser.generator.emission.meta.field.FieldMeta;
import io.openaev.ocsf.parser.generator.emission.meta.method.MethodMeta;
import io.openaev.ocsf.parser.generator.emission.render.Helper;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

public class ClassMeta implements Emitter {
  private final Set<AnnotationMeta> annotations = new HashSet<>();
  private final Set<String> imports = new HashSet<>();
  private final Set<MethodMeta> methods = new HashSet<>();
  private final Set<FieldMeta> fields = new HashSet<>();
  private ExtendMeta extend;
  private String packageName;
  private String name;

  public ClassMeta withAnnotation(AnnotationMeta meta) {
    annotations.add(meta);
    return this;
  }

  public ClassMeta withMethod(MethodMeta meta) {
    methods.add(meta);
    return this;
  }

  public ClassMeta withField(FieldMeta meta) {
    fields.add(meta);
    return this;
  }

  public ClassMeta withExtend(ExtendMeta meta) {
    this.extend = meta;
    return this;
  }

  public ClassMeta withPackage(String packageName) {
    this.packageName = packageName;
    return this;
  }

  public ClassMeta withImport(String importSpec) {
    this.imports.add(importSpec);
    return this;
  }

  public ClassMeta withName(String name) {
    this.name = name;
    return this;
  }

  @Override
  public String emit() {
    return "package "
        + this.packageName
        + ";"
        + "\n\n"
        + this.imports.stream()
            .map(imprt -> "import " + imprt + ";")
            .collect(Collectors.joining("\n"))
        + "\n\n"
        + this.annotations.stream().map(AnnotationMeta::emit).collect(Collectors.joining("\n"))
        + "\n"
        + "public class "
        + this.name
        + (this.extend != null ? " extends " + this.extend.emit() : "")
        + " {"
        + "\n"
        + Helper.indent(
            1, this.fields.stream().map(FieldMeta::emit).collect(Collectors.joining("\n\n")))
        + "\n"
        + Helper.indent(
            1, this.methods.stream().map(MethodMeta::emit).collect(Collectors.joining("\n\n")))
        + "\n"
        + "}\n";
  }
}
