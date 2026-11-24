package io.openaev.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Which gate shapes the fail-closed detector treats as a read that returns zero rows under an empty
 * tenant scope. {@code can_access_tenant(tenant_id, true)} returns true for a platform row before
 * it looks at the scope, so a statement whose only gate is that call AND-ed with {@code tenant_id
 * IS NULL} reads platform rows only and returns them whatever the scope is.
 */
class FailClosedDetectorGateShapeTest {

  private static final String PLATFORM_ONLY_BY_KEY =
      "SELECT s1_0.parameter_value FROM parameters s1_0 WHERE (s1_0.parameter_key = ?"
          + " AND s1_0.tenant_id IS NULL) AND (can_access_tenant(s1_0.tenant_id, true))";
  private static final String PLATFORM_ONLY_ALL =
      "SELECT s1_0.parameter_value FROM parameters s1_0 WHERE (s1_0.tenant_id IS NULL)"
          + " AND (can_access_tenant(s1_0.tenant_id, true))";

  @Nested
  @DisplayName("A statement with a platform-only gate")
  class PlatformOnlyGate {

    @Test
    @DisplayName("is not a fail-closed read when the key is also constrained")
    void given_platformRowsByKey_should_notBeFailClosed() {
      // Arrange
      String sql = PLATFORM_ONLY_BY_KEY;

      // Act
      boolean failClosed = FailClosedDetectorListener.isFailClosedRead(sql);

      // Assert
      assertThat(failClosed).isFalse();
    }

    @Test
    @DisplayName("is not a fail-closed read when the platform predicate is the whole filter")
    void given_allPlatformRows_should_notBeFailClosed() {
      // Arrange
      String sql = PLATFORM_ONLY_ALL;

      // Act
      boolean failClosed = FailClosedDetectorListener.isFailClosedRead(sql);

      // Assert
      assertThat(failClosed).isFalse();
    }
  }

  @Nested
  @DisplayName("A statement with a tenant gate")
  class TenantGate {

    @Test
    @DisplayName("is a fail-closed read for a strict table")
    void given_strictTableGate_should_beFailClosed() {
      // Arrange
      String sql = "SELECT t1_0.tag_id FROM tags t1_0 WHERE (can_access_tenant(t1_0.tenant_id))";

      // Act
      boolean failClosed = FailClosedDetectorListener.isFailClosedRead(sql);

      // Assert
      assertThat(failClosed).isTrue();
    }

    @Test
    @DisplayName("is a fail-closed read for a dual-scope table read without a platform predicate")
    void given_dualScopeGateWithoutIsNull_should_beFailClosed() {
      // Arrange
      String sql =
          "SELECT g1_0.group_id FROM groups g1_0 WHERE (g1_0.group_id = ?)"
              + " AND (can_access_tenant(g1_0.tenant_id, true))";

      // Act
      boolean failClosed = FailClosedDetectorListener.isFailClosedRead(sql);

      // Assert
      assertThat(failClosed).isTrue();
    }

    @Test
    @DisplayName(
        "is a fail-closed read when the platform predicate is an alternative, not a filter")
    void given_isNullInsideAnOr_should_beFailClosed() {
      // Arrange
      String sql =
          "SELECT g1_0.group_id FROM groups g1_0 WHERE (g1_0.group_name = ?"
              + " OR g1_0.tenant_id IS NULL) AND (can_access_tenant(g1_0.tenant_id, true))";

      // Act
      boolean failClosed = FailClosedDetectorListener.isFailClosedRead(sql);

      // Assert
      assertThat(failClosed).isTrue();
    }

    @Test
    @DisplayName("is a fail-closed read when the platform predicate bears on another table")
    void given_isNullOnAnotherAlias_should_beFailClosed() {
      // Arrange
      String sql =
          "SELECT p.parameter_value FROM parameters p JOIN groups g ON g.group_id = p.parameter_key"
              + " WHERE (p.tenant_id IS NULL) AND (can_access_tenant(p.tenant_id, true))"
              + " AND (can_access_tenant(g.tenant_id, true))";

      // Act
      boolean failClosed = FailClosedDetectorListener.isFailClosedRead(sql);

      // Assert
      assertThat(failClosed).isTrue();
    }

    @Test
    @DisplayName("is a fail-closed read when it joins a gated table to a platform-only one")
    void given_platformOnlyGateNextToStrictGate_should_beFailClosed() {
      // Arrange
      String sql =
          PLATFORM_ONLY_ALL
              + " AND EXISTS (SELECT 1 FROM tags t WHERE can_access_tenant(t.tenant_id))";

      // Act
      boolean failClosed = FailClosedDetectorListener.isFailClosedRead(sql);

      // Assert
      assertThat(failClosed).isTrue();
    }
  }

  @Nested
  @DisplayName("A statement without any gate")
  class NoGate {

    @Test
    @DisplayName("is not a fail-closed read")
    void given_noGate_should_notBeFailClosed() {
      // Arrange
      String sql = "SELECT s1_0.parameter_value FROM parameters s1_0";

      // Act
      boolean failClosed = FailClosedDetectorListener.isFailClosedRead(sql);

      // Assert
      assertThat(failClosed).isFalse();
    }
  }
}
