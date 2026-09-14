package io.openaev.architecture;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaConstructorCall;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import io.openaev.config.TenantTables;
import io.openaev.database.model.DualScopeBase;
import io.openaev.database.model.TenantBase;
import io.openaev.database.model.TenantIdBase;
import jakarta.persistence.Table;
import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
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
 *
 * <p><b>Known limit, a baseline waiver covers the whole class.</b> The baseline is keyed by class,
 * so a new background method added to a class already listed inherits that class's waiver: a second
 * {@code @Scheduled} method added to a bean waived {@code touches-no-tenant-table} is never
 * re-examined, because {@code no_baseline_entry_is_stale} only re-checks that the class is still a
 * background entry point and still off the primitive, not that the reason still fits every method
 * it now carries. Closing this at method level runs into the same delegation problem as the
 * on-primitive limit above. The mitigation is review: a change to a baselined class must re-read
 * its waiver reason against the method that was added.
 *
 * <p><b>Known limit, startup and message-listener mechanisms are out of scope.</b> The six families
 * do not recognise {@code InitializingBean.afterPropertiesSet}, {@code SmartLifecycle},
 * {@code @Bean(initMethod = ...)}, or message listeners ({@code @RabbitListener},
 * {@code @KafkaListener}, {@code @JmsListener}). None exists in production code (verified by grep
 * over all {@code src/main/java}, 2026-09-13); the day one is introduced it escapes this guard
 * until a family is added for it.
 */
public class BackgroundEntrypointTenantScopeArchTest {

  private static final String PRIMITIVE = "io.openaev.context.TenantScopedTransaction";
  private static final String BASELINE_RESOURCE = "/background-guard-baseline.txt";
  private static final String MISSED_TABLES_RESOURCE =
      "/tenant-strict-tables-missed-by-entity-scan.txt";

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
                  + " (touches-no-tenant-table / delegates-to-<Class> / platform-global /"
                  + " cross-tenant-resolve):\n");
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
  @DisplayName("every baseline reason follows the documented grammar")
  void every_baseline_reason_follows_the_grammar() {
    List<String> malformed = new ArrayList<>();
    for (RawLine line : rawBaselineEntries()) {
      if (!line.reason.isBlank() && !isWellFormedReason(line.reason)) {
        malformed.add("line " + line.number + ": '" + line.fqcn + "' -> " + line.reason);
      }
    }
    assertTrue(
        malformed.isEmpty(),
        "every reason must begin with a documented classification (touches-no-tenant-table,"
            + " delegates-to-<Class>#<method>, platform-global, cross-tenant-resolve), optionally"
            + " followed by an until-active:<table> tag and free text. Arbitrary text is not"
            + " evidence and would let a waiver live forever with no tag to expire:\n  "
            + String.join("\n  ", malformed));
  }

  @Test
  @DisplayName("no baseline entry is stale (shrink-only)")
  void no_baseline_entry_is_stale() {
    Map<String, JavaClass> byName = new LinkedHashMap<>();
    for (JavaClass clazz : PRODUCTION_CLASSES) {
      byName.put(clazz.getFullName(), clazz);
    }

    List<String> stale = staleEntries(loadBaseline(), byName);
    assertTrue(
        stale.isEmpty(),
        "background-guard-baseline.txt may only shrink; these entries are stale and must be"
            + " removed:\n  "
            + String.join("\n  ", stale));
  }

  /**
   * The baseline entries that are stale: the class is gone, is no longer a recognised background
   * entry point, or now carries the primitive. Pulled out of the test so the same rule can be
   * exercised over an injected baseline. The "no longer an entry point" arm mirrors the production
   * scan exactly: it is stale both when {@link #familiesOf} is empty and when {@link
   * #isConcreteBean} is false, because {@code every_background_entrypoint_is_scoped_or_classified}
   * skips non-concrete classes. A waived class later made abstract (or an interface/enum) is no
   * longer enumerated by the guard, so its waiver must be removed rather than left as a permanent,
   * un-enumerated exemption.
   */
  static List<String> staleEntries(Map<String, String> baseline, Map<String, JavaClass> byName) {
    List<String> stale = new ArrayList<>();
    for (String fqcn : new TreeSet<>(baseline.keySet())) {
      JavaClass clazz = byName.get(fqcn);
      if (clazz == null) {
        stale.add(fqcn + " is not a production class any more, remove it");
      } else if (!isConcreteBean(clazz) || familiesOf(clazz).isEmpty()) {
        stale.add(fqcn + " is no longer a background entry point, remove it");
      } else if (isOnPrimitive(clazz)) {
        stale.add(fqcn + " now references the primitive, remove its waiver");
      }
    }
    return stale;
  }

  @Test
  @DisplayName("no waiver outlives the v1 table it was granted against (until-active:<table>)")
  void no_baseline_waiver_outlives_its_table() {
    List<String> expired =
        expiredWaivers(loadBaseline(), productionActiveTables(), wildcardActivatedTables());
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
    List<String> unknown = unknownUntilActiveTables(loadBaseline(), knownTenantTables());
    assertTrue(
        unknown.isEmpty(),
        "an until-active:<table> tag must name a table the terminal '*' activation turns v2 (a"
            + " strict, non-outside-v2 table), or it can never expire and the waiver is permanent by"
            + " accident:\n  "
            + String.join("\n  ", unknown));
  }

  /**
   * The waivers whose until-active tag names a table not in {@code known}. Pulled out of the test
   * so the same validation can be exercised over an injected baseline: a test that only inspects
   * {@code knownTenantTables()} would still pass if this rejection were deleted.
   */
  static List<String> unknownUntilActiveTables(Map<String, String> baseline, Set<String> known) {
    List<String> unknown = new ArrayList<>();
    baseline.forEach(
        (fqcn, reason) -> {
          for (String table : untilActiveTables(reason)) {
            if (!known.contains(table)) {
              unknown.add(fqcn + "  ->  until-active:" + table);
            }
          }
        });
    return unknown;
  }

  // --- reason grammar ----------------------------------------------------------------------------

  /**
   * A reason must begin with one of the documented classifications, so an entry carries real
   * evidence and an until-active waiver is spelled as such. Anything else (e.g. {@code temporary})
   * is rejected. After the classification an until-active tag and free text may follow, separated
   * by a colon or a space. The four classifications carry the until-active tag differently:
   *
   * <ul>
   *   <li>{@code touches-no-tenant-table} : reads and writes nothing with a tenant_id column. A
   *       permanent classification, so an until-active tag is a contradiction (nothing activates to
   *       make it wrong) and is rejected: a temporary waiver must not hide behind a permanent
   *       label.
   *   <li>{@code delegates-to-<Class>#<method>} : the scope is set by the named service/method it
   *       calls; the {@code #method} separator is required so the delegate is actually named, and
   *       an until-active tag is allowed (e.g. a v1-scoped delegate) but not required.
   *   <li>{@code platform-global} : operates on platform rows or across tenants by design.
   *       Permanent like {@code touches-no-tenant-table}, so an until-active tag is rejected.
   *   <li>{@code cross-tenant-resolve} : resolves a row in another tenant by a globally-unique id,
   *       honest only while its table stays v1. It MUST carry an until-active tag, or nothing
   *       expires the waiver and a real unscoped cross-tenant access lives forever under a label
   *       that admits it is temporary.
   * </ul>
   */
  private static final Pattern TOUCHES_NO_TENANT_TABLE =
      Pattern.compile("touches-no-tenant-table(?:[:\\s].*)?", Pattern.DOTALL);

  private static final Pattern PLATFORM_GLOBAL =
      Pattern.compile("platform-global(?:[:\\s].*)?", Pattern.DOTALL);

  private static final Pattern CROSS_TENANT_RESOLVE =
      Pattern.compile("cross-tenant-resolve(?:[:\\s].*)?", Pattern.DOTALL);

  private static final Pattern DELEGATES_TO =
      Pattern.compile("delegates-to-[A-Za-z0-9_.]+#[A-Za-z0-9_]+(?:[:\\s].*)?", Pattern.DOTALL);

  static boolean isWellFormedReason(String reason) {
    String r = reason.strip();
    if (hasMalformedUntilActive(r)) {
      return false;
    }
    boolean carriesUntilActive = !untilActiveTables(r).isEmpty();
    if (TOUCHES_NO_TENANT_TABLE.matcher(r).matches() || PLATFORM_GLOBAL.matcher(r).matches()) {
      return !carriesUntilActive;
    }
    if (CROSS_TENANT_RESOLVE.matcher(r).matches()) {
      return carriesUntilActive;
    }
    return DELEGATES_TO.matcher(r).matches();
  }

  // --- until-active waivers ----------------------------------------------------------------------

  /**
   * The tag is token-bounded: the table name ends at the first character that is neither a name
   * char nor a hyphen, so {@code until-active:injects-typo} matches nothing (the trailing {@code
   * -typo} is not a legal continuation and a bare {@code injects} prefix would swallow a typo). A
   * {@code cross-tenant-resolve} whose only tag is malformed then carries no tag at all and the
   * grammar rejects it, rather than silently expiring against the wrong table.
   */
  private static final Pattern UNTIL_ACTIVE =
      Pattern.compile("until-active:([a-z0-9_]+)(?![a-z0-9_-])", Pattern.CASE_INSENSITIVE);

  /** Every mention of the reserved keyword, so a malformed one cannot be read as "no tag". */
  private static final Pattern UNTIL_ACTIVE_KEYWORD =
      Pattern.compile("until-active", Pattern.CASE_INSENSITIVE);

  /**
   * Whether the reason mentions {@code until-active} in any form other than a well-formed {@code
   * until-active:<table>} tag. {@link #untilActiveTables} returns nothing for a malformed token (a
   * hyphenated name like {@code until-active:injects-typo}, an empty {@code until-active:}, a colon
   * broken by a space), so a permanent classification reads it as "no tag" and accepts it, and a
   * delegates-to reads it as an absent optional tag: the unknown-table and expiry checks never see
   * it and a typo launders a permanent waiver. So {@code isWellFormedReason} rejects a reason where
   * any {@code until-active} keyword does not begin a valid tag, for every classification, before
   * its own rule. The keyword must open a match of the same {@link #UNTIL_ACTIVE} pattern the
   * extractor uses, so there is one source of truth for what a valid tag is.
   */
  static boolean hasMalformedUntilActive(String reason) {
    Matcher keyword = UNTIL_ACTIVE_KEYWORD.matcher(reason);
    Matcher tag = UNTIL_ACTIVE.matcher(reason);
    while (keyword.find()) {
      if (!(tag.find(keyword.start()) && tag.start() == keyword.start())) {
        return true;
      }
    }
    return false;
  }

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
   * The waivers whose table is no longer v1. Under an explicit allowlist a table counts as active
   * when it is named. Under the wildcard {@link TenantTables#ALL_STRICT} the terminal state does
   * not activate <em>every</em> table: {@code *} activates strict tables only, minus those
   * permanently outside v2 ({@link TenantTables#restrictTo}). So a tag expires under {@code *} only
   * when its table is in {@code wildcardActivates} (the {@code *}-expansion). A dual-scope or
   * outside-v2 table is never in that set, so a mis-tagged waiver on one does not spuriously expire
   * here; it is rejected upstream by {@code every_until_active_tag_names_a_real_tenant_table},
   * which restricts legal until-active tables to the same {@code *}-activated set.
   *
   * <p>(This check reads the production {@code application.properties} file, so it sees {@code *}
   * only once that file carries it; the nightly shadow run arms {@code *} through a JVM property
   * that never reaches this file read.)
   */
  static List<String> expiredWaivers(
      Map<String, String> baseline, Set<String> activeTables, Set<String> wildcardActivates) {
    boolean wildcard =
        activeTables.stream().anyMatch(t -> TenantTables.ALL_STRICT.equals(t.strip()));
    Set<String> nowActive =
        (wildcard ? wildcardActivates : activeTables)
            .stream().map(t -> t.strip().toLowerCase(Locale.ROOT)).collect(Collectors.toSet());
    List<String> expired = new ArrayList<>();
    baseline.forEach(
        (fqcn, reason) -> {
          for (String table : untilActiveTables(reason)) {
            if (nowActive.contains(table)) {
              expired.add(
                  fqcn
                      + " is waived until '"
                      + table
                      + "' activates, but that table is now active"
                      + (wildcard
                          ? " ('*' activates every strict table except those outside v2)"
                          : "")
                      + "; scope the path through TenantScopedTransaction and remove the waiver");
            }
          }
        });
    return expired;
  }

  /**
   * The tables the terminal {@code *} activation turns v2: the strict entity tables minus those
   * outside v2, plus the strict tables that carry a {@code tenant_id} but no tenant-marker entity
   * ({@link #strictTablesMissedByEntityScan()}). Production derives its set from {@code
   * information_schema} and so covers those; this guard has no database, so it unions the
   * checked-in inventory to stay identical to production. {@code
   * TenantFilteringConfigTest#backgroundGuardWildcardMatchesProductionSchema} fails the build if
   * the two ever diverge.
   */
  public static Set<String> wildcardActivatedTables() {
    Set<String> activated =
        new TreeSet<>(
            productionTenantTables().restrictTo(List.of(TenantTables.ALL_STRICT)).strict());
    activated.addAll(strictTablesMissedByEntityScan());
    return activated;
  }

  /**
   * Strict tenant tables (a {@code tenant_id NOT NULL} column) that the entity-model derivation
   * cannot see: link tables with no entity, or an entity that maps a tenant_id table without any of
   * the {@code TenantBase}/{@code DualScopeBase}/{@code TenantIdBase} markers. Read from {@code
   * tenant-strict-tables-missed-by-entity-scan.txt}; kept honest against the live schema by the
   * DB-backed {@code TenantFilteringConfigTest}.
   */
  static Set<String> strictTablesMissedByEntityScan() {
    Set<String> tables = new TreeSet<>();
    try (InputStream in =
        BackgroundEntrypointTenantScopeArchTest.class.getResourceAsStream(MISSED_TABLES_RESOURCE)) {
      if (in == null) {
        throw new IllegalStateException(MISSED_TABLES_RESOURCE + " is missing from the classpath");
      }
      try (BufferedReader reader =
          new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
        String raw;
        while ((raw = reader.readLine()) != null) {
          String line = raw.strip();
          if (line.isEmpty() || line.startsWith("#")) {
            continue;
          }
          tables.add(line.toLowerCase(Locale.ROOT));
        }
      }
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    return tables;
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
   * The tenant tables of the entity model. {@link TenantTables#fromEntities} classifies {@link
   * TenantBase}/{@link DualScopeBase}; the model also has a third strict marker, {@link
   * TenantIdBase} (e.g. Collector/Injector/Executor), which {@code fromEntities} does not read, so
   * its tables are added here as strict. Tables the entity model does not mark at all (link tables
   * with no entity, or an entity that maps a tenant_id table without a marker) are covered by
   * {@link #strictTablesMissedByEntityScan()}.
   */
  static TenantTables productionTenantTables() {
    List<Class<?>> markedEntities = new ArrayList<>();
    Set<String> tenantIdStrict = new TreeSet<>();
    for (JavaClass clazz : PRODUCTION_CLASSES) {
      if (clazz.isInterface() || clazz.getModifiers().contains(JavaModifier.ABSTRACT)) {
        continue;
      }
      if (clazz.isAssignableTo(TenantBase.class) || clazz.isAssignableTo(DualScopeBase.class)) {
        markedEntities.add(clazz.reflect());
      } else if (clazz.isAssignableTo(TenantIdBase.class)) {
        // A concrete TenantIdBase without a @Table is not a mapped table (e.g. the
        // SecretsProvider$Placeholder helper); it has no row in the schema, so skip it.
        String table = entityTableName(clazz.reflect());
        if (table != null) {
          tenantIdStrict.add(table);
        }
      }
    }
    TenantTables marked = TenantTables.fromEntities(markedEntities);
    Set<String> strict = new TreeSet<>(marked.strict());
    strict.addAll(tenantIdStrict);
    return new TenantTables(strict, marked.dualScope());
  }

  /**
   * The {@code @Table} name of an entity, walking up to the nearest mapping, or null if unmapped.
   */
  private static String entityTableName(Class<?> entity) {
    for (Class<?> type = entity;
        type != null && type != Object.class;
        type = type.getSuperclass()) {
      Table table = type.getAnnotation(Table.class);
      if (table != null && !table.name().isBlank()) {
        return table.name();
      }
    }
    return null;
  }

  /**
   * The tables an until-active tag may legally name: exactly those the terminal {@code *}
   * activation turns v2 (strict, minus those permanently outside v2), computed through {@link
   * TenantTables#restrictTo}. A dual-scope table ({@code *} never activates one) or an outside-v2
   * strict table would give a waiver whose expiry the terminal state never enforces, so it is
   * permanent by accident and rejected here, together with a typo that names no table at all.
   */
  static Set<String> knownTenantTables() {
    return wildcardActivatedTables();
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
        clazz.getAllFields().stream()
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
   *
   * <p>The scan reads the class's own code and the bodies of the methods it INHERITS from
   * non-{@code Object} superclasses, so a concrete bean whose only hand-off is declared in an
   * abstract parent's method (the parent is filtered out by {@link #isConcreteBean}, so it is never
   * checked directly) is not missed. This matches the inheritance behaviour of the annotation and
   * field family detectors ({@code getAllMethods}/{@code getAllFields}); {@code
   * getMethodCallsFromSelf} on the class alone sees only self-declared bodies.
   */
  private static boolean detachesInline(JavaClass clazz) {
    List<JavaMethodCall> methodCalls = methodCallsIncludingInherited(clazz);
    List<JavaConstructorCall> constructorCalls = constructorCallsIncludingInherited(clazz);
    boolean completableFuture =
        methodCalls.stream()
            .anyMatch(
                call ->
                    "java.util.concurrent.CompletableFuture"
                            .equals(call.getTargetOwner().getFullName())
                        && ("supplyAsync".equals(call.getName())
                            || "runAsync".equals(call.getName())));
    boolean newThread =
        constructorCalls.stream()
            .anyMatch(call -> "java.lang.Thread".equals(call.getTargetOwner().getFullName()));
    return completableFuture || newThread || usesExecutorInline(methodCalls);
  }

  /**
   * The method calls made from the class's own code plus the bodies of the methods it inherits from
   * non-{@code Object} superclasses. An inherited method is owned by the declaring parent, so its
   * calls are not in {@code clazz.getMethodCallsFromSelf()}; they are collected here from {@link
   * JavaClass#getAllMethods()}.
   */
  private static List<JavaMethodCall> methodCallsIncludingInherited(JavaClass clazz) {
    List<JavaMethodCall> calls = new ArrayList<>(clazz.getMethodCallsFromSelf());
    for (JavaMethod method : clazz.getAllMethods()) {
      if (isInheritedNonObjectBody(clazz, method)) {
        calls.addAll(method.getMethodCallsFromSelf());
      }
    }
    return calls;
  }

  private static List<JavaConstructorCall> constructorCallsIncludingInherited(JavaClass clazz) {
    List<JavaConstructorCall> calls = new ArrayList<>(clazz.getConstructorCallsFromSelf());
    for (JavaMethod method : clazz.getAllMethods()) {
      if (isInheritedNonObjectBody(clazz, method)) {
        calls.addAll(method.getConstructorCallsFromSelf());
      }
    }
    return calls;
  }

  private static boolean isInheritedNonObjectBody(JavaClass clazz, JavaMethod method) {
    JavaClass owner = method.getOwner();
    return !owner.equals(clazz) && !owner.getFullName().equals("java.lang.Object");
  }

  /**
   * An executor used inline, with no executor field for {@link #isDetachedHandoff} to catch: either
   * a submission call on any {@code Executor}-typed receiver (whatever its origin: a getter, a
   * method parameter, or a freshly created pool), or an executor obtained inline from {@code
   * Executors.new*} / {@code ForkJoinPool.commonPool}. Either alone is enough: the submission
   * proves work is handed off, the obtain proves a fresh unscoped pool is being spun up.
   */
  private static boolean usesExecutorInline(List<JavaMethodCall> methodCalls) {
    return methodCalls.stream()
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

  static Map<String, String> loadBaseline() {
    return baselineFrom(rawBaselineEntries());
  }

  /**
   * Builds the class-keyed baseline, rejecting duplicate FQCNs. A duplicate is not benign: a later
   * line silently overwrites the earlier reason, so a permissive second entry can bury an {@code
   * until-active} tag while every stale-entry check still passes. Both offending line numbers are
   * named so the duplicate is easy to remove.
   */
  static Map<String, String> baselineFrom(List<RawLine> entries) {
    Map<String, String> baseline = new LinkedHashMap<>();
    Map<String, Integer> firstSeen = new HashMap<>();
    for (RawLine line : entries) {
      Integer previous = firstSeen.putIfAbsent(line.fqcn(), line.number());
      if (previous != null) {
        throw new IllegalStateException(
            "background-guard-baseline.txt has a duplicate entry for '"
                + line.fqcn()
                + "' at lines "
                + previous
                + " and "
                + line.number()
                + "; a duplicate silently overwrites the earlier reason, remove one");
      }
      baseline.put(line.fqcn(), line.reason());
    }
    return baseline;
  }

  record RawLine(int number, String fqcn, String reason) {}

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
