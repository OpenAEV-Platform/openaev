package io.openaev.config.tls.trustmanager;

import static io.openaev.utils.helpers.X509CertificateHelper.readCertificate;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;

import io.openaev.IntegrationTest;
import java.security.cert.X509Certificate;
import javax.net.ssl.X509TrustManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

@TestInstance(PER_CLASS)
@TestPropertySource(
    properties = {"openaev.extra-trusted-certs-dir=src/test/resources/tls/extra-certs/identical"})
public class TlsConfigIdenticalCertsTest extends IntegrationTest {

  @Autowired private X509TrustManager trustManager;

  @Test
  @DisplayName("Should get a single extra cert, based on both identical certs")
  void tlsContextCustomWithWrongFolder() throws Exception {
    X509Certificate cert1 =
        readCertificate("src/test/resources/tls/extra-certs/identical/cert1.pem");
    X509Certificate cert2 =
        readCertificate("src/test/resources/tls/extra-certs/identical/cert2.pem");

    assertThat(trustManager.getAcceptedIssuers()).contains(cert1);
    assertThat(trustManager.getAcceptedIssuers()).contains(cert2);
  }
}
