package io.openaev.utils;

import java.util.Set;

/**
 * Protects a CSV opened in a spreadsheet: a cell starting with one of {@link #FORMULA_STARTS} is
 * run as a formula. An export puts a {@code '} in front of such a value, an import takes it back
 * off, so the round trip is lossless.
 *
 * <p>A value that already starts with quotes in front of a formula start ({@code '=X}) gets one
 * more, so that taking one off gives the stored value back. The line feed is a formula start too:
 * the CSV reader gives back a carriage return inside a quoted cell as one.
 */
public final class CsvInjectionUtils {

  private static final Set<Character> FORMULA_STARTS = Set.of('=', '+', '-', '@', '\t', '\r', '\n');

  private CsvInjectionUtils() {}

  public static String neutralise(String value) {
    return startsFormulaAfterQuotes(value) ? "'" + value : value;
  }

  public static String undoNeutralisation(String value) {
    return value != null && value.startsWith("'") && startsFormulaAfterQuotes(value)
        ? value.substring(1)
        : value;
  }

  private static boolean startsFormulaAfterQuotes(String value) {
    if (value == null) {
      return false;
    }
    int index = 0;
    while (index < value.length() && value.charAt(index) == '\'') {
      index++;
    }
    return index < value.length() && FORMULA_STARTS.contains(value.charAt(index));
  }
}
