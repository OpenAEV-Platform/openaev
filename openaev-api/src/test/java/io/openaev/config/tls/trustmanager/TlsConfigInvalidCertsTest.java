package io.openaev.config.tls.trustmanager;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;

import io.openaev.IntegrationTest;
import java.security.KeyStore;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

@TestInstance(PER_CLASS)
@TestPropertySource(
    properties = {"openaev.extra-trusted-certs-dir=src/test/resources/tls/extra-certs/invalid"})
public class TlsConfigInvalidCertsTest extends IntegrationTest {

  @Autowired private X509TrustManager trustManager;

  @Test
  @DisplayName("Should get no extra cert because cert in folder is invalid")
  void tlsContextCustomWithWrongFolder() throws Exception {
    TrustManagerFactory manager =
        TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
    manager.init((KeyStore) null);
    X509TrustManager defaultX509CertificateTrustManager =
        (X509TrustManager) manager.getTrustManagers()[0];

    assertThat(defaultX509CertificateTrustManager).isNotEqualTo(trustManager);
    assertThat(defaultX509CertificateTrustManager.getAcceptedIssuers())
        .isEqualTo(trustManager.getAcceptedIssuers());
  }
}
