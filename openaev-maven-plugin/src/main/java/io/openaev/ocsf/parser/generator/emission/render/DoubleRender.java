package io.openaev.ocsf.parser.generator.emission.render;

public class DoubleRender implements Render<Double> {
  @Override
  public String render(Double source) {
    return String.valueOf(source);
  }
}
