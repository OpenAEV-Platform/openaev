package io.openaev.ocsf.parser.generator.emission.render;

public class FloatRender implements Render<Float> {
  @Override
  public String render(Float source) {
    return String.valueOf(source);
  }
}
