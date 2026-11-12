package io.openaev.api.credentials;

import static io.openaev.api.credentials.CredentialApi.TENANT_CREDENTIALS_URI;
import static io.openaev.utils.JsonTestUtils.asJsonString;
import static io.openaev.utils.TagLinkJdbc.column;
import static io.openaev.utils.TagLinkJdbc.saveTag;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.openaev.IntegrationTest;
import io.openaev.api.credentials.form.CredentialInput;
import io.openaev.config.ProductionActiveTables;
import io.openaev.context.TenantContext;
import io.openaev.database.model.CredentialSecretReference;
import io.openaev.database.repository.TagRepository;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.mockUser.WithMockUser;
import jakarta.persistence.EntityManager;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.transaction.TestTransaction;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

/**
 * A credential is linked to the tags of its own tenant, on create and on update.
 *
 * <p>The credential is written by the provider outside the request transaction, in a transaction
 * scoped to its tenant. The tag ids of the body resolve in that tenant alone. The caller belongs to
 * both tenants, and the rows are committed because the write runs in transactions of its own. The
 * links are read from {@code secret_reference_tags} through JDBC on the test connection, which the
 * statement inspector does not rewrite. The class arms the production list.
 */
@Transactional
@TestPropertySource(properties = "openaev.enabled-dev-features=CREDENTIAL_ASSET")
@WithMockUser(isAdmin = true)
@DisplayName("Credential create and update link only the tags of the credential tenant")
class CredentialTagLinkTenantTest extends IntegrationTest {

  @DynamicPropertySource
  static void productionActiveTables(DynamicPropertyRegistry registry) {
    registry.add(
        "openaev.tenant.active-tables", () -> String.join(",", ProductionActiveTables.names()));
  }

  private static final String REFERENCE_TAGS =
      "SELECT tag_id FROM secret_reference_tags WHERE secret_reference_id = ?";

  @Autowired private MockMvc mvc;
  @Autowired private TenantIsolationTestHelper tenantHelper;
  @Autowired private TagRepository tagRepository;
  @Autowired private EntityManager entityManager;

  private final List<String> committedTenantIds = new ArrayList<>();
  private String tenantA;
  private String tagInA;
  private String tagInOther;

  @BeforeEach
  void seedTwoCommittedTenants() throws Exception {
    tenantA = tenantHelper.createTenantWithCurrentUser("credential-tag-link-a").getId();
    String tenantOther = tenantHelper.createTenantWithCurrentUser("credential-tag-link-o").getId();
    committedTenantIds.add(tenantA);
    committedTenantIds.add(tenantOther);
    tagInA = saveTag(tagRepository, tenantA);
    tagInOther = saveTag(tagRepository, tenantOther);
    commitArrangeAndStartFreshTransaction();
    TenantContext.clearCurrentTenant();
  }

  @AfterEach
  void cleanup() {
    TenantContext.clearCurrentTenant();
    tenantHelper.deleteCommittedTenants(committedTenantIds.toArray(String[]::new));
    committedTenantIds.clear();
  }

  @Nested
  @DisplayName("Create")
  class Create {

    @Test
    @DisplayName(
        "given a credential of A when the body names a tag of A and a tag of another tenant then"
            + " only the tag of A is linked")
    void given_credentialOfA_should_linkOnlyTheTagOfA() throws Exception {
      // -- Arrange --
      CredentialInput input = input(List.of(tagInA, tagInOther));

      // -- Act --
      String response =
          mvc.perform(multipartCreate(input).accept(MediaType.APPLICATION_JSON))
              .andExpect(status().is2xxSuccessful())
              .andReturn()
              .getResponse()
              .getContentAsString();

      // -- Assert --
      String credentialId = JsonPath.read(response, "$.credential_id");
      assertThat(column(entityManager, REFERENCE_TAGS, credentialId))
          .as("tags linked to the credential of A: kept %s, dropped %s", tagInA, tagInOther)
          .containsExactly(tagInA);
    }
  }

  @Nested
  @DisplayName("Update")
  class Update {

    @Test
    @DisplayName(
        "given a credential of A when the update body names a tag of A and a tag of another tenant"
            + " then only the tag of A is linked")
    void given_credentialOfA_should_linkOnlyTheTagOfAOnUpdate() throws Exception {
      // -- Arrange --
      String created =
          mvc.perform(multipartCreate(input(List.of())).accept(MediaType.APPLICATION_JSON))
              .andExpect(status().is2xxSuccessful())
              .andReturn()
              .getResponse()
              .getContentAsString();
      String credentialId = JsonPath.read(created, "$.credential_id");

      // -- Act --
      mvc.perform(
              multipartCreate(input(List.of(tagInA, tagInOther)), credentialId)
                  .with(
                      request -> {
                        request.setMethod("PUT");
                        return request;
                      })
                  .accept(MediaType.APPLICATION_JSON))
          .andExpect(status().is2xxSuccessful());

      // -- Assert --
      assertThat(column(entityManager, REFERENCE_TAGS, credentialId))
          .as("tags linked to the credential of A: kept %s, dropped %s", tagInA, tagInOther)
          .containsExactly(tagInA);
    }
  }

  // -- Helpers --

  private CredentialInput input(List<String> tags) {
    String name = "credential-tag-link-" + UUID.randomUUID();
    return new CredentialInput(
        name,
        CredentialSecretReference.CREDENTIAL_TYPE.IDENTITY,
        CredentialSecretReference.CREDENTIAL_AUTH_METHOD.USERNAME_PASSWORD,
        "description-" + name,
        "user-" + name,
        "pass-" + name,
        null,
        null,
        tags);
  }

  private MockMultipartHttpServletRequestBuilder multipartCreate(CredentialInput input) {
    return multipartCreate(input, null);
  }

  private MockMultipartHttpServletRequestBuilder multipartCreate(
      CredentialInput input, String credentialId) {
    String uri = TENANT_CREDENTIALS_URI.replace("{tenantId}", tenantA);
    MockMultipartHttpServletRequestBuilder builder =
        multipart(credentialId == null ? uri : uri + "/" + credentialId);
    builder.file(
        new MockMultipartFile(
            "input",
            "",
            MediaType.APPLICATION_JSON_VALUE,
            asJsonString(input).getBytes(StandardCharsets.UTF_8)));
    return builder.with(csrf());
  }

  private void commitArrangeAndStartFreshTransaction() {
    entityManager.flush();
    entityManager.clear();
    TestTransaction.flagForCommit();
    TestTransaction.end();
    TestTransaction.start();
  }
}
