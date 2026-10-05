package io.openaev.ee;

/** Where the platform's Enterprise Edition license comes from. */
public enum LicenseSource {
  /** This platform's own OpenAEV license, installed in the settings or the configuration. */
  openaev,
  /** The XTM license of the connected XTM One, when it sub-licenses this platform. */
  xtm_one,
}
