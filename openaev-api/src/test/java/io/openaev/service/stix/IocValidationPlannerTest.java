package io.openaev.service.stix;

import static io.openaev.rest.payload.service.PayloadService.IOC_VALIDATION_FILE_NAME_KEY;
import static io.openaev.rest.payload.service.PayloadService.IOC_VALIDATION_HOST_KEY;
import static io.openaev.rest.payload.service.PayloadService.IOC_VALIDATION_PORT_KEY;
import static io.openaev.rest.payload.service.PayloadService.IOC_VALIDATION_PROXY_KEY;
import static io.openaev.rest.payload.service.PayloadService.IOC_VALIDATION_URL_KEY;
import static io.openaev.rest.payload.service.PayloadService.IOC_VALIDATION_VALUE_KEY;
import static org.assertj.core.api.Assertions.assertThat;

import io.openaev.database.model.IocValidationIoc;
import io.openaev.database.model.IocValidationTestKind;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("IOC validation planner")
class IocValidationPlannerTest {

  private static IocValidationSettings settings(
      Set<IocValidationTestKind> kinds, String proxy, String sinkhole) {
    return new IocValidationSettings(kinds, proxy, sinkhole, 8443, "");
  }

  private static IocValidationSettings allowAll(String proxy, String sinkhole) {
    return settings(EnumSet.allOf(IocValidationTestKind.class), proxy, sinkhole);
  }

  private static IocValidationIoc ioc(String type, String value, IocValidationTestKind kind) {
    IocValidationIoc ioc = new IocValidationIoc();
    ioc.setIndicatorRef("indicator--6d2f6bb1-31b1-4b8a-9d36-3b3b3a1f0e11");
    ioc.setObservableType(type);
    ioc.setValue(value);
    ioc.setRequestedTestKind(kind);
    return ioc;
  }

  @Nested
  @DisplayName("Safety allow-list")
  class AllowList {

    @Test
    @DisplayName("only DNS resolution runs with the default settings")
    void given_defaultSettings_should_onlyRunDnsResolution() {
      IocValidationSettings defaults =
          settings(EnumSet.of(IocValidationTestKind.DNS_RESOLUTION), "", "");

      IocValidationPlanner.Plan dns =
          IocValidationPlanner.plan(
              ioc("Domain-Name", "evil.example.com", IocValidationTestKind.DNS_RESOLUTION),
              defaults);
      IocValidationPlanner.Plan network =
          IocValidationPlanner.plan(
              ioc("IPv4-Addr", "203.0.113.7", IocValidationTestKind.NETWORK_TRAFFIC), defaults);

      assertThat(dns.runnable()).isTrue();
      assertThat(network.runnable()).isFalse();
      // The message names the test as people read it, never the contract value
      assertThat(network.message())
          .contains("do not allow this test (Network traffic)")
          .doesNotContain("network_traffic");
      assertThat(network.arguments()).isEmpty();
    }

    @Test
    @DisplayName("an IOC without requested test kind is skipped")
    void given_noRequestedKind_should_skip() {
      IocValidationPlanner.Plan plan =
          IocValidationPlanner.plan(ioc("Domain-Name", "evil.example.com", null), allowAll("", ""));
      assertThat(plan.runnable()).isFalse();
      assertThat(plan.message()).isNotBlank();
    }

    @Test
    @DisplayName("a kind never replaces another one when it does not apply to the observable")
    void given_kindNotApplicable_should_skipInsteadOfSwitchingKind() {
      IocValidationPlanner.Plan plan =
          IocValidationPlanner.plan(
              ioc("IPv4-Addr", "203.0.113.7", IocValidationTestKind.DNS_RESOLUTION),
              allowAll("", ""));
      assertThat(plan.runnable()).isFalse();
      assertThat(plan.testKind()).isNull();
      assertThat(plan.message()).contains("does not apply");
    }
  }

  @Nested
  @DisplayName("Benign test per IOC type")
  class PerType {

    @Test
    @DisplayName("DNS resolution lower-cases the host name")
    void given_domain_should_planDnsResolution() {
      IocValidationPlanner.Plan plan =
          IocValidationPlanner.plan(
              ioc("Domain-Name", "Evil.Example.COM", IocValidationTestKind.DNS_RESOLUTION),
              allowAll("", ""));
      assertThat(plan.testKind()).isEqualTo(IocValidationTestKind.DNS_RESOLUTION);
      assertThat(plan.arguments()).containsValue("evil.example.com");
    }

    @Test
    @DisplayName("DNS resolution refuses values that are not host names")
    void given_invalidHost_should_skip() {
      IocValidationPlanner.Plan plan =
          IocValidationPlanner.plan(
              ioc("Domain-Name", "evil example;rm -rf", IocValidationTestKind.DNS_RESOLUTION),
              allowAll("", ""));
      assertThat(plan.runnable()).isFalse();
    }

    @Test
    @DisplayName("network test connects to the IP address with the configured port")
    void given_ipWithoutSinkhole_should_connectToAddress() {
      IocValidationPlanner.Plan plan =
          IocValidationPlanner.plan(
              ioc("IPv4-Addr", "203.0.113.7", IocValidationTestKind.NETWORK_TRAFFIC),
              allowAll("", ""));
      assertThat(plan.testKind()).isEqualTo(IocValidationTestKind.NETWORK_TRAFFIC);
      assertThat(plan.arguments())
          .containsEntry(IOC_VALIDATION_HOST_KEY, "203.0.113.7")
          .containsEntry(IOC_VALIDATION_PORT_KEY, "8443");
      assertThat(plan.message()).isNull();
    }

    @Test
    @DisplayName("network test is redirected to the sinkhole when one is configured")
    void given_sinkhole_should_substituteAddress() {
      IocValidationPlanner.Plan plan =
          IocValidationPlanner.plan(
              ioc("IPv4-Addr", "203.0.113.7", IocValidationTestKind.NETWORK_TRAFFIC),
              allowAll("", "192.0.2.10"));
      assertThat(plan.arguments()).containsEntry(IOC_VALIDATION_HOST_KEY, "192.0.2.10");
      assertThat(plan.arguments()).doesNotContainValue("203.0.113.7");
      assertThat(plan.message()).contains("sinkhole");
    }

    @Test
    @DisplayName("network ranges are never tested")
    void given_cidr_should_skip() {
      IocValidationPlanner.Plan plan =
          IocValidationPlanner.plan(
              ioc("IPv4-Addr", "203.0.113.0/24", IocValidationTestKind.NETWORK_TRAFFIC),
              allowAll("", ""));
      assertThat(plan.runnable()).isFalse();
    }

    @Test
    @DisplayName("HTTP HEAD needs the egress proxy")
    void given_urlWithoutProxy_should_skip() {
      IocValidationPlanner.Plan plan =
          IocValidationPlanner.plan(
              ioc("Url", "https://evil.example.com/payload", IocValidationTestKind.HTTP_HEAD),
              allowAll("", ""));
      assertThat(plan.runnable()).isFalse();
      assertThat(plan.message()).contains("proxy");
    }

    @Test
    @DisplayName("HTTP HEAD goes through the egress proxy")
    void given_urlWithProxy_should_planHttpHead() {
      IocValidationPlanner.Plan plan =
          IocValidationPlanner.plan(
              ioc("Url", "https://evil.example.com/payload", IocValidationTestKind.HTTP_HEAD),
              allowAll("http://proxy.internal:3128", ""),
              host -> List.of());
      assertThat(plan.testKind()).isEqualTo(IocValidationTestKind.HTTP_HEAD);
      assertThat(plan.arguments())
          .containsEntry(IOC_VALIDATION_PROXY_KEY, "http://proxy.internal:3128")
          .containsKey(IOC_VALIDATION_URL_KEY);
    }

    @Test
    @DisplayName("HTTP HEAD refuses non web schemes")
    void given_nonHttpUrl_should_skip() {
      IocValidationPlanner.Plan plan =
          IocValidationPlanner.plan(
              ioc("Url", "file:///etc/passwd", IocValidationTestKind.HTTP_HEAD),
              allowAll("http://proxy.internal:3128", ""));
      assertThat(plan.runnable()).isFalse();
    }

    @Test
    @DisplayName("file drop names the surrogate after the base name of the IOC file")
    void given_fileName_should_planSurrogateNamedAfterIt() {
      IocValidationIoc file = ioc("StixFile", "d41d8cd98f00b204e9800998ecf8427e", null);
      file.setRequestedTestKind(IocValidationTestKind.FILE_DROP);
      file.setFileName("C:\\Users\\Public\\invoice.exe");
      IocValidationPlanner.Plan plan = IocValidationPlanner.plan(file, allowAll("", ""));
      assertThat(plan.testKind()).isEqualTo(IocValidationTestKind.FILE_DROP);
      assertThat(plan.arguments()).containsEntry(IOC_VALIDATION_FILE_NAME_KEY, "invoice.exe");
    }

    @Test
    @DisplayName("file drop refuses a file name it would have to rewrite")
    void given_fileNameWithReservedCharacters_should_refuse() {
      IocValidationIoc file = ioc("StixFile", "d41d8cd98f00b204e9800998ecf8427e", null);
      file.setRequestedTestKind(IocValidationTestKind.FILE_DROP);
      file.setFileName("in<voice>.exe");
      IocValidationPlanner.Plan plan = IocValidationPlanner.plan(file, allowAll("", ""));
      assertThat(plan.runnable()).isFalse();
      assertThat(plan.refused()).isTrue();
      assertThat(plan.message()).startsWith("Refused: ").contains("'<'");
    }

    @Test
    @DisplayName("log injection writes the IOC value in a benign log line")
    void given_hash_should_planLogInjection() {
      IocValidationIoc hash =
          ioc(
              "StixFile",
              "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
              IocValidationTestKind.LOG_INJECTION);
      hash.setHashes(
          new java.util.LinkedHashMap<>(
              Map.of(
                  "SHA-256", "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855")));
      IocValidationPlanner.Plan plan = IocValidationPlanner.plan(hash, allowAll("", ""));
      assertThat(plan.testKind()).isEqualTo(IocValidationTestKind.LOG_INJECTION);
      assertThat(plan.arguments().get(IOC_VALIDATION_VALUE_KEY)).doesNotContain("\n", "\r");
    }
  }

  @Test
  @DisplayName("apply records the kind that runs and the reason of every skip")
  void given_iocs_should_recordPlanOnEachIoc() {
    IocValidationIoc runnable =
        ioc("Domain-Name", "evil.example.com", IocValidationTestKind.DNS_RESOLUTION);
    IocValidationIoc skipped =
        ioc("Url", "https://evil.example.com", IocValidationTestKind.HTTP_HEAD);

    IocValidationPlanner.apply(
        List.of(runnable, skipped),
        settings(EnumSet.of(IocValidationTestKind.DNS_RESOLUTION), "", ""));

    assertThat(runnable.getTestKind()).isEqualTo(IocValidationTestKind.DNS_RESOLUTION);
    assertThat(runnable.getMessage()).isNull();
    assertThat(skipped.getTestKind()).isNull();
    assertThat(skipped.getMessage()).isNotBlank();
    assertThat(runnable.getPlanFingerprint()).hasSize(64);
    assertThat(skipped.getPlanFingerprint()).isNull();
  }

  @Test
  @DisplayName("the plan fingerprint changes exactly when the test that would run changes")
  void given_settingsChange_should_changeFingerprintOnlyWithThePlan() {
    IocValidationIoc ioc = ioc("IPv4-Addr", "203.0.113.7", IocValidationTestKind.NETWORK_TRAFFIC);

    IocValidationPlanner.apply(List.of(ioc), allowAll("", "192.0.2.53"));
    String sinkholed = ioc.getPlanFingerprint();
    IocValidationPlanner.apply(
        List.of(ioc), allowAll("http://proxy.example.com:3128", "192.0.2.53"));
    String sinkholedWithProxy = ioc.getPlanFingerprint();
    IocValidationPlanner.apply(List.of(ioc), allowAll("", ""));
    String direct = ioc.getPlanFingerprint();

    assertThat(sinkholedWithProxy).isEqualTo(sinkholed);
    assertThat(direct).isNotNull().isNotEqualTo(sinkholed);
  }

  @Test
  @DisplayName("URL and IP validators only accept plain web URLs and single addresses")
  void given_values_should_validateUrlsAndAddresses() {
    assertThat(IocValidationPlanner.isHttpUrl("https://proxy.internal:3128")).isTrue();
    assertThat(IocValidationPlanner.isHttpUrl("ftp://proxy.internal")).isFalse();
    assertThat(IocValidationPlanner.isHttpUrl("not a url")).isFalse();
    assertThat(IocValidationPlanner.isIpLiteral("192.0.2.10")).isTrue();
    assertThat(IocValidationPlanner.isIpLiteral("2001:db8::1")).isTrue();
    assertThat(IocValidationPlanner.isIpLiteral("sinkhole.internal")).isFalse();
  }
}
