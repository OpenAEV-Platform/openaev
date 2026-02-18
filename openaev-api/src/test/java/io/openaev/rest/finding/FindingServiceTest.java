package io.openaev.rest.finding;

import static io.openaev.utils.fixtures.AssetFixture.createDefaultAsset;
import static io.openaev.utils.fixtures.InjectFixture.getDefaultInject;
import static io.openaev.utils.fixtures.OutputParserFixture.getDefaultContractOutputElement;
import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.openaev.IntegrationTest;
import io.openaev.database.model.*;
import io.openaev.database.repository.FindingRepository;
import io.openaev.injector_contract.outputs.InjectorContractContentOutputElement;
import io.openaev.rest.injector_contract.InjectorContractContentUtils;
import io.openaev.utils.helpers.InjectTestHelper;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
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
  @DisplayName("Should have two assets for a finding")
  void given_a_finding_already_existent_with_one_asset_should_have_two_assets() {
    Inject inject = getDefaultInject();
    Asset asset1 = createDefaultAsset(ASSET_1);
    asset1 = injectTestHelper.forceSaveAsset(asset1);
    Asset asset2 = createDefaultAsset(ASSET_2);
    asset2 = injectTestHelper.forceSaveAsset(asset2);
    String value = "value-already-existent";
    ContractOutputElement contractOutputElement = getDefaultContractOutputElement();

    Finding finding1 = new Finding();
    finding1.setValue(value);
    finding1.setInject(inject);
    finding1.setField(contractOutputElement.getKey());
    finding1.setType(contractOutputElement.getType());
    finding1.setAssets(new ArrayList<>(Arrays.asList(asset1)));

    injectTestHelper.forceSaveInject(inject);
    injectTestHelper.forceSaveFinding(finding1);

    findingService.buildFinding(inject, asset2, contractOutputElement, value);

    Finding capturedFinding =
        findingRepository
            .findByInjectIdAndValueAndTypeAndKey(
                finding1.getInject().getId(),
                finding1.getValue(),
                finding1.getType(),
                finding1.getField())
            .orElseThrow();

    assertEquals(2, capturedFinding.getAssets().size());
    Set<String> assetIds =
        capturedFinding.getAssets().stream().map(Asset::getId).collect(Collectors.toSet());
    assertTrue(assetIds.contains(asset1.getId()));
    assertTrue(assetIds.contains(asset2.getId()));
  }

  @Test
  @DisplayName("Should have one asset for a finding")
  void given_a_finding_already_existent_with_same_asset_should_have_one_assets() {
    Inject inject = getDefaultInject();
    Asset asset1 = createDefaultAsset(ASSET_1);
    asset1 = injectTestHelper.forceSaveAsset(asset1);
    String value = "value-already-existent";
    ContractOutputElement contractOutputElement = getDefaultContractOutputElement();

    Finding finding1 = new Finding();
    finding1.setValue(value);
    finding1.setInject(inject);
    finding1.setField(contractOutputElement.getKey());
    finding1.setType(contractOutputElement.getType());
    finding1.setAssets(new ArrayList<>(Arrays.asList(asset1)));

    injectTestHelper.forceSaveInject(inject);
    injectTestHelper.forceSaveFinding(finding1);

    findingService.buildFinding(inject, asset1, contractOutputElement, value);

    Finding capturedFinding =
        findingRepository
            .findByInjectIdAndValueAndTypeAndKey(
                finding1.getInject().getId(),
                finding1.getValue(),
                finding1.getType(),
                finding1.getField())
            .orElseThrow();

    assertEquals(1, capturedFinding.getAssets().size());
    Set<String> assetIds =
        capturedFinding.getAssets().stream().map(Asset::getId).collect(Collectors.toSet());
    assertTrue(assetIds.contains(asset1.getId()));
  }

  @Test
  @DisplayName("Should return empty findings when contract output is not finding compatible")
  void shouldReturnEmptyFindingsWhenContractOutputIsNotFindingCompatible() throws Exception {

    ObjectMapper mapper = new ObjectMapper();

    // Simulate a contract with a non-finding-compatible output
    String contractJson =
        """
            {
              "outputs": [
                {
                  "field": "found_assets",
                  "isFindingCompatible": false,
                  "isMultiple": true,
                  "labels": ["shodan"],
                  "type": "asset"
                }
              ]
            }
            """;

    ObjectNode convertedContent = (ObjectNode) mapper.readTree(contractJson);

    // Simulate structured output
    ObjectNode structuredOutput =
        (ObjectNode)
            mapper.readTree(
                """
                    {
                      "found_assets": [
                        { "name": "Asset A" },
                        { "name": "Asset B" }
                      ]
                    }
                    """);

    // Convert JSON outputs to InjectorContractContentOutputElement
    List<InjectorContractContentOutputElement> contractOutputs =
        injectorContractContentUtils.getContractOutputs(convertedContent, mapper);

    // Call the method to check behavior when isFindingCompatible=false
    List<Finding> findings =
        findingService.getFindingsFromInjectorContract(contractOutputs, structuredOutput);

    // Assert that findings is empty because isFindingCompatible=false
    assertNotNull(findings);
    assertTrue(findings.isEmpty());
  }

  @Test
  @DisplayName("should return findings for multiple finding-compatible contract outputs")
  void shouldReturnFindingsForMultipleFindingCompatibleContractOutputs() throws Exception {
    ObjectMapper mapper = new ObjectMapper();
    String contractJson =
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
            """;
    ObjectNode convertedContent = (ObjectNode) mapper.readTree(contractJson);
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
    List<Finding> findings =
        findingService.getFindingsFromInjectorContract(contractOutputs, structuredOutput);
    assertNotNull(findings);
    assertEquals(2, findings.size());
    assertTrue(findings.stream().allMatch(f -> f.getType().equals(ContractOutputType.CVE)));
  }

  @Test
  @DisplayName("should throw exception when finding is not correctly formatted")
  void shouldThrowExceptionWhenFindingNotCorrectlyFormatted() throws Exception {
    ObjectMapper mapper = new ObjectMapper();
    String contractJson =
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
            """;
    ObjectNode convertedContent = (ObjectNode) mapper.readTree(contractJson);
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
    assertThrows(
        IllegalArgumentException.class,
        () -> findingService.getFindingsFromInjectorContract(contractOutputs, structuredOutput));
  }
}
