package io.openaev.service.stix;

import static io.openaev.rest.payload.service.PayloadService.DYNAMIC_DNS_RESOLUTION_HOSTNAME_KEY;
import static io.openaev.rest.payload.service.PayloadService.IOC_VALIDATION_FILE_NAME_KEY;
import static io.openaev.rest.payload.service.PayloadService.IOC_VALIDATION_HOST_KEY;
import static io.openaev.rest.payload.service.PayloadService.IOC_VALIDATION_PORT_KEY;
import static io.openaev.rest.payload.service.PayloadService.IOC_VALIDATION_PROXY_KEY;
import static io.openaev.rest.payload.service.PayloadService.IOC_VALIDATION_URL_KEY;
import static io.openaev.rest.payload.service.PayloadService.IOC_VALIDATION_VALUE_KEY;

import com.google.common.net.InetAddresses;
import com.google.common.primitives.Ints;
import io.openaev.database.model.IocValidationIoc;
import io.openaev.database.model.IocValidationTestKind;
import java.net.IDN;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Decides, for one IOC and the tenant safety settings, which benign test runs and with which
 * arguments. Deterministic: the same IOC, settings and DNS answers always give the same plan, so
 * the preview shown before approval is exactly what the approval builds.
 *
 * <p>Nothing here ever widens what OpenCTI asked for: a test kind outside the allow-list, an
 * observable the kind does not apply to, or a value that fails validation is skipped with the
 * reason, never replaced by another kind.
 *
 * <p>IOC values come from threat-intelligence feeds and end up in the commands of the benign
 * payloads. A value is used only when it is made of an explicit set of ASCII characters (an http or
 * https URL, an IP address, a hexadecimal hash, a plain file name); anything else is refused, never
 * repaired, and the refusal is recorded on the IOC with its reason. No test is planned towards an
 * internal address: unspecified, loopback, link-local, private, unique local, multicast or
 * broadcast. For a URL host name this is what the OpenAEV server resolves; the egress proxy
 * resolves the name again when the test runs, so refusing internal destinations at that point is
 * the proxy's job.
 */
public final class IocValidationPlanner {

  static final int MAX_HOST_LENGTH = 253;
  static final int MAX_URL_LENGTH = 2048;
  static final int MAX_FILE_NAME_LENGTH = 128;
  static final Duration RESOLUTION_DEADLINE = Duration.ofSeconds(5);
  private static final int RESOLUTION_THREADS = 16;

  private static final Set<String> HOST_TYPES = Set.of("domain-name", "hostname");
  private static final Set<String> ADDRESS_TYPES = Set.of("ipv4-addr", "ipv6-addr");
  private static final Set<String> URL_TYPES = Set.of("url");
  private static final Set<String> FILE_TYPES = Set.of("stixfile", "file", "artifact");
  private static final List<String> HASH_PREFERENCE = List.of("SHA-256", "SHA-512", "SHA-1", "MD5");
  private static final Set<Integer> HASH_LENGTHS = Set.of(32, 40, 64, 128);
  private static final Map<String, Integer> HASH_LENGTH_BY_ALGORITHM =
      Map.of("MD5", 32, "SHA1", 40, "SHA256", 64, "SHA512", 128);

  // Letters, digits and marks of any script, '-', '_' and the dots IDNA reads as label separators:
  // IDN.toASCII would turn a quote or any other punctuation into a punycode label nobody can own
  private static final Pattern HOST_FORBIDDEN =
      Pattern.compile("[^\\p{L}\\p{M}\\p{N}_.\\-\\u3002\\uFF0E\\uFF61]");
  private static final Pattern HOST_LABEL =
      Pattern.compile("^[a-z0-9_](?:[a-z0-9_-]{0,61}[a-z0-9_])?$");
  // A top-level label never starts with a digit: 'http://127.1' or 'http://0x7f.1' is not a name
  private static final Pattern TOP_LEVEL_LABEL = Pattern.compile("^[a-z](?:[a-z0-9-]*[a-z0-9])?$");
  // Special-use and private-use top-level labels, which only an internal resolver answers
  private static final Set<String> INTERNAL_TOP_LEVEL_LABELS =
      Set.of(
          "localhost",
          "local",
          "localdomain",
          "internal",
          "intranet",
          "lan",
          "home",
          "corp",
          "private",
          "arpa");
  // RFC 3986 unreserved and reserved characters and '%', without the apostrophe, a shell quote
  private static final Pattern URL_FORBIDDEN =
      Pattern.compile("[^A-Za-z0-9\\-._~:/?#\\[\\]@!$&()*+,;=%]");
  private static final Pattern PERCENT_WITHOUT_HEX = Pattern.compile("%(?![0-9A-Fa-f]{2})");
  private static final Pattern FILE_NAME_FORBIDDEN = Pattern.compile("[^A-Za-z0-9._-]");
  private static final Pattern HEX = Pattern.compile("^[0-9A-Fa-f]+$");

  private IocValidationPlanner() {}

  /**
   * The test planned for one IOC.
   *
   * @param testKind the kind that runs, {@code null} when the IOC is skipped
   * @param arguments the inject content values the benign payload reads, empty when skipped
   * @param message why the IOC is skipped, or how it is adapted (sinkhole); {@code null} otherwise
   * @param refused whether the IOC value itself failed the checks (as opposed to a test the
   *     settings do not allow or that does not apply)
   */
  public record Plan(
      IocValidationTestKind testKind,
      Map<String, String> arguments,
      String message,
      boolean refused) {

    public Plan(IocValidationTestKind testKind, Map<String, String> arguments, String message) {
      this(testKind, arguments, message, false);
    }

    public boolean runnable() {
      return testKind != null;
    }

    static Plan skip(String message) {
      return new Plan(null, Map.of(), message, false);
    }

    static Plan refuse(String reason) {
      return new Plan(null, Map.of(), "Refused: " + reason, true);
    }
  }

  /**
   * A value that passed the checks, or why it is refused.
   *
   * @param value the normalized value, {@code null} when refused
   * @param refusal why the value is refused, {@code null} when accepted
   */
  record Checked<T>(T value, String refusal) {

    static <T> Checked<T> accepted(T value) {
      return new Checked<>(value, null);
    }

    static <T> Checked<T> refused(String refusal) {
      return new Checked<>(null, refusal);
    }

    boolean isAccepted() {
      return refusal == null;
    }
  }

  /** Every address a host name resolves to; empty when the name does not resolve. */
  @FunctionalInterface
  interface HostResolver {

    HostResolver SYSTEM =
        host -> {
          try {
            return List.of(InetAddress.getAllByName(host));
          } catch (UnknownHostException e) {
            return List.of();
          }
        };

    List<InetAddress> resolve(String host);
  }

  /** Plans the requested test of one IOC under the given settings. */
  public static Plan plan(IocValidationIoc ioc, IocValidationSettings settings) {
    return plan(ioc, settings, HostResolver.SYSTEM);
  }

  static Plan plan(IocValidationIoc ioc, IocValidationSettings settings, HostResolver resolver) {
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
      case HTTP_HEAD -> planHttpHead(ioc, observableType, settings, resolver);
      case FILE_DROP -> planFileDrop(ioc, observableType);
      case LOG_INJECTION -> planLogInjection(ioc);
    };
  }

  /**
   * Applies {@link #plan} to every IOC, recording the kind that runs and the reason otherwise.
   *
   * @return the DNS answers the plans were built from, to build the same plans again without a
   *     second lookup
   */
  static HostResolver apply(List<IocValidationIoc> iocs, IocValidationSettings settings) {
    return apply(iocs, settings, HostResolver.SYSTEM);
  }

  static HostResolver apply(
      List<IocValidationIoc> iocs, IocValidationSettings settings, HostResolver resolver) {
    HostResolver once = resolveAll(iocs, resolver, RESOLUTION_DEADLINE);
    for (IocValidationIoc ioc : iocs) {
      Plan plan = plan(ioc, settings, once);
      ioc.setTestKind(plan.testKind());
      ioc.setMessage(plan.message());
      ioc.setRefused(plan.refused());
      ioc.setPlanFingerprint(plan.runnable() ? fingerprint(plan) : null);
    }
    return once;
  }

  /**
   * Resolves every host name an HTTP HEAD test of these IOCs would request: once per host, in
   * parallel, and within {@code deadline} overall, since planning runs inside the transaction of
   * the intake or the approval. A name not answered in time counts as a name that does not resolve
   * from the OpenAEV server (the egress proxy resolves it again at execution anyway). A host the
   * IOCs did not announce is resolved directly.
   */
  static HostResolver resolveAll(
      List<IocValidationIoc> iocs, HostResolver resolver, Duration deadline) {
    Set<String> hosts = new LinkedHashSet<>();
    for (IocValidationIoc ioc : iocs) {
      if (ioc.getRequestedTestKind() == IocValidationTestKind.HTTP_HEAD) {
        urlHostName(ioc.getValue()).ifPresent(hosts::add);
      }
    }
    Map<String, List<InetAddress>> answers = new HashMap<>();
    if (!hosts.isEmpty()) {
      List<String> names = List.copyOf(hosts);
      ExecutorService pool =
          Executors.newFixedThreadPool(
              Math.min(RESOLUTION_THREADS, names.size()),
              Thread.ofPlatform().daemon().name("ioc-validation-dns-", 0).factory());
      try {
        List<Future<List<InetAddress>>> lookups =
            pool.invokeAll(
                names.stream()
                    .map(host -> (Callable<List<InetAddress>>) () -> resolver.resolve(host))
                    .toList(),
                deadline.toMillis(),
                TimeUnit.MILLISECONDS);
        // Only the answers in time: a lookup cancelled at the deadline never changes the result
        for (int index = 0; index < names.size(); index++) {
          Future<List<InetAddress>> lookup = lookups.get(index);
          if (!lookup.isCancelled()) {
            try {
              answers.put(names.get(index), lookup.get());
            } catch (ExecutionException e) {
              // a failed lookup counts as a name that does not resolve
            }
          }
        }
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      } finally {
        pool.shutdownNow();
      }
    }
    return host ->
        hosts.contains(host) ? answers.getOrDefault(host, List.of()) : resolver.resolve(host);
  }

  /** The host name {@link #normalizeUrl} would resolve for a value, empty for an IP literal. */
  private static Optional<String> urlHostName(String value) {
    Checked<URI> parsed = parseHttpUrl(value);
    if (!parsed.isAccepted() || InetAddresses.isUriInetAddress(parsed.value().getHost())) {
      return Optional.empty();
    }
    return normalizeHost(parsed.value().getHost());
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
                Plan.refuse(
                    "'%s' is not a resolvable host name".formatted(display(ioc.getValue()))));
  }

  private static Plan planNetwork(
      IocValidationIoc ioc, String observableType, IocValidationSettings settings) {
    if (!ADDRESS_TYPES.contains(observableType)) {
      return notApplicable(IocValidationTestKind.NETWORK_TRAFFIC, ioc);
    }
    Checked<String> address = normalizeAddress(ioc.getValue());
    if (!address.isAccepted()) {
      return Plan.refuse(address.refusal());
    }
    Map<String, String> arguments = new LinkedHashMap<>();
    String message = null;
    if (settings.hasSinkhole()) {
      arguments.put(IOC_VALIDATION_HOST_KEY, settings.sinkholeAddress().trim());
      message =
          "Safe mode: connects to the sinkhole %s instead of %s"
              .formatted(settings.sinkholeAddress().trim(), address.value());
    } else {
      arguments.put(IOC_VALIDATION_HOST_KEY, address.value());
    }
    arguments.put(IOC_VALIDATION_PORT_KEY, String.valueOf(settings.networkPort()));
    return new Plan(IocValidationTestKind.NETWORK_TRAFFIC, Map.copyOf(arguments), message);
  }

  private static Plan planHttpHead(
      IocValidationIoc ioc,
      String observableType,
      IocValidationSettings settings,
      HostResolver resolver) {
    if (!URL_TYPES.contains(observableType)) {
      return notApplicable(IocValidationTestKind.HTTP_HEAD, ioc);
    }
    if (!settings.hasHttpProxy()) {
      return Plan.skip("Not run: HTTP HEAD tests need an egress proxy and none is configured");
    }
    String proxy = settings.httpProxyUrl().trim();
    if (!isHttpUrl(proxy)) {
      return Plan.skip(
          "Not run: the egress proxy of the IOC validation settings is not a valid http or https"
              + " URL");
    }
    Checked<String> url = normalizeUrl(ioc.getValue(), resolver);
    if (!url.isAccepted()) {
      return Plan.refuse(url.refusal());
    }
    return new Plan(
        IocValidationTestKind.HTTP_HEAD,
        Map.of(IOC_VALIDATION_URL_KEY, url.value(), IOC_VALIDATION_PROXY_KEY, proxy),
        null);
  }

  private static Plan planFileDrop(IocValidationIoc ioc, String observableType) {
    if (!FILE_TYPES.contains(observableType)) {
      return notApplicable(IocValidationTestKind.FILE_DROP, ioc);
    }
    Checked<String> fileName = sanitizeFileName(ioc.getFileName());
    if (!fileName.isAccepted()) {
      return Plan.refuse(fileName.refusal());
    }
    return new Plan(
        IocValidationTestKind.FILE_DROP,
        Map.of(IOC_VALIDATION_FILE_NAME_KEY, fileName.value()),
        null);
  }

  private static Plan planLogInjection(IocValidationIoc ioc) {
    Checked<String> value = logValue(ioc);
    if (!value.isAccepted()) {
      return Plan.refuse(value.refusal());
    }
    return new Plan(
        IocValidationTestKind.LOG_INJECTION, Map.of(IOC_VALIDATION_VALUE_KEY, value.value()), null);
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
    if (host.isEmpty()
        || InetAddresses.isInetAddress(host)
        || HOST_FORBIDDEN.matcher(host).find()) {
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

  /**
   * A single public IP literal (a /32 or /128 range is accepted as its address), parsed without DNS
   * and written in its canonical form, or why it is refused.
   */
  static Checked<String> normalizeAddress(String value) {
    if (value == null || value.isBlank()) {
      return Checked.refused("the IOC has no IP address");
    }
    String address = value.trim();
    int slash = address.indexOf('/');
    if (slash >= 0) {
      String prefix = address.substring(slash + 1);
      address = address.substring(0, slash);
      boolean ipv6 = address.contains(":");
      if (!(ipv6 ? "128" : "32").equals(prefix)) {
        return Checked.refused(
            "'%s' is a network range: only single IP addresses are tested"
                .formatted(display(value)));
      }
    }
    if (address.indexOf('%') >= 0 || !InetAddresses.isInetAddress(address)) {
      return Checked.refused("'%s' is not a single IP address".formatted(display(value)));
    }
    InetAddress parsed = InetAddresses.forString(address);
    Optional<String> range = internalRange(parsed);
    if (range.isPresent()) {
      return Checked.refused(
          "'%s' is %s: only public addresses are tested".formatted(display(value), range.get()));
    }
    return Checked.accepted(InetAddresses.toAddrString(parsed));
  }

  /** Whether the value is an IP literal; used to validate the sinkhole setting. */
  public static boolean isIpLiteral(String value) {
    return value != null && InetAddresses.isInetAddress(value.trim());
  }

  /**
   * An absolute http(s) URL an HTTP HEAD test may request, or why it is refused. On top of the
   * syntax of {@link #parseHttpUrl}: no credentials, a host that is a public IP literal or a DNS
   * name of at least two labels under a public top-level label, and no address the name resolves to
   * from the OpenAEV server is internal. A name that does not resolve from the server is accepted:
   * the request still goes through the egress proxy, which resolves the name again at execution and
   * is the control that refuses an internal destination then.
   */
  static Checked<String> normalizeUrl(String value, HostResolver resolver) {
    Checked<URI> parsed = parseHttpUrl(value);
    if (!parsed.isAccepted()) {
      return Checked.refused(parsed.refusal());
    }
    URI uri = parsed.value();
    String url = value.trim();
    String shown = display(url);
    if (uri.getRawUserInfo() != null) {
      return Checked.refused("'%s' carries credentials before its host".formatted(shown));
    }
    String host = uri.getHost();
    if (InetAddresses.isUriInetAddress(host)) {
      return internalRange(InetAddresses.forUriString(host))
          .<Checked<String>>map(
              range ->
                  Checked.refused(
                      "'%s' targets %s: only public addresses are tested".formatted(shown, range)))
          .orElseGet(() -> Checked.accepted(url));
    }
    String name = normalizeHost(host).orElseThrow();
    String[] labels = name.split("\\.");
    String topLevel = labels[labels.length - 1];
    if (labels.length < 2) {
      return Checked.refused(
          "'%s' has a single-label host name, which only an internal resolver answers"
              .formatted(shown));
    }
    if (!TOP_LEVEL_LABEL.matcher(topLevel).matches()) {
      return Checked.refused(
          "'%s' has a host that is neither a DNS name nor a plain IP address".formatted(shown));
    }
    if (INTERNAL_TOP_LEVEL_LABELS.contains(topLevel)) {
      return Checked.refused(
          "'%s' names a host of an internal network (.%s): only public addresses are tested"
              .formatted(shown, topLevel));
    }
    for (InetAddress address : resolver.resolve(name)) {
      Optional<String> range = internalRange(address);
      if (range.isPresent()) {
        return Checked.refused(
            "'%s' resolves to %s, %s: only public addresses are tested"
                .formatted(shown, InetAddresses.toAddrString(address), range.get()));
      }
    }
    return Checked.accepted(url);
  }

  /**
   * Whether the value is an absolute http(s) URL made of the characters {@link #parseHttpUrl}
   * accepts; used to validate the proxy setting, which may be an internal address.
   */
  public static boolean isHttpUrl(String value) {
    return parseHttpUrl(value).isAccepted();
  }

  /**
   * The syntax shared by IOC URLs and the egress proxy: an absolute http or https URL of at most
   * {@link #MAX_URL_LENGTH} characters, made only of the ASCII characters of RFC 3986 except the
   * apostrophe (which percent-encodes as {@code %27}), every {@code %} starting a percent-encoded
   * byte, with a DNS name or an IP literal as host and a valid port.
   */
  private static Checked<URI> parseHttpUrl(String value) {
    if (value == null || value.isBlank()) {
      return Checked.refused("the URL is empty");
    }
    String url = value.trim();
    String shown = display(url);
    if (url.length() > MAX_URL_LENGTH) {
      return Checked.refused("'%s' is longer than %d characters".formatted(shown, MAX_URL_LENGTH));
    }
    Matcher forbidden = URL_FORBIDDEN.matcher(url);
    if (forbidden.find()) {
      return Checked.refused(
          ("'%s' contains %s: a URL is made only of the ASCII characters of RFC 3986, and an"
                  + " apostrophe is percent-encoded as %%27")
              .formatted(shown, characterName(url.codePointAt(forbidden.start()))));
    }
    if (PERCENT_WITHOUT_HEX.matcher(url).find()) {
      return Checked.refused(
          "'%s' has a '%%' that does not start a percent-encoded byte".formatted(shown));
    }
    URI uri;
    try {
      uri = new URI(url);
    } catch (URISyntaxException e) {
      return Checked.refused("'%s' is not a valid URL".formatted(shown));
    }
    String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
    if (!("http".equals(scheme) || "https".equals(scheme))) {
      return Checked.refused("'%s' is not an absolute http or https URL".formatted(shown));
    }
    String host = uri.getHost();
    if (host == null
        || !(InetAddresses.isUriInetAddress(host) || normalizeHost(host).isPresent())) {
      return Checked.refused("'%s' has no valid host name or IP address".formatted(shown));
    }
    if (uri.getPort() != -1 && (uri.getPort() < 1 || uri.getPort() > 65535)) {
      return Checked.refused("'%s' has an invalid port".formatted(shown));
    }
    return Checked.accepted(uri);
  }

  /**
   * Which internal range the address belongs to, worded for a message ("a loopback address"), or
   * empty for a public address. An IPv6 address that embeds an IPv4 address (mapped, compatible,
   * 6to4, Teredo, NAT64) is judged by the embedded address too.
   */
  static Optional<String> internalRange(InetAddress address) {
    if (address instanceof Inet6Address ipv6) {
      Optional<String> embedded = embeddedIpv4(ipv6).flatMap(IocValidationPlanner::internalRange);
      if (embedded.isPresent()) {
        return embedded;
      }
    }
    byte[] bytes = address.getAddress();
    if (address.isAnyLocalAddress() || (address instanceof Inet4Address && bytes[0] == 0)) {
      return Optional.of("an unspecified address");
    }
    if (address.isLoopbackAddress()) {
      return Optional.of("a loopback address");
    }
    if (address.isLinkLocalAddress()) {
      return Optional.of("a link-local address");
    }
    if (address.isSiteLocalAddress()) {
      return Optional.of("a private address");
    }
    if (address.isMulticastAddress()) {
      return Optional.of("a multicast address");
    }
    if (address instanceof Inet6Address && (bytes[0] & 0xfe) == 0xfc) {
      return Optional.of("a unique local address");
    }
    if (address instanceof Inet4Address && Ints.fromByteArray(bytes) == -1) {
      return Optional.of("the broadcast address");
    }
    return NON_GLOBAL_RANGES.stream()
        .filter(range -> range.contains(bytes))
        .map(AddressRange::label)
        .findFirst();
  }

  /**
   * The ranges of the IANA special-purpose registries that are not globally reachable and that the
   * {@link InetAddress} flags above do not cover.
   */
  private static final List<AddressRange> NON_GLOBAL_RANGES =
      List.of(
          new AddressRange("100.64.0.0", 10, "a shared address (carrier-grade NAT)"),
          new AddressRange("192.0.0.0", 24, "an IETF protocol assignment"),
          new AddressRange("192.0.2.0", 24, "a documentation address"),
          new AddressRange("198.18.0.0", 15, "a benchmarking address"),
          new AddressRange("198.51.100.0", 24, "a documentation address"),
          new AddressRange("203.0.113.0", 24, "a documentation address"),
          new AddressRange("240.0.0.0", 4, "a reserved address"),
          new AddressRange("64:ff9b:1::", 48, "a local-use NAT64 address"),
          new AddressRange("100::", 64, "a discard-only address"),
          new AddressRange("2001::", 23, "an IETF protocol assignment"),
          new AddressRange("2001:db8::", 32, "a documentation address"),
          new AddressRange("3fff::", 20, "a documentation address"));

  private record AddressRange(byte[] network, int prefix, String label) {

    AddressRange(String network, int prefix, String label) {
      this(InetAddresses.forString(network).getAddress(), prefix, label);
    }

    boolean contains(byte[] address) {
      int whole = prefix / 8;
      if (address.length != network.length
          || !Arrays.equals(address, 0, whole, network, 0, whole)) {
        return false;
      }
      int mask = (0xff << (8 - prefix % 8)) & 0xff;
      return prefix % 8 == 0 || (address[whole] & mask) == (network[whole] & mask);
    }
  }

  private static Optional<Inet4Address> embeddedIpv4(Inet6Address address) {
    if (InetAddresses.hasEmbeddedIPv4ClientAddress(address)) {
      return Optional.of(InetAddresses.getEmbeddedIPv4ClientAddress(address));
    }
    byte[] bytes = address.getAddress();
    boolean mapped = isZero(bytes, 0, 10) && bytes[10] == (byte) 0xff && bytes[11] == (byte) 0xff;
    // 64:ff9b::/96, the NAT64 well-known prefix
    boolean nat64 =
        bytes[0] == 0
            && bytes[1] == 0x64
            && bytes[2] == (byte) 0xff
            && bytes[3] == (byte) 0x9b
            && isZero(bytes, 4, 12);
    if (!mapped && !nat64) {
      return Optional.empty();
    }
    return Optional.of(
        InetAddresses.fromInteger(Ints.fromBytes(bytes[12], bytes[13], bytes[14], bytes[15])));
  }

  private static boolean isZero(byte[] bytes, int from, int to) {
    for (int index = from; index < to; index++) {
      if (bytes[index] != 0) {
        return false;
      }
    }
    return true;
  }

  /**
   * The base name of the IOC file, used as is for the surrogate: ASCII letters, digits, {@code .},
   * {@code _} and {@code -} only, at most {@link #MAX_FILE_NAME_LENGTH} characters, and not made of
   * dots only. A name outside these rules is refused rather than rewritten: a surrogate under
   * another name would not test this IOC. Only the directory part of a path is dropped, so the
   * surrogate is always written inside its run directory and the cleanup removes exactly that file.
   */
  static Checked<String> sanitizeFileName(String fileName) {
    if (fileName == null || fileName.isBlank()) {
      return Checked.refused("the IOC has no file name for the benign surrogate");
    }
    String name = fileName.strip();
    int separator = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
    if (separator >= 0) {
      name = name.substring(separator + 1);
    }
    if (name.isEmpty()) {
      return Checked.refused(
          "'%s' has no file name after its directory".formatted(display(fileName)));
    }
    Matcher forbidden = FILE_NAME_FORBIDDEN.matcher(name);
    if (forbidden.find()) {
      return Checked.refused(
          ("the file name '%s' contains %s: only ASCII letters, digits, '.', '_' and '-' are"
                  + " accepted, and a surrogate under another name would not test this IOC")
              .formatted(display(name), characterName(name.codePointAt(forbidden.start()))));
    }
    if (name.length() > MAX_FILE_NAME_LENGTH) {
      return Checked.refused(
          "the file name '%s' is longer than %d characters"
              .formatted(display(name), MAX_FILE_NAME_LENGTH));
    }
    if (name.chars().allMatch(character -> character == '.')) {
      return Checked.refused("'%s' is not a file name".formatted(display(name)));
    }
    return Checked.accepted(name);
  }

  /**
   * The hash written in the benign log line, in lower case: the strongest hash of the IOC, or its
   * value when it has no hash. It must be hexadecimal with the length of an MD5, SHA-1, SHA-256 or
   * SHA-512 digest (the length of its algorithm when the algorithm is one of them); anything else
   * is refused, so the log line never carries free text from a feed.
   */
  static Checked<String> logValue(IocValidationIoc ioc) {
    Map<String, String> hashes = ioc.getHashes() == null ? Map.of() : ioc.getHashes();
    Map.Entry<String, String> chosen = null;
    for (String algorithm : HASH_PREFERENCE) {
      chosen =
          hashes.entrySet().stream()
              .filter(
                  entry ->
                      entry.getKey() != null
                          && normalizeAlgorithm(entry.getKey())
                              .equals(normalizeAlgorithm(algorithm)))
              .findFirst()
              .orElse(null);
      if (chosen != null) {
        break;
      }
    }
    if (chosen == null && !hashes.isEmpty()) {
      chosen = hashes.entrySet().iterator().next();
    }
    String value = chosen == null ? ioc.getValue() : chosen.getValue();
    String hash = value == null ? "" : value.trim();
    if (hash.isEmpty()) {
      return Checked.refused("the IOC has no hash to write in the benign log line");
    }
    String label =
        chosen == null || chosen.getKey() == null
            ? "the IOC value"
            : "the %s hash".formatted(display(chosen.getKey()));
    if (!HEX.matcher(hash).matches() || !HASH_LENGTHS.contains(hash.length())) {
      return Checked.refused(
          "%s '%s' is not a hexadecimal MD5, SHA-1, SHA-256 or SHA-512 hash"
              .formatted(label, display(hash)));
    }
    Integer expected =
        chosen == null || chosen.getKey() == null
            ? null
            : HASH_LENGTH_BY_ALGORITHM.get(normalizeAlgorithm(chosen.getKey()));
    if (expected != null && expected != hash.length()) {
      return Checked.refused(
          "%s '%s' does not have the %d hexadecimal characters of its algorithm"
              .formatted(label, display(hash), expected));
    }
    return Checked.accepted(hash.toLowerCase(Locale.ROOT));
  }

  private static String normalizeAlgorithm(String algorithm) {
    return algorithm.replace("-", "").replace("_", "").toUpperCase(Locale.ROOT);
  }

  /** A character as named in a refusal: printable ASCII quoted, anything else as U+XXXX. */
  private static String characterName(int codePoint) {
    if (codePoint == ' ') {
      return "a space";
    }
    return codePoint > 0x20 && codePoint < 0x7f
        ? "'" + (char) codePoint + "'"
        : "U+%04X".formatted(codePoint);
  }

  /**
   * A value as shown in a refusal: every character outside printable ASCII written {@code
   * <U+XXXX>}, so an invisible or look-alike character is visible, then shortened.
   */
  private static String display(String value) {
    if (value == null) {
      return "";
    }
    StringBuilder shown = new StringBuilder();
    value
        .codePoints()
        .forEach(
            codePoint -> {
              if (codePoint >= 0x20 && codePoint < 0x7f) {
                shown.appendCodePoint(codePoint);
              } else {
                shown.append("<U+%04X>".formatted(codePoint));
              }
            });
    return shown.length() > 80 ? shown.substring(0, 80) + "..." : shown.toString();
  }
}
