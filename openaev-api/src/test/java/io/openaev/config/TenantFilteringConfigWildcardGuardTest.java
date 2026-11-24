package io.openaev.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("The startup guard of the '*' activation")
class TenantFilteringConfigWildcardGuardTest {

  private static final List<String> WILDCARD = List.of(TenantTables.ALL_STRICT);

  private static TenantTables schemaWithAnchors() {
    return new TenantTables(
        Set.of("injects", "exercises", "scenarios", "findings", "documents"), Set.of("roles"));
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
      assertThatThrownBy(() -> TenantFilteringConfig.arm(nothing, WILDCARD))
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
      assertThatThrownBy(() -> TenantFilteringConfig.arm(onlySelfIsolated, WILDCARD))
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
      assertThatThrownBy(() -> TenantFilteringConfig.arm(withoutInjects, WILDCARD))
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
      assertThatThrownBy(() -> TenantFilteringConfig.arm(withoutRoles, WILDCARD))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("roles");
    }
  }

  @Nested
  @DisplayName("When the schema is complete or the list is explicit")
  class Accepted {

    @Test
    @DisplayName("given every anchor, should arm what '*' arms")
    void given_everyAnchor_should_armWhatTheWildcardArms() {
      // Act
      TenantTables armed = TenantFilteringConfig.arm(schemaWithAnchors(), WILDCARD);

      // Assert
      assertThat(armed.strict()).contains("injects", "documents");
      assertThat(armed.dualScope()).containsExactly("roles");
    }

    @Test
    @DisplayName("given an explicit list on a schema without anchors, should not apply the guard")
    void given_explicitListOnSchemaWithoutAnchors_should_notApplyTheGuard() {
      // Arrange
      TenantTables small = new TenantTables(Set.of("documents"), Set.of());

      // Act
      TenantTables armed = TenantFilteringConfig.arm(small, List.of("documents"));

      // Assert
      assertThat(armed.strict()).containsExactly("documents");
    }

    @Test
    @DisplayName("given an empty list, should arm nothing and boot")
    void given_emptyList_should_armNothingAndBoot() {
      // Act
      TenantTables armed = TenantFilteringConfig.arm(schemaWithAnchors(), List.of());

      // Assert
      assertThat(armed.strict()).isEmpty();
      assertThat(armed.dualScope()).isEmpty();
    }
  }
}
