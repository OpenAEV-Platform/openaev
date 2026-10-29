package io.openaev.architecture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import io.openaev.config.ProductionActiveTables;
import io.openaev.config.TenantTables;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.select.WithItem;
import net.sf.jsqlparser.util.TablesNamesFinder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

/**
 * Refuses a CTE named after a tenant table in any repository native query.
 *
 * <p>The tenant statement inspector decides what to wrap from the relation name alone. A reference
 * to a CTE called {@code exercises} is therefore wrapped as the {@code exercises} table once that
 * table is active, the wrapper reads {@code tenant_id} from a CTE that has none, and PostgreSQL
 * refuses the whole statement. The inspector itself accepts the rewrite, so {@link
 * NativeQueryTenantInspectorProbeTest} stays green: the failure only shows on a live database, at
 * activation time.
 *
 * <p>The names checked are every table the inspector wraps today or will wrap once the rollout
 * ends: the production activation list ({@link ProductionActiveTables}), the strict and dual-scope
 * tenant tables of the entity model and its missed-table inventory ({@code
 * BackgroundEntrypointTenantScopeArchTest#knownTenantTables()}, a superset of the activation
 * burn-down, since {@code TenantTableActivationCoverageArchTest} refuses a deferral that names a
 * table outside it), and the self-isolated tables. Needs no database and no Spring context.
 *
 * <p>What this guard does not see, so a green run is worth no more than this:
 *
 * <ul>
 *   <li>native SQL outside a {@code @Query(nativeQuery = true)} annotation: {@code
 *       createNativeQuery}, {@code JdbcTemplate}, SQL assembled in a service;
 *   <li>a CTE whose name is built at runtime, since only the annotation's constant text is read;
 *   <li>repositories outside {@code io.openaev.database.repository};
 *   <li>tables scoped by a dimension other than the tenant one (markings).
 * </ul>
 */
@DisplayName("No native query names a CTE after a tenant table")
class NativeQueryCteNameArchTest {

  private static final String REPOSITORY_PACKAGE = "io.openaev.database.repository";

  /**
   * Spring resolves a SpEL selector into a bind parameter before Hibernate sees the SQL, so the
   * parser only ever reads the resolved form.
   */
  private static final Pattern SPEL = Pattern.compile("[:?]#\\{[^}]*}");

  private record NativeQuery(String location, String sql) {}

  @Nested
  @DisplayName("Repository native queries")
  class RepositoryQueries {

    @Test
    @DisplayName("no CTE carries the name of a tenant table")
    void given_everyRepositoryNativeQuery_should_nameNoCteAfterATenantTable() throws Exception {
      // Arrange
      Set<String> tenantTables = tenantTables();
      List<NativeQuery> queries = nativeQueries();

      // Act
      List<String> collisions = new ArrayList<>();
      for (NativeQuery query : queries) {
        for (String cte : collidingCteNames(query.sql(), tenantTables)) {
          collisions.add(query.location() + " :: CTE '" + cte + "' is a tenant table");
        }
      }

      // Assert
      assertFalse(queries.isEmpty(), "the scan found no native query, so it checked nothing");
      assertTrue(
          collisions.isEmpty(),
          "the tenant inspector wraps a relation by name, so a CTE named after a tenant table is"
              + " rewritten as that table once it is active and PostgreSQL refuses the statement."
              + " Rename the CTE (for instance with an _agg suffix):\n  "
              + String.join("\n  ", collisions));
    }
  }

  @Nested
  @DisplayName("CTE name detection")
  class Detection {

    private static final Set<String> TABLES = Set.of("exercises", "tags");

    @Test
    @DisplayName("a top-level CTE named after a tenant table is reported")
    void given_topLevelCteNamedAfterTable_should_reportIt() throws Exception {
      // Arrange
      String sql = "WITH exercises AS (SELECT 1 AS id) SELECT * FROM exercises ex";

      // Act
      Set<String> collisions = collidingCteNames(sql, TABLES);

      // Assert
      assertEquals(Set.of("exercises"), collisions);
    }

    @Test
    @DisplayName("a CTE nested in a sub-query, quoted or upper case, is reported")
    void given_nestedQuotedUpperCaseCte_should_reportIt() throws Exception {
      // Arrange
      String sql =
          "SELECT * FROM scenarios s WHERE s.id IN ("
              + "WITH \"TAGS\" AS (SELECT 1 AS id) SELECT id FROM \"TAGS\")";

      // Act
      Set<String> collisions = collidingCteNames(sql, TABLES);

      // Assert
      assertEquals(Set.of("tags"), collisions);
    }

    @Test
    @DisplayName("a CTE with a distinct name, or a table only aliased, is not reported")
    void given_distinctCteAndTableAlias_should_reportNothing() throws Exception {
      // Arrange
      String sql =
          "WITH exercises_agg AS (SELECT 1 AS id) "
              + "SELECT * FROM exercises_agg ea JOIN scenarios exercises ON true";

      // Act
      Set<String> collisions = collidingCteNames(sql, TABLES);

      // Assert
      assertTrue(collisions.isEmpty(), collisions.toString());
    }
  }

  /** The CTE names of {@code sql}, at any depth, that match a table of {@code tables}. */
  private static Set<String> collidingCteNames(String sql, Set<String> tables)
      throws JSQLParserException {
    CteNameCollector collector = new CteNameCollector();
    collector.getTables(CCJSqlParserUtil.parse(SPEL.matcher(sql).replaceAll("?")));
    Set<String> collisions = new TreeSet<>(collector.names);
    collisions.retainAll(tables);
    return collisions;
  }

  /** Every table the tenant inspector wraps today or will wrap, lowercased. */
  private static Set<String> tenantTables() {
    Set<String> tables = new TreeSet<>(ProductionActiveTables.names());
    tables.addAll(BackgroundEntrypointTenantScopeArchTest.knownTenantTables());
    tables.addAll(TenantTables.selfIsolatedTables());
    Set<String> normalised = new TreeSet<>();
    tables.forEach(table -> normalised.add(table.toLowerCase(Locale.ROOT)));
    return normalised;
  }

  /** Every {@code @Query(nativeQuery = true)} declared on a repository interface. */
  private static List<NativeQuery> nativeQueries() {
    List<NativeQuery> found = new ArrayList<>();
    for (JavaClass javaClass :
        new ClassFileImporter()
            .withImportOption(new ImportOption.DoNotIncludeTests())
            .importPackages(REPOSITORY_PACKAGE)) {
      if (!javaClass.isInterface()) {
        continue;
      }
      Class<?> repository = javaClass.reflect();
      if (!Repository.class.isAssignableFrom(repository)) {
        continue;
      }
      for (Method method : repository.getDeclaredMethods()) {
        // javac copies the annotation onto a bridge method, which would report a query twice.
        if (method.isBridge()) {
          continue;
        }
        Query query = method.getAnnotation(Query.class);
        if (query != null && query.nativeQuery() && !query.value().isBlank()) {
          found.add(new NativeQuery(repository.getName() + "#" + method.getName(), query.value()));
        }
      }
    }
    return found;
  }

  /**
   * Records the name of every CTE the finder visits, unquoted and lowercased like the inspector.
   */
  private static final class CteNameCollector extends TablesNamesFinder<Void> {
    private final Set<String> names = new TreeSet<>();

    @Override
    public <S> Void visit(WithItem<?> withItem, S context) {
      names.add(withItem.getUnquotedAliasName().toLowerCase(Locale.ROOT));
      return super.visit(withItem, context);
    }
  }
}
