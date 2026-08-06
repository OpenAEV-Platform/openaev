package io.openaev.ocsf.parser.generator.emission.render;

public class IntegerRender implements Render<Integer> {
  @Override
  public String render(Integer source) {
    return String.valueOf(source);
  }
}
