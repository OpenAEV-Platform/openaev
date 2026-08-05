package io.openaev.rest.finding;

import static io.openaev.utils.fixtures.AssetFixture.createDefaultAsset;
import static io.openaev.utils.fixtures.InjectFixture.getDefaultInject;
import static io.openaev.utils.fixtures.OutputParserFixture.getContractOutputElementTypeIPv6;
import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.openaev.IntegrationTest;
import io.openaev.context.TenantContext;
import io.openaev.database.model.*;
import io.openaev.database.repository.FindingRepository;
import io.openaev.database.repository.InjectRepository;
import io.openaev.injector_contract.outputs.InjectorContractContentOutputElement;
import io.openaev.rest.inject.service.ContractOutputContext;
import io.openaev.utils.helpers.InjectTestHelper;
import io.openaev.utils.injector_contract.InjectorContractContentUtils;
import io.openaev.utils.mockUser.WithMockUser;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityNotFoundException;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Transactional
class FindingServiceTest extends IntegrationTest {

  public static final String ASSET_1 = "asset1";
  public static final String ASSET_2 = "asset2";

  @Autowired private InjectTestHelper injectTestHelper;
  @Autowired private FindingService findingService;
  @Autowired private FindingRepository findingRepository;
  @Autowired private InjectRepository injectRepository;
  @Autowired private InjectorContractContentUtils injectorContractContentUtils;
  @Autowired private EntityManager entityManager;
  @Autowired private PlatformTransactionManager transactionManager;

  @Test
  @DisplayName("Should have two assets when finding already exists with one asset")
  void given_a_finding_already_existent_with_one_asset_should_have_two_assets() {
    Inject inject = getDefaultInject();
    Asset asset1 = injectTestHelper.forceSaveAsset(createDefaultAsset(ASSET_1));
    Asset asset2 = injectTestHelper.forceSaveAsset(createDefaultAsset(ASSET_2));
    String value = "value-already-existent";
    ContractOutputElement contractOutputElement = getContractOutputElementTypeIPv6();
    ContractOutputContext contractOutputContext = ContractOutputContext.from(contractOutputElement);

    Finding existing = new Finding();
    existing.setValue(value);
    existing.setInject(inject);
    existing.setField(contractOutputElement.getKey());
    existing.setType(contractOutputElement.getType());
    existing.setAssets(new ArrayList<>(List.of(asset1)));

    injectTestHelper.forceSaveInject(inject);
    injectTestHelper.forceSaveFinding(existing);

    findingService.saveAgentFinding(inject, asset2, contractOutputContext, value);

    Finding result =
        findingRepository
            .findByInjectIdAndValueAndTypeAndKey(
                inject.getId(),
                value,
                contractOutputElement.getType(),
                contractOutputElement.getKey())
            .orElseThrow();

    assertEquals(2, result.getAssets().size());
    Set<String> assetIds =
        result.getAssets().stream().map(Asset::getId).collect(Collectors.toSet());
    assertTrue(assetIds.contains(asset1.getId()));
    assertTrue(assetIds.contains(asset2.getId()));
  }

  @Test
  @DisplayName("Should have one asset when finding already exists with the same asset")
  void given_a_finding_already_existent_with_same_asset_should_have_one_asset() {
    Inject inject = getDefaultInject();
    Asset asset1 = injectTestHelper.forceSaveAsset(createDefaultAsset(ASSET_1));
    String value = "value-already-existent";
    ContractOutputElement contractOutputElement = getContractOutputElementTypeIPv6();
    ContractOutputContext contractOutputContext = ContractOutputContext.from(contractOutputElement);

    Finding existing = new Finding();
    existing.setValue(value);
    existing.setInject(inject);
    existing.setField(contractOutputElement.getKey());
    existing.setType(contractOutputElement.getType());
    existing.setAssets(new ArrayList<>(List.of(asset1)));

    injectTestHelper.forceSaveInject(inject);
    injectTestHelper.forceSaveFinding(existing);

    findingService.saveAgentFinding(inject, asset1, contractOutputContext, value);

    Finding result =
        findingRepository
            .findByInjectIdAndValueAndTypeAndKey(
                inject.getId(),
                value,
                contractOutputElement.getType(),
                contractOutputElement.getKey())
            .orElseThrow();

    assertEquals(1, result.getAssets().size());
    assertTrue(
        result.getAssets().stream()
            .map(Asset::getId)
            .collect(Collectors.toSet())
            .contains(asset1.getId()));
  }

  @Test
  @DisplayName("Should return two findings for multiple finding-compatible CVE contract outputs")
  void shouldReturnFindingsForMultipleFindingCompatibleContractOutputs() throws Exception {
    ObjectMapper mapper = new ObjectMapper();
    ObjectNode convertedContent =
        (ObjectNode)
            mapper.readTree(
                """
        {
          "outputs": [
            {
              "field": "cves",
              "isFindingCompatible": true,
              "isMultiple": true,
              "labels": ["nuclei"],
              "type": "cve"
            }
          ]
        }
        """);
    ObjectNode structuredOutput =
        (ObjectNode)
            mapper.readTree(
                """
        {
          "cves": [
            { "id": "cve A", "host": "host A", "severity": "high" },
            { "id": "cve B", "host": "host B", "severity": "medium" }
          ]
        }
        """);

    List<InjectorContractContentOutputElement> contractOutputs =
        injectorContractContentUtils.getContractOutputs(convertedContent, mapper);
    ContractOutputContext ctx = ContractOutputContext.from(contractOutputs.getFirst());
    JsonNode elementNode = structuredOutput.path("cves");

    List<Finding> findings =
        findingService.buildFindings(
            elementNode,
            ctx,
            node -> node.hasNonNull("id") && node.hasNonNull("host") && node.hasNonNull("severity"),
            node -> node.get("id").asText(),
            node -> Collections.emptyList(),
            node -> Collections.emptyList(),
            node -> Collections.emptyList());

    assertNotNull(findings);
    assertEquals(2, findings.size());
    assertTrue(findings.stream().allMatch(f -> f.getType().equals(ContractOutputType.CVE)));
    Set<String> values = findings.stream().map(Finding::getValue).collect(Collectors.toSet());
    assertTrue(values.contains("cve A"));
    assertTrue(values.contains("cve B"));
  }

  @Test
  @DisplayName("Should skip malformed finding nodes in a multiple batch instead of throwing")
  void shouldSkipMalformedFindingNodesInMultipleBatch() throws Exception {
    ObjectMapper mapper = new ObjectMapper();
    ObjectNode convertedContent =
        (ObjectNode)
            mapper.readTree(
                """
        {
          "outputs": [
            {
              "field": "port_scans",
              "isFindingCompatible": true,
              "isMultiple": true,
              "labels": ["nuclei"],
              "type": "portscan"
            }
          ]
        }
        """);
    // One malformed entry (null) followed by one valid entry: the malformed one is skipped so the
    // valid one still produces a finding and the execution callback is never aborted mid-batch.
    ObjectNode structuredOutput =
        (ObjectNode)
            mapper.readTree(
                """
        {
          "port_scans": [ null, { "host": "host A", "port": 443, "service": "https" } ]
        }
        """);

    List<InjectorContractContentOutputElement> contractOutputs =
        injectorContractContentUtils.getContractOutputs(convertedContent, mapper);
    ContractOutputContext ctx = ContractOutputContext.from(contractOutputs.getFirst());
    JsonNode elementNode = structuredOutput.path("port_scans");

    List<Finding> findings =
        findingService.buildFindings(
            elementNode,
            ctx,
            node ->
                node.hasNonNull("host") && node.hasNonNull("port") && node.hasNonNull("service"),
            node -> node.get("port").asText(),
            node -> Collections.emptyList(),
            node -> Collections.emptyList(),
            node -> Collections.emptyList());

    assertNotNull(findings);
    assertEquals(1, findings.size());
    assertEquals("443", findings.getFirst().getValue());
  }

  @Test
  @DisplayName("Should throw exception when a single finding node is not correctly formatted")
  void shouldThrowExceptionWhenSingleFindingNotCorrectlyFormatted() throws Exception {
    ObjectMapper mapper = new ObjectMapper();
    ObjectNode convertedContent =
        (ObjectNode)
            mapper.readTree(
                """
        {
          "outputs": [
            {
              "field": "port_scan",
              "isFindingCompatible": true,
              "isMultiple": false,
              "labels": ["nuclei"],
              "type": "portscan"
            }
          ]
        }
        """);
    ObjectNode structuredOutput =
        (ObjectNode)
            mapper.readTree(
                """
        {
          "port_scan": { "host": "host A" }
        }
        """);

    List<InjectorContractContentOutputElement> contractOutputs =
        injectorContractContentUtils.getContractOutputs(convertedContent, mapper);
    ContractOutputContext ctx = ContractOutputContext.from(contractOutputs.getFirst());
    JsonNode elementNode = structuredOutput.path("port_scan");

    assertThrows(
        IllegalArgumentException.class,
        () ->
            findingService.buildFindings(
                elementNode,
                ctx,
                node ->
                    node.hasNonNull("host")
                        && node.hasNonNull("port")
                        && node.hasNonNull("service"),
                node -> node.get("port").asText(),
                node -> Collections.emptyList(),
                node -> Collections.emptyList(),
                node -> Collections.emptyList()));
  }

  @Nested
  @DisplayName("Tenant Isolation")
  @WithMockUser
  class TenantIsolation {

    private void switchToTenant(String tenantId) {
      entityManager.flush();
      entityManager.clear();
      TenantContext.setCurrentTenant(tenantId);
      entityManager
          .unwrap(org.hibernate.Session.class)
          .enableFilter("tenantFilter")
          .setParameter("tenantId", tenantId);
    }

    private Finding createFindingInTenant(String tenantId) {
      String previousTenant = TenantContext.getCurrentTenant();
      try {
        switchToTenant(tenantId);

        Inject inject = getDefaultInject();
        inject = injectRepository.save(inject);

        Finding finding = new Finding();
        finding.setValue("tenant-finding-" + UUID.randomUUID());
        finding.setInject(inject);
        finding.setField("finding_field");
        finding.setType(ContractOutputType.Text);
        finding.setAssets(new ArrayList<>());

        return findingRepository.save(finding);
      } finally {
        switchToTenant(previousTenant);
      }
    }

    @Test
    @DisplayName("Finding created in tenant X should NOT be readable from tenant Y")
    void given_findingInTenantX_should_notBeReadableFromTenantY() {
      // -------- Arrange --------
      String tenantX = TenantContext.getCurrentTenant();
      String tenantY = UUID.randomUUID().toString();

      Finding finding = createFindingInTenant(tenantX);
      switchToTenant(tenantY);

      // -------- Act & Assert --------
      assertThrows(EntityNotFoundException.class, () -> findingService.finding(finding.getId()));
    }

    @Test
    @DisplayName("Finding created in tenant X should be readable from tenant X")
    void given_findingInTenantX_should_beReadableFromTenantX() {
      // -------- Arrange --------
      String tenantX = TenantContext.getCurrentTenant();
      Finding finding = createFindingInTenant(tenantX);

      switchToTenant(tenantX);

      // -------- Act --------
      Finding result = findingService.finding(finding.getId());

      // -------- Assert --------
      assertEquals(finding.getId(), result.getId());
    }

    @Test
    @DisplayName("Finding list in tenant Y should NOT return findings from tenant X")
    void given_findingInTenantX_should_notAppearInTenantYList() {
      // -------- Arrange --------
      String tenantX = TenantContext.getCurrentTenant();
      String tenantY = UUID.randomUUID().toString();

      Finding findingInTenantX = createFindingInTenant(tenantX);
      switchToTenant(tenantY);

      // -------- Act --------
      List<Finding> findings = findingService.findings();
      Set<String> findingIds = findings.stream().map(Finding::getId).collect(Collectors.toSet());

      // -------- Assert --------
      assertFalse(findingIds.contains(findingInTenantX.getId()));
      assertTrue(findingIds.isEmpty());
    }

    @Test
    @DisplayName("Finding created in tenant X should NOT be deletable from tenant Y")
    void given_findingInTenantX_should_notBeDeletableFromTenantY() {
      // -------- Arrange --------
      String tenantX = TenantContext.getCurrentTenant();
      String tenantY = UUID.randomUUID().toString();

      Finding finding = createFindingInTenant(tenantX);
      switchToTenant(tenantY);

      // -------- Act & Assert --------
      assertThrows(
          EntityNotFoundException.class, () -> findingService.deleteFinding(finding.getId()));

      // Ensure record is still visible from owner tenant
      switchToTenant(tenantX);
      assertDoesNotThrow(() -> findingService.finding(finding.getId()));
    }
  }

  /**
   * Reproduces (does NOT fix) a static-analysis hypothesis: the agent path ({@link
   * FindingWriter#saveCompleteFinding}) upserts via a native {@code ON CONFLICT ... DO UPDATE}, so
   * a re-detected finding (same natural key: inject/value/type/field) is silently updated. The
   * injector-bulk path ({@link FindingService#createFindings}) instead calls {@code
   * findingRepository.saveAll(...)} on brand-new {@link Finding} entities (id == null) with no
   * pre-fetch of an existing row sharing the same natural key - {@code
   * deduplicateFindings} only dedups WITHIN one in-memory batch, never against what is already
   * persisted. The {@code unique_finding_constraint} added by {@code
   * V3_81__Add_Unique_constraint_findings} therefore may or may not be hit at runtime, depending
   * on whether the second occurrence lands in a persistence context that still has the first
   * occurrence's managed instance (same-batch dirty-checking would hide the issue) or a genuinely
   * separate one (true second run).
   *
   * <p>Each "run" below is executed via a raw {@link TransactionTemplate} configured with {@code
   * PROPAGATION_REQUIRES_NEW} (see {@link #runInNewTransaction}) so it commits independently, on
   * its own connection, with a Hibernate persistence context that starts out empty - mirroring two
   * genuinely separate injector executions of the same inject rather than two calls sharing one
   * in-memory transaction/session (where Hibernate's first-level cache could paper over the bug).
   */
  @Nested
  @DisplayName("Injector-bulk recurrence: same natural key across two separate executions")
  class RecurrenceAcrossSeparateExecutions {

    @Test
    @DisplayName(
        "createFindings() called twice for the same natural key in two separate transactions")
    void given_sameNaturalKeyInTwoSeparateExecutions_should_notThrowAndUpdateInPlace() {
      // -------- Arrange --------
      String uniqueValue = "test-recurrence-check-" + UUID.randomUUID();
      String field = "finding_field";
      ContractOutputType type = ContractOutputType.Text;

      String injectId =
          runInNewTransaction(() -> injectRepository.save(getDefaultInject()).getId());

      // -------- Act: "run #1" - first injector execution creates the finding --------
      runInNewTransaction(
          () -> {
            findingService.createFindings(List.of(bareFinding(uniqueValue, type, field)), injectId);
            return null;
          });

      Optional<Finding> afterFirstRun =
          findingRepository.findByInjectIdAndValueAndTypeAndKey(injectId, uniqueValue, type, field);
      assertTrue(afterFirstRun.isPresent(), "Finding should exist after the first run");
      Instant updatedAtAfterFirstRun = afterFirstRun.get().getUpdateDate();

      // -------- Act: "run #2" - second, later injector execution re-detects the SAME finding,
      // in a brand-new transaction/persistence context (a new Finding instance, id == null, just
      // like FindingUtils.createFinding() would build from scratch on a real second run) --------
      Exception thrownOnSecondRun = null;
      try {
        runInNewTransaction(
            () -> {
              findingService.createFindings(
                  List.of(bareFinding(uniqueValue, type, field)), injectId);
              return null;
            });
      } catch (Exception e) {
        thrownOnSecondRun = e;
      }

      // -------- Assert --------
      if (thrownOnSecondRun != null) {
        // BUG CONFIRMED at runtime: do NOT silence this - fail loudly with the full cause chain
        // so it surfaces in CI/review, instead of asserting "yes it throws" as if that were the
        // expected/desired behavior.
        throw new AssertionError(
            "BUG CONFIRMED: FindingService.createFindings() threw when the same natural key "
                + "(inject_id="
                + injectId
                + ", value="
                + uniqueValue
                + ", type="
                + type
                + ", field="
                + field
                + ") recurred across two separate executions/persistence contexts. The"
                + " injector-bulk path has no upsert/pre-fetch, unlike"
                + " FindingWriter#saveCompleteFinding's native ON CONFLICT DO UPDATE on the agent"
                + " path.",
            thrownOnSecondRun);
      }

      // No exception: verify explicitly whether the unique constraint quietly let a duplicate
      // row through, or the finding was genuinely updated in place as expected.
      List<Finding> allMatches = findingRepository.findAllByInjectId(injectId);
      assertEquals(
          1,
          allMatches.size(),
          "Expected exactly one Finding row for this natural key after both runs, found "
              + allMatches.size()
              + " - the unique constraint is not preventing duplicates as expected.");

      Finding afterSecondRun = allMatches.getFirst();
      assertEquals(uniqueValue, afterSecondRun.getValue());
      assertTrue(
          afterSecondRun.getUpdateDate().isAfter(updatedAtAfterFirstRun)
              || afterSecondRun.getUpdateDate().equals(updatedAtAfterFirstRun),
          "finding_updated_at should not have gone backwards after the second run");
    }

    private Finding bareFinding(String value, ContractOutputType type, String field) {
      Finding finding = new Finding();
      finding.setValue(value);
      finding.setType(type);
      finding.setField(field);
      finding.setAssets(new ArrayList<>());
      return finding;
    }
  }

  /**
   * Runs {@code action} in a brand-new, independently-committed transaction (PROPAGATION_REQUIRES_NEW)
   * on the current thread, suspending whatever ambient (test-rollback) transaction is active. Used
   * to simulate two genuinely separate injector executions rather than two calls sharing the same
   * Hibernate persistence context.
   */
  private <T> T runInNewTransaction(java.util.concurrent.Callable<T> action) {
    TransactionTemplate template = new TransactionTemplate(transactionManager);
    template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    return template.execute(
        status -> {
          try {
            return action.call();
          } catch (RuntimeException e) {
            throw e;
          } catch (Exception e) {
            throw new RuntimeException(e);
          }
        });
  }
}
