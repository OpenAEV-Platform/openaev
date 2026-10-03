package io.openaev.ocsf.parser.generator.emission.render;

public class CharacterRender extends Render<Character> {
  public CharacterRender(Character obj) {
    super(obj);
  }

  @Override
  public String render() {
    return "'" + getValue() + "'";
  }
}
