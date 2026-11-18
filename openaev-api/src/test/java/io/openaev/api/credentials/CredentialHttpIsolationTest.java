package io.openaev.api.credentials;

import static io.openaev.api.credentials.CredentialApi.TENANT_CREDENTIALS_URI;
import static io.openaev.utils.JsonTestUtils.asJsonString;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.openaev.IntegrationTest;
import io.openaev.database.model.Tenant;
import io.openaev.database.repository.CredentialSecretReferenceRepository;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.CredentialFixture;
import io.openaev.utils.mockUser.WithMockUser;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * End-to-end proof that, with {@code secret_references} activated, the tenant scope resolved from
 * the request isolates credentials through the real {@link CredentialApi} endpoints.
 *
 * <p>Kept apart from {@link CredentialApiTest}: the test properties do not activate any table, and
 * the reads that rely on the transaction scope alone are only isolated once the table is active.
 */
@Transactional
@TestPropertySource(
    properties = {
      "openaev.tenant.active-tables=secret_references,secrets",
      "openaev.enabled-dev-features=CREDENTIAL_ASSET"
    })
@WithMockUser(isAdmin = true)
@DisplayName("Credential read isolation through the real HTTP endpoint")
class CredentialHttpIsolationTest extends IntegrationTest {

  @Autowired private MockMvc mvc;
  @Autowired private TenantIsolationTestHelper tenantIsolationTestHelper;
  @Autowired private CredentialSecretReferenceRepository credentialSecretReferenceRepository;

  @Nested
  @DisplayName("Find by ids")
  class FindCredentials {

    @Test
    @DisplayName("given_otherTenantCredentialId_should_notResolveIt")
    void given_otherTenantCredentialId_should_notResolveIt() throws Exception {
      // Arrange
      Tenant tenantA = tenantIsolationTestHelper.createTenantWithCurrentUser("credential-find-a");
      Tenant tenantB = tenantIsolationTestHelper.createTenantWithCurrentUser("credential-find-b");
      String tenantACredentialId =
          credentialSecretReferenceRepository
              .save(CredentialFixture.createDefaultUsernameCredentialReference(tenantA))
              .getId();
      String tenantBCredentialId =
          credentialSecretReferenceRepository
              .save(CredentialFixture.createDefaultUsernameCredentialReference(tenantB))
              .getId();

      // Act
      String response =
          mvc.perform(
                  post(TENANT_CREDENTIALS_URI.replace("{tenantId}", tenantA.getId()) + "/find")
                      .with(csrf())
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(asJsonString(List.of(tenantACredentialId, tenantBCredentialId)))
                      .accept(MediaType.APPLICATION_JSON))
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString();

      // Assert
      List<String> ids = JsonPath.read(response, "$[*].credential_id");
      assertThat(ids).containsExactly(tenantACredentialId).doesNotContain(tenantBCredentialId);
    }
  }
}
