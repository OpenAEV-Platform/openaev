package io.openaev.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.function.Supplier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("The oracle of a detector listener watches the gated tenant tables")
class DetectorListenerOracleTest {

  private static final String SQL =
      "SELECT * FROM injects i JOIN groups g ON g.group_id = i.inject_id";

  private static void assertWatchesAStrictAndADualScopeTable(
      Supplier<TenantSqlLeakOracle> oracleOf) {
    // Act
    TenantSqlLeakOracle oracle = oracleOf.get();

    // Assert
    assertThat(oracle.mentioned(SQL)).containsExactlyInAnyOrder("injects", "groups");
  }

  @Nested
  @DisplayName("The fail-closed detector listener")
  class FailClosedDetector {

    @Test
    @DisplayName("given a statement on gated tables, should watch a strict and a dual-scope table")
    void given_statementOnGatedTables_should_watchStrictAndDualScopeTables() {
      assertWatchesAStrictAndADualScopeTable(() -> new FailClosedDetectorListener().oracle());
    }
  }

  @Nested
  @DisplayName("The lazy-after-commit probe listener")
  class LazyAfterCommitProbe {

    @Test
    @DisplayName("given a statement on gated tables, should watch a strict and a dual-scope table")
    void given_statementOnGatedTables_should_watchStrictAndDualScopeTables() {
      assertWatchesAStrictAndADualScopeTable(
          () -> new LazyAfterCommitProbeListener(() -> null).oracle());
    }
  }
}
