package io.openaev.utils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.openaev.context.TxCtx;
import io.openaev.rest.exception.TenantSingleScopeRequiredException;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("TxCtxScopeUtils#requireOneTenantOfScope")
class TxCtxScopeUtilsTest {

  @Nested
  @DisplayName("given a scope pinning exactly one tenant")
  class GivenASingleTenantScope {

    @Test
    void should_returnThatTenant() {
      // Arrange
      TxCtx ctx = TxCtx.forTenant("tenant-a");

      // Act
      String resolved = TxCtxScopeUtils.requireOneTenantOfScope(ctx);

      // Assert
      assertThat(resolved).isEqualTo("tenant-a");
    }
  }

  @Nested
  @DisplayName("given an ambiguous scope")
  class GivenAnAmbiguousScope {

    @Test
    void given_noTenantInScope_should_refuseWithoutMentioningACreate() {
      // Arrange
      TxCtx ctx = TxCtx.missing();

      // Act / Assert
      assertThatThrownBy(() -> TxCtxScopeUtils.requireOneTenantOfScope(ctx))
          .isInstanceOf(TenantSingleScopeRequiredException.class)
          .hasMessageNotContaining("create")
          .hasMessageContaining(TenantSingleScopeRequiredException.CODE);
    }

    @Test
    void given_multipleTenantsInScope_should_refuseWithoutMentioningACreate() {
      // Arrange
      TxCtx ctx = TxCtx.forTenants(Set.of("tenant-a", "tenant-b"));

      // Act / Assert
      assertThatThrownBy(() -> TxCtxScopeUtils.requireOneTenantOfScope(ctx))
          .isInstanceOf(TenantSingleScopeRequiredException.class)
          .hasMessageNotContaining("create")
          .hasMessageContaining(TenantSingleScopeRequiredException.CODE);
    }
  }
}
