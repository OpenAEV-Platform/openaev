package io.openaev.api.asset.software;

import java.util.Locale;

/**
 * The identity of a software component on an asset: the PURL when there is one, else the CPE, else
 * vendor, product and version lower-cased. Recording the same component twice therefore updates one
 * row; two versions of one product are two rows.
 */
final class SoftwareIdentity {

  private SoftwareIdentity() {}

  static String key(String vendor, String product, String version, String cpe, String purl) {
    if (purl != null) {
      return "purl:" + purl;
    }
    if (cpe != null) {
      return "cpe:" + cpe.toLowerCase(Locale.ROOT);
    }
    return "name:"
        + lower(vendor)
        + "|"
        + lower(product)
        + "|"
        + (version == null ? "?" : version.toLowerCase(Locale.ROOT));
  }

  /** True when the PURL names a version ({@code pkg:type/ns/name@version}). */
  static boolean purlHasVersion(String purl) {
    int at = purl.lastIndexOf('@');
    return at > purl.lastIndexOf('/');
  }

  /** The LIKE escape character the search query declares ({@code ESCAPE '!'}). */
  static final char LIKE_ESCAPE = '!';

  /** Escapes the LIKE wildcards of a literal prefix with {@link #LIKE_ESCAPE}. */
  static String escapeLike(String value) {
    return value.replace("!", "!!").replace("%", "!%").replace("_", "!_");
  }

  private static String lower(String value) {
    return value == null ? "" : value.toLowerCase(Locale.ROOT);
  }
}
