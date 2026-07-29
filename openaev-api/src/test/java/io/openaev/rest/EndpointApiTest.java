package io.openaev.rest;

import static io.openaev.rest.asset.endpoint.EndpointApi.ASSET_URI;
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
import io.openaev.context.TenantContext;
import io.openaev.database.model.*;
import io.openaev.database.model.Tag;
import io.openaev.database.repository.AssetAgentJobRepository;
import io.openaev.database.repository.AssetGroupRepository;
import io.openaev.database.repository.EndpointRepository;
import io.openaev.database.repository.InjectRepository;
import io.openaev.database.repository.SecurityPlatformRepository;
import io.openaev.database.repository.TagRepository;
import io.openaev.rest.asset.endpoint.form.EndpointInput;
import io.openaev.rest.asset.endpoint.form.EndpointRegisterInput;
import io.openaev.rest.asset.form.AssetBulkProcessingInput;
import io.openaev.rest.exercise.service.ExerciseService;
import io.openaev.rest.inject.service.InjectStatusService;
import io.openaev.service.EndpointService;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.EndpointFixture;
import io.openaev.utils.fixtures.ExecutorFixture;
import io.openaev.utils.fixtures.ExerciseFixture;
import io.openaev.utils.fixtures.PaginationFixture;
import io.openaev.utils.fixtures.SecurityPlatformFixture;
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
  @Autowired private SecurityPlatformRepository securityPlatformRepository;
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
    assertThatJson(response).node("asset_hostname").isEqualTo(endpointInput.getHostname());
    assertThatJson(response).node("endpoint_platform").isEqualTo(endpointInput.getPlatform());
    assertThatJson(response).node("endpoint_arch").isEqualTo(endpointInput.getArch());
    assertThatJson(response).node("asset_ips").isEqualTo(endpointInput.getIps());
    assertThatJson(response).node("asset_tags").isEqualTo(endpointInput.getTags());
    assertThatJson(response).node("asset_agents").isEqualTo(endpointInput.getAgents());
  }

  @DisplayName(
      "Given a web application input without platform/arch, should create it as WEB_APPLICATION")
  @Test
  @WithMockUser(isAdmin = true)
  void given_webApplicationInput_should_createWithCategory() throws Exception {
    // --PREPARE--
    EndpointInput input = new EndpointInput();
    input.setName("Filigran website");
    input.setCategory(AssetCategory.WEB_APPLICATION);
    input.setSubcategory(AssetSubCategory.WEBSITE);
    input.setUrl("https://filigran.io");
    input.setInternetFacing(true);

    // --EXECUTE--
    String response =
        mvc.perform(
                post(ENDPOINT_URI + "/agentless")
                    .content(asJsonString(input))
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .with(csrf()))
            .andExpect(status().is2xxSuccessful())
            .andReturn()
            .getResponse()
            .getContentAsString();

    // --ASSERT--
    assertThatJson(response).node("asset_category").isEqualTo("WEB_APPLICATION");
    assertThatJson(response).node("asset_subcategory").isEqualTo("WEBSITE");
    assertThatJson(response).node("asset_url").isEqualTo("https://filigran.io");
    assertThatJson(response).node("asset_internet_facing").isEqualTo(true);
    // platform / arch default to Unknown server-side when omitted
    assertThatJson(response).node("endpoint_platform").isEqualTo("Unknown");
    assertThatJson(response).node("endpoint_arch").isEqualTo("Unknown");
  }

  @DisplayName("Given a cloud resource input, should persist provider and native type")
  @Test
  @WithMockUser(isAdmin = true)
  void given_cloudResourceInput_should_persistCloudFields() throws Exception {
    // --PREPARE--
    EndpointInput input = new EndpointInput();
    input.setName("prod-data-bucket");
    input.setCategory(AssetCategory.CLOUD_RESOURCE);
    input.setSubcategory(AssetSubCategory.STORAGE);
    input.setCloudProvider(CloudProvider.AWS);
    input.setCloudNativeType("s3_bucket");
    input.setCloudRegion("eu-west-1");

    // --EXECUTE--
    String response =
        mvc.perform(
                post(ENDPOINT_URI + "/agentless")
                    .content(asJsonString(input))
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .with(csrf()))
            .andExpect(status().is2xxSuccessful())
            .andReturn()
            .getResponse()
            .getContentAsString();

    // --ASSERT--
    assertThatJson(response).node("asset_category").isEqualTo("CLOUD_RESOURCE");
    assertThatJson(response).node("asset_subcategory").isEqualTo("STORAGE");
    assertThatJson(response).node("asset_cloud_provider").isEqualTo("AWS");
    assertThatJson(response).node("asset_cloud_native_type").isEqualTo("s3_bucket");
    assertThatJson(response).node("asset_cloud_region").isEqualTo("eu-west-1");
  }

  @DisplayName(
      "Given an identity input with a blank linked person, should not violate the person FK")
  @Test
  @WithMockUser(isAdmin = true)
  void given_identityInputWithBlankLinkedPerson_should_createWithoutFkViolation() throws Exception {
    // --PREPARE--
    // A cleared person picker submits an empty string; it must be normalized to null so the
    // asset_linked_person -> users(user_id) foreign key is never violated.
    EndpointInput input = new EndpointInput();
    input.setName("svc-account");
    input.setCategory(AssetCategory.IDENTITY);
    input.setSubcategory(AssetSubCategory.SERVICE_ACCOUNT);
    input.setLinkedPerson("");

    // --EXECUTE--
    String response =
        mvc.perform(
                post(ENDPOINT_URI + "/agentless")
                    .content(asJsonString(input))
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .with(csrf()))
            .andExpect(status().is2xxSuccessful())
            .andReturn()
            .getResponse()
            .getContentAsString();

    // --ASSERT--
    assertThatJson(response).node("asset_category").isEqualTo("IDENTITY");
    assertThatJson(response).node("asset_subcategory").isEqualTo("SERVICE_ACCOUNT");
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

    String newName = "new-hostname";
    registerInput.setHostname(newName);

    Mockito.doReturn("command")
        .when(endpointService)
        .generateUpgradeCommand(
            String.valueOf(Endpoint.PLATFORM_TYPE.Windows),
            null,
            null,
            null,
            TenantContext.getCurrentTenant());

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
    assertEquals(newName.toLowerCase(), JsonPath.read(response, "$.asset_hostname"));
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
    Endpoint endpoint = new Endpoint();
    endpoint.setUpdateAttributes(registerInput);
    endpoint.setIps(EndpointMapper.setIps(registerInput.getIps()));
    endpoint.setMacAddresses(EndpointMapper.setMacAddresses(registerInput.getMacAddresses()));
    Agent agent = createAgent(endpoint, externalReference);
    endpoint.setAgents(List.of(agent));

    Mockito.doReturn("command")
        .when(endpointService)
        .generateUpgradeCommand(
            String.valueOf(Endpoint.PLATFORM_TYPE.Windows),
            null,
            null,
            null,
            TenantContext.getCurrentTenant());

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
    String newName = "New-hostname";
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
    assertThatJson(response).node("asset_hostname").isEqualTo(newName.toLowerCase());
    assertThatJson(response).node("endpoint_platform").isEqualTo(endpointCreated.getPlatform());
    assertThatJson(response).node("asset_ips").isEqualTo(endpointCreated.getIps());
  }

  @DisplayName("Given valid input, should delete an endpoint successfully")
  @Test
  @WithMockUser(isAdmin = true)
  void given_validInput_should_deleteEndpointSuccessfully() throws Exception {
    // --PREPARE--
    Tag tag = tagRepository.save(getTagNoId());
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

    @Test
    @DisplayName("Should return endpoints when filterGroup is omitted (regression #6927)")
    void given_missingFilterGroup_should_returnEndpointsWithoutError() throws Exception {
      // GIVEN - the body shape posted by injectors that omit filterGroup entirely,
      // which used to 500 with an NPE in searchManagedEndpoints (#6927)
      endpointRepository.save(EndpointFixture.createEndpoint());

      // WHEN / THEN - falls back to an empty filter group and returns managed endpoints
      mvc.perform(
              post(ENDPOINT_URI + "/targets")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"page\": 0, \"size\": 20}")
                  .with(csrf()))
          .andExpect(status().is2xxSuccessful())
          .andExpect(jsonPath("$.content").isArray());
    }

    @Test
    @DisplayName("Should tolerate uppercase filter enums from older injectors (regression #6927)")
    void given_uppercaseFilterEnums_should_returnEndpointsWithoutError() throws Exception {
      // GIVEN - an asset group targeted with the drifted vocabulary ("CONTAINS", "OR") that an
      // older injector image may post, which used to fail with a bare 400 (#6927)
      Endpoint endpoint = endpointRepository.save(EndpointFixture.createEndpoint());
      AssetGroup assetGroup =
          assetGroupRepository.save(createAssetGroupWithAssets("All windows", List.of(endpoint)));
      String body =
          """
          {
            "page": 0,
            "size": 20,
            "filterGroup": {
              "mode": "OR",
              "filters": [
                {
                  "id": "drifted-filter",
                  "key": "assetGroups",
                  "mode": "OR",
                  "operator": "CONTAINS",
                  "values": ["%s"]
                }
              ]
            }
          }
          """
              .formatted(assetGroup.getId());

      // WHEN / THEN - deserializes case-insensitively and resolves the target
      mvc.perform(
              post(ENDPOINT_URI + "/targets")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(body)
                  .with(csrf()))
          .andExpect(status().is2xxSuccessful())
          .andExpect(jsonPath("$.numberOfElements").value(1))
          .andExpect(jsonPath("$.content.[0].asset_id").value(endpoint.getId()));
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
        endpointInput.setHostname(name.replaceAll("[^A-Za-z0-9\\-.]", ""));

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
        String tenantA = TenantContext.getCurrentTenant();

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

        // Create tenant B with the same executor ID to reproduce the cross-tenant join bug
        Tenant tenantB = tenantHelper.createTenantWithCurrentUser("TenantB-CompositeKey");
        tenantHelper.switchToTenant(tenantB.getId(), entityManager);

        Executor executorB = executorFixture.createDefaultExecutor("OpenAEV-B");
        executorB.setId(executorA.getId());
        executorComposer.forExecutor(executorB).persist().get();

        // Switch back to tenant A
        tenantHelper.switchToTenant(tenantA, entityManager);

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

      Agent agent = new Agent();
      agent.setId("agent-1");

      AssetAgentJob assetAgentJob = new AssetAgentJob();
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
      AssetAgentJob assetAgentJob = new AssetAgentJob();
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

  @Nested
  @DisplayName("DELETE /api/assets - bulk delete")
  class BulkDeleteAssets {

    private Endpoint createPersistedEndpoint(String name, String hostname, String ip) {
      return endpointComposer
          .forEndpoint(
              EndpointFixture.createEndpoint(
                  name,
                  Endpoint.PLATFORM_TYPE.Windows,
                  Endpoint.PLATFORM_ARCH.x86_64,
                  hostname,
                  new String[] {ip}))
          .persist()
          .get();
    }

    @Test
    @DisplayName("Given explicit asset ids, should delete only those assets")
    @WithMockUser(isAdmin = true)
    void given_explicitAssetIds_should_deleteOnlyThoseAssets() throws Exception {
      // -- PREPARE --
      Endpoint toDelete1 = createPersistedEndpoint("bulk-delete-1", "bulk-host-01", "10.1.0.1");
      Endpoint toDelete2 = createPersistedEndpoint("bulk-delete-2", "bulk-host-02", "10.1.0.2");
      Endpoint toKeep = createPersistedEndpoint("bulk-keep", "bulk-host-03", "10.1.0.3");

      AssetBulkProcessingInput input = new AssetBulkProcessingInput();
      input.setAssetIdsToProcess(List.of(toDelete1.getId(), toDelete2.getId()));

      // -- EXECUTE --
      String response =
          mvc.perform(
                  delete(ASSET_URI)
                      .content(asJsonString(input))
                      .contentType(MediaType.APPLICATION_JSON)
                      .accept(MediaType.APPLICATION_JSON)
                      .with(csrf()))
              .andExpect(status().is2xxSuccessful())
              .andReturn()
              .getResponse()
              .getContentAsString();

      entityManager.flush();
      entityManager.clear();

      // -- ASSERT --
      JSONArray deletedIds = new JSONArray(response);
      assertEquals(2, deletedIds.length());
      assertThat(endpointRepository.findById(toDelete1.getId())).isEmpty();
      assertThat(endpointRepository.findById(toDelete2.getId())).isEmpty();
      assertThat(endpointRepository.findById(toKeep.getId())).isPresent();
    }

    @Test
    @DisplayName(
        "Given a search input with ignored ids, should delete matching assets except the ignored ones")
    @WithMockUser(isAdmin = true)
    void given_searchInputWithIgnoredIds_should_deleteMatchingAssetsExceptIgnored()
        throws Exception {
      // -- PREPARE --
      Endpoint toDelete = createPersistedEndpoint("bulkwipe-one", "bulk-host-04", "10.1.0.4");
      Endpoint toIgnore = createPersistedEndpoint("bulkwipe-two", "bulk-host-05", "10.1.0.5");
      Endpoint unrelated = createPersistedEndpoint("unrelated", "bulk-host-06", "10.1.0.6");

      AssetBulkProcessingInput input = new AssetBulkProcessingInput();
      input.setSearchPaginationInput(PaginationFixture.simpleTextSearch("bulkwipe"));
      input.setAssetIdsToIgnore(List.of(toIgnore.getId()));

      // -- EXECUTE --
      mvc.perform(
              delete(ASSET_URI)
                  .content(asJsonString(input))
                  .contentType(MediaType.APPLICATION_JSON)
                  .accept(MediaType.APPLICATION_JSON)
                  .with(csrf()))
          .andExpect(status().is2xxSuccessful());

      entityManager.flush();
      entityManager.clear();

      // -- ASSERT --
      assertThat(endpointRepository.findById(toDelete.getId())).isEmpty();
      assertThat(endpointRepository.findById(toIgnore.getId())).isPresent();
      assertThat(endpointRepository.findById(unrelated.getId())).isPresent();
    }

    @Test
    @DisplayName("Given both explicit ids and a search input, should return a bad request")
    @WithMockUser(isAdmin = true)
    void given_bothIdsAndSearchInput_should_returnBadRequest() throws Exception {
      // -- PREPARE --
      AssetBulkProcessingInput input = new AssetBulkProcessingInput();
      input.setAssetIdsToProcess(List.of("some-id"));
      input.setSearchPaginationInput(PaginationFixture.simpleTextSearch("some text"));

      // -- EXECUTE & ASSERT --
      mvc.perform(
              delete(ASSET_URI)
                  .content(asJsonString(input))
                  .contentType(MediaType.APPLICATION_JSON)
                  .accept(MediaType.APPLICATION_JSON)
                  .with(csrf()))
          .andExpect(status().is4xxClientError());
    }

    @Test
    @DisplayName("Given a security platform id, should never delete it through the bulk endpoint")
    @WithMockUser(isAdmin = true)
    void given_securityPlatformId_should_neverDeleteIt() throws Exception {
      // -- PREPARE --
      SecurityPlatform securityPlatform =
          securityPlatformRepository.save(SecurityPlatformFixture.createDefault("Bulk EDR", "EDR"));

      AssetBulkProcessingInput input = new AssetBulkProcessingInput();
      input.setAssetIdsToProcess(List.of(securityPlatform.getId()));

      // -- EXECUTE --
      String response =
          mvc.perform(
                  delete(ASSET_URI)
                      .content(asJsonString(input))
                      .contentType(MediaType.APPLICATION_JSON)
                      .accept(MediaType.APPLICATION_JSON)
                      .with(csrf()))
              .andExpect(status().is2xxSuccessful())
              .andReturn()
              .getResponse()
              .getContentAsString();

      entityManager.flush();
      entityManager.clear();

      // -- ASSERT --
      JSONArray deletedIds = new JSONArray(response);
      assertEquals(0, deletedIds.length());
      assertThat(securityPlatformRepository.findById(securityPlatform.getId())).isPresent();
    }
  }
}
