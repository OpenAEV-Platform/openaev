package io.openaev.architecture;

import static com.tngtech.archunit.base.DescribedPredicate.not;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.belongToAnyOf;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import io.openaev.config.security.SecurityService;
import io.openaev.database.model.User;
import io.openaev.database.repository.TenantRepository;
import io.openaev.importer.V1_DataImporter;
import io.openaev.notification.engine.NotificationEngineListener;
import io.openaev.opencti.connectors.service.OpenCTIConnectorPrivilegeService;
import io.openaev.processor.datapack.V20260805_Observer_and_manager_users;
import io.openaev.rest.user.PlayerService;
import io.openaev.runner.InitAdminCommandLineRunner;
import io.openaev.service.UserMappingService;
import io.openaev.service.UserService;
import io.openaev.service.account.AdminPrivilegeService;
import io.openaev.service.account.AgentServiceAccountPrivilegeService;
import io.openaev.service.account.CatalogConnectorPrivilegeService;
import io.openaev.service.tenants.TenantUserService;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.data.jpa.repository.NativeQuery;
import org.springframework.data.jpa.repository.Query;

/**
 * Who writes {@code users_tenants}, and where each writer gets the tenant of the row. The table is
 * self-isolated: the statement inspector never gates it (see {@code TenantTables}), so a membership
 * written into the wrong tenant is a grant nothing but the writer's own choice of tenant prevents.
 * The write-attribution detector catches such a write in the test suite; this test makes adding a
 * writer a reviewed decision, one list entry carrying the answer to "where does its tenant come
 * from".
 *
 * <p>The table is written three ways, and each has its own rule below:
 *
 * <ul>
 *   <li>the native statements {@code TenantRepository#addUserToTenant} and {@code
 *       #removeUserFromTenant};
 *   <li>{@code TenantUserService#attachToTenant}, the service door to the first one, which
 *       provisioning code uses with the tenant it provisions;
 *   <li>the {@code User#tenants} collection, which Hibernate writes to the table when the user is
 *       saved ({@code setTenants}, or {@code getTenants().add}).
 * </ul>
 *
 * <p>Denominator: the main classes of {@code io.openaev}, a call counting when it names one of the
 * methods above, plus every repository method whose merged {@code @Query} writes {@code
 * users_tenants} (see {@code only_the_two_known_queries_write_users_tenants}: a third statement
 * spelled out in a repository fails). The annotation is read merged, so {@code @NativeQuery} and
 * any annotation meta-annotated with {@code @Query} count. Not seen: a membership written through
 * SQL that no such annotation carries ({@code JdbcTemplate}, {@code
 * EntityManager#createNativeQuery}, a named query declared on an entity or in {@code orm.xml}, a
 * string built at run time), a mutation of the {@code tenants} collection through a reference that
 * is not a {@code getTenants} or {@code setTenants} call (reflection, a copy constructor), the
 * foreign key cascade that removes the rows of a deleted tenant or user, and Flyway migrations. A
 * user saved after an unrelated {@code getTenants} caller needs no rule: reading the collection
 * does not dirty it, only a caller that adds to it or sets it does, and those are the listed ones.
 *
 * <p>Each list is also checked the other way: an entry that no longer calls its method is stale and
 * fails, so the lists name the writers of the tree exactly.
 */
@AnalyzeClasses(packages = "io.openaev", importOptions = ImportOption.DoNotIncludeTests.class)
class UsersTenantsWritersArchTest {

  private record Writer(Class<?> type, String tenantSource) {}

  /** Callers of the native membership statements. */
  private static final List<Writer> NATIVE_WRITERS =
      List.of(
          new Writer(
              TenantUserService.class,
              "attachToTenant takes the tenant as an argument; createOrAttach and detach resolve"
                  + " it from the request's write scope (TenantWriteScopeResolver)"),
          new Writer(
              V1_DataImporter.class,
              "the tenant the import writes to: importData aligns the ambient tenant on the"
                  + " resolved write tenant for the whole import (AmbientTenantBridge)"));

  /** Callers of {@code TenantUserService#attachToTenant}. */
  private static final List<Writer> ATTACH_CALLERS =
      List.of(
          new Writer(
              TenantUserService.class,
              "createOrAttach: the request's write scope; createDependencyForTenant: the tenant"
                  + " being created"),
          new Writer(PlayerService.class, "the request's write scope (TenantWriteScopeResolver)"),
          new Writer(AdminPrivilegeService.class, "the tenantId argument of the self-heal"),
          new Writer(
              AgentServiceAccountPrivilegeService.class,
              "the tenantId of the account it provisions"),
          new Writer(
              CatalogConnectorPrivilegeService.class, "the tenantId of the account it provisions"),
          new Writer(
              OpenCTIConnectorPrivilegeService.class,
              "the tenant of the connector row itself (connector.getTenantId())"),
          new Writer(
              InitAdminCommandLineRunner.class, "the default tenant constant, at startup only"));

  /** Callers of {@code User#setTenants} and {@code User#getTenants}. */
  private static final List<Writer> ENTITY_COLLECTION_USERS =
      List.of(
          new Writer(
              UserService.class,
              "the tenants chosen by a platform administrator (input.tenantIds), none for a"
                  + " tenant-scoped creation"),
          new Writer(
              UserMappingService.class,
              "the tenant of the SSO group mapping that grants the group"),
          new Writer(
              SecurityService.class,
              "the tenant configured for the SSO provider, the default tenant otherwise"),
          new Writer(
              V1_DataImporter.class,
              "the ambient tenant, aligned on the import's write tenant (AmbientTenantBridge)"),
          new Writer(
              V20260805_Observer_and_manager_users.class, "the tenant the datapack runs for"),
          new Writer(NotificationEngineListener.class, "reads the tenant ids only"),
          new Writer(io.openaev.api.users.dto.UserMapper.class, "reads the tenants only"));

  private static Class<?>[] types(List<Writer> writers) {
    return writers.stream().map(Writer::type).toArray(Class<?>[]::new);
  }

  @ArchTest
  static final ArchRule only_listed_classes_call_the_native_membership_statements =
      noClasses()
          .that(not(belongToAnyOf(types(NATIVE_WRITERS))))
          .should()
          .callMethod(TenantRepository.class, "addUserToTenant", String.class, String.class)
          .orShould()
          .callMethod(TenantRepository.class, "removeUserFromTenant", String.class, String.class)
          .because(
              "a membership written into the wrong tenant is a grant, and the table is not gated"
                  + " by the statement inspector: add the caller to NATIVE_WRITERS with where its"
                  + " tenant comes from");

  @ArchTest
  static final ArchRule only_listed_classes_attach_a_user_to_a_tenant =
      noClasses()
          .that(not(belongToAnyOf(types(ATTACH_CALLERS))))
          .should()
          .callMethod(TenantUserService.class, "attachToTenant", String.class, String.class)
          .because(
              "attachToTenant writes the membership of the tenant it is given: add the caller to"
                  + " ATTACH_CALLERS with where its tenant comes from");

  @ArchTest
  static final ArchRule only_listed_classes_touch_the_tenants_of_a_user =
      noClasses()
          .that(not(belongToAnyOf(types(ENTITY_COLLECTION_USERS))))
          .should()
          .callMethod(User.class, "setTenants", java.util.List.class)
          .orShould()
          .callMethod(User.class, "getTenants")
          .because(
              "saving a user writes the rows of its tenants collection to users_tenants: add the"
                  + " caller to ENTITY_COLLECTION_USERS with where its tenant comes from");

  @ArchTest
  static void only_the_two_known_queries_write_users_tenants(JavaClasses classes) {
    assertThat(queryWritersOf(classes))
        .as(
            "repository queries that write users_tenants: a new one is a new writer of a table the"
                + " inspector does not gate, so name it here with where its tenant comes from")
        .containsExactly(
            "TenantRepository#addUserToTenant", "TenantRepository#removeUserFromTenant");
  }

  private static List<String> queryWritersOf(JavaClasses classes) {
    return classes.stream()
        .flatMap(javaClass -> javaClass.getMethods().stream())
        .filter(
            method ->
                Optional.ofNullable(
                        AnnotatedElementUtils.findMergedAnnotation(method.reflect(), Query.class))
                    .map(Query::value)
                    .filter(UsersTenantsWritersArchTest::writesUsersTenants)
                    .isPresent())
        .map(method -> method.getOwner().getSimpleName() + "#" + method.getName())
        .sorted()
        .toList();
  }

  private static boolean writesUsersTenants(String query) {
    String sql = query.toLowerCase(java.util.Locale.ROOT);
    return sql.contains("users_tenants") && WRITE_STATEMENT.matcher(sql).find();
  }

  /**
   * A write statement, also one inside a CTE; a column named {@code user_updated_at} is not. The
   * table of an UPDATE may carry an alias, with or without {@code AS}; the tables of an INSERT, a
   * DELETE and a MERGE are not read, so their aliases change nothing. A TRUNCATE is a write
   * whatever tables it lists, since the caller already requires the statement to name {@code
   * users_tenants}.
   */
  private static final Pattern WRITE_STATEMENT =
      Pattern.compile(
          "\\binsert\\s+into\\b|\\bdelete\\s+from\\b|\\bmerge\\s+into\\b|\\btruncate\\b"
              + "|\\bupdate\\s+\\S+(?:\\s+(?:as\\s+)?\\w+)?\\s+set\\b");

  private static final List<String> WRITES_OF_USERS_TENANTS =
      List.of(
          "UPDATE users_tenants SET tenant_id = :tenantId WHERE user_id = :userId",
          "UPDATE users_tenants ut SET tenant_id = :tenantId WHERE ut.user_id = :userId",
          "UPDATE users_tenants AS ut SET tenant_id = :tenantId WHERE ut.user_id = :userId",
          "UPDATE\n    users_tenants\n    AS ut\n  SET tenant_id = :tenantId",
          "DELETE FROM users_tenants ut WHERE ut.user_id = :userId",
          "DELETE FROM users_tenants AS ut WHERE ut.user_id = :userId",
          "INSERT INTO users_tenants AS ut (user_id, tenant_id) VALUES (:userId, :tenantId)",
          "WITH moved AS (UPDATE users_tenants ut SET tenant_id = :t RETURNING user_id)"
              + " SELECT * FROM moved",
          "MERGE INTO users_tenants ut USING (SELECT :userId AS user_id) src"
              + " ON ut.user_id = src.user_id WHEN NOT MATCHED THEN"
              + " INSERT (user_id, tenant_id) VALUES (:userId, :tenantId)",
          "merge into users_tenants as ut using users u on ut.user_id = u.user_id"
              + " when matched then delete",
          "TRUNCATE users_tenants",
          "TRUNCATE TABLE ONLY users_tenants RESTART IDENTITY CASCADE",
          "truncate table users, users_tenants");

  private static final List<String> READS_OF_USERS_TENANTS =
      List.of(
          "SELECT ut.tenant_id FROM users_tenants ut WHERE ut.user_id = :userId",
          "SELECT u.user_updated_at FROM users u JOIN users_tenants ut ON ut.user_id = u.user_id",
          "SELECT * FROM users_tenants ut ORDER BY ut.user_id",
          "SELECT ut.tenant_id, ut.truncated_at, ut.merge_into_id FROM users_tenants ut");

  @ArchTest
  static void the_writer_detection_reads_aliased_statements(JavaClasses classes) {
    assertThat(WRITES_OF_USERS_TENANTS)
        .as("statements the detection must report as writing users_tenants")
        .allMatch(UsersTenantsWritersArchTest::writesUsersTenants);
    assertThat(READS_OF_USERS_TENANTS)
        .as("statements the detection must not report")
        .noneMatch(UsersTenantsWritersArchTest::writesUsersTenants);
  }

  /**
   * Writes {@code users_tenants} through {@code @NativeQuery}, which Spring Data composes from
   * {@code @Query}. {@code @Query} and {@code @Modifying} themselves cannot be written in this
   * module (NoRepositoryRule).
   */
  private interface NativeQueryMover {
    @NativeQuery("UPDATE users_tenants SET tenant_id = :tenantId WHERE user_id = :userId")
    void move(String userId, String tenantId);
  }

  private interface NativeQueryReader {
    @NativeQuery("SELECT tenant_id FROM users_tenants WHERE user_id = :userId")
    List<String> tenantsOf(String userId);
  }

  @ArchTest
  static void the_writer_detection_reads_composed_query_annotations(JavaClasses classes) {
    JavaClasses fixtures =
        new ClassFileImporter().importClasses(NativeQueryMover.class, NativeQueryReader.class);

    assertThat(queryWritersOf(fixtures))
        .as("a @NativeQuery write is reported, a @NativeQuery read is not")
        .containsExactly("NativeQueryMover#move");
  }

  @ArchTest
  static void every_listed_writer_still_calls_its_method(JavaClasses classes) {
    assertStillCalls(
        classes, NATIVE_WRITERS, TenantRepository.class, "addUserToTenant", "removeUserFromTenant");
    assertStillCalls(classes, ATTACH_CALLERS, TenantUserService.class, "attachToTenant");
    assertStillCalls(classes, ENTITY_COLLECTION_USERS, User.class, "setTenants", "getTenants");
    assertThat(
            java.util.stream.Stream.of(NATIVE_WRITERS, ATTACH_CALLERS, ENTITY_COLLECTION_USERS)
                .flatMap(List::stream)
                .filter(writer -> writer.tenantSource().isBlank()))
        .as("every writer says where its tenant comes from")
        .isEmpty();
  }

  private static void assertStillCalls(
      JavaClasses classes, List<Writer> writers, Class<?> owner, String... methodNames) {
    Set<String> names = Set.of(methodNames);
    List<String> stale =
        writers.stream()
            .filter(
                writer -> {
                  JavaClass javaClass = classes.get(writer.type());
                  return javaClass.getMethodCallsFromSelf().stream()
                      .noneMatch(
                          call ->
                              call.getTargetOwner().isEquivalentTo(owner)
                                  && names.contains(call.getName()));
                })
            .map(writer -> writer.type().getSimpleName())
            .toList();
    assertThat(stale)
        .as(
            "writers listed for %s#%s that no longer call it: remove them from the list",
            owner.getSimpleName(), names)
        .isEmpty();
  }
}
