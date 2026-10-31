package io.openaev.ee;

/** Where the platform's Enterprise Edition license comes from. */
public enum LicenseSource {
  /** This platform's own OpenAEV license, installed in the settings or the configuration. */
  openaev,
  /** The Filigran license of the connected XTM One, when it sub-licenses this platform. */
  xtm_one,
  /**
   * A Filigran license (product {@code filigran xtm}, signed by the XTM CA) installed as this
   * platform's own license, in the settings or the configuration.
   */
  filigran,
}
