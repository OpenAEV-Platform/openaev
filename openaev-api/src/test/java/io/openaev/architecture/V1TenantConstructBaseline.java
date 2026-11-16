package io.openaev.architecture;

import io.openaev.architecture.V1TenantConstructCatalog.Site;
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
 * Reads {@code v1-tenant-construct-baseline.txt}, the reasoned list of the production sites of the
 * multi-tenancy v1 constructs. One site per line, {@code location | construct | count | why}:
 *
 * <ul>
 *   <li>{@code location}: {@code fully.qualified.Class#method}, or the entity class for an
 *       {@code @EntityListeners};
 *   <li>{@code construct}: {@code TenantContext.<method>}, {@code AmbientTenantBridge.<method>},
 *       {@code @EntityListeners(<Listener>)} or {@code @<Annotation>(#tenantContext)};
 *   <li>{@code count}: the number of source lines of the method holding a call or a method
 *       reference to that construct, or of tenant SpEL expressions in its annotations, 1 for a
 *       listener, at least 1, so a call added to a listed method on a new line, or removed from it,
 *       fails until the line says so;
 *   <li>{@code why}: what the site does with the ambient tenant, in at least {@value #MIN_WORDS}
 *       words.
 * </ul>
 */
final class V1TenantConstructBaseline {

  static final String RESOURCE = "v1-tenant-construct-baseline.txt";
  static final int MIN_WORDS = 6;

  private static final Pattern LOCATION =
      Pattern.compile("[a-zA-Z_][\\w.$]*\\.[A-Z][\\w$]*(?:#[\\w$<>]+)?");

  private static final Pattern CONSTRUCT =
      Pattern.compile(
          "(?:TenantContext|AmbientTenantBridge)\\.[\\w$]+"
              + "|@EntityListeners\\([A-Z][\\w$]*\\)"
              + "|@[A-Z][\\w$]*\\(#tenantContext\\)");

  /** One line: its number in the file, its four columns as written. */
  record Entry(int line, String location, String construct, String count, String why) {

    Site site() {
      return new Site(location, construct);
    }

    /** The count, or -1 when the line does not hold an integer of at least 1. */
    int countValue() {
      try {
        int value = Integer.parseInt(count);
        return value >= 1 ? value : -1;
      } catch (NumberFormatException e) {
        return -1;
      }
    }
  }

  private V1TenantConstructBaseline() {}

  static List<Entry> load() {
    try (InputStream in =
        V1TenantConstructBaseline.class.getClassLoader().getResourceAsStream(RESOURCE)) {
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
    Map<Site, Integer> firstSeen = new HashMap<>();
    for (Entry entry : entries) {
      String where = "line " + entry.line() + " '" + entry.site() + "': ";
      if (!LOCATION.matcher(entry.location()).matches()) {
        problems.add(where + "the first column must read 'fully.qualified.Class#method'");
      } else if (!CONSTRUCT.matcher(entry.construct()).matches()) {
        problems.add(where + "the second column must name a v1 construct");
      } else if (firstSeen.putIfAbsent(entry.site(), entry.line()) != null) {
        problems.add(where + "duplicates line " + firstSeen.get(entry.site()));
      } else if (entry.countValue() < 1) {
        problems.add(where + "the third column must be the count, an integer of at least 1");
      } else if (entry.why().isBlank() || entry.why().split("\\s+").length < MIN_WORDS) {
        problems.add(where + "the fourth column must say why, in at least " + MIN_WORDS + " words");
      }
    }
    return problems;
  }
}
