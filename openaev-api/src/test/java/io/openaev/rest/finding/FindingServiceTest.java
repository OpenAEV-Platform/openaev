package io.openaev.rest.finding;

import static io.openaev.utils.fixtures.AssetFixture.createDefaultAsset;
import static io.openaev.utils.fixtures.InjectFixture.getDefaultInject;
import static io.openaev.utils.fixtures.OutputParserFixture.getDefaultContractOutputElement;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

import io.openaev.IntegrationTest;
import io.openaev.database.model.Asset;
import io.openaev.database.model.ContractOutputElement;
import io.openaev.database.model.Finding;
import io.openaev.database.model.Inject;
import io.openaev.database.repository.AssetRepository;
import io.openaev.database.repository.FindingRepository;
import io.openaev.database.repository.TeamRepository;
import io.openaev.database.repository.UserRepository;
import io.openaev.rest.inject.service.InjectService;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FindingServiceTest extends IntegrationTest {

  public static final String ASSET_1 = "asset1";
  public static final String ASSET_2 = "asset2";

  @Mock private InjectService injectService;
  @Mock private FindingRepository findingRepository;
  @Mock private AssetRepository assetRepository;
  @Mock private TeamRepository teamRepository;
  @Mock private UserRepository userRepository;
  @InjectMocks private FindingService findingService;

  @BeforeEach
  void setUp() {
    findingService =
        new FindingService(
            injectService, findingRepository, assetRepository, teamRepository, userRepository);
  }

  @Test
  @DisplayName("Should have two assets for a finding")
  void given_a_finding_already_existent_with_one_asset_should_have_two_assets() {
    Inject inject = getDefaultInject();
    Asset asset1 = createDefaultAsset(ASSET_1);
    asset1.setId(ASSET_1);
    Asset asset2 = createDefaultAsset(ASSET_2);
    asset2.setId(ASSET_2);
    String value = "value-already-existent";
    ContractOutputElement contractOutputElement = getDefaultContractOutputElement();

    Finding finding1 = new Finding();
    finding1.setValue(value);
    finding1.setInject(inject);
    finding1.setField(contractOutputElement.getKey());
    finding1.setType(contractOutputElement.getType());
    finding1.setAssets(new ArrayList<>(Arrays.asList(asset1)));

    when(findingRepository.findByInjectIdAndValueAndTypeAndKey(
            inject.getId(), value, contractOutputElement.getType(), contractOutputElement.getKey()))
        .thenReturn(Optional.of(finding1));

    findingService.buildFinding(inject, asset2, contractOutputElement, value);

    ArgumentCaptor<Finding> findingCaptor = ArgumentCaptor.forClass(Finding.class);
    verify(findingRepository).save(findingCaptor.capture());
    Finding capturedFinding = findingCaptor.getValue();

    assertEquals(2, capturedFinding.getAssets().size());
    Set<String> assetIds =
        capturedFinding.getAssets().stream().map(Asset::getId).collect(Collectors.toSet());
    assertTrue(assetIds.contains(ASSET_1));
    assertTrue(assetIds.contains(ASSET_2));
  }

  @Test
  @DisplayName("Should have one asset for a finding")
  void given_a_finding_already_existent_with_same_asset_should_have_one_assets() {
    Inject inject = getDefaultInject();
    Asset asset1 = createDefaultAsset(ASSET_1);
    asset1.setId(ASSET_1);
    String value = "value-already-existent";
    ContractOutputElement contractOutputElement = getDefaultContractOutputElement();

    Finding finding1 = new Finding();
    finding1.setValue(value);
    finding1.setInject(inject);
    finding1.setField(contractOutputElement.getKey());
    finding1.setType(contractOutputElement.getType());
    finding1.setAssets(new ArrayList<>(Arrays.asList(asset1)));

    when(findingRepository.findByInjectIdAndValueAndTypeAndKey(
            inject.getId(), value, contractOutputElement.getType(), contractOutputElement.getKey()))
        .thenReturn(Optional.of(finding1));

    findingService.buildFinding(inject, asset1, contractOutputElement, value);

    verify(findingRepository, never()).save(any());
  }
}
