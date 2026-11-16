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
 * Reads {@code linked-id-resolution-baseline.txt}, the reasoned list of the call sites that resolve
 * ids on the repository of an active tenant table without a tenant. One site per line, {@code
 * fully.qualified.Class#method -> Repository | <kind> | <calls> | <why>}. The kind is one of:
 *
 * <ul>
 *   <li>{@code link}: the site resolves ids named in a request body and links the rows it finds to
 *       the entity written. It is debt: the ids resolve with {@code findAllByIdInTenant(ids,
 *       tenantId)}, and the line leaves the file when the site is converted. When a site cannot be
 *       classified by reading, it is a {@code link}, so that doubt is converted rather than kept;
 *   <li>{@code other}: the site does something else, and the reason says what: the ids come from a
 *       path, a stored row, an export, a platform read.
 * </ul>
 *
 * <p>{@code calls} is the number of calls and method references the method holds on that
 * repository, at least 1: a call added to a listed method, or removed from it, changes the number
 * and fails until the line says so. The reason is at least {@value #MIN_WORDS} words and describes
 * what the site does.
 */
final class LinkedIdResolutionBaseline {

  static final String RESOURCE = "linked-id-resolution-baseline.txt";
  static final String LINK = "link";
  static final String OTHER = "other";
  static final Set<String> KINDS = Set.of(LINK, OTHER);
  static final int MIN_WORDS = 6;

  private static final Pattern SITE =
      Pattern.compile("[a-zA-Z_][\\w.$]*\\.[A-Z][\\w$]*#[\\w$<>]+ -> [A-Z][\\w$]*");

  /**
   * One line: its number in the file, the site, its kind, its call count as written, its reason.
   */
  record Entry(int line, String site, String kind, String calls, String why) {

    /** The call count, or -1 when the line does not hold an integer of at least 1. */
    int callCount() {
      try {
        int count = Integer.parseInt(calls);
        return count >= 1 ? count : -1;
      } catch (NumberFormatException e) {
        return -1;
      }
    }
  }

  private LinkedIdResolutionBaseline() {}

  static List<Entry> load() {
    try (InputStream in =
        LinkedIdResolutionBaseline.class.getClassLoader().getResourceAsStream(RESOURCE)) {
      if (in == null) {
        throw new IllegalStateException(RESOURCE + " is missing from the classpath");
      }
      try (BufferedReader reader =
          new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
        return parse(reader.lines().toList());
      }
    } catch (IOException e) {
      throw new UncheckedIOException("cannot read " + RESOURCE, e);
    }
  }

  static List<Entry> parse(List<String> lines) {
    List<Entry> entries = new ArrayList<>();
    for (int i = 0; i < lines.size(); i++) {
      String line = lines.get(i).strip();
      if (line.isEmpty() || line.startsWith("#")) {
        continue;
      }
      String[] parts = line.split("\\|", 4);
      entries.add(
          new Entry(
              i + 1,
              parts[0].strip(),
              parts.length > 1 ? parts[1].strip() : "",
              parts.length > 2 ? parts[2].strip() : "",
              parts.length > 3 ? parts[3].strip() : ""));
    }
    return entries;
  }

  /** The entries a reader cannot trust, each with what is wrong with it. */
  static List<String> malformed(List<Entry> entries) {
    List<String> problems = new ArrayList<>();
    Map<String, Integer> firstSeen = new HashMap<>();
    for (Entry entry : entries) {
      String where = "line " + entry.line() + " '" + entry.site() + "': ";
      if (!SITE.matcher(entry.site()).matches()) {
        problems.add(
            where + "the first column must read 'fully.qualified.Class#method -> Repository'");
        continue;
      }
      Integer first = firstSeen.putIfAbsent(entry.site(), entry.line());
      if (first != null) {
        problems.add(where + "duplicates line " + first);
      } else if (!KINDS.contains(entry.kind())) {
        problems.add(
            where + "the second column must be one of " + KINDS.stream().sorted().toList());
      } else if (entry.callCount() < 1) {
        problems.add(
            where + "the third column must be the number of calls, an integer of at least 1");
      } else if (entry.why().isBlank() || entry.why().split("\\s+").length < MIN_WORDS) {
        problems.add(
            where + "'" + entry.kind() + "' needs a reason of at least " + MIN_WORDS + " words");
      }
    }
    return problems;
  }

  static long count(List<Entry> entries, String kind) {
    return entries.stream().filter(entry -> kind.equals(entry.kind())).count();
  }
}
