package io.openaev.api.credentials;

import static io.openaev.api.credentials.CredentialApi.GCP_PRIVATE_KEY_PART;
import static io.openaev.api.credentials.CredentialApi.MAX_GCP_PRIVATE_KEY_SIZE_BYTES;
import static io.openaev.api.credentials.CredentialApi.TENANT_CREDENTIALS_URI;
import static io.openaev.database.model.AwsAssumeRoleSecret.AWS_SOURCE_IDENTITY_TYPE.STATIC_ACCESS_KEY;
import static io.openaev.database.model.SecretReference.SECRET_STATUS.ACTIVE;
import static io.openaev.database.model.SecretReference.SECRET_STATUS.AUTH_FAILED;
import static io.openaev.database.model.SecretReference.SECRET_STATUS.TIMEOUT;
import static io.openaev.integration.impl.secrets.local.LocalSecretsProviderIntegration.LOCAL_SECRETS_PROVIDER_ID;
import static io.openaev.service.credential.CredentialService.GCP_DEFAULT_SCOPE;
import static io.openaev.utils.JsonTestUtils.asJsonString;
import static io.openaev.utils.fixtures.SecretStoreRequestFixture.AWS_ACCESS_KEY_ID;
import static io.openaev.utils.fixtures.SecretStoreRequestFixture.AWS_DEFAULT_REGION;
import static io.openaev.utils.fixtures.SecretStoreRequestFixture.AWS_EXTERNAL_ID;
import static io.openaev.utils.fixtures.SecretStoreRequestFixture.AWS_ROLE_ARN;
import static io.openaev.utils.fixtures.SecretStoreRequestFixture.AWS_SECRET_ACCESS_KEY;
import static io.openaev.utils.fixtures.SecretStoreRequestFixture.AWS_SOURCE_PROFILE_ACCESS_KEY_ID;
import static io.openaev.utils.fixtures.SecretStoreRequestFixture.AWS_SOURCE_PROFILE_SECRET_ACCESS_KEY;
import static io.openaev.utils.fixtures.SecretStoreRequestFixture.AZURE_CLIENT_ID;
import static io.openaev.utils.fixtures.SecretStoreRequestFixture.AZURE_CLIENT_SECRET;
import static io.openaev.utils.fixtures.SecretStoreRequestFixture.AZURE_ENVIRONMENT;
import static io.openaev.utils.fixtures.SecretStoreRequestFixture.AZURE_SUBSCRIPTION_ID;
import static io.openaev.utils.fixtures.SecretStoreRequestFixture.AZURE_TENANT_ID;
import static io.openaev.utils.fixtures.SecretStoreRequestFixture.GCP_OAUTH_CLIENT_ID;
import static io.openaev.utils.fixtures.SecretStoreRequestFixture.GCP_OAUTH_CLIENT_SECRET;
import static io.openaev.utils.fixtures.SecretStoreRequestFixture.GCP_OAUTH_REFRESH_TOKEN;
import static io.openaev.utils.fixtures.SecretStoreRequestFixture.GCP_PROJECT_ID;
import static io.openaev.utils.fixtures.SecretStoreRequestFixture.GCP_SCOPE;
import static io.openaev.utils.fixtures.SecretStoreRequestFixture.gcpPrivateKeyJsonBytes;
import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.openaev.IntegrationTest;
import io.openaev.api.credentials.form.CredentialBulkProcessingInput;
import io.openaev.api.credentials.form.CredentialInput;
import io.openaev.database.model.*;
import io.openaev.database.repository.CredentialSecretReferenceRepository;
import io.openaev.database.repository.SecretsRepository;
import io.openaev.database.repository.TagRepository;
import io.openaev.database.repository.UserRepository;
import io.openaev.secrets.provider.SecretConnectionResult;
import io.openaev.secrets.provider.impl.validators.AwsCredentialConnectivityCheck;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.CredentialFixture;
import io.openaev.utils.fixtures.CredentialInputFixture;
import io.openaev.utils.fixtures.TagFixture;
import io.openaev.utils.fixtures.UserFixture;
import io.openaev.utils.mockUser.WithMockUser;
import io.openaev.utils.pagination.SearchPaginationInput;
import jakarta.persistence.EntityManager;
import java.util.ArrayList;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.transaction.TestTransaction;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
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
  @Autowired private UserRepository userRepository;
  @Autowired private SecretsRepository secretRepository;
  @Autowired private EntityManager entityManager;
  @MockitoBean private AwsCredentialConnectivityCheck awsCredentialConnectivityCheck;

  private final List<String> committedTenantIds = new ArrayList<>();

  @Nested
  @DisplayName("Get Credential contract")
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
      List<String> authMethods = JsonPath.read(response, "$[*].credential_auth_method");
      assertThat(authMethods)
          .containsExactlyInAnyOrder(
              CredentialSecretReference.CREDENTIAL_AUTH_METHOD.USERNAME_PASSWORD.name(),
              CredentialSecretReference.CREDENTIAL_AUTH_METHOD.HASH.name(),
              CredentialSecretReference.CREDENTIAL_AUTH_METHOD.AWS_ACCESS_KEY.name(),
              CredentialSecretReference.CREDENTIAL_AUTH_METHOD.AWS_ASSUME_ROLE.name(),
              CredentialSecretReference.CREDENTIAL_AUTH_METHOD.AZURE_SERVICE_PRINCIPAL.name(),
              CredentialSecretReference.CREDENTIAL_AUTH_METHOD.AZURE_MANAGED_IDENTITY.name(),
              CredentialSecretReference.CREDENTIAL_AUTH_METHOD.GCP_SERVICE_ACCOUNT.name(),
              CredentialSecretReference.CREDENTIAL_AUTH_METHOD.GCP_OAUTH2.name());
    }

    @Test
    @DisplayName("given_gcpServiceAccountContract_should_exposeScopeDefaultAndFileField")
    void given_gcpServiceAccountContract_should_exposeScopeDefaultAndFileField() throws Exception {
      // Arrange
      Tenant tenant = tenantIsolationTestHelper.createTenantWithCurrentUser("credential-gcp-ct");
      String uri = tenantCredentialsUri(tenant.getId()) + "/contracts";

      // Act
      String response =
          mvc.perform(get(uri).accept(MediaType.APPLICATION_JSON))
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString();

      // Assert
      String fieldPath =
          "$[?(@.credential_auth_method == 'GCP_SERVICE_ACCOUNT')].fields[?(@.field_name == '%s')]";
      List<String> scopeDefaults =
          JsonPath.read(response, String.format(fieldPath, "gcp_scope") + ".default_value");
      assertThat(scopeDefaults).containsExactly(GCP_DEFAULT_SCOPE);
      List<String> keyFieldTypes =
          JsonPath.read(response, String.format(fieldPath, "gcp_private_key_json") + ".field_type");
      assertThat(keyFieldTypes).containsExactly("file");
    }
  }

  @Nested
  @DisplayName("Search")
  class SearchCredentials {

    @Test
    @DisplayName("given_twoTenantsCredentials_should_returnOnlyRequestedTenantCredentials")
    void given_twoTenantsCredentials_should_returnOnlyRequestedTenantCredentials()
        throws Exception {
      // Arrange
      Tenant tenantA = tenantIsolationTestHelper.createTenantWithCurrentUser("credential-search-a");
      Tenant tenantB = tenantIsolationTestHelper.createTenantWithCurrentUser("credential-search-b");

      String tenantACredentialId1 =
          credentialSecretReferenceRepository
              .save(CredentialFixture.createDefaultUsernameCredentialReference(tenantA))
              .getId();
      String tenantACredentialId2 =
          credentialSecretReferenceRepository
              .save(CredentialFixture.createDefaultUsernameCredentialReference(tenantA))
              .getId();
      String tenantBCredentialId =
          credentialSecretReferenceRepository
              .save(CredentialFixture.createDefaultUsernameCredentialReference(tenantB))
              .getId();

      // Act
      String responseA =
          mvc.perform(
                  post(tenantCredentialsUri(tenantA.getId()) + "/search")
                      .with(csrf())
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(asJsonString(new SearchPaginationInput()))
                      .accept(MediaType.APPLICATION_JSON))
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString();

      // Assert
      List<String> tenantAIds = JsonPath.read(responseA, "$.content[*].credential_id");
      assertThat(tenantAIds)
          .containsExactlyInAnyOrder(tenantACredentialId1, tenantACredentialId2)
          .doesNotContain(tenantBCredentialId);
    }

    @Test
    @DisplayName("given_filters_should_filterByTypeAuthMethodTagsAndCreatedBy")
    void given_filters_should_filterByTypeAuthMethodTagsAndCreatedBy() throws Exception {
      // Arrange
      Tenant tenant = tenantIsolationTestHelper.createTenantWithCurrentUser("credential-filter");
      Tag matchTag = TagFixture.getTagWithText("credential-filter-match");
      Tag otherTag = TagFixture.getTagWithText("credential-filter-other");
      tagRepository.saveAll(List.of(matchTag, otherTag));

      User matchUser = UserFixture.getUserWithDefaultEmail();
      User otherUser = UserFixture.getUserWithDefaultEmail();
      userRepository.saveAll(List.of(matchUser, otherUser));

      CredentialSecretReference hashReferenceThatMatch =
          CredentialFixture.createDefaultHashCredential(tenant);
      hashReferenceThatMatch.setTags(Set.of(matchTag));
      hashReferenceThatMatch.setCreatedBy(matchUser);

      CredentialSecretReference hashReferenceThatDoNotMatch =
          CredentialFixture.createDefaultHashCredential(tenant);
      hashReferenceThatDoNotMatch.setTags(Set.of(otherTag));
      hashReferenceThatDoNotMatch.setCreatedBy(matchUser);

      CredentialSecretReference hashReferenceThatDoNotMatch2 =
          CredentialFixture.createDefaultHashCredential(tenant);
      hashReferenceThatDoNotMatch2.setTags(Set.of(matchTag));
      hashReferenceThatDoNotMatch2.setCreatedBy(otherUser);

      CredentialSecretReference passwordReferenceThatDoNotMatch =
          CredentialFixture.createDefaultUsernameCredentialReference(tenant);
      passwordReferenceThatDoNotMatch.setTags(Set.of(matchTag));
      passwordReferenceThatDoNotMatch.setCreatedBy(matchUser);
      credentialSecretReferenceRepository.saveAll(
          List.of(
              hashReferenceThatMatch,
              hashReferenceThatDoNotMatch,
              hashReferenceThatDoNotMatch2,
              passwordReferenceThatDoNotMatch));

      SearchPaginationInput input = new SearchPaginationInput();
      input.setFilterGroup(
          Filters.FilterGroup.filterGroupWithFilters(
              List.of(
                  filter("credential_type", "IDENTITY"),
                  filter("credential_auth_method", "HASH"),
                  filter("credential_tags_ids", matchTag.getId()),
                  filter("credential_created_by", matchUser.getId()))));

      // Act
      String response =
          mvc.perform(
                  post(tenantCredentialsUri(tenant.getId()) + "/search")
                      .with(csrf())
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(asJsonString(input))
                      .accept(MediaType.APPLICATION_JSON))
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString();

      List<String> ids = JsonPath.read(response, "$.content[*].credential_id");
      assertThat(ids)
          .containsExactly(hashReferenceThatMatch.getId())
          .doesNotContain(
              hashReferenceThatDoNotMatch.getId(),
              hashReferenceThatDoNotMatch2.getId(),
              passwordReferenceThatDoNotMatch.getId());
    }

    @Test
    @DisplayName("given_textSearch_should_searchByCredentialName")
    void given_textSearch_should_searchByCredentialName() throws Exception {
      // Arrange
      Tenant tenant =
          tenantIsolationTestHelper.createTenantWithCurrentUser("credential-search-name");
      String matchCredential1 =
          credentialSecretReferenceRepository
              .save(CredentialFixture.createDefaultUsernameCredentialReference("match-01", tenant))
              .getId();
      String otherCredential =
          credentialSecretReferenceRepository
              .save(CredentialFixture.createDefaultUsernameCredentialReference("test", tenant))
              .getId();

      SearchPaginationInput input = new SearchPaginationInput();
      input.setTextSearch("match-01");

      // Act
      String response =
          mvc.perform(
                  post(tenantCredentialsUri(tenant.getId()) + "/search")
                      .with(csrf())
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(asJsonString(input))
                      .accept(MediaType.APPLICATION_JSON))
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString();

      // Assert
      List<String> ids = JsonPath.read(response, "$.content[*].credential_id");
      assertThat(ids).containsExactlyInAnyOrder(matchCredential1).doesNotContain(otherCredential);
    }
  }

  @Nested
  @DisplayName("Create")
  class CreateCredential {

    @Test
    @DisplayName("given_validInput_should_createCredentialInRequestedTenant")
    void given_validInput_should_createCredentialInRequestedTenant() throws Exception {
      // Arrange
      Tenant tenant = createCommittedTenantWithCurrentUser("credential-create-a");
      String tenantId = tenant.getId();
      String uniqueSuffix = UUID.randomUUID().toString().substring(0, 8);
      String credentialName = "cred-a-" + uniqueSuffix;

      // Act
      CredentialInput input =
          new CredentialInput(
              credentialName,
              CredentialSecretReference.CREDENTIAL_TYPE.IDENTITY,
              CredentialSecretReference.CREDENTIAL_AUTH_METHOD.USERNAME_PASSWORD,
              "description-cred-a",
              "user-a",
              "pass-a",
              null,
              null,
              List.of());

      String response =
          mvc.perform(
                  multipartCreate(tenantCredentialsUri(tenantA.getId()), input)
                      .with(csrf())
                      .accept(MediaType.APPLICATION_JSON))
              .andExpect(status().is2xxSuccessful())
              .andReturn()
              .getResponse()
              .getContentAsString();

      // Assert
      CredentialSecretReference credentialSecretReference =
          credentialSecretReferenceRepository
              .findById(JsonPath.read(response, "$.credential_id"))
              .orElseThrow();
      assertThat(credentialSecretReference.getTenant().getId()).isEqualTo(tenantId);
      assertThat(credentialSecretReference.getName()).isEqualTo(credentialName);

      Secret secret =
          secretRepository.findById(credentialSecretReference.getLocation()).orElseThrow();
      assertThat(secret).isInstanceOf(UsernamePasswordSecret.class);
      assertThat(secret.getTenant().getId()).isEqualTo(tenantId);
      UsernamePasswordSecret usernamePasswordSecret = (UsernamePasswordSecret) secret;
      assertThat(usernamePasswordSecret.getUsername()).isEqualTo("user-a");
    }

    @Test
    @DisplayName(
        "given_awsAccessKeyCredential_when_created_should_runConnectivityCheckAndReturnUpdatedStatus")
    void
        given_awsAccessKeyCredential_when_created_should_runConnectivityCheckAndReturnUpdatedStatus()
            throws Exception {
      // Arrange
      Tenant tenant = createCommittedTenantWithCurrentUser("credential-create-aws-ak");
      CredentialInput input = awsAccessKeyInput("aws-ak-create");
      when(awsCredentialConnectivityCheck.validateAccessKey(
              eq(AWS_DEFAULT_REGION), eq(AWS_ACCESS_KEY_ID), eq(AWS_SECRET_ACCESS_KEY), eq(null)))
          .thenReturn(SecretConnectionResult.active());

      // Act
      String response =
          mvc.perform(
                  post(tenantCredentialsUri(tenant.getId()))
                      .with(csrf())
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(asJsonString(input))
                      .accept(MediaType.APPLICATION_JSON))
              .andExpect(status().is2xxSuccessful())
              .andReturn()
              .getResponse()
              .getContentAsString();

      // Assert
      verify(awsCredentialConnectivityCheck)
          .validateAccessKey(AWS_DEFAULT_REGION, AWS_ACCESS_KEY_ID, AWS_SECRET_ACCESS_KEY, null);
      assertThatJson(response).node("credential_status").isEqualTo(ACTIVE.name());
      assertThatJson(response).node("credential_last_verified_at").isPresent();

      CredentialSecretReference persisted =
          credentialSecretReferenceRepository
              .findById(JsonPath.read(response, "$.credential_id"))
              .orElseThrow();
      assertThat(persisted.getStatus()).isEqualTo(ACTIVE);
      assertThat(persisted.getLastVerifiedAt()).isNotNull();
    }

    @Test
    @DisplayName(
        "given_awsAssumeRoleCredential_when_created_should_runConnectivityCheckAndReturnFailureStatus")
    void
        given_awsAssumeRoleCredential_when_created_should_runConnectivityCheckAndReturnFailureStatus()
            throws Exception {
      // Arrange
      Tenant tenant = createCommittedTenantWithCurrentUser("credential-create-aws-assume");
      CredentialInput input = awsAssumeRoleInput("aws-assume-create");
      when(awsCredentialConnectivityCheck.validateAssumeRole(
              eq(AWS_DEFAULT_REGION),
              eq(AWS_ROLE_ARN),
              eq(AWS_EXTERNAL_ID),
              eq(STATIC_ACCESS_KEY),
              eq(AWS_SOURCE_PROFILE_ACCESS_KEY_ID),
              eq(AWS_SOURCE_PROFILE_SECRET_ACCESS_KEY)))
          .thenReturn(SecretConnectionResult.authFailed());

      // Act
      String response =
          mvc.perform(
                  post(tenantCredentialsUri(tenant.getId()))
                      .with(csrf())
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(asJsonString(input))
                      .accept(MediaType.APPLICATION_JSON))
              .andExpect(status().is2xxSuccessful())
              .andReturn()
              .getResponse()
              .getContentAsString();

      // Assert
      verify(awsCredentialConnectivityCheck)
          .validateAssumeRole(
              AWS_DEFAULT_REGION,
              AWS_ROLE_ARN,
              AWS_EXTERNAL_ID,
              STATIC_ACCESS_KEY,
              AWS_SOURCE_PROFILE_ACCESS_KEY_ID,
              AWS_SOURCE_PROFILE_SECRET_ACCESS_KEY);
      assertThatJson(response).node("credential_status").isEqualTo(AUTH_FAILED.name());
      assertThatJson(response).node("credential_last_verified_at").isPresent();

      CredentialSecretReference persisted =
          credentialSecretReferenceRepository
              .findById(JsonPath.read(response, "$.credential_id"))
              .orElseThrow();
      assertThat(persisted.getStatus()).isEqualTo(AUTH_FAILED);
      assertThat(persisted.getLastVerifiedAt()).isNotNull();
    }

    @Test
    @DisplayName("given_hashCredentialWithoutHash_should_failCreation")
    void given_hashCredentialWithoutHash_should_failCreation() throws Exception {
      // Arrange
      Tenant tenant = createCommittedTenantWithCurrentUser("credential-post-hash-no-hash");
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
      String errorResponse =
          mvc.perform(multipartCreate(tenantCredentialsUri(tenant.getId()), input))
              .andExpect(status().isBadRequest())
              .andReturn()
              .getResponse()
              .getContentAsString();

      assertThat(errorResponse).isNotBlank();
      assertThat(errorResponse).containsIgnoringCase("hash");
    }

    @Test
    @DisplayName("given_hashCredentialWithoutHashAlgorithm_should_failCreation")
    void given_hashCredentialWithoutHashAlgorithm_should_failCreation() throws Exception {
      // Arrange
      Tenant tenant = createCommittedTenantWithCurrentUser("credential-post-hash-no-algo");
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
      String errorResponse =
          mvc.perform(multipartCreate(tenantCredentialsUri(tenant.getId()), input))
              .andExpect(status().isBadRequest())
              .andReturn()
              .getResponse()
              .getContentAsString();

      assertThat(errorResponse).isNotBlank();
      assertThat(errorResponse).containsIgnoringCase("Hash algorithm");
    }

    @Test
    @DisplayName("given_usernameCredentialWithoutUsername_should_failCreation")
    void given_usernameCredentialWithoutUsername_should_failCreation() throws Exception {
      // Arrange
      Tenant tenant = createCommittedTenantWithCurrentUser("credential-post-up-no-user");
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
      String errorResponse =
          mvc.perform(multipartCreate(tenantCredentialsUri(tenant.getId()), input))
              .andExpect(status().isBadRequest())
              .andReturn()
              .getResponse()
              .getContentAsString();

      assertThat(errorResponse).isNotBlank();
      assertThat(errorResponse).containsIgnoringCase("username");
    }

    @Test
    @DisplayName("given_usernameCredentialWithoutPassword_should_failCreation")
    void given_usernameCredentialWithoutPassword_should_failCreation() throws Exception {
      // Arrange
      Tenant tenant = createCommittedTenantWithCurrentUser("credential-post-up-no-pass");
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
      String errorResponse =
          mvc.perform(multipartCreate(tenantCredentialsUri(tenant.getId()), input))
              .andExpect(status().isBadRequest())
              .andReturn()
              .getResponse()
              .getContentAsString();

      assertThat(errorResponse).isNotBlank();
      assertThat(errorResponse).containsIgnoringCase("password");
    }

    @Test
    @DisplayName("given_azureServicePrincipalInput_should_createAzureServicePrincipalSecret")
    void given_azureServicePrincipalInput_should_createAzureServicePrincipalSecret()
        throws Exception {
      // Arrange
      Tenant tenant = createCommittedTenantWithCurrentUser("credential-azure-sp");
      CredentialInput input = CredentialInputFixture.azureServicePrincipalInput("azure-sp");

      // Act
      String response =
          mvc.perform(
                  multipartCreate(tenantCredentialsUri(tenant.getId()), input)
                      .accept(MediaType.APPLICATION_JSON))
              .andExpect(status().is2xxSuccessful())
              .andReturn()
              .getResponse()
              .getContentAsString();

      // Assert
      CredentialSecretReference credential =
          credentialSecretReferenceRepository
              .findById(JsonPath.read(response, "$.credential_id"))
              .orElseThrow();
      assertThat(credential.getCredentialType())
          .isEqualTo(CredentialSecretReference.CREDENTIAL_TYPE.CLOUD_AZURE);

      Secret secret = secretRepository.findById(credential.getLocation()).orElseThrow();
      assertThat(secret).isInstanceOf(AzureServicePrincipalSecret.class);
      assertThat(secret.getTenant().getId()).isEqualTo(tenant.getId());
      AzureServicePrincipalSecret azureSecret = (AzureServicePrincipalSecret) secret;
      assertThat(azureSecret.getAzureEnvironment()).isEqualTo(AZURE_ENVIRONMENT);
      assertThat(azureSecret.getAzureClientId()).isEqualTo(AZURE_CLIENT_ID);
      assertThat(azureSecret.getAzureTenantId()).isEqualTo(AZURE_TENANT_ID);
      // The client secret is stored encrypted, never in clear text
      assertThat(azureSecret.getAzureClientSecret()).isNotEqualTo(AZURE_CLIENT_SECRET);
    }

    @Test
    @DisplayName("given_azureManagedIdentityInput_should_createAzureManagedIdentitySecret")
    void given_azureManagedIdentityInput_should_createAzureManagedIdentitySecret()
        throws Exception {
      // Arrange
      Tenant tenant = createCommittedTenantWithCurrentUser("credential-azure-mi");
      CredentialInput input =
          CredentialInputFixture.azureSystemAssignedManagedIdentityInput("azure-mi");

      // Act
      String response =
          mvc.perform(
                  multipartCreate(tenantCredentialsUri(tenant.getId()), input)
                      .accept(MediaType.APPLICATION_JSON))
              .andExpect(status().is2xxSuccessful())
              .andReturn()
              .getResponse()
              .getContentAsString();

      // Assert
      CredentialSecretReference credential =
          credentialSecretReferenceRepository
              .findById(JsonPath.read(response, "$.credential_id"))
              .orElseThrow();
      Secret secret = secretRepository.findById(credential.getLocation()).orElseThrow();
      assertThat(secret).isInstanceOf(AzureManagedIdentitySecret.class);
      AzureManagedIdentitySecret azureSecret = (AzureManagedIdentitySecret) secret;
      assertThat(azureSecret.getAzureEnvironment()).isEqualTo(AZURE_ENVIRONMENT);
      assertThat(azureSecret.getAzureClientId()).isNull();
    }

    @Test
    @DisplayName("given_azureCredentialWithoutEnvironment_should_failCreation")
    void given_azureCredentialWithoutEnvironment_should_failCreation() throws Exception {
      // Arrange
      Tenant tenant = createCommittedTenantWithCurrentUser("credential-azure-no-env");
      CredentialInput input =
          CredentialInputFixture.azureInput(
              "azure-no-env",
              CredentialSecretReference.CREDENTIAL_AUTH_METHOD.AZURE_MANAGED_IDENTITY,
              null,
              null,
              null,
              null,
              null);

      // Act & Assert
      String errorResponse =
          mvc.perform(multipartCreate(tenantCredentialsUri(tenant.getId()), input))
              .andExpect(status().isBadRequest())
              .andReturn()
              .getResponse()
              .getContentAsString();

      assertThat(errorResponse).containsIgnoringCase("Azure environment");
    }

    @Test
    @DisplayName("given_azureCredentialWithUnsupportedEnvironment_should_failCreation")
    void given_azureCredentialWithUnsupportedEnvironment_should_failCreation() throws Exception {
      // Arrange
      Tenant tenant = createCommittedTenantWithCurrentUser("credential-azure-bad-env");
      CredentialInput input =
          CredentialInputFixture.azureInput(
              "azure-bad-env",
              CredentialSecretReference.CREDENTIAL_AUTH_METHOD.AZURE_MANAGED_IDENTITY,
              "NotAnAzureCloud",
              null,
              null,
              null,
              null);

      // Act & Assert
      String errorResponse =
          mvc.perform(multipartCreate(tenantCredentialsUri(tenant.getId()), input))
              .andExpect(status().isBadRequest())
              .andReturn()
              .getResponse()
              .getContentAsString();

      assertThat(errorResponse).containsIgnoringCase("Unsupported Azure environment");
    }

    @Test
    @DisplayName("given_oversizedGcpKeyFile_should_failCreation")
    void given_oversizedGcpKeyFile_should_failCreation() throws Exception {
      // Arrange
      Tenant tenant =
          tenantIsolationTestHelper.createTenantWithCurrentUser("credential-gcp-big-key");
      CredentialInput input = CredentialInputFixture.gcpServiceAccountInput("gcp-big-key");
      byte[] oversizedKey = new byte[(int) MAX_GCP_PRIVATE_KEY_SIZE_BYTES + 1];

      // Act: the size guard runs before the payload ever reaches a handler
      String errorResponse =
          mvc.perform(multipartCreate(tenantCredentialsUri(tenant.getId()), input, oversizedKey))
              .andExpect(status().isBadRequest())
              .andReturn()
              .getResponse()
              .getContentAsString();

      // Assert
      assertThat(errorResponse).containsIgnoringCase("key file");
    }

    @Test
    @DisplayName("given_gcpTypeWithAzureAuthMethod_should_failCreation")
    void given_gcpTypeWithAzureAuthMethod_should_failCreation() throws Exception {
      // Arrange
      Tenant tenant =
          tenantIsolationTestHelper.createTenantWithCurrentUser("credential-gcp-bad-method");
      CredentialInput input =
          CredentialInputFixture.gcpInput(
              "gcp-bad-method",
              CredentialSecretReference.CREDENTIAL_AUTH_METHOD.AZURE_SERVICE_PRINCIPAL,
              GCP_SCOPE,
              GCP_PROJECT_ID,
              GCP_OAUTH_CLIENT_ID,
              GCP_OAUTH_CLIENT_SECRET,
              GCP_OAUTH_REFRESH_TOKEN);

      // Act & Assert
      mvc.perform(
              multipartCreate(
                  tenantCredentialsUri(tenant.getId()), input, gcpPrivateKeyJsonBytes()))
          .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("given_azureTypeWithAwsAuthMethod_should_failCreation")
    void given_azureTypeWithAwsAuthMethod_should_failCreation() throws Exception {
      // Arrange
      Tenant tenant = createCommittedTenantWithCurrentUser("credential-azure-bad-method");
      CredentialInput input =
          CredentialInputFixture.azureInput(
              "azure-bad-method",
              CredentialSecretReference.CREDENTIAL_AUTH_METHOD.AWS_ACCESS_KEY,
              AZURE_ENVIRONMENT,
              null,
              null,
              null,
              null);

      // Act & Assert
      mvc.perform(multipartCreate(tenantCredentialsUri(tenant.getId()), input))
          .andExpect(status().isBadRequest());
    }
  }

  @Nested
  @DisplayName("Get Credential")
  class GetCredential {

    @Test
    @DisplayName("given_credential_should_ReturnIt")
    void given_credential_should_ReturnIt() throws Exception {
      // Arrange
      Tenant tenant = tenantIsolationTestHelper.createTenantWithCurrentUser("credential-delete");

      UsernamePasswordSecret initialSecret = new UsernamePasswordSecret();
      initialSecret.setTenant(tenant);
      initialSecret.setUsername("user");
      initialSecret.setPassword("pass");
      Secret secret = secretRepository.save(initialSecret);

      CredentialSecretReference initialPasswordReference =
          CredentialFixture.createDefaultUsernameCredentialReference(tenant);
      initialPasswordReference.setLocation(secret.getId());
      initialPasswordReference.setDescription("Initial description");
      initialPasswordReference.setConnectorInstanceId(LOCAL_SECRETS_PROVIDER_ID);
      CredentialSecretReference credentialReference =
          credentialSecretReferenceRepository.save(initialPasswordReference);
      commitArrangeAndStartFreshTransaction();

      // Act
      String ownTenantResponse =
          mvc.perform(get(tenantCredentialsUri(tenant.getId()) + "/" + credentialReference.getId()))
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString();

      // Assert
      assertThatJson(ownTenantResponse)
          .node("credential_id")
          .isEqualTo(credentialReference.getId());
      assertThatJson(ownTenantResponse)
          .node("credential_auth_method")
          .isEqualTo(CredentialSecretReference.CREDENTIAL_AUTH_METHOD.USERNAME_PASSWORD.name());
      assertThatJson(ownTenantResponse).node("credential_hash").isAbsent();
      assertThatJson(ownTenantResponse).node("credential_username").isEqualTo("user");
      assertThatJson(ownTenantResponse).node("credential_password").isAbsent();
    }

    @Test
    @DisplayName("given_azureCredential_should_returnNonSensitiveFieldsOnly")
    void given_azureCredential_should_returnNonSensitiveFieldsOnly() throws Exception { // Arrange
      Tenant tenant = createCommittedTenantWithCurrentUser("credential-azure-get");
      String credentialId =
          JsonPath.read(
              mvc.perform(
                      multipartCreate(
                              tenantCredentialsUri(tenant.getId()),
                              CredentialInputFixture.azureServicePrincipalInput("azure-get"))
                          .accept(MediaType.APPLICATION_JSON))
                  .andExpect(status().is2xxSuccessful())
                  .andReturn()
                  .getResponse()
                  .getContentAsString(),
              "$.credential_id");

      // Act
      String response =
          mvc.perform(get(tenantCredentialsUri(tenant.getId()) + "/" + credentialId))
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString();

      // Assert: those identifiers are not credentials, the form needs them to prefill
      assertThatJson(response).node("credential_azure_environment").isEqualTo(AZURE_ENVIRONMENT);
      assertThatJson(response).node("credential_azure_client_id").isEqualTo(AZURE_CLIENT_ID);
      assertThatJson(response).node("credential_azure_tenant_id").isEqualTo(AZURE_TENANT_ID);
      assertThatJson(response)
          .node("credential_azure_subscription_id")
          .isEqualTo(AZURE_SUBSCRIPTION_ID);
      // The client secret must never travel back to the client, even encrypted
      assertThat(response).doesNotContain(AZURE_CLIENT_SECRET);
    }
  }

  @Nested
  @DisplayName("Update Credential")
  class UpdateCredential {

    @Test
    @DisplayName("given_existingCredential_should_updateMetadataAndSecret")
    void given_existingCredential_should_updateMetadataAndSecret() throws Exception {
      // Arrange
      Tenant tenant = createCommittedTenantWithCurrentUser("credential-update");
      tenantIsolationTestHelper.switchToTenantNoFlush(tenant.getId());

      UsernamePasswordSecret initialSecret = new UsernamePasswordSecret();
      initialSecret.setTenant(tenant);
      initialSecret.setUsername("initial-user");
      initialSecret.setPassword("initial-pass");
      Secret secret = secretRepository.save(initialSecret);

      CredentialSecretReference initialPasswordReference =
          CredentialFixture.createDefaultUsernameCredentialReference(tenant);
      initialPasswordReference.setLocation(secret.getId());
      initialPasswordReference.setDescription("Initial description");
      initialPasswordReference.setConnectorInstanceId(LOCAL_SECRETS_PROVIDER_ID);
      CredentialSecretReference credentialReference =
          credentialSecretReferenceRepository.save(initialPasswordReference);
      commitArrangeAndStartFreshTransaction();

      CredentialInput updateInput =
          new CredentialInput(
              "after-update",
              CredentialSecretReference.CREDENTIAL_TYPE.IDENTITY,
              CredentialSecretReference.CREDENTIAL_AUTH_METHOD.USERNAME_PASSWORD,
              "description-after-update",
              "after-user",
              "after-pass",
              null,
              null,
              List.of());

      // Act
      mvc.perform(
              multipartUpdate(
                  tenantCredentialsUri(tenant.getId()) + "/" + credentialReference.getId(),
                  updateInput))
          .andExpect(status().is2xxSuccessful());

      // Assert
      CredentialSecretReference updatedCredential =
          credentialSecretReferenceRepository.findById(credentialReference.getId()).orElseThrow();
      assertThat(updatedCredential.getName()).isEqualTo("after-update");
      assertThat(updatedCredential.getCredentialAuthMethod())
          .isEqualTo(CredentialSecretReference.CREDENTIAL_AUTH_METHOD.USERNAME_PASSWORD);
      assertThat(updatedCredential.getLocation()).isEqualTo(initialSecret.getId());

      Secret updatedSecret =
          secretRepository.findById(updatedCredential.getLocation()).orElseThrow();
      assertThat(updatedSecret).isInstanceOf(UsernamePasswordSecret.class);
      assertThat(((UsernamePasswordSecret) updatedSecret).getUsername()).isEqualTo("after-user");
    }

    @Test
    @DisplayName(
        "given_existingCredential_when_updatedToAwsAccessKey_should_runConnectivityCheckAndReturnUpdatedStatus")
    void
        given_existingCredential_when_updatedToAwsAccessKey_should_runConnectivityCheckAndReturnUpdatedStatus()
            throws Exception {
      // Arrange
      Tenant tenant = createCommittedTenantWithCurrentUser("credential-update-aws-ak");

      String credentialId =
          JsonPath.read(
              mvc.perform(
                      post(tenantCredentialsUri(tenant.getId()))
                          .with(csrf())
                          .contentType(MediaType.APPLICATION_JSON)
                          .content(asJsonString(validUsernamePasswordInput("before-aws-ak-update")))
                          .accept(MediaType.APPLICATION_JSON))
                  .andExpect(status().is2xxSuccessful())
                  .andReturn()
                  .getResponse()
                  .getContentAsString(),
              "$.credential_id");

      CredentialInput updateInput = awsAccessKeyInput("aws-ak-update");
      when(awsCredentialConnectivityCheck.validateAccessKey(
              eq(AWS_DEFAULT_REGION), eq(AWS_ACCESS_KEY_ID), eq(AWS_SECRET_ACCESS_KEY), eq(null)))
          .thenReturn(SecretConnectionResult.timeout());

      // Act
      String response =
          mvc.perform(
                  put(tenantCredentialsUri(tenant.getId()) + "/" + credentialId)
                      .with(csrf())
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(asJsonString(updateInput))
                      .accept(MediaType.APPLICATION_JSON))
              .andExpect(status().is2xxSuccessful())
              .andReturn()
              .getResponse()
              .getContentAsString();

      // Assert
      verify(awsCredentialConnectivityCheck)
          .validateAccessKey(AWS_DEFAULT_REGION, AWS_ACCESS_KEY_ID, AWS_SECRET_ACCESS_KEY, null);
      assertThatJson(response).node("credential_status").isEqualTo(TIMEOUT.name());
      assertThatJson(response).node("credential_last_verified_at").isPresent();

      CredentialSecretReference persisted =
          credentialSecretReferenceRepository.findById(credentialId).orElseThrow();
      assertThat(persisted.getStatus()).isEqualTo(TIMEOUT);
      assertThat(persisted.getLastVerifiedAt()).isNotNull();
    }

    @Test
    @DisplayName("given_hashCredential_when_updatingToUsernamePassword_should_replaceHashSecret")
    void given_usernameCredential_when_updatingToHash_should_replaceUsernameSecret()
        throws Exception {
      // Arrange
      Tenant tenant = createCommittedTenantWithCurrentUser("credential-update");
      tenantIsolationTestHelper.switchToTenantNoFlush(tenant.getId());

      UsernamePasswordSecret initialSecret = new UsernamePasswordSecret();
      initialSecret.setTenant(tenant);
      initialSecret.setUsername("initial-user");
      initialSecret.setPassword("initial-pass");
      Secret secret = secretRepository.save(initialSecret);

      CredentialSecretReference initialPasswordReference =
          CredentialFixture.createDefaultUsernameCredentialReference(tenant);
      initialPasswordReference.setLocation(secret.getId());
      initialPasswordReference.setDescription("Initial description");
      initialPasswordReference.setConnectorInstanceId(LOCAL_SECRETS_PROVIDER_ID);
      CredentialSecretReference credentialReference =
          credentialSecretReferenceRepository.save(initialPasswordReference);
      commitArrangeAndStartFreshTransaction();

      CredentialInput updateInput =
          new CredentialInput(
              "after-update",
              CredentialSecretReference.CREDENTIAL_TYPE.IDENTITY,
              CredentialSecretReference.CREDENTIAL_AUTH_METHOD.HASH,
              "description-after-update",
              null,
              null,
              HashSecret.HASH_ALGORITHM.SHA,
              "hash-secret",
              List.of());

      // Act
      mvc.perform(
              multipartUpdate(
                  tenantCredentialsUri(tenant.getId()) + "/" + credentialReference.getId(),
                  updateInput))
          .andExpect(status().is2xxSuccessful());

      CredentialSecretReference updatedCredential =
          credentialSecretReferenceRepository.findById(credentialReference.getId()).orElseThrow();
      assertThat(updatedCredential.getName()).isEqualTo("after-update");
      assertThat(updatedCredential.getCredentialAuthMethod())
          .isEqualTo(CredentialSecretReference.CREDENTIAL_AUTH_METHOD.HASH);
      assertThat(updatedCredential.getLocation()).isNotEqualTo(secret.getId());

      assertThat(secretRepository.findById(secret.getId())).isEmpty();

      Secret updatedSecret =
          secretRepository.findById(updatedCredential.getLocation()).orElseThrow();
      assertThat(updatedSecret).isInstanceOf(HashSecret.class);
      assertThat(((HashSecret) updatedSecret).getHashAlgorithm())
          .isEqualTo(HashSecret.HASH_ALGORITHM.SHA);
    }

    @Test
    @DisplayName(
        "given_usernameCredential_when_updatingToHashWithoutHashAlgorithm_should_throwError")
    void given_usernameCredential_when_updatingToHashWithoutHashAlgorithm_should_throwError()
        throws Exception {
      // Arrange
      Tenant tenant = createCommittedTenantWithCurrentUser("credential-update");
      tenantIsolationTestHelper.switchToTenantNoFlush(tenant.getId());

      UsernamePasswordSecret initialSecret = new UsernamePasswordSecret();
      initialSecret.setTenant(tenant);
      initialSecret.setUsername("initial-user");
      initialSecret.setPassword("initial-pass");
      Secret secret = secretRepository.save(initialSecret);

      CredentialSecretReference initialPasswordReference =
          CredentialFixture.createDefaultUsernameCredentialReference(tenant);
      initialPasswordReference.setLocation(secret.getId());
      initialPasswordReference.setDescription("Initial description");
      initialPasswordReference.setConnectorInstanceId(LOCAL_SECRETS_PROVIDER_ID);
      CredentialSecretReference credentialReference =
          credentialSecretReferenceRepository.save(initialPasswordReference);
      commitArrangeAndStartFreshTransaction();

      CredentialInput invalidUpdateInput =
          new CredentialInput(
              "after-update",
              CredentialSecretReference.CREDENTIAL_TYPE.IDENTITY,
              CredentialSecretReference.CREDENTIAL_AUTH_METHOD.HASH,
              "description-after-update",
              null,
              null,
              null,
              "hash-secret",
              List.of());

      // Act
      String errorResponse =
          mvc.perform(
                  multipartUpdate(
                      tenantCredentialsUri(tenant.getId()) + "/" + credentialReference.getId(),
                      invalidUpdateInput))
              .andExpect(status().isBadRequest())
              .andReturn()
              .getResponse()
              .getContentAsString();

      // Assert
      assertThat(errorResponse).containsIgnoringCase("hash algorithm");
    }
  }

  @Nested
  @DisplayName("Delete Credential")
  class DeleteCredential {

    @Test
    @DisplayName("given_existingCredential_should_deleteCredentialAndSecret")
    void given_existingCredential_should_deleteCredentialAndSecret() throws Exception {
      // Arrange
      Tenant tenant = tenantIsolationTestHelper.createTenantWithCurrentUser("credential-delete");

      UsernamePasswordSecret initialSecret = new UsernamePasswordSecret();
      initialSecret.setTenant(tenant);
      initialSecret.setUsername("initial-user");
      initialSecret.setPassword("initial-pass");
      Secret secret = secretRepository.save(initialSecret);

      CredentialSecretReference initialPasswordReference =
          CredentialFixture.createDefaultUsernameCredentialReference(tenant);
      initialPasswordReference.setLocation(secret.getId());
      initialPasswordReference.setDescription("Initial description");
      initialPasswordReference.setConnectorInstanceId(LOCAL_SECRETS_PROVIDER_ID);
      CredentialSecretReference credentialReference =
          credentialSecretReferenceRepository.save(initialPasswordReference);

      // Act
      mvc.perform(
              delete(tenantCredentialsUri(tenant.getId()) + "/" + credentialReference.getId())
                  .with(csrf()))
          .andExpect(status().is2xxSuccessful());

      // Assert
      assertThat(credentialSecretReferenceRepository.findById(credentialReference.getId()))
          .isEmpty();
      assertThat(secretRepository.findById(secret.getId())).isEmpty();
    }
  }

  @Nested
  @DisplayName("Capabilities")
  class CredentialCapabilities {

    private static final String UNKNOWN_CREDENTIAL_ID = "00000000-0000-0000-0000-000000000999";

    @Test
    @DisplayName("given_accessCredentials_should_allowSearch")
    @WithMockUser(withCapabilities = {Capability.ACCESS_CREDENTIALS})
    void given_accessCredentials_should_allowSearch() throws Exception {
      // Arrange
      Tenant tenant =
          createCommittedTenantWithCapabilities(
              "credential-cap-search", Set.of(Capability.ACCESS_CREDENTIALS));

      // Act
      int responseStatus =
          mvc.perform(
                  post(tenantCredentialsUri(tenant.getId()) + "/search")
                      .with(csrf())
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(asJsonString(new SearchPaginationInput()))
                      .accept(MediaType.APPLICATION_JSON))
              .andReturn()
              .getResponse()
              .getStatus();

      // Assert
      assertThat(responseStatus).isEqualTo(200);
    }

    @Test
    @DisplayName("given_manageCredentials_should_allowCreate")
    @WithMockUser(withCapabilities = {Capability.MANAGE_CREDENTIALS})
    void given_manageCredentials_should_allowCreate() throws Exception {
      // Arrange
      Tenant tenant =
          createCommittedTenantWithCapabilities(
              "credential-cap-create", Set.of(Capability.MANAGE_CREDENTIALS));
      CredentialInput input = validUsernamePasswordInput("credential-cap-create");

      // Act
      int responseStatus =
          mvc.perform(
                  multipartCreate(tenantCredentialsUri(tenant.getId()), input)
                      .accept(MediaType.APPLICATION_JSON))
              .andReturn()
              .getResponse()
              .getStatus();

      // Assert
      assertThat(responseStatus).isNotEqualTo(403);
      assertThat(responseStatus).isEqualTo(200);
    }

    @Test
    @DisplayName("given_manageCredentials_should_allowUpdate")
    @WithMockUser(withCapabilities = {Capability.MANAGE_CREDENTIALS})
    void given_manageCredentials_should_allowUpdate() throws Exception {
      // Arrange
      Tenant tenant =
          createCommittedTenantWithCapabilities(
              "credential-cap-update", Set.of(Capability.MANAGE_CREDENTIALS));
      CredentialInput input = validUsernamePasswordInput("credential-cap-update");

      // Act
      int responseStatus =
          mvc.perform(
                  multipartUpdate(
                          tenantCredentialsUri(tenant.getId()) + "/" + UNKNOWN_CREDENTIAL_ID, input)
                      .accept(MediaType.APPLICATION_JSON))
              .andReturn()
              .getResponse()
              .getStatus();

      // Assert
      assertThat(responseStatus).isNotEqualTo(403);
    }

    @Test
    @DisplayName("given_accessCredentials_should_forbidUpdate")
    @WithMockUser(withCapabilities = {Capability.ACCESS_CREDENTIALS})
    void given_accessCredentials_should_forbidUpdate() throws Exception {
      // Arrange
      Tenant tenant =
          createCommittedTenantWithCapabilities(
              "credential-cap-forbid-update", Set.of(Capability.ACCESS_CREDENTIALS));
      CredentialInput input = validUsernamePasswordInput("credential-cap-forbid-update");

      // Act & Assert
      mvc.perform(
              multipartUpdate(
                      tenantCredentialsUri(tenant.getId()) + "/" + UNKNOWN_CREDENTIAL_ID, input)
                  .accept(MediaType.APPLICATION_JSON))
          .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("given_deleteCredentials_should_allowDelete")
    @WithMockUser(withCapabilities = {Capability.DELETE_CREDENTIALS})
    void given_deleteCredentials_should_allowDelete() throws Exception {
      // Arrange
      Tenant tenant =
          createCommittedTenantWithCapabilities(
              "credential-cap-delete", Set.of(Capability.DELETE_CREDENTIALS));

      // Act
      int responseStatus =
          mvc.perform(
                  delete(tenantCredentialsUri(tenant.getId()) + "/" + UNKNOWN_CREDENTIAL_ID)
                      .with(csrf()))
              .andReturn()
              .getResponse()
              .getStatus();

      // Assert
      assertThat(responseStatus).isNotEqualTo(403);
    }

    @Test
    @DisplayName("given_unrelatedCapability_should_forbidCredentialRead")
    @WithMockUser(withCapabilities = {Capability.MANAGE_ASSETS})
    void given_unrelatedCapability_should_forbidCredentialRead() throws Exception {
      // Arrange
      Tenant tenant =
          createCommittedTenantWithCapabilities(
              "credential-cap-forbid-read", Set.of(Capability.MANAGE_ASSETS));

      // Act & Assert
      mvc.perform(
              get(tenantCredentialsUri(tenant.getId()) + "/contracts")
                  .accept(MediaType.APPLICATION_JSON))
          .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("given_unrelatedCapability_should_forbidCredentialWrite")
    @WithMockUser(withCapabilities = {Capability.MANAGE_ASSETS})
    void given_unrelatedCapability_should_forbidCredentialWrite() throws Exception {
      // Arrange
      Tenant tenant =
          createCommittedTenantWithCapabilities(
              "credential-cap-forbid-write", Set.of(Capability.MANAGE_ASSETS));
      CredentialInput input = validUsernamePasswordInput("credential-cap-forbid-write");

      // Act & Assert
      mvc.perform(
              multipartCreate(tenantCredentialsUri(tenant.getId()), input)
                  .accept(MediaType.APPLICATION_JSON))
          .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("given_unrelatedCapability_should_forbidCredentialDelete")
    @WithMockUser(withCapabilities = {Capability.MANAGE_ASSETS})
    void given_unrelatedCapability_should_forbidCredentialDelete() throws Exception {
      // Arrange
      Tenant tenant =
          createCommittedTenantWithCapabilities(
              "credential-cap-forbid-delete", Set.of(Capability.MANAGE_ASSETS));

      // Act & Assert
      mvc.perform(
              delete(tenantCredentialsUri(tenant.getId()) + "/" + UNKNOWN_CREDENTIAL_ID)
                  .with(csrf()))
          .andExpect(status().isForbidden());
    }

    private CredentialInput validUsernamePasswordInput(String name) {
      return new CredentialInput(
          name,
          CredentialSecretReference.CREDENTIAL_TYPE.IDENTITY,
          CredentialSecretReference.CREDENTIAL_AUTH_METHOD.USERNAME_PASSWORD,
          "credential-capability-test",
          "user-" + name,
          "pass-" + name,
          null,
          null,
          List.of());
    }
  }

  @Nested
  @DisplayName("Bulk delete credentials")
  class BulkDeleteCredentials {

    @Test
    @DisplayName("given_idsToProcess_should_deleteOnlySelectedCredentialsAndSecrets")
    void given_idsToProcess_should_deleteOnlySelectedCredentialsAndSecrets() throws Exception {
      // Arrange
      Tenant tenant = tenantIsolationTestHelper.createTenantWithCurrentUser("credential-bulk-ids");
      Persisted first = persistFullCredential(tenant, "bulk-ids-1");
      Persisted second = persistFullCredential(tenant, "bulk-ids-2");
      Persisted kept = persistFullCredential(tenant, "bulk-ids-kept");

      CredentialBulkProcessingInput input = new CredentialBulkProcessingInput();
      input.setCredentialIdsToProcess(List.of(first.credentialId(), second.credentialId()));

      // Act
      String response =
          mvc.perform(
                  delete(tenantCredentialsUri(tenant.getId()))
                      .with(csrf())
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(asJsonString(input))
                      .accept(MediaType.APPLICATION_JSON))
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString();

      // Assert
      List<String> deletedIds = JsonPath.read(response, "$");
      assertThat(deletedIds).containsExactlyInAnyOrder(first.credentialId(), second.credentialId());
      assertThat(credentialSecretReferenceRepository.findById(first.credentialId())).isEmpty();
      assertThat(credentialSecretReferenceRepository.findById(second.credentialId())).isEmpty();
      assertThat(secretRepository.findById(first.secretId())).isEmpty();
      assertThat(secretRepository.findById(second.secretId())).isEmpty();
      // Not selected -> untouched
      assertThat(credentialSecretReferenceRepository.findById(kept.credentialId())).isPresent();
      assertThat(secretRepository.findById(kept.secretId())).isPresent();
    }

    @Test
    @DisplayName("given_searchInputWithIgnore_should_deleteAllExceptIgnored")
    void given_searchInputWithIgnore_should_deleteAllExceptIgnored() throws Exception {
      // Arrange
      Tenant tenant =
          tenantIsolationTestHelper.createTenantWithCurrentUser("credential-bulk-search");
      Persisted first = persistFullCredential(tenant, "bulk-search-1");
      Persisted second = persistFullCredential(tenant, "bulk-search-2");
      Persisted ignored = persistFullCredential(tenant, "bulk-search-ignored");

      // Select-all (empty search) with one exclusion.
      CredentialBulkProcessingInput input = new CredentialBulkProcessingInput();
      input.setSearchPaginationInput(new SearchPaginationInput());
      input.setCredentialIdsToIgnore(List.of(ignored.credentialId()));

      // Act
      String response =
          mvc.perform(
                  delete(tenantCredentialsUri(tenant.getId()))
                      .with(csrf())
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(asJsonString(input))
                      .accept(MediaType.APPLICATION_JSON))
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString();

      // Assert
      List<String> deletedIds = JsonPath.read(response, "$");
      assertThat(deletedIds)
          .containsExactlyInAnyOrder(first.credentialId(), second.credentialId())
          .doesNotContain(ignored.credentialId());
      assertThat(credentialSecretReferenceRepository.findById(ignored.credentialId())).isPresent();
      assertThat(secretRepository.findById(ignored.secretId())).isPresent();
    }

    @Test
    @DisplayName("given_bothIdsAndSearchInput_should_failBadRequest")
    void given_bothIdsAndSearchInput_should_failBadRequest() throws Exception {
      // Arrange
      Tenant tenant =
          tenantIsolationTestHelper.createTenantWithCurrentUser("credential-bulk-invalid");
      Persisted credential = persistFullCredential(tenant, "bulk-invalid");

      CredentialBulkProcessingInput input = new CredentialBulkProcessingInput();
      input.setCredentialIdsToProcess(List.of(credential.credentialId()));
      input.setSearchPaginationInput(new SearchPaginationInput());

      // Act & Assert
      mvc.perform(
              delete(tenantCredentialsUri(tenant.getId()))
                  .with(csrf())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(asJsonString(input)))
          .andExpect(status().isBadRequest());
      // Nothing deleted
      assertThat(credentialSecretReferenceRepository.findById(credential.credentialId()))
          .isPresent();
    }

    @Test
    @DisplayName("given_otherTenantCredentials_should_notBeDeleted")
    void given_otherTenantCredentials_should_notBeDeleted() throws Exception {
      // Arrange
      Tenant tenantA =
          tenantIsolationTestHelper.createTenantWithCurrentUser("credential-bulk-tenant-a");
      Tenant tenantB =
          tenantIsolationTestHelper.createTenantWithCurrentUser("credential-bulk-tenant-b");
      Persisted credentialA = persistFullCredential(tenantA, "bulk-tenant-a");
      Persisted credentialB = persistFullCredential(tenantB, "bulk-tenant-b");

      // Select-all within tenant A's scope.
      CredentialBulkProcessingInput input = new CredentialBulkProcessingInput();
      input.setSearchPaginationInput(new SearchPaginationInput());

      // Act
      String response =
          mvc.perform(
                  delete(tenantCredentialsUri(tenantA.getId()))
                      .with(csrf())
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(asJsonString(input))
                      .accept(MediaType.APPLICATION_JSON))
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString();

      // Assert: only tenant A's credential is deleted; tenant B's is untouched.
      List<String> deletedIds = JsonPath.read(response, "$");
      assertThat(deletedIds)
          .containsExactly(credentialA.credentialId())
          .doesNotContain(credentialB.credentialId());
      assertThat(credentialSecretReferenceRepository.findById(credentialB.credentialId()))
          .isPresent();
      assertThat(secretRepository.findById(credentialB.secretId())).isPresent();
    }

    private Persisted persistFullCredential(Tenant tenant, String name) {
      UsernamePasswordSecret secret = new UsernamePasswordSecret();
      secret.setTenant(tenant);
      secret.setUsername("user-" + name);
      secret.setPassword("pass-" + name);
      Secret savedSecret = secretRepository.save(secret);

      CredentialSecretReference reference =
          CredentialFixture.createDefaultUsernameCredentialReference(name, tenant);
      reference.setLocation(savedSecret.getId());
      reference.setConnectorInstanceId(LOCAL_SECRETS_PROVIDER_ID);
      CredentialSecretReference saved = credentialSecretReferenceRepository.save(reference);
      return new Persisted(saved.getId(), savedSecret.getId());
    }
  }

  @AfterEach
  void cleanupCommittedTenants() {
    if (committedTenantIds.isEmpty()) {
      return;
    }
    tenantIsolationTestHelper.deleteCommittedTenants(committedTenantIds.toArray(String[]::new));
    committedTenantIds.clear();
  }

  private void commitArrangeAndStartFreshTransaction() {
    entityManager.flush();
    entityManager.clear();
    TestTransaction.flagForCommit();
    TestTransaction.end();
    TestTransaction.start();
  }

  private Tenant createCommittedTenantWithCurrentUser(String name) throws Exception {
    Tenant tenant = tenantIsolationTestHelper.createTenantWithCurrentUser(name);
    committedTenantIds.add(tenant.getId());
    commitArrangeAndStartFreshTransaction();
    return tenant;
  }

  private Tenant createCommittedTenantWithCapabilities(String name, Set<Capability> capabilities)
      throws Exception {
    Tenant tenant = tenantIsolationTestHelper.createTenantWithCapabilities(name, capabilities);
    committedTenantIds.add(tenant.getId());
    commitArrangeAndStartFreshTransaction();
    return tenant;
  }

  private record Persisted(String credentialId, String secretId) {}

  private CredentialInput validUsernamePasswordInput(String name) {
    return new CredentialInput(
        name,
        CredentialSecretReference.CREDENTIAL_TYPE.IDENTITY,
        CredentialSecretReference.CREDENTIAL_AUTH_METHOD.USERNAME_PASSWORD,
        "description-" + name,
        "user-" + name,
        "pass-" + name,
        null,
        null,
        List.of());
  }

  private CredentialInput awsAccessKeyInput(String name) {
    return new CredentialInput(
        name,
        CredentialSecretReference.CREDENTIAL_TYPE.CLOUD_AWS,
        CredentialSecretReference.CREDENTIAL_AUTH_METHOD.AWS_ACCESS_KEY,
        "description-" + name,
        // IDENTITY
        null,
        null,
        null,
        null,
        // AWS
        AWS_DEFAULT_REGION,
        AWS_ACCESS_KEY_ID,
        AWS_SECRET_ACCESS_KEY,
        null,
        null,
        null,
        null,
        null,
        null,
        // AZURE
        null,
        null,
        null,
        null,
        null,
        List.of());
  }
  private CredentialInput awsAssumeRoleInput(String name) {
    return new CredentialInput(
        name,
        CredentialSecretReference.CREDENTIAL_TYPE.CLOUD_AWS,
        CredentialSecretReference.CREDENTIAL_AUTH_METHOD.AWS_ASSUME_ROLE,
        "description-" + name,
        // IDENTITY
        null,
        null,
        null,
        null,
        // AWS
        AWS_DEFAULT_REGION,
        null,
        null,
        null,
        AWS_ROLE_ARN,
        AWS_EXTERNAL_ID,
        STATIC_ACCESS_KEY,
        AWS_SOURCE_PROFILE_ACCESS_KEY_ID,
        AWS_SOURCE_PROFILE_SECRET_ACCESS_KEY,
        // AZURE
        null,
        null,
        null,
        null,
        null,
        List.of());
  }

  /**
   * Builds the multipart create request: the credential payload always travels as an {@code input}
   * JSON part, and file-backed fields (only the GCP key so far) as their own part.
   */
  private MockHttpServletRequestBuilder multipartCreate(
      String uri, CredentialInput input, byte[] gcpPrivateKeyJson) {
    MockMultipartHttpServletRequestBuilder builder = multipart(uri);
    builder.file(inputPart(input));
    if (gcpPrivateKeyJson != null) {
      builder.file(
          new MockMultipartFile(
              GCP_PRIVATE_KEY_PART,
              "key.json",
              MediaType.APPLICATION_JSON_VALUE,
              gcpPrivateKeyJson));
    }
    return builder.with(csrf());
  }

  private MockHttpServletRequestBuilder multipartCreate(String uri, CredentialInput input) {
    return multipartCreate(uri, input, null);
  }

  /** Same as {@link #multipartCreate}, forced to PUT: MockMvc's multipart defaults to POST. */
  private MockHttpServletRequestBuilder multipartUpdate(
      String uri, CredentialInput input, byte[] gcpPrivateKeyJson) {
    return multipartCreate(uri, input, gcpPrivateKeyJson)
        .with(
            request -> {
              request.setMethod("PUT");
              return request;
            });
  }

  private MockHttpServletRequestBuilder multipartUpdate(String uri, CredentialInput input) {
    return multipartUpdate(uri, input, null);
  }

  private MockMultipartFile inputPart(CredentialInput input) {
    return new MockMultipartFile(
        "input",
        "",
        MediaType.APPLICATION_JSON_VALUE,
        asJsonString(input).getBytes(StandardCharsets.UTF_8));
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

  private String tenantCredentialsUri(String tenantId) {
    return TENANT_CREDENTIALS_URI.replace("{tenantId}", tenantId);
  }
}
