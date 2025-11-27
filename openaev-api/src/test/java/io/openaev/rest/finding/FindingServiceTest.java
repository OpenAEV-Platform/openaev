package io.openaev.rest.finding;

import static io.openaev.utils.fixtures.AssetFixture.createDefaultAsset;
import static io.openaev.utils.fixtures.InjectFixture.getDefaultInject;
import static io.openaev.utils.fixtures.OutputParserFixture.getDefaultContractOutputElement;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.openaev.IntegrationTest;
import io.openaev.database.model.Asset;
import io.openaev.database.model.ContractOutputElement;
import io.openaev.database.model.Finding;
import io.openaev.database.model.Inject;
import io.openaev.database.repository.FindingRepository;
import io.openaev.utils.helpers.InjectTestHelper;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

@ExtendWith(MockitoExtension.class)
class FindingServiceTest extends IntegrationTest {

  public static final String ASSET_1 = "asset1";
  public static final String ASSET_2 = "asset2";

  @Autowired private InjectTestHelper injectTestHelper;
  @Autowired private FindingService findingService;
  @Autowired private FindingRepository findingRepository;

  @Test
  @DisplayName("Should have two assets for a finding")
  @Transactional
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
  @Transactional
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
}
