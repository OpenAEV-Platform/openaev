package io.openaev.secrets.provider.impl;

import static io.openaev.database.model.Secret.SECRET_TYPE.HASH_VALUE;
import static io.openaev.database.model.Secret.SECRET_TYPE.USERNAME_PASSWORD_VALUE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.openaev.database.model.CredentialSecretReference;
import io.openaev.database.model.HashSecret;
import io.openaev.database.model.Secret;
import io.openaev.database.model.SecretReference;
import io.openaev.database.model.Tenant;
import io.openaev.database.model.UsernamePasswordSecret;
import io.openaev.database.repository.SecretReferenceRepository;
import io.openaev.database.repository.SecretsRepository;
import io.openaev.secrets.provider.SecretStoreRequest;
import io.openaev.service.connector_instances.NativeEncryptionService;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("LocalSecretsProvider")
class LocalSecretsProviderTest {

  @Mock private NativeEncryptionService nativeEncryptionService;
  @Mock private SecretsRepository secretsRepository;
  @Mock private SecretReferenceRepository secretReferenceRepository;

  private LocalSecretsProvider localSecretsProvider;

  @BeforeEach
  void setUp() {
    localSecretsProvider =
        new LocalSecretsProvider(
            "test-id",
            "Test Local Provider",
            nativeEncryptionService,
            secretsRepository,
            secretReferenceRepository);
  }

  @Nested
  @DisplayName("Store")
  class Store {

    @Test
    @DisplayName("Store username/password secret and persist secret reference")
    void given_usernamePasswordReference_should_persistPasswordSecret_and_reference() {
      // Arrange
      CredentialSecretReference secretReference = new CredentialSecretReference();
      secretReference.setName("test-credential");
      secretReference.setCredentialAuthMethod(USERNAME_PASSWORD_VALUE);
      secretReference.setTenant(new Tenant("tenant-1"));

      when(nativeEncryptionService.encrypt("plain-secret")).thenReturn("encrypted-secret");
      when(secretsRepository.save(any(UsernamePasswordSecret.class)))
          .thenAnswer(
              invocation -> {
                UsernamePasswordSecret persisted = invocation.getArgument(0);
                persisted.setId("secret-id-1");
                return persisted;
              });
      when(secretReferenceRepository.save(any(SecretReference.class)))
          .thenAnswer(invocation -> invocation.getArgument(0));

      // Act
      SecretReference result =
          localSecretsProvider.store(
              secretReference, new SecretStoreRequest("admin", "plain-secret", null, null));

      // Assert
      assertThat(result.getLocation()).isEqualTo("secret-id-1");
      ArgumentCaptor<UsernamePasswordSecret> secretCaptor =
          ArgumentCaptor.forClass(UsernamePasswordSecret.class);
      verify(secretsRepository).save(secretCaptor.capture());
      assertThat(secretCaptor.getValue().getPassword()).isEqualTo("encrypted-secret");
    }

    @Test
    @DisplayName("Store hash secret and persist secret reference")
    void given_hashReference_should_persistHashSecret_and_reference() {
      // Arrange
      CredentialSecretReference secretReference = new CredentialSecretReference();
      secretReference.setName("test-hash");
      secretReference.setCredentialAuthMethod(HASH_VALUE);
      secretReference.setTenant(new Tenant("tenant-1"));

      when(nativeEncryptionService.encrypt("plain-hash")).thenReturn("encrypted-hash");
      when(secretsRepository.save(any(HashSecret.class)))
          .thenAnswer(
              invocation -> {
                HashSecret persisted = invocation.getArgument(0);
                persisted.setId("secret-id-2");
                return persisted;
              });
      when(secretReferenceRepository.save(any(SecretReference.class)))
          .thenAnswer(invocation -> invocation.getArgument(0));

      // Act
      SecretReference result =
          localSecretsProvider.store(
              secretReference, new SecretStoreRequest(null, null, "plain-hash", "NTLM"));

      // Assert
      assertThat(result.getLocation()).isEqualTo("secret-id-2");
      ArgumentCaptor<HashSecret> secretCaptor = ArgumentCaptor.forClass(HashSecret.class);
      verify(secretsRepository).save(secretCaptor.capture());
      assertThat(secretCaptor.getValue().getHash()).isEqualTo("encrypted-hash");
      assertThat(secretCaptor.getValue().getHashAlgorithm()).isEqualTo("NTLM");
    }

    @Test
    @DisplayName("Fail when storing a hash secret without hash value")
    void given_hashReferenceWithoutHash_should_throwNullPointerException() {
      // Arrange
      CredentialSecretReference secretReference = new CredentialSecretReference();
      secretReference.setName("test-hash");
      secretReference.setCredentialAuthMethod(HASH_VALUE);
      secretReference.setTenant(new Tenant("tenant-1"));

      // Act & Assert
      assertThatThrownBy(
              () ->
                  localSecretsProvider.store(
                      secretReference, new SecretStoreRequest(null, null, null, "NTLM")))
          .isInstanceOf(NullPointerException.class)
          .hasMessage("request.hash must not be null");
    }

    @Test
    @DisplayName("Fail when storing a username/password secret without password")
    void given_usernamePasswordReferenceWithoutPassword_should_throwNullPointerException() {
      // Arrange
      CredentialSecretReference secretReference = new CredentialSecretReference();
      secretReference.setName("test-credential");
      secretReference.setCredentialAuthMethod(USERNAME_PASSWORD_VALUE);
      secretReference.setTenant(new Tenant("tenant-1"));

      // Act & Assert
      assertThatThrownBy(
              () ->
                  localSecretsProvider.store(
                      secretReference, new SecretStoreRequest("admin", null, null, null)))
          .isInstanceOf(NullPointerException.class)
          .hasMessage("request.password must not be null");
    }
  }

  @Nested
  @DisplayName("Update")
  class Update {

    @Test
    @DisplayName("Update existing username/password secret and keep reference")
    void given_usernamePasswordReference_should_updateExistingSecret_and_reference() {
      // Arrange
      CredentialSecretReference secretReference = new CredentialSecretReference();
      secretReference.setCredentialAuthMethod(USERNAME_PASSWORD_VALUE);
      secretReference.setName("test-credential");
      secretReference.setLocation("secret-id-1");
      secretReference.setTenant(new Tenant("tenant-1"));

      UsernamePasswordSecret existingSecret = new UsernamePasswordSecret();
      existingSecret.setId("secret-id-1");

      when(secretsRepository.findById("secret-id-1")).thenReturn(Optional.of(existingSecret));
      when(nativeEncryptionService.encrypt("next-password")).thenReturn("encrypted-next-password");
      when(secretsRepository.save(any(Secret.class)))
          .thenAnswer(invocation -> invocation.getArgument(0));
      when(secretReferenceRepository.save(any(SecretReference.class)))
          .thenAnswer(invocation -> invocation.getArgument(0));

      // Act
      SecretReference result =
          localSecretsProvider.update(
              secretReference, new SecretStoreRequest("new-user", "next-password", null, null));

      // Assert
      assertThat(result).isSameAs(secretReference);
      assertThat(existingSecret.getUsername()).isEqualTo("new-user");
      assertThat(existingSecret.getPassword()).isEqualTo("encrypted-next-password");
    }

    @Test
    @DisplayName("Update existing hash secret and keep reference")
    void given_hashReference_should_updateExistingHashSecret_and_reference() {
      // Arrange
      CredentialSecretReference secretReference = new CredentialSecretReference();
      secretReference.setCredentialAuthMethod(HASH_VALUE);
      secretReference.setName("test-hash");
      secretReference.setLocation("secret-id-2");
      secretReference.setTenant(new Tenant("tenant-1"));

      HashSecret existingSecret = new HashSecret();
      existingSecret.setId("secret-id-2");

      when(secretsRepository.findById("secret-id-2")).thenReturn(Optional.of(existingSecret));
      when(nativeEncryptionService.encrypt("next-hash")).thenReturn("encrypted-next-hash");
      when(secretsRepository.save(any(Secret.class)))
          .thenAnswer(invocation -> invocation.getArgument(0));
      when(secretReferenceRepository.save(any(SecretReference.class)))
          .thenAnswer(invocation -> invocation.getArgument(0));

      // Act
      SecretReference result =
          localSecretsProvider.update(
              secretReference, new SecretStoreRequest(null, null, "next-hash", "SHA256"));

      // Assert
      assertThat(result).isSameAs(secretReference);
      assertThat(existingSecret.getHash()).isEqualTo("encrypted-next-hash");
      assertThat(existingSecret.getHashAlgorithm()).isEqualTo("SHA256");
    }

    @Test
    @DisplayName("Fail when updating a secret that does not exist")
    void given_unknownSecretId_should_throwSecretNotFoundException() {
      // Arrange
      CredentialSecretReference secretReference = new CredentialSecretReference();
      secretReference.setCredentialAuthMethod(USERNAME_PASSWORD_VALUE);
      secretReference.setName("test-credential");
      secretReference.setLocation("unknown-secret-id");
      secretReference.setTenant(new Tenant("tenant-1"));

      when(secretsRepository.findById("unknown-secret-id")).thenReturn(Optional.empty());

      // Act & Assert
      assertThatThrownBy(
              () ->
                  localSecretsProvider.update(
                      secretReference,
                      new SecretStoreRequest("new-user", "next-password", null, null)))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("Secret not found for id: unknown-secret-id");
    }

    @Test
    @DisplayName("Fail when updating hash secret without new hash value")
    void given_hashReferenceWithoutNewHash_should_throwNullHashException() {
      // Arrange
      CredentialSecretReference secretReference = new CredentialSecretReference();
      secretReference.setCredentialAuthMethod(HASH_VALUE);
      secretReference.setName("test-hash");
      secretReference.setLocation("secret-id-2");
      secretReference.setTenant(new Tenant("tenant-1"));

      HashSecret existingSecret = new HashSecret();
      existingSecret.setId("secret-id-2");

      when(secretsRepository.findById("secret-id-2")).thenReturn(Optional.of(existingSecret));

      // Act & Assert
      assertThatThrownBy(
              () ->
                  localSecretsProvider.update(
                      secretReference, new SecretStoreRequest(null, null, null, "NTLM")))
          .isInstanceOf(NullPointerException.class)
          .hasMessage("request.hash must not be null");
    }

    @Test
    @DisplayName("Fail when updating username/password secret without new password")
    void given_usernamePasswordReferenceWithoutNewPassword_should_throwNullPasswordException() {
      // Arrange
      CredentialSecretReference secretReference = new CredentialSecretReference();
      secretReference.setCredentialAuthMethod(USERNAME_PASSWORD_VALUE);
      secretReference.setName("test-credential");
      secretReference.setLocation("secret-id-1");
      secretReference.setTenant(new Tenant("tenant-1"));

      UsernamePasswordSecret existingSecret = new UsernamePasswordSecret();
      existingSecret.setId("secret-id-1");

      when(secretsRepository.findById("secret-id-1")).thenReturn(Optional.of(existingSecret));

      // Act & Assert
      assertThatThrownBy(
              () ->
                  localSecretsProvider.update(
                      secretReference, new SecretStoreRequest("new-user", null, null, null)))
          .isInstanceOf(NullPointerException.class)
          .hasMessage("request.password must not be null");
    }
  }

  @Nested
  @DisplayName("Delete")
  class Delete {

    @Test
    @DisplayName("Delete secret and secret reference")
    void given_secretReference_should_deleteSecret_and_reference() {
      // Arrange
      SecretReference secretReference = new SecretReference();
      secretReference.setId("ref-id-1");
      secretReference.setName("test-credential");
      secretReference.setLocation("secret-id-1");

      // Act
      localSecretsProvider.delete(secretReference);

      // Assert
      verify(secretsRepository).deleteById("secret-id-1");
      verify(secretReferenceRepository).delete(secretReference);
    }
  }
}
