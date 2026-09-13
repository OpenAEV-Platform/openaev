package io.openaev.architecture;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The build-time guard over the five background entry-point families (T1.6, #6398).
 *
 * <p>Background work is where the v2 migration goes wrong: an entry point with no tenant scope does
 * not read zero rows, it falls back to the default tenant, which is a silent wrong-tenant write no
 * detector catches. An activation only inventories the paths touching its own table, so a path no
 * active table touches yet is never looked at. This test looks at all of them, always.
 *
 * <p>It enumerates every production class in the five families and requires each to be either
 *
 * <ul>
 *   <li>on the primitive: it depends on {@link io.openaev.context.TenantScopedTransaction}, which
 *       carries the scope; or
 *   <li>listed in {@code background-guard-baseline.txt} with a one-line written reason.
 * </ul>
 *
 * A new entry point in any family then fails the build until someone classifies it. The baseline is
 * the same hand-authored, reviewed, shrink-only shape as {@code failclosed-baseline.txt}: a run
 * cannot rewrite it, and three checks keep it honest, every entry carries a reason, no entry is
 * stale (still a background entry point and still off the primitive).
 *
 * <p>The five families, and how each is recognised structurally (matching {@code tools/mt-jobs.sh},
 * which re-counts the same families for {@code evidence/background-jobs.md}):
 *
 * <ol>
 *   <li>Quartz jobs, classes assignable to {@code org.quartz.Job};
 *   <li>{@code @Async} methods, a class or method annotated {@code @Async};
 *   <li>event listeners, {@code ApplicationListener} or a method annotated {@code @EventListener} /
 *       {@code @TransactionalEventListener};
 *   <li>seeding and startup, {@code CommandLineRunner} / {@code ApplicationRunner} or a method
 *       annotated {@code @PostConstruct};
 *   <li>detached thread hand-offs, a field of an executor type ({@code Executor} family or {@code
 *       TaskScheduler}).
 * </ol>
 */
class BackgroundEntrypointTenantScopeArchTest {

  private static final String PRIMITIVE = "io.openaev.context.TenantScopedTransaction";
  private static final String BASELINE_RESOURCE = "/background-guard-baseline.txt";

  private static final JavaClasses PRODUCTION_CLASSES =
      new ClassFileImporter()
          .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
          .importPackages("io.openaev");

  @Test
  @DisplayName("every background entry point is on the primitive or classified in the baseline")
  void every_background_entrypoint_is_scoped_or_classified() {
    Map<String, String> baseline = loadBaseline();

    Map<String, List<String>> unclassified = new TreeMap<>();
    for (JavaClass clazz : PRODUCTION_CLASSES) {
      if (!isConcreteBean(clazz)) {
        continue;
      }
      List<String> families = familiesOf(clazz);
      if (families.isEmpty()) {
        continue;
      }
      if (isOnPrimitive(clazz) || baseline.containsKey(clazz.getFullName())) {
        continue;
      }
      unclassified.put(clazz.getFullName(), families);
    }

    if (!unclassified.isEmpty()) {
      StringBuilder message =
          new StringBuilder(
              unclassified.size()
                  + " background entry point(s) neither reference TenantScopedTransaction nor are"
                  + " listed in background-guard-baseline.txt with a reason.\n"
                  + "Scope the path through the primitive, or classify it in the baseline"
                  + " (touches-no-tenant-table / delegates-to-<Class> / platform-global):\n");
      unclassified.forEach(
          (fqcn, families) ->
              message
                  .append("  ")
                  .append(fqcn)
                  .append("  [")
                  .append(String.join(", ", families))
                  .append("]\n"));
      assertTrue(unclassified.isEmpty(), message.toString());
    }
  }

  @Test
  @DisplayName("every baseline entry carries a one-line reason")
  void every_baseline_entry_carries_a_reason() {
    List<String> malformed = new ArrayList<>();
    for (RawLine line : rawBaselineEntries()) {
      if (line.reason.isBlank()) {
        malformed.add("line " + line.number + ": '" + line.fqcn + "' has no reason after '#'");
      }
    }
    assertTrue(
        malformed.isEmpty(),
        "Every background-guard-baseline.txt entry must carry a reason (\"<fqcn>  # <reason>\"):\n  "
            + String.join("\n  ", malformed));
  }

  @Test
  @DisplayName("no baseline entry is stale (shrink-only)")
  void no_baseline_entry_is_stale() {
    Map<String, String> baseline = loadBaseline();
    Map<String, JavaClass> byName = new LinkedHashMap<>();
    for (JavaClass clazz : PRODUCTION_CLASSES) {
      byName.put(clazz.getFullName(), clazz);
    }

    List<String> stale = new ArrayList<>();
    for (String fqcn : new TreeSet<>(baseline.keySet())) {
      JavaClass clazz = byName.get(fqcn);
      if (clazz == null) {
        stale.add(fqcn + " is not a production class any more, remove it");
      } else if (familiesOf(clazz).isEmpty()) {
        stale.add(fqcn + " is no longer a background entry point, remove it");
      } else if (isOnPrimitive(clazz)) {
        stale.add(fqcn + " now references the primitive, remove its waiver");
      }
    }
    assertTrue(
        stale.isEmpty(),
        "background-guard-baseline.txt may only shrink; these entries are stale and must be"
            + " removed:\n  "
            + String.join("\n  ", stale));
  }

  // --- family recognition ------------------------------------------------------------------------

  private static List<String> familiesOf(JavaClass clazz) {
    List<String> families = new ArrayList<>();
    if (isQuartzJob(clazz)) {
      families.add("quartz");
    }
    if (isAsync(clazz)) {
      families.add("async");
    }
    if (isEventListener(clazz)) {
      families.add("listener");
    }
    if (isSeedingOrStartup(clazz)) {
      families.add("seeding");
    }
    if (isDetachedHandoff(clazz)) {
      families.add("handoff");
    }
    return families;
  }

  private static boolean isQuartzJob(JavaClass clazz) {
    return clazz.isAssignableTo("org.quartz.Job");
  }

  private static boolean isAsync(JavaClass clazz) {
    if (clazz.isAnnotatedWith("org.springframework.scheduling.annotation.Async")) {
      return true;
    }
    return clazz.getMethods().stream()
        .anyMatch(m -> m.isAnnotatedWith("org.springframework.scheduling.annotation.Async"));
  }

  private static boolean isEventListener(JavaClass clazz) {
    if (clazz.isAssignableTo("org.springframework.context.ApplicationListener")) {
      return true;
    }
    return clazz.getMethods().stream()
        .anyMatch(
            m ->
                m.isAnnotatedWith("org.springframework.context.event.EventListener")
                    || m.isAnnotatedWith(
                        "org.springframework.transaction.event.TransactionalEventListener"));
  }

  private static boolean isSeedingOrStartup(JavaClass clazz) {
    if (clazz.isAssignableTo("org.springframework.boot.CommandLineRunner")
        || clazz.isAssignableTo("org.springframework.boot.ApplicationRunner")) {
      return true;
    }
    return clazz.getMethods().stream()
        .anyMatch(m -> m.isAnnotatedWith("jakarta.annotation.PostConstruct"));
  }

  private static boolean isDetachedHandoff(JavaClass clazz) {
    return clazz.getFields().stream()
        .anyMatch(
            f ->
                f.getRawType().isAssignableTo("java.util.concurrent.Executor")
                    || f.getRawType()
                        .isAssignableTo("org.springframework.scheduling.TaskScheduler"));
  }

  private static boolean isOnPrimitive(JavaClass clazz) {
    return clazz.getDirectDependenciesFromSelf().stream()
        .anyMatch(d -> PRIMITIVE.equals(d.getTargetClass().getFullName()));
  }

  private static boolean isConcreteBean(JavaClass clazz) {
    return !clazz.isInterface()
        && !clazz.isEnum()
        && !clazz.isAnnotation()
        && !clazz.isAnonymousClass()
        && !clazz.getModifiers().contains(JavaModifier.ABSTRACT);
  }

  // --- baseline ---------------------------------------------------------------------------------

  private static Map<String, String> loadBaseline() {
    Map<String, String> baseline = new LinkedHashMap<>();
    for (RawLine line : rawBaselineEntries()) {
      baseline.put(line.fqcn, line.reason);
    }
    return baseline;
  }

  private record RawLine(int number, String fqcn, String reason) {}

  private static List<RawLine> rawBaselineEntries() {
    List<RawLine> entries = new ArrayList<>();
    try (InputStream in =
        BackgroundEntrypointTenantScopeArchTest.class.getResourceAsStream(BASELINE_RESOURCE)) {
      if (in == null) {
        throw new IllegalStateException(BASELINE_RESOURCE + " is missing from the classpath");
      }
      try (BufferedReader reader =
          new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
        String raw;
        int number = 0;
        while ((raw = reader.readLine()) != null) {
          number++;
          String line = raw.strip();
          if (line.isEmpty() || line.startsWith("#")) {
            continue;
          }
          int hash = line.indexOf('#');
          String fqcn = (hash < 0 ? line : line.substring(0, hash)).strip();
          String reason = hash < 0 ? "" : line.substring(hash + 1).strip();
          entries.add(new RawLine(number, fqcn, reason));
        }
      }
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    return entries;
  }
}
