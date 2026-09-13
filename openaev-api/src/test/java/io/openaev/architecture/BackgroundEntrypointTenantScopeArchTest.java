package io.openaev.architecture;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import io.openaev.config.TenantTables;
import io.openaev.database.model.DualScopeBase;
import io.openaev.database.model.TenantBase;
import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The build-time guard over the background entry-point families (T1.6, #6398).
 *
 * <p>Background work is where the v2 migration goes wrong: an entry point with no tenant scope does
 * not read zero rows, it falls back to the default tenant, which is a silent wrong-tenant write no
 * detector catches. An activation only inventories the paths touching its own table, so a path no
 * active table touches yet is never looked at. This test enumerates every production class it can
 * recognise as a background entry point and requires each to be either
 *
 * <ul>
 *   <li>on the primitive: it depends on {@link io.openaev.context.TenantScopedTransaction}, which
 *       carries the scope; or
 *   <li>listed in {@code background-guard-baseline.txt} with a one-line written reason.
 * </ul>
 *
 * A new entry point in a recognised family then fails the build until someone classifies it. The
 * baseline is the same hand-authored, reviewed shape as {@code failclosed-baseline.txt}: a run
 * cannot rewrite it. Additions to the baseline are stopped by review, not by a machine; the checks
 * here enforce that every entry carries a reason ({@code every_baseline_entry_carries_a_reason}),
 * that no entry is stale ({@code no_baseline_entry_is_stale}: still a background entry point and
 * still off the primitive), and that a waiver granted only while a table stays v1 expires the day
 * that table activates ({@code no_baseline_waiver_outlives_its_table} / {@code
 * every_until_active_tag_names_a_real_tenant_table}).
 *
 * <p>The six families, and how each is recognised structurally (matching {@code tools/mt-jobs.sh},
 * which re-counts the same families for {@code evidence/background-jobs.md}):
 *
 * <ol>
 *   <li>Quartz jobs, classes assignable to {@code org.quartz.Job};
 *   <li>{@code @Async} methods, a class or method annotated {@code @Async};
 *   <li>{@code @Scheduled} methods, a method annotated {@code @Scheduled} (a bean whose only marker
 *       is {@code @Scheduled} is a real poller, e.g. ImapService; it would otherwise escape);
 *   <li>event listeners, {@code ApplicationListener} or a method annotated {@code @EventListener} /
 *       {@code @TransactionalEventListener};
 *   <li>seeding and startup, {@code CommandLineRunner} / {@code ApplicationRunner} or a method
 *       annotated {@code @PostConstruct};
 *   <li>detached thread hand-offs: a field of an executor type ({@code Executor} family or {@code
 *       TaskScheduler}), or inline detachment with no such field, i.e. {@code
 *       CompletableFuture.supplyAsync/runAsync}, a raw {@code new Thread(...)}, or an executor
 *       obtained and used inline ({@code Executors.newSingleThreadExecutor().execute(...)}, {@code
 *       ForkJoinPool.commonPool().submit(...)}).
 * </ol>
 *
 * <p>Method-level markers are read with {@code getAllMethods()}, not {@code getMethods()}, so a
 * concrete bean that inherits its only background method from an abstract parent is not missed (the
 * live instance is {@code EngineSyncExecutionJob} extending {@code SelfConfiguredPlatformJob}).
 *
 * <p><b>Known limit, on-primitive is class-level.</b> A class is treated as on the primitive when
 * it has any direct dependency on {@code TenantScopedTransaction}, so a bean with one scoped method
 * and one unscoped background method passes without a baseline entry. Closing this at method level
 * is not tractable here: scope is usually established by delegation (a call to a scoped runner or
 * service, not a direct reference in the entry method), so a method-level check would misclassify
 * every legitimate delegate as unscoped. The mitigation is that the exempted set is small (listed
 * in the T1.6b report) and visible in the diff, so adding a bean to it is a review event; the
 * report records which exempted beans were read in depth. A bean that references the primitive for
 * one method while running another background method unscoped is the residual risk this check does
 * not close.
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

  @Test
  @DisplayName("no waiver outlives the v1 table it was granted against (until-active:<table>)")
  void no_baseline_waiver_outlives_its_table() {
    List<String> expired = expiredWaivers(loadBaseline(), productionActiveTables());
    assertTrue(
        expired.isEmpty(),
        "an until-active waiver was granted only while its table stayed v1, and that table is now"
            + " in openaev.tenant.active-tables. Convert the path to the primitive and remove the"
            + " waiver:\n  "
            + String.join("\n  ", expired));
  }

  @Test
  @DisplayName("every until-active tag names a real tenant table (a typo would never expire)")
  void every_until_active_tag_names_a_real_tenant_table() {
    Set<String> known = knownTenantTables();
    List<String> unknown = new ArrayList<>();
    loadBaseline()
        .forEach(
            (fqcn, reason) -> {
              for (String table : untilActiveTables(reason)) {
                if (!known.contains(table)) {
                  unknown.add(fqcn + "  ->  until-active:" + table);
                }
              }
            });
    assertTrue(
        unknown.isEmpty(),
        "an until-active:<table> tag must name a tenant-aware table, or it can never expire and the"
            + " waiver is permanent by accident:\n  "
            + String.join("\n  ", unknown));
  }

  // --- until-active waivers ----------------------------------------------------------------------

  private static final Pattern UNTIL_ACTIVE =
      Pattern.compile("until-active:([a-z0-9_]+)", Pattern.CASE_INSENSITIVE);

  /** Tables a reason declares the waiver depends on staying v1 ({@code until-active:<table>}). */
  static Set<String> untilActiveTables(String reason) {
    Set<String> tables = new TreeSet<>();
    Matcher matcher = UNTIL_ACTIVE.matcher(reason);
    while (matcher.find()) {
      tables.add(matcher.group(1).toLowerCase(Locale.ROOT));
    }
    return tables;
  }

  /**
   * The waivers whose table is no longer v1. A table counts as active when it is named in the
   * allowlist, or when the allowlist is the wildcard {@link TenantTables#ALL_STRICT}: {@code *} is
   * the rollout's terminal state, where it activates every strict table, so no background path may
   * still be waiting on one to stay v1. (This check reads the production {@code
   * application.properties} file, so it sees {@code *} only once that file carries it; the nightly
   * shadow run arms {@code *} through a JVM property that never reaches this file read.) A table
   * that is permanently outside v2 must not carry an until-active tag in the first place ({@code
   * touches-no-tenant-table} or {@code platform-global} is its reason), so failing such a mis-tag
   * under the wildcard is correct too.
   */
  static List<String> expiredWaivers(Map<String, String> baseline, Set<String> activeTables) {
    boolean wildcard =
        activeTables.stream().anyMatch(t -> TenantTables.ALL_STRICT.equals(t.strip()));
    Set<String> active =
        activeTables.stream()
            .map(t -> t.strip().toLowerCase(Locale.ROOT))
            .collect(Collectors.toSet());
    List<String> expired = new ArrayList<>();
    baseline.forEach(
        (fqcn, reason) -> {
          for (String table : untilActiveTables(reason)) {
            if (wildcard || active.contains(table)) {
              expired.add(
                  fqcn
                      + " is waived until '"
                      + table
                      + "' activates, but that table is now active"
                      + (wildcard ? " ('*' activates every strict table)" : "")
                      + "; scope the path through TenantScopedTransaction and remove the waiver");
            }
          }
        });
    return expired;
  }

  private static Set<String> productionActiveTables() {
    Properties props = new Properties();
    try (InputStream in = new FileInputStream("src/main/resources/application.properties")) {
      props.load(in);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    return Arrays.stream(props.getProperty("openaev.tenant.active-tables", "").split(","))
        .map(String::strip)
        .filter(s -> !s.isEmpty())
        .collect(Collectors.toSet());
  }

  /**
   * Every tenant-aware table name known to the entity model (strict and dual-scope), derived the
   * same way production does ({@link TenantTables#fromEntities}). Used to reject an until-active
   * tag that names no real table: such a waiver could never expire.
   */
  static Set<String> knownTenantTables() {
    List<Class<?>> entities = new ArrayList<>();
    for (JavaClass clazz : PRODUCTION_CLASSES) {
      if (clazz.isInterface() || clazz.getModifiers().contains(JavaModifier.ABSTRACT)) {
        continue;
      }
      if (clazz.isAssignableTo(TenantBase.class) || clazz.isAssignableTo(DualScopeBase.class)) {
        entities.add(clazz.reflect());
      }
    }
    TenantTables tables = TenantTables.fromEntities(entities);
    Set<String> names = new HashSet<>(tables.strict());
    names.addAll(tables.dualScope());
    return names;
  }

  // --- family recognition ------------------------------------------------------------------------

  static List<String> familiesOf(JavaClass clazz) {
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
    if (isScheduled(clazz)) {
      families.add("scheduled");
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
    return clazz.getAllMethods().stream()
        .anyMatch(m -> m.isAnnotatedWith("org.springframework.scheduling.annotation.Async"));
  }

  private static boolean isScheduled(JavaClass clazz) {
    return clazz.getAllMethods().stream()
        .anyMatch(m -> m.isAnnotatedWith("org.springframework.scheduling.annotation.Scheduled"));
  }

  private static boolean isEventListener(JavaClass clazz) {
    if (clazz.isAssignableTo("org.springframework.context.ApplicationListener")) {
      return true;
    }
    return clazz.getAllMethods().stream()
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
    return clazz.getAllMethods().stream()
        .anyMatch(m -> m.isAnnotatedWith("jakarta.annotation.PostConstruct"));
  }

  private static boolean isDetachedHandoff(JavaClass clazz) {
    boolean executorField =
        clazz.getFields().stream()
            .anyMatch(
                f ->
                    f.getRawType().isAssignableTo("java.util.concurrent.Executor")
                        || f.getRawType()
                            .isAssignableTo("org.springframework.scheduling.TaskScheduler"));
    return executorField || detachesInline(clazz);
  }

  /** Submission methods that hand work to an {@link java.util.concurrent.Executor}. */
  private static final Set<String> EXECUTOR_SUBMIT_METHODS =
      Set.of("execute", "submit", "invokeAll", "invokeAny");

  /**
   * Detachment with no executor field. Three shapes, each spawning a thread that inherits neither
   * the caller's transaction nor its tenant scope, so the class is an entry point even though a
   * field-typed executor detector would not see it:
   *
   * <ul>
   *   <li>{@code CompletableFuture.supplyAsync/runAsync} (default {@code ForkJoinPool});
   *   <li>a raw {@code new Thread(...)};
   *   <li>an executor obtained and used inline: a submission call ({@code execute}, {@code submit},
   *       {@code invokeAll}, {@code invokeAny} or a {@code schedule*}) on any {@code Executor}
   *       whatever the receiver's origin, or an executor obtained inline from {@code
   *       Executors.new*} / {@code ForkJoinPool.commonPool}. This is the shape the T1.6b review
   *       flagged: {@code Executors.newSingleThreadExecutor().execute(this::work)} has no field for
   *       the field-typed detector to see.
   * </ul>
   */
  private static boolean detachesInline(JavaClass clazz) {
    boolean completableFuture =
        clazz.getMethodCallsFromSelf().stream()
            .anyMatch(
                call ->
                    "java.util.concurrent.CompletableFuture"
                            .equals(call.getTargetOwner().getFullName())
                        && ("supplyAsync".equals(call.getName())
                            || "runAsync".equals(call.getName())));
    boolean newThread =
        clazz.getConstructorCallsFromSelf().stream()
            .anyMatch(call -> "java.lang.Thread".equals(call.getTargetOwner().getFullName()));
    return completableFuture || newThread || usesExecutorInline(clazz);
  }

  /**
   * An executor used inline, with no executor field for {@link #isDetachedHandoff} to catch: either
   * a submission call on any {@code Executor}-typed receiver (whatever its origin: a getter, a
   * method parameter, or a freshly created pool), or an executor obtained inline from {@code
   * Executors.new*} / {@code ForkJoinPool.commonPool}. Either alone is enough: the submission
   * proves work is handed off, the obtain proves a fresh unscoped pool is being spun up.
   */
  private static boolean usesExecutorInline(JavaClass clazz) {
    return clazz.getMethodCallsFromSelf().stream()
        .anyMatch(
            call -> {
              String owner = call.getTargetOwner().getFullName();
              String name = call.getName();
              boolean obtainsExecutor =
                  ("java.util.concurrent.Executors".equals(owner) && name.startsWith("new"))
                      || ("java.util.concurrent.ForkJoinPool".equals(owner)
                          && "commonPool".equals(name));
              boolean submitsToExecutor =
                  call.getTargetOwner().isAssignableTo("java.util.concurrent.Executor")
                      && (EXECUTOR_SUBMIT_METHODS.contains(name) || name.startsWith("schedule"));
              return obtainsExecutor || submitsToExecutor;
            });
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
