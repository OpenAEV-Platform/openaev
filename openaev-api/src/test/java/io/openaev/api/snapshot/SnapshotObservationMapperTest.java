package io.openaev.api.snapshot;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.jayway.jsonpath.JsonPath;
import io.openaev.api.snapshot.form.AttackObservationOutput;
import io.openaev.api.snapshot.form.VulnerabilityObservationOutput;
import io.openaev.engine.model.snapshotobservation.EsAttackObservation;
import io.openaev.engine.model.snapshotobservation.EsVulnerabilityObservation;
import java.time.Instant;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Regression net for the FR4/FR5 wire contract (story 7505, §15.1/§15.5): every JSON property name
 * is asserted, not just the record components, so a {@code @JsonProperty} typo fails the build.
 */
class SnapshotObservationMapperTest {

  private final SnapshotObservationMapper mapper = new SnapshotObservationMapper();
  private final ObjectMapper objectMapper =
      new ObjectMapper()
          .registerModule(new JavaTimeModule())
          .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

  private String asJson(Object value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  @Nested
  @DisplayName("Attack observation")
  class AttackObservation {

    @Test
    @DisplayName("every FR4 field is mapped and serialized under its contract name")
    void given_fullyPopulatedDocument_should_mapEveryField() {
      // -- ARRANGE --
      EsAttackObservation es = new EsAttackObservation();
      es.setBase_id("obs-id");
      es.setBase_updated_at(Instant.parse("2024-01-01T00:00:00Z"));
      es.setBase_asset_side("asset-id");
      es.setBase_scenario_side("scenario-id");
      es.setBase_simulation_side("simulation-id");
      es.setBase_security_platforms_side(Set.of("platform-id"));
      es.setAsset_name("my-asset");
      es.setAsset_hostname("WIN-HOST");
      es.setEndpoint_platform("Windows");
      es.setAttack_observation_tenant_name("my-tenant");
      es.setAttack_observation_attack_pattern_external_id("T1055");
      es.setAttack_observation_attack_pattern_name("Process Injection");
      es.setAttack_observation_scenario_name("my-scenario");
      es.setAttack_observation_simulation_name("my-simulation");
      es.setAttack_observation_expectation_type("PREVENTION");
      es.setAttack_observation_status("SUCCESS");
      es.setAttack_observation_attempts_total(3L);
      es.setAttack_observation_attempts_success(2L);
      es.setAttack_observation_coverage_ratio(0.66);
      es.setAttack_observation_platforms_succeeded(Set.of("platform-id"));
      es.setAttack_observation_last_verified_at(Instant.parse("2024-01-02T00:00:00Z"));

      // -- ACT --
      AttackObservationOutput output = mapper.toOutput(es);
      String json = asJson(output);

      // -- ASSERT: every FR4 field, under its contract name --
      assertThat((String) JsonPath.read(json, "$.id")).isEqualTo("obs-id");
      assertThat((String) JsonPath.read(json, "$.updated_at")).isEqualTo("2024-01-01T00:00:00Z");
      assertThat((String) JsonPath.read(json, "$.asset_id")).isEqualTo("asset-id");
      assertThat((String) JsonPath.read(json, "$.scenario_id")).isEqualTo("scenario-id");
      assertThat((String) JsonPath.read(json, "$.last_simulation_id")).isEqualTo("simulation-id");
      assertThat((java.util.List<String>) JsonPath.read(json, "$.platforms_reporting"))
          .containsExactly("platform-id");
      assertThat((String) JsonPath.read(json, "$.asset_name")).isEqualTo("my-asset");
      assertThat((String) JsonPath.read(json, "$.endpoint_hostname")).isEqualTo("WIN-HOST");
      assertThat((String) JsonPath.read(json, "$.endpoint_platform")).isEqualTo("Windows");
      assertThat((String) JsonPath.read(json, "$.tenant_name")).isEqualTo("my-tenant");
      assertThat((String) JsonPath.read(json, "$.attack_pattern_external_id")).isEqualTo("T1055");
      assertThat((String) JsonPath.read(json, "$.attack_pattern_name"))
          .isEqualTo("Process Injection");
      assertThat((String) JsonPath.read(json, "$.scenario_name")).isEqualTo("my-scenario");
      assertThat((String) JsonPath.read(json, "$.last_simulation_name")).isEqualTo("my-simulation");
      assertThat((String) JsonPath.read(json, "$.expectation_type")).isEqualTo("PREVENTION");
      assertThat((String) JsonPath.read(json, "$.expectation_status")).isEqualTo("SUCCESS");
      assertThat((Integer) JsonPath.read(json, "$.attempts_total")).isEqualTo(3);
      assertThat((Integer) JsonPath.read(json, "$.attempts_success")).isEqualTo(2);
      assertThat((Double) JsonPath.read(json, "$.coverage_ratio")).isEqualTo(0.66);
      assertThat((java.util.List<String>) JsonPath.read(json, "$.platforms_succeeded"))
          .containsExactly("platform-id");
      assertThat((String) JsonPath.read(json, "$.last_verified_at"))
          .isEqualTo("2024-01-02T00:00:00Z");

      // -- ASSERT: base_attack_patterns_side deliberately stays unexposed (§15.1) --
      assertThat(json).doesNotContain("attack_pattern_id").doesNotContain("attack_patterns_side");
    }
  }

  @Nested
  @DisplayName("Vulnerability observation")
  class VulnerabilityObservation {

    @Test
    @DisplayName("every FR5 field is mapped and serialized under its contract name")
    void given_fullyPopulatedDocument_should_mapEveryField() {
      // -- ARRANGE --
      EsVulnerabilityObservation es = new EsVulnerabilityObservation();
      es.setBase_id("obs-id");
      es.setBase_updated_at(Instant.parse("2024-01-01T00:00:00Z"));
      es.setBase_asset_side("asset-id");
      es.setBase_findings_side(Set.of("finding-id"));
      es.setBase_scenario_side("scenario-id");
      es.setBase_simulation_side("simulation-id");
      es.setFinding_type("CVE");
      es.setFinding_value("CVE-2024-1234");
      es.setAsset_name("my-asset");
      es.setAsset_hostname("WIN-HOST");
      es.setEndpoint_platform("Windows");
      es.setVulnerability_observation_tenant_name("my-tenant");
      es.setVulnerability_observation_external_id("CVE-2024-1234");
      es.setVulnerability_observation_scenario_name("my-scenario");
      es.setVulnerability_observation_simulation_name("my-simulation");
      es.setVulnerability_observation_last_verified_at(Instant.parse("2024-01-02T00:00:00Z"));

      // -- ACT --
      VulnerabilityObservationOutput output = mapper.toOutput(es);
      String json = asJson(output);

      // -- ASSERT: every FR5 field, under its contract name --
      assertThat((String) JsonPath.read(json, "$.id")).isEqualTo("obs-id");
      assertThat((String) JsonPath.read(json, "$.updated_at")).isEqualTo("2024-01-01T00:00:00Z");
      assertThat((String) JsonPath.read(json, "$.asset_id")).isEqualTo("asset-id");
      assertThat((String) JsonPath.read(json, "$.last_finding_id")).isEqualTo("finding-id");
      assertThat((String) JsonPath.read(json, "$.last_scenario_id")).isEqualTo("scenario-id");
      assertThat((String) JsonPath.read(json, "$.last_simulation_id")).isEqualTo("simulation-id");
      assertThat((String) JsonPath.read(json, "$.finding_type")).isEqualTo("CVE");
      assertThat((String) JsonPath.read(json, "$.finding_value")).isEqualTo("CVE-2024-1234");
      assertThat((String) JsonPath.read(json, "$.asset_name")).isEqualTo("my-asset");
      assertThat((String) JsonPath.read(json, "$.endpoint_hostname")).isEqualTo("WIN-HOST");
      assertThat((String) JsonPath.read(json, "$.endpoint_platform")).isEqualTo("Windows");
      assertThat((String) JsonPath.read(json, "$.tenant_name")).isEqualTo("my-tenant");
      assertThat((String) JsonPath.read(json, "$.vulnerability_external_id"))
          .isEqualTo("CVE-2024-1234");
      assertThat((String) JsonPath.read(json, "$.last_scenario_name")).isEqualTo("my-scenario");
      assertThat((String) JsonPath.read(json, "$.last_simulation_name")).isEqualTo("my-simulation");
      assertThat((String) JsonPath.read(json, "$.last_verified_at"))
          .isEqualTo("2024-01-02T00:00:00Z");
    }

    @Test
    @DisplayName("an empty findings set maps to a null last_finding_id")
    void given_emptyFindingsSet_should_mapToNull() {
      // -- ARRANGE --
      EsVulnerabilityObservation es = new EsVulnerabilityObservation();
      es.setBase_findings_side(Set.of());

      // -- ACT --
      VulnerabilityObservationOutput output = mapper.toOutput(es);

      // -- ASSERT --
      assertThat(output.lastFindingId()).isNull();
    }
  }
}
