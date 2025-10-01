package io.openaev.config.tls.client;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

import io.openaev.IntegrationTest;
import io.openaev.authorisation.HttpClientFactory;
import java.io.IOException;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.HttpStatus;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockserver.configuration.Configuration;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.socket.PortFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("TLS certificate validation tests - trusted certificate")
@TestPropertySource(
    properties = {"openaev.extra-trusted-certs-dir=src/test/resources/tls/localhost"})
public class TlsTrustedCertificateTest extends IntegrationTest {
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
  @DisplayName("When the server certificate chain is trusted, authorise connection")
  void whenTheServerCertificateChainIsUntrusted_ThrowAnException() throws IOException {
    mockServer
        .withSecure(true)
        .when(request().withMethod("GET").withPath("/"))
        .respond(response().withStatusCode(200));

    CloseableHttpClient client = httpClientFactory.httpClientCustom();
    HttpGet get = new HttpGet(String.format("https://localhost:%d", mockServer.getLocalPort()));
    HttpResponse r = client.execute(get);

    assertThat(r.getCode()).isEqualTo(HttpStatus.SC_OK);
  }
}
