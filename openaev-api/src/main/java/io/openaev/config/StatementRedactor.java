package io.openaev.config;

/**
 * Removes literal values and comments from SQL text before it is logged, following the PostgreSQL
 * lexical rules: a statement written by hand can carry a value in any of them, and a pattern that
 * pairs apostrophes misreads dollar quotes, backslash escapes, comments and quoted identifiers.
 *
 * <p>One pass, left to right, with no regular expression and no recursion, so the time is linear
 * and a literal of any length is safe. A literal, a dollar-quoted body or a comment left open cuts
 * the text from its opening token to the end, followed by {@value #UNTERMINATED}.
 */
final class StatementRedactor {

  static final String UNTERMINATED = "[unterminated literal]";

  private static final String VALUE = "?";

  private StatementRedactor() {}

  /**
   * Redacts SQL: a string literal ({@code '...'}, {@code E'...'}) or a dollar-quoted body ({@code
   * $$...$$}, {@code $tag$...$tag$}) becomes {@code ?}, a comment becomes one space, a quoted
   * identifier and a {@code $1} parameter are copied as they are.
   */
  static String redactStatement(String sql) {
    return redact(sql, false);
  }

  /**
   * Redacts an exception message that quotes SQL. Besides the literals and comments of {@link
   * #redactStatement}, every double-quoted part becomes {@code "?"}: the parser quotes the token it
   * stopped at that way, with its apostrophes escaped by a backslash, and that token can be literal
   * text.
   */
  static String redactMessage(String message) {
    return redact(message, true);
  }

  private static String redact(String text, boolean message) {
    StringBuilder out = new StringBuilder(Math.min(text.length(), 4096));
    int i = 0;
    while (i < text.length()) {
      char c = text.charAt(i);
      char next = i + 1 < text.length() ? text.charAt(i + 1) : 0;
      int end;
      String replacement;
      if (c == '"' && message) {
        end = endOfMessageQuote(text, i + 1);
        replacement = "\"" + VALUE + "\"";
      } else if (c == '"') {
        end = endOfQuotedIdentifier(text, i + 1);
        replacement = null; // copied as it is
      } else if (c == '\'') {
        end = endOfString(text, i + 1, false);
        replacement = VALUE;
      } else if ((c == 'E' || c == 'e') && next == '\'' && !continuesIdentifier(text, i)) {
        end = endOfString(text, i + 2, true);
        replacement = VALUE;
      } else if (c == '$' && !continuesIdentifier(text, i) && endOfDollarTag(text, i) > 0) {
        end = endOfDollarBody(text, i);
        replacement = VALUE;
      } else if (c == '-' && next == '-') {
        end = endOfLine(text, i + 2);
        replacement = " ";
      } else if (c == '/' && next == '*') {
        end = endOfBlockComment(text, i + 2);
        replacement = " ";
      } else {
        out.append(c);
        i++;
        continue;
      }
      if (end < 0) {
        return out.append(UNTERMINATED).toString();
      }
      if (replacement == null) {
        out.append(text, i, end);
      } else {
        out.append(replacement);
      }
      i = end;
    }
    return out.toString();
  }

  /**
   * True when the character before {@code index} belongs to an identifier, which then absorbs a
   * following {@code E} or {@code $}: {@code typE'x'} is an identifier and a literal, {@code a$b$}
   * is one identifier.
   */
  private static boolean continuesIdentifier(String text, int index) {
    if (index == 0) {
      return false;
    }
    char before = text.charAt(index - 1);
    return Character.isLetterOrDigit(before) || before == '_' || before == '$';
  }

  /** Index after the closing quote of a string body starting at {@code from}, or -1. */
  private static int endOfString(String text, int from, boolean backslashEscapes) {
    int i = from;
    while (i < text.length()) {
      char c = text.charAt(i);
      if (backslashEscapes && c == '\\') {
        i += 2;
      } else if (c != '\'') {
        i++;
      } else if (i + 1 < text.length() && text.charAt(i + 1) == '\'') {
        i += 2;
      } else {
        return i + 1;
      }
    }
    return -1;
  }

  /** Index after the closing double quote of an identifier ({@code ""} escapes one), or -1. */
  private static int endOfQuotedIdentifier(String text, int from) {
    int i = from;
    while (i < text.length()) {
      if (text.charAt(i) != '"') {
        i++;
      } else if (i + 1 < text.length() && text.charAt(i + 1) == '"') {
        i += 2;
      } else {
        return i + 1;
      }
    }
    return -1;
  }

  /** Index after the closing double quote of a message part (a backslash escapes one), or -1. */
  private static int endOfMessageQuote(String text, int from) {
    int i = from;
    while (i < text.length()) {
      char c = text.charAt(i);
      if (c == '\\') {
        i += 2;
      } else if (c == '"') {
        return i + 1;
      } else {
        i++;
      }
    }
    return -1;
  }

  /**
   * Index after the second {@code $} of a dollar-quote delimiter starting at {@code from}, or -1
   * when the text there is not one ({@code $1} is a parameter). A tag starts with a letter or an
   * underscore and goes on with letters, digits and underscores.
   */
  private static int endOfDollarTag(String text, int from) {
    int i = from + 1;
    if (i < text.length() && (Character.isLetter(text.charAt(i)) || text.charAt(i) == '_')) {
      i++;
      while (i < text.length()
          && (Character.isLetterOrDigit(text.charAt(i)) || text.charAt(i) == '_')) {
        i++;
      }
    }
    return i < text.length() && text.charAt(i) == '$' ? i + 1 : -1;
  }

  /** Index after the delimiter that closes the dollar-quoted body opened at {@code from}, or -1. */
  private static int endOfDollarBody(String text, int from) {
    int bodyStart = endOfDollarTag(text, from);
    String delimiter = text.substring(from, bodyStart);
    int close = text.indexOf(delimiter, bodyStart);
    return close < 0 ? -1 : close + delimiter.length();
  }

  /** Index of the line break ending a line comment, or the text length. */
  private static int endOfLine(String text, int from) {
    int i = from;
    while (i < text.length() && text.charAt(i) != '\n' && text.charAt(i) != '\r') {
      i++;
    }
    return i;
  }

  /** Index after the {@code *}{@code /} closing a block comment, nested ones included, or -1. */
  private static int endOfBlockComment(String text, int from) {
    int depth = 1;
    int i = from;
    while (i < text.length()) {
      if (text.startsWith("/*", i)) {
        depth++;
        i += 2;
      } else if (text.startsWith("*/", i)) {
        depth--;
        i += 2;
        if (depth == 0) {
          return i;
        }
      } else {
        i++;
      }
    }
    return -1;
  }
}
