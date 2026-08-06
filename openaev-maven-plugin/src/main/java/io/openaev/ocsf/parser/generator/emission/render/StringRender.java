package io.openaev.ocsf.parser.generator.emission.render;

public class StringRender implements Render<String> {
  @Override
  public String render(String source) {
    return "\"" + source + "\"";
  }
}
