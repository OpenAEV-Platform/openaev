package io.openaev.utils.helpers;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

/** Trust managers standing in for the platform's {@code X509TrustManager} in unit tests. */
public final class TlsTestSupport {

  /** The CA that signed {@code localhost.crt}, the certificate the TLS mock servers present. */
  public static final String LOCALHOST_CA_PEM = "src/test/resources/tls/localhost/ca.pem";

  public static final String LOCALHOST_CERTIFICATE =
      "src/test/resources/tls/localhost/localhost.crt";
  public static final String LOCALHOST_PRIVATE_KEY =
      "src/test/resources/tls/localhost/localhost.key";

  private TlsTestSupport() {}

  /** What the platform trusts when no extra certificate is configured: the JVM's own roots. */
  public static X509TrustManager jdkTrustManager() {
    return trustManagerOf(null);
  }

  /** What the platform trusts when the CA of the TLS mock servers is an extra trusted cert. */
  public static X509TrustManager trustingLocalhostCa() {
    try (InputStream pem = new FileInputStream(LOCALHOST_CA_PEM)) {
      Certificate ca = CertificateFactory.getInstance("X.509").generateCertificate(pem);
      KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
      keyStore.load(null, null);
      keyStore.setCertificateEntry("localhost-ca", ca);
      return trustManagerOf(keyStore);
    } catch (IOException | GeneralSecurityException e) {
      throw new IllegalStateException("Cannot load " + LOCALHOST_CA_PEM, e);
    }
  }

  private static X509TrustManager trustManagerOf(KeyStore keyStore) {
    try {
      TrustManagerFactory factory =
          TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
      factory.init(keyStore);
      for (TrustManager trustManager : factory.getTrustManagers()) {
        if (trustManager instanceof X509TrustManager x509) {
          return x509;
        }
      }
      throw new IllegalStateException("No X509TrustManager available");
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException(e);
    }
  }
}
