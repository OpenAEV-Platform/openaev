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
import java.util.regex.Pattern;

/**
 * Reads {@code tenant-isolation-coverage-baseline.txt}, the reasoned list of the active tables that
 * no test class covers yet. One table per line, {@code <table> | <missing> | <why>}: what the
 * existing tests lack, then why the table is on the list. The reason has at least {@value
 * PinnedActiveTablesBaseline#MIN_WORDS} words, the bar {@link PinnedActiveTablesBaseline} sets.
 *
 * <p>The list only shrinks. A line whose table becomes covered or inactive fails the gate, and the
 * number of lines is frozen, so adding a line is a review call.
 */
final class TenantIsolationCoverageBaseline {

  static final String RESOURCE = "tenant-isolation-coverage-baseline.txt";

  private static final Pattern TABLE = Pattern.compile("[a-z][a-z0-9_]*");

  /** One line: the table, what is missing, and why it is allowed to stay missing. */
  record Entry(int line, String table, String missing, String why) {}

  private TenantIsolationCoverageBaseline() {}

  static List<Entry> load() {
    try (InputStream in =
        TenantIsolationCoverageBaseline.class.getClassLoader().getResourceAsStream(RESOURCE)) {
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
      entries.add(
          new Entry(
              i + 1,
              parts[0].strip(),
              parts.length > 1 ? parts[1].strip() : "",
              parts.length > 2 ? parts[2].strip() : ""));
    }
    return entries;
  }

  /** The entries a reader cannot trust, each with what is wrong with it. */
  static List<String> malformed(List<Entry> entries) {
    List<String> problems = new ArrayList<>();
    Map<String, Integer> firstSeen = new HashMap<>();
    for (Entry entry : entries) {
      String where = "line " + entry.line() + " '" + entry.table() + "': ";
      if (!TABLE.matcher(entry.table()).matches()) {
        problems.add(where + "the first column must be a table name");
        continue;
      }
      Integer previous = firstSeen.putIfAbsent(entry.table(), entry.line());
      if (previous != null) {
        problems.add(where + "duplicates line " + previous);
      } else if (entry.missing().isEmpty()) {
        problems.add(where + "the second column must say what the existing tests lack");
      } else if (entry.why().isEmpty()
          || entry.why().split("\\s+").length < PinnedActiveTablesBaseline.MIN_WORDS) {
        problems.add(
            where
                + "the third column must say why, in at least "
                + PinnedActiveTablesBaseline.MIN_WORDS
                + " words");
      }
    }
    return problems;
  }
}
