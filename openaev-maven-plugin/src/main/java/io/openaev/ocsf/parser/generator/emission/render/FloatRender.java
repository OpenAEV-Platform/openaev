package io.openaev.ocsf.parser.generator.emission.render;

public class FloatRender extends Render<Float> {
  public FloatRender(Float obj) {
    super(obj);
  }

  @Override
  public String render() {
    return String.valueOf(getValue());
  }
}
