package io.openaev.ee;

import static io.openaev.ee.XtmLicenseVerifier.OID_LEGACY_PRODUCT;
import static io.openaev.ee.XtmLicenseVerifier.OID_LEGACY_TYPE;
import static io.openaev.ee.XtmLicenseVerifier.OID_OPENAEV_SUBLICENSE;
import static io.openaev.ee.XtmLicenseVerifier.OID_PRODUCT;
import static io.openaev.ee.XtmLicenseVerifier.OID_TYPE;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.time.temporal.ChronoUnit.DAYS;
import static java.time.temporal.ChronoUnit.MINUTES;
import static java.time.temporal.ChronoUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.Signature;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Date;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.bouncycastle.asn1.ASN1EncodableVector;
import org.bouncycastle.asn1.ASN1Encoding;
import org.bouncycastle.asn1.ASN1Integer;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.DERBitString;
import org.bouncycastle.asn1.DERNull;
import org.bouncycastle.asn1.DERSequence;
import org.bouncycastle.asn1.DERUTF8String;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.AlgorithmIdentifier;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.Extensions;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle.asn1.x509.TBSCertificate;
import org.bouncycastle.asn1.x509.Time;
import org.bouncycastle.asn1.x509.V3TBSCertificateGenerator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("XTM license verification")
class XtmLicenseVerifierTest {

  private static final Instant NOW = Instant.parse("2026-09-26T12:00:00Z");
  private static final String PLATFORM_ID = "4a0f1a6e-5b6e-4a51-8f6a-0d6b2b0c9a11";
  private static final String OTHER_PLATFORM_ID = "9c2e7d4b-1f3a-4e8b-b5d6-7a8c9e0f1b22";
  private static final String OPENCTI_SUBLICENSE_OID = "1.3.6.1.4.1.62944.50";
  private static final String SHA256_WITH_RSA = "1.2.840.113549.1.1.11";
  private static final String SHA1_WITH_RSA = "1.2.840.113549.1.1.5";
  private static final X500Name CA_NAME =
      new X500Name("CN=Filigran CA CERT, C=FR, L=Paris, O=Filigran, OU=Filigran");

  private static KeyPair caKeys;
  private static KeyPair foreignKeys;
  private static KeyPair licenseKeys;
  private static X509Certificate testCa;

  @BeforeAll
  static void createCertificateAuthorities() throws Exception {
    KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
    generator.initialize(2048);
    caKeys = generator.generateKeyPair();
    foreignKeys = generator.generateKeyPair();
    licenseKeys = generator.generateKeyPair();
    byte[] caDer =
        sign(
            caKeys,
            "SHA256withRSA",
            SHA256_WITH_RSA,
            CA_NAME,
            caKeys.getPublic(),
            NOW.minus(365, DAYS),
            NOW.plus(3650, DAYS),
            Map.of());
    testCa = Pem.parseCert(toPem(caDer));
  }

  // -- Helpers --

  private static XtmLicense verify(String pem) throws XtmLicenseException {
    return XtmLicenseVerifier.verify(pem, PLATFORM_ID, null, NOW, testCa);
  }

  private static XtmLicense verify(String pem, String platformId) throws XtmLicenseException {
    return XtmLicenseVerifier.verify(pem, platformId, null, NOW, testCa);
  }

  private static XtmLicense verifyAt(String pem, Instant now) throws XtmLicenseException {
    return XtmLicenseVerifier.verify(pem, PLATFORM_ID, null, now, testCa);
  }

  private static XtmLicense verifyCi(String pem, Instant instanceCreationDate)
      throws XtmLicenseException {
    return XtmLicenseVerifier.verify(pem, PLATFORM_ID, instanceCreationDate, NOW, testCa);
  }

  private static void assertRefused(String pem, String reason) {
    assertThatThrownBy(() -> verify(pem))
        .isInstanceOf(XtmLicenseException.class)
        .hasMessageContaining(reason);
  }

  private static String toPem(byte[] der) {
    return "-----BEGIN CERTIFICATE-----\n"
        + Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.US_ASCII)).encodeToString(der)
        + "\n-----END CERTIFICATE-----\n";
  }

  private static byte[] sign(
      KeyPair signer,
      String algorithm,
      String algorithmOid,
      X500Name subject,
      PublicKey subjectKey,
      Instant notBefore,
      Instant notAfter,
      Map<String, byte[]> extensionValues)
      throws Exception {
    AlgorithmIdentifier signatureAlgorithm =
        new AlgorithmIdentifier(new ASN1ObjectIdentifier(algorithmOid), DERNull.INSTANCE);
    V3TBSCertificateGenerator generator = new V3TBSCertificateGenerator();
    // Filigran certificates carry serial number 0.
    generator.setSerialNumber(new ASN1Integer(BigInteger.ZERO));
    generator.setSignature(signatureAlgorithm);
    generator.setIssuer(CA_NAME);
    generator.setSubject(subject);
    generator.setStartDate(new Time(Date.from(notBefore)));
    generator.setEndDate(new Time(Date.from(notAfter)));
    generator.setSubjectPublicKeyInfo(SubjectPublicKeyInfo.getInstance(subjectKey.getEncoded()));
    if (!extensionValues.isEmpty()) {
      generator.setExtensions(
          new Extensions(
              extensionValues.entrySet().stream()
                  .map(
                      e -> new Extension(new ASN1ObjectIdentifier(e.getKey()), false, e.getValue()))
                  .toArray(Extension[]::new)));
    }
    TBSCertificate tbs = generator.generateTBSCertificate();
    Signature signature = Signature.getInstance(algorithm);
    signature.initSign(signer.getPrivate());
    signature.update(tbs.getEncoded(ASN1Encoding.DER));
    ASN1EncodableVector certificate = new ASN1EncodableVector();
    certificate.add(tbs);
    certificate.add(signatureAlgorithm);
    certificate.add(new DERBitString(signature.sign()));
    return new DERSequence(certificate).getEncoded(ASN1Encoding.DER);
  }

  private static byte[] replace(byte[] source, byte[] target, byte[] replacement) {
    for (int i = 0; i <= source.length - target.length; i++) {
      if (Arrays.equals(source, i, i + target.length, target, 0, target.length)) {
        byte[] result = source.clone();
        System.arraycopy(replacement, 0, result, i, replacement.length);
        return result;
      }
    }
    throw new IllegalArgumentException("Target bytes not found");
  }

  /**
   * A license certificate as filigran-licenses issues it: the extension values are the UTF-8 text
   * itself, issued by the test CA unless told otherwise.
   */
  private static final class LicenseCertificate {

    private final Map<String, byte[]> extensionValues = new LinkedHashMap<>();
    private Instant notBefore = NOW.minus(30, DAYS);
    private Instant notAfter = NOW.plus(335, DAYS);
    private KeyPair signer = caKeys;
    private String signatureAlgorithm = "SHA256withRSA";
    private String signatureAlgorithmOid = SHA256_WITH_RSA;

    LicenseCertificate() {
      text(OID_PRODUCT, "filigran xtm");
      text(OID_TYPE, "standard");
      subLicense("[\"global\"]");
    }

    /** Sets an extension to raw UTF-8 text, or removes it when {@code value} is null. */
    LicenseCertificate text(String oid, String value) {
      if (value == null) {
        extensionValues.remove(oid);
      } else {
        extensionValues.put(oid, value.getBytes(UTF_8));
      }
      return this;
    }

    /** Sets an extension to a DER UTF8String wrapping the text, which XTM One also reads. */
    LicenseCertificate derString(String oid, String value) throws Exception {
      extensionValues.put(oid, new DERUTF8String(value).getEncoded(ASN1Encoding.DER));
      return this;
    }

    LicenseCertificate subLicense(String json) {
      return text(OID_OPENAEV_SUBLICENSE, json);
    }

    LicenseCertificate type(String type) {
      return text(OID_TYPE, type);
    }

    LicenseCertificate validity(Instant notBefore, Instant notAfter) {
      this.notBefore = notBefore;
      this.notAfter = notAfter;
      return this;
    }

    LicenseCertificate signedBy(KeyPair signer) {
      this.signer = signer;
      return this;
    }

    LicenseCertificate signatureAlgorithm(String algorithm, String oid) {
      this.signatureAlgorithm = algorithm;
      this.signatureAlgorithmOid = oid;
      return this;
    }

    byte[] der() throws Exception {
      return sign(
          signer,
          signatureAlgorithm,
          signatureAlgorithmOid,
          new X500Name("CN=Filigran XTM license, O=ACME, OU=global"),
          licenseKeys.getPublic(),
          notBefore,
          notAfter,
          extensionValues);
    }

    String pem() throws Exception {
      return toPem(der());
    }
  }

  // -- Tests --

  @Nested
  @DisplayName("Granted")
  class Granted {

    @Test
    @DisplayName("Given a global OpenAEV sub-license should grant")
    void given_globalSubLicense_should_grant() throws Exception {
      // Arrange
      String pem = new LicenseCertificate().pem();

      // Act
      XtmLicense license = verify(pem);

      // Assert
      assertThat(license.type()).isEqualTo("standard");
      assertThat(license.customer()).isEqualTo("ACME");
      assertThat(license.globalGrant()).isTrue();
      assertThat(license.coveredPlatform()).isEqualTo("global");
      assertThat(license.startDate()).isEqualTo(NOW.minus(30, DAYS));
      assertThat(license.expirationDate()).isEqualTo(NOW.plus(335, DAYS));
      assertThat(Pem.parseSingleCert(pem).getSerialNumber()).isZero();
    }

    @Test
    @DisplayName("Given a sub-license listing this platform id should grant")
    void given_ownPlatformIdSubLicense_should_grant() throws Exception {
      // Arrange
      String pem =
          new LicenseCertificate()
              .subLicense("[\"" + OTHER_PLATFORM_ID + "\", \"" + PLATFORM_ID + "\"]")
              .pem();

      // Act
      XtmLicense license = verify(pem);

      // Assert
      assertThat(license.globalGrant()).isFalse();
      assertThat(license.coveredPlatform()).isEqualTo(PLATFORM_ID);
    }

    @Test
    @DisplayName("Given a sub-license longer than 127 bytes should read it whole and grant")
    void given_longSubLicense_should_grant() throws Exception {
      // Arrange
      List<String> ids = new ArrayList<>();
      for (int i = 0; i < 5; i++) {
        ids.add(OTHER_PLATFORM_ID.substring(0, 35) + i);
      }
      ids.add(PLATFORM_ID);
      String subLicense = "[\"" + String.join("\",\"", ids) + "\"]";
      String pem = new LicenseCertificate().subLicense(subLicense).pem();

      // Act
      XtmLicense license = verify(pem);

      // Assert
      assertThat(subLicense.getBytes(UTF_8).length).isGreaterThan(127);
      assertThat(license.globalGrant()).isFalse();
    }

    @Test
    @DisplayName("Given extension values wrapped in a DER string should grant")
    void given_derStringExtensions_should_grant() throws Exception {
      // Arrange
      String pem =
          new LicenseCertificate()
              .derString(OID_PRODUCT, "filigran xtm")
              .derString(OID_TYPE, "lts")
              .derString(OID_OPENAEV_SUBLICENSE, "[\"global\"]")
              .pem();

      // Act
      XtmLicense license = verify(pem);

      // Assert
      assertThat(license.type()).isEqualTo("lts");
    }

    @Test
    @DisplayName("Given only the legacy product and type extensions should grant")
    void given_legacyExtensions_should_grant() throws Exception {
      // Arrange
      String pem =
          new LicenseCertificate()
              .text(OID_PRODUCT, null)
              .text(OID_LEGACY_PRODUCT, "filigran xtm")
              .text(OID_TYPE, null)
              .text(OID_LEGACY_TYPE, "nfr")
              .pem();

      // Act
      XtmLicense license = verify(pem);

      // Assert
      assertThat(license.type()).isEqualTo("nfr");
    }

    @Test
    @DisplayName("Given no type extension should verify as a trial license")
    void given_missingType_should_verifyAsTrial() throws Exception {
      // Arrange
      String pem = new LicenseCertificate().type(null).pem();

      // Act
      XtmLicense license = verify(pem);

      // Assert
      assertThat(license.type()).isEqualTo("trial");
    }

    @Test
    @DisplayName("Given no platform id and a global sub-license should grant")
    void given_noPlatformIdAndGlobalSubLicense_should_grant() throws Exception {
      // Arrange
      String pem = new LicenseCertificate().pem();

      // Act
      XtmLicense license = verify(pem, "");

      // Assert
      assertThat(license.globalGrant()).isTrue();
    }
  }

  @Nested
  @DisplayName("Not granted")
  class NotGranted {

    @Test
    @DisplayName("Given no certificate should refuse")
    void given_noCertificate_should_refuse() {
      assertRefused(null, "no certificate");
      assertRefused("  ", "no certificate");
    }

    @Test
    @DisplayName("Given a sub-license for another platform id only should refuse")
    void given_otherPlatformIdSubLicense_should_refuse() throws Exception {
      String pem = new LicenseCertificate().subLicense("[\"" + OTHER_PLATFORM_ID + "\"]").pem();

      assertRefused(pem, "does not sub-license this platform");
    }

    @Test
    @DisplayName("Given this platform id with another case should refuse")
    void given_platformIdWithAnotherCase_should_refuse() throws Exception {
      String pem =
          new LicenseCertificate().subLicense("[\"" + PLATFORM_ID.toUpperCase() + "\"]").pem();

      assertRefused(pem, "does not sub-license this platform");
    }

    @Test
    @DisplayName("Given no platform id and a sub-license by platform id only should refuse")
    void given_noPlatformIdAndPlatformSubLicense_should_refuse() throws Exception {
      String pem = new LicenseCertificate().subLicense("[\"" + PLATFORM_ID + "\"]").pem();

      assertThatThrownBy(() -> verify(pem, ""))
          .isInstanceOf(XtmLicenseException.class)
          .hasMessageContaining("does not sub-license this platform");
    }

    @Test
    @DisplayName("Given this platform listed under another product's sub-license OID should refuse")
    void given_platformUnderAnotherProductOid_should_refuse() throws Exception {
      String pem =
          new LicenseCertificate()
              .subLicense(null)
              .text(OPENCTI_SUBLICENSE_OID, "[\"global\", \"" + PLATFORM_ID + "\"]")
              .pem();

      assertRefused(pem, "does not sub-license this platform");
    }

    @ParameterizedTest(name = "sub-license {0}")
    @ValueSource(
        strings = {"global", "[\"global\"] trailing", "{\"ids\": [\"global\"]}", "[1, true, null]"})
    @DisplayName("Given a sub-license that is not a JSON array of strings should refuse")
    void given_malformedSubLicense_should_refuse(String subLicense) throws Exception {
      String pem = new LicenseCertificate().subLicense(subLicense).pem();

      assertRefused(pem, "does not sub-license this platform");
    }

    @Test
    @DisplayName("Given another product should refuse")
    void given_otherProduct_should_refuse() throws Exception {
      assertRefused(
          new LicenseCertificate().text(OID_PRODUCT, "openaev").pem(),
          "not a Filigran XTM license");
      assertRefused(
          new LicenseCertificate().text(OID_PRODUCT, null).pem(), "not a Filigran XTM license");
    }

    @Test
    @DisplayName("Given a wrong standard product should ignore the legacy one and refuse")
    void given_wrongStandardAndValidLegacyProduct_should_refuse() throws Exception {
      String pem =
          new LicenseCertificate()
              .text(OID_PRODUCT, "openaev")
              .text(OID_LEGACY_PRODUCT, "filigran xtm")
              .pem();

      assertRefused(pem, "not a Filigran XTM license");
    }

    @Test
    @DisplayName("Given an unknown license type should refuse")
    void given_unknownType_should_refuse() throws Exception {
      assertRefused(new LicenseCertificate().type("enterprise").pem(), "license type");
    }

    @Test
    @DisplayName("Given a certificate signed by another CA should refuse")
    void given_foreignCa_should_refuse() throws Exception {
      assertRefused(
          new LicenseCertificate().signedBy(foreignKeys).pem(), "signature does not verify");
    }

    @Test
    @DisplayName("Given a tampered sub-license should refuse")
    void given_tamperedCertificate_should_refuse() throws Exception {
      // Arrange
      byte[] genuine = new LicenseCertificate().subLicense("[\"" + OTHER_PLATFORM_ID + "\"]").der();
      byte[] tampered =
          replace(genuine, OTHER_PLATFORM_ID.getBytes(UTF_8), PLATFORM_ID.getBytes(UTF_8));

      // Act & Assert
      assertRefused(toPem(genuine), "does not sub-license this platform");
      assertRefused(toPem(tampered), "signature does not verify");
    }

    @Test
    @DisplayName(
        "Given a signature algorithm other than RSASSA-PKCS1-v1_5 with SHA-2 should refuse")
    void given_sha1Signature_should_refuse() throws Exception {
      String pem = new LicenseCertificate().signatureAlgorithm("SHA1withRSA", SHA1_WITH_RSA).pem();

      assertRefused(pem, "signature algorithm");
    }

    @Test
    @DisplayName("Given anything but exactly one PEM certificate should refuse")
    void given_notExactlyOneCertificate_should_refuse() throws Exception {
      byte[] der = new LicenseCertificate().der();
      String pem = toPem(der);
      byte[] trailingBytes = Arrays.copyOf(der, der.length + 2);
      trailingBytes[der.length] = 0x05;

      assertRefused(pem + pem, "not exactly one PEM certificate");
      assertRefused(
          pem + "-----BEGIN PRIVATE KEY-----\nMIIBVQIBADANBg==\n-----END PRIVATE KEY-----\n",
          "not exactly one PEM certificate");
      assertRefused(toPem(trailingBytes), "not exactly one PEM certificate");
      assertRefused("not a certificate", "not exactly one PEM certificate");
    }
  }

  @Nested
  @DisplayName("Validity dates")
  class ValidityDates {

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"standard", "lts", "nfr"})
    @DisplayName("Given a license in its 90-day grace period should grant, and refuse after it")
    void given_gracePeriod_should_grantUntilItsEnd(String type) throws Exception {
      // Arrange
      Instant notAfter = NOW.minus(60, DAYS);
      String pem =
          new LicenseCertificate().type(type).validity(NOW.minus(425, DAYS), notAfter).pem();
      Instant graceEnd = notAfter.plus(90, DAYS);

      // Act
      XtmLicense license = verify(pem);

      // Assert
      assertThat(license.validUntil()).isEqualTo(graceEnd);
      assertThat(verifyAt(pem, graceEnd.minus(1, SECONDS))).isEqualTo(license);
      assertThatThrownBy(() -> verifyAt(pem, graceEnd))
          .isInstanceOf(XtmLicenseException.class)
          .hasMessageContaining("grace period ended");
    }

    @Test
    @DisplayName("Given a standard license expired beyond its grace period should refuse")
    void given_expiredBeyondGrace_should_refuse() throws Exception {
      String pem =
          new LicenseCertificate().validity(NOW.minus(500, DAYS), NOW.minus(91, DAYS)).pem();

      assertRefused(pem, "grace period ended");
    }

    @Test
    @DisplayName("Given a standard license that has not started yet should grant")
    void given_standardNotStartedYet_should_grant() throws Exception {
      String pem = new LicenseCertificate().validity(NOW.plus(10, DAYS), NOW.plus(375, DAYS)).pem();

      assertThat(verify(pem).type()).isEqualTo("standard");
    }

    @Test
    @DisplayName("Given a trial license should grant within its dates only, with no grace")
    void given_trialLicense_should_grantWithinItsDatesOnly() throws Exception {
      // Arrange
      Instant notBefore = NOW.minus(10, DAYS);
      Instant notAfter = NOW.plus(20, DAYS);
      String pem = new LicenseCertificate().type("trial").validity(notBefore, notAfter).pem();

      // Act & Assert
      assertThat(verifyAt(pem, notBefore).type()).isEqualTo("trial");
      assertThat(verifyAt(pem, notAfter).type()).isEqualTo("trial");
      assertThatThrownBy(() -> verifyAt(pem, notAfter.plus(1, SECONDS)))
          .isInstanceOf(XtmLicenseException.class)
          .hasMessageContaining("trial license expired");
      assertThatThrownBy(() -> verifyAt(pem, notBefore.minus(1, SECONDS)))
          .isInstanceOf(XtmLicenseException.class)
          .hasMessageContaining("not valid before");
    }

    @Test
    @DisplayName("Given a ci license should end 45 minutes after this instance was created")
    void given_ciLicense_should_endFortyFiveMinutesAfterInstanceCreation() throws Exception {
      // Arrange
      String pem =
          new LicenseCertificate()
              .type("ci")
              .validity(NOW.minus(1, DAYS), NOW.plus(364, DAYS))
              .pem();
      Instant createdAt = NOW.minus(10, MINUTES);

      // Act
      XtmLicense license = verifyCi(pem, createdAt);

      // Assert
      assertThat(license.expirationDate()).isEqualTo(createdAt.plus(45, MINUTES));
      assertThat(license.isActiveAt(createdAt.plus(45, MINUTES))).isTrue();
      assertThat(license.isActiveAt(createdAt.plus(46, MINUTES))).isFalse();
    }

    @Test
    @DisplayName("Given a ci license on an instance created over 45 minutes ago should refuse")
    void given_ciLicenseOnOlderInstance_should_refuse() throws Exception {
      String pem =
          new LicenseCertificate()
              .type("ci")
              .validity(NOW.minus(1, DAYS), NOW.plus(364, DAYS))
              .pem();

      assertThatThrownBy(() -> verifyCi(pem, NOW.minus(46, MINUTES)))
          .isInstanceOf(XtmLicenseException.class)
          .hasMessageContaining("ci license expired");
    }

    @Test
    @DisplayName("Given a ci license started over 365 days ago should refuse")
    void given_ciLicenseStartedOverAYearAgo_should_refuse() throws Exception {
      String pem =
          new LicenseCertificate()
              .type("ci")
              .validity(NOW.minus(366, DAYS), NOW.plus(364, DAYS))
              .pem();

      assertThatThrownBy(() -> verifyCi(pem, NOW.minus(10, MINUTES)))
          .isInstanceOf(XtmLicenseException.class)
          .hasMessageContaining("ci license expired");
    }

    @Test
    @DisplayName("Given a ci license should ignore its notAfter")
    void given_ciLicense_should_ignoreNotAfter() throws Exception {
      String pem =
          new LicenseCertificate()
              .type("ci")
              .validity(NOW.minus(2, DAYS), NOW.minus(1, DAYS))
              .pem();

      assertThat(verifyCi(pem, NOW.minus(10, MINUTES)).type()).isEqualTo("ci");
    }

    @Test
    @DisplayName("Given a ci license and an unknown instance creation date should refuse")
    void given_ciLicenseWithoutInstanceCreationDate_should_refuse() throws Exception {
      String pem = new LicenseCertificate().type("ci").pem();

      assertThatThrownBy(() -> verifyCi(pem, null))
          .isInstanceOf(XtmLicenseException.class)
          .hasMessageContaining("creation date of this instance");
    }
  }

  @Nested
  @DisplayName("Pinned trust anchor")
  class PinnedTrustAnchor {

    @Test
    @DisplayName("Given the pinned XTM CA should match the fingerprints XTM One publishes")
    void given_pinnedXtmCa_should_matchPublishedFingerprints() throws Exception {
      // Act
      X509Certificate xtmCa = Pem.getXtmCaCert();
      byte[] certificateDigest = MessageDigest.getInstance("SHA-256").digest(xtmCa.getEncoded());
      byte[] keyDigest =
          MessageDigest.getInstance("SHA-256").digest(xtmCa.getPublicKey().getEncoded());

      // Assert
      assertThat(HexFormat.ofDelimiter(":").withUpperCase().formatHex(certificateDigest))
          .isEqualTo(
              "43:6E:0B:82:14:0F:43:CA:0E:15:3F:1B:27:AC:E3:B3:88:F1:9F:24:BC:6C:36:6E:C1:24:91:87:45:BC:40:64");
      assertThat(HexFormat.of().formatHex(keyDigest))
          .isEqualTo("9ef09cc6ab116344afd1a47ae5b777842b8c44fae78d223440d19bb7bc7d84ec");
    }

    @Test
    @DisplayName("Given a license the pinned XTM CA did not sign should refuse")
    void given_licenseFromAnotherCa_should_beRefusedByThePinnedAnchor() throws Exception {
      String pem = new LicenseCertificate().pem();

      assertThatThrownBy(() -> XtmLicenseVerifier.verify(pem, PLATFORM_ID, null, NOW))
          .isInstanceOf(XtmLicenseException.class)
          .hasMessageContaining("signature does not verify");
    }
  }
}
