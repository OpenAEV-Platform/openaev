package io.openaev.ocsf.parser.generator.emission.render;

/** Renders the value literally without escaping or quoting */
public class LiteralRender extends Render<String> {
  protected LiteralRender(String value) {
    super(value);
  }

  @Override
  public String render() {
    return getValue();
  }
}
