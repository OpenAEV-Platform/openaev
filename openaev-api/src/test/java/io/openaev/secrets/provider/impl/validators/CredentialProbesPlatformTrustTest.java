package io.openaev.secrets.provider.impl.validators;

import static io.openaev.database.model.SecretReference.SECRET_STATUS.ACTIVE;
import static io.openaev.utils.fixtures.AwsStsResponseFixture.GET_CALLER_IDENTITY_RESPONSE;
import static io.openaev.utils.fixtures.AwsStsResponseFixture.STS_CONTENT_TYPE;
import static io.openaev.utils.fixtures.GcpServiceAccountKeyFixture.GCP_KEY_FILE_TOKEN_URI;
import static io.openaev.utils.fixtures.GcpServiceAccountKeyFixture.createServiceAccountKeyJson;
import static io.openaev.utils.fixtures.SecretStoreRequestFixture.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

import io.openaev.secrets.provider.SecretConnectionResult;
import io.openaev.utils.helpers.TlsTestSupport;
import java.io.IOException;
import javax.net.ssl.X509TrustManager;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockserver.configuration.Configuration;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.model.MediaType;
import org.mockserver.socket.PortFactory;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.auth.credentials.AnonymousCredentialsProvider;
import software.amazon.awssdk.services.sts.StsClient;
import software.amazon.awssdk.services.sts.model.GetCallerIdentityRequest;

/**
 * The probes of a sovereign or mocked cloud behind a private CA must trust what the platform
 * trusts, extra trusted certificates included: the SDKs of AWS and GCP only know the JVM's own
 * roots unless the platform's trust manager is handed to them.
 *
 * <p>A TLS server presenting a certificate signed by a CA outside the JVM roots stands in for that
 * cloud; the platform's trust manager is either given that CA (an extra trusted cert) or not.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("Credential probes trust the platform's certificates")
class CredentialProbesPlatformTrustTest {

  private static final String HANDSHAKE_FAILURE = "javax.net.ssl.SSLHandshakeException";
  private static final String ACCESS_TOKEN = "fake-access-token";

  private ClientAndServer cloud;
  private String baseUrl;

  @BeforeAll
  void startTlsCloud() {
    cloud =
        new ClientAndServer(
            Configuration.configuration()
                .certificateAuthorityCertificate(TlsTestSupport.LOCALHOST_CA_PEM)
                .x509CertificatePath(TlsTestSupport.LOCALHOST_CERTIFICATE)
                .privateKeyPath(TlsTestSupport.LOCALHOST_PRIVATE_KEY)
                .rebuildTLSContext(true),
            PortFactory.findFreePort());
    baseUrl = "https://localhost:" + cloud.getLocalPort();
    cloud
        .withSecure(true)
        .when(request().withPath("/"))
        .respond(
            response()
                .withStatusCode(200)
                .withContentType(MediaType.parse(STS_CONTENT_TYPE))
                .withBody(GET_CALLER_IDENTITY_RESPONSE));
    cloud
        .withSecure(true)
        .when(request().withPath("/token"))
        .respond(
            response()
                .withStatusCode(200)
                .withContentType(MediaType.JSON_UTF_8)
                .withBody(
                    "{\"access_token\": \"%s\", \"expires_in\": 3600, \"token_type\": \"Bearer\"}"
                        .formatted(ACCESS_TOKEN)));
    cloud
        .withSecure(true)
        .when(request().withPath("/v1/projects/" + GCP_PROJECT_ID))
        .respond(
            response().withStatusCode(200).withContentType(MediaType.JSON_UTF_8).withBody("{}"));
  }

  @AfterAll
  void stopTlsCloud() {
    cloud.stop();
  }

  @Nested
  @DisplayName("AWS")
  class Aws {

    private void callerIdentity(X509TrustManager trustManager) {
      DefaultAwsCredentialConnectivityCheckFactory factory =
          new DefaultAwsCredentialConnectivityCheckFactory(trustManager);
      try (StsClient client =
          factory.stsClient(AWS_DEFAULT_REGION, AnonymousCredentialsProvider.create(), baseUrl)) {
        client.getCallerIdentity(GetCallerIdentityRequest.builder().build());
      } finally {
        factory.close();
      }
    }

    @Test
    @DisplayName("An STS endpoint signed by an extra trusted CA is reached")
    void given_extraTrustedCa_should_reachTheEndpoint() {
      // Act & Assert
      callerIdentity(TlsTestSupport.trustingLocalhostCa());
    }

    @Test
    @DisplayName("An STS endpoint signed by a CA the platform does not trust is refused")
    void given_unknownCa_should_failTheHandshake() {
      // Act & Assert
      assertThatThrownBy(() -> callerIdentity(TlsTestSupport.jdkTrustManager()))
          .hasStackTraceContaining(HANDSHAKE_FAILURE);
    }
  }

  @Nested
  @DisplayName("GCP")
  class Gcp {

    private DefaultGcpCredentialConnectivityCheckFactory factory(X509TrustManager trustManager) {
      return new DefaultGcpCredentialConnectivityCheckFactory(trustManager);
    }

    @Test
    @DisplayName("A service account token endpoint signed by an extra trusted CA is reached")
    void given_extraTrustedCa_should_mintAServiceAccountToken() throws IOException {
      // Act
      String token =
          factory(TlsTestSupport.trustingLocalhostCa())
              .forServiceAccount(
                  createServiceAccountKeyJson(GCP_KEY_FILE_TOKEN_URI),
                  GCP_SCOPE,
                  baseUrl + "/token")
              .refreshAccessToken()
              .getTokenValue();

      // Assert
      assertThat(token).isEqualTo(ACCESS_TOKEN);
    }

    @Test
    @DisplayName("An OAuth 2.0 token endpoint signed by an extra trusted CA is reached")
    void given_extraTrustedCa_should_mintAnOAuth2Token() throws IOException {
      // Act
      String token =
          factory(TlsTestSupport.trustingLocalhostCa())
              .forOAuth2(
                  GCP_OAUTH_CLIENT_ID,
                  GCP_OAUTH_CLIENT_SECRET,
                  GCP_OAUTH_REFRESH_TOKEN,
                  GCP_SCOPE,
                  baseUrl + "/token")
              .refreshAccessToken()
              .getTokenValue();

      // Assert
      assertThat(token).isEqualTo(ACCESS_TOKEN);
    }

    @Test
    @DisplayName("A token endpoint signed by a CA the platform does not trust is refused")
    void given_unknownCa_should_failTheHandshake() {
      // Act & Assert
      assertThatThrownBy(
              () ->
                  factory(TlsTestSupport.jdkTrustManager())
                      .forOAuth2(
                          GCP_OAUTH_CLIENT_ID,
                          GCP_OAUTH_CLIENT_SECRET,
                          GCP_OAUTH_REFRESH_TOKEN,
                          GCP_SCOPE,
                          baseUrl + "/token")
                      .refreshAccessToken())
          .hasStackTraceContaining(HANDSHAKE_FAILURE);
    }

    @Test
    @DisplayName("The token exchange and the project read both go over the platform's trust")
    void given_extraTrustedCa_should_probeTokenAndProject() {
      // Arrange
      GcpCredentialConnectivityCheck check =
          new GcpCredentialConnectivityCheck(factory(TlsTestSupport.trustingLocalhostCa()));
      ReflectionTestUtils.setField(check, "timeoutSeconds", 10);

      // Act
      SecretConnectionResult result =
          check.validateOAuth2(
              GCP_OAUTH_CLIENT_ID,
              GCP_OAUTH_CLIENT_SECRET,
              GCP_OAUTH_REFRESH_TOKEN,
              GCP_SCOPE,
              GCP_PROJECT_ID,
              baseUrl + "/token",
              baseUrl + "/v1/projects/");

      // Assert
      assertThat(result.status()).isEqualTo(ACTIVE);
    }
  }
}
