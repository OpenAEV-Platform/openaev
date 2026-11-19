package io.openaev.opencrq.counterpart;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.opencti.config.XtmConfig;
import io.openaev.xtm.counterpart.Counterpart;
import io.openaev.xtm.counterpart.CounterpartKind;
import io.openaev.xtm.counterpart.CounterpartUnavailableException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class OpenCRQCounterpartProviderTest {

  private final ObjectMapper mapper = new ObjectMapper();

  private OpenCRQCounterpartProvider provider(Map<String, Object> opencrq) {
    XtmConfig config = new XtmConfig();
    org.springframework.test.util.ReflectionTestUtils.setField(config, "mapper", mapper);
    config.setOpencrq(opencrq);
    return new OpenCRQCounterpartProvider(config, null, mapper);
  }

  @Test
  @DisplayName("a tenant is connected to OpenCRQ only with enable, url and token")
  void connectedUrl() {
    OpenCRQCounterpartProvider p =
        provider(
            Map.of(
                "a", Map.of("enable", true, "url", "https://ocrq", "token", "ogrc_x"),
                "b", Map.of("enable", false, "url", "https://ocrq", "token", "ogrc_x"),
                "c", Map.of("enable", true, "url", "https://ocrq")));

    assertThat(p.connectedUrl("a")).contains("https://ocrq/");
    assertThat(p.connectedUrl("b")).isEmpty();
    assertThat(p.connectedUrl("c")).isEmpty();
    assertThat(p.connectedUrl("unknown")).isEmpty();
    assertThat(p.connectedUrl(null)).isEmpty();
  }

  @Test
  @DisplayName("OpenCRQ holds threats, vulnerabilities and assets, never attack patterns or alerts")
  void supports() {
    OpenCRQCounterpartProvider p = provider(Map.of());

    assertThat(p.supports(CounterpartKind.THREAT)).isTrue();
    assertThat(p.supports(CounterpartKind.VULNERABILITY)).isTrue();
    assertThat(p.supports(CounterpartKind.ASSET)).isTrue();
    assertThat(p.supports(CounterpartKind.ATTACK_PATTERN)).isFalse();
    assertThat(p.supports(CounterpartKind.ALERT)).isFalse();
  }

  @Test
  @DisplayName("the lookup is one tRPC query of xtm.lookup with the kind and the keys")
  void lookupUrl() {
    String url =
        provider(Map.of())
            .lookupUrl(
                "https://ocrq/trpc", CounterpartKind.VULNERABILITY, List.of("CVE-2024-1", "CVE-2"));

    assertThat(url).startsWith("https://ocrq/trpc/xtm.lookup?input=");
    String input = URLDecoder.decode(url.substring(url.indexOf('=') + 1), StandardCharsets.UTF_8);
    assertThat(input).isEqualTo("{\"kind\":\"VULNERABILITY\",\"keys\":[\"CVE-2024-1\",\"CVE-2\"]}");
  }

  @Test
  @DisplayName("an answered key matches the asked key in any case and carries the risk facts")
  void match() throws Exception {
    JsonNode body =
        mapper.readTree(
            """
            {"result":{"data":[
              {"object_key":"intrusion-set--abc","object_id":"t1","object_type":"Intrusion-Set",
               "object_name":"APT29","object_url":"https://ocrq/t/threat-center/threats/t1",
               "object_facts":[{"key":"risk_scenarios","value":"3"},
                               {"key":"annual_loss","value":"125000"}]},
              {"object_key":"intrusion-set--zzz","object_id":"t2","object_type":"Intrusion-Set",
               "object_name":"other","object_url":"https://ocrq/t/x","object_facts":[]},
              {"object_key":"intrusion-set--abc","object_id":"t3","object_type":"Intrusion-Set",
               "object_name":"dup","object_url":"https://ocrq/t/y","object_facts":[]}
            ]}}
            """);

    Map<String, Counterpart> found =
        provider(Map.of()).match(CounterpartKind.THREAT, List.of("Intrusion-Set--ABC"), body);

    assertThat(found).containsOnlyKeys("Intrusion-Set--ABC");
    Counterpart c = found.get("Intrusion-Set--ABC");
    assertThat(c.product()).isEqualTo("opencrq");
    assertThat(c.entityId()).isEqualTo("t1");
    assertThat(c.url()).isEqualTo("https://ocrq/t/threat-center/threats/t1");
    assertThat(c.facts())
        .containsExactly(
            new Counterpart.Fact("risk_scenarios", "3"),
            new Counterpart.Fact("annual_loss", "125000"));
  }

  @Test
  @DisplayName("an answer without the tRPC result shape matches nothing")
  void matchEmpty() throws Exception {
    assertThat(
            provider(Map.of())
                .match(
                    CounterpartKind.ASSET,
                    List.of("host"),
                    mapper.readTree("{\"error\":{\"message\":\"FORBIDDEN\"}}")))
        .isEmpty();
  }

  @Test
  @DisplayName("a tenant not connected to OpenCRQ is refused as unavailable before any call")
  void notConnected() {
    assertThatThrownBy(
            () -> provider(Map.of()).resolve("t", CounterpartKind.ASSET, List.of("host")))
        .isInstanceOf(CounterpartUnavailableException.class);
  }
}
