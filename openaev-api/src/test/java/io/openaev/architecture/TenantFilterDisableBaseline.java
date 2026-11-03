package io.openaev.architecture;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Reads {@code tenant-filter-disable-baseline.txt}, the reasoned list of the production methods
 * still allowed to call {@code disableFilter("tenantFilter")}. One site per line, {@code
 * fully.qualified.Class#method # <classification>: <why>}, where the classification is one of:
 *
 * <ul>
 *   <li>{@code restores-captured-state}: the call puts the filter back to the state captured on
 *       entry to the method, so the session leaves the call as it came in;
 *   <li>{@code defensive}: the session can arrive with the filter armed by earlier work (say how)
 *       and the call is what keeps the read below from being narrowed to one tenant.
 * </ul>
 *
 * <p>The classification is followed by a written explanation of at least {@value #MIN_WORDS} words.
 * A bare table name, a bare classification or a missing reason is rejected: the reason is what
 * tells a legitimate keep from a call nobody has looked at.
 */
final class TenantFilterDisableBaseline {

  static final String RESOURCE = "tenant-filter-disable-baseline.txt";
  static final Set<String> CLASSIFICATIONS = Set.of("restores-captured-state", "defensive");
  static final int MIN_WORDS = 6;

  private static final Pattern SITE = Pattern.compile("[a-zA-Z_][\\w.$]*#[a-zA-Z_$][\\w$]*");

  record Entry(int line, String site, String reason) {}

  private TenantFilterDisableBaseline() {}

  static List<Entry> load() {
    try (InputStream in =
        TenantFilterDisableBaseline.class.getClassLoader().getResourceAsStream(RESOURCE)) {
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

  /**
   * Every non-comment line, split into its site and its reason; a line without {@code #} has none.
   */
  static List<Entry> parse(List<String> lines) {
    List<Entry> entries = new ArrayList<>();
    for (int i = 0; i < lines.size(); i++) {
      String line = lines.get(i).strip();
      if (line.isEmpty() || line.startsWith("#")) {
        continue;
      }
      int marker = line.indexOf(" #");
      String site = (marker < 0 ? line : line.substring(0, marker)).strip();
      String reason = marker < 0 ? "" : line.substring(marker + 2).strip();
      entries.add(new Entry(i + 1, site, reason));
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
        problems.add(where + "the site must be fully.qualified.Class#method");
      }
      Integer previous = firstSeen.putIfAbsent(entry.site(), entry.line());
      if (previous != null) {
        problems.add(where + "duplicates line " + previous);
      }
      String problem = reasonProblem(entry.reason());
      if (problem != null) {
        problems.add(where + problem);
      }
    }
    return problems;
  }

  static Set<String> sites(List<Entry> entries) {
    Set<String> sites = new LinkedHashSet<>();
    entries.forEach(entry -> sites.add(entry.site()));
    return sites;
  }

  private static String reasonProblem(String reason) {
    if (reason.isBlank()) {
      return "no reason after '#'";
    }
    int colon = reason.indexOf(':');
    String classification = colon < 0 ? reason : reason.substring(0, colon).strip();
    if (!CLASSIFICATIONS.contains(classification)) {
      return "the reason must begin with one of " + CLASSIFICATIONS + " followed by ':'";
    }
    String why = colon < 0 ? "" : reason.substring(colon + 1).strip();
    if (why.isEmpty() || why.split("\\s+").length < MIN_WORDS) {
      return "'"
          + classification
          + "' must be followed by why, in at least "
          + MIN_WORDS
          + " words";
    }
    return null;
  }
}
