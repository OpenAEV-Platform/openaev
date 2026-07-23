package io.openaev.api.attackpath;

import static org.assertj.core.api.Assertions.assertThat;

import io.openaev.IntegrationTest;
import io.openaev.database.model.Tenant;
import io.openaev.database.model.attackpath.AttackPathExecution;
import io.openaev.database.model.attackpath.AttackPathExecutionFinding;
import io.openaev.database.model.attackpath.AttackPathFinding;
import io.openaev.database.repository.attackpath.AttackPathExecutionRepository;
import io.openaev.database.repository.attackpath.AttackPathFindingRepository;
import io.openaev.service.attackpath.AttackPathGraphService;
import io.openaev.service.attackpath.AttackPathIds;
import io.openaev.service.attackpath.dto.AttackPathFindingItemDTO;
import io.openaev.service.attackpath.dto.AttackPathFindingPageDTO;
import io.openaev.utils.fixtures.tenants.TenantFixture;
import io.openaev.utils.mockUser.WithMockUser;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

/**
 * The finding-widget drawer read (issue 5048). Listing a widget category's findings for a
 * simulation returns one item per (value, endpoint) with the producing execution ids (for the
 * cross-focus) and the resolved endpoint node id; it excludes orphan findings (the CF1 invariant),
 * filters by the category's finding types, and masks credential values server-side.
 */
@Transactional
@WithMockUser(isAdmin = true)
@DisplayName("attack path finding-widget drawer list read")
class AttackPathFindingsListTest extends IntegrationTest {

  private static final String SIM = "SIM-FINDINGS";

  @Autowired private AttackPathGraphService graphService;
  @Autowired private AttackPathExecutionRepository executionRepository;
  @Autowired private AttackPathFindingRepository findingRepository;

  private Tenant tenant;
  private String executionId;

  @BeforeEach
  void seed() {
    tenant = tenantRepository.save(TenantFixture.getTenant("ap-findings-tenant"));
    executionId = execution("nmap", "host-x");
    // A credentials finding produced by the execution, a cve produced too, and an orphan credential
    // with no producing execution (must be excluded).
    linkedFinding("host-x", "credentials", "admin:secret", executionId);
    linkedFinding("host-x", "cve", "CVE-2026-1", executionId);
    orphanFinding("host-x", "credentials", "orphan:nolink");
    entityManager.flush();
  }

  @Test
  @DisplayName("credentials category: one masked item, orphan and other types excluded")
  void listsCredentialsMaskedAndExcludesOrphanAndOtherTypes() {
    AttackPathFindingPageDTO page =
        graphService.listFindings(SIM, "credentials", PageRequest.of(0, 10));

    assertThat(page.total()).as("only the linked credential; orphan and cve excluded").isEqualTo(1);
    assertThat(page.items()).hasSize(1);

    AttackPathFindingItemDTO item = page.items().get(0);
    assertThat(item.type()).isEqualTo("credentials");
    assertThat(item.value())
        .as("the username is kept, the secret is masked, server-side")
        .startsWith("admin:")
        .doesNotContain("secret");
    assertThat(item.endpointKey()).isEqualTo("host-x");
    assertThat(item.endpointNodeId()).isEqualTo(AttackPathIds.endpointNode("host-x"));
    assertThat(item.executionIds())
        .as("the producing execution, for the cross-focus")
        .containsExactly(executionId);
  }

  @Test
  @DisplayName("cve category: one item, value not masked")
  void listsCvesUnmasked() {
    AttackPathFindingPageDTO page = graphService.listFindings(SIM, "cves", PageRequest.of(0, 10));

    assertThat(page.total()).isEqualTo(1);
    assertThat(page.items().get(0).type()).isEqualTo("cve");
    assertThat(page.items().get(0).value()).as("cve value is not masked").isEqualTo("CVE-2026-1");
  }

  @Test
  @DisplayName("a value shared across endpoints is one item per endpoint")
  void sharedValueAcrossEndpointsYieldsOneItemPerEndpoint() {
    // The same credential discovered on a second endpoint: two drawer items (one per endpoint), not
    // one collapsed item, so the drawer shows where each was found. Its own producing execution.
    String execYId = execution("nmap", "host-y");
    linkedFinding("host-y", "credentials", "admin:secret", execYId);
    entityManager.flush();

    AttackPathFindingPageDTO page =
        graphService.listFindings(SIM, "credentials", PageRequest.of(0, 10));

    assertThat(page.total()).as("the shared credential is one item per endpoint").isEqualTo(2);
    assertThat(page.items())
        .extracting(AttackPathFindingItemDTO::endpointNodeId)
        .containsExactlyInAnyOrder(
            AttackPathIds.endpointNode("host-x"), AttackPathIds.endpointNode("host-y"));
    assertThat(page.items())
        .allSatisfy(i -> assertThat(i.value()).startsWith("admin:").doesNotContain("secret"));
  }

  @Test
  @DisplayName("an unknown category (e.g. ports, not a product widget) yields an empty page")
  void unknownCategoryYieldsEmptyPage() {
    AttackPathFindingPageDTO page = graphService.listFindings(SIM, "ports", PageRequest.of(0, 10));

    assertThat(page.total()).isZero();
    assertThat(page.items()).isEmpty();
  }

  @Test
  @DisplayName("pagination is stable: pages are ordered by endpoint and do not overlap")
  void paginationIsStableAndOrdered() {
    // host-a and host-b in addition to seed()'s host-x credential: three credentials across
    // endpoints.
    String execA = execution("nmap", "host-a");
    String execB = execution("nmap", "host-b");
    linkedFinding("host-a", "credentials", "aaa:1", execA);
    linkedFinding("host-b", "credentials", "bbb:2", execB);
    entityManager.flush();

    AttackPathFindingPageDTO p0 =
        graphService.listFindings(SIM, "credentials", PageRequest.of(0, 1));
    AttackPathFindingPageDTO p1 =
        graphService.listFindings(SIM, "credentials", PageRequest.of(1, 1));

    assertThat(p0.total()).as("count is across all pages").isEqualTo(3);
    assertThat(p0.items())
        .singleElement()
        .extracting(AttackPathFindingItemDTO::endpointKey)
        .isEqualTo("host-a");
    assertThat(p1.items())
        .as("the next page is the next endpoint, no overlap")
        .singleElement()
        .extracting(AttackPathFindingItemDTO::endpointKey)
        .isEqualTo("host-b");
  }

  private String execution(String injector, String endpoint) {
    AttackPathExecution e = new AttackPathExecution();
    e.setTenant(tenant);
    e.setSimulationId(SIM);
    e.setSourceKind("INJECTOR");
    e.setSourceInjector(injector);
    e.setTargetKind("ASSET");
    e.setTargetAssetId(endpoint);
    e.setTargetKey(endpoint);
    e.setTargetHostname(endpoint);
    e.setExecutedAt(Instant.parse("2026-06-18T08:00:00Z"));
    e.setPreventionStatus("Prevented");
    e.setDetectionStatus("Not Detected");
    return executionRepository.save(e).getId();
  }

  private void linkedFinding(String endpoint, String type, String value, String execId) {
    String findingId = saveFinding(endpoint, type, value);
    AttackPathExecutionFinding link = new AttackPathExecutionFinding();
    link.setExecutionId(execId);
    link.setFindingId(findingId);
    entityManager.persist(link);
  }

  private void orphanFinding(String endpoint, String type, String value) {
    saveFinding(endpoint, type, value);
  }

  private String saveFinding(String endpoint, String type, String value) {
    AttackPathFinding f = new AttackPathFinding();
    f.setTenant(tenant);
    f.setSimulationId(SIM);
    f.setType(type);
    f.setValue(value);
    f.setEndpointId(endpoint);
    f.setEndpointKey(endpoint);
    return findingRepository.save(f).getId();
  }
}
