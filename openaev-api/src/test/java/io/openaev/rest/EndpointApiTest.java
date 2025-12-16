package io.openaev.rest;

import static io.openaev.rest.asset.endpoint.EndpointApi.ENDPOINT_URI;
import static io.openaev.utils.JsonTestUtils.asJsonString;
import static io.openaev.utils.fixtures.AgentFixture.createAgent;
import static io.openaev.utils.fixtures.AssetGroupFixture.createAssetGroupWithAssets;
import static io.openaev.utils.fixtures.AssetGroupFixture.createDefaultAssetGroup;
import static io.openaev.utils.fixtures.EndpointFixture.*;
import static io.openaev.utils.fixtures.InjectFixture.getDefaultInject;
import static io.openaev.utils.fixtures.TagFixture.getTag;
import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.openaev.IntegrationTest;
import io.openaev.database.model.*;
import io.openaev.database.model.Tag;
import io.openaev.database.repository.AssetGroupRepository;
import io.openaev.database.repository.EndpointRepository;
import io.openaev.database.repository.InjectRepository;
import io.openaev.database.repository.TagRepository;
import io.openaev.rest.asset.endpoint.form.EndpointInput;
import io.openaev.rest.asset.endpoint.form.EndpointRegisterInput;
import io.openaev.rest.exercise.service.ExerciseService;
import io.openaev.service.EndpointService;
import io.openaev.utils.fixtures.EndpointFixture;
import io.openaev.utils.fixtures.ExecutorFixture;
import io.openaev.utils.fixtures.ExerciseFixture;
import io.openaev.utils.fixtures.PaginationFixture;
import io.openaev.utils.fixtures.composers.ExecutorComposer;
import io.openaev.utils.mapper.EndpointMapper;
import io.openaev.utils.mockUser.WithMockUser;
import io.openaev.utils.pagination.SearchPaginationInput;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.json.JSONArray;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@TestInstance(PER_CLASS)
@Transactional
class EndpointApiTest extends IntegrationTest {

  @Autowired private MockMvc mvc;
  @Autowired private TagRepository tagRepository;
  @Autowired private EndpointRepository endpointRepository;
  @Autowired private InjectRepository injectRepository;
  @Autowired private ExerciseService exerciseService;
  @Autowired private ExecutorComposer executorComposer;
  @Autowired private ExecutorFixture executorFixture;

  @SpyBean private EndpointService endpointService;
  @Autowired private AssetGroupRepository assetGroupRepository;

  @BeforeEach
  public void setup() {
    executorComposer.forExecutor(executorFixture.getDefaultExecutor()).persist();
  }

  @DisplayName("Given valid input, should create an endpoint agentless successfully")
  @Test
  @WithMockUser(isAdmin = true)
  void given_validInput_should_createEndpointAgentlessSuccessfully() throws Exception {
    // --PREPARE--
    Endpoint endpointInput = createEndpoint();

    // --EXECUTE--
    String response =
        mvc.perform(
                post(ENDPOINT_URI + "/agentless")
                    .content(asJsonString(endpointInput))
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().is2xxSuccessful())
            .andReturn()
            .getResponse()
            .getContentAsString();

    // --ASSERT
    assertThatJson(response).node("asset_name").isEqualTo(endpointInput.getName());
    assertThatJson(response).node("asset_description").isEqualTo(endpointInput.getDescription());
    assertThatJson(response).node("endpoint_hostname").isEqualTo(endpointInput.getHostname());
    assertThatJson(response).node("endpoint_platform").isEqualTo(endpointInput.getPlatform());
    assertThatJson(response).node("endpoint_arch").isEqualTo(endpointInput.getArch());
    assertThatJson(response).node("endpoint_ips").isEqualTo(endpointInput.getIps());
    assertThatJson(response).node("endpoint_ips").isEqualTo(endpointInput.getIps());
    assertThatJson(response).node("asset_tags").isEqualTo(endpointInput.getTags());
    assertThatJson(response).node("asset_agents").isEqualTo(endpointInput.getAgents());
  }

  @DisplayName("Given wrong input, can't create an endpoint agentless successfully")
  @Test
  @WithMockUser(isAdmin = true)
  void given_wrongInput_cant_createEndpointAgentlessSuccessfully() throws Exception {
    // --PREPARE--
    Endpoint endpointInput = new Endpoint();
    endpointInput.setHostname("Missing attributes for this endpoint");

    // --EXECUTE--
    mvc.perform(
            post(ENDPOINT_URI + "/agentless")
                .content(asJsonString(endpointInput))
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().is4xxClientError());
  }

  @DisplayName("Given valid endpoint input, should upsert an endpoint successfully")
  @Test
  @WithMockUser(isAdmin = true)
  void given_validEndpointInput_should_upsertEndpointSuccessfully() throws Exception {
    // --PREPARE--
    Tag tag = tagRepository.save(getTag());
    String externalReference = "external01";
    EndpointRegisterInput registerInput =
        createWindowsEndpointRegisterInput(List.of(tag.getId()), externalReference);
    Endpoint endpoint = new Endpoint();
    endpoint.setUpdateAttributes(registerInput);
    endpoint.setIps(EndpointMapper.setIps(registerInput.getIps()));
    endpoint.setMacAddresses(EndpointMapper.setMacAddresses(registerInput.getMacAddresses()));
    Agent agent = createAgent(endpoint, externalReference);
    endpoint.setAgents(
        new ArrayList<>() {
          {
            add(agent);
          }
        });
    endpointRepository.save(endpoint);

    String newName = "New hostname";
    registerInput.setHostname(newName);

    Mockito.doReturn("command")
        .when(endpointService)
        .generateUpgradeCommand(String.valueOf(Endpoint.PLATFORM_TYPE.Windows), null, null, null);

    // --EXECUTE--
    String response =
        mvc.perform(
                post(ENDPOINT_URI + "/register")
                    .content(asJsonString(registerInput))
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().is2xxSuccessful())
            .andReturn()
            .getResponse()
            .getContentAsString();

    // --ASSERT--
    assertEquals(newName.toLowerCase(), JsonPath.read(response, "$.endpoint_hostname"));
  }

  @DisplayName(
      "Given valid input for a non-existing endpoint, should create and upsert successfully")
  @Test
  @WithMockUser(isAdmin = true)
  void given_validInputForNonExistingEndpoint_should_createAndUpsertSuccessfully()
      throws Exception {
    // --PREPARE--
    Tag tag = tagRepository.save(getTag());
    String externalReference = "external01";
    EndpointRegisterInput registerInput =
        createWindowsEndpointRegisterInput(List.of(tag.getId()), externalReference);
    Endpoint endpoint = new Endpoint();
    endpoint.setUpdateAttributes(registerInput);
    endpoint.setIps(EndpointMapper.setIps(registerInput.getIps()));
    endpoint.setMacAddresses(EndpointMapper.setMacAddresses(registerInput.getMacAddresses()));
    Agent agent = createAgent(endpoint, externalReference);
    endpoint.setAgents(List.of(agent));

    Mockito.doReturn("command")
        .when(endpointService)
        .generateUpgradeCommand(String.valueOf(Endpoint.PLATFORM_TYPE.Windows), null, null, null);

    // --EXECUTE--
    String response =
        mvc.perform(
                post(ENDPOINT_URI + "/register")
                    .content(asJsonString(registerInput))
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().is2xxSuccessful())
            .andReturn()
            .getResponse()
            .getContentAsString();

    // --ASSERT--
    assertEquals(WINDOWS_ASSET_NAME_INPUT, JsonPath.read(response, "$.asset_name"));
  }

  @DisplayName("Given valid input, should update an endpoint successfully")
  @Test
  @WithMockUser(isAdmin = true)
  void given_validInput_should_updateEndpointSuccessfully() throws Exception {
    // --PREPARE--
    Tag tag = tagRepository.save(getTag());
    String externalReference = "external01";
    EndpointInput endpointInput = createWindowsEndpointInput(List.of(tag.getId()));
    Endpoint endpoint = new Endpoint();
    endpoint.setUpdateAttributes(endpointInput);
    endpoint.setIps(EndpointMapper.setIps(endpointInput.getIps()));
    endpoint.setMacAddresses(EndpointMapper.setMacAddresses(endpointInput.getMacAddresses()));
    Agent agent = createAgent(endpoint, externalReference);
    endpoint.setAgents(
        new ArrayList<>() {
          {
            add(agent);
          }
        });
    Endpoint endpointCreated = endpointRepository.save(endpoint);

    EndpointInput updateInput = new EndpointInput();
    String newName = "New hostname";
    updateInput.setName(newName);
    updateInput.setHostname(newName);
    updateInput.setIps(endpointInput.getIps());
    updateInput.setPlatform(endpointInput.getPlatform());
    updateInput.setArch(endpointInput.getArch());

    // --EXECUTE--
    String response =
        mvc.perform(
                put(ENDPOINT_URI + "/" + endpointCreated.getId())
                    .content(asJsonString(updateInput))
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().is2xxSuccessful())
            .andReturn()
            .getResponse()
            .getContentAsString();

    // --ASSERT
    assertThatJson(response).node("asset_name").isEqualTo(newName);
    assertThatJson(response).node("endpoint_hostname").isEqualTo(newName.toLowerCase());
    assertThatJson(response).node("endpoint_platform").isEqualTo(endpointCreated.getPlatform());
    assertThatJson(response).node("endpoint_ips").isEqualTo(endpointCreated.getIps());
  }

  @DisplayName("Given valid input, should delete an endpoint successfully")
  @Test
  @WithMockUser(isAdmin = true)
  void given_validInput_should_deleteEndpointSuccessfully() throws Exception {
    // --PREPARE--
    Tag tag = tagRepository.save(getTag());
    String externalReference = "external01";
    EndpointInput endpointInput = createWindowsEndpointInput(List.of(tag.getId()));
    Endpoint endpoint = new Endpoint();
    endpoint.setUpdateAttributes(endpointInput);
    endpoint.setIps(EndpointMapper.setIps(endpointInput.getIps()));
    endpoint.setMacAddresses(EndpointMapper.setMacAddresses(endpointInput.getMacAddresses()));
    Agent agent = createAgent(endpoint, externalReference);
    endpoint.setAgents(
        new ArrayList<>() {
          {
            add(agent);
          }
        });
    Endpoint endpointCreated = endpointRepository.save(endpoint);

    // -- EXECUTE --
    mvc.perform(
            delete(ENDPOINT_URI + "/" + endpointCreated.getId()).accept(MediaType.APPLICATION_JSON))
        .andExpect(status().is2xxSuccessful());

    // The 2 calls (delete then get) should not be in the same transaction
    // so we use this workaround to make it work
    entityManager.flush();
    entityManager.clear();

    // -- ASSERT --
    mvc.perform(
            get(ENDPOINT_URI + "/" + endpointCreated.getId()).accept(MediaType.APPLICATION_JSON))
        .andExpect(status().is4xxClientError());
  }

  @Nested
  @DisplayName("Retrieve targets")
  @WithMockUser(isAdmin = true)
  class TargetEndpoint {

    @Test
    @DisplayName("Should return matching endpoints when given a static asset group or asset ID")
    void given_staticAssetGroupOrAssetId_should_returnMatchingEndpoints() throws Exception {
      // -- PREPARE --
      SearchPaginationInput searchPaginationInput = PaginationFixture.getDefault().build();

      // Prepare asset group with an endpoint
      Endpoint endpoint = endpointRepository.save(EndpointFixture.createEndpoint());
      AssetGroup assetGroup =
          assetGroupRepository.save(createAssetGroupWithAssets("All windows", List.of(endpoint)));

      // Prepare an endpoint
      Endpoint endpoint2 = endpointRepository.save(EndpointFixture.createEndpoint());
      // Prepare another endpoint, that we shouldn't retrieve
      endpointRepository.save(EndpointFixture.createEndpoint());

      // Prepare asset group filter
      Filters.Filter filterAssetGroup =
          buildFilter("assetGroups", Filters.FilterMode.or, List.of(assetGroup.getId()));

      // Prepare asset filter
      Filters.Filter filterAsset =
          buildFilter("asset_id", Filters.FilterMode.or, List.of(endpoint2.getId()));

      // Prepare filter group
      Filters.FilterGroup filterGroup = new Filters.FilterGroup();
      filterGroup.setMode(Filters.FilterMode.or);
      filterGroup.setFilters(List.of(filterAssetGroup, filterAsset));
      searchPaginationInput.setFilterGroup(filterGroup);

      String response =
          mvc.perform(
                  post(ENDPOINT_URI + "/targets")
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(asJsonString(searchPaginationInput)))
              .andExpect(status().is2xxSuccessful())
              .andExpect(jsonPath("$.numberOfElements").value(2))
              .andReturn()
              .getResponse()
              .getContentAsString();
      assertThatJson(response)
          .inPath("$.content[*].asset_id")
          .isArray()
          .containsExactlyInAnyOrderElementsOf(List.of(endpoint.getId(), endpoint2.getId()));
    }

    @Test
    @DisplayName("Should return matching endpoints when given dynamic asset group")
    void given_dynamicAssetGroupId_should_returnMatchingEndpoints() throws Exception {
      // -- PREPARE --
      SearchPaginationInput searchPaginationInput = PaginationFixture.getDefault().build();

      // Prepare an endpoint
      Endpoint windowEndpoint = endpointRepository.save(EndpointFixture.createEndpoint());
      Endpoint linuxEndpoint = EndpointFixture.createEndpoint();
      linuxEndpoint.setPlatform(Endpoint.PLATFORM_TYPE.Linux);
      endpointRepository.save(linuxEndpoint);

      // Prepare dynamic asset group
      Filters.Filter windowfilter =
          buildFilter("endpoint_platform", Filters.FilterMode.or, List.of("Windows"));
      Filters.FilterGroup dynamicFilter = Filters.FilterGroup.defaultFilterGroup();
      dynamicFilter.setFilters(List.of(windowfilter));
      AssetGroup assetGroup = createDefaultAssetGroup("All windows");
      assetGroup.setDynamicFilter(dynamicFilter);
      AssetGroup assetGroupSaved = assetGroupRepository.save(assetGroup);

      // Prepare searcPagination input
      Filters.Filter assetGroupfilter =
          buildFilter("assetGroups", Filters.FilterMode.or, List.of(assetGroupSaved.getId()));
      Filters.FilterGroup searchPaginationFilterGroup = new Filters.FilterGroup();
      searchPaginationFilterGroup.setFilters(List.of(assetGroupfilter));
      searchPaginationFilterGroup.setMode(Filters.FilterMode.or);
      searchPaginationInput.setFilterGroup(searchPaginationFilterGroup);

      mvc.perform(
              post(ENDPOINT_URI + "/targets")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(asJsonString(searchPaginationInput)))
          .andExpect(status().is2xxSuccessful())
          .andExpect(jsonPath("$.numberOfElements").value(1))
          .andExpect(jsonPath("$.content.[0].asset_id").value(windowEndpoint.getId()));
    }

    @Test
    @DisplayName("Should return one endpoints when given dynamic asset group AND asset id")
    void given_dynamicAssetGroupAndAssetID_should_ReturnEndpointsPresentInBoth() throws Exception {
      // -- PREPARE --
      SearchPaginationInput searchPaginationInput = PaginationFixture.getDefault().build();

      // Prepare an endpoint
      endpointRepository.save(EndpointFixture.createEndpoint());
      Endpoint windowEndpoint2 = endpointRepository.save(EndpointFixture.createEndpoint());

      // Prepare dynamic asset group
      Filters.Filter windowfilter =
          buildFilter("endpoint_platform", Filters.FilterMode.or, List.of("Windows"));
      Filters.FilterGroup dynamicFilter = Filters.FilterGroup.defaultFilterGroup();
      dynamicFilter.setFilters(List.of(windowfilter));
      AssetGroup assetGroup = createDefaultAssetGroup("All windows");
      assetGroup.setDynamicFilter(dynamicFilter);
      AssetGroup assetGroupSaved = assetGroupRepository.save(assetGroup);

      // Prepare searcPagination input
      Filters.Filter assetGroupfilter =
          buildFilter("assetGroups", Filters.FilterMode.or, List.of(assetGroupSaved.getId()));
      Filters.Filter assetIdFilter =
          buildFilter("asset_id", Filters.FilterMode.or, List.of(windowEndpoint2.getId()));
      Filters.FilterGroup searchPaginationFilterGroup = new Filters.FilterGroup();
      searchPaginationFilterGroup.setFilters(List.of(assetGroupfilter, assetIdFilter));
      searchPaginationFilterGroup.setMode(Filters.FilterMode.and);
      searchPaginationInput.setFilterGroup(searchPaginationFilterGroup);

      mvc.perform(
              post(ENDPOINT_URI + "/targets")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(asJsonString(searchPaginationInput)))
          .andExpect(status().is2xxSuccessful())
          .andExpect(jsonPath("$.numberOfElements").value(1))
          .andExpect(jsonPath("$.content.[0].asset_id").value(windowEndpoint2.getId()));
    }
  }

  private Inject prepareOptionsEndpointTestData() {
    // Teams
    Endpoint e1input = createEndpoint();
    e1input.setName(WINDOWS_ASSET_NAME_INPUT + "1");
    Endpoint endpoint1 = this.endpointRepository.save(e1input);
    Endpoint e2input = createEndpoint();
    e2input.setName(WINDOWS_ASSET_NAME_INPUT + "2");
    Endpoint endpoint2 = this.endpointRepository.save(e2input);
    Endpoint e3input = createEndpoint();
    e3input.setName(WINDOWS_ASSET_NAME_INPUT + "3");
    Endpoint endpoint3 = this.endpointRepository.save(e3input);
    Endpoint e4input = createEndpoint();
    e4input.setName(WINDOWS_ASSET_NAME_INPUT + "4");
    Endpoint endpoint4 = this.endpointRepository.save(e4input);
    Exercise exInput = ExerciseFixture.getExercise();
    Exercise exercise = this.exerciseService.createExercise(exInput);
    // Inject
    Inject inject = getDefaultInject();
    inject.setExercise(exercise);
    inject.setAssets(
        new ArrayList<>() {
          {
            add(endpoint1);
            add(endpoint2);
            add(endpoint3);
            add(endpoint4);
          }
        });
    return this.injectRepository.save(inject);
  }

  Stream<Arguments> optionsByNameTestParameters() {
    return Stream.of(
        Arguments.of(
            null, false, 0), // Case 1: searchText is null and simulationOrScenarioId is null
        Arguments.of(
            WINDOWS_ASSET_NAME_INPUT,
            false,
            0), // Case 2: searchText is valid and simulationOrScenarioId is null
        Arguments.of(
            WINDOWS_ASSET_NAME_INPUT + "2",
            false,
            0), // Case 2: searchText is valid and simulationOrScenarioId is null
        Arguments.of(
            null, true, 4), // Case 3: searchText is null and simulationOrScenarioId is valid
        Arguments.of(
            WINDOWS_ASSET_NAME_INPUT,
            true,
            4), // Case 4: searchText is valid and simulationOrScenarioId is valid
        Arguments.of(
            WINDOWS_ASSET_NAME_INPUT + "2",
            true,
            1) // Case 5: searchText is valid and simulationOrScenarioId is valid
        );
  }

  @DisplayName("Test optionsByName")
  @ParameterizedTest
  @MethodSource("optionsByNameTestParameters")
  @WithMockUser(isAdmin = true)
  void optionsByNameTest(
      String searchText, Boolean simulationOrScenarioId, Integer expectedNumberOfResults)
      throws Exception {
    // --PREPARE--
    Inject i = prepareOptionsEndpointTestData();
    Exercise exercise = i.getExercise();

    // --EXECUTE--;
    String response =
        mvc.perform(
                get(ENDPOINT_URI + "/options")
                    .queryParam("searchText", searchText)
                    .queryParam("sourceId", simulationOrScenarioId ? exercise.getId() : null)
                    .accept(MediaType.APPLICATION_JSON))
            .andReturn()
            .getResponse()
            .getContentAsString();

    JSONArray jsonArray = new JSONArray(response);

    // --ASSERT--
    assertEquals(expectedNumberOfResults, jsonArray.length());
  }

  Stream<Arguments> optionsByIdTestParameters() {
    return Stream.of(
        Arguments.of(0, 0), // Case 1: 0 ID given
        Arguments.of(1, 1), // Case 1: 1 ID given
        Arguments.of(2, 2) // Case 2: 2 IDs given
        );
  }

  @DisplayName("Test optionsById")
  @ParameterizedTest
  @MethodSource("optionsByIdTestParameters")
  @WithMockUser(isAdmin = true)
  void optionsByIdTest(Integer numberOfAssetToProvide, Integer expectedNumberOfResults)
      throws Exception {
    // --PREPARE--
    Inject inject = prepareOptionsEndpointTestData();
    List<Asset> assets = inject.getAssets();

    List<String> idsToSearch = new ArrayList<>();
    for (int i = 0; i < numberOfAssetToProvide; i++) {
      idsToSearch.add(assets.get(i).getId());
    }

    // --EXECUTE--;
    String response =
        mvc.perform(
                post(ENDPOINT_URI + "/options")
                    .content(asJsonString(idsToSearch))
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON))
            .andReturn()
            .getResponse()
            .getContentAsString();

    JSONArray jsonArray = new JSONArray(response);

    // --ASSERT--
    assertEquals(expectedNumberOfResults, jsonArray.length());
  }

  private Filters.Filter buildFilter(String key, Filters.FilterMode mode, List<String> values) {
    Filters.Filter filter = new Filters.Filter();
    filter.setKey(key);
    filter.setMode(mode);
    filter.setOperator(Filters.FilterOperator.eq);
    filter.setValues(values);
    return filter;
  }
}
