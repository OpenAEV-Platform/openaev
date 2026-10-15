package io.openaev.rest.attack_pattern.service;

import static io.openaev.database.model.Tenant.DEFAULT_TENANT_UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;

import io.openaev.IntegrationTest;
import io.openaev.context.TenantScopedTransaction;
import io.openaev.context.TxCtx;
import io.openaev.database.model.AttackPattern;
import io.openaev.database.repository.AttackPatternRepository;
import io.openaev.rest.attack_pattern.form.AttackPatternCreateInput;
import io.openaev.utils.mockUser.WithMockUser;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Transactional
@TestInstance(PER_CLASS)
@TestPropertySource(properties = "openaev.tenant.active-tables=attack_patterns")
@DisplayName("Attack pattern upsert")
class AttackPatternUpsertServiceTest extends IntegrationTest {

  @Autowired private AttackPatternUpsertService upsertService;
  @Autowired private AttackPatternRepository attackPatternRepository;
  @Autowired private TenantScopedTransaction tenantTx;

  @Nested
  @DisplayName("Concurrency")
  class Concurrency {

    @Test
    @WithMockUser(isAdmin = true)
    @DisplayName("given an upsert running in a tenant, should reject a second one with a 409")
    void given_upsertRunning_should_rejectSecondUpsertInSameTenant() {
      // Arrange
      attackPatternRepository.tryLockUpsert(DEFAULT_TENANT_UUID);

      // Act / Assert
      assertThatThrownBy(() -> upsertInNewTransaction(DEFAULT_TENANT_UUID))
          .isInstanceOfSatisfying(
              ResponseStatusException.class,
              e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    @WithMockUser(isAdmin = true)
    @DisplayName("given an upsert running in a tenant, should let another tenant upsert")
    void given_upsertRunning_should_letOtherTenantProceed() {
      // Arrange
      attackPatternRepository.tryLockUpsert(DEFAULT_TENANT_UUID);

      // Act
      List<AttackPattern> upserted = upsertInNewTransaction(UUID.randomUUID().toString());

      // Assert
      assertThat(upserted).isEmpty();
    }

    private List<AttackPattern> upsertInNewTransaction(String tenantId) {
      TxCtx ctx = TxCtx.forTenant(tenantId);
      return tenantTx.executeNew(ctx, () -> upsertService.upsert(ctx, List.of(), false));
    }
  }

  @Nested
  @DisplayName("Parent")
  class Parent {

    @Test
    @WithMockUser(isAdmin = true, autoJoinDefaultTenant = true)
    @DisplayName("given a new child and its parent in one upsert, should link the child")
    void given_newChildAndParent_should_linkChildToParent() {
      // Arrange
      AttackPatternCreateInput parent = input("T9902");
      AttackPatternCreateInput child = input("T9902.001");
      child.setParentId(parent.getStixId());

      // Act
      List<AttackPattern> upserted =
          upsertService.upsert(TxCtx.forTenant(DEFAULT_TENANT_UUID), List.of(child, parent), false);

      // Assert
      assertThat(upserted)
          .filteredOn(a -> a.getExternalId().equals("T9902.001"))
          .singleElement()
          .extracting(a -> a.getParent().getExternalId())
          .isEqualTo("T9902");
    }

    private static AttackPatternCreateInput input(String externalId) {
      AttackPatternCreateInput input = new AttackPatternCreateInput();
      input.setName("upsert-" + externalId);
      input.setExternalId(externalId);
      return input;
    }
  }
}
