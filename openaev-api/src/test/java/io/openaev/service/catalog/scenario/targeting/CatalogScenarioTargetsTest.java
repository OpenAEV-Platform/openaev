package io.openaev.service.catalog.scenario.targeting;

import static io.openaev.database.model.InjectorContract.CONTRACT_ELEMENT_CONTENT_KEY_ASSETS;
import static io.openaev.database.model.InjectorContract.CONTRACT_ELEMENT_CONTENT_KEY_ASSET_GROUPS;
import static io.openaev.database.model.InjectorContract.CONTRACT_ELEMENT_CONTENT_KEY_TEAMS;
import static io.openaev.utils.InjectContentUtils.MANUAL_TARGETS_CONTENT_KEY;
import static io.openaev.utils.InjectContentUtils.TARGET_SELECTOR_CONTENT_KEY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.lenient;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.openaev.database.model.AssetGroup;
import io.openaev.database.model.Endpoint;
import io.openaev.database.model.Inject;
import io.openaev.database.model.InjectorContract;
import io.openaev.database.model.Team;
import io.openaev.service.AssetGroupService;
import io.openaev.service.AssetService;
import io.openaev.service.TeamService;
import io.openaev.utils.injector_contract.InjectorContractContentUtils;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("Targets chosen at a catalogue scenario install")
class CatalogScenarioTargetsTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final List<String> ALL = List.of("chosen");

  @Mock private AssetService assetService;
  @Mock private AssetGroupService assetGroupService;
  @Mock private TeamService teamService;

  private CatalogScenarioTargets targets;
  private Endpoint asset;
  private AssetGroup assetGroup;
  private Team team;

  @BeforeEach
  void setUp() {
    targets =
        new CatalogScenarioTargets(
            new InjectorContractContentUtils(MAPPER), assetService, assetGroupService, teamService);
    asset = new Endpoint();
    asset.setId("asset");
    assetGroup = new AssetGroup();
    assetGroup.setId("asset-group");
    team = new Team();
    team.setId("team");
    lenient().when(assetService.assets(anyList())).thenReturn(List.of(asset));
    lenient().when(assetGroupService.assetGroups(anyList())).thenReturn(List.of(assetGroup));
    lenient().when(teamService.getTeamsByIds(anyList())).thenReturn(List.of(team));
  }

  private static Inject injectWithFields(String... fields) {
    InjectorContract contract = new InjectorContract();
    contract.setContent(
        Arrays.stream(fields)
            .map(field -> "{\"key\":\"" + field + "\"}")
            .collect(Collectors.joining(",", "{\"fields\":[", "]}")));
    Inject inject = new Inject();
    inject.setInjectorContract(contract);
    return inject;
  }

  private static Inject selectorInject(String selected, String hosts) {
    Inject inject = injectWithFields(TARGET_SELECTOR_CONTENT_KEY, MANUAL_TARGETS_CONTENT_KEY);
    ObjectNode content = MAPPER.createObjectNode();
    content.put(TARGET_SELECTOR_CONTENT_KEY, selected);
    content.put(MANUAL_TARGETS_CONTENT_KEY, hosts);
    inject.setContent(content);
    return inject;
  }

  @Nested
  @DisplayName("Contract fields")
  class Fields {

    @Test
    @DisplayName("each inject gets the chosen targets of the fields its contract has")
    void given_everyKindChosen_should_addOnlyTheKindsTheContractTakes() {
      // Arrange
      Inject implant =
          injectWithFields(
              CONTRACT_ELEMENT_CONTENT_KEY_ASSETS, CONTRACT_ELEMENT_CONTENT_KEY_ASSET_GROUPS);
      Inject mail = injectWithFields(CONTRACT_ELEMENT_CONTENT_KEY_TEAMS);
      mail.setAllTeams(true);

      // Act
      targets.addTo(List.of(implant, mail), new TargetChoice(ALL, ALL, ALL, List.of()));

      // Assert
      assertThat(implant.getAssets()).containsExactly(asset);
      assertThat(implant.getAssetGroups()).containsExactly(assetGroup);
      assertThat(implant.getTeams()).isEmpty();
      assertThat(mail.getTeams()).containsExactly(team);
      assertThat(mail.isAllTeams()).isFalse();
    }
  }

  @Nested
  @DisplayName("Target selector")
  class Selector {

    @Test
    @DisplayName("an inject selecting a kind not chosen switches to the first kind chosen")
    void given_selectedKindNotChosen_should_selectTheFirstChosenKind() {
      // Arrange
      Inject scan = selectorInject("manual", "");

      // Act
      targets.addTo(List.of(scan), new TargetChoice(ALL, List.of(), List.of(), List.of()));

      // Assert
      assertThat(scan.getContent().path(TARGET_SELECTOR_CONTENT_KEY).asText()).isEqualTo("assets");
      assertThat(scan.getAssets()).containsExactly(asset);
    }

    @Test
    @DisplayName("an inject selecting hand-typed hosts keeps them and adds the chosen ones")
    void given_hostsSelectedAndChosen_should_keepTheSelectorAndAddTheHosts() {
      // Arrange
      Inject scan = selectorInject("manual", "10.0.0.1");

      // Act
      targets.addTo(
          List.of(scan),
          new TargetChoice(ALL, List.of(), List.of(), List.of(" 10.0.0.2 ", "10.0.0.1")));

      // Assert
      assertThat(scan.getContent().path(TARGET_SELECTOR_CONTENT_KEY).asText()).isEqualTo("manual");
      assertThat(scan.getContent().path(MANUAL_TARGETS_CONTENT_KEY).asText())
          .isEqualTo("10.0.0.1,10.0.0.2");
      assertThat(scan.getAssets()).isEmpty();
    }
  }
}
