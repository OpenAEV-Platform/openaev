package io.openaev.ocsf.parser.generator.emission.render;

public class ArrayRender<T> extends Render<T[]> {
  public ArrayRender(T[] value) {
    super(value);
  }

  @Override
  public String render() {
    return "";
  }
}
