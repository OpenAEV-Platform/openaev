package io.openaev.ocsf.parser.generator.emission.render;

public class IntegerRender extends Render<Integer> {
  protected IntegerRender(Integer value) {
    super(value);
  }

  @Override
  public String render() {
    return String.valueOf(getValue());
  }
}
