package io.openaev.rest;

import static io.openaev.rest.asset.endpoint.EndpointApi.ENDPOINT_URI;
import static io.openaev.utils.JsonTestUtils.asJsonString;
import static io.openaev.utils.fixtures.AgentFixture.createAgent;
import static io.openaev.utils.fixtures.AgentFixture.createDefaultAgentService;
import static io.openaev.utils.fixtures.AssetGroupFixture.createAssetGroupWithAssets;
import static io.openaev.utils.fixtures.AssetGroupFixture.createDefaultAssetGroup;
import static io.openaev.utils.fixtures.EndpointFixture.*;
import static io.openaev.utils.fixtures.InjectFixture.getDefaultInject;
import static io.openaev.utils.fixtures.TagFixture.getTagNoId;
import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.openaev.IntegrationTest;
import io.openaev.context.TxCtx;
import io.openaev.database.model.*;
import io.openaev.database.model.Tag;
import io.openaev.database.repository.AssetAgentJobRepository;
import io.openaev.database.repository.AssetGroupRepository;
import io.openaev.database.repository.EndpointRepository;
import io.openaev.database.repository.InjectRepository;
import io.openaev.database.repository.TagRepository;
import io.openaev.rest.asset.endpoint.form.EndpointInput;
import io.openaev.rest.asset.endpoint.form.EndpointRegisterInput;
import io.openaev.rest.exercise.service.ExerciseService;
import io.openaev.rest.inject.service.InjectStatusService;
import io.openaev.service.EndpointService;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.EndpointFixture;
import io.openaev.utils.fixtures.ExecutorFixture;
import io.openaev.utils.fixtures.ExerciseFixture;
import io.openaev.utils.fixtures.PaginationFixture;
import io.openaev.utils.fixtures.composers.AgentComposer;
import io.openaev.utils.fixtures.composers.EndpointComposer;
import io.openaev.utils.fixtures.composers.ExecutorComposer;
import io.openaev.utils.mapper.EndpointMapper;
import io.openaev.utils.mockUser.WithMockUser;
import io.openaev.utils.pagination.SearchPaginationInput;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.json.JSONArray;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
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
  @Autowired private TenantIsolationTestHelper tenantHelper;
  @Autowired private EndpointComposer endpointComposer;
  @Autowired private AgentComposer agentComposer;

  @MockitoSpyBean private EndpointService endpointService;
  @MockitoSpyBean private AssetAgentJobRepository assetAgentJobRepository;
  @MockitoSpyBean private InjectStatusService injectStatusService;
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
                    .accept(MediaType.APPLICATION_JSON)
                    .with(csrf()))
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
    Endpoint endpointInput = Endpoint.fromTenant("tenant");
    endpointInput.setHostname("Missing attributes for this endpoint");

    // --EXECUTE--
    mvc.perform(
            post(ENDPOINT_URI + "/agentless")
                .content(asJsonString(endpointInput))
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .with(csrf()))
        .andExpect(status().is4xxClientError());
  }

  @DisplayName("Given valid endpoint input, should upsert an endpoint successfully")
  @Test
  @WithMockUser(isAdmin = true)
  void given_validEndpointInput_should_upsertEndpointSuccessfully() throws Exception {
    // --PREPARE--
    Tag tag = tagRepository.save(getTagNoId());
    String externalReference = "external01";
    EndpointRegisterInput registerInput =
        createWindowsEndpointRegisterInput(List.of(tag.getId()), externalReference);
    Endpoint endpoint = Endpoint.fromTenant("tenant");
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
        .generateUpgradeCommand(
            TxCtx.of("tenant"),
            String.valueOf(Endpoint.PLATFORM_TYPE.Windows),
            null,
            null,
            null,
            "tenant");

    // --EXECUTE--
    String response =
        mvc.perform(
                post(ENDPOINT_URI + "/register")
                    .content(asJsonString(registerInput))
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .with(csrf()))
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
    Tag tag = tagRepository.save(getTagNoId());
    String externalReference = "external01";
    EndpointRegisterInput registerInput =
        createWindowsEndpointRegisterInput(List.of(tag.getId()), externalReference);
    Endpoint endpoint = Endpoint.fromTenant("tenant");
    endpoint.setUpdateAttributes(registerInput);
    endpoint.setIps(EndpointMapper.setIps(registerInput.getIps()));
    endpoint.setMacAddresses(EndpointMapper.setMacAddresses(registerInput.getMacAddresses()));
    Agent agent = createAgent(endpoint, externalReference);
    endpoint.setAgents(List.of(agent));

    Mockito.doReturn("command")
        .when(endpointService)
        .generateUpgradeCommand(
            TxCtx.of("tenant"),
            String.valueOf(Endpoint.PLATFORM_TYPE.Windows),
            null,
            null,
            null,
            "tenant");

    // --EXECUTE--
    String response =
        mvc.perform(
                post(ENDPOINT_URI + "/register")
                    .content(asJsonString(registerInput))
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .with(csrf()))
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
    Tag tag = tagRepository.save(getTagNoId());
    String externalReference = "external01";
    EndpointInput endpointInput = createWindowsEndpointInput(List.of(tag.getId()));
    Endpoint endpoint = Endpoint.fromTenant("tenant");
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
                    .accept(MediaType.APPLICATION_JSON)
                    .with(csrf()))
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
    Tag tag = tagRepository.save(getTagNoId());
    String externalReference = "external01";
    EndpointInput endpointInput = createWindowsEndpointInput(List.of(tag.getId()));
    Endpoint endpoint = Endpoint.fromTenant("tenant");
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
            delete(ENDPOINT_URI + "/" + endpointCreated.getId())
                .accept(MediaType.APPLICATION_JSON)
                .with(csrf()))
        .andExpect(status().is2xxSuccessful());

    // The 2 calls (delete then get) should not be in the same transaction
    // so we use this workaround to make it work
    entityManager.flush();
    entityManager.clear();

    // -- ASSERT --
    mvc.perform(
            get(ENDPOINT_URI + "/" + endpointCreated.getId())
                .accept(MediaType.APPLICATION_JSON)
                .with(csrf()))
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
                      .content(asJsonString(searchPaginationInput))
                      .with(csrf()))
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
                  .content(asJsonString(searchPaginationInput))
                  .with(csrf()))
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
                  .content(asJsonString(searchPaginationInput))
                  .with(csrf()))
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
                    .accept(MediaType.APPLICATION_JSON)
                    .with(csrf()))
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
                    .accept(MediaType.APPLICATION_JSON)
                    .with(csrf()))
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

  @Nested
  @DisplayName("Tenant Isolation")
  @WithMockUser(isAdmin = true)
  class TenantIsolation {

    @Nested
    @DisplayName("Scenario-style endpoint isolation")
    @WithMockUser
    class EndpointCrudIsolation {

      private Endpoint createTenantEndpoint(String tenantId, String name) throws Exception {
        Endpoint endpointInput = createEndpoint();
        endpointInput.setName(name);
        endpointInput.setHostname(name);

        String createResponse =
            mvc.perform(
                    post("/api/tenants/" + tenantId + "/endpoints/agentless")
                        .content(asJsonString(endpointInput))
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .with(csrf()))
                .andExpect(status().is2xxSuccessful())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String endpointId = JsonPath.read(createResponse, "$.asset_id");
        return endpointRepository.findByIdAndTenantId(endpointId, tenantId).orElseThrow();
      }

      @Test
      @DisplayName("Endpoint created in tenant X should NOT be readable from tenant Y")
      void given_endpointInTenantX_should_notBeReadableFromTenantY() throws Exception {
        // -------- Arrange --------
        Tenant tenantX =
            tenantHelper.createTenantWithCapabilities(
                "Tenant X", Set.of(Capability.MANAGE_ASSETS, Capability.ACCESS_ASSETS));
        Tenant tenantY =
            tenantHelper.createTenantWithCapabilities("Tenant Y", Set.of(Capability.ACCESS_ASSETS));

        Endpoint endpointX = createTenantEndpoint(tenantX.getId(), "Isolation Read Endpoint");
        entityManager.flush();
        entityManager.clear();

        // -------- Act --------
        int responseStatus =
            mvc.perform(
                    get("/api/tenants/" + tenantY.getId() + "/endpoints/" + endpointX.getId())
                        .accept(MediaType.APPLICATION_JSON)
                        .with(csrf()))
                .andReturn()
                .getResponse()
                .getStatus();

        // -------- Assert --------
        assertThat(responseStatus).isEqualTo(HttpStatus.NOT_FOUND.value());
      }

      @Test
      @DisplayName("Endpoint created in tenant X should be readable from tenant X")
      void given_endpointInTenantX_should_beReadableFromTenantX() throws Exception {
        // -------- Arrange --------
        Tenant tenantX =
            tenantHelper.createTenantWithCapabilities(
                "Tenant X", Set.of(Capability.MANAGE_ASSETS, Capability.ACCESS_ASSETS));
        Endpoint endpointX = createTenantEndpoint(tenantX.getId(), "Same Tenant Endpoint");

        // -------- Act --------
        String response =
            mvc.perform(
                    get("/api/tenants/" + tenantX.getId() + "/endpoints/" + endpointX.getId())
                        .accept(MediaType.APPLICATION_JSON)
                        .with(csrf()))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        // -------- Assert --------
        assertThatJson(response).node("asset_id").isEqualTo(endpointX.getId());
      }

      @Test
      @DisplayName("Endpoint search in tenant Y should NOT return endpoints from tenant X")
      void given_endpointInTenantX_should_notAppearInTenantYSearch() throws Exception {
        // -------- Arrange --------
        Tenant tenantX =
            tenantHelper.createTenantWithCapabilities(
                "Tenant X", Set.of(Capability.MANAGE_ASSETS, Capability.ACCESS_ASSETS));
        Tenant tenantY =
            tenantHelper.createTenantWithCapabilities("Tenant Y", Set.of(Capability.ACCESS_ASSETS));

        createTenantEndpoint(tenantX.getId(), "CrossTenantSearchEndpoint");
        entityManager.flush();
        entityManager.clear();

        SearchPaginationInput searchInput =
            PaginationFixture.simpleTextSearch("CrossTenantSearchEndpoint");

        // -------- Act --------
        String searchResponse =
            mvc.perform(
                    post("/api/tenants/" + tenantY.getId() + "/endpoints/search")
                        .content(asJsonString(searchInput))
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .with(csrf()))
                .andExpect(status().is2xxSuccessful())
                .andReturn()
                .getResponse()
                .getContentAsString();

        // -------- Assert --------
        assertEquals(Integer.valueOf(0), JsonPath.read(searchResponse, "$.totalElements"));
      }

      @Test
      @DisplayName("Endpoint created in tenant X should NOT be updatable from tenant Y")
      void given_endpointInTenantX_should_notBeUpdatableFromTenantY() throws Exception {
        // -------- Arrange --------
        Tenant tenantX =
            tenantHelper.createTenantWithCapabilities(
                "Tenant X", Set.of(Capability.MANAGE_ASSETS, Capability.ACCESS_ASSETS));
        Tenant tenantY =
            tenantHelper.createTenantWithCapabilities(
                "Tenant Y", Set.of(Capability.MANAGE_ASSETS, Capability.ACCESS_ASSETS));

        Endpoint endpointX = createTenantEndpoint(tenantX.getId(), "Update Isolation Endpoint");
        entityManager.flush();
        entityManager.clear();

        EndpointInput updateInput = createWindowsEndpointInput(List.of());
        updateInput.setName("Hijacked Endpoint");
        updateInput.setHostname("hijacked-endpoint");

        // -------- Act --------
        int responseStatus =
            mvc.perform(
                    put("/api/tenants/" + tenantY.getId() + "/endpoints/" + endpointX.getId())
                        .content(asJsonString(updateInput))
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .with(csrf()))
                .andReturn()
                .getResponse()
                .getStatus();

        // -------- Assert --------
        assertThat(responseStatus).isEqualTo(HttpStatus.NOT_FOUND.value());
      }
    }

    @Nested
    class AgentExecutorJoin {

      @Test
      @DisplayName(
          "Given same executor type in two tenants, endpoint API should return agent without duplicates")
      void givenSameExecutorInTwoTenants_endpointShouldReturnAgentWithoutDuplicates()
          throws Exception {
        // -- Arrange --
        String tenantA = "tenant";

        ExecutorComposer.Composer executorComposerA =
            executorComposer.forExecutor(executorFixture.getDefaultExecutor());
        Executor executorA = executorComposerA.get();

        Endpoint endpointA = EndpointFixture.createEndpoint("Endpoint-TenantA");
        AgentComposer.Composer agentComposerA =
            agentComposer.forAgent(createDefaultAgentService()).withExecutor(executorComposerA);
        EndpointComposer.Composer endpointComposerA =
            endpointComposer.forEndpoint(endpointA).withAgent(agentComposerA);
        endpointComposerA.persist();

        // Flush and clear to avoid "Tenant is immutable" when creating tenant B
        entityManager.flush();
        entityManager.clear();

        Executor executorB = executorFixture.createDefaultExecutor("OpenAEV-B");
        executorB.setId(executorA.getId());
        executorComposer.forExecutor(executorB).persist().get();

        // -- Act --
        String response =
            mvc.perform(
                    get(ENDPOINT_URI + "/" + endpointA.getId())
                        .accept(MediaType.APPLICATION_JSON)
                        .with(csrf()))
                .andExpect(status().is2xxSuccessful())
                .andReturn()
                .getResponse()
                .getContentAsString();

        // -- Assert --
        assertThatJson(response)
            .inPath("$.asset_agents")
            .isArray()
            .as(
                "Endpoint should have exactly 1 agent — not duplicated by cross-tenant executor join")
            .hasSize(1);

        assertThatJson(response)
            .inPath("$.asset_agents[0].agent_executor.executor_id")
            .asString()
            .as("Returned agent_id should match tenant A's agent and not a cross-tenant agent")
            .isEqualTo(executorA.getId());
      }
    }
  }

  @Nested
  @DisplayName("Agent jobs")
  @WithMockUser(isAdmin = true)
  class AgentJobs {

    @Test
    @DisplayName("Given endpoint register input, should return endpoint jobs from service")
    void given_endpointRegisterInput_should_returnEndpointJobsFromService() throws Exception {
      // -- PREPARE --
      EndpointRegisterInput input = createWindowsEndpointRegisterInput(List.of(), "jobs-ext-ref");
      input.setExecutedByUser("jobs-user");
      input.setService(true);
      input.setElevated(false);

      Agent agent = Agent.fromTenant("tenant");
      agent.setId("agent-1");

      AssetAgentJob assetAgentJob = AssetAgentJob.fromTenant("tenant");
      assetAgentJob.setId("job-1");
      assetAgentJob.setCommand("whoami");
      assetAgentJob.setAgent(agent);

      Mockito.doReturn(List.of(assetAgentJob))
          .when(endpointService)
          .getEndpointJobs(Mockito.any(EndpointRegisterInput.class));

      // -- EXECUTE --
      mvc.perform(
              post(ENDPOINT_URI + "/jobs")
                  .content(asJsonString(input))
                  .contentType(MediaType.APPLICATION_JSON)
                  .accept(MediaType.APPLICATION_JSON)
                  .with(csrf()))
          .andExpect(status().is2xxSuccessful())
          .andExpect(jsonPath("$[0].asset_agent_id").value("job-1"))
          .andExpect(jsonPath("$[0].asset_agent_command").value("whoami"));

      // -- ASSERT --
      Mockito.verify(endpointService).getEndpointJobs(Mockito.any(EndpointRegisterInput.class));
    }

    @Test
    @DisplayName("Given existing asset agent job id, should trace retrieval and delete job")
    void given_existingAssetAgentJobId_should_traceRetrievalAndDeleteJob() throws Exception {
      // -- PREPARE --
      String assetAgentJobId = "job-to-clean";
      AssetAgentJob assetAgentJob = AssetAgentJob.fromTenant("tenant");
      assetAgentJob.setId(assetAgentJobId);
      assetAgentJob.setCommand("command");

      Mockito.doReturn(java.util.Optional.of(assetAgentJob))
          .when(assetAgentJobRepository)
          .findById(assetAgentJobId);

      // -- EXECUTE --
      mvc.perform(delete(ENDPOINT_URI + "/jobs/" + assetAgentJobId).with(csrf()))
          .andExpect(status().is2xxSuccessful());

      // -- ASSERT --
      Mockito.verify(assetAgentJobRepository).findById(assetAgentJobId);
      Mockito.verify(injectStatusService).addJobRetrievalTraces(assetAgentJob);
      Mockito.verify(assetAgentJobRepository).deleteById(assetAgentJobId);
    }

    @Test
    @DisplayName(
        "Given unknown asset agent job id, should not trace retrieval and should not delete")
    void given_unknownAssetAgentJobId_should_notTraceRetrievalAndShouldNotDelete()
        throws Exception {
      // -- PREPARE --
      String assetAgentJobId = "job-missing";
      Mockito.doReturn(java.util.Optional.empty())
          .when(assetAgentJobRepository)
          .findById(assetAgentJobId);

      // -- EXECUTE --
      mvc.perform(delete(ENDPOINT_URI + "/jobs/" + assetAgentJobId).with(csrf()))
          .andExpect(status().is2xxSuccessful());

      // -- ASSERT --
      Mockito.verify(assetAgentJobRepository).findById(assetAgentJobId);
      Mockito.verify(injectStatusService, Mockito.never())
          .addJobRetrievalTraces(Mockito.any(AssetAgentJob.class));
      Mockito.verify(assetAgentJobRepository, Mockito.never()).deleteById(assetAgentJobId);
    }
  }
}
