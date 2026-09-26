package io.openaev.ee;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * A Filigran XTM license certificate that verified against the XTM CA pinned in this build and
 * sub-licenses this platform (see {@link XtmLicenseVerifier}).
 *
 * @param type the license type: {@code standard}, {@code lts}, {@code nfr}, {@code trial} or {@code
 *     ci}
 * @param customer the customer the license was issued to (subject {@code O}), informational
 * @param startDate the certificate's {@code notBefore}
 * @param expirationDate the end of the license: the certificate's {@code notAfter}, or the capped
 *     window of a {@code ci} license
 * @param coveredPlatform what the OpenAEV sub-license grants: {@code global}, or this platform's id
 */
public record XtmLicense(
    String type,
    String customer,
    Instant startDate,
    Instant expirationDate,
    String coveredPlatform) {

  /** XTM One's grace period after the end of a standard, lts or nfr license: days, not months. */
  public static final Duration GRACE_PERIOD = Duration.ofDays(90);

  static final String GLOBAL_GRANT = "global";

  /** Whether the sub-license covers every OpenAEV platform rather than this platform's id. */
  public boolean globalGrant() {
    return GLOBAL_GRANT.equals(coveredPlatform);
  }

  /**
   * XTM One's date rule. A {@code trial} or {@code ci} license is valid from its start to its end,
   * inclusive, with no grace. The other types are valid until the end of the grace period, and
   * their start is not enforced: XTM One accepts a renewal issued ahead of its start date.
   */
  public boolean isActiveAt(Instant now) {
    if (hasGracePeriod()) {
      return now.isBefore(validUntil());
    }
    return !now.isBefore(startDate) && !now.isAfter(expirationDate);
  }

  /** The instant the entitlement ends, grace period included. */
  public Instant validUntil() {
    return hasGracePeriod() ? expirationDate.plus(GRACE_PERIOD) : expirationDate;
  }

  /**
   * The platform license this XTM license stands for at {@code now}, for every Enterprise Edition
   * gate and the license settings. A {@code ci} license reads as {@code trial}, the platform type
   * with the same rule (no grace): {@link LicenseTypeEnum} has no {@code ci}, on purpose, since
   * OpenAEV's own licenses of that type are refused. Only a license past its expiration date is
   * expired: a renewal active ahead of its start date is not, and an expired license that still
   * grants is in its grace period.
   */
  public License toLicense(Instant now) {
    boolean active = isActiveAt(now);
    boolean expired = now.isAfter(expirationDate);
    License license = new License();
    license.setSource(LicenseSource.xtm_one);
    license.setLicenseEnterprise(true);
    license.setValidCert(true);
    license.setValidProduct(true);
    license.setType(hasGracePeriod() ? LicenseTypeEnum.valueOf(type) : LicenseTypeEnum.trial);
    license.setCreator("Filigran XTM");
    license.setCustomer(customer);
    license.setPlatform(coveredPlatform);
    license.setPlatformMatch(true);
    license.setGlobalLicense(globalGrant());
    license.setStartDate(startDate);
    license.setExpirationDate(expirationDate);
    license.setLicenseExpired(expired);
    license.setLicenseValidated(active);
    if (active && expired) {
      license.setExtraExpiration(true);
      license.setExtraExpirationDays(ChronoUnit.DAYS.between(now, validUntil()));
    }
    return license;
  }

  private boolean hasGracePeriod() {
    return !"trial".equals(type) && !"ci".equals(type);
  }
}
