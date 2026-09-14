package io.openaev.architecture;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import io.openaev.config.TenantFilteringException;
import io.openaev.config.TenantStatementInspector;
import io.openaev.config.TenantTables;
import jakarta.persistence.Entity;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

/**
 * Runs every {@code @Query(nativeQuery = true)} in the repository layer through {@link
 * TenantStatementInspector} with every tenant table active, and fails naming the repository method
 * of any query the inspector refuses. Activating a table then stops being the moment a refused
 * query shape is discovered in production (the {@code #7007} / {@code #6438} class of regression):
 * the shape is pinned here, before go-live, on the real SQL read reflectively off the annotation.
 *
 * <p>The active set is derived from the entity model with {@link TenantTables#fromEntities}, which
 * is a plain unit-test path needing no database. It misses tables that carry a {@code tenant_id}
 * but have no entity class (join and link tables); those are only visible to the schema-derived
 * production path ({@code TenantFilteringConfig#deriveFromSchema}), which needs a live schema. The
 * inspector's gate keys on table names, so a query that touches only such a name is not exercised
 * here; the tables it names are still covered whenever they are entity-backed.
 *
 * <p>A query whose SpEL or parameter syntax cannot be parsed even before the inspector touches it
 * is not a failure of the inspector: it is reported separately (parse gate below) and skipped,
 * because the probe checks the rewrite shape, not parameter binding.
 */
@DisplayName("Native queries survive the tenant inspector with every table active")
class NativeQueryTenantInspectorProbeTest {

  /**
   * Spring resolves a SpEL selector ({@code :#{...}} or {@code ?#{...}}) into a bind parameter long
   * before Hibernate sees the SQL, so JSqlParser only ever parses the resolved form. Collapse each
   * to a positional placeholder so the shape can be parsed.
   */
  private static final Pattern SPEL = Pattern.compile("[:?]#\\{[^}]*}");

  private record NativeQuery(String method, String sql) {}

  @Test
  @DisplayName("no native query is refused by the inspector when every tenant table is active")
  void noNativeQueryIsRefusedWithEveryTableActive() {
    JavaClasses classes =
        new ClassFileImporter()
            .withImportOption(new ImportOption.DoNotIncludeTests())
            .importPackages("io.openaev");

    TenantStatementInspector inspector = new TenantStatementInspector(everyTenantTable(classes));

    List<NativeQuery> queries = nativeQueries(classes);
    List<String> refused = new ArrayList<>();
    List<String> unparseable = new ArrayList<>();

    for (NativeQuery query : queries) {
      String sql = SPEL.matcher(query.sql()).replaceAll("?");
      try {
        CCJSqlParserUtil.parse(sql);
      } catch (Exception parseFailure) {
        // A parse failure is NOT a free pass. TenantStatementInspector.inspect uses the same parser
        // and refuses what it cannot parse, fail-closed, so a query that does not parse here is a
        // query that breaks once its table is active. It is tracked separately only to give a
        // clearer
        // message (make it parseable, or carry a reason), and the assertion below fails on it.
        unparseable.add(query.method() + " :: " + parseFailure.getMessage());
        continue;
      }
      try {
        inspector.inspect(sql);
      } catch (TenantFilteringException refusal) {
        refused.add(query.method() + " :: " + refusal.getMessage());
      }
    }

    System.out.println(
        "[native-query-probe] total="
            + queries.size()
            + " refused="
            + refused.size()
            + " unparseable-before-rewrite="
            + unparseable.size());
    System.out.println("[native-query-probe] unparseable: " + unparseable);

    assertTrue(
        unparseable.isEmpty(),
        "native queries JSqlParser cannot read, which the inspector refuses fail-closed in production"
            + " exactly as it does here, so activating their table breaks them:\n  "
            + String.join("\n  ", unparseable));

    assertTrue(
        refused.isEmpty(),
        "the tenant inspector refused native queries that activating their table would break in"
            + " production; add the reviewed-safe marker (e.g. LATERAL) or cover the shape:\n"
            + String.join("\n", refused));
  }

  /**
   * Every {@code @Query(nativeQuery = true)} declared on a Spring Data repository interface, read
   * reflectively so a query added or edited tomorrow is covered without a hand-maintained list.
   */
  private static List<NativeQuery> nativeQueries(JavaClasses classes) {
    List<NativeQuery> found = new ArrayList<>();
    for (JavaClass javaClass : classes) {
      if (!javaClass.isInterface()) {
        continue;
      }
      Class<?> repository = javaClass.reflect();
      if (!Repository.class.isAssignableFrom(repository)) {
        continue;
      }
      for (Method method : repository.getDeclaredMethods()) {
        Query query = method.getAnnotation(Query.class);
        if (query != null && query.nativeQuery() && !query.value().isBlank()) {
          found.add(
              new NativeQuery(repository.getSimpleName() + "#" + method.getName(), query.value()));
        }
      }
    }
    return found;
  }

  /**
   * Every tenant table derived from the entity model, strict and dual-scope alike, so the probe
   * exercises the widest activation the inspector will ever face.
   */
  private static TenantTables everyTenantTable(JavaClasses classes) {
    List<Class<?>> entities =
        classes.stream()
            .filter(javaClass -> javaClass.isAnnotatedWith(Entity.class))
            .<Class<?>>map(JavaClass::reflect)
            .toList();
    return TenantTables.fromEntities(entities);
  }
}
