package io.openaev.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.openaev.architecture.BackgroundEntrypointTenantScopeArchTest;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("The production activation list is read as the tables it arms")
class ProductionActiveTablesTest {

  @Nested
  @DisplayName("An explicit list")
  class ExplicitList {

    @Test
    @DisplayName("given an explicit list, should return its tables trimmed and lowercased")
    void given_explicitList_should_returnItsTablesTrimmedAndLowercased() {
      // Act
      Set<String> tables = ProductionActiveTables.armedBy(" Tags, documents ,");

      // Assert
      assertThat(tables).containsExactly("documents", "tags");
    }

    @Test
    @DisplayName("given an empty value, should arm nothing")
    void given_emptyValue_should_armNothing() {
      // Act & Assert
      assertThat(ProductionActiveTables.armedBy("")).isEmpty();
    }
  }

  @Nested
  @DisplayName("The terminal wildcard")
  class TerminalWildcard {

    @Test
    @DisplayName("given the wildcard, should return every table it arms and not the wildcard")
    void given_wildcard_should_returnEveryTableItArmsAndNotTheWildcard() {
      // Act
      Set<String> tables = ProductionActiveTables.armedBy(" " + TenantTables.ALL_STRICT + " ");

      // Assert
      assertThat(tables)
          .doesNotContain(TenantTables.ALL_STRICT)
          .contains("scenarios", "exercises")
          .containsExactlyInAnyOrderElementsOf(
              BackgroundEntrypointTenantScopeArchTest.wildcardActivatedTables());
    }

    @Test
    @DisplayName("given the wildcard beside a table, should refuse it as production does")
    void given_wildcardBesideATable_should_refuseItAsProductionDoes() {
      // Act & Assert
      assertThatThrownBy(() -> ProductionActiveTables.armedBy(TenantTables.ALL_STRICT + ",tags"))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("not both");
    }
  }
}
