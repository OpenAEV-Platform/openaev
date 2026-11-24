package io.openaev.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.function.Function;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

@DisplayName("The oracle of a detector listener under the '*' activation")
class DetectorListenerWildcardOracleTest {

  private static final String SQL =
      "SELECT * FROM injects i JOIN groups g ON g.group_id = i.inject_id";

  private static MockEnvironment wildcard() {
    return new MockEnvironment().withProperty("openaev.tenant.active-tables", "*");
  }

  private static void assertWatchesTablesTheWildcardArms(
      Function<MockEnvironment, TenantSqlLeakOracle> oracleOf) {
    // Act
    TenantSqlLeakOracle oracle = oracleOf.apply(wildcard());

    // Assert
    assertThat(oracle.mentioned(SQL)).containsExactlyInAnyOrder("injects", "groups");
  }

  @Nested
  @DisplayName("The fail-closed detector listener")
  class FailClosedDetector {

    @Test
    @DisplayName("given '*', should watch a strict and a dual-scope table")
    void given_wildcard_should_watchStrictAndDualScopeTables() {
      assertWatchesTablesTheWildcardArms(env -> new FailClosedDetectorListener(env).oracle());
    }
  }

  @Nested
  @DisplayName("The lazy-after-commit probe listener")
  class LazyAfterCommitProbe {

    @Test
    @DisplayName("given '*', should watch a strict and a dual-scope table")
    void given_wildcard_should_watchStrictAndDualScopeTables() {
      assertWatchesTablesTheWildcardArms(
          env -> new LazyAfterCommitProbeListener(env, () -> null).oracle());
    }
  }
}
