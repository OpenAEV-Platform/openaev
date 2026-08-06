package io.openaev.ocsf.parser.generator.emission.render;

public class BooleanRender implements Render<Boolean> {
  @Override
  public String render(Boolean source) {
    return String.valueOf(source);
  }
}
