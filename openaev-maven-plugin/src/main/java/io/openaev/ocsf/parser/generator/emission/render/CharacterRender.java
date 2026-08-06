package io.openaev.ocsf.parser.generator.emission.render;

public class CharacterRender implements Render<Character> {
  @Override
  public String render(Character source) {
    return "'" + source + "'";
  }
}
