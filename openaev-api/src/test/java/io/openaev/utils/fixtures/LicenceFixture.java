package io.openaev.utils.fixtures;

import static io.openaev.ee.EnterpriseEditionService.*;

import io.openaev.ee.LicenseTypeEnum;
import java.io.IOException;
import java.io.StringWriter;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import org.bouncycastle.asn1.*;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.jcajce.JcaPEMWriter;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

/**
 * Proposes various forms of EE licence certificate fixtures NOTE: the signature will NOT be
 * validated by the embedded CA certificate. Tests making use of this fixture must explicitly force
 * {@link io.openaev.ee.EnterpriseEditionService#verifyCertificate(X509Certificate,
 * X509Certificate)} to return true if needing a "valid" EE licence.
 */
public class LicenceFixture {

  public static X509Certificate getLicenceCertificateForGlobal() throws Exception {
    return getLicenceCertificateForInstanceId("global");
  }

  public static X509Certificate getLicenceCertificateForInstanceId(String instanceId)
      throws Exception {
    KeyPair keyPair = KeyPairGenerator.getInstance("RSA").genKeyPair();
    SubjectPublicKeyInfo subPubKeyInfo =
        SubjectPublicKeyInfo.getInstance(keyPair.getPublic().getEncoded());
    Instant now = Instant.now();
    Date validFrom = Date.from(now.minus(3, ChronoUnit.DAYS));
    Date validTo = Date.from(now.plus(10, ChronoUnit.DAYS));
    X509v3CertificateBuilder certBuilder =
        new X509v3CertificateBuilder(
            new X500Name("CN=LicenceFixture,O=Filigran,OU=OAEV"),
            BigInteger.ONE,
            validFrom,
            validTo,
            new X500Name("CN=OAEV Test Instance,O=Filigran,OU=%s".formatted(instanceId)),
            subPubKeyInfo);
    certBuilder.addExtension(
        new Extension(
            new ASN1ObjectIdentifier(LICENSE_OPTION_PRODUCT),
            false,
            new DEROctetString("openaev".getBytes(StandardCharsets.UTF_8))));
    certBuilder.addExtension(
        new Extension(
            new ASN1ObjectIdentifier(LICENSE_OPTION_CREATOR),
            false,
            new DEROctetString("LicenceFixture invocation".getBytes(StandardCharsets.UTF_8))));
    certBuilder.addExtension(
        new Extension(
            new ASN1ObjectIdentifier(LICENSE_OPTION_TYPE),
            false,
            new DEROctetString(LicenseTypeEnum.nfr.name().getBytes(StandardCharsets.UTF_8))));
    ContentSigner signer =
        new JcaContentSignerBuilder("SHA256WithRSA")
            .setProvider(new BouncyCastleProvider())
            .build(keyPair.getPrivate());
    return new JcaX509CertificateConverter().getCertificate(certBuilder.build(signer));
  }

  public static String getLicenceStringForGlobal() throws Exception {
    return writeToString(getLicenceCertificateForGlobal());
  }

  public static String getLicenceStringForInstanceId(String instanceId) throws Exception {
    return writeToString(getLicenceCertificateForInstanceId(instanceId));
  }

  private static String writeToString(X509Certificate certificate) throws IOException {
    StringWriter sw = new StringWriter();
    try (JcaPEMWriter pemWriter = new JcaPEMWriter(sw)) {
      pemWriter.writeObject(certificate);
    }
    return sw.toString();
  }
}
