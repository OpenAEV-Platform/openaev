package io.openaev.config;

import static org.junit.jupiter.api.Assertions.*;

import io.openaev.database.model.Asset;
import io.openaev.database.model.Document;
import io.openaev.database.model.Endpoint;
import io.openaev.database.model.Group;
import io.openaev.database.model.Setting;
import io.openaev.database.model.Tenant;
import io.openaev.database.model.TenantBase;
import io.openaev.database.model.Token;
import io.openaev.database.model.attackpath.AttackPathGraphVersion;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("TenantTables: the entity model and the gated tables")
class TenantTablesTest {

  @Test
  @DisplayName("classifies strict and dual-scope entities by their @Table name")
  void classifiesByEntityModel() {
    TenantTables tables =
        TenantTables.fromEntities(List.of(Document.class, Group.class, Setting.class));
    assertEquals(TenantTables.Family.STRICT, tables.family("documents"));
    assertEquals(TenantTables.Family.DUAL, tables.family("groups"));
    assertEquals(TenantTables.Family.DUAL, tables.family("parameters"));
  }

  @Test
  @DisplayName("ignores non-tenant entities")
  void ignoresNonTenantEntities() {
    TenantTables tables = TenantTables.fromEntities(List.of(Document.class, Token.class));
    assertEquals(Set.of("documents"), tables.strict());
    assertTrue(tables.dualScope().isEmpty());
  }

  @Test
  @DisplayName("a SINGLE_TABLE subclass resolves to its parent's @Table")
  void resolvesInheritedTable() {
    TenantTables tables = TenantTables.fromEntities(List.of(Asset.class, Endpoint.class));
    assertEquals(TenantTables.Family.STRICT, tables.family("assets"));
  }

  @Test
  @DisplayName("a subclass alone still resolves to the inherited table")
  void subclassAloneResolvesInheritedTable() {
    TenantTables tables = TenantTables.fromEntities(List.of(Endpoint.class));
    assertEquals(TenantTables.Family.STRICT, tables.family("assets"));
  }

  @Test
  @DisplayName("rejects a tenant entity without @Table (fail-closed at startup)")
  void rejectsTenantEntityWithoutTable() {
    assertThrows(
        IllegalStateException.class, () -> TenantTables.fromEntities(List.of(NoTableTenant.class)));
  }

  // --- withoutSelfIsolated: the tables the inspector gates ----------------

  @Test
  @DisplayName("given strict and dual-scope tables, should gate every one, keeping its family")
  void given_strictAndDualScopeTables_should_gateEveryOneKeepingItsFamily() {
    // Arrange
    TenantTables model = new TenantTables(Set.of("documents", "findings"), Set.of("groups"));

    // Act
    TenantTables gated = model.withoutSelfIsolated();

    // Assert
    assertEquals(Set.of("documents", "findings"), gated.strict());
    assertEquals(
        Set.of("groups"),
        gated.dualScope(),
        "a platform row is written under the platform-write marker, which the mechanism covers");
    assertEquals(TenantTables.Family.DUAL, gated.family("groups"));
  }

  /**
   * The table each entity is mapped to, read from the entity itself. Writing the name as a literal
   * here would make the test pass after a rename while the exclusion silently stopped matching,
   * which is the whole protection gone with nothing red.
   */
  private static String tableOf(Class<?> entity) {
    return entity.getAnnotation(jakarta.persistence.Table.class).name();
  }

  @Test
  @DisplayName("given self-isolated strict tables, should leave every one out")
  void given_selfIsolatedStrictTables_should_leaveEveryOneOut() {
    // Arrange
    String graphVersion = tableOf(AttackPathGraphVersion.class);
    String tenants = tableOf(Tenant.class);
    // A join table with no entity class, so its name cannot be read from a mapping.
    String usersTenants = "users_tenants";
    TenantTables model =
        new TenantTables(
            Set.of("documents", graphVersion, tenants, usersTenants), Set.of("groups"));

    // Act
    TenantTables gated = model.withoutSelfIsolated();

    // Assert
    assertEquals(Set.of("documents"), gated.strict());
    assertEquals(
        TenantTables.Family.NONE,
        gated.family(graphVersion),
        "its upsert is a shape the inspector cannot rewrite; it isolates itself by explicit predicate");
    assertEquals(
        TenantTables.Family.NONE,
        gated.family(tenants),
        "the tenant registry is not tenant-scoped data; gating it stops the platform from bootstrapping");
    assertEquals(
        TenantTables.Family.NONE,
        gated.family(usersTenants),
        "the membership check that computes the scope reads it before any scope exists");
  }

  @Test
  @DisplayName("given a self-isolated table read as dual-scope, should leave it out")
  void given_selfIsolatedTableReadAsDualScope_should_leaveItOut() {
    // Arrange: the schema reads a table as dual-scope when its tenant_id is nullable
    TenantTables model =
        new TenantTables(
            Set.of("documents"), Set.of("groups", tableOf(Tenant.class), "users_tenants"));

    // Act
    TenantTables gated = model.withoutSelfIsolated();

    // Assert
    assertEquals(Set.of("groups"), gated.dualScope());
    assertEquals(TenantTables.Family.NONE, gated.family(tableOf(Tenant.class)));
    assertEquals(TenantTables.Family.NONE, gated.family("users_tenants"));
  }

  @Test
  @DisplayName(
      "the self-isolated set is the tenant registry, the membership mapping and the"
          + " self-predicated graph version")
  void selfIsolatedTablesAreExposedForDerivation() {
    assertEquals(
        Set.of(tableOf(AttackPathGraphVersion.class), tableOf(Tenant.class), "users_tenants"),
        TenantTables.selfIsolatedTables());
  }

  /** A tenant-aware entity missing its {@code @Table} mapping. */
  static final class NoTableTenant implements TenantBase {
    @Override
    public String getId() {
      return null;
    }

    @Override
    public void setId(String id) {}

    @Override
    public Tenant getTenant() {
      return null;
    }

    @Override
    public void setTenant(Tenant tenant) {}
  }
}
