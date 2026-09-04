package io.openaev.ocsf.parser.generator.emission.render;

public class DoubleRender extends Render<Double> {
  public DoubleRender(Double obj) {
    super(obj);
  }

  @Override
  public String render() {
    return String.valueOf(getValue());
  }
}
