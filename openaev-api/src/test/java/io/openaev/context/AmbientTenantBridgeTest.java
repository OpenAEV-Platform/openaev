package io.openaev.context;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * The bridge aligns the ambient tenant on the write tenant for the duration of a call, then puts
 * back exactly what it found: the same tenant, or none at all.
 */
@DisplayName("Ambient tenant bridge")
class AmbientTenantBridgeTest {

  private final AmbientTenantBridge bridge = new AmbientTenantBridge();

  private String tenantA;
  private String tenantB;

  @BeforeEach
  void pickTenants() {
    tenantA = UUID.randomUUID().toString();
    tenantB = UUID.randomUUID().toString();
    TenantContext.clearCurrentTenant();
  }

  @AfterEach
  void cleanup() {
    TenantContext.clearCurrentTenant();
  }

  @Nested
  @DisplayName("When the ambient tenant is another one")
  class WhenTheAmbientTenantDiffers {

    @Test
    @DisplayName("given_ambientTenantA_should_runInTheWriteTenantThenRestoreA")
    void given_ambientTenantA_should_runInTheWriteTenantThenRestoreA() {
      // Arrange
      TenantContext.setCurrentTenant(tenantA);

      // Act
      String seenDuring = bridge.callInTenant(tenantB, TenantContext::getCurrentTenant);

      // Assert
      assertEquals(tenantB, seenDuring, "inside the call the ambient tenant is the write tenant");
      assertEquals(tenantA, TenantContext.getCurrentTenant(), "ambient tenant restored");
    }

    @Test
    @DisplayName("given_noAmbientTenant_should_runInTheWriteTenantThenClearIt")
    void given_noAmbientTenant_should_runInTheWriteTenantThenClearIt() {
      // Arrange
      assertFalse(TenantContext.hasCurrentTenant(), "precondition: no ambient tenant");

      // Act
      String seenDuring = bridge.callInTenant(tenantB, TenantContext::getCurrentTenant);

      // Assert
      assertEquals(tenantB, seenDuring, "inside the call the ambient tenant is the write tenant");
      assertFalse(TenantContext.hasCurrentTenant(), "the ambient tenant is cleared again");
    }

    @Test
    @DisplayName(
        "given_noAmbientTenantAndTheDefaultWriteTenant_should_setTheDefaultTenantThenClearIt")
    void given_noAmbientTenantAndTheDefaultWriteTenant_should_setTheDefaultTenantThenClearIt() {
      // Arrange: with nothing set, getCurrentTenant() answers the default tenant, which must not
      // read as "already set": the listener that stamps new rows requires hasCurrentTenant()
      assertFalse(TenantContext.hasCurrentTenant(), "precondition: no ambient tenant");
      String defaultTenant = io.openaev.database.model.Tenant.DEFAULT_TENANT_UUID;

      // Act
      boolean setDuring = bridge.callInTenant(defaultTenant, TenantContext::hasCurrentTenant);

      // Assert
      assertTrue(setDuring, "inside the call the default write tenant is set on the context");
      assertFalse(TenantContext.hasCurrentTenant(), "the ambient tenant is cleared again");
    }

    @Test
    @DisplayName("given_workThrows_should_restoreTheAmbientTenantAndRethrow")
    void given_workThrows_should_restoreTheAmbientTenantAndRethrow() {
      // Arrange
      TenantContext.setCurrentTenant(tenantA);

      // Act
      IllegalStateException thrown =
          assertThrows(
              IllegalStateException.class,
              () ->
                  bridge.callInTenant(
                      tenantB,
                      () -> {
                        throw new IllegalStateException("boom");
                      }));

      // Assert
      assertEquals("boom", thrown.getMessage());
      assertEquals(tenantA, TenantContext.getCurrentTenant(), "ambient tenant restored");
    }
  }

  @Nested
  @DisplayName("When the ambient tenant already is the write tenant")
  class WhenTheAmbientTenantAlreadyMatches {

    @Test
    @DisplayName("given_ambientAlreadyTheWriteTenant_should_leaveItInPlace")
    void given_ambientAlreadyTheWriteTenant_should_leaveItInPlace() {
      // Arrange
      TenantContext.setCurrentTenant(tenantB);

      // Act
      String seenDuring = bridge.callInTenant(tenantB, TenantContext::getCurrentTenant);

      // Assert
      assertEquals(tenantB, seenDuring);
      assertTrue(TenantContext.hasCurrentTenant(), "the ambient tenant stays set");
      assertEquals(tenantB, TenantContext.getCurrentTenant());
    }
  }

  @Nested
  @DisplayName("When there is no write tenant")
  class WhenThereIsNoWriteTenant {

    @Test
    @DisplayName("given_nullWriteTenant_should_runTheWorkWithoutTouchingTheAmbientTenant")
    void given_nullWriteTenant_should_runTheWorkWithoutTouchingTheAmbientTenant() {
      // Arrange
      TenantContext.setCurrentTenant(tenantA);

      // Act
      String seenDuring = bridge.callInTenant(null, TenantContext::getCurrentTenant);

      // Assert
      assertEquals(tenantA, seenDuring);
      assertEquals(tenantA, TenantContext.getCurrentTenant());
    }
  }
}
