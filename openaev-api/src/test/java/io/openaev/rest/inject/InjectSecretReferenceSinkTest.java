package io.openaev.rest.inject;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.openaev.IntegrationTest;
import io.openaev.context.TenantContext;
import io.openaev.database.model.CredentialSecretReference;
import io.openaev.database.model.Inject;
import io.openaev.database.model.SecretReference;
import io.openaev.database.model.Tenant;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.CredentialFixture;
import io.openaev.utils.fixtures.InjectFixture;
import io.openaev.utils.mockUser.WithMockUser;
import jakarta.persistence.EntityManager;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@TestPropertySource(properties = "openaev.tenant.active-tables=secret_references")
@WithMockUser(isAdmin = true)
@DisplayName("an inject still carries its secret references when serialized")
class InjectSecretReferenceSinkTest extends IntegrationTest {

  @Autowired private MockMvc mvc;
  @Autowired private TenantIsolationTestHelper tenantHelper;
  @Autowired private EntityManager entityManager;
  @Autowired private DataSource dataSource;
  @Autowired private PlatformTransactionManager transactionManager;

  private JdbcTemplate jdbc;
  private String tenantId;
  private String injectId;
  private String secretReferenceId;

  @AfterEach
  void sweep() {
    // injects_secret_references cascades from both injects and secret_references.
    jdbc.update("DELETE FROM injects WHERE tenant_id = ?", tenantId);
    jdbc.update("DELETE FROM secret_references WHERE tenant_id = ?", tenantId);
    tenantHelper.deleteCommittedTenants(tenantId);
    TenantContext.clearCurrentTenant();
  }

  @BeforeEach
  void seedAnInjectLinkedToACredential() {
    jdbc = new JdbcTemplate(dataSource);
    // The seed needs its own short transaction and has to COMMIT, so that the request under test
    // runs against committed rows and its own transaction is the one that closes before Jackson
    // serializes.
    new TransactionTemplate(transactionManager)
        .executeWithoutResult(
            status -> {
              try {
                tenantId =
                    tenantHelper.createTenantWithCurrentUser("isr-" + UUID.randomUUID()).getId();
              } catch (Exception e) {
                throw new IllegalStateException(e);
              }
              tenantHelper.switchToTenant(tenantId, entityManager);
              Tenant tenant = new Tenant(tenantId);

              CredentialSecretReference credential =
                  CredentialFixture.createDefaultUsernameCredentialReference(tenant);
              entityManager.persist(credential);
              secretReferenceId = credential.getId();

              Inject inject = InjectFixture.getDefaultInject();
              inject.setTenant(tenant);
              inject.setSecretReferences(new ArrayList<>(List.<SecretReference>of(credential)));
              entityManager.persist(inject);
              injectId = inject.getId();
              entityManager.flush();
            });
  }

  @Test
  @DisplayName("GET an inject: inject_secret_references is not silently empty")
  void given_injectWithCredential_should_serializeItsSecretReferences() throws Exception {
    // Act
    String body =
        mvc.perform(get("/api/tenants/{tenantId}/injects/{id}", tenantId, injectId))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    // Assert
    List<String> secretReferenceIds = JsonPath.read(body, "$.inject_secret_references");
    assertEquals(
        List.of(secretReferenceId),
        secretReferenceIds,
        "inject_secret_references must still hold the linked credential; an empty array here is"
            + " the #7026 regression (lazy association resolved after the tenant scope closed),"
            + " not a data problem: "
            + body);
  }
}
