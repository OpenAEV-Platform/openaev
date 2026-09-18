package io.openaev.ocsf.parser.generator.emission.render;

public class BooleanRender extends Render<Boolean> {
  public BooleanRender(Boolean value) {
    super(value);
  }

  @Override
  public String render() {
    return String.valueOf(getValue());
  }
}
