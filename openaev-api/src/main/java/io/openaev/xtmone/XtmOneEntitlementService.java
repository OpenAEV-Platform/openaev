package io.openaev.xtmone;

import io.openaev.ee.XtmLicense;
import io.openaev.ee.XtmLicenseException;
import io.openaev.ee.XtmLicenseVerifier;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * The Enterprise Edition that a connected XTM One grants through its XTM license (the {@code
 * xtm_sublicense} path of the registration answer), the alternative to this platform's own license
 * in the one Enterprise Edition decision ({@code LicenseCacheManager}).
 *
 * <p>Granted only by the Filigran-signed XTM license certificate the answer carries ({@code
 * xtm_license_pem}), verified by {@link XtmLicenseVerifier}: the answer comes from whichever host
 * the XTM One URL names, so {@code ee_enabled} is advisory and never grants anything. Every
 * registration answer is a heartbeat and re-evaluates it: an answer without a certificate that
 * verifies ends it, no answer at all keeps the last verified certificate, and that certificate's
 * date rule is applied on every read, so the entitlement lapses on time between heartbeats too.
 */
@Service
@Slf4j
public class XtmOneEntitlementService {

  static final String XTM_LICENSE_PEM = "xtm_license_pem";
  static final String EE_SOURCES = "ee_sources";
  static final String EE_ENABLED = "ee_enabled";
  static final String EE_SOURCE_XTM_SUBLICENSE = "xtm_sublicense";

  private volatile XtmLicense license;

  /** Last warning logged, so that a heartbeat repeating it does not log it again. */
  private String lastWarning;

  /** The license the last answer left in force, to tell the caller when it changed. */
  private XtmLicense lastActive;

  // -- READ --

  /** The verified XTM license that currently grants Enterprise Edition, if any. */
  public Optional<XtmLicense> activeLicense() {
    return activeLicenseAt(Instant.now());
  }

  Optional<XtmLicense> activeLicenseAt(Instant now) {
    XtmLicense current = license;
    return current != null && current.isActiveAt(now) ? Optional.of(current) : Optional.empty();
  }

  // -- UPDATE --

  /**
   * Re-evaluates the entitlement from one registration answer.
   *
   * @param answer the registration answer, or {@code null} when XTM One gave none
   * @param platformId the platform id sent in the registration request
   * @param instanceCreationDate this instance's creation date, or {@code null} when unknown
   * @param ownLicenseValidated whether this platform's own Enterprise Edition license validated: an
   *     XTM One that predates the certificate then explains its {@code ee_enabled} with that
   *     license, and there is nothing to warn about
   * @return whether the license in force changed (granted, renewed, ended or lapsed since the
   *     previous answer), so that the Enterprise Edition decision can be refreshed
   */
  public synchronized boolean onRegistrationAnswer(
      Map<String, Object> answer,
      String platformId,
      Instant instanceCreationDate,
      boolean ownLicenseValidated) {
    if (answer != null) {
      apply(answer, platformId, instanceCreationDate, ownLicenseValidated);
    }
    XtmLicense active = activeLicense().orElse(null);
    boolean changed = !Objects.equals(active, lastActive);
    lastActive = active;
    return changed;
  }

  private void apply(
      Map<String, Object> answer,
      String platformId,
      Instant instanceCreationDate,
      boolean ownLicenseValidated) {
    XtmLicense previous = license;
    if (answer.get(XTM_LICENSE_PEM) instanceof String pem && !pem.isBlank()) {
      verify(pem, platformId, instanceCreationDate, previous);
      return;
    }
    license = null;
    if (answer.get(EE_SOURCES) instanceof List<?> sources) {
      if (sources.contains(EE_SOURCE_XTM_SUBLICENSE)) {
        warn(
            "[XTM One] XTM One reports that its XTM license sub-licenses this platform but returned"
                + " no license certificate (xtm_license_pem): Enterprise Edition is not granted"
                + " through XTM One.");
        return;
      }
    } else if (Boolean.TRUE.equals(answer.get(EE_ENABLED)) && !ownLicenseValidated) {
      warn(
          "[XTM One] XTM One reports ee_enabled=true but returned no Filigran-signed license"
              + " certificate (xtm_license_pem): ee_enabled is advisory and never grants Enterprise"
              + " Edition. Upgrade XTM One to a version that returns xtm_license_pem in its"
              + " registration answer.");
      return;
    }
    lastWarning = null;
    if (previous != null) {
      log.info(
          "[XTM One] XTM One no longer returns an XTM license for this platform: Enterprise Edition"
              + " through XTM One ended.");
    }
  }

  private void verify(
      String pem, String platformId, Instant instanceCreationDate, XtmLicense previous) {
    XtmLicense verified;
    try {
      verified = XtmLicenseVerifier.verify(pem, platformId, instanceCreationDate, Instant.now());
    } catch (XtmLicenseException e) {
      license = null;
      warn(
          "[XTM One] The XTM license returned by XTM One grants nothing ("
              + e.getMessage()
              + "): Enterprise Edition is not granted through XTM One.");
      return;
    } catch (RuntimeException e) {
      license = null;
      warn(
          "[XTM One] The XTM license returned by XTM One could not be verified: Enterprise Edition"
              + " is not granted through XTM One.",
          e);
      return;
    }
    license = verified;
    lastWarning = null;
    if (!verified.equals(previous)) {
      log.info(
          "[XTM One] XTM license verified: Enterprise Edition is granted through XTM One ({}"
              + " license covering {}, valid until {}).",
          verified.type(),
          verified.globalGrant() ? "every OpenAEV platform" : "this platform id",
          verified.validUntil());
    }
  }

  private void warn(String message) {
    warn(message, null);
  }

  private void warn(String message, Throwable cause) {
    if (!Objects.equals(message, lastWarning)) {
      log.warn(message, cause);
      lastWarning = message;
    }
  }
}
