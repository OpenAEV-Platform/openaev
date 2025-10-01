package io.openaev.authorisation;

import io.openaev.config.OpenAEVConfig;
import jakarta.annotation.Resource;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class TlsConfig {

  @Resource private OpenAEVConfig openAEVConfig;

  /** Get files paths ".pem" from directory */
  private Set<String> getFilesPaths(String dir) {
    try (Stream<Path> stream = Files.list(Paths.get(dir))) {
      return stream
          .filter(file -> !Files.isDirectory(file))
          .map(path -> path.toAbsolutePath().normalize())
          .map(Path::toString)
          .filter(path -> path.endsWith(".pem"))
          .collect(Collectors.toSet());
    } catch (Exception e) {
      log.info("No extra trusted certificate found in {}", dir, e);
      return new HashSet<>();
    }
  }

  /** Get extra trusted certs from files */
  private List<X509Certificate> getExtraCerts(Set<String> filesPaths)
      throws IOException, CertificateException {
    List<X509Certificate> extraCerts = new ArrayList<>();
    for (String filePath : filesPaths) {
      try (FileInputStream myKey = new FileInputStream(filePath)) {
        CertificateFactory certFactory = CertificateFactory.getInstance("X.509");
        try {
          X509Certificate cert = (X509Certificate) certFactory.generateCertificate(myKey);
          Pattern pattern = Pattern.compile("CN=([^,]+)");
          Matcher matcher = pattern.matcher(cert.getSubjectX500Principal().getName());
          String name = matcher.find() ? matcher.group(1) : filePath;
          if (!extraCerts.contains(cert)) {
            cert.checkValidity();
            extraCerts.add(cert);
            log.info("Added extra trusted certificate: {}", name);
          } else {
            log.info("Extra trusted certificate duplicated: {}", name);
          }
        } catch (CertificateException e) {
          log.error("Extra trusted certificate {} is not a valid PEM certificate", filePath, e);
        }
      }
    }
    return extraCerts;
  }

  private X509TrustManager getDefaultTrustManager()
      throws NoSuchAlgorithmException, KeyStoreException {
    TrustManagerFactory trustManagerFactory =
        TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
    trustManagerFactory.init((KeyStore) null);

    X509TrustManager defaultX509CertificateTrustManager = null;
    for (TrustManager trustManager : trustManagerFactory.getTrustManagers()) {
      if (trustManager instanceof X509TrustManager x509TrustManager) {
        defaultX509CertificateTrustManager = x509TrustManager;
        break;
      }
    }

    return defaultX509CertificateTrustManager;
  }

  private Optional<X509TrustManager> getAdditionalTrustManager()
      throws NoSuchAlgorithmException, KeyStoreException, CertificateException, IOException {
    Set<String> filesPaths = getFilesPaths(openAEVConfig.getExtraTrustedCertsDir());

    // early return; if there aren't any extra certs
    // don't bother building a trust manager
    if (filesPaths.isEmpty()) {
      return Optional.empty();
    }

    List<X509Certificate> extraCerts = getExtraCerts(filesPaths);

    // in-memory keystore
    KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
    keyStore.load(null, null);
    for (X509Certificate cert : extraCerts) {
      keyStore.setCertificateEntry(cert.getSubjectX500Principal().getName(), cert);
    }

    TrustManagerFactory trustManagerFactory =
        TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
    trustManagerFactory.init(keyStore);

    return Arrays.stream(trustManagerFactory.getTrustManagers())
        .filter(tm -> tm instanceof X509TrustManager)
        .map(tm -> Optional.of((X509TrustManager) tm))
        .findFirst()
        .orElse(Optional.empty());
  }

  /**
   * Get and set extra trusted certificates to the java trust manager
   *
   * @return tls context with our extra trusted certificates
   * @throws Exception exception
   */
  @Bean
  public X509TrustManager tlsContextCustom() throws Exception {
    X509TrustManager defaultX509CertificateTrustManager = getDefaultTrustManager();
    Optional<X509TrustManager> additionalTrustManager = getAdditionalTrustManager();

    return new X509TrustManager() {
      private X509Certificate[] mergeCertificates() {
        ArrayList<X509Certificate> resultingCerts =
            new ArrayList<>(Arrays.asList(defaultX509CertificateTrustManager.getAcceptedIssuers()));
        additionalTrustManager.ifPresent(
            tm -> resultingCerts.addAll(Arrays.asList(tm.getAcceptedIssuers())));
        return resultingCerts.toArray(new X509Certificate[0]);
      }

      @Override
      public X509Certificate[] getAcceptedIssuers() {
        return mergeCertificates();
      }

      @Override
      public void checkServerTrusted(X509Certificate[] chain, String authType)
          throws CertificateException {
        try {
          defaultX509CertificateTrustManager.checkServerTrusted(chain, authType);
        } catch (Exception e) {
          if (additionalTrustManager.isPresent()) {
            additionalTrustManager.get().checkServerTrusted(chain, authType);
          } else {
            throw e;
          }
        }
      }

      @Override
      public void checkClientTrusted(X509Certificate[] chain, String authType)
          throws CertificateException {
        try {
          defaultX509CertificateTrustManager.checkClientTrusted(chain, authType);
        } catch (Exception e) {
          if (additionalTrustManager.isPresent()) {
            additionalTrustManager.get().checkClientTrusted(chain, authType);
          } else {
            throw e;
          }
        }
      }
    };
  }
}
