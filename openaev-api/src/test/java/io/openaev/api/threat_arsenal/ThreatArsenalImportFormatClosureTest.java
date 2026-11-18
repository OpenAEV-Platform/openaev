package io.openaev.api.threat_arsenal;

import static io.openaev.api.threat_arsenal.ThreatArsenalApi.TENANT_THREAT_ARSENAL_URL;
import static io.openaev.service.threat_arsenal.ThreatArsenalImportService.EXPORT_FORMAT_TYPES;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import io.openaev.IntegrationTest;
import io.openaev.context.TenantContext;
import io.openaev.database.model.AttackPattern;
import io.openaev.database.model.Cwe;
import io.openaev.database.model.InjectorContract;
import io.openaev.database.model.KillChainPhase;
import io.openaev.database.model.Payload;
import io.openaev.database.model.Tenant;
import io.openaev.database.model.Vulnerability;
import io.openaev.database.repository.CweRepository;
import io.openaev.database.repository.KillChainPhaseRepository;
import io.openaev.integration.impl.injectors.openaev.OpenaevInjectorIntegrationFactory;
import io.openaev.utils.fixtures.BuiltinConnectorRegistration;
import io.openaev.utils.fixtures.DetectionRemediationFixture;
import io.openaev.utils.fixtures.DocumentFixture;
import io.openaev.utils.fixtures.DomainFixture;
import io.openaev.utils.fixtures.InjectorContractFixture;
import io.openaev.utils.fixtures.InjectorFixture;
import io.openaev.utils.fixtures.KillChainPhaseFixture;
import io.openaev.utils.fixtures.OutputParserFixture;
import io.openaev.utils.fixtures.PayloadFixture;
import io.openaev.utils.fixtures.SecurityPlatformFixture;
import io.openaev.utils.fixtures.TagFixture;
import io.openaev.utils.fixtures.VulnerabilityFixture;
import io.openaev.utils.fixtures.composers.AttackPatternComposer;
import io.openaev.utils.fixtures.composers.DetectionRemediationComposer;
import io.openaev.utils.fixtures.composers.DocumentComposer;
import io.openaev.utils.fixtures.composers.DomainComposer;
import io.openaev.utils.fixtures.composers.InjectorContractComposer;
import io.openaev.utils.fixtures.composers.PayloadComposer;
import io.openaev.utils.fixtures.composers.SecurityPlatformComposer;
import io.openaev.utils.fixtures.composers.TagComposer;
import io.openaev.utils.fixtures.composers.VulnerabilityComposer;
import io.openaev.utils.fixtures.files.AttackPatternFixture;
import io.openaev.utils.mockUser.WithMockUser;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * Closure of the threat-arsenal export format: a fully populated action is exported, every type its
 * export carries is a type the import rebuilds, and importing it keeps every entity of the export.
 * It fails when a relation added to the export format is not in the import's type set.
 */
@TestInstance(PER_CLASS)
@Transactional
@WithMockUser(isAdmin = true)
@DisplayName("Threat arsenal import - export format closure")
class ThreatArsenalImportFormatClosureTest extends IntegrationTest {

  // Types owned by the imported payload: each import creates its own rows.
  private static final Map<String, String> OWNED_TABLES =
      Map.of(
          "detection_remediations", "detection_remediations",
          "output_parsers", "output_parsers",
          "contract_output_elements", "contract_output_elements",
          "regex_groups", "regex_groups");

  @Autowired private BuiltinConnectorRegistration builtinConnectorRegistration;
  @Autowired private OpenaevInjectorIntegrationFactory openaevInjectorIntegrationFactory;
  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private DomainComposer domainComposer;
  @Autowired private InjectorContractComposer injectorContractComposer;
  @Autowired private PayloadComposer payloadComposer;
  @Autowired private DetectionRemediationComposer detectionRemediationComposer;
  @Autowired private SecurityPlatformComposer securityPlatformComposer;
  @Autowired private TagComposer tagComposer;
  @Autowired private AttackPatternComposer attackPatternComposer;
  @Autowired private VulnerabilityComposer vulnerabilityComposer;
  @Autowired private DocumentComposer documentComposer;
  @Autowired private InjectorFixture injectorFixture;
  @Autowired private CweRepository cweRepository;
  @Autowired private KillChainPhaseRepository killChainPhaseRepository;

  @BeforeEach
  void beforeEach() {
    builtinConnectorRegistration.register(
        openaevInjectorIntegrationFactory, TenantContext.getCurrentTenant());
    domainComposer.reset();
    injectorContractComposer.reset();
    if (testUserHolder.isSet()) {
      tenantRepository.addUserToTenant(testUserHolder.get().getId(), Tenant.DEFAULT_TENANT_UUID);
    }
  }

  /**
   * @param withUnkeyedTypes also attach the entities that have no business key (security platform,
   *     vulnerability, cwe, document): exporting them is covered, but a same-database re-import
   *     cannot rebuild them under their exported ids.
   */
  private InjectorContract persistContract(boolean withUnkeyedTypes) {
    String tenantId = TenantContext.getCurrentTenant();
    KillChainPhase phase =
        killChainPhaseRepository.save(
            KillChainPhaseFixture.getKillChainPhase(
                "closure-phase-" + UUID.randomUUID(), 1L, tenantId));
    AttackPattern attackPattern =
        AttackPatternFixture.createAttackPatternsWithExternalId(
            "T" + UUID.randomUUID().toString().substring(0, 6));
    attackPattern.setKillChainPhases(new ArrayList<>(List.of(phase)));

    InjectorContractComposer.Composer contractComposer =
        injectorContractComposer
            .forInjectorContract(InjectorContractFixture.createDefaultInjectorContract())
            .withInjector(injectorFixture.getWellKnownOaevImplantInjector())
            .withDomain(domainComposer.forDomain(DomainFixture.getRandomDomain()))
            .withTag(
                tagComposer.forTag(TagFixture.getTagWithText("closure-tag-" + UUID.randomUUID())))
            .withAttackPattern(attackPatternComposer.forAttackPattern(attackPattern));

    Payload payload;
    PayloadComposer.Composer payloadWrapper;
    if (withUnkeyedTypes) {
      Cwe cwe = new Cwe();
      cwe.setExternalId("CWE-" + UUID.randomUUID().toString().substring(0, 6));
      cwe.setSource("test");
      cwe.setTenant(new Tenant(tenantId));
      cwe = cweRepository.save(cwe);
      Vulnerability vulnerability =
          VulnerabilityFixture.createVulnerabilityInput(
              VulnerabilityFixture.getRandomExternalVulnerabilityId());
      vulnerability.setCwes(new ArrayList<>(List.of(cwe)));
      contractComposer.withVulnerability(vulnerabilityComposer.forVulnerability(vulnerability));
      payload = PayloadFixture.createDefaultExecutable();
      payloadWrapper =
          payloadComposer
              .forPayload(payload)
              .withExecutable(
                  documentComposer
                      .forDocument(DocumentFixture.getDocumentJpeg())
                      .withTag(
                          tagComposer.forTag(
                              TagFixture.getTagWithText("closure-doc-tag-" + UUID.randomUUID()))))
              .withDetectionRemediation(
                  detectionRemediationComposer
                      .forDetectionRemediation(
                          DetectionRemediationFixture.createDefaultDetectionRemediation())
                      .withSecurityPlatform(
                          securityPlatformComposer.forSecurityPlatform(
                              SecurityPlatformFixture.createDefault(
                                  "closure-sp-" + UUID.randomUUID(), "EDR"))));
    } else {
      payload = PayloadFixture.createDefaultCommand();
      payloadWrapper = payloadComposer.forPayload(payload);
    }
    payload.setOutputParsers(Set.of(OutputParserFixture.getDefaultOutputParser()));
    return contractComposer.withPayload(payloadWrapper).persist().get();
  }

  private byte[] export(String contractId) throws Exception {
    return mockMvc
        .perform(
            get(tenantUri(TENANT_THREAT_ARSENAL_URL + "/" + contractId + "/export")).with(csrf()))
        .andExpect(status().is2xxSuccessful())
        .andReturn()
        .getResponse()
        .getContentAsByteArray();
  }

  /** Number of resources per type in the root + included of an export. */
  private Map<String, Integer> typeCounts(byte[] zip) throws Exception {
    Map<String, Integer> counts = new TreeMap<>();
    try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zip))) {
      ZipEntry entry;
      while ((entry = zis.getNextEntry()) != null) {
        if (entry.getName().endsWith(".json") && !"meta.json".equals(entry.getName())) {
          JsonNode doc = objectMapper.readTree(zis.readAllBytes());
          counts.merge(doc.at("/data/type").asText(), 1, Integer::sum);
          for (JsonNode included : doc.path("included")) {
            counts.merge(included.path("type").asText(), 1, Integer::sum);
          }
        }
        zis.closeEntry();
      }
    }
    return counts;
  }

  private long count(String table) {
    entityManager.flush();
    entityManager.clear();
    return ((Number)
            entityManager.createNativeQuery("select count(*) from " + table).getSingleResult())
        .longValue();
  }

  @Test
  @DisplayName("Every type a fully populated export carries is a type the import rebuilds")
  void given_fullyPopulatedExport_should_onlyCarryTypesTheImportRebuilds() throws Exception {
    // Arrange
    InjectorContract contract = persistContract(true);
    entityManager.flush();
    entityManager.clear();

    // Act
    Map<String, Integer> exportedCounts = typeCounts(export(contract.getId()));

    // Assert: the export format is inside the import's type set...
    Set<String> notRebuilt = new TreeSet<>(exportedCounts.keySet());
    notRebuilt.removeAll(EXPORT_FORMAT_TYPES);
    assertTrue(notRebuilt.isEmpty(), "export types the import does not rebuild: " + notRebuilt);
    // ...and the fixture really exercises the whole format
    Set<String> notExported = new TreeSet<>(EXPORT_FORMAT_TYPES);
    notExported.removeAll(exportedCounts.keySet());
    assertEquals(
        Set.of(
            "payloads", "command", "file_drop", "dns_resolution", "network_traffic", "ai_attack"),
        notExported,
        "types of the format this fixture does not export (the base payload type and the other payload subtypes)");
  }

  @Test
  @DisplayName("Importing an export keeps every entity of the export")
  void given_export_should_keepEveryEntityOnImport() throws Exception {
    // Arrange
    InjectorContract contract = persistContract(false);
    entityManager.flush();
    entityManager.clear();
    byte[] exported = export(contract.getId());
    Map<String, Integer> exportedCounts = typeCounts(exported);
    Map<String, Long> before = new TreeMap<>();
    OWNED_TABLES.forEach((type, table) -> before.put(table, count(table)));

    // Act
    String importResponse =
        mockMvc
            .perform(
                multipart(tenantUri(TENANT_THREAT_ARSENAL_URL + "/import"))
                    .file(new MockMultipartFile("file", "action.zip", "application/zip", exported))
                    .with(csrf()))
            .andExpect(status().is2xxSuccessful())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String importedId = JsonPath.read(importResponse, "$.injector_contract_id");

    // Assert: the rows owned by the payload are all created again...
    OWNED_TABLES.forEach(
        (type, table) ->
            assertEquals(
                before.get(table) + exportedCounts.getOrDefault(type, 0),
                count(table),
                "rows of " + table + " after the import"));
    // ...and the imported action exports the same entities as the original one
    assertEquals(exportedCounts, typeCounts(export(importedId)));
  }
}
