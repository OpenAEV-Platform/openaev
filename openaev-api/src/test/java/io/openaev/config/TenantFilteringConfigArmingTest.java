package io.openaev.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("The tenant tables the statement inspector gates")
class TenantFilteringConfigArmingTest {

  private static TenantTables schemaWithAnchors() {
    return new TenantTables(
        Set.of("injects", "exercises", "scenarios", "findings", "documents"), Set.of("roles"));
  }

  @Nested
  @DisplayName("When the schema holds the anchor tables")
  class CompleteSchema {

    @Test
    @DisplayName("given every anchor, should arm every tenant table, strict and dual-scope")
    void given_everyAnchor_should_armEveryTenantTable() {
      // Act
      TenantTables armed = TenantFilteringConfig.arm(schemaWithAnchors());

      // Assert
      assertThat(armed.strict())
          .containsExactlyInAnyOrder("injects", "exercises", "scenarios", "findings", "documents");
      assertThat(armed.dualScope()).containsExactly("roles");
    }

    @Test
    @DisplayName("given self-isolated tables in the schema, should leave them out")
    void given_selfIsolatedTablesInTheSchema_should_leaveThemOut() {
      // Arrange
      TenantTables schema =
          new TenantTables(
              Set.of(
                  "injects",
                  "exercises",
                  "scenarios",
                  "findings",
                  "tenants",
                  "users_tenants",
                  "attackpath_graph_version"),
              Set.of("roles"));

      // Act
      TenantTables armed = TenantFilteringConfig.arm(schema);

      // Assert
      assertThat(armed.strict())
          .containsExactlyInAnyOrder("injects", "exercises", "scenarios", "findings")
          .doesNotContainAnyElementsOf(TenantTables.selfIsolatedTables());
      assertThat(armed.dualScope()).containsExactly("roles");
    }
  }

  @Nested
  @DisplayName("When the schema yields nothing")
  class EmptySchema {

    @Test
    @DisplayName("given no tenant table, should refuse to start rather than arm nothing")
    void given_noTenantTable_should_refuseToStart() {
      // Arrange
      TenantTables nothing = new TenantTables(Set.of(), Set.of());

      // Act & Assert
      assertThatThrownBy(() -> TenantFilteringConfig.arm(nothing))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("no tenant table");
    }

    @Test
    @DisplayName("given only self-isolated tables, should refuse to start")
    void given_onlySelfIsolatedTables_should_refuseToStart() {
      // Arrange
      TenantTables onlySelfIsolated =
          new TenantTables(Set.of("tenants", "users_tenants"), Set.of());

      // Act & Assert
      assertThatThrownBy(() -> TenantFilteringConfig.arm(onlySelfIsolated))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("no tenant table");
    }
  }

  @Nested
  @DisplayName("When the schema lacks an anchor table")
  class MissingAnchor {

    @Test
    @DisplayName("given a strict anchor missing, should refuse to start and name it")
    void given_strictAnchorMissing_should_refuseToStartAndNameIt() {
      // Arrange
      TenantTables withoutInjects =
          new TenantTables(
              Set.of("exercises", "scenarios", "findings", "documents"), Set.of("roles"));

      // Act & Assert
      assertThatThrownBy(() -> TenantFilteringConfig.arm(withoutInjects))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("injects");
    }

    @Test
    @DisplayName("given the dual-scope anchor missing, should refuse to start and name it")
    void given_dualScopeAnchorMissing_should_refuseToStartAndNameIt() {
      // Arrange
      TenantTables withoutRoles =
          new TenantTables(Set.of("injects", "exercises", "scenarios", "findings"), Set.of());

      // Act & Assert
      assertThatThrownBy(() -> TenantFilteringConfig.arm(withoutRoles))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("roles");
    }
  }
}
