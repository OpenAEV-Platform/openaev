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
 * @param platformHosts host names and IP literals of the platform (OpenAEV, the OpenCTI of the
 *     tenant, the egress proxy) in the form {@link IocValidationPlanner#platformHosts} gives them,
 *     with the addresses their names resolve to; no test targets them
 * @param unansweredPlatformHosts host names of the platform whose lookup got no answer: their
 *     addresses are unknown, so no network or HTTP HEAD test runs
 */
public record IocValidationSettings(
    Set<IocValidationTestKind> allowedTestKinds,
    String httpProxyUrl,
    String sinkholeAddress,
    int networkPort,
    String assetGroupId,
    Set<String> platformHosts,
    Set<String> unansweredPlatformHosts) {

  public static final int DEFAULT_NETWORK_PORT = 443;

  public IocValidationSettings {
    allowedTestKinds =
        allowedTestKinds == null || allowedTestKinds.isEmpty()
            ? Collections.unmodifiableSet(EnumSet.noneOf(IocValidationTestKind.class))
            : Collections.unmodifiableSet(EnumSet.copyOf(allowedTestKinds));
    platformHosts = platformHosts == null ? Set.of() : Set.copyOf(platformHosts);
    unansweredPlatformHosts =
        unansweredPlatformHosts == null ? Set.of() : Set.copyOf(unansweredPlatformHosts);
  }

  /** The stored settings, before the host names of the platform are known. */
  public IocValidationSettings(
      Set<IocValidationTestKind> allowedTestKinds,
      String httpProxyUrl,
      String sinkholeAddress,
      int networkPort,
      String assetGroupId) {
    this(
        allowedTestKinds,
        httpProxyUrl,
        sinkholeAddress,
        networkPort,
        assetGroupId,
        Set.of(),
        Set.of());
  }

  /** The same settings, with the host names of the platform no test may target. */
  public IocValidationSettings withPlatformHosts(Set<String> hosts) {
    return withPlatformHosts(hosts, Set.of());
  }

  /**
   * The same settings, with the hosts of the platform no test may target and the platform names
   * whose lookup got no answer.
   */
  public IocValidationSettings withPlatformHosts(Set<String> hosts, Set<String> unanswered) {
    return new IocValidationSettings(
        allowedTestKinds,
        httpProxyUrl,
        sinkholeAddress,
        networkPort,
        assetGroupId,
        hosts,
        unanswered);
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
