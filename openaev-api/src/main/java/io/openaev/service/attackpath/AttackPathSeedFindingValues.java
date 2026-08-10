package io.openaev.service.attackpath;

import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Produces attack-path finding VALUES in the exact byte formats the OutputProcessors emit (the
 * inverse of {@link AttackPathKeyMatcher}'s sub-field extractors), so a seeded finding is matched
 * by a consumed key exactly like a real one. Kept as one small pure helper so the seed and its
 * tests share a single source of truth for the formats.
 */
public final class AttackPathSeedFindingValues {

  private AttackPathSeedFindingValues() {}

  /** portscan: {@code host:port (service)}, service dropped when blank. */
  public static String portscan(String host, int port, String service) {
    return host + ":" + port + (isBlank(service) ? "" : " (" + service + ")");
  }

  /** credentials: {@code username:password} (split on the first colon). */
  public static String credentials(String username, String password) {
    return username + ":" + password;
  }

  /**
   * share: the ShareOutputProcessor form; the UNC host prefix is dropped when the host is blank.
   */
  public static String share(String host, String shareName, String permissions) {
    String value = shareName + " (" + permissions + ")";
    return isBlank(host) ? value : "\\\\" + host + "\\" + value;
  }

  /**
   * file: the FileOutputProcessor form. A UNC path ({@code \\host\share\dir\file}) on a share,
   * {@code host:/path} otherwise, or the bare relative path when the host is blank. The basename is
   * the matched sub-field.
   */
  public static String file(String host, String share, String path, String fileName) {
    String relative =
        Stream.of(share, path, fileName).filter(s -> !isBlank(s)).collect(Collectors.joining("/"));
    if (isBlank(host)) {
      return relative;
    }
    if (!isBlank(share)) {
      return "\\\\" + host + "\\" + relative.replace('/', '\\');
    }
    return host + ":" + (relative.startsWith("/") ? relative : "/" + relative);
  }

  /** username: the UsernameOutputProcessor form; the domain prefix is dropped when it is blank. */
  public static String username(String domain, String user) {
    return isBlank(domain) ? user : domain + "\\" + user;
  }

  /** cve: the identifier itself (whole-value match). */
  public static String cve(String cveId) {
    return cveId;
  }

  /** bare port: the port number as text (whole-value match). */
  public static String port(int port) {
    return Integer.toString(port);
  }

  private static boolean isBlank(String value) {
    return value == null || value.isBlank();
  }
}
