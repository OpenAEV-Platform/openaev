package io.openaev.database.model.catalog;

import io.openaev.database.model.TenantSettingKeys;

public enum ContentType {
  PAYLOAD(TenantSettingKeys.CATALOG_PAYLOAD_AUTO_INSTALL),
  SCENARIO(TenantSettingKeys.CATALOG_SCENARIO_AUTO_INSTALL);

  // Compile-time constants for @DiscriminatorValue
  public static final String PAYLOAD_VALUE = "PAYLOAD";
  public static final String SCENARIO_VALUE = "SCENARIO";

  private final TenantSettingKeys autoInstallSetting;

  ContentType(TenantSettingKeys autoInstallSetting) {
    this.autoInstallSetting = autoInstallSetting;
  }

  /** The tenant setting telling whether the content a release brings installs itself. */
  public TenantSettingKeys autoInstallSetting() {
    return autoInstallSetting;
  }
}
