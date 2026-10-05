package io.openaev.service.stix;

import static io.openaev.rest.payload.service.PayloadService.DYNAMIC_DNS_RESOLUTION_HOSTNAME_KEY;
import static io.openaev.rest.payload.service.PayloadService.IOC_VALIDATION_FILE_NAME_KEY;
import static io.openaev.rest.payload.service.PayloadService.IOC_VALIDATION_HOST_KEY;
import static io.openaev.rest.payload.service.PayloadService.IOC_VALIDATION_PORT_KEY;
import static io.openaev.rest.payload.service.PayloadService.IOC_VALIDATION_PROXY_KEY;
import static io.openaev.rest.payload.service.PayloadService.IOC_VALIDATION_URL_KEY;
import static io.openaev.rest.payload.service.PayloadService.IOC_VALIDATION_VALUE_KEY;

import com.google.common.net.InetAddresses;
import io.openaev.database.model.IocValidationIoc;
import io.openaev.database.model.IocValidationTestKind;
import java.net.IDN;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;

/**
 * Decides, for one IOC and the tenant safety settings, which benign test runs and with which
 * arguments. Pure and deterministic: the same IOC and settings always give the same plan, so the
 * preview shown before approval is exactly what the approval builds.
 *
 * <p>Nothing here ever widens what OpenCTI asked for: a test kind outside the allow-list, an
 * observable the kind does not apply to, or a value that fails validation is skipped with the
 * reason, never replaced by another kind.
 */
public final class IocValidationPlanner {

  static final int MAX_HOST_LENGTH = 253;
  static final int MAX_URL_LENGTH = 2048;
  static final int MAX_LOG_VALUE_LENGTH = 512;
  static final int MAX_FILE_NAME_LENGTH = 128;

  private static final Set<String> HOST_TYPES = Set.of("domain-name", "hostname");
  private static final Set<String> ADDRESS_TYPES = Set.of("ipv4-addr", "ipv6-addr");
  private static final Set<String> URL_TYPES = Set.of("url");
  private static final Set<String> FILE_TYPES = Set.of("stixfile", "file", "artifact");
  private static final List<String> HASH_PREFERENCE = List.of("SHA-256", "SHA-512", "SHA-1", "MD5");

  private static final Pattern HOST_LABEL =
      Pattern.compile("^[a-z0-9_](?:[a-z0-9_-]{0,61}[a-z0-9_])?$");
  private static final Pattern CONTROL_CHARS = Pattern.compile("[\\p{Cntrl}\\u2028\\u2029]");
  private static final Pattern FILE_NAME_FORBIDDEN = Pattern.compile("[<>:\"/\\\\|?*]");

  private IocValidationPlanner() {}

  /**
   * The test planned for one IOC.
   *
   * @param testKind the kind that runs, {@code null} when the IOC is skipped
   * @param arguments the inject content values the benign payload reads, empty when skipped
   * @param message why the IOC is skipped, or how it is adapted (sinkhole); {@code null} otherwise
   */
  public record Plan(
      IocValidationTestKind testKind, Map<String, String> arguments, String message) {

    public boolean runnable() {
      return testKind != null;
    }

    static Plan skip(String message) {
      return new Plan(null, Map.of(), message);
    }
  }

  /** Plans the requested test of one IOC under the given settings. */
  public static Plan plan(IocValidationIoc ioc, IocValidationSettings settings) {
    IocValidationTestKind kind = ioc.getRequestedTestKind();
    if (kind == null) {
      return Plan.skip("No test kind was requested for this IOC");
    }
    if (!settings.allows(kind)) {
      return Plan.skip(
          "Not run: the IOC validation settings of this tenant do not allow this test (%s)"
              .formatted(kind.label()));
    }
    String observableType =
        ioc.getObservableType() == null ? "" : ioc.getObservableType().toLowerCase(Locale.ROOT);
    return switch (kind) {
      case DNS_RESOLUTION -> planDns(ioc, observableType);
      case NETWORK_TRAFFIC -> planNetwork(ioc, observableType, settings);
      case HTTP_HEAD -> planHttpHead(ioc, observableType, settings);
      case FILE_DROP -> planFileDrop(ioc, observableType);
      case LOG_INJECTION -> planLogInjection(ioc);
    };
  }

  /** Applies {@link #plan} to every IOC, recording the kind that runs and the reason otherwise. */
  public static void apply(List<IocValidationIoc> iocs, IocValidationSettings settings) {
    for (IocValidationIoc ioc : iocs) {
      Plan plan = plan(ioc, settings);
      ioc.setTestKind(plan.testKind());
      ioc.setMessage(plan.message());
      ioc.setPlanFingerprint(plan.runnable() ? fingerprint(plan) : null);
    }
  }

  /**
   * SHA-256 of the test kind and its arguments sorted by key: equal exactly when the same test
   * would run.
   */
  static String fingerprint(Plan plan) {
    StringBuilder canonical = new StringBuilder(plan.testKind().name()).append('\n');
    new TreeMap<>(plan.arguments())
        .forEach((key, value) -> canonical.append(key).append('=').append(value).append('\n'));
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256")
                  .digest(canonical.toString().getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 is not available", e);
    }
  }

  private static Plan planDns(IocValidationIoc ioc, String observableType) {
    if (!HOST_TYPES.contains(observableType)) {
      return notApplicable(IocValidationTestKind.DNS_RESOLUTION, ioc);
    }
    return normalizeHost(ioc.getValue())
        .map(
            host ->
                new Plan(
                    IocValidationTestKind.DNS_RESOLUTION,
                    Map.of(DYNAMIC_DNS_RESOLUTION_HOSTNAME_KEY, host),
                    null))
        .orElseGet(
            () ->
                Plan.skip(
                    "Not run: '%s' is not a resolvable host name"
                        .formatted(shorten(ioc.getValue()))));
  }

  private static Plan planNetwork(
      IocValidationIoc ioc, String observableType, IocValidationSettings settings) {
    if (!ADDRESS_TYPES.contains(observableType)) {
      return notApplicable(IocValidationTestKind.NETWORK_TRAFFIC, ioc);
    }
    Optional<String> address = normalizeAddress(ioc.getValue());
    if (address.isEmpty()) {
      return Plan.skip(
          "Not run: '%s' is not a single IP address (network ranges are not tested)"
              .formatted(shorten(ioc.getValue())));
    }
    Map<String, String> arguments = new LinkedHashMap<>();
    String message = null;
    if (settings.hasSinkhole()) {
      arguments.put(IOC_VALIDATION_HOST_KEY, settings.sinkholeAddress().trim());
      message =
          "Safe mode: connects to the sinkhole %s instead of %s"
              .formatted(settings.sinkholeAddress().trim(), address.get());
    } else {
      arguments.put(IOC_VALIDATION_HOST_KEY, address.get());
    }
    arguments.put(IOC_VALIDATION_PORT_KEY, String.valueOf(settings.networkPort()));
    return new Plan(IocValidationTestKind.NETWORK_TRAFFIC, Map.copyOf(arguments), message);
  }

  private static Plan planHttpHead(
      IocValidationIoc ioc, String observableType, IocValidationSettings settings) {
    if (!URL_TYPES.contains(observableType)) {
      return notApplicable(IocValidationTestKind.HTTP_HEAD, ioc);
    }
    if (!settings.hasHttpProxy()) {
      return Plan.skip("Not run: HTTP HEAD tests need an egress proxy and none is configured");
    }
    return normalizeUrl(ioc.getValue())
        .map(
            url ->
                new Plan(
                    IocValidationTestKind.HTTP_HEAD,
                    Map.of(
                        IOC_VALIDATION_URL_KEY,
                        url,
                        IOC_VALIDATION_PROXY_KEY,
                        settings.httpProxyUrl().trim()),
                    null))
        .orElseGet(
            () ->
                Plan.skip(
                    "Not run: '%s' is not an absolute http or https URL"
                        .formatted(shorten(ioc.getValue()))));
  }

  private static Plan planFileDrop(IocValidationIoc ioc, String observableType) {
    if (!FILE_TYPES.contains(observableType)) {
      return notApplicable(IocValidationTestKind.FILE_DROP, ioc);
    }
    return sanitizeFileName(ioc.getFileName())
        .map(
            fileName ->
                new Plan(
                    IocValidationTestKind.FILE_DROP,
                    Map.of(IOC_VALIDATION_FILE_NAME_KEY, fileName),
                    null))
        .orElseGet(() -> Plan.skip("Not run: the file drop surrogate needs a usable file name"));
  }

  private static Plan planLogInjection(IocValidationIoc ioc) {
    String value = logValue(ioc);
    if (value.isBlank()) {
      return Plan.skip("Not run: the IOC has no value to write in the benign log line");
    }
    return new Plan(
        IocValidationTestKind.LOG_INJECTION, Map.of(IOC_VALIDATION_VALUE_KEY, value), null);
  }

  private static Plan notApplicable(IocValidationTestKind kind, IocValidationIoc ioc) {
    return Plan.skip(
        "Not run: this test (%s) does not apply to %s observables"
            .formatted(kind.label(), ioc.getObservableType()));
  }

  /** Lower-case ASCII host name, or empty when the value is not a resolvable name. */
  static Optional<String> normalizeHost(String value) {
    if (value == null) {
      return Optional.empty();
    }
    String host = value.trim();
    if (host.endsWith(".")) {
      host = host.substring(0, host.length() - 1);
    }
    if (host.isEmpty() || InetAddresses.isInetAddress(host)) {
      return Optional.empty();
    }
    try {
      host = IDN.toASCII(host, IDN.ALLOW_UNASSIGNED).toLowerCase(Locale.ROOT);
    } catch (IllegalArgumentException e) {
      return Optional.empty();
    }
    if (host.length() > MAX_HOST_LENGTH) {
      return Optional.empty();
    }
    for (String label : host.split("\\.", -1)) {
      if (!HOST_LABEL.matcher(label).matches()) {
        return Optional.empty();
      }
    }
    return Optional.of(host);
  }

  /** A single IP literal (a /32 or /128 range is accepted as its address), parsed without DNS. */
  static Optional<String> normalizeAddress(String value) {
    if (value == null) {
      return Optional.empty();
    }
    String address = value.trim();
    int slash = address.indexOf('/');
    if (slash >= 0) {
      String prefix = address.substring(slash + 1);
      address = address.substring(0, slash);
      boolean ipv6 = address.contains(":");
      if (!(ipv6 ? "128" : "32").equals(prefix)) {
        return Optional.empty();
      }
    }
    if (!InetAddresses.isInetAddress(address)) {
      return Optional.empty();
    }
    return Optional.of(InetAddresses.toAddrString(InetAddresses.forString(address)));
  }

  /** Whether the value is an IP literal; used to validate the sinkhole setting. */
  public static boolean isIpLiteral(String value) {
    return value != null && InetAddresses.isInetAddress(value.trim());
  }

  /** An absolute http(s) URL with a host, or empty. */
  static Optional<String> normalizeUrl(String value) {
    if (value == null) {
      return Optional.empty();
    }
    String url = value.trim();
    if (url.isEmpty() || url.length() > MAX_URL_LENGTH || CONTROL_CHARS.matcher(url).find()) {
      return Optional.empty();
    }
    try {
      URI uri = new URI(url);
      String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
      if (!("http".equals(scheme) || "https".equals(scheme)) || uri.getHost() == null) {
        return Optional.empty();
      }
      return Optional.of(url);
    } catch (URISyntaxException e) {
      return Optional.empty();
    }
  }

  /** Whether the value is an absolute http(s) URL; used to validate the proxy setting. */
  public static boolean isHttpUrl(String value) {
    return normalizeUrl(value).isPresent();
  }

  /**
   * The base name of the IOC file without any path or reserved character, so the surrogate is
   * always written inside the temporary directory and the cleanup removes exactly that file.
   */
  static Optional<String> sanitizeFileName(String fileName) {
    if (fileName == null) {
      return Optional.empty();
    }
    String name = fileName;
    int separator = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
    if (separator >= 0) {
      name = name.substring(separator + 1);
    }
    name = CONTROL_CHARS.matcher(name).replaceAll("");
    name = FILE_NAME_FORBIDDEN.matcher(name).replaceAll("_");
    name = name.strip();
    while (name.startsWith(".")) {
      name = name.substring(1);
    }
    while (name.endsWith(".") || name.endsWith(" ")) {
      name = name.substring(0, name.length() - 1);
    }
    if (name.isEmpty()) {
      return Optional.empty();
    }
    if (name.length() > MAX_FILE_NAME_LENGTH) {
      name = name.substring(name.length() - MAX_FILE_NAME_LENGTH);
    }
    return Optional.of(name);
  }

  /** The strongest hash of the IOC when it has some, its value otherwise, as one log token. */
  static String logValue(IocValidationIoc ioc) {
    String value = null;
    Map<String, String> hashes = ioc.getHashes() == null ? Map.of() : ioc.getHashes();
    for (String algorithm : HASH_PREFERENCE) {
      value =
          hashes.entrySet().stream()
              .filter(
                  entry -> normalizeAlgorithm(entry.getKey()).equals(normalizeAlgorithm(algorithm)))
              .map(Map.Entry::getValue)
              .findFirst()
              .orElse(null);
      if (value != null) {
        break;
      }
    }
    if (value == null) {
      value = hashes.values().stream().findFirst().orElse(ioc.getValue());
    }
    value = value == null ? "" : CONTROL_CHARS.matcher(value).replaceAll("").trim();
    return value.length() > MAX_LOG_VALUE_LENGTH ? value.substring(0, MAX_LOG_VALUE_LENGTH) : value;
  }

  private static String normalizeAlgorithm(String algorithm) {
    return algorithm.replace("-", "").replace("_", "").toUpperCase(Locale.ROOT);
  }

  private static String shorten(String value) {
    if (value == null) {
      return "";
    }
    return value.length() > 80 ? value.substring(0, 80) + "..." : value;
  }
}
