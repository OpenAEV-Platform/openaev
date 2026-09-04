package io.openaev.ocsf.parser.generator.emission.render;

public class StringRender extends Render<String> {
  public StringRender(String obj) {
    super(obj);
  }

  @Override
  public String render() {
    return "\"" + getValue() + "\"";
  }
}
