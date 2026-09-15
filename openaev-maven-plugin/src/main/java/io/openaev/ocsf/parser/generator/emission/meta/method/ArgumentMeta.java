package io.openaev.ocsf.parser.generator.emission.meta.method;

import io.openaev.ocsf.parser.generator.emission.Emitter;

public class ArgumentMeta implements Emitter {
  public final Class<?> cls;
  public final String name;

  public ArgumentMeta(Class<?> cls, String name) {
    this.cls = cls;
    this.name = name;
  }

  @Override
  public String emit() {
    return cls.getName() + " " + name;
  }
}
