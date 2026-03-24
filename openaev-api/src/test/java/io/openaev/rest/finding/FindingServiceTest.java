package io.openaev.rest.finding;

import static io.openaev.utils.fixtures.AssetFixture.createDefaultAsset;
import static io.openaev.utils.fixtures.InjectFixture.getDefaultInject;
import static io.openaev.utils.fixtures.OutputParserFixture.getContractOutputElementTypeIPv6;
import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.openaev.IntegrationTest;
import io.openaev.database.model.*;
import io.openaev.database.repository.FindingRepository;
import io.openaev.injector_contract.outputs.InjectorContractContentOutputElement;
import io.openaev.rest.inject.service.ContractOutputContext;
import io.openaev.rest.injector_contract.InjectorContractContentUtils;
import io.openaev.utils.helpers.InjectTestHelper;
import java.util.*;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class FindingServiceTest extends IntegrationTest {

  public static final String ASSET_1 = "asset1";
  public static final String ASSET_2 = "asset2";

  @Autowired private InjectTestHelper injectTestHelper;
  @Autowired private FindingService findingService;
  @Autowired private FindingRepository findingRepository;
  @Autowired private InjectorContractContentUtils injectorContractContentUtils;

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
  @DisplayName("Should throw exception when finding node is not correctly formatted")
  void shouldThrowExceptionWhenFindingNotCorrectlyFormatted() throws Exception {
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
    ObjectNode structuredOutput =
        (ObjectNode)
            mapper.readTree(
                """
        {
          "port_scans": [ null ]
        }
        """);

    List<InjectorContractContentOutputElement> contractOutputs =
        injectorContractContentUtils.getContractOutputs(convertedContent, mapper);
    ContractOutputContext ctx = ContractOutputContext.from(contractOutputs.getFirst());
    JsonNode elementNode = structuredOutput.path("port_scans");

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
}
