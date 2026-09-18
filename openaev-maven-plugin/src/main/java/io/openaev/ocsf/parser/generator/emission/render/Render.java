package io.openaev.ocsf.parser.generator.emission.render;

import lombok.Getter;

public abstract class Render<T> {
  @Getter private final T value;

  protected Render(T value) {
    this.value = value;
  }

  public abstract String render();
}
