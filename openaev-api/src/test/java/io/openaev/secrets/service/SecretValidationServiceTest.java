package io.openaev.secrets.service;

import static io.openaev.database.model.CredentialSecretReference.CREDENTIAL_AUTH_METHOD.*;
import static io.openaev.database.model.SecretReference.SECRET_STATUS.*;
import static io.openaev.integration.impl.secrets.local.LocalSecretsProviderIntegration.LOCAL_SECRETS_PROVIDER_ID;
import static io.openaev.secrets.service.SecretValidationService.VALIDATABLE_AUTH_METHODS;
import static org.assertj.core.api.Assertions.assertThat;

import io.openaev.IntegrationTest;
import io.openaev.database.model.CredentialSecretReference;
import io.openaev.database.model.CredentialSecretReference.CREDENTIAL_AUTH_METHOD;
import io.openaev.database.model.SecretReference;
import io.openaev.database.model.SecretReference.SECRET_STATUS;
import io.openaev.database.model.Tenant;
import io.openaev.database.repository.SecretReferenceRepository;
import io.openaev.database.repository.TenantRepository;
import io.openaev.secrets.provider.SecretConnectionResult;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

/**
 * Integration tests for the three phases of the credential status validation.
 *
 * <p>{@code CREDENTIAL_ASSET} must be on: the local secrets provider is spawned only when that
 * preview feature is enabled ({@code LocalSecretsProviderIntegrationFactory#findRelatedInstances}
 * returns nothing otherwise), so without it every probe short-circuits on {@code
 * PROVIDER_NOT_FOUND} and the tests would assert the wrong path.
 */
@TestPropertySource(properties = "openaev.enabled-dev-features=CREDENTIAL_ASSET")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Transactional
@DisplayName("SecretValidationService tests")
class SecretValidationServiceTest extends IntegrationTest {

  private static final Duration REVALIDATE_AFTER = Duration.ofDays(7);
  private static final int LARGE_BUDGET = 100;

  @Autowired private SecretValidationService secretValidationService;
  @Autowired private SecretReferenceRepository secretReferenceRepository;
  @Autowired private TenantRepository tenantRepository;

  private Tenant tenant;

  @BeforeEach
  void setUp() {
    tenant = tenantRepository.findById(Tenant.DEFAULT_TENANT_UUID).orElseThrow();
  }

  @AfterEach
  void cleanup() {
    secretReferenceRepository.deleteAll(
        secretReferenceRepository.findAll().stream()
            .filter(reference -> reference.getName().startsWith(namePrefix()))
            .toList());
  }

  private static String namePrefix() {
    return "credential-validation-";
  }

  /**
   * Seeds a reference directly through the repository: the location deliberately points at no
   * secret, so the prepared probe exercises the local provider's defensive path without needing a
   * real Azure secret. The connector instance is the real local provider, otherwise every probe
   * would short-circuit on {@code PROVIDER_NOT_FOUND} and the tests would assert the wrong path.
   */
  private CredentialSecretReference seedReference(
      CREDENTIAL_AUTH_METHOD authMethod, SECRET_STATUS status, Instant lastVerifiedAt) {
    return seedReference(authMethod, status, lastVerifiedAt, LOCAL_SECRETS_PROVIDER_ID);
  }

  private CredentialSecretReference seedReference(
      CREDENTIAL_AUTH_METHOD authMethod,
      SECRET_STATUS status,
      Instant lastVerifiedAt,
      String connectorInstanceId) {
    CredentialSecretReference reference = new CredentialSecretReference();
    reference.setName(namePrefix() + UUID.randomUUID());
    reference.setConnectorInstanceId(connectorInstanceId);
    reference.setCredentialType(authMethodToType(authMethod));
    reference.setCredentialAuthMethod(authMethod);
    reference.setStatus(status);
    reference.setLastVerifiedAt(lastVerifiedAt);
    reference.setTenant(tenant);
    return secretReferenceRepository.save(reference);
  }

  private static CredentialSecretReference.CREDENTIAL_TYPE authMethodToType(
      CREDENTIAL_AUTH_METHOD authMethod) {
    return switch (authMethod) {
      case AZURE_SERVICE_PRINCIPAL, AZURE_MANAGED_IDENTITY ->
          CredentialSecretReference.CREDENTIAL_TYPE.CLOUD_AZURE;
      case AWS_ACCESS_KEY, AWS_ASSUME_ROLE -> CredentialSecretReference.CREDENTIAL_TYPE.CLOUD_AWS;
      case USERNAME_PASSWORD, HASH -> CredentialSecretReference.CREDENTIAL_TYPE.IDENTITY;
    };
  }

  private List<CredentialSecretReference> dueCandidates(int maxPerRun) {
    return secretValidationService.findDueForValidation(maxPerRun, REVALIDATE_AFTER);
  }

  private List<String> dueReferenceIds(int maxPerRun) {
    return dueCandidates(maxPerRun).stream().map(CredentialSecretReference::getId).toList();
  }

  @Nested
  @DisplayName("Selecting the credentials due for verification")
  class FindDueForValidation {

    @Test
    @DisplayName("A credential never verified is due")
    void given_neverVerifiedCredential_should_beDue() {
      // Arrange
      CredentialSecretReference reference = seedReference(AZURE_SERVICE_PRINCIPAL, UNSET, null);

      // Act
      List<String> dueIds = dueReferenceIds(LARGE_BUDGET);

      // Assert
      assertThat(dueIds).contains(reference.getId());
    }

    @Test
    @DisplayName("A credential verified long ago is due again")
    void given_staleCredential_should_beDue() {
      // Arrange
      CredentialSecretReference reference =
          seedReference(AZURE_MANAGED_IDENTITY, ACTIVE, Instant.now().minus(30, ChronoUnit.DAYS));

      // Act
      List<String> dueIds = dueReferenceIds(LARGE_BUDGET);

      // Assert
      assertThat(dueIds).contains(reference.getId());
    }

    @Test
    @DisplayName("A credential verified recently is left alone")
    void given_freshlyVerifiedCredential_should_notBeDue() {
      // Arrange
      CredentialSecretReference reference =
          seedReference(AZURE_SERVICE_PRINCIPAL, ACTIVE, Instant.now().minus(1, ChronoUnit.HOURS));

      // Act
      List<String> dueIds = dueReferenceIds(LARGE_BUDGET);

      // Assert
      assertThat(dueIds).doesNotContain(reference.getId());
    }

    @Test
    @DisplayName("A credential with no validator never enters the run")
    void given_nonValidatableAuthMethod_should_neverBeDue() {
      // Arrange — these would otherwise burn the run's budget for nothing.
      CredentialSecretReference usernamePassword = seedReference(USERNAME_PASSWORD, UNSET, null);
      CredentialSecretReference hash = seedReference(HASH, UNSET, null);

      // Act
      List<String> dueIds = dueReferenceIds(LARGE_BUDGET);

      // Assert
      assertThat(dueIds).doesNotContain(usernamePassword.getId(), hash.getId());
      assertThat(VALIDATABLE_AUTH_METHODS).doesNotContain(USERNAME_PASSWORD, HASH);
    }

    @Test
    @DisplayName("The run budget caps how many credentials are returned")
    void given_moreCredentialsThanBudget_should_capTheBatch() {
      // Arrange
      seedReference(AZURE_SERVICE_PRINCIPAL, UNSET, null);
      seedReference(AZURE_SERVICE_PRINCIPAL, UNSET, null);
      seedReference(AZURE_SERVICE_PRINCIPAL, UNSET, null);

      // Act
      List<String> dueIds = dueReferenceIds(2);

      // Assert
      assertThat(dueIds).hasSize(2);
    }

    @Test
    @DisplayName("A non-positive budget returns nothing instead of querying")
    void given_zeroBudget_should_returnEmpty() {
      // Arrange
      seedReference(AZURE_SERVICE_PRINCIPAL, UNSET, null);

      // Act
      List<String> dueIds = dueReferenceIds(0);

      // Assert
      assertThat(dueIds).isEmpty();
    }
  }

  @Nested
  @DisplayName("Running connectivity validation")
  class ValidateConnectivity {

    @Test
    @DisplayName("A reference whose secret cannot be loaded still travels through the run")
    void given_referenceWithoutLocation_should_returnNotChecked() {
      // Arrange — the reference seeded here has no location at all.
      CredentialSecretReference reference = seedReference(AZURE_SERVICE_PRINCIPAL, ACTIVE, null);

      // Act
      SecretConnectionResult result =
          secretValidationService.validateConnectivity(tenant.getId(), reference);

      // Assert — preparation fails before any provider-specific validator can run.
      assertThat(result.status()).isEqualTo(UNSET);
      assertThat(result.wasChecked()).isFalse();
      assertThat(result.statusToPersist()).isEmpty();
    }

    @Test
    @DisplayName("A reference pointing at an unknown provider is reported, not dropped")
    void given_unresolvableProvider_should_returnNotChecked() {
      // Arrange — a connector instance no integration is spawned for.
      CredentialSecretReference reference =
          seedReference(AZURE_SERVICE_PRINCIPAL, ACTIVE, null, "unknown-connector-instance");

      // Act
      SecretConnectionResult result =
          secretValidationService.validateConnectivity(tenant.getId(), reference);

      // Assert — one unresolvable provider must not fail the whole run.
      assertThat(result.status()).isEqualTo(UNSET);
      assertThat(result.wasChecked()).isFalse();
      assertThat(result.statusToPersist()).isEmpty();
    }

    @Test
    @DisplayName("A blank tenant id is treated as not checked")
    void given_blankTenantId_should_returnNotChecked() {
      // Arrange
      CredentialSecretReference reference = seedReference(AZURE_SERVICE_PRINCIPAL, ACTIVE, null);

      // Act
      SecretConnectionResult result =
          secretValidationService.validateConnectivity("   ", reference);

      // Assert
      assertThat(result.status()).isEqualTo(UNSET);
      assertThat(result.wasChecked()).isFalse();
    }

    @Test
    @DisplayName("A null reference is treated as not checked")
    void given_nullReference_should_returnNotChecked() {
      // Arrange & Act
      SecretConnectionResult result =
          secretValidationService.validateConnectivity(tenant.getId(), null);

      // Assert
      assertThat(result.status()).isEqualTo(UNSET);
      assertThat(result.wasChecked()).isFalse();
      assertThat(result.statusToPersist()).isEmpty();
    }
  }

  @Nested
  @DisplayName("Persisting the outcomes")
  class PersistResults {

    @Test
    @DisplayName("A definitive answer writes both the status and the verification stamp")
    void given_definitiveOutcome_should_writeStatusAndStamp() {
      // Arrange
      CredentialSecretReference reference = seedReference(AZURE_SERVICE_PRINCIPAL, UNSET, null);

      // Act
      int updated =
          secretValidationService.persistResults(
              Map.of(reference.getId(), SecretConnectionResult.active()));

      // Assert
      assertThat(updated).isEqualTo(1);
      SecretReference reloaded =
          secretReferenceRepository.findById(reference.getId()).orElseThrow();
      assertThat(reloaded.getStatus()).isEqualTo(ACTIVE);
      assertThat(reloaded.getLastVerifiedAt()).isNotNull();
    }

    @Test
    @DisplayName("An auth failure writes AUTH_FAILED")
    void given_authFailure_should_writeAuthFailed() {
      // Arrange
      CredentialSecretReference reference = seedReference(AZURE_SERVICE_PRINCIPAL, ACTIVE, null);

      // Act
      secretValidationService.persistResults(
          Map.of(reference.getId(), SecretConnectionResult.authFailed()));

      // Assert
      SecretReference reloaded =
          secretReferenceRepository.findById(reference.getId()).orElseThrow();
      assertThat(reloaded.getStatus()).isEqualTo(AUTH_FAILED);
    }

    @Test
    @DisplayName("An unknown check result writes UNKNOWN and stamps the attempt")
    void given_unknownOutcome_should_writeUnknownAndStampAttempt() {
      // Arrange
      CredentialSecretReference reference = seedReference(AZURE_MANAGED_IDENTITY, ACTIVE, null);

      // Act
      int updated =
          secretValidationService.persistResults(
              Map.of(reference.getId(), SecretConnectionResult.unknown()));

      // Assert
      assertThat(updated).isEqualTo(1);
      SecretReference reloaded =
          secretReferenceRepository.findById(reference.getId()).orElseThrow();
      assertThat(reloaded.getStatus()).isEqualTo(UNKNOWN);
      assertThat(reloaded.getLastVerifiedAt()).isNotNull();
    }

    @Test
    @DisplayName("A credential no validator ever ran on is left completely untouched")
    void given_notCheckedOutcome_should_writeNothing() {
      // Arrange — a dangling secret: inconclusive AND never probed.
      CredentialSecretReference reference = seedReference(AZURE_SERVICE_PRINCIPAL, UNSET, null);

      // Act
      int updated =
          secretValidationService.persistResults(
              Map.of(reference.getId(), SecretConnectionResult.notChecked()));

      // Assert — no status, and no verification stamp either.
      assertThat(updated).isZero();
      SecretReference reloaded =
          secretReferenceRepository.findById(reference.getId()).orElseThrow();
      assertThat(reloaded.getStatus()).isEqualTo(UNSET);
      assertThat(reloaded.getLastVerifiedAt()).isNull();
    }

    @Test
    @DisplayName("An unsupported check result writes nothing")
    void given_unsupportedOutcome_should_writeNothing() { // Arrange
      CredentialSecretReference reference = seedReference(AZURE_SERVICE_PRINCIPAL, UNSET, null);

      // Act
      int updated =
          secretValidationService.persistResults(
              Map.of(reference.getId(), SecretConnectionResult.unsupported()));

      // Assert
      assertThat(updated).isZero();
      SecretReference reloaded =
          secretReferenceRepository.findById(reference.getId()).orElseThrow();
      assertThat(reloaded.getStatus()).isEqualTo(UNSET);
      assertThat(reloaded.getLastVerifiedAt()).isNull();
    }

    @Test
    @DisplayName("An empty batch is a no-op")
    void given_noResult_should_doNothing() {
      // Arrange & Act
      int updated = secretValidationService.persistResults(Map.of());

      // Assert
      assertThat(updated).isZero();
    }
  }
}
