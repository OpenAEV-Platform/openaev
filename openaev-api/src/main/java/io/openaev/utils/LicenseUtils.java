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
}
