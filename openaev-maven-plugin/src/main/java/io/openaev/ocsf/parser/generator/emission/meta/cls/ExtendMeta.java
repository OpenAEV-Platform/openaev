package io.openaev.ocsf.parser.generator.emission.meta.cls;

import io.openaev.ocsf.parser.generator.emission.Emitter;

public class ExtendMeta implements Emitter {
  private final String cls;
  private Class<?> genericTypeArgument;

  public ExtendMeta(String cls) {
    this.cls = cls;
  }

  public ExtendMeta withGenericTypeArgument(Class<?> arg) {
    this.genericTypeArgument = arg;
    return this;
  }

  @Override
  public String emit() {
    StringBuilder emitted = new StringBuilder(cls);
    if (genericTypeArgument != null) {
      emitted.append("<").append(genericTypeArgument.getName()).append(">");
    }
    return emitted.toString();
  }
}
