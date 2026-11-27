package io.openaev.architecture;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaAccess;
import com.tngtech.archunit.core.domain.JavaCodeUnit;
import com.tngtech.archunit.core.domain.JavaCodeUnitAccess;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.core.domain.JavaMethodReference;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import io.openaev.aop.PlatformWrite;
import io.openaev.context.TenantScopedTransaction;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Group;
import io.openaev.database.model.Role;
import io.openaev.database.model.Setting;
import io.openaev.database.model.Tenant;
import io.openaev.database.model.Theme;
import io.openaev.database.repository.GroupRepository;
import io.openaev.database.repository.RoleRepository;
import io.openaev.database.repository.SettingRepository;
import jakarta.persistence.EntityManager;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

/**
 * What the guards on a dual-scope table check, and how. A platform row ({@code tenant_id IS NULL})
 * is readable under any scope but written only inside a platform-write transaction; a tenant row
 * read with no scope comes back empty. So the guards follow the WRITES of a row of the table and
 * the READS of one: every path that reaches one must cross a marker. Each guarded table is a {@link
 * DualScopeTable}: {@link #PARAMETERS} (entity {@code Setting}) and {@link #ROLES} (entity {@code
 * Role}). The list below is written for {@code parameters}; for another table read its entity,
 * repository and table names in place of {@code Setting}, {@code SettingRepository} and {@code
 * parameters}.
 *
 * <p>A writer is a method that makes one of these calls or refers to one as a method reference
 * ({@code .ifPresent(settingRepository::delete)}, {@code forEach(settingRepository::save)}), and a
 * path is followed from there to its callers, services included ({@code SettingService}, {@code
 * PlatformSettingsService}, {@code TenantSettingsService} are writers because they make such a
 * call, so what reaches them is checked):
 *
 * <ul>
 *   <li>a modifying method of {@link SettingRepository}: a {@code @Modifying} query, or a name
 *       starting {@code save}, {@code delete}, {@code remove}, {@code update}, {@code insert},
 *       {@code upsert}, which takes in every inherited {@code CrudRepository} write, {@code
 *       saveAll} included;
 *   <li>a {@code @Modifying} method of any other repository whose {@code @Query} is a write
 *       statement naming {@code parameters} or {@code Setting};
 *   <li>a setter of a {@code Setting} called from outside the entity: a managed row modified and
 *       then flushed by dirty checking, with no explicit save, is written by the setter;
 *   <li>in a method that names {@code Setting} in its signature or touches one: an {@code
 *       EntityManager} {@code persist}/{@code merge}/{@code remove}, a Hibernate {@code Session}
 *       write, or a query {@code executeUpdate};
 *   <li>in a method that does either of the above, or whose string constants hold a write statement
 *       naming the table ({@code update|delete from|insert into|merge into} followed by {@code
 *       parameters} or {@code Setting}): an {@code executeUpdate}, a {@code JdbcTemplate} / {@code
 *       NamedParameterJdbcTemplate} {@code update}, {@code batchUpdate}, {@code execute}, or a JDBC
 *       {@code Statement} execution.
 * </ul>
 *
 * <p>A reader is any repository {@code find*}/{@code exists*}/{@code count*} not named for the
 * platform row, called or referred to as a method reference.
 *
 * <p>What the rule does not cover: Flyway migrations ({@code io.openaev.migration}), which write
 * {@code parameters} by raw SQL at boot, before any request, and are reviewed as their own class.
 *
 * <p>What the rule cannot see: a statement assembled at run time (the table name arrives in a
 * variable), a {@code Setting} passed as {@code Object} or a generic to a method that does not name
 * the type, a write through reflection or a bean copier, a write made by code outside the imported
 * classes (a migration, manual SQL), and a write reached only through an interface call or a Spring
 * event dispatch (see the denominator below). The database refuses the write at run time, outside a
 * platform-write transaction: the platform write refusal ({@code require_tenant_write}) raises on
 * an HQL, a native statement and a dirty-checked row, and a row trigger on {@code parameters}
 * applies it to a {@code JdbcTemplate} or JDBC statement of the same transaction, whatever SQL text
 * it assembles ({@code PlatformSettingsUnscopedAccessTest}). The trigger covers platform rows only,
 * so this rule also follows {@code JdbcTemplate} and {@code Statement} calls.
 *
 * <p>A call is under a marker when the transaction it runs in is marked at that point:
 *
 * <ul>
 *   <li>the calling method is one the aspect honours: {@code @PlatformWrite} (platform write) or
 *       {@code @Transactional} (scope), on a method taking a {@code TxCtx};
 *   <li>the calling method calls the current-transaction setter ({@code
 *       setPlatformWriteOnCurrentTransaction}, {@code setScopeOnCurrentTransaction}) on a line
 *       before the call;
 *   <li>the call is made only from a lambda of the calling method, and the calling method itself
 *       calls the primitive that opens a marked transaction around a callback. A method that opens
 *       such a transaction and then calls directly, after the primitive returned, is NOT marked:
 *       only the callback is. ArchUnit folds a lambda's calls into its creator, so {@link
 *       CallSites} reads the class file to tell the two apart.
 * </ul>
 *
 * <p>Denominator and limits: the callers ArchUnit resolves in the imported classes. A call through
 * an interface, a reflective call and a Spring event dispatch are not followed, so a method reached
 * only that way has no caller and is reported unless it is itself marked (fails closed). A lambda
 * or a method reference is tied to the primitive by its creator, not by the argument the primitive
 * receives: a method that creates two of them and hands only one to the primitive marks both.
 */
final class PlatformRowWriters {

  /**
   * A method that writes a tenant row of the table only, with the reason. The method is named with
   * its parameter types: an overload of the same name is not exempted by it.
   */
  record TenantRowWriter(
      Class<?> owner, String method, List<Class<?>> parameterTypes, String reason) {}

  /**
   * The writers that never touch a platform row: each one attributes the row to a tenant it names,
   * and the database checks that tenant against the transaction's scope. A write of a tenant row
   * does not need the marker. Each entry must stay a live writer that touches tenant rows only
   * ({@link #tenantRowOnlyViolations}, checked by {@code TenantActiveTableAccessArchTest}).
   */
  private static final List<TenantRowWriter> PARAMETERS_TENANT_ROW_WRITERS =
      List.of(
          new TenantRowWriter(
              io.openaev.service.settings.TenantSettingsService.class,
              "clearSettingIfMatch",
              List.of(String.class, String.class, String.class),
              "clears a setting looked up by (key, tenantId): a tenant row"),
          new TenantRowWriter(
              io.openaev.service.settings.TenantSettingsService.class,
              "upsert",
              List.of(Tenant.class, String.class, String.class),
              "creates or updates the tenant's own setting, the new row carries the tenant"),
          new TenantRowWriter(
              io.openaev.service.autonomous.AutonomousRunService.class,
              "updateDefaultAdditionalAgentIds",
              List.of(TxCtx.class, List.class),
              "writes the tenant's default agents, the tenant is the one the scope pins"),
          new TenantRowWriter(
              io.openaev.service.autonomous.AutonomousRunService.class,
              "updateDefaultAdditionalAgentModes",
              List.of(TxCtx.class, java.util.Map.class),
              "writes the tenant's default agent modes, the tenant is the one the scope pins"),
          new TenantRowWriter(
              io.openaev.service.autonomous.AutonomousRunService.class,
              "newTenantSetting",
              List.of(String.class, String.class, String.class),
              "builds the tenant's own new row, attributed to the tenant it names; the write is"
                  + " the save of the default agents writers above"),
          new TenantRowWriter(
              io.openaev.service.settings.TenantSettingsService.class,
              "upsertThemeKey",
              List.of(Tenant.class, String.class, Theme.THEME_KEYS.class, String.class),
              "deletes the setting looked up by (key, tenantId) through a method reference: a tenant"
                  + " row"),
          new TenantRowWriter(
              io.openaev.processor.datapack.V20260101_Starter_pack.class,
              "setDefaultDashboard",
              List.of(Tenant.class, String.class, String.class),
              "sets the default dashboard of the tenant the starter pack is installed for"));

  /**
   * A root of a path to a tenant-row read that sets no scope itself, because the scope is set by a
   * caller ArchUnit cannot follow (a call through an abstract method). Each entry names the caller.
   */
  record ReviewedRoot(Class<?> owner, String method, String reason) {}

  private static final List<ReviewedRoot> PARAMETERS_REVIEWED_UNSCOPED_ROOTS =
      List.of(
          new ReviewedRoot(
              io.openaev.processor.datapack.V20260101_Starter_pack.class,
              "doProcess",
              "called through DataPack#process by MigrationProcessor#init, which joins the"
                  + " onboarding transaction with setScopeOnCurrentTransaction or runs each pack in"
                  + " tenantTx.execute of the tenant's own scope"));

  /** The writers of {@code roles} that never touch a platform role, each with its reason. */
  private static final List<TenantRowWriter> ROLES_TENANT_ROW_WRITERS =
      List.of(
          new TenantRowWriter(
              io.openaev.service.TenantRoleService.class,
              "createRoleInternal",
              List.of(String.class, String.class, String.class, Set.class, String.class),
              "builds the role in the tenant it names (setTenant), a tenant row"),
          new TenantRowWriter(
              io.openaev.service.TenantRoleService.class,
              "updateRoleInternal",
              List.of(String.class, String.class, String.class, Set.class, String.class),
              "updates the role looked up by (id, tenantId) through findByIdAndTenant: a tenant row"),
          new TenantRowWriter(
              io.openaev.service.TenantRoleService.class,
              "delete",
              List.of(String.class, String.class),
              "deletes the role looked up by id among the tenant it names: a tenant row"),
          new TenantRowWriter(
              io.openaev.processor.core
                  .V20260811_Add_phishing_reporting_capabilities_to_default_roles.class,
              "doMigrate",
              List.of(Tenant.class),
              "rewrites the capabilities of the roles of the tenant it migrates (findAllByTenantId)"),
          new TenantRowWriter(
              io.openaev.processor.datapack.V20261001_Default_tenant_roles_and_groups.class,
              "alignCapabilitiesWithPreset",
              List.of(Role.class, String.class),
              "rewrites a role that doProcess read with findAllByTenantId: a tenant row"));

  /**
   * A dual-scope table under guard: its entity, its repository, the names a write statement uses
   * for it, the repository calls a writer of tenant rows may make (each keyed by a tenant, or a
   * save or delete of a row it holds), the writers exempted as tenant-row-only, and the roots of
   * reads of tenant rows whose scope is set by a caller ArchUnit cannot follow.
   */
  record DualScopeTable(
      String name,
      Class<?> entity,
      Class<?> repository,
      java.util.regex.Pattern writeStatement,
      Set<String> tenantRowRepositoryCalls,
      List<TenantRowWriter> tenantRowWriters,
      List<ReviewedRoot> reviewedUnscopedRoots) {}

  static final DualScopeTable PARAMETERS =
      new DualScopeTable(
          "parameters",
          Setting.class,
          SettingRepository.class,
          writeStatementOn("parameters", "Setting"),
          Set.of("save", "delete", "findByKeyAndTenantId", "findAllByTenantId"),
          PARAMETERS_TENANT_ROW_WRITERS,
          PARAMETERS_REVIEWED_UNSCOPED_ROOTS);

  /**
   * {@code roles} and its capabilities: a platform role's {@code roles_capabilities} rows are
   * written only under the platform-write marker too (a row trigger refuses them otherwise), so a
   * statement naming that table is a write of a role. {@code groups_roles} is not: its rule is that
   * a role and a group share a tenant, not the marker.
   */
  static final DualScopeTable ROLES =
      new DualScopeTable(
          "roles",
          Role.class,
          RoleRepository.class,
          writeStatementOn("roles", "roles_capabilities", "Role"),
          Set.of(
              "save",
              "delete",
              "findByIdAndTenantId",
              "findAllByTenantId",
              "findByIdAndTenantIdIn",
              "findAllByIdInAndTenantId",
              "findAllByTenantIdIn",
              "findByTenantIdAndName"),
          ROLES_TENANT_ROW_WRITERS,
          List.of());

  /** The writers of {@code groups} that never touch a platform group, each with its reason. */
  private static final List<TenantRowWriter> GROUPS_TENANT_ROW_WRITERS =
      List.of(
          new TenantRowWriter(
              io.openaev.service.TenantGroupService.class,
              "createGroup",
              List.of(String.class, io.openaev.api.groups.dto.TenantGroupCreateInput.class),
              "saves the group built by createGroupInner, in the tenant it names: a tenant row"),
          new TenantRowWriter(
              io.openaev.service.TenantGroupService.class,
              "createInternalGroupWithRole",
              List.of(
                  String.class,
                  String.class,
                  io.openaev.api.groups.dto.TenantGroupCreateInput.class,
                  List.class),
              "saves the group built by createGroupInner, in the tenant it names: a tenant row"),
          new TenantRowWriter(
              io.openaev.service.TenantGroupService.class,
              "createGroup",
              List.of(String.class, String.class, String.class, List.class),
              "builds the group in the tenant it names (setTenant): a tenant row"),
          new TenantRowWriter(
              io.openaev.service.TenantGroupService.class,
              "createGroupInner",
              List.of(
                  String.class,
                  String.class,
                  io.openaev.api.groups.dto.TenantGroupCreateInput.class),
              "builds the group in the tenant it names (setTenant): a tenant row"),
          new TenantRowWriter(
              io.openaev.service.TenantGroupService.class,
              "updateGroup",
              List.of(
                  String.class,
                  String.class,
                  io.openaev.api.groups.dto.TenantGroupCreateInput.class),
              "updates the group looked up by (id, tenantId) through findByIdAndTenantForWrite: a"
                  + " tenant row"),
          new TenantRowWriter(
              io.openaev.service.TenantGroupService.class,
              "updateGroupRoles",
              List.of(
                  String.class,
                  String.class,
                  io.openaev.rest.group.form.GroupUpdateRolesInput.class),
              "updates the group looked up by (id, tenantId) through findByIdAndTenantForWrite: a"
                  + " tenant row"),
          new TenantRowWriter(
              io.openaev.service.TenantGroupService.class,
              "updateInternalGroupWithRoles",
              List.of(
                  Group.class, io.openaev.api.groups.dto.TenantGroupCreateInput.class, List.class),
              "saves the group it is given, already a tenant row: the caller holds it"),
          new TenantRowWriter(
              io.openaev.service.TenantGroupService.class,
              "updateGroupUsers",
              List.of(
                  String.class,
                  String.class,
                  io.openaev.rest.group.form.GroupUpdateUsersInput.class),
              "updates the group looked up by (id, tenantId) through findByIdAndTenantForWrite: a"
                  + " tenant row"),
          new TenantRowWriter(
              io.openaev.service.TenantGroupService.class,
              "updateGroupMarkings",
              List.of(
                  String.class,
                  String.class,
                  io.openaev.api.groups.dto.GroupUpdateMarkingsInput.class),
              "updates the group looked up by (id, tenantId) through findByIdAndTenantForWrite: a"
                  + " tenant row"),
          new TenantRowWriter(
              io.openaev.service.TenantGroupService.class,
              "updateAutoNewMarking",
              List.of(String.class, Group.class, Boolean.class),
              "saves the group it is given, already a tenant row: the caller holds it"),
          new TenantRowWriter(
              io.openaev.service.TenantGroupService.class,
              "receiveEveryMarking",
              List.of(Group.class, List.class),
              "saves the group it is given, already a tenant row: the caller holds it"),
          new TenantRowWriter(
              io.openaev.service.TenantGroupService.class,
              "grantMissingMarkings",
              List.of(Group.class, java.util.Collection.class),
              "saves the group it is given, already a tenant row: the caller holds it"),
          new TenantRowWriter(
              io.openaev.service.TenantGroupService.class,
              "stopReceivingNewMarkings",
              List.of(Group.class),
              "saves the group it is given, already a tenant row: its one caller,"
                  + " XtmPeerPrivilegeService.issueToken, passes the default tenant's peer group"),
          new TenantRowWriter(
              io.openaev.service.TenantGroupService.class,
              "delete",
              List.of(String.class, String.class),
              "deletes the group looked up by (id, tenantId) through findByIdAndTenantForWrite: a"
                  + " tenant row"),
          new TenantRowWriter(
              io.openaev.service.TenantGroupService.class,
              "addGrant",
              List.of(String.class, String.class, io.openaev.rest.group.form.GroupGrantInput.class),
              "saves the group looked up by (id, tenantId) through findByIdAndTenantForWrite: a"
                  + " tenant row"),
          new TenantRowWriter(
              io.openaev.service.TenantGroupService.class,
              "removeGrant",
              List.of(String.class, String.class, String.class),
              "saves the group looked up by (id, tenantId) through findByIdAndTenantForWrite: a"
                  + " tenant row"),
          new TenantRowWriter(
              io.openaev.opencti.connectors.service.LegacyOpenCTIConnectorMigration.class,
              "deleteLegacyConnector",
              List.of(String.class),
              "deletes the legacy pre-multi-tenant connector group by its well-known id, which lives"
                  + " in the default tenant (DEFAULT_TENANT_UUID), inside its own default-tenant"
                  + " transaction: a tenant row"),
          new TenantRowWriter(
              io.openaev.processor.datapack.V20260330_Default_tenant_data.class,
              "doProcess",
              List.of(Tenant.class),
              "builds the group in the tenant it processes (setTenant): a tenant row"),
          new TenantRowWriter(
              io.openaev.processor.datapack.V20261002_Default_group_markings.class,
              "assignMarkingToGroup",
              List.of(Tenant.class, String.class, String.class),
              "updates the group looked up by (name, tenantId) through findByNameAndTenantId: a"
                  + " tenant row"));

  /**
   * {@code groups} is dual-scope like {@code roles}: a platform group ({@code tenant_id IS NULL})
   * is readable under any scope and written only inside a platform-write transaction. Every writer
   * this rule can see is marked or listed as a writer of tenant groups only.
   */
  static final DualScopeTable GROUPS =
      new DualScopeTable(
          "groups",
          Group.class,
          GroupRepository.class,
          writeStatementOn("groups", "Group"),
          Set.of("save", "delete", "deleteById", "existsById", "findByNameAndTenantId"),
          GROUPS_TENANT_ROW_WRITERS,
          List.of());

  /**
   * What marks a transaction: a method the aspect honours, a call on the current transaction, and
   * the primitives that open a marked transaction around a callback.
   */
  record Marker(
      Predicate<JavaMethod> honouredAnnotation,
      Set<String> currentTransactionCalls,
      Set<String> callbackPrimitives,
      String description) {}

  static final Marker PLATFORM_WRITE =
      new Marker(
          PlatformRowWriters::honoursPlatformWrite,
          Set.of("setPlatformWriteOnCurrentTransaction"),
          Set.of("executeAsPlatformWrite", "executeNewAsPlatformWrite"),
          "platform-write marker");

  /** The scope of a transaction: the aspect writes it from a TxCtx argument, the primitive too. */
  static final Marker TENANT_SCOPE =
      new Marker(
          PlatformRowWriters::hasTransactionalWithTxCtx,
          Set.of("setScopeOnCurrentTransaction"),
          Set.of(
              "execute",
              "executeReadOnly",
              "executeNew",
              "executeAsPlatformWrite",
              "executeNewAsPlatformWrite",
              "forEachTenant"),
          "tenant scope");

  /** A call a unit makes toward the code under guard: its target and its line. */
  private record Edge(CallSites.Target target, int line) {}

  private static final List<String> WRITE_PREFIXES =
      List.of("save", "delete", "remove", "update", "insert", "upsert");

  private static final Set<String> ENTITY_MANAGER_WRITES = Set.of("persist", "merge", "remove");

  private static final Set<String> SESSION_WRITES =
      Set.of(
          "save",
          "saveOrUpdate",
          "persist",
          "merge",
          "remove",
          "delete",
          "update",
          "replicate",
          "executeUpdate");

  /** Statement executors that take SQL text: the table is only visible in a string constant. */
  private static final Set<String> JDBC_WRITES =
      Set.of(
          "update",
          "batchUpdate",
          "execute",
          "executeUpdate",
          "executeBatch",
          "executeLargeUpdate");

  /** A write statement ({@code update}, {@code delete from}, ...) on one of {@code names}. */
  private static java.util.regex.Pattern writeStatementOn(String... names) {
    return java.util.regex.Pattern.compile(
        "(?is)\\b(update|delete\\s+from|insert\\s+into|merge\\s+into)\\s+(only\\s+)?\"?("
            + String.join("|", names)
            + ")\"?\\b");
  }

  private static final Set<String> CALLBACK_TYPES =
      Set.of(
          "java.lang.Runnable",
          "java.util.function.Supplier",
          "java.util.function.Consumer",
          "java.util.function.Function",
          "java.util.concurrent.Callable");

  private static final Set<String> TRANSACTIONAL =
      Set.of(
          "org.springframework.transaction.annotation.Transactional",
          "jakarta.transaction.Transactional");

  private PlatformRowWriters() {}

  /** Methods that modify a row of {@code table}, outside {@code tenantRowWriters}. */
  static DescribedPredicate<JavaMethod> writeARow(
      DualScopeTable table, List<TenantRowWriter> tenantRowWriters) {
    return new DescribedPredicate<>("write a row of " + table.name()) {
      @Override
      public boolean test(JavaMethod method) {
        return !isBridge(method)
            && !isMigration(method)
            && !isTenantRowWriter(method, tenantRowWriters)
            && !writesOf(table, method).isEmpty();
      }
    };
  }

  /** Methods that read a tenant row of {@code table}. */
  static DescribedPredicate<JavaMethod> readATenantRow(DualScopeTable table) {
    return new DescribedPredicate<>("read a tenant row of " + table.name()) {
      @Override
      public boolean test(JavaMethod method) {
        return !isBridge(method) && !readsOf(table, method).isEmpty();
      }
    };
  }

  /**
   * A Flyway migration runs once at boot on the migration connection, before any request, and
   * writes the table by raw SQL: it is reviewed as its own class, not by this guard.
   */
  private static boolean isMigration(JavaMethod method) {
    return method.getOwner().getPackageName().startsWith("io.openaev.migration");
  }

  /** The compiler's bridge to a covariant override repeats the call of the real method. */
  private static boolean isBridge(JavaMethod method) {
    return method.getModifiers().contains(JavaModifier.BRIDGE);
  }

  /** Whether {@code unit} is one of the listed tenant-row-only writers. */
  static boolean isTenantRowWriter(JavaCodeUnit unit, List<TenantRowWriter> tenantRowWriters) {
    List<String> parameters = unit.getRawParameterTypes().stream().map(t -> t.getName()).toList();
    return tenantRowWriters.stream()
        .anyMatch(
            e ->
                unit.getOwner().isAssignableTo(e.owner())
                    && e.method().equals(unit.getName())
                    && e.parameterTypes().stream().map(Class::getName).toList().equals(parameters));
  }

  /**
   * What takes an exempted writer out of the tenant rows. It may call the table's repository only
   * through {@link DualScopeTable#tenantRowRepositoryCalls}, which never reach a platform row (a
   * lookup by tenant, a save or delete of the row it holds), and every row it builds must be given
   * a tenant. Empty when the method stays a tenant-row writer. A row handed in as an argument is
   * not checked here; the caller is, and the database refuses a platform row written outside a
   * platform-write transaction.
   */
  static List<String> tenantRowOnlyViolations(DualScopeTable table, JavaCodeUnit unit) {
    List<String> violations = new ArrayList<>();
    for (JavaCodeUnitAccess<?> call : callsAndReferences(unit)) {
      if (onTheRepository(table, unit, call)
          && !table.tenantRowRepositoryCalls().contains(call.getName())) {
        violations.add(
            unit.getFullName()
                + " calls "
                + table.repository().getSimpleName()
                + "."
                + call.getName()
                + ", which is not keyed by a tenant");
      }
    }
    boolean buildsARow =
        unit.getConstructorCallsFromSelf().stream()
            .anyMatch(c -> c.getTargetOwner().isAssignableTo(table.entity()));
    boolean givesItATenant =
        unit.getMethodCallsFromSelf().stream()
            .anyMatch(
                c ->
                    c.getTargetOwner().isAssignableTo(table.entity())
                        && c.getName().equals("setTenant"));
    if (buildsARow && !givesItATenant) {
      violations.add(
          unit.getFullName()
              + " builds a "
              + table.entity().getSimpleName()
              + " and never calls setTenant: that is a platform row");
    }
    return violations;
  }

  private static boolean isReviewedRoot(DualScopeTable table, JavaCodeUnit unit) {
    return table.reviewedUnscopedRoots().stream()
        .anyMatch(
            r -> unit.getOwner().isAssignableTo(r.owner()) && r.method().equals(unit.getName()));
  }

  /** True when the method modifies a row of {@code table}, ignoring any exemption. */
  static boolean writesARow(DualScopeTable table, JavaMethod method) {
    return !writesOf(table, method).isEmpty();
  }

  /**
   * What a unit calls or refers to. ArchUnit keeps a method reference ({@code repo::delete}) apart
   * from a call, and the idiom {@code .ifPresent(settingRepository::delete)} is a write that no
   * call lists, so a reference is followed exactly like a call. A constructor reference is read
   * too: none of the shapes below writes through one, so it never makes a writer.
   */
  private static List<JavaCodeUnitAccess<?>> callsAndReferences(JavaCodeUnit unit) {
    List<JavaCodeUnitAccess<?>> accesses = new ArrayList<>(unit.getMethodCallsFromSelf());
    accesses.addAll(unit.getMethodReferencesFromSelf());
    accesses.addAll(unit.getConstructorReferencesFromSelf());
    return accesses;
  }

  private static List<JavaCodeUnitAccess<?>> writesOf(DualScopeTable table, JavaCodeUnit unit) {
    boolean mentionsTheEntity = mentionsTheEntity(table, unit);
    boolean namesTheTable = namesTheTableInAWriteStatement(table, unit);
    return callsAndReferences(unit).stream()
        .filter(call -> writes(table, unit, call, mentionsTheEntity, namesTheTable))
        .toList();
  }

  private static boolean writes(
      DualScopeTable table,
      JavaCodeUnit unit,
      JavaCodeUnitAccess<?> call,
      boolean mentionsTheEntity,
      boolean namesTheTable) {
    var target = call.getTargetOwner();
    if (onTheRepository(table, unit, call) && modifies(call)) {
      return true;
    }
    if (modifiesTheTableByQuery(table, call)) {
      return true;
    }
    // Dirty checking: a managed row changed by a setter is flushed with no save call.
    if (target.isAssignableTo(table.entity())
        && call.getName().startsWith("set")
        && !unit.getOwner().isAssignableTo(table.entity())) {
      return true;
    }
    if (mentionsTheEntity
        && ((target.isAssignableTo(EntityManager.class)
                && ENTITY_MANAGER_WRITES.contains(call.getName()))
            || (target.isAssignableTo("org.hibernate.Session")
                && SESSION_WRITES.contains(call.getName())))) {
      return true;
    }
    boolean executesAQuery =
        target.isAssignableTo("jakarta.persistence.Query")
            || target.isAssignableTo("org.hibernate.query.MutationQuery");
    if (executesAQuery && call.getName().equals("executeUpdate")) {
      return mentionsTheEntity || namesTheTable;
    }
    boolean executesSql =
        target.isAssignableTo("org.springframework.jdbc.core.JdbcOperations")
            || target.isAssignableTo(
                "org.springframework.jdbc.core.namedparam.NamedParameterJdbcOperations")
            || target.isAssignableTo(
                "org.springframework.jdbc.core.simple.JdbcClient$StatementSpec")
            || target.isAssignableTo("java.sql.Statement");
    return executesSql && JDBC_WRITES.contains(call.getName()) && namesTheTable;
  }

  /**
   * Whether the call, or the method reference, is on the table's repository. A reference carries
   * the type that declares the method ({@code CrudRepository} for {@code
   * settingRepository::delete}), which is no more a {@code SettingRepository} than any other
   * repository, so the type it is taken on is read from the class file.
   */
  private static boolean onTheRepository(
      DualScopeTable table, JavaCodeUnit unit, JavaCodeUnitAccess<?> access) {
    if (access.getTargetOwner().isAssignableTo(table.repository())) {
      return true;
    }
    return access instanceof JavaMethodReference
        && CallSites.receiversOf(
                unit.getOwner(),
                unit.getName(),
                new CallSites.Target(access.getTargetOwner().getName(), access.getName()))
            .contains(table.repository().getName());
  }

  /** A {@code @Modifying} method of another repository whose query writes the table. */
  private static boolean modifiesTheTableByQuery(DualScopeTable table, JavaCodeUnitAccess<?> call) {
    return call.getTarget().resolveMember().stream()
        .filter(member -> member.isAnnotatedWith(Modifying.class))
        .flatMap(member -> member.tryGetAnnotationOfType(Query.class).stream())
        .anyMatch(query -> isWriteOnTheTable(table, query.value()));
  }

  /** Whether {@code text} is a statement that writes the table or its entity. */
  static boolean isWriteOnTheTable(DualScopeTable table, String text) {
    return table.writeStatement().matcher(text).find();
  }

  private static boolean namesTheTableInAWriteStatement(DualScopeTable table, JavaCodeUnit unit) {
    return CallSites.stringConstants(unit.getOwner(), unit.getName()).stream()
        .anyMatch(text -> isWriteOnTheTable(table, text));
  }

  private static List<JavaCodeUnitAccess<?>> readsOf(DualScopeTable table, JavaCodeUnit unit) {
    return callsAndReferences(unit).stream()
        .filter(call -> onTheRepository(table, unit, call) && isTenantRowRead(call.getName()))
        .toList();
  }

  /** Every find, exists or count, but the ones named for the platform row. */
  private static boolean isTenantRowRead(String name) {
    boolean read = name.startsWith("find") || name.startsWith("exists") || name.startsWith("count");
    return read && !name.contains("TenantIsNull");
  }

  private static boolean modifies(JavaCodeUnitAccess<?> call) {
    return WRITE_PREFIXES.stream().anyMatch(call.getName()::startsWith)
        || call.getTarget().resolveMember().stream()
            .anyMatch(member -> member.isAnnotatedWith(Modifying.class));
  }

  private static boolean mentionsTheEntity(DualScopeTable table, JavaCodeUnit unit) {
    return unit.getRawParameterTypes().stream().anyMatch(t -> t.isAssignableTo(table.entity()))
        || unit.getAccessesFromSelf().stream()
            .anyMatch(a -> a.getTargetOwner().isAssignableTo(table.entity()));
  }

  /** Every path that reaches a platform-row write crosses a platform-write marker. */
  static ArchCondition<JavaMethod> beReachedOnlyUnderPlatformWrite(DualScopeTable table) {
    return beReachedOnlyUnder(
        table,
        PLATFORM_WRITE,
        unit -> writesOf(table, unit),
        "writes a platform row of " + table.name());
  }

  /** Every path to a reader crosses a tenant scope: an unscoped tenant row comes back empty. */
  static ArchCondition<JavaMethod> beReachedOnlyUnderATenantScope(DualScopeTable table) {
    return beReachedOnlyUnder(
        table, TENANT_SCOPE, unit -> readsOf(table, unit), "reads a tenant row of " + table.name());
  }

  private static ArchCondition<JavaMethod> beReachedOnlyUnder(
      DualScopeTable table,
      Marker marker,
      Function<JavaCodeUnit, List<JavaCodeUnitAccess<?>>> calls,
      String what) {
    return new ArchCondition<>("be reached only under the " + marker.description()) {
      @Override
      public void check(JavaMethod method, ConditionEvents events) {
        Set<JavaCodeUnit> unmarkedRoots = new HashSet<>();
        Set<String> visited = new HashSet<>();
        for (JavaCodeUnitAccess<?> call : calls.apply(method)) {
          Edge edge =
              new Edge(
                  new CallSites.Target(call.getTargetOwner().getName(), call.getName()),
                  call.getLineNumber());
          collectUnmarkedRoots(method, edge, marker, visited, unmarkedRoots);
        }
        for (JavaCodeUnit root : unmarkedRoots) {
          if (marker == TENANT_SCOPE && isReviewedRoot(table, root)) {
            continue;
          }
          events.add(
              SimpleConditionEvent.violated(
                  method,
                  method.getFullName()
                      + " "
                      + what
                      + " and is reached from "
                      + root.getFullName()
                      + ", which is not under the "
                      + marker.description()));
        }
      }
    };
  }

  /**
   * Walks to the callers of {@code unit}; {@code edge} is the call {@code unit} makes toward the
   * guarded code, which is what a marker is compared against.
   */
  private static void collectUnmarkedRoots(
      JavaCodeUnit unit,
      Edge edge,
      Marker marker,
      Set<String> visited,
      Set<JavaCodeUnit> unmarkedRoots) {
    // The line is part of the key: two calls to one target, one before and one after a marker,
    // are two different questions.
    if (!visited.add(unit.getFullName() + "->" + edge) || isMarked(unit, edge, marker)) {
      return;
    }
    Set<JavaAccess<?>> callers = new HashSet<>(unit.getAccessesToSelf());
    if (callers.isEmpty()) {
      unmarkedRoots.add(unit);
      return;
    }
    for (JavaAccess<?> access : callers) {
      Edge toUnit =
          new Edge(
              new CallSites.Target(access.getTargetOwner().getName(), access.getName()),
              access.getLineNumber());
      collectUnmarkedRoots(access.getOrigin(), toUnit, marker, visited, unmarkedRoots);
    }
  }

  private static boolean isMarked(JavaCodeUnit unit, Edge edge, Marker marker) {
    if (unit instanceof JavaMethod method && marker.honouredAnnotation().test(method)) {
      return true;
    }
    if (edge.line() > 0
        && unit.getMethodCallsFromSelf().stream()
            .anyMatch(
                call ->
                    call.getTargetOwner().isAssignableTo(TenantScopedTransaction.class)
                        && marker.currentTransactionCalls().contains(call.getName())
                        && call.getLineNumber() > 0
                        && call.getLineNumber() < edge.line())) {
      return true;
    }
    // Inside a callback handed to a primitive that opens the marked transaction: the call is made
    // only from a lambda of the method, and the method itself calls such a primitive, or a helper
    // that runs its Runnable/Supplier parameter inside one.
    return CallSites.onlyInsideLambdas(unit.getOwner(), unit.getName(), edge.target())
        && opensCallbackTransaction(unit, marker, 0);
  }

  private static boolean opensCallbackTransaction(JavaCodeUnit unit, Marker marker, int depth) {
    for (JavaMethodCall call : unit.getMethodCallsFromSelf()) {
      CallSites.Target target =
          new CallSites.Target(call.getTargetOwner().getName(), call.getName());
      if (!CallSites.directlyCalls(unit.getOwner(), unit.getName(), target)) {
        continue;
      }
      if (call.getTargetOwner().isAssignableTo(TenantScopedTransaction.class)
          && marker.callbackPrimitives().contains(call.getName())) {
        return true;
      }
      if (depth < 3
          && call.getTarget().resolveMember().stream()
              .anyMatch(
                  helper ->
                      takesACallback(helper)
                          && opensCallbackTransaction(helper, marker, depth + 1))) {
        return true;
      }
    }
    return false;
  }

  private static boolean takesACallback(JavaCodeUnit unit) {
    return unit.getRawParameterTypes().stream().anyMatch(t -> CALLBACK_TYPES.contains(t.getName()));
  }

  /**
   * The aspect sets the marker only on a method directly annotated {@code @Transactional} that
   * takes a {@code TxCtx}; on any other method {@link PlatformWrite} does nothing.
   */
  static boolean honoursPlatformWrite(JavaMethod method) {
    return method.isAnnotatedWith(PlatformWrite.class) && hasTransactionalWithTxCtx(method);
  }

  /** The aspect writes the scope only for a directly annotated {@code @Transactional} method. */
  static boolean hasTransactionalWithTxCtx(JavaMethod method) {
    return TRANSACTIONAL.stream().anyMatch(method::isAnnotatedWith)
        && method.getRawParameterTypes().stream().anyMatch(t -> t.isAssignableTo(TxCtx.class));
  }

  /** {@code @PlatformWrite} on a method the aspect would ignore. */
  static ArchCondition<JavaMethod> beHonouredByTheAspect() {
    return new ArchCondition<>("be a @Transactional method with a TxCtx parameter") {
      @Override
      public void check(JavaMethod method, ConditionEvents events) {
        if (!honoursPlatformWrite(method)) {
          events.add(
              SimpleConditionEvent.violated(
                  method,
                  method.getFullName()
                      + " carries @PlatformWrite but is not a @Transactional method with a TxCtx"
                      + " parameter: the aspect never sets the marker for it"));
        }
      }
    };
  }
}
