package io.openaev.utils;

import io.openaev.ee.License;
import io.openaev.ee.LicenseTypeEnum;

public class LicenseUtils {

  public static String computeXtmHubContractLevel(License license) {
    if (license.isLicenseEnterprise()) {
      if (license.getType() == LicenseTypeEnum.trial) {
        return "trial";
      }
      return "EE";
    }
    return "CE";
  }

  /** CE or EE only: the trial contract is reserved for platforms deployed by XTM Hub. */
  public static String computeXtmHubEdition(License license) {
    if (license != null && license.isLicenseEnterprise() && license.isLicenseValidated()) {
      return "EE";
    }
    return "CE";
  }
}
