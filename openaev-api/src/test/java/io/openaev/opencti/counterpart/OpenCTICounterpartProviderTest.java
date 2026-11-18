package io.openaev.opencti.counterpart;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.openaev.opencti.config.XtmConfig;
import io.openaev.xtm.counterpart.Counterpart;
import io.openaev.xtm.counterpart.CounterpartKind;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class OpenCTICounterpartProviderTest {

  private final ObjectMapper mapper = new ObjectMapper();

  private OpenCTICounterpartProvider provider(Map<String, Object> opencti) {
    XtmConfig config = new XtmConfig();
    org.springframework.test.util.ReflectionTestUtils.setField(config, "mapper", mapper);
    config.setOpencti(opencti);
    return new OpenCTICounterpartProvider(config, null, mapper);
  }

  @Test
  @DisplayName("a tenant is connected only with enable, url and token")
  void connectedUrl() {
    OpenCTICounterpartProvider p =
        provider(
            Map.of(
                "a", Map.of("enable", true, "url", "https://octi", "token", "x"),
                "b", Map.of("enable", false, "url", "https://octi", "token", "x"),
                "c", Map.of("enable", true, "url", "https://octi")));

    assertThat(p.connectedUrl("a")).contains("https://octi/");
    assertThat(p.connectedUrl("b")).isEmpty();
    assertThat(p.connectedUrl("c")).isEmpty();
    assertThat(p.connectedUrl("unknown")).isEmpty();
  }

  @Test
  @DisplayName("an attack pattern is looked up by x_mitre_id on Attack-Pattern only")
  void attackPatternFilter() {
    ObjectNode vars =
        provider(Map.of()).variables(CounterpartKind.ATTACK_PATTERN, List.of("T1059", "T1003"));

    JsonNode filters = vars.path("filters").path("filters");
    assertThat(vars.path("first").asInt()).isEqualTo(4);
    assertThat(filters.get(0).path("key").get(0).asText()).isEqualTo("entity_type");
    assertThat(filters.get(0).path("values").get(0).asText()).isEqualTo("Attack-Pattern");
    assertThat(filters.get(1).path("key").get(0).asText()).isEqualTo("x_mitre_id");
    assertThat(filters.get(1).path("values")).hasSize(2);
  }

  @Test
  @DisplayName("an asset is looked up on systems by name or on host and address observables")
  void assetFilter() {
    ObjectNode vars = provider(Map.of()).variables(CounterpartKind.ASSET, List.of("10.0.0.1"));

    JsonNode groups = vars.path("filters").path("filterGroups");
    assertThat(vars.path("filters").path("mode").asText()).isEqualTo("or");
    assertThat(groups).hasSize(2);
    assertThat(groups.get(1).path("filters").get(0).path("values")).hasSize(4);
    assertThat(groups.get(1).path("filters").get(1).path("key").get(0).asText())
        .isEqualTo("value");
  }

  @Test
  @DisplayName("only nodes whose identifier is a requested key match, with the deep link")
  void match() throws Exception {
    JsonNode data =
        mapper.readTree(
            """
            {"stixCoreObjects":{"edges":[
              {"node":{"id":"v1","entity_type":"Vulnerability","name":"cve-2024-1",
                       "representative":{"main":"CVE-2024-1"}}},
              {"node":{"id":"v2","entity_type":"Vulnerability","name":"CVE-2024-9",
                       "representative":{"main":"CVE-2024-9"}}}
            ]}}
            """);

    Map<String, Counterpart> found =
        provider(Map.of())
            .match(CounterpartKind.VULNERABILITY, List.of("CVE-2024-1"), data, "https://octi/");

    assertThat(found).containsOnlyKeys("CVE-2024-1");
    assertThat(found.get("CVE-2024-1").url()).isEqualTo("https://octi/dashboard/id/v1");
    assertThat(found.get("CVE-2024-1").entityType()).isEqualTo("Vulnerability");
  }
}
