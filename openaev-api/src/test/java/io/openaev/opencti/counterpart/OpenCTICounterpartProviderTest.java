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
    assertThat(groups.get(1).path("filters").get(1).path("key").get(0).asText()).isEqualTo("value");
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
    assertThat(found.get("CVE-2024-1").facts()).isEmpty();
  }

  @Test
  @DisplayName("a match carries the reports, confidence, last change and markings OpenCTI answered")
  void matchFacts() throws Exception {
    JsonNode data =
        mapper.readTree(
            """
            {"stixCoreObjects":{"edges":[
              {"node":{"id":"a1","entity_type":"Attack-Pattern","x_mitre_id":"T1059",
                       "representative":{"main":"Command and Scripting Interpreter"},
                       "confidence":75,"modified":"2026-11-20T10:00:00.000Z",
                       "reports":{"pageInfo":{"globalCount":12}},
                       "objectMarking":[{"definition":"TLP:GREEN"},{"definition":"PAP:CLEAR"}]}}
            ]}}
            """);

    Counterpart found =
        provider(Map.of())
            .match(CounterpartKind.ATTACK_PATTERN, List.of("T1059"), data, "https://octi/")
            .get("T1059");

    assertThat(found.facts())
        .containsExactly(
            new Counterpart.Fact("reports", "12"),
            new Counterpart.Fact("confidence", "75"),
            new Counterpart.Fact("last_modified", "2026-11-20T10:00:00.000Z"),
            new Counterpart.Fact("markings", "TLP:GREEN, PAP:CLEAR"));
  }

  @Test
  @DisplayName("an observable without confidence or markings gives only the facts it has")
  void matchPartialFacts() throws Exception {
    JsonNode node =
        mapper.readTree(
            """
            {"id":"o1","entity_type":"Hostname","observable_value":"srv-01",
             "reports":{"pageInfo":{"globalCount":0}},"objectMarking":[]}
            """);

    assertThat(OpenCTICounterpartProvider.facts(node))
        .containsExactly(new Counterpart.Fact("reports", "0"));
  }

  @Test
  @DisplayName("an alert is looked up on external references by url")
  void alertFilter() {
    ObjectNode vars =
        provider(Map.of()).variables(CounterpartKind.ALERT, List.of("https://edr/alert/1"));

    JsonNode filters = vars.path("filters").path("filters");
    assertThat(filters).hasSize(1);
    assertThat(filters.get(0).path("key").get(0).asText()).isEqualTo("url");
    assertThat(filters.get(0).path("values").get(0).asText()).isEqualTo("https://edr/alert/1");
  }

  @Test
  @DisplayName("an alert link resolves to the incident referencing it before a sighting or report")
  void matchAlerts() throws Exception {
    JsonNode data =
        mapper.readTree(
            """
            {"externalReferences":{"edges":[
              {"node":{"url":"https://edr/alert/1","references":{"edges":[
                {"node":{"id":"r1","entity_type":"Report","representative":{"main":"Weekly"}}},
                {"node":{"id":"s1","entity_type":"stix-sighting-relationship",
                         "representative":{"main":"seen"}}},
                {"node":{"id":"i1","entity_type":"Incident","representative":{"main":"EDR alert"}}}
              ]}}},
              {"node":{"url":"https://edr/alert/2","references":{"edges":[]}}},
              {"node":{"url":"https://other","references":{"edges":[
                {"node":{"id":"x","entity_type":"Incident","representative":{"main":"x"}}}
              ]}}}
            ]}}
            """);

    Map<String, Counterpart> found =
        provider(Map.of())
            .matchAlerts(
                List.of("https://edr/alert/1", "https://edr/alert/2"), data, "https://octi/");

    assertThat(found).containsOnlyKeys("https://edr/alert/1");
    assertThat(found.get("https://edr/alert/1").entityType()).isEqualTo("Incident");
    assertThat(found.get("https://edr/alert/1").url()).isEqualTo("https://octi/dashboard/id/i1");
  }
}
