package io.openaev.service.stix;

import static io.openaev.rest.payload.service.PayloadService.IOC_VALIDATION_FILE_NAME_KEY;
import static io.openaev.rest.payload.service.PayloadService.IOC_VALIDATION_URL_KEY;
import static io.openaev.rest.payload.service.PayloadService.IOC_VALIDATION_VALUE_KEY;
import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.net.InetAddresses;
import io.openaev.database.model.IocValidationIoc;
import io.openaev.database.model.IocValidationTestKind;
import io.openaev.rest.payload.service.PayloadService;
import io.openaev.service.stix.IocValidationPlanner.HostResolver;
import io.openaev.service.stix.IocValidationPlanner.Plan;
import io.openaev.utils.command.CommandArgumentBinder;
import java.net.InetAddress;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("IOC validation value checks")
class IocValidationValueChecksTest {

  /**
   * The value of the security report: PowerShell closes a single-quoted string at U+2019, so the
   * line {@code $OAEV_ARG = '...'} built from it would run {@code Write-Output(...)} as a command.
   */
  private static final String REPRODUCTION =
      "http://a.example/x\u2019;Write-Output(\u2018PWNED\u2019);\u2018";

  private static final String MD5 = "d41d8cd98f00b204e9800998ecf8427e";
  private static final String SHA256 =
      "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";
  private static final String PROXY = "http://proxy.example.net:3128";
  private static final HostResolver PUBLIC = host -> List.of(address("8.8.8.8"));

  private static IocValidationSettings allowAll() {
    return new IocValidationSettings(
        EnumSet.allOf(IocValidationTestKind.class), PROXY, "", 8443, "");
  }

  private static InetAddress address(String literal) {
    return InetAddresses.forString(literal);
  }

  private static IocValidationIoc ioc(String type, String value, IocValidationTestKind kind) {
    IocValidationIoc ioc = new IocValidationIoc();
    ioc.setIndicatorRef("indicator--6d2f6bb1-31b1-4b8a-9d36-3b3b3a1f0e11");
    ioc.setObservableType(type);
    ioc.setValue(value);
    ioc.setRequestedTestKind(kind);
    return ioc;
  }

  private static IocValidationIoc file(String fileName, Map<String, String> hashes) {
    IocValidationIoc file = ioc("StixFile", MD5, IocValidationTestKind.FILE_DROP);
    file.setFileName(fileName);
    file.setHashes(hashes);
    return file;
  }

  private static IocValidationIoc logLine(String value, Map<String, String> hashes) {
    IocValidationIoc ioc = ioc("StixFile", value, IocValidationTestKind.LOG_INJECTION);
    ioc.setHashes(hashes);
    return ioc;
  }

  private static Plan plan(IocValidationIoc ioc) {
    return IocValidationPlanner.plan(ioc, allowAll(), PUBLIC);
  }

  /** An IOC of every test type carrying the value where that type reads it. */
  private static Stream<Arguments> everyTestType(String value) {
    return Stream.of(
        Arguments.of(
            IocValidationTestKind.DNS_RESOLUTION,
            ioc("Domain-Name", value, IocValidationTestKind.DNS_RESOLUTION)),
        Arguments.of(
            IocValidationTestKind.NETWORK_TRAFFIC,
            ioc("IPv4-Addr", value, IocValidationTestKind.NETWORK_TRAFFIC)),
        Arguments.of(
            IocValidationTestKind.HTTP_HEAD, ioc("Url", value, IocValidationTestKind.HTTP_HEAD)),
        Arguments.of(IocValidationTestKind.FILE_DROP, file(value, Map.of())),
        Arguments.of(IocValidationTestKind.LOG_INJECTION, logLine(value, Map.of())));
  }

  private static void assertRefused(Plan plan) {
    assertThat(plan.runnable()).isFalse();
    assertThat(plan.refused()).isTrue();
    assertThat(plan.arguments()).isEmpty();
    assertThat(plan.message()).startsWith("Refused: ");
  }

  private static boolean isPowerShellQuote(char character) {
    return character == '\'' || (character >= '\u2018' && character <= '\u201B');
  }

  /**
   * Whether the text is exactly one PowerShell single-quoted string: it opens and closes with one
   * of the five apostrophes PowerShell accepts as a delimiter (language specification, 2.3.5.2),
   * and inside it every apostrophe of any of the five kinds is doubled, which is the only escape.
   */
  private static boolean isSinglePowerShellString(String text) {
    if (text.length() < 2 || !isPowerShellQuote(text.charAt(0))) {
      return false;
    }
    int index = 1;
    while (index < text.length()) {
      if (isPowerShellQuote(text.charAt(index))) {
        if (index + 1 < text.length() && isPowerShellQuote(text.charAt(index + 1))) {
          index += 2;
          continue;
        }
        return index == text.length() - 1;
      }
      index++;
    }
    return false;
  }

  /**
   * The value part of every {@code $OAEV_ARG_... = '...'} line of a rendered PowerShell command.
   */
  private static List<String> powerShellDeclarations(Map<String, String> arguments) {
    CommandArgumentBinder binder =
        CommandArgumentBinder.forExecutor(PayloadService.IOC_VALIDATION_WINDOWS_EXECUTOR);
    arguments.forEach(binder::bind);
    String template =
        arguments.keySet().stream()
            .map(key -> "Write-Output #{" + key + "}")
            .collect(Collectors.joining("\n"));
    List<String> declarations = new ArrayList<>();
    for (String line : binder.render(template).split("\n")) {
      if (line.startsWith("$OAEV_ARG_")) {
        declarations.add(line.substring(line.indexOf(" = ") + 3));
      }
    }
    assertThat(declarations).hasSameSizeAs(arguments.keySet());
    return declarations;
  }

  @Nested
  @DisplayName("Typographic apostrophes")
  class TypographicApostrophes {

    private static Stream<Arguments> apostrophes() {
      return Stream.of('\u2018', '\u2019', '\u201A', '\u201B').map(Arguments::of);
    }

    private static String codePoint(char character) {
      return "U+%04X".formatted((int) character);
    }

    @ParameterizedTest(name = "{0} in the path, the query, the fragment and the host")
    @MethodSource("apostrophes")
    @DisplayName("an HTTP HEAD test refuses a URL that contains one")
    void given_urlWithTypographicApostrophe_should_refuse(char apostrophe) {
      for (String url :
          List.of(
              "https://evil.example.com/x" + apostrophe + "y",
              "https://evil.example.com/p?q=" + apostrophe,
              "https://evil.example.com/p#" + apostrophe,
              "https://evil" + apostrophe + ".example.com/")) {
        Plan plan = plan(ioc("Url", url, IocValidationTestKind.HTTP_HEAD));
        assertRefused(plan);
        assertThat(plan.message()).contains(codePoint(apostrophe));
      }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("apostrophes")
    @DisplayName("a log line test refuses a hash or an IOC value that contains one")
    void given_hashWithTypographicApostrophe_should_refuse(char apostrophe) {
      Plan hash = plan(logLine("ignored", Map.of("SHA-256", SHA256.substring(1) + apostrophe)));
      Plan value = plan(logLine(MD5.substring(1) + apostrophe, Map.of()));
      assertRefused(hash);
      assertRefused(value);
      assertThat(hash.message()).contains("<" + codePoint(apostrophe) + ">");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("apostrophes")
    @DisplayName("a file drop test refuses a file name that contains one")
    void given_fileNameWithTypographicApostrophe_should_refuse(char apostrophe) {
      Plan plan = plan(file("invoice" + apostrophe + ".exe", Map.of()));
      assertRefused(plan);
      assertThat(plan.message()).contains(codePoint(apostrophe));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("apostrophes")
    @DisplayName("DNS and network tests refuse a host name or an address that contains one")
    void given_hostOrAddressWithTypographicApostrophe_should_refuse(char apostrophe) {
      assertRefused(
          plan(
              ioc(
                  "Domain-Name",
                  "evil" + apostrophe + ".example.com",
                  IocValidationTestKind.DNS_RESOLUTION)));
      assertRefused(
          plan(ioc("IPv4-Addr", "8.8.4.4" + apostrophe, IocValidationTestKind.NETWORK_TRAFFIC)));
      assertRefused(
          plan(
              ioc("IPv6-Addr", "2606:4700::" + apostrophe, IocValidationTestKind.NETWORK_TRAFFIC)));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("apostrophes")
    @DisplayName("the value checks refuse one wherever it is placed")
    void given_typographicApostrophe_should_failEveryValueCheck(char apostrophe) {
      assertThat(
              IocValidationPlanner.normalizeUrl("https://a.example/" + apostrophe, PUBLIC)
                  .isAccepted())
          .isFalse();
      assertThat(IocValidationPlanner.sanitizeFileName(apostrophe + "a.txt").isAccepted())
          .isFalse();
      assertThat(IocValidationPlanner.normalizeAddress("10.0.0.1" + apostrophe).isAccepted())
          .isFalse();
      assertThat(IocValidationPlanner.normalizeHost("a" + apostrophe + ".example.com")).isEmpty();
    }
  }

  @Nested
  @DisplayName("The reproduction of the security report")
  class Reproduction {

    private static Stream<Arguments> reproductionInEveryTestType() {
      return everyTestType(REPRODUCTION);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("reproductionInEveryTestType")
    @DisplayName("is refused by every test type")
    void given_reproduction_should_beRefusedByEveryTestType(
        IocValidationTestKind kind, IocValidationIoc ioc) {
      Plan plan = plan(ioc);
      assertRefused(plan);
      // The refusal shows the invisible difference instead of the look-alike character
      assertThat(plan.message()).doesNotContain("\u2018", "\u2019").contains("U+2019");
    }

    @Test
    @DisplayName("would leave the PowerShell string if it were quoted like an ASCII value")
    void given_reproductionQuotedOnlyForAsciiApostrophes_should_notBeSingleString() {
      // Control for the checker below: quoting that knows only the ASCII apostrophe breaks
      assertThat(isSinglePowerShellString("'" + REPRODUCTION.replace("'", "''") + "'")).isFalse();
    }

    @Test
    @DisplayName("is refused at intake, recorded on the IOC and never planned")
    void given_reproduction_should_beRecordedAsRefused() {
      IocValidationIoc url = ioc("Url", REPRODUCTION, IocValidationTestKind.HTTP_HEAD);
      IocValidationIoc dns =
          ioc("Domain-Name", "evil.example.com", IocValidationTestKind.DNS_RESOLUTION);

      IocValidationPlanner.apply(List.of(url, dns), allowAll(), PUBLIC);

      assertThat(url.isRefused()).isTrue();
      assertThat(url.getTestKind()).isNull();
      assertThat(url.getPlanFingerprint()).isNull();
      assertThat(url.getMessage()).startsWith("Refused: ").contains("U+2019");
      assertThat(dns.isRefused()).isFalse();
      assertThat(dns.getTestKind()).isEqualTo(IocValidationTestKind.DNS_RESOLUTION);
    }
  }

  @Nested
  @DisplayName("Internal addresses")
  class InternalAddresses {

    @ParameterizedTest(name = "{0}")
    @ValueSource(
        strings = {
          "0.0.0.0",
          "::",
          "127.0.0.1",
          "127.12.34.56",
          "::1",
          "169.254.169.254",
          "fe80::1",
          "10.0.0.1",
          "172.16.0.1",
          "172.31.255.254",
          "192.168.1.1",
          "fc00::1",
          "fd12:3456:789a::1",
          "224.0.0.1",
          "ff02::1",
          "255.255.255.255",
          "::ffff:127.0.0.1",
          "::ffff:10.1.2.3",
          "64:ff9b::a9fe:a9fe",
          "2002:7f00:1::1",
          "10.0.0.1/32",
          "100.64.0.1",
          "100.127.255.254",
          "198.18.0.1",
          "198.19.255.254",
          "192.0.0.8",
          "192.0.2.1",
          "198.51.100.7",
          "203.0.113.7",
          "240.0.0.1",
          "2001:db8::1",
          "3fff::1",
          "100::1",
          "64:ff9b:1::a",
          "2001:0:4136:e378:8000:63bf:3fff:fdd2"
        })
    @DisplayName("a network test refuses an address that is not globally reachable")
    void given_internalAddress_should_refuseNetworkTest(String value) {
      String type = value.contains(":") ? "IPv6-Addr" : "IPv4-Addr";
      Plan plan = plan(ioc(type, value, IocValidationTestKind.NETWORK_TRAFFIC));
      assertRefused(plan);
      assertThat(plan.message()).contains("only public addresses are tested");
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(
        strings = {
          "8.8.4.4",
          "172.32.0.1",
          "100.128.0.1",
          "198.20.0.1",
          "8.8.8.8/32",
          "2606:4700:4700::1111",
          "2001:4860:4860::8888"
        })
    @DisplayName("a network test accepts a public address")
    void given_publicAddress_should_planNetworkTest(String value) {
      String type = value.contains(":") ? "IPv6-Addr" : "IPv4-Addr";
      Plan plan = plan(ioc(type, value, IocValidationTestKind.NETWORK_TRAFFIC));
      assertThat(plan.runnable()).isTrue();
      assertThat(plan.refused()).isFalse();
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(
        strings = {
          "http://127.0.0.1/",
          "http://[::1]:8080/",
          "http://169.254.169.254/latest/meta-data/",
          "https://10.1.2.3/admin",
          "http://192.168.0.1/",
          "http://[fd00::1]/",
          "http://[::ffff:127.0.0.1]/",
          "http://0.0.0.0/",
          "http://100.64.0.1/",
          "http://198.18.0.1:8080/",
          "http://[2001:db8::1]/"
        })
    @DisplayName("an HTTP HEAD test refuses a URL whose host is an internal IP literal")
    void given_urlWithInternalIpLiteral_should_refuse(String url) {
      HostResolver unused =
          host -> {
            throw new AssertionError("an IP literal is never resolved: " + host);
          };
      Plan plan =
          IocValidationPlanner.plan(
              ioc("Url", url, IocValidationTestKind.HTTP_HEAD), allowAll(), unused);
      assertRefused(plan);
      assertThat(plan.message()).contains("only public addresses are tested");
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(
        strings = {
          "http://localhost/",
          "http://printer.local/",
          "http://wiki.corp/",
          "http://db.internal:5432/",
          "http://2130706433/",
          "http://0x7f.1/",
          "http://127.1/"
        })
    @DisplayName("an HTTP HEAD test refuses a host only an internal resolver answers")
    void given_urlWithInternalHostName_should_refuse(String url) {
      assertRefused(plan(ioc("Url", url, IocValidationTestKind.HTTP_HEAD)));
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(
        strings = {"10.0.0.5", "127.0.0.1", "169.254.169.254", "100.64.1.1", "fd00::5", "::1"})
    @DisplayName("an HTTP HEAD test refuses a host name that resolves to an internal address")
    void given_hostResolvingToInternalAddress_should_refuse(String internal) {
      HostResolver resolver = host -> List.of(address("8.8.8.8"), address(internal));
      Plan plan =
          IocValidationPlanner.plan(
              ioc("Url", "https://intranet.example.com/x", IocValidationTestKind.HTTP_HEAD),
              allowAll(),
              resolver);
      assertRefused(plan);
      assertThat(plan.message())
          .contains("resolves to " + InetAddresses.toAddrString(address(internal)));
    }

    @Test
    @DisplayName("an HTTP HEAD test accepts a public host, and one that does not resolve here")
    void given_publicOrUnresolvedHost_should_planHttpHead() {
      Plan resolved =
          IocValidationPlanner.plan(
              ioc("Url", "https://evil.example.com/a", IocValidationTestKind.HTTP_HEAD),
              allowAll(),
              PUBLIC);
      Plan unresolved =
          IocValidationPlanner.plan(
              ioc("Url", "https://evil.example.com/a", IocValidationTestKind.HTTP_HEAD),
              allowAll(),
              host -> List.of());
      assertThat(resolved.runnable()).isTrue();
      assertThat(unresolved.runnable()).isTrue();
    }

    @Test
    @DisplayName("a request resolves each host once")
    void given_severalUrlsOfOneHost_should_resolveItOnce() {
      List<String> lookups = Collections.synchronizedList(new ArrayList<>());
      HostResolver counting =
          host -> {
            lookups.add(host);
            return List.of(address("8.8.8.8"));
          };
      HostResolver answers =
          IocValidationPlanner.apply(
              List.of(
                  ioc("Url", "https://evil.example.com/a", IocValidationTestKind.HTTP_HEAD),
                  ioc("Url", "https://evil.example.com/b", IocValidationTestKind.HTTP_HEAD)),
              allowAll(),
              counting);
      // The approval builds its injects from the same answers, without a second lookup
      answers.resolve("evil.example.com");
      assertThat(lookups).containsExactly("evil.example.com");
    }

    @Test
    @DisplayName("only the host names of HTTP HEAD tests are resolved")
    void given_mixedIocs_should_resolveOnlyUrlHostNames() {
      assertThat(
              IocValidationPlanner.urlHostNames(
                  List.of(
                      ioc("Domain-Name", "dns.example.com", IocValidationTestKind.DNS_RESOLUTION),
                      ioc("Url", "https://WEB.example.com/x", IocValidationTestKind.HTTP_HEAD),
                      ioc("Url", "https://web.example.com/y", IocValidationTestKind.HTTP_HEAD),
                      ioc("Url", "http://8.8.4.4/x", IocValidationTestKind.HTTP_HEAD),
                      ioc("Url", REPRODUCTION, IocValidationTestKind.HTTP_HEAD))))
          .containsExactly("web.example.com");
    }

    @Test
    @DisplayName("a slow DNS answer never holds the request beyond the deadline")
    void given_slowResolver_should_answerWithinTheDeadline() {
      HostResolver slow =
          host -> {
            if (host.startsWith("slow.")) {
              try {
                Thread.sleep(10_000);
              } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
              }
              return List.of(address("10.0.0.1"));
            }
            return List.of(address("8.8.8.8"));
          };
      long start = System.nanoTime();

      HostResolver answers =
          IocValidationHostAnswers.resolve(
                  List.of("slow.example.com", "fast.example.com"), slow, Duration.ofMillis(500))
              .resolver();

      assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(5));
      // Not answered in time: a name that does not resolve here, whatever the late answer says
      assertThat(answers.resolve("slow.example.com")).isEmpty();
      assertThat(answers.resolve("fast.example.com")).containsExactly(address("8.8.8.8"));
    }

    @Test
    @DisplayName("the lookups of every request share a bounded number of threads")
    void given_manyHosts_should_neverRunMoreLookupsThanThePool() {
      AtomicInteger running = new AtomicInteger();
      AtomicInteger highest = new AtomicInteger();
      HostResolver tracking =
          host -> {
            highest.accumulateAndGet(running.incrementAndGet(), Math::max);
            try {
              Thread.sleep(100);
            } catch (InterruptedException e) {
              Thread.currentThread().interrupt();
            } finally {
              running.decrementAndGet();
            }
            return List.of(address("8.8.8.8"));
          };
      List<String> hosts =
          IntStream.range(0, 4 * IocValidationHostAnswers.THREADS)
              .mapToObj(index -> "host" + index + ".example.com")
              .toList();

      HostResolver answers =
          IocValidationHostAnswers.resolve(hosts, tracking, Duration.ofSeconds(10)).resolver();

      assertThat(highest.get()).isBetween(1, IocValidationHostAnswers.THREADS);
      assertThat(hosts).allSatisfy(host -> assertThat(answers.resolve(host)).hasSize(1));
    }

    @Test
    @DisplayName("a host the request did not announce is resolved directly")
    void given_unannouncedHost_should_resolveItDirectly() {
      HostResolver answers =
          IocValidationHostAnswers.resolve(
                  List.of(), host -> List.of(address("10.0.0.1")), Duration.ofSeconds(1))
              .resolver();
      assertThat(answers.resolve("other.example.com")).containsExactly(address("10.0.0.1"));
    }
  }

  @Nested
  @DisplayName("Accepted formats")
  class AcceptedFormats {

    @Test
    @DisplayName("a URL keeps every RFC 3986 character and an apostrophe percent-encoded")
    void given_rfc3986Url_should_acceptItUnchanged() {
      String url = "https://evil.example.com:8443/a%27b/c;d?x=1&y=(2)*+,$!~#frag";
      Plan plan = plan(ioc("Url", url, IocValidationTestKind.HTTP_HEAD));
      assertThat(plan.arguments()).containsEntry(IOC_VALIDATION_URL_KEY, url);
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(
        strings = {
          "https://evil.example.com/a'b",
          "https://evil.example.com/a b",
          "https://evil.example.com/a\"b",
          "https://evil.example.com/a`b",
          "https://evil.example.com/caf\u00e9",
          "https://evil.example.com/%zz",
          "ftp://evil.example.com/a",
          "https://user:secret@evil.example.com/",
          "https://evil_host.example.com/"
        })
    @DisplayName("a URL outside the accepted syntax is refused")
    void given_urlOutsideAcceptedSyntax_should_refuse(String url) {
      assertRefused(plan(ioc("Url", url, IocValidationTestKind.HTTP_HEAD)));
    }

    @Test
    @DisplayName("a URL longer than the cap is refused")
    void given_overlongUrl_should_refuse() {
      String url = "https://evil.example.com/" + "a".repeat(IocValidationPlanner.MAX_URL_LENGTH);
      assertRefused(plan(ioc("Url", url, IocValidationTestKind.HTTP_HEAD)));
    }

    @ParameterizedTest(name = "{0} characters")
    @ValueSource(ints = {32, 40, 64, 128})
    @DisplayName("a hexadecimal hash of a known length is written in lower case")
    void given_hexadecimalHash_should_writeItInLowerCase(int length) {
      String hash = "AbCdEf0123456789".repeat(8).substring(0, length);
      Plan plan = plan(logLine(hash, Map.of()));
      assertThat(plan.arguments())
          .containsEntry(IOC_VALIDATION_VALUE_KEY, hash.toLowerCase(Locale.ROOT));
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(
        strings = {
          "d41d8cd98f00b204e9800998ecf8427",
          "g41d8cd98f00b204e9800998ecf8427e",
          "d41d8cd98f00b204 e9800998ecf8427e",
          "evil.example.com"
        })
    @DisplayName("a value that is not a hexadecimal hash of a known length is refused")
    void given_nonHexadecimalOrUnknownLength_should_refuse(String value) {
      assertRefused(plan(logLine(value, Map.of())));
    }

    @Test
    @DisplayName("a hash whose length is not the length of its algorithm is refused")
    void given_hashOfAnotherAlgorithmLength_should_refuse() {
      Plan plan = plan(logLine("ignored", Map.of("SHA-256", MD5)));
      assertRefused(plan);
      assertThat(plan.message()).contains("64 hexadecimal characters");
    }

    @Test
    @DisplayName("a file name is capped in length and made only of the allowed characters")
    void given_fileNameAtAndOverTheCap_should_acceptThenRefuse() {
      String atCap = "a".repeat(IocValidationPlanner.MAX_FILE_NAME_LENGTH - 4) + ".exe";
      assertThat(plan(file(atCap, Map.of())).arguments())
          .containsEntry(IOC_VALIDATION_FILE_NAME_KEY, atCap);
      assertRefused(plan(file("a" + atCap, Map.of())));
      assertRefused(plan(file("..", Map.of())));
      assertRefused(plan(file("in voice.exe", Map.of())));
      assertRefused(plan(file("invoice.exe'", Map.of())));
    }
  }

  @Nested
  @DisplayName("PowerShell command of an accepted value")
  class PowerShellLine {

    private static Stream<Arguments> acceptedPlans() {
      return Stream.of(
          Arguments.of(
              ioc(
                  "Url",
                  "https://evil.example.com/a%27b?x=(1)&y=$z",
                  IocValidationTestKind.HTTP_HEAD)),
          Arguments.of(logLine("ignored", Map.of("SHA-256", SHA256.toUpperCase(Locale.ROOT)))),
          Arguments.of(file("C:\\Users\\Public\\in-voice_2026.v1.exe", Map.of())),
          Arguments.of(
              ioc("IPv6-Addr", "2606:4700:4700::1111", IocValidationTestKind.NETWORK_TRAFFIC)),
          Arguments.of(
              ioc("Domain-Name", "b\u00fccher.example.com", IocValidationTestKind.DNS_RESOLUTION)));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("acceptedPlans")
    @DisplayName("declares every argument as a single PowerShell string")
    void given_acceptedValue_should_declareSinglePowerShellString(IocValidationIoc ioc) {
      Plan plan = plan(ioc);
      assertThat(plan.runnable()).isTrue();
      for (String declaration : powerShellDeclarations(plan.arguments())) {
        assertThat(isSinglePowerShellString(declaration)).as(declaration).isTrue();
        assertThat(declaration.chars().allMatch(character -> character < 0x7f))
            .as(declaration)
            .isTrue();
      }
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(IocValidationTestKind.class)
    @DisplayName("never receives a value with an apostrophe from any test type")
    void given_everyTestType_should_neverPlanAnApostrophe(IocValidationTestKind kind) {
      for (char apostrophe : new char[] {'\'', '\u2018', '\u2019', '\u201A', '\u201B'}) {
        everyTestType("a" + apostrophe + "b")
            .map(arguments -> (IocValidationIoc) arguments.get()[1])
            .filter(ioc -> ioc.getRequestedTestKind() == kind)
            .map(IocValidationValueChecksTest::plan)
            .forEach(IocValidationValueChecksTest::assertRefused);
      }
    }
  }
}
