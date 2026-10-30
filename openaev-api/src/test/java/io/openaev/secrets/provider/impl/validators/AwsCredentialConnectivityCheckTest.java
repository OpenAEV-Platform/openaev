package io.openaev.secrets.provider.impl.validators;

import static io.openaev.database.model.AwsAssumeRoleSecret.AWS_SOURCE_IDENTITY_TYPE.STATIC_ACCESS_KEY;
import static io.openaev.database.model.SecretReference.SECRET_STATUS.ACTIVE;
import static io.openaev.database.model.SecretReference.SECRET_STATUS.AUTH_FAILED;
import static io.openaev.database.model.SecretReference.SECRET_STATUS.FORMAT_ERROR;
import static io.openaev.database.model.SecretReference.SECRET_STATUS.NETWORK_ERROR;
import static io.openaev.database.model.SecretReference.SECRET_STATUS.TIMEOUT;
import static io.openaev.utils.fixtures.AwsStsResponseFixture.*;
import static io.openaev.utils.fixtures.SecretStoreRequestFixture.*;
import static org.assertj.core.api.Assertions.assertThat;

import io.openaev.secrets.provider.SecretConnectionResult;
import io.openaev.utils.helpers.MockCloudServer;
import io.openaev.utils.helpers.MockCloudServer.CannedResponse;
import java.io.IOException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Probes AWS credentials against a mocked STS, through the real SDK client.
 *
 * <p>A local server stands in for the sovereign or mocked cloud the endpoint override points to:
 * this is the very use case of the override, and the only way to prove the probe reaches it rather
 * than the official STS endpoint.
 */
@DisplayName("AwsCredentialConnectivityCheck tests")
class AwsCredentialConnectivityCheckTest {

  private static final int TIMEOUT_SECONDS = 5;

  private MockCloudServer sts;
  private AwsCredentialConnectivityCheck check;

  @BeforeEach
  void setUp() throws IOException {
    sts = MockCloudServer.start();
    DefaultAwsCredentialConnectivityCheckFactory factory =
        new DefaultAwsCredentialConnectivityCheckFactory();
    // A @Value field: without a Spring context it stays 0, which the factory clamps to 1s. A
    // wider budget keeps the local round trips clear of the timeout under a loaded test run.
    ReflectionTestUtils.setField(factory, "timeoutSeconds", TIMEOUT_SECONDS);
    check = new AwsCredentialConnectivityCheck(factory);
  }

  @AfterEach
  void tearDown() {
    sts.close();
  }

  private SecretConnectionResult validateAccessKey(String endpointUrl) {
    return check.validateAccessKey(
        AWS_DEFAULT_REGION, AWS_ACCESS_KEY_ID, AWS_SECRET_ACCESS_KEY, null, endpointUrl);
  }

  @Nested
  @DisplayName("Access key")
  class AccessKey {

    @Test
    @DisplayName("The probe is sent to the overridden STS endpoint")
    void given_endpointOverride_should_probeTheOverriddenEndpoint() {
      // Arrange
      sts.respond("/", 200, STS_CONTENT_TYPE, GET_CALLER_IDENTITY_RESPONSE);

      // Act
      SecretConnectionResult result = validateAccessKey(sts.url("/"));

      // Assert
      assertThat(result.status()).isEqualTo(ACTIVE);
      assertThat(sts.requests())
          .singleElement()
          .satisfies(
              request -> assertThat(isStsAction(request.body(), "GetCallerIdentity")).isTrue());
    }

    @Test
    @DisplayName("A rejection by the overridden endpoint flags the credential")
    void given_rejectionByOverriddenEndpoint_should_returnAuthFailed() {
      // Arrange
      sts.respond("/", 403, STS_CONTENT_TYPE, INVALID_CLIENT_TOKEN_RESPONSE);

      // Act
      SecretConnectionResult result = validateAccessKey(sts.url("/"));

      // Assert
      assertThat(result.status()).isEqualTo(AUTH_FAILED);
    }

    @Test
    @DisplayName("An overridden endpoint that does not answer is inconclusive, never a rejection")
    void given_unreachableEndpointOverride_should_returnInconclusiveStatus() throws IOException {
      // Arrange
      String unreachable = MockCloudServer.unreachableUrl("/");

      // Act
      SecretConnectionResult result = validateAccessKey(unreachable);

      // Assert
      assertThat(result.status()).isIn(TIMEOUT, NETWORK_ERROR);
    }

    @Test
    @DisplayName("A malformed endpoint override is a configuration problem")
    void given_malformedEndpointOverride_should_returnFormatError() {
      // Arrange & Act
      SecretConnectionResult result = validateAccessKey("http://not a host");

      // Assert
      assertThat(result.status()).isEqualTo(FORMAT_ERROR);
      assertThat(sts.requests()).isEmpty();
    }
  }

  @Nested
  @DisplayName("Assume role")
  class AssumeRole {

    @Test
    @DisplayName("Both the role assumption and the probe are sent to the overridden endpoint")
    void given_endpointOverride_should_sendBothCallsToTheOverriddenEndpoint() {
      // Arrange
      sts.respond(
          "/",
          request ->
              isStsAction(request.body(), "AssumeRole")
                  ? new CannedResponse(200, STS_CONTENT_TYPE, ASSUME_ROLE_RESPONSE)
                  : new CannedResponse(200, STS_CONTENT_TYPE, GET_CALLER_IDENTITY_RESPONSE));

      // Act
      SecretConnectionResult result =
          check.validateAssumeRole(
              AWS_DEFAULT_REGION,
              AWS_ROLE_ARN,
              AWS_EXTERNAL_ID,
              STATIC_ACCESS_KEY,
              AWS_SOURCE_PROFILE_ACCESS_KEY_ID,
              AWS_SOURCE_PROFILE_SECRET_ACCESS_KEY,
              sts.url("/"));

      // Assert
      assertThat(result.status()).isEqualTo(ACTIVE);
      assertThat(sts.requests())
          .hasSize(2)
          .satisfiesExactly(
              first -> assertThat(isStsAction(first.body(), "AssumeRole")).isTrue(),
              second -> assertThat(isStsAction(second.body(), "GetCallerIdentity")).isTrue());
    }
  }
}
