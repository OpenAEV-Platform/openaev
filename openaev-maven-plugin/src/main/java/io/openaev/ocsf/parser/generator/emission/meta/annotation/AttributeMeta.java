package io.openaev.ocsf.parser.generator.emission.meta.annotation;

import io.openaev.ocsf.parser.generator.emission.Emitter;
import io.openaev.ocsf.parser.generator.emission.render.Render;
import io.openaev.ocsf.parser.generator.emission.render.RenderFactory;

public class AttributeMeta<T> implements Emitter {
  private final String key;
  private final Render<T> render;

  public AttributeMeta(String key, T value) {
    this(key, value, false);
  }

  public AttributeMeta(String key, T value, boolean literal) {
    this.key = key;
    this.render =
        literal
            ? (Render<T>) new RenderFactory().getLiteralRender((String) value)
            : new RenderFactory().getRender(value);
  }

  @Override
  public String emit() {
    return key + " = " + render.render();
  }
}
