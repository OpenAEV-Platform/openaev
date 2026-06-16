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
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("TenantTables.fromEntities")
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

  // --- restrictTo: the activation allowlist --------------------------------

  private static final TenantTables MODEL =
      new TenantTables(Set.of("documents", "findings"), Set.of("groups"));

  @Test
  @DisplayName("an empty allowlist leaves no table active")
  void restrictToEmptyAllowlistActivatesNothing() {
    TenantTables active = MODEL.restrictTo(Set.of());
    assertTrue(active.strict().isEmpty());
    assertTrue(active.dualScope().isEmpty());
  }

  @Test
  @DisplayName("only the allowlisted tables stay active, keeping their family")
  void restrictToKeepsAllowlistedTablesAndFamily() {
    TenantTables active = MODEL.restrictTo(Set.of("documents", "groups"));
    assertEquals(Set.of("documents"), active.strict());
    assertEquals(Set.of("groups"), active.dualScope());
    assertEquals(TenantTables.Family.NONE, active.family("findings"));
  }

  @Test
  @DisplayName("the allowlist is matched case-insensitively")
  void restrictToIsCaseInsensitive() {
    assertEquals(
        TenantTables.Family.STRICT, MODEL.restrictTo(Set.of("DOCUMENTS")).family("documents"));
  }

  @Test
  @DisplayName("an allowlist entry that is not a known tenant table fails fast")
  void restrictToRejectsUnknownTable() {
    assertThrows(
        IllegalArgumentException.class, () -> MODEL.restrictTo(Set.of("not_a_tenant_table")));
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
