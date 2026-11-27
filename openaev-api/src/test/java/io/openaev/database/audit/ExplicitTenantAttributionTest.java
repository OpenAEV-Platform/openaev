package io.openaev.database.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.openaev.IntegrationTest;
import io.openaev.context.TenantContext;
import io.openaev.database.model.ContractOutputType;
import io.openaev.database.model.Cwe;
import io.openaev.database.model.Finding;
import io.openaev.database.model.ImportMapper;
import io.openaev.database.model.Mitigation;
import io.openaev.database.model.Tenant;
import io.openaev.database.model.attackpath.AttackPathExecution;
import io.openaev.database.model.attackpath.AttackPathFinding;
import io.openaev.database.model.attackpath.AttackPathGraphVersion;
import io.openaev.utils.mockUser.WithMockUser;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

/**
 * A row of these tables is attributed by its creator. Nothing fills a missing tenant from the
 * thread: a request or a job carries an ambient tenant, and a creator that omits the tenant gets a
 * refusal, not a row filed under that ambient tenant.
 *
 * <p>Every test runs with an ambient tenant set, the situation in which a stamp accepts the row, so
 * a refusal comes from the entity and not from a missing thread-local.
 */
@Transactional
@WithMockUser(isAdmin = true)
@DisplayName("A row saved without a tenant is refused, not filed under the ambient tenant")
class ExplicitTenantAttributionTest extends IntegrationTest {

  private static final String NULL_TENANT_REFUSED = "null value in column \"tenant_id\"";

  @BeforeEach
  void setAnAmbientTenant() {
    TenantContext.setCurrentTenant(Tenant.DEFAULT_TENANT_UUID);
    // Arrange (shared): an ambient tenant is present, so only the entity can refuse the row
    assertThat(TenantContext.hasCurrentTenant()).isTrue();
  }

  @AfterEach
  void clearAmbientTenant() {
    TenantContext.clearCurrentTenant();
  }

  private void persistAndFlush(Object entity) {
    entityManager.persist(entity);
    entityManager.flush();
  }

  @Nested
  @DisplayName("findings family")
  class FindingsFamily {

    @Test
    @DisplayName("given a finding without tenant, should refuse the insert")
    void given_findingWithoutTenant_should_refuseTheInsert() {
      // Arrange
      Finding finding = new Finding();
      finding.setType(ContractOutputType.Text);
      finding.setField("text_field");
      finding.setValue("value");

      // Act & Assert
      assertThatThrownBy(() -> persistAndFlush(finding)).hasMessageContaining(NULL_TENANT_REFUSED);
    }

    @Test
    @DisplayName("given an attack path execution without tenant, should refuse the insert")
    void given_attackPathExecutionWithoutTenant_should_refuseTheInsert() {
      // Arrange
      AttackPathExecution execution = new AttackPathExecution();
      execution.setSimulationId("sim-no-tenant");
      execution.setExecutedAt(Instant.now());

      // Act & Assert
      assertThatThrownBy(() -> persistAndFlush(execution))
          .hasMessageContaining(NULL_TENANT_REFUSED);
    }

    @Test
    @DisplayName("given an attack path finding without tenant, should refuse the insert")
    void given_attackPathFindingWithoutTenant_should_refuseTheInsert() {
      // Arrange
      AttackPathFinding finding = new AttackPathFinding();
      finding.setSimulationId("sim-no-tenant");
      finding.setType("Text");
      finding.setValue("value");
      finding.setEndpointKey("endpoint-key");

      // Act & Assert
      assertThatThrownBy(() -> persistAndFlush(finding)).hasMessageContaining(NULL_TENANT_REFUSED);
    }

    @Test
    @DisplayName("given an attack path graph version without tenant, should refuse the insert")
    void given_attackPathGraphVersionWithoutTenant_should_refuseTheInsert() {
      // Arrange
      AttackPathGraphVersion version = new AttackPathGraphVersion();
      version.setSimulationId("sim-no-tenant");
      version.setValue(1L);

      // Act & Assert
      assertThatThrownBy(() -> persistAndFlush(version)).hasMessageContaining(NULL_TENANT_REFUSED);
    }
  }

  @Nested
  @DisplayName("reference data")
  class ReferenceData {

    @Test
    @DisplayName("given a cwe without tenant, should refuse the insert")
    void given_cweWithoutTenant_should_refuseTheInsert() {
      // Arrange
      Cwe cwe = Cwe.builder().externalId("CWE-0").source("NVD").build();

      // Act & Assert
      assertThatThrownBy(() -> persistAndFlush(cwe)).hasMessageContaining(NULL_TENANT_REFUSED);
    }

    @Test
    @DisplayName("given a mitigation without tenant, should refuse the insert")
    void given_mitigationWithoutTenant_should_refuseTheInsert() {
      // Arrange
      Mitigation mitigation = new Mitigation();
      mitigation.setStixId("course-of-action--" + UUID.randomUUID());
      mitigation.setExternalId("M0000");
      mitigation.setName("mitigation without tenant");

      // Act & Assert
      assertThatThrownBy(() -> persistAndFlush(mitigation))
          .hasMessageContaining(NULL_TENANT_REFUSED);
    }

    @Test
    @DisplayName("given an import mapper without tenant, should refuse the insert")
    void given_importMapperWithoutTenant_should_refuseTheInsert() {
      // Arrange
      ImportMapper mapper = new ImportMapper();
      mapper.setName("mapper without tenant");
      mapper.setInjectTypeColumn("A");

      // Act & Assert
      assertThatThrownBy(() -> persistAndFlush(mapper)).hasMessageContaining(NULL_TENANT_REFUSED);
    }
  }
}
