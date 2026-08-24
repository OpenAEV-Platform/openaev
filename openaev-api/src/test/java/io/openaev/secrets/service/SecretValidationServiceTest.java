package io.openaev.secrets.service;

import static io.openaev.database.model.CredentialSecretReference.CREDENTIAL_AUTH_METHOD.AZURE_MANAGED_IDENTITY;
import static io.openaev.database.model.CredentialSecretReference.CREDENTIAL_AUTH_METHOD.AZURE_SERVICE_PRINCIPAL;
import static io.openaev.database.model.CredentialSecretReference.CREDENTIAL_AUTH_METHOD.HASH;
import static io.openaev.database.model.CredentialSecretReference.CREDENTIAL_AUTH_METHOD.USERNAME_PASSWORD;
import static io.openaev.database.model.SecretReference.SECRET_STATUS.ACTIVE;
import static io.openaev.database.model.SecretReference.SECRET_STATUS.INACTIVE;
import static io.openaev.database.model.SecretReference.SECRET_STATUS.UNSET;
import static io.openaev.secrets.provider.SecretValidationDetails.SECRET_NOT_FOUND;
import static io.openaev.secrets.provider.SecretValidationDetails.UNREACHABLE;
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
import io.openaev.secrets.provider.SecretValidationResult;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

/** Integration tests for the three phases of the credential status validation. */
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
   * secret, so {@code validate} exercises its defensive path without needing a real Azure secret.
   */
  private CredentialSecretReference seedReference(
      CREDENTIAL_AUTH_METHOD authMethod, SECRET_STATUS status, Instant lastVerifiedAt) {
    CredentialSecretReference reference = new CredentialSecretReference();
    reference.setName(namePrefix() + UUID.randomUUID());
    reference.setConnectorInstanceId("local-secrets-provider");
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

  private List<String> dueReferenceIds(int maxPerRun) {
    return secretValidationService.findDueForValidation(maxPerRun, REVALIDATE_AFTER).stream()
        .map(SecretValidationCandidate::referenceId)
        .toList();
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
  @DisplayName("Validating one credential")
  class Validate {

    @Test
    @DisplayName("A dangling location yields an inconclusive result instead of failing the run")
    void given_missingSecret_should_returnUnknown() {
      // Arrange — the reference seeded here has no location at all.
      CredentialSecretReference reference = seedReference(AZURE_SERVICE_PRINCIPAL, ACTIVE, null);
      SecretValidationCandidate candidate = new SecretValidationCandidate(reference.getId(), null);

      // Act
      SecretValidationResult result = secretValidationService.validate(candidate);

      // Assert
      assertThat(result.outcome()).isEqualTo(SecretValidationResult.OUTCOME.UNKNOWN);
      assertThat(result.detail()).isEqualTo(SECRET_NOT_FOUND);
      assertThat(result.statusToPersist()).isEmpty();
    }

    @Test
    @DisplayName("A reference whose secret cannot be loaded still travels through the run")
    void given_referenceWithoutLocation_should_beReturnedWithNullSecret() {
      // Arrange
      CredentialSecretReference reference = seedReference(AZURE_SERVICE_PRINCIPAL, ACTIVE, null);

      // Act
      List<SecretValidationCandidate> candidates =
          secretValidationService.findDueForValidation(LARGE_BUDGET, REVALIDATE_AFTER);

      // Assert — present in the batch, with no secret, so it gets an outcome rather than vanishing.
      assertThat(candidates)
          .filteredOn(candidate -> candidate.referenceId().equals(reference.getId()))
          .singleElement()
          .satisfies(candidate -> assertThat(candidate.secret()).isNull());
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
              Map.of(reference.getId(), SecretValidationResult.active()));

      // Assert
      assertThat(updated).isEqualTo(1);
      SecretReference reloaded =
          secretReferenceRepository.findById(reference.getId()).orElseThrow();
      assertThat(reloaded.getStatus()).isEqualTo(ACTIVE);
      assertThat(reloaded.getLastVerifiedAt()).isNotNull();
    }

    @Test
    @DisplayName("A rejection flips the status to inactive")
    void given_inactiveOutcome_should_writeInactive() {
      // Arrange
      CredentialSecretReference reference = seedReference(AZURE_SERVICE_PRINCIPAL, ACTIVE, null);

      // Act
      secretValidationService.persistResults(
          Map.of(reference.getId(), SecretValidationResult.inactive("AUTH_REJECTED")));

      // Assert
      SecretReference reloaded =
          secretReferenceRepository.findById(reference.getId()).orElseThrow();
      assertThat(reloaded.getStatus()).isEqualTo(INACTIVE);
    }

    @Test
    @DisplayName("An inconclusive check keeps the previous status but still stamps the attempt")
    void given_unknownOutcome_should_keepStatusAndStampAttempt() {
      // Arrange — this is the transient-outage case: a valid credential must NOT be flipped.
      CredentialSecretReference reference = seedReference(AZURE_MANAGED_IDENTITY, ACTIVE, null);

      // Act
      int updated =
          secretValidationService.persistResults(
              Map.of(reference.getId(), SecretValidationResult.unknown(UNREACHABLE)));

      // Assert
      assertThat(updated).isEqualTo(1);
      SecretReference reloaded =
          secretReferenceRepository.findById(reference.getId()).orElseThrow();
      assertThat(reloaded.getStatus()).isEqualTo(ACTIVE);
      // Stamped anyway, otherwise a permanently unreachable provider would pin the same rows at
      // the head of every single run.
      assertThat(reloaded.getLastVerifiedAt()).isNotNull();
    }

    @Test
    @DisplayName("An unsupported credential type is left completely untouched")
    void given_unsupportedOutcome_should_writeNothing() {
      // Arrange
      CredentialSecretReference reference = seedReference(AZURE_SERVICE_PRINCIPAL, UNSET, null);

      // Act
      int updated =
          secretValidationService.persistResults(
              Map.of(reference.getId(), SecretValidationResult.unsupported()));

      // Assert — never checked, so never stamped as verified.
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
