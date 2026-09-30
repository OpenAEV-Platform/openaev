package io.openaev.ocsf.parser.generator.emission.meta.enums;

import io.openaev.ocsf.parser.generator.emission.Emitter;
import io.openaev.ocsf.parser.generator.emission.meta.field.FieldMeta;
import io.openaev.ocsf.parser.generator.emission.meta.method.MethodMeta;
import io.openaev.ocsf.parser.generator.emission.render.Helper;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

public class EnumMeta implements Emitter {
  private final Set<String> imports = new HashSet<>();
  private final Set<OptionMeta> options = new HashSet<>();
  private final Set<MethodMeta> methods = new HashSet<>();
  private final Set<FieldMeta> fields = new HashSet<>();
  private String packageName;
  private String name;

  public EnumMeta withOption(OptionMeta option) {
    options.add(option);
    return this;
  }

  public EnumMeta withMethod(MethodMeta meta) {
    methods.add(meta);
    return this;
  }

  public EnumMeta withField(FieldMeta meta) {
    fields.add(meta);
    return this;
  }

  public EnumMeta withPackage(String packageName) {
    this.packageName = packageName;
    return this;
  }

  public EnumMeta withImport(String importSpec) {
    this.imports.add(importSpec);
    return this;
  }

  public EnumMeta withName(String name) {
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
        + "public enum "
        + this.name
        + " {"
        + "\n"
        + Helper.indent(
            1,
            this.options.stream()
                    .sorted(Comparator.comparing(OptionMeta::getName))
                    .map(OptionMeta::emit)
                    .collect(Collectors.joining(",\n"))
                + ";")
        + "\n\n"
        + Helper.indent(
            1,
            this.fields.stream()
                .sorted(Comparator.comparing(FieldMeta::getName))
                .map(FieldMeta::emit)
                .collect(Collectors.joining("\n\n")))
        + "\n"
        + Helper.indent(
            1,
            this.methods.stream()
                .sorted(Comparator.comparing(MethodMeta::getName))
                .map(MethodMeta::emit)
                .collect(Collectors.joining("\n\n")))
        + "\n"
        + "}\n";
  }
}
