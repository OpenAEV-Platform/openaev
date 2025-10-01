package io.openaev.config.tls.trustmanager;

import static io.openaev.utils.helpers.X509CertificateHelper.readCertificate;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;

import io.openaev.IntegrationTest;
import java.security.cert.X509Certificate;
import javax.net.ssl.X509TrustManager;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

@TestInstance(PER_CLASS)
@TestPropertySource(
    properties = {"openaev.extra-trusted-certs-dir=src/test/resources/tls/extra-certs/one"})
class TlsConfigOneExtraCertTest extends IntegrationTest {

  @Autowired private X509TrustManager trustManager;

  @Test
  @DisplayName("Should get one extra certs")
  void tlsContextCustomWithOneExtraCert() throws Exception {
    X509Certificate cert = readCertificate("src/test/resources/tls/extra-certs/one/cert.pem");
    assertThat(trustManager.getAcceptedIssuers()).contains(cert);
  }
}
