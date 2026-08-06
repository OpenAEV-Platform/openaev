package io.openaev.ocsf.parser.generator.emission.meta.annotation;

import io.openaev.ocsf.parser.generator.emission.Emitter;
import io.openaev.ocsf.parser.generator.emission.render.Render;
import io.openaev.ocsf.parser.generator.emission.render.RenderFactory;

public class AttributeMeta<T> implements Emitter {
  private final String key;
  private final T value;
  private final Render<T> render;

  public AttributeMeta(String key, T value) {
    this.key = key;
    this.value = value;
    this.render = new RenderFactory().getRender(value);
  }

  @Override
  public String emit() {
    return key + " = " + render.render(value);
  }
}
