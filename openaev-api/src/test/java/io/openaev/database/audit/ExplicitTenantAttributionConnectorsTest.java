package io.openaev.database.audit;

import static io.openaev.utils.fixtures.ConnectorInstanceFixture.createDefaultConnectorInstance;
import static io.openaev.utils.fixtures.CredentialSecretReferenceFixture.getUsernamePasswordReference;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.openaev.IntegrationTest;
import io.openaev.context.TenantContext;
import io.openaev.database.model.CatalogConnector;
import io.openaev.database.model.ConnectorInstancePersisted;
import io.openaev.database.model.CredentialSecretReference;
import io.openaev.database.model.Tenant;
import io.openaev.database.model.UsernamePasswordSecret;
import io.openaev.utils.fixtures.CatalogConnectorFixture;
import io.openaev.utils.fixtures.composers.CatalogConnectorComposer;
import io.openaev.utils.mockUser.WithMockUser;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

/**
 * A secret, a secret reference and a connector instance are attributed by their creator. Nothing
 * fills a missing tenant from the thread: a request or a job carries an ambient tenant, and a
 * creator that omits the tenant gets a refusal from the database, not a row filed under that
 * ambient tenant.
 *
 * <p>Every test runs with an ambient tenant set, the situation in which a stamp accepts the row, so
 * a refusal comes from the entity and not from a missing thread-local.
 */
@Transactional
@WithMockUser(isAdmin = true)
@DisplayName("A row saved without a tenant is refused, not filed under the ambient tenant")
class ExplicitTenantAttributionConnectorsTest extends IntegrationTest {

  private static final String NULL_TENANT_REFUSED = "null value in column \"tenant_id\"";

  @Autowired private EntityManager entityManager;
  @Autowired private CatalogConnectorComposer catalogConnectorComposer;

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
  @DisplayName("secrets family")
  class SecretsFamily {

    @Test
    @DisplayName("given a secret without tenant, should refuse the insert")
    void given_secretWithoutTenant_should_refuseTheInsert() {
      // Arrange
      UsernamePasswordSecret secret = new UsernamePasswordSecret();
      secret.setUsername("user");
      secret.setPassword("password");

      // Act & Assert
      assertThatThrownBy(() -> persistAndFlush(secret)).hasMessageContaining(NULL_TENANT_REFUSED);
    }

    @Test
    @DisplayName("given a secret reference without tenant, should refuse the insert")
    void given_secretReferenceWithoutTenant_should_refuseTheInsert() {
      // Arrange
      CredentialSecretReference reference = getUsernamePasswordReference();

      // Act & Assert
      assertThatThrownBy(() -> persistAndFlush(reference))
          .hasMessageContaining(NULL_TENANT_REFUSED);
    }
  }

  @Nested
  @DisplayName("connector instances")
  class ConnectorInstances {

    @Test
    @DisplayName("given a connector instance without tenant, should refuse the insert")
    void given_connectorInstanceWithoutTenant_should_refuseTheInsert() {
      // Arrange
      CatalogConnector catalogConnector =
          catalogConnectorComposer
              .forCatalogConnector(
                  CatalogConnectorFixture.createDefaultCatalogConnectorManagedByXtmComposer(
                      "explicit-tenant-attribution"))
              .persist()
              .get();
      ConnectorInstancePersisted instance = createDefaultConnectorInstance();
      instance.setTenant(null);
      instance.setCatalogConnector(catalogConnector);

      // Act & Assert
      assertThatThrownBy(() -> persistAndFlush(instance)).hasMessageContaining(NULL_TENANT_REFUSED);
    }
  }
}
