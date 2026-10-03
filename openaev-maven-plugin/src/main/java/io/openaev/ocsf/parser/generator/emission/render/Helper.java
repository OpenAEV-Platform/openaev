package io.openaev.ocsf.parser.generator.emission.render;

public class Helper {
  private static final int INDENT_SPACES = 2;
  private static final char INDENT_CHAR = ' ';

  public static String indent(int level, String orig) {
    return getIndent().repeat(level) + orig.replaceAll("\n", "\n" + getIndent().repeat(level));
  }

  private static String getIndent() {
    return String.valueOf(INDENT_CHAR).repeat(INDENT_SPACES);
  }
}
