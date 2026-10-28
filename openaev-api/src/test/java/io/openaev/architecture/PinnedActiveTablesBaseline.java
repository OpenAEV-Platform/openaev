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
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads {@code pinned-active-tables-baseline.txt}, the reasoned list of the test classes that set
 * the activation list themselves. One class per line, {@code fully.qualified.Class | <tables> |
 * <kind>[ until-active:<table>,...]: <why>}. The tables are the list the class runs with: the
 * annotation's value, or the value its dynamic registration produced when evaluated, {@code none}
 * for an empty list, or {@code unevaluated} for a registration that could not be evaluated. The
 * kind is one of:
 *
 * <ul>
 *   <li>{@code probe}: arms a chosen set on purpose, every assertion is about rows of that set or a
 *       mechanism measured on it, and no assertion is that a flow works; a table production
 *       activates beside it changes nothing it proves;
 *   <li>{@code control}: leaves a table off on purpose, to show that a mechanism other than the
 *       inspector holds on its own. It names, in {@code until-active:}, the activation that retires
 *       that mechanism, and the line fails once production arms one of those tables;
 *   <li>{@code drifts}: an assertion depends on how a production-active table outside its list is
 *       scoped, or it tests that a flow works without arming every production-active table the flow
 *       reads, so it tests less than production does and must follow the production list.
 * </ul>
 *
 * <p>The kind is followed by a written explanation of at least {@value #MIN_WORDS} words.
 */
final class PinnedActiveTablesBaseline {

  static final String RESOURCE = "pinned-active-tables-baseline.txt";
  static final Set<String> DELIBERATE_KINDS = Set.of("probe", "control");
  static final Set<String> DEBT_KINDS = Set.of("drifts");
  static final String NO_TABLES = "none";
  static final String UNEVALUATED = "unevaluated";
  static final int MIN_WORDS = 6;

  private static final String EXPIRING_KIND = "control";
  private static final Pattern CLASS_NAME = Pattern.compile("[a-zA-Z_][\\w.$]*\\.[A-Z][\\w$]*");
  private static final Pattern REASON =
      Pattern.compile(
          "([a-z-]+)(?:\\s+until-active:([a-z_]+(?:,[a-z_]+)*))?\\s*:\\s*(.*)", Pattern.DOTALL);

  /**
   * One line. {@code tables} is null for a registration that could not be evaluated. {@code
   * untilActive} names the tables whose activation retires the line, empty when it carries none.
   */
  record Entry(
      int line,
      String testClass,
      Set<String> tables,
      String kind,
      Set<String> untilActive,
      String why) {}

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
          UNEVALUATED.equals(tablesColumn)
              ? null
              : NO_TABLES.equals(tablesColumn)
                  ? Set.of()
                  : PinnedActiveTablesCatalog.tables(tablesColumn);
      Matcher matcher = REASON.matcher(reason);
      if (matcher.matches()) {
        Set<String> untilActive =
            matcher.group(2) == null
                ? Set.of()
                : PinnedActiveTablesCatalog.tables(matcher.group(2));
        entries.add(
            new Entry(i + 1, testClass, tables, matcher.group(1), untilActive, matcher.group(3)));
      } else {
        entries.add(new Entry(i + 1, testClass, tables, reason, Set.of(), ""));
      }
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
      } else if (!DELIBERATE_KINDS.contains(entry.kind()) && !DEBT_KINDS.contains(entry.kind())) {
        problems.add(
            where
                + "the third column must begin with one of "
                + new TreeSet<>(DELIBERATE_KINDS)
                + " or "
                + DEBT_KINDS
                + ", optionally followed by until-active:<table>, then ':'");
      } else if (EXPIRING_KIND.equals(entry.kind()) && entry.untilActive().isEmpty()) {
        problems.add(
            where
                + "a control must name, in until-active:<table>, the activation that ends what"
                + " it shows");
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

  /**
   * The entries whose {@code until-active} names a table no activation can ever arm, a typo
   * included: such a line would never expire.
   */
  static List<String> unknownUntilActive(List<Entry> entries, Set<String> activatable) {
    List<String> unknown = new ArrayList<>();
    for (Entry entry : entries) {
      Set<String> names = new TreeSet<>(entry.untilActive());
      names.removeAll(activatable);
      if (!names.isEmpty()) {
        unknown.add(
            "line "
                + entry.line()
                + " '"
                + entry.testClass()
                + "': no activation arms "
                + String.join(",", names));
      }
    }
    return unknown;
  }

  /** The entries whose {@code until-active} names a table production now arms. */
  static List<String> expired(List<Entry> entries, Set<String> production) {
    List<String> expired = new ArrayList<>();
    for (Entry entry : entries) {
      Set<String> active = new TreeSet<>(entry.untilActive());
      active.retainAll(production);
      if (!active.isEmpty()) {
        expired.add(
            "line "
                + entry.line()
                + " '"
                + entry.testClass()
                + "': production now arms "
                + String.join(",", active));
      }
    }
    return expired;
  }
}
