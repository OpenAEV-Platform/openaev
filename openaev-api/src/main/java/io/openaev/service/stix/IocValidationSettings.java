package io.openaev.service.stix;

import io.openaev.database.model.IocValidationTestKind;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * The tenant IOC validation safety settings, read once per decision so the intake preview, the
 * approval and the scenario built from it all apply the same values.
 *
 * @param allowedTestKinds test kinds an administrator allows; anything else is skipped
 * @param httpProxyUrl egress proxy every HTTP HEAD test goes through, blank when none
 * @param sinkholeAddress IP literal network tests connect to instead of the IOC address, blank when
 *     none
 * @param networkPort TCP port of network tests
 * @param assetGroupId asset group targeted by validation injects, blank when none
 */
public record IocValidationSettings(
    Set<IocValidationTestKind> allowedTestKinds,
    String httpProxyUrl,
    String sinkholeAddress,
    int networkPort,
    String assetGroupId) {

  public static final int DEFAULT_NETWORK_PORT = 443;

  public IocValidationSettings {
    allowedTestKinds =
        allowedTestKinds == null || allowedTestKinds.isEmpty()
            ? Collections.unmodifiableSet(EnumSet.noneOf(IocValidationTestKind.class))
            : Collections.unmodifiableSet(EnumSet.copyOf(allowedTestKinds));
  }

  public boolean allows(IocValidationTestKind kind) {
    return allowedTestKinds.contains(kind);
  }

  public boolean hasHttpProxy() {
    return httpProxyUrl != null && !httpProxyUrl.isBlank();
  }

  public boolean hasSinkhole() {
    return sinkholeAddress != null && !sinkholeAddress.isBlank();
  }

  public boolean hasAssetGroup() {
    return assetGroupId != null && !assetGroupId.isBlank();
  }
}
