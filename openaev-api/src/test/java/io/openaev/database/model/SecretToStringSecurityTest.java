package io.openaev.database.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Secret toString security tests")
class SecretToStringSecurityTest {

  @Test
  @DisplayName("given_hashSecret_should_notExposeHashInToString")
  void given_hashSecret_should_notExposeHashInToString() {
    // Arrange
    HashSecret secret = new HashSecret();
    secret.setHashAlgorithm(HashSecret.HASH_ALGORITHM.SHA);
    secret.setHash("__SENSITIVE_HASH__");

    // Act
    String output = secret.toString();

    // Assert
    assertThat(output).contains("HashSecret");
    assertThat(output).contains("type=");
    assertThat(output).doesNotContain("__SENSITIVE_HASH__", "hash=", "secret_hash");
  }

  @Test
  @DisplayName("given_usernamePasswordSecret_should_notExposePasswordInToString")
  void given_usernamePasswordSecret_should_notExposePasswordInToString() {
    // Arrange
    UsernamePasswordSecret secret = new UsernamePasswordSecret();
    secret.setUsername("john.doe");
    secret.setPassword("__SENSITIVE_PASSWORD__");

    // Act
    String output = secret.toString();

    // Assert
    assertThat(output).contains("UsernamePasswordSecret");
    assertThat(output).contains("type=");
    assertThat(output).doesNotContain("__SENSITIVE_PASSWORD__", "password=", "secret_password");
  }

  @Test
  @DisplayName("given_usernamePasswordSecret_should_notExposeUsernameInToString")
  void given_usernamePasswordSecret_should_notExposeUsernameInToString() {
    // Arrange
    UsernamePasswordSecret secret = new UsernamePasswordSecret();
    secret.setUsername("__SENSITIVE_USERNAME__");

    // Act
    String output = secret.toString();

    // Assert
    assertThat(output)
        .contains("UsernamePasswordSecret")
        .doesNotContain("__SENSITIVE_USERNAME__", "username=", "secret_username");
  }

  @Test
  @DisplayName("given_azureServicePrincipalSecret_should_notExposeSensitiveValuesInToString")
  void given_azureServicePrincipalSecret_should_notExposeSensitiveValuesInToString() {
    // Arrange
    AzureServicePrincipalSecret secret = new AzureServicePrincipalSecret();
    secret.setAzureEnvironment("AzureCloud");
    secret.setAzureClientId("a-client-id");
    secret.setAzureClientSecret("__SENSITIVE_CLIENT_SECRET__");
    secret.setAzureTenantId("__SENSITIVE_TENANT_ID__");
    secret.setAzureSubscriptionId("__SENSITIVE_SUBSCRIPTION_ID__");

    // Act
    String output = secret.toString();

    // Assert
    assertThat(output).contains("AzureServicePrincipalSecret");
    assertThat(output)
        .doesNotContain(
            "__SENSITIVE_CLIENT_SECRET__",
            "__SENSITIVE_TENANT_ID__",
            "__SENSITIVE_SUBSCRIPTION_ID__");
  }

  @Test
  @DisplayName("given_azureManagedIdentitySecret_should_notExposeSensitiveValuesInToString")
  void given_azureManagedIdentitySecret_should_notExposeSensitiveValuesInToString() {
    // Arrange
    AzureManagedIdentitySecret secret = new AzureManagedIdentitySecret();
    secret.setAzureEnvironment("AzureCloud");
    secret.setAzureSubscriptionId("__SENSITIVE_SUBSCRIPTION_ID__");

    // Act
    String output = secret.toString();

    // Assert
    assertThat(output).contains("AzureManagedIdentitySecret");
    assertThat(output).doesNotContain("__SENSITIVE_SUBSCRIPTION_ID__");
  }

  @Test
  @DisplayName("given_gcpServiceAccountSecret_should_notExposeSensitiveValuesInToString")
  void given_gcpServiceAccountSecret_should_notExposeSensitiveValuesInToString() {
    // Arrange
    GcpServiceAccountSecret secret = new GcpServiceAccountSecret();
    secret.setScope("https://www.googleapis.com/auth/cloud-platform");
    secret.setProjectId("__SENSITIVE_PROJECT_ID__");
    secret.setPrivateKeyJson("__SENSITIVE_PRIVATE_KEY__".getBytes(StandardCharsets.UTF_8));

    // Act
    String output = secret.toString();

    // Assert
    assertThat(output).contains("GcpServiceAccountSecret");
    assertThat(output)
        .doesNotContain("__SENSITIVE_PRIVATE_KEY__", "__SENSITIVE_PROJECT_ID__", "privateKeyJson=");
  }

  @Test
  @DisplayName("given_gcpOAuth2Secret_should_notExposeSensitiveValuesInToString")
  void given_gcpOAuth2Secret_should_notExposeSensitiveValuesInToString() {
    // Arrange
    GcpOAuth2Secret secret = new GcpOAuth2Secret();
    secret.setScope("https://www.googleapis.com/auth/cloud-platform");
    secret.setProjectId("__SENSITIVE_PROJECT_ID__");
    secret.setOauthClientId("__SENSITIVE_CLIENT_ID__");
    secret.setOauthClientSecret("__SENSITIVE_CLIENT_SECRET__");
    secret.setOauthRefreshToken("__SENSITIVE_REFRESH_TOKEN__");

    // Act
    String output = secret.toString();

    // Assert
    assertThat(output).contains("GcpOAuth2Secret");
    assertThat(output)
        .doesNotContain(
            "__SENSITIVE_CLIENT_SECRET__",
            "__SENSITIVE_REFRESH_TOKEN__",
            "__SENSITIVE_CLIENT_ID__",
            "__SENSITIVE_PROJECT_ID__");
  }
}
