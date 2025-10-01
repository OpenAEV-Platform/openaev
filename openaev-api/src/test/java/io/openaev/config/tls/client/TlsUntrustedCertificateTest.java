package io.openaev.config.tls.client;

import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

import io.openaev.IntegrationTest;
import io.openaev.authorisation.HttpClientFactory;
import java.io.IOException;
import java.security.cert.CertificateException;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockserver.configuration.Configuration;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.socket.PortFactory;
import org.springframework.beans.factory.annotation.Autowired;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("TLS certificate validation tests - untrusted certificate")
public class TlsUntrustedCertificateTest extends IntegrationTest {
  private ClientAndServer mockServer;
  @Autowired private HttpClientFactory httpClientFactory;

  @BeforeAll
  public void before() {
    mockServer =
        new ClientAndServer(
            Configuration.configuration()
                .certificateAuthorityCertificate("src/test/resources/tls/localhost/ca.pem")
                .x509CertificatePath("src/test/resources/tls/localhost/localhost.crt")
                .privateKeyPath("src/test/resources/tls/localhost/localhost.key")
                .rebuildTLSContext(true),
            PortFactory.findFreePort());
  }

  @Test
  @DisplayName("When the server certificate chain is untrusted, throw an exception")
  void whenTheServerCertificateChainIsTrusted_AllowConnection() throws IOException {
    mockServer
        .withSecure(true)
        .when(request().withMethod("GET").withPath("/"))
        .respond(response().withStatusCode(200));

    CloseableHttpClient client = httpClientFactory.httpClientCustom();
    HttpGet get = new HttpGet(String.format("https://localhost:%d", mockServer.getLocalPort()));
    assertThatThrownBy(() -> client.execute(get)).hasCauseInstanceOf(CertificateException.class);
  }
}
