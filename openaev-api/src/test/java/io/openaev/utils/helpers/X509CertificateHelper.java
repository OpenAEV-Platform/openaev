package io.openaev.utils.helpers;

import java.io.FileInputStream;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;

public class X509CertificateHelper {
  public static X509Certificate readCertificate(String filePath) throws Exception {
    try (FileInputStream myKey = new FileInputStream(filePath)) {
      CertificateFactory certFactory = CertificateFactory.getInstance("X.509");
      try {
        return (X509Certificate) certFactory.generateCertificate(myKey);
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    }
  }
}
