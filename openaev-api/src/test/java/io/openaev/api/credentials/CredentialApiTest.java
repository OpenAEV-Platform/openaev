package io.openaev.api.credentials;

import static io.openaev.api.credentials.CredentialApi.TENANT_CREDENTIALS_URI;
import static io.openaev.utils.JsonTestUtils.asJsonString;
import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.openaev.IntegrationTest;
import io.openaev.api.credentials.form.CredentialInput;
import io.openaev.database.model.CredentialSecretReference;
import io.openaev.database.model.Filters;
import io.openaev.database.model.HashSecret;
import io.openaev.database.model.Tag;
import io.openaev.database.model.Tenant;
import io.openaev.database.repository.CredentialSecretReferenceRepository;
import io.openaev.database.repository.TagRepository;
import io.openaev.secrets.service.SecretService;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.mockUser.WithMockUser;
import io.openaev.utils.pagination.SearchPaginationInput;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@TestInstance(PER_CLASS)
@Transactional
@WithMockUser(isAdmin = true)
@TestPropertySource(properties = "openaev.enabled-dev-features=CREDENTIAL_ASSET")
@DisplayName("Credential API integration tests")
class CredentialApiTest extends IntegrationTest {

  @Autowired private MockMvc mvc;
  @Autowired private TenantIsolationTestHelper tenantIsolationTestHelper;
  @Autowired private TagRepository tagRepository;
  @Autowired private CredentialSecretReferenceRepository credentialSecretReferenceRepository;
  @Autowired private SecretService secretService;

  @Nested
  @DisplayName("GET /contracts")
  class GetContracts {

    @Test
    @DisplayName("given_contractsEndpoint_should_returnSupportedContracts")
    void given_contractsEndpoint_should_returnSupportedContracts() throws Exception {
      // Arrange
      Tenant tenant = tenantIsolationTestHelper.createTenantWithCurrentUser("credential-contracts");
      String uri = tenantCredentialsUri(tenant.getId()) + "/contracts";

      // Act
      String response =
          mvc.perform(get(uri).accept(MediaType.APPLICATION_JSON))
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString();

      // Assert
      assertThatJson(response).isArray().size().isEqualTo(2);
      List<String> authMethods = JsonPath.read(response, "$[*].credential_auth_method");
      assertThat(authMethods)
          .containsExactlyInAnyOrder(
              CredentialSecretReference.CREDENTIAL_AUTH_METHOD.USERNAME_PASSWORD.name(),
              CredentialSecretReference.CREDENTIAL_AUTH_METHOD.HASH.name());
    }
  }

  @Nested
  @DisplayName("POST /search")
  class SearchCredentials {

    @Test
    @DisplayName("given_twoTenantsCredentials_should_returnOnlyRequestedTenantCredentials")
    void given_twoTenantsCredentials_should_returnOnlyRequestedTenantCredentials()
        throws Exception {
      // Arrange
      Tenant tenantA = tenantIsolationTestHelper.createTenantWithCurrentUser("credential-search-a");
      Tenant tenantB = tenantIsolationTestHelper.createTenantWithCurrentUser("credential-search-b");
      String tenantACredentialId = createCredential(tenantA.getId(), "cred-a", "user-a", "pass-a");
      createCredential(tenantB.getId(), "cred-b", "user-b", "pass-b");

      // Act
      String responseA = searchCredentials(tenantA.getId());

      // Assert
      List<String> tenantAIds = JsonPath.read(responseA, "$.content[*].credential_id");
      assertThat(tenantAIds).containsExactly(tenantACredentialId);
    }

    @Test
    @DisplayName("given_filters_should_filterByTypeAuthMethodTagsAndCreatedBy")
    void given_filters_should_filterByTypeAuthMethodTagsAndCreatedBy() throws Exception {
      // Arrange
      Tenant tenant = tenantIsolationTestHelper.createTenantWithCurrentUser("credential-filter");
      Tag matchTag = createTag(tenant.getId(), "credential-filter-match");
      Tag otherTag = createTag(tenant.getId(), "credential-filter-other");

      String matchId =
          createHashCredential(
              tenant.getId(),
              "hash-credential-match",
              "match-hash",
              HashSecret.HASH_ALGORITHM.SHA,
              List.of(matchTag.getId()));
      String otherId =
          createHashCredential(
              tenant.getId(),
              "hash-credential-other",
              "other-hash",
              HashSecret.HASH_ALGORITHM.NTLM,
              List.of(otherTag.getId()));

      CredentialSecretReference otherCredential =
          credentialSecretReferenceRepository.findById(otherId).orElseThrow();
      otherCredential.setCreatedBy(null);
      credentialSecretReferenceRepository.save(otherCredential);

      String createdById =
          credentialSecretReferenceRepository
              .findById(matchId)
              .orElseThrow()
              .getCreatedBy()
              .getId();

      SearchPaginationInput input = new SearchPaginationInput();
      input.setFilterGroup(
          Filters.FilterGroup.filterGroupWithFilters(
              List.of(
                  filter("credential_type", "IDENTITY"),
                  filter("credential_auth_method", "HASH"),
                  filter("credential_tags_ids", matchTag.getId()),
                  filter("credential_created_by", createdById))));

      // Act
      String response = searchCredentials(tenant.getId(), input);

      // Assert
      List<String> ids = JsonPath.read(response, "$.content[*].credential_id");
      assertThat(ids).containsExactly(matchId);
    }

    @Test
    @DisplayName("given_textSearch_should_searchByCredentialName")
    void given_textSearch_should_searchByCredentialName() throws Exception {
      // Arrange
      Tenant tenant =
          tenantIsolationTestHelper.createTenantWithCurrentUser("credential-search-name");
      String matchId =
          createCredential(tenant.getId(), "vpn-admin-credential", "vpn-user", "vpn-pass");
      createCredential(tenant.getId(), "db-reader-credential", "db-user", "db-pass");

      SearchPaginationInput input = new SearchPaginationInput();
      input.setTextSearch("vpn-admin");

      // Act
      String response = searchCredentials(tenant.getId(), input);

      // Assert
      List<String> ids = JsonPath.read(response, "$.content[*].credential_id");
      assertThat(ids).containsExactly(matchId);
    }

    @Test
    @DisplayName("given_twoTenants_when_searchingTenantA_should_notReturnTenantBCredentials")
    void given_twoTenants_when_searchingTenantA_should_notReturnTenantBCredentials()
        throws Exception {
      // Arrange
      Tenant tenantA =
          tenantIsolationTestHelper.createTenantWithCurrentUser("credential-search-iso-a");
      Tenant tenantB =
          tenantIsolationTestHelper.createTenantWithCurrentUser("credential-search-iso-b");
      String tenantACredentialId =
          createCredential(tenantA.getId(), "tenant-a-credential", "a-user", "a-pass");
      createCredential(tenantB.getId(), "tenant-b-credential", "b-user", "b-pass");

      SearchPaginationInput input = new SearchPaginationInput();
      input.setTextSearch("tenant-");

      // Act
      String response = searchCredentials(tenantA.getId(), input);

      // Assert
      List<String> ids = JsonPath.read(response, "$.content[*].credential_id");
      assertThat(ids).containsExactly(tenantACredentialId);
    }
  }

  @Nested
  @DisplayName("POST /")
  class CreateCredential {

    @Test
    @DisplayName("given_validInput_should_createCredentialInRequestedTenant")
    void given_validInput_should_createCredentialInRequestedTenant() throws Exception {
      // Arrange
      Tenant tenantA = tenantIsolationTestHelper.createTenantWithCurrentUser("credential-create-a");
      Tenant tenantB = tenantIsolationTestHelper.createTenantWithCurrentUser("credential-create-b");

      // Act
      String credentialId = createCredential(tenantA.getId(), "created-credential", "user", "pass");
      String responseA = searchCredentials(tenantA.getId());
      String responseB = searchCredentials(tenantB.getId());

      // Assert
      List<String> tenantAIds = JsonPath.read(responseA, "$.content[*].credential_id");
      List<String> tenantBIds = JsonPath.read(responseB, "$.content[*].credential_id");
      assertThat(tenantAIds).contains(credentialId);
      assertThat(tenantBIds).doesNotContain(credentialId);
    }

    @Test
    @DisplayName("given_hashCredentialWithoutHash_should_failCreation")
    void given_hashCredentialWithoutHash_should_failCreation() throws Exception {
      // Arrange
      Tenant tenant =
          tenantIsolationTestHelper.createTenantWithCurrentUser("credential-post-hash-no-hash");
      CredentialInput input =
          new CredentialInput(
              "invalid-hash-missing-hash",
              CredentialSecretReference.CREDENTIAL_TYPE.IDENTITY,
              CredentialSecretReference.CREDENTIAL_AUTH_METHOD.HASH,
              "desc",
              null,
              null,
              HashSecret.HASH_ALGORITHM.SHA,
              null,
              List.of());

      // Act & Assert
      mvc.perform(
              post(tenantCredentialsUri(tenant.getId()))
                  .with(csrf())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(asJsonString(input)))
          .andExpect(status().is4xxClientError());
    }

    @Test
    @DisplayName("given_hashCredentialWithoutHashAlgorithm_should_failCreation")
    void given_hashCredentialWithoutHashAlgorithm_should_failCreation() throws Exception {
      // Arrange
      Tenant tenant =
          tenantIsolationTestHelper.createTenantWithCurrentUser("credential-post-hash-no-algo");
      CredentialInput input =
          new CredentialInput(
              "invalid-hash-missing-algo",
              CredentialSecretReference.CREDENTIAL_TYPE.IDENTITY,
              CredentialSecretReference.CREDENTIAL_AUTH_METHOD.HASH,
              "desc",
              null,
              null,
              null,
              "hash-value",
              List.of());

      // Act & Assert
      mvc.perform(
              post(tenantCredentialsUri(tenant.getId()))
                  .with(csrf())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(asJsonString(input)))
          .andExpect(status().is4xxClientError());
    }

    @Test
    @DisplayName("given_usernameCredentialWithoutUsername_should_failCreation")
    void given_usernameCredentialWithoutUsername_should_failCreation() throws Exception {
      // Arrange
      Tenant tenant =
          tenantIsolationTestHelper.createTenantWithCurrentUser("credential-post-up-no-user");
      CredentialInput input =
          new CredentialInput(
              "invalid-up-missing-username",
              CredentialSecretReference.CREDENTIAL_TYPE.IDENTITY,
              CredentialSecretReference.CREDENTIAL_AUTH_METHOD.USERNAME_PASSWORD,
              "desc",
              null,
              "password",
              null,
              null,
              List.of());

      // Act & Assert
      mvc.perform(
              post(tenantCredentialsUri(tenant.getId()))
                  .with(csrf())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(asJsonString(input)))
          .andExpect(status().is4xxClientError());
    }

    @Test
    @DisplayName("given_usernameCredentialWithoutPassword_should_failCreation")
    void given_usernameCredentialWithoutPassword_should_failCreation() throws Exception {
      // Arrange
      Tenant tenant =
          tenantIsolationTestHelper.createTenantWithCurrentUser("credential-post-up-no-pass");
      CredentialInput input =
          new CredentialInput(
              "invalid-up-missing-password",
              CredentialSecretReference.CREDENTIAL_TYPE.IDENTITY,
              CredentialSecretReference.CREDENTIAL_AUTH_METHOD.USERNAME_PASSWORD,
              "desc",
              "username",
              null,
              null,
              null,
              List.of());

      // Act & Assert
      mvc.perform(
              post(tenantCredentialsUri(tenant.getId()))
                  .with(csrf())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(asJsonString(input)))
          .andExpect(status().is4xxClientError());
    }
  }

  @Nested
  @DisplayName("GET /{credentialId}")
  class GetCredential {

    @Test
    @DisplayName("given_credentialFromOtherTenant_should_notReturnIt")
    void given_credentialFromOtherTenant_should_notReturnIt() throws Exception {
      // Arrange
      Tenant tenantA = tenantIsolationTestHelper.createTenantWithCurrentUser("credential-get-a");
      Tenant tenantB = tenantIsolationTestHelper.createTenantWithCurrentUser("credential-get-b");
      String credentialId =
          createCredential(tenantA.getId(), "get-credential", "user-get", "pass-get");

      // Act
      String ownTenantResponse =
          mvc.perform(get(tenantCredentialsUri(tenantA.getId()) + "/" + credentialId))
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString();

      // Assert
      assertThatJson(ownTenantResponse).node("credential_id").isEqualTo(credentialId);
      mvc.perform(get(tenantCredentialsUri(tenantB.getId()) + "/" + credentialId))
          .andExpect(status().is4xxClientError());
    }

    @Test
    @DisplayName("given_tenantA_when_gettingTenantBCredential_should_fail")
    void given_tenantA_when_gettingTenantBCredential_should_fail() throws Exception {
      // Arrange
      Tenant tenantA =
          tenantIsolationTestHelper.createTenantWithCurrentUser("credential-get-cross-a");
      Tenant tenantB =
          tenantIsolationTestHelper.createTenantWithCurrentUser("credential-get-cross-b");
      String tenantBCredentialId =
          createCredential(tenantB.getId(), "b-only-credential", "b-user", "b-pass");

      // Act & Assert
      mvc.perform(get(tenantCredentialsUri(tenantA.getId()) + "/" + tenantBCredentialId))
          .andExpect(status().is4xxClientError());
    }
  }

  @Nested
  @DisplayName("PUT /{credentialId}")
  class UpdateCredential {

    @Test
    @DisplayName("given_existingCredential_should_updateIt")
    void given_existingCredential_should_updateIt() throws Exception {
      // Arrange
      Tenant tenant = tenantIsolationTestHelper.createTenantWithCurrentUser("credential-update");
      String credentialId =
          createCredential(tenant.getId(), "before-update", "before-user", "before-pass");
      CredentialInput updateInput =
          usernamePasswordInput("after-update", "after-user", "after-pass", List.of());

      // Act
      mvc.perform(
              put(tenantCredentialsUri(tenant.getId()) + "/" + credentialId)
                  .with(csrf())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(asJsonString(updateInput)))
          .andExpect(status().is2xxSuccessful());

      String getResponse =
          mvc.perform(get(tenantCredentialsUri(tenant.getId()) + "/" + credentialId))
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString();

      // Assert
      assertThatJson(getResponse).node("credential_name").isEqualTo("after-update");
      assertThatJson(getResponse).node("credential_auth_method").isEqualTo("USERNAME_PASSWORD");
    }

    @Test
    @DisplayName("given_hashCredential_when_updatingToUsernamePassword_should_replaceHashSecret")
    void given_hashCredential_when_updatingToUsernamePassword_should_replaceHashSecret()
        throws Exception {
      // Arrange
      Tenant tenant =
          tenantIsolationTestHelper.createTenantWithCurrentUser("credential-update-switch");
      String credentialId =
          createHashCredential(
              tenant.getId(),
              "hash-before-switch",
              "old-hash",
              HashSecret.HASH_ALGORITHM.SHA,
              List.of());
      String beforeUpdateResponse =
          mvc.perform(get(tenantCredentialsUri(tenant.getId()) + "/" + credentialId))
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString();
      String previousSecretId =
          credentialSecretReferenceRepository.findById(credentialId).orElseThrow().getLocation();

      CredentialInput switchInput =
          usernamePasswordInput("hash-switched-to-userpass", "new-user", "new-password", List.of());

      // Act
      mvc.perform(
              put(tenantCredentialsUri(tenant.getId()) + "/" + credentialId)
                  .with(csrf())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(asJsonString(switchInput)))
          .andExpect(status().is2xxSuccessful());

      String afterUpdateResponse =
          mvc.perform(get(tenantCredentialsUri(tenant.getId()) + "/" + credentialId))
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString();
      String currentSecretId =
          credentialSecretReferenceRepository.findById(credentialId).orElseThrow().getLocation();

      // Assert
      assertThatJson(beforeUpdateResponse).node("credential_auth_method").isEqualTo("HASH");
      assertThatJson(afterUpdateResponse)
          .node("credential_auth_method")
          .isEqualTo("USERNAME_PASSWORD");
      assertThatJson(afterUpdateResponse).node("credential_hash_algorithm").isNull();
      assertThatJson(afterUpdateResponse).node("credential_username").isEqualTo("new-user");
      assertThat(currentSecretId).isNotEqualTo(previousSecretId);
      assertThat(secretService.findByIdOrThrow(currentSecretId)).isNotNull();
    }
  }

  @Nested
  @DisplayName("DELETE /{credentialId}")
  class DeleteCredential {

    @Test
    @DisplayName("given_existingCredential_should_deleteIt")
    void given_existingCredential_should_deleteIt() throws Exception {
      // Arrange
      Tenant tenant = tenantIsolationTestHelper.createTenantWithCurrentUser("credential-delete");
      String credentialId =
          createCredential(tenant.getId(), "to-delete", "delete-user", "delete-pass");

      // Act
      mvc.perform(delete(tenantCredentialsUri(tenant.getId()) + "/" + credentialId).with(csrf()))
          .andExpect(status().is2xxSuccessful());
      String searchResponse = searchCredentials(tenant.getId());

      // Assert
      List<String> credentialIds = JsonPath.read(searchResponse, "$.content[*].credential_id");
      assertThat(credentialIds).doesNotContain(credentialId);
    }
  }

  private String searchCredentials(String tenantId) throws Exception {
    return searchCredentials(tenantId, new SearchPaginationInput());
  }

  private String searchCredentials(String tenantId, SearchPaginationInput input) throws Exception {
    return mvc.perform(
            post(tenantCredentialsUri(tenantId) + "/search")
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJsonString(input))
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andReturn()
        .getResponse()
        .getContentAsString();
  }

  private String createCredential(String tenantId, String name, String username, String password)
      throws Exception {
    CredentialInput input = usernamePasswordInput(name, username, password, List.of());
    String response =
        mvc.perform(
                post(tenantCredentialsUri(tenantId))
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(asJsonString(input))
                    .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().is2xxSuccessful())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return JsonPath.read(response, "$.credential_id");
  }

  private String createHashCredential(
      String tenantId,
      String name,
      String hash,
      HashSecret.HASH_ALGORITHM hashAlgorithm,
      List<String> tagIds)
      throws Exception {
    CredentialInput input =
        new CredentialInput(
            name,
            CredentialSecretReference.CREDENTIAL_TYPE.IDENTITY,
            CredentialSecretReference.CREDENTIAL_AUTH_METHOD.HASH,
            "description-" + name,
            null,
            null,
            hashAlgorithm,
            hash,
            tagIds);
    String response =
        mvc.perform(
                post(tenantCredentialsUri(tenantId))
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(asJsonString(input))
                    .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().is2xxSuccessful())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return JsonPath.read(response, "$.credential_id");
  }

  private Tag createTag(String tenantId, String name) {
    Tag tag = new Tag();
    tag.setName(name + "-" + UUID.randomUUID());
    tag.setColor("#FFFFFF");
    tag.setTenant(new Tenant(tenantId));
    return tagRepository.save(tag);
  }

  private Filters.Filter filter(String key, String value) {
    Filters.Filter filter = new Filters.Filter();
    filter.setId(UUID.randomUUID().toString());
    filter.setKey(key);
    filter.setMode(Filters.FilterMode.or);
    filter.setOperator(Filters.FilterOperator.eq);
    filter.setValues(List.of(value));
    return filter;
  }

  private CredentialInput usernamePasswordInput(
      String name, String username, String password, List<String> tagIds) {
    return new CredentialInput(
        name,
        CredentialSecretReference.CREDENTIAL_TYPE.IDENTITY,
        CredentialSecretReference.CREDENTIAL_AUTH_METHOD.USERNAME_PASSWORD,
        "description-" + name,
        username,
        password,
        null,
        null,
        tagIds);
  }

  private String tenantCredentialsUri(String tenantId) {
    return TENANT_CREDENTIALS_URI.replace("{tenantId}", tenantId);
  }
}
