package io.openaev.datapack.packs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

import io.openaev.IntegrationTest;
import io.openaev.context.TenantContext;
import io.openaev.database.model.Tenant;
import io.openaev.processor.datapack.V20260518_Service_Account;
import io.openaev.service.DataPackService;
import io.openaev.service.UserService;
import io.openaev.service.account.ServiceAccountPrivilegeService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("Service Account DataPack process tests")
@Transactional
public class V20260518_Service_AccountTest extends IntegrationTest {

  @Autowired private DataPackService dataPackService;
  @Autowired private ServiceAccountPrivilegeService privilegeService;
  @Autowired private UserService userService;
  @Autowired private EntityManager entityManager;

  @Test
  @DisplayName("Should create service account when processing pack")
  void given_noExistingServiceAccount_should_createServiceAccount() {
    // Arrange
    V20260518_Service_Account datapack =
        new V20260518_Service_Account(dataPackService, privilegeService);

    // Act
    datapack.process(new Tenant(TenantContext.getCurrentTenant()));

    // Flush and clear persistence context to force reload from DB
    entityManager.flush();
    entityManager.clear();

    // Assert — service account token should be retrievable
    var user = privilegeService.getUserServiceAccountByTenant(TenantContext.getCurrentTenant());

    assertThat(user).isPresent();
    assertThat(user.get().getTokens()).hasSize(1);

    // DataPack should be marked as processed
    assertTrue(
        dataPackService
            .findByIdAndTenant(
                V20260518_Service_Account.class.getCanonicalName(),
                new Tenant(TenantContext.getCurrentTenant()))
            .isPresent());
  }

  @Test
  @DisplayName("Should not re-create service account if already processed")
  void given_alreadyProcessed_should_notReprocess() {
    // Arrange
    V20260518_Service_Account datapack =
        new V20260518_Service_Account(dataPackService, privilegeService);

    // Act — process twice
    datapack.process(new Tenant(TenantContext.getCurrentTenant()));
    datapack.process(new Tenant(TenantContext.getCurrentTenant()));

    // Flush and clear to force reload
    entityManager.flush();
    entityManager.clear();

    // Assert — still only one user with one token
    String expectedEmail =
        ServiceAccountPrivilegeService.SERVICE_EMAIL_PATTERN.formatted(
            TenantContext.getCurrentTenant());
    var user = userService.findByEmailIgnoreCase(expectedEmail);
    assertThat(user).isPresent();
    assertThat(user.get().getTokens()).hasSize(1);
  }

  @Test
  @DisplayName("Should return false and not mark datapack when privilege service throws")
  void given_privilegeServiceFailure_should_returnFalse() {
    // Arrange — local mock; we don't need to replace the Spring bean since the datapack
    // takes the privilege service as a constructor argument.
    ServiceAccountPrivilegeService mockPrivilegeService =
        mock(ServiceAccountPrivilegeService.class);
    doThrow(new RuntimeException("DB error"))
        .when(mockPrivilegeService)
        .ensurePrivilegedUserExists(anyString());
    V20260518_Service_Account datapack =
        new V20260518_Service_Account(dataPackService, mockPrivilegeService);

    // Act
    datapack.process(new Tenant(TenantContext.getCurrentTenant()));

    // Assert — datapack should NOT be marked as processed
    assertFalse(
        dataPackService
            .findByIdAndTenant(
                V20260518_Service_Account.class.getCanonicalName(),
                new Tenant(TenantContext.getCurrentTenant()))
            .isPresent());
  }
}
