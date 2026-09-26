package io.openaev.ee;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.PublicKey;
import java.security.Signature;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.naming.InvalidNameException;
import javax.naming.ldap.LdapName;
import javax.naming.ldap.Rdn;

/**
 * Verifies the Filigran XTM license certificate that XTM One returns at registration ({@code
 * xtm_license_pem}): the proof that the XTM license installed on XTM One sub-licenses this
 * platform. The operator chooses the host that answers as XTM One, so nothing else in the answer
 * proves anything ({@code ee_enabled} and {@code ee_sources} are advisory): only a certificate
 * signed by Filigran, verified against the anchor pinned in {@link Pem}, does.
 *
 * <p>Runs the checks XTM One runs itself ({@code decode_license_pem}), for the same outcome. The
 * signature is verified directly against the pinned CA public key, with no chain and no path
 * validation, which is why the PKIX check of this platform's own license is not reused: it would
 * also refuse the grace period and the early renewals XTM One accepts. Then come the product, the
 * type, XTM One's validity rule and this product's own sub-license extension.
 */
public final class XtmLicenseVerifier {

  static final String OID_TYPE = "1.3.6.1.4.1.62944.10";
  static final String OID_LEGACY_TYPE = "2.14521.4.4.10";
  static final String OID_PRODUCT = "1.3.6.1.4.1.62944.20";
  static final String OID_LEGACY_PRODUCT = "2.14521.4.4.20";

  /** OpenAEV's sub-license extension (XTM One maps the legacy {@code openbas} type to it). */
  static final String OID_OPENAEV_SUBLICENSE = "1.3.6.1.4.1.62944.60";

  static final String XTM_PRODUCT = "filigran xtm";
  static final String GLOBAL_GRANT = XtmLicense.GLOBAL_GRANT;

  /**
   * A {@code ci} license ends at {@code min(instance creation + 45 minutes, start + 365 days)},
   * whatever its {@code notAfter} says: it must not outlive the pipeline that created the instance.
   */
  static final Duration CI_INSTANCE_WINDOW = Duration.ofMinutes(45);

  static final Duration CI_MAX_VALIDITY = Duration.ofDays(365);

  private static final Set<String> LICENSE_TYPES = Set.of("standard", "lts", "trial", "nfr", "ci");

  /** RSASSA-PKCS1-v1_5 with a SHA-2 digest, by signature algorithm OID; Filigran signs SHA-256. */
  private static final Map<String, String> PKCS1_V15_SIGNATURES =
      Map.of(
          "1.2.840.113549.1.1.14", "SHA224withRSA",
          "1.2.840.113549.1.1.11", "SHA256withRSA",
          "1.2.840.113549.1.1.12", "SHA384withRSA",
          "1.2.840.113549.1.1.13", "SHA512withRSA");

  private static final ObjectReader JSON_READER =
      new ObjectMapper()
          .readerFor(JsonNode.class)
          .with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);

  private XtmLicenseVerifier() {}

  /**
   * Verifies an XTM license certificate against the XTM CA pinned in this build.
   *
   * @param pem the {@code xtm_license_pem} of the registration answer
   * @param platformId the platform id this platform registers with; when blank, only a {@code
   *     global} grant covers it
   * @param instanceCreationDate this instance's creation date, which bounds a {@code ci} license;
   *     {@code null} when unknown, which refuses a {@code ci} license
   * @param now the instant the validity rule is applied at
   * @return the verified license
   * @throws XtmLicenseException with the reason, when the certificate grants nothing
   */
  public static XtmLicense verify(
      String pem, String platformId, Instant instanceCreationDate, Instant now)
      throws XtmLicenseException {
    X509Certificate trustAnchor;
    try {
      trustAnchor = Pem.getXtmCaCert();
    } catch (Exception e) {
      throw new IllegalStateException("The XTM CA pinned in this build cannot be parsed", e);
    }
    return verify(pem, platformId, instanceCreationDate, now, trustAnchor);
  }

  static XtmLicense verify(
      String pem,
      String platformId,
      Instant instanceCreationDate,
      Instant now,
      X509Certificate trustAnchor)
      throws XtmLicenseException {
    if (pem == null || pem.isBlank()) {
      throw new XtmLicenseException("no certificate was provided");
    }
    X509Certificate certificate;
    try {
      certificate = Pem.parseSingleCert(pem);
    } catch (Exception e) {
      throw new XtmLicenseException("it is not exactly one PEM certificate");
    }
    verifySignature(certificate, trustAnchor.getPublicKey());
    try {
      return checkLicense(certificate, platformId, instanceCreationDate, now);
    } catch (RuntimeException e) {
      throw new XtmLicenseException("its license extensions cannot be read");
    }
  }

  private static void verifySignature(X509Certificate certificate, PublicKey caPublicKey)
      throws XtmLicenseException {
    String algorithm = PKCS1_V15_SIGNATURES.get(certificate.getSigAlgOID());
    if (algorithm == null || !(caPublicKey instanceof RSAPublicKey)) {
      throw new XtmLicenseException("its signature algorithm is not RSASSA-PKCS1-v1_5 with SHA-2");
    }
    boolean verified;
    try {
      Signature signature = Signature.getInstance(algorithm);
      signature.initVerify(caPublicKey);
      signature.update(certificate.getTBSCertificate());
      verified = signature.verify(certificate.getSignature());
    } catch (GeneralSecurityException e) {
      verified = false;
    }
    if (!verified) {
      throw new XtmLicenseException(
          "its signature does not verify against the Filigran XTM CA pinned in this build");
    }
  }

  private static XtmLicense checkLicense(
      X509Certificate certificate, String platformId, Instant instanceCreationDate, Instant now)
      throws XtmLicenseException {
    if (!XTM_PRODUCT.equals(extension(certificate, OID_PRODUCT, OID_LEGACY_PRODUCT))) {
      throw new XtmLicenseException("it is not a Filigran XTM license");
    }
    String type = extension(certificate, OID_TYPE, OID_LEGACY_TYPE);
    if (type == null || type.isEmpty()) {
      type = "trial";
    }
    if (!LICENSE_TYPES.contains(type)) {
      throw new XtmLicenseException(
          "its license type is not one of standard, lts, trial, nfr or ci");
    }
    Instant start = certificate.getNotBefore().toInstant();
    Instant end = certificate.getNotAfter().toInstant();
    if ("ci".equals(type)) {
      if (instanceCreationDate == null) {
        throw new XtmLicenseException(
            "it is a ci license and the creation date of this instance, which bounds it, is"
                + " unknown");
      }
      Instant instanceWindowEnd = instanceCreationDate.plus(CI_INSTANCE_WINDOW);
      Instant maximumEnd = start.plus(CI_MAX_VALIDITY);
      end = instanceWindowEnd.isBefore(maximumEnd) ? instanceWindowEnd : maximumEnd;
    }
    List<String> grants = subLicensedPlatformIds(certificate);
    boolean globalGrant = grants.contains(GLOBAL_GRANT);
    XtmLicense license =
        new XtmLicense(
            type, customer(certificate), start, end, globalGrant ? GLOBAL_GRANT : platformId);
    if (!license.isActiveAt(now)) {
      throw new XtmLicenseException(inactiveReason(license, now));
    }
    boolean platformGrant =
        platformId != null && !platformId.isBlank() && grants.contains(platformId);
    if (!globalGrant && !platformGrant) {
      throw new XtmLicenseException(
          "it does not sub-license this platform: its OpenAEV sub-license lists neither 'global'"
              + " nor this platform's id");
    }
    return license;
  }

  private static String inactiveReason(XtmLicense license, Instant now) {
    if (now.isBefore(license.startDate())) {
      return "this " + license.type() + " license is not valid before " + license.startDate();
    }
    return switch (license.type()) {
      case "trial" -> "this trial license expired on " + license.expirationDate();
      case "ci" ->
          "this ci license expired on "
              + license.expirationDate()
              + " (45 minutes after this instance was created, at most 365 days after its start)";
      default ->
          "this "
              + license.type()
              + " license expired on "
              + license.expirationDate()
              + " and its grace period ended on "
              + license.validUntil();
    };
  }

  /** The customer name, the subject {@code O}; informational, as for XTM One. */
  private static String customer(X509Certificate certificate) {
    try {
      for (Rdn rdn : new LdapName(certificate.getSubjectX500Principal().getName()).getRdns()) {
        if ("O".equalsIgnoreCase(rdn.getType())) {
          return rdn.getValue().toString();
        }
      }
    } catch (InvalidNameException e) {
      // an unreadable subject names no customer
    }
    return "Unknown";
  }

  /** The standard extension, or the legacy one only when the standard one is absent. */
  private static String extension(X509Certificate certificate, String oid, String legacyOid) {
    String value = Pem.getExtensionText(certificate, oid);
    return value != null ? value : Pem.getExtensionText(certificate, legacyOid);
  }

  /**
   * The strings of the JSON array of OpenAEV's sub-license extension. A missing extension,
   * malformed JSON or anything but an array is an empty list, as for XTM One. An item of another
   * JSON type is left out: XTM One compares it through Python's {@code str()}, which never yields
   * {@code global} or a platform id (a UUID), so the outcome is XTM One's.
   */
  private static List<String> subLicensedPlatformIds(X509Certificate certificate) {
    String value = Pem.getExtensionText(certificate, OID_OPENAEV_SUBLICENSE);
    if (value == null || value.isEmpty()) {
      return List.of();
    }
    JsonNode node;
    try {
      node = JSON_READER.readValue(value);
    } catch (IOException e) {
      return List.of();
    }
    List<String> ids = new ArrayList<>();
    if (node != null && node.isArray()) {
      node.forEach(
          element -> {
            if (element.isTextual()) {
              ids.add(element.textValue());
            }
          });
    }
    return ids;
  }
}
