package io.openaev.architecture;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Reads {@code pinned-active-tables-baseline.txt}, the reasoned list of the test classes that set
 * the activation list themselves. One class per line, {@code fully.qualified.Class | <tables> |
 * <kind>: <why>}. The tables are the class's pinned list as written, {@code none} for an empty
 * list, or {@code dynamic} for a {@code @DynamicPropertySource} registration. The kind is one of:
 *
 * <ul>
 *   <li>{@code probe}: arms a chosen set on purpose, and every isolation it asserts is about a
 *       table in that set, so a table production activates beside it changes nothing it proves;
 *   <li>{@code control}: leaves a table off on purpose, to show that a mechanism other than the
 *       inspector holds on its own;
 *   <li>{@code drifts}: asserts an isolation that depends on a table outside its list, so it tests
 *       less than production does and must follow the production list instead;
 *   <li>{@code own-reader}: follows production through its own parser of the properties file rather
 *       than the shared reader, and must move to it.
 * </ul>
 *
 * <p>The kind is followed by a written explanation of at least {@value #MIN_WORDS} words.
 */
final class PinnedActiveTablesBaseline {

  static final String RESOURCE = "pinned-active-tables-baseline.txt";
  static final Set<String> LEGITIMATE_KINDS = Set.of("probe", "control");
  static final Set<String> DEBT_KINDS = Set.of("drifts", "own-reader");
  static final String NO_TABLES = "none";
  static final String DYNAMIC = "dynamic";
  static final int MIN_WORDS = 6;

  private static final Pattern CLASS_NAME = Pattern.compile("[a-zA-Z_][\\w.$]*\\.[A-Z][\\w$]*");

  /**
   * One line. {@code tables} is null for a dynamic registration, whose value is not known before
   * the context starts.
   */
  record Entry(int line, String testClass, Set<String> tables, String kind, String why) {}

  private PinnedActiveTablesBaseline() {}

  static List<Entry> load() {
    try (InputStream in =
        PinnedActiveTablesBaseline.class.getClassLoader().getResourceAsStream(RESOURCE)) {
      if (in == null) {
        throw new IllegalStateException(RESOURCE + " is missing from the classpath");
      }
      try (BufferedReader reader =
          new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
        return parse(reader.lines().toList());
      }
    } catch (IOException e) {
      throw new UncheckedIOException("could not read " + RESOURCE, e);
    }
  }

  static List<Entry> parse(List<String> lines) {
    List<Entry> entries = new ArrayList<>();
    for (int i = 0; i < lines.size(); i++) {
      String line = lines.get(i).strip();
      if (line.isEmpty() || line.startsWith("#")) {
        continue;
      }
      String[] parts = line.split("\\|", 3);
      String testClass = parts[0].strip();
      String tablesColumn = parts.length > 1 ? parts[1].strip() : "";
      String reason = parts.length > 2 ? parts[2].strip() : "";
      Set<String> tables =
          DYNAMIC.equals(tablesColumn)
              ? null
              : NO_TABLES.equals(tablesColumn)
                  ? Set.of()
                  : PinnedActiveTablesCatalog.tables(tablesColumn);
      int colon = reason.indexOf(':');
      String kind = colon < 0 ? reason : reason.substring(0, colon).strip();
      String why = colon < 0 ? "" : reason.substring(colon + 1).strip();
      entries.add(new Entry(i + 1, testClass, tables, kind, why));
    }
    return entries;
  }

  /** The entries a reader cannot trust, each with what is wrong with it. */
  static List<String> malformed(List<Entry> entries) {
    List<String> problems = new ArrayList<>();
    Map<String, Integer> firstSeen = new HashMap<>();
    for (Entry entry : entries) {
      String where = "line " + entry.line() + " '" + entry.testClass() + "': ";
      if (!CLASS_NAME.matcher(entry.testClass()).matches()) {
        problems.add(where + "the first column must be a fully qualified class name");
        continue;
      }
      Integer previous = firstSeen.putIfAbsent(entry.testClass(), entry.line());
      if (previous != null) {
        problems.add(where + "duplicates line " + previous);
      } else if (!LEGITIMATE_KINDS.contains(entry.kind()) && !DEBT_KINDS.contains(entry.kind())) {
        problems.add(
            where
                + "the third column must begin with one of "
                + LEGITIMATE_KINDS
                + " or "
                + DEBT_KINDS
                + " followed by ':'");
      } else if (entry.why().split("\\s+").length < MIN_WORDS) {
        problems.add(
            where
                + "'"
                + entry.kind()
                + "' must be followed by why, in at least "
                + MIN_WORDS
                + " words");
      }
    }
    return problems;
  }
}
