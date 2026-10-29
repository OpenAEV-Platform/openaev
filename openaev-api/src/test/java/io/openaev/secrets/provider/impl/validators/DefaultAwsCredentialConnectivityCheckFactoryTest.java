package io.openaev.secrets.provider.impl.validators;

import static io.openaev.utils.fixtures.SecretStoreRequestFixture.*;
import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import software.amazon.awssdk.auth.credentials.AnonymousCredentialsProvider;
import software.amazon.awssdk.services.sts.StsClient;

@DisplayName("DefaultAwsCredentialConnectivityCheckFactory tests")
class DefaultAwsCredentialConnectivityCheckFactoryTest {

  private final DefaultAwsCredentialConnectivityCheckFactory factory =
      new DefaultAwsCredentialConnectivityCheckFactory();

  @Nested
  @DisplayName("STS endpoint")
  class StsEndpoint {

    @Test
    @DisplayName("A stored endpoint override replaces the official STS endpoint")
    void given_endpointOverride_should_targetIt() {
      // Arrange & Act
      try (StsClient client =
          factory.stsClient(
              AWS_DEFAULT_REGION, AnonymousCredentialsProvider.create(), AWS_ENDPOINT_URL)) {

        // Assert
        assertThat(client.serviceClientConfiguration().endpointOverride())
            .contains(URI.create(AWS_ENDPOINT_URL));
      }
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = "   ")
    @DisplayName("Without an override the official endpoint of the region is kept")
    void given_noEndpointOverride_should_keepTheOfficialEndpoint(String endpointUrl) {
      // Arrange & Act
      try (StsClient client =
          factory.stsClient(
              AWS_DEFAULT_REGION, AnonymousCredentialsProvider.create(), endpointUrl)) {

        // Assert
        assertThat(client.serviceClientConfiguration().endpointOverride()).isEmpty();
      }
    }
  }
}
