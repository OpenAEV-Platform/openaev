package io.openaev.rest.finding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.IntegrationTest;
import io.openaev.config.ProductionActiveTables;
import io.openaev.database.model.Agent;
import io.openaev.database.model.ContractOutputType;
import io.openaev.database.model.Endpoint;
import io.openaev.database.model.Inject;
import io.openaev.database.model.InjectStatus;
import io.openaev.database.model.Tenant;
import io.openaev.finding.identity.LocationIdentity.UnknownLocation;
import io.openaev.output_processor.CVEOutputProcessor;
import io.openaev.rest.inject.service.ContractOutputContext;
import io.openaev.rest.inject.service.ExecutionProcessingContext;
import io.openaev.utils.mockUser.WithMockUser;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Predicate;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Each source writes its findings through {@code recordFindingsByInject} (#7848, chunk 4): the
 * injector locates them on the assets of its output, the agent on the asset whose host they name.
 *
 * <p>Not {@code @Transactional}: the writer opens its own {@code REQUIRES_NEW} transaction, so the
 * seeded rows must be committed for it to see them. They are removed after each test.
 */
@WithMockUser(isAdmin = true)
@DisplayName("FindingService.generateFindings")
class FindingGenerationTest extends IntegrationTest {

  @DynamicPropertySource
  static void activeTables(DynamicPropertyRegistry registry) {
    registry.add(
        "openaev.tenant.active-tables", () -> String.join(",", ProductionActiveTables.names()));
  }

  private static final Instant SENT_DATE = Instant.parse("2026-11-02T09:00:00Z");
  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final Predicate<JsonNode> HAS_ID = node -> node.hasNonNull("id");
  private static final Function<JsonNode, String> ID = node -> node.get("id").asText();

  @Autowired private FindingService findingService;
  @Autowired private CVEOutputProcessor cveOutputProcessor;
  @Autowired private DataSource dataSource;

  private JdbcTemplate jdbc;
  private String tenantId;
  private Inject inject;
  private String assetA;
  private String assetB;

  @BeforeEach
  void seed() {
    jdbc = new JdbcTemplate(dataSource);
    tenantId = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO tenants (tenant_id, tenant_name, tenant_created_at, tenant_updated_at)"
            + " VALUES (?, ?, now(), now())",
        tenantId,
        "generate-findings-" + tenantId);
    String injectId = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO injects (inject_id, inject_title, inject_created_at, inject_updated_at,"
            + " inject_depends_duration, inject_all_teams, inject_enabled, tenant_id)"
            + " VALUES (?, 'generate findings inject', now(), now(), 0, false, true, ?)",
        injectId,
        tenantId);
    inject = new Inject();
    inject.setId(injectId);
    inject.setTenant(new Tenant(tenantId));
    InjectStatus status = new InjectStatus();
    status.setTrackingSentDate(SENT_DATE);
    inject.setStatus(status);
    assetA = seedAsset();
    assetB = seedAsset();
  }

  @AfterEach
  void sweep() {
    String ofTenant = " WHERE finding_id IN (SELECT finding_id FROM findings WHERE tenant_id = ?)";
    jdbc.update("DELETE FROM findings_assets" + ofTenant, tenantId);
    jdbc.update("DELETE FROM findings WHERE tenant_id = ?", tenantId);
    jdbc.update("DELETE FROM assets WHERE tenant_id = ?", tenantId);
    jdbc.update("DELETE FROM injects WHERE tenant_id = ?", tenantId);
    jdbc.update("DELETE FROM tenants WHERE tenant_id = ?", tenantId);
  }

  @Nested
  @DisplayName("When an injector reports findings")
  class WhenAnInjectorReportsFindings {

    @Test
    @DisplayName("given an entry on two assets, should create one finding per asset")
    void given_anEntryOnTwoAssets_should_createOneFindingPerAsset() throws Exception {
      // -- ACT --
      generateInjectorFindings(
          "[{\"id\": \"CVE-2024-1234\", \"asset_id\": [\"%s\", \"%s\"]}]"
              .formatted(assetA, assetB));

      // -- ASSERT --
      assertThat(locationKeys()).containsExactlyInAnyOrder("asset:" + assetA, "asset:" + assetB);
    }

    @Test
    @DisplayName("given a malformed entry, should skip it and keep the others")
    void given_aMalformedEntry_should_skipItAndKeepTheOthers() throws Exception {
      // -- ACT --
      generateInjectorFindings(
          "[null, {\"severity\": \"high\"}, {\"id\": \"CVE-2024-1234\", \"asset_id\": \"%s\"}]"
              .formatted(assetA));

      // -- ASSERT --
      assertThat(locationKeys()).containsExactly("asset:" + assetA);
    }

    @Test
    @DisplayName("given two entries, should create one finding each")
    void given_twoEntries_should_createOneFindingEach() throws Exception {
      // -- ACT --
      generateInjectorFindings(
          ("[{\"id\": \"CVE-2024-1234\", \"asset_id\": \"%s\"},"
                  + " {\"id\": \"CVE-2024-5678\", \"asset_id\": \"%s\"}]")
              .formatted(assetA, assetA));

      // -- ASSERT --
      assertThat(findingValues()).containsExactlyInAnyOrder("CVE-2024-1234", "CVE-2024-5678");
    }

    @Test
    @DisplayName("given a malformed single output, should skip it without throwing")
    void given_aMalformedSingleOutput_should_skipItWithoutThrowing() {
      // -- ACT & ASSERT --
      assertThatCode(() -> generateInjectorFindings("{\"severity\": \"high\"}", false))
          .doesNotThrowAnyException();
      assertThat(findingValues()).isEmpty();
    }
  }

  @Nested
  @DisplayName("When an agent reports findings")
  class WhenAnAgentReportsFindings {

    @Test
    @DisplayName("given a host matching a targeted asset, should locate the finding on it")
    void given_aHostMatchingATargetedAsset_should_locateTheFindingOnIt() throws Exception {
      // -- ACT --
      generateAgentFindings(
          "[{\"id\": \"CVE-2024-1234\", \"host\": \"10.0.0.2\"}]",
          Map.of("10.0.0.2", endpoint(assetB)));

      // -- ASSERT --
      assertThat(locationKeys()).containsExactly("asset:" + assetB);
    }

    @Test
    @DisplayName("given no host, should locate the finding on the agent's asset")
    void given_noHost_should_locateTheFindingOnTheAgentsAsset() throws Exception {
      // -- ACT --
      generateAgentFindings("[{\"id\": \"CVE-2024-1234\"}]", Map.of());

      // -- ASSERT --
      assertThat(locationKeys()).containsExactly("asset:" + assetA);
    }

    @Test
    @DisplayName("given a host matching no targeted asset, should drop the finding")
    void given_aHostMatchingNoTargetedAsset_should_dropTheFinding() throws Exception {
      // -- ACT --
      generateAgentFindings(
          "[{\"id\": \"CVE-2024-1234\", \"host\": \"10.0.0.9\"}]",
          Map.of("10.0.0.2", endpoint(assetB)));

      // -- ASSERT --
      assertThat(locationKeys()).isEmpty();
    }
  }

  // -- HELPERS --

  private void generateInjectorFindings(String output) throws Exception {
    generateInjectorFindings(output, true);
  }

  private void generateInjectorFindings(String output, boolean multiple) throws Exception {
    findingService.generateFindings(
        new ExecutionProcessingContext(inject, null, null, Map.of()),
        context(multiple),
        MAPPER.readTree(output),
        HAS_ID,
        ID,
        cveOutputProcessor::toFindingLocations);
  }

  private void generateAgentFindings(String output, Map<String, Endpoint> targetedAssets)
      throws Exception {
    Agent agent = new Agent();
    agent.setAsset(endpoint(assetA));
    findingService.generateFindings(
        new ExecutionProcessingContext(inject, agent, null, targetedAssets),
        context(true),
        MAPPER.readTree(output),
        HAS_ID,
        ID,
        node -> List.of(UnknownLocation.INSTANCE));
  }

  private static ContractOutputContext context(boolean multiple) {
    return new ContractOutputContext(
        "cves", "CVE", ContractOutputType.CVE, multiple, new String[0]);
  }

  private static Endpoint endpoint(String assetId) {
    Endpoint endpoint = new Endpoint();
    endpoint.setId(assetId);
    return endpoint;
  }

  private String seedAsset() {
    String assetId = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO assets (asset_id, asset_name, asset_type, asset_created_at, asset_updated_at,"
            + " tenant_id, asset_hostname, endpoint_platform, endpoint_arch)"
            + " VALUES (?, ?, 'Endpoint', now(), now(), ?, ?, 'Linux', 'x86_64')",
        assetId,
        "generate-asset-" + assetId,
        tenantId,
        "generate-asset-" + assetId);
    return assetId;
  }

  private List<String> findingValues() {
    return jdbc.queryForList(
        "SELECT finding_value FROM findings WHERE tenant_id = ?", String.class, tenantId);
  }

  private List<String> locationKeys() {
    return jdbc.queryForList(
        "SELECT l.finding_location_key FROM findings f"
            + " JOIN finding_locations l ON l.finding_location_id = f.finding_location_id"
            + " WHERE f.tenant_id = ?",
        String.class,
        tenantId);
  }
}
