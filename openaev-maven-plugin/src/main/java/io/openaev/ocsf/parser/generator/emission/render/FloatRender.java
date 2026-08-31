package io.openaev.ocsf.parser.generator.emission.render;

public class FloatRender extends Render<Double> {
  public FloatRender(Double obj) {
    super(obj);
  }

  @Override
  public String render() {
    return String.valueOf(getValue());
  }
}
