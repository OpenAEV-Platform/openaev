package io.openaev.rest.asset_group;

import static io.openaev.rest.asset_group.AssetGroupApi.ASSET_GROUP_URI;
import static io.openaev.utils.JsonTestUtils.asJsonString;
import static io.openaev.utils.fixtures.AssetGroupFixture.*;
import static io.openaev.utils.fixtures.InjectFixture.getDefaultInject;
import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.openaev.IntegrationTest;
import io.openaev.database.model.*;
import io.openaev.database.model.Tag;
import io.openaev.database.repository.AssetGroupRepository;
import io.openaev.database.repository.InjectRepository;
import io.openaev.database.repository.TagRepository;
import io.openaev.rest.asset_group.form.AssetGroupInput;
import io.openaev.rest.exercise.service.ExerciseService;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.EndpointFixture;
import io.openaev.utils.fixtures.ExerciseFixture;
import io.openaev.utils.fixtures.PaginationFixture;
import io.openaev.utils.fixtures.TagFixture;
import io.openaev.utils.fixtures.composers.AssetGroupComposer;
import io.openaev.utils.fixtures.composers.EndpointComposer;
import io.openaev.utils.mockUser.WithMockUser;
import io.openaev.utils.pagination.SearchPaginationInput;
import jakarta.persistence.EntityManager;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.json.JSONArray;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@TestInstance(PER_CLASS)
@Transactional
class AssetGroupApiTest extends IntegrationTest {

  private static final String ASSET_GROUP_NAME = "assetGroup Test";

  @Autowired private MockMvc mvc;
  @Autowired private AssetGroupRepository assetGroupRepository;
  @Autowired private TagRepository tagRepository;
  @Autowired private InjectRepository injectRepository;
  @Autowired private ExerciseService exerciseService;
  @Autowired private EntityManager entityManager;
  @Autowired private EndpointComposer endpointComposer;
  @Autowired private AssetGroupComposer assetGroupComposer;
  @Autowired private TenantIsolationTestHelper tenantIsolationHelper;

  @DisplayName(
      "Given valid AssetGroupInput, should create and get assetGroup without dynamic filter successfully")
  @Test
  @WithMockUser(isAdmin = true)
  void given_validAssetGroupInput_should_createAndGetAssetGroupWithoutDynamicFilterSuccessfully()
      throws Exception {
    // -- PREPARE --
    Tag tag = tagRepository.save(TagFixture.getTagNoId());
    AssetGroupInput assetGroupInput = createAssetGroupWithTags("Asset group", List.of(tag.getId()));
    Filters.FilterGroup filterGroupExpected = Filters.FilterGroup.defaultFilterGroup();

    // --EXECUTE--
    String response =
        mvc.perform(
                post(ASSET_GROUP_URI)
                    .content(asJsonString(assetGroupInput))
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .with(csrf()))
            .andExpect(status().is2xxSuccessful())
            .andReturn()
            .getResponse()
            .getContentAsString();

    // --ASSERT--
    assertThatJson(response).node("asset_group_name").isEqualTo(assetGroupInput.getName());
    assertThatJson(response)
        .node("asset_group_description")
        .isEqualTo(assetGroupInput.getDescription());
    // Check default because null in the input
    assertThatJson(response).node("asset_group_dynamic_filter").isEqualTo(filterGroupExpected);
    assertThatJson(response).node("asset_group_tags[0]").isEqualTo(tag.getId());

    // --EXECUTE--
    String response2 =
        mvc.perform(
                get(ASSET_GROUP_URI + "/" + JsonPath.read(response, "$.asset_group_id"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .with(csrf()))
            .andExpect(status().is2xxSuccessful())
            .andReturn()
            .getResponse()
            .getContentAsString();

    // --ASSERT--
    assertThatJson(response2).node("asset_group_name").isEqualTo(assetGroupInput.getName());
    assertThatJson(response2)
        .node("asset_group_description")
        .isEqualTo(assetGroupInput.getDescription());
    // Check default because null in the input
    assertThatJson(response2).node("asset_group_dynamic_filter").isEqualTo(filterGroupExpected);
    assertThatJson(response2).node("asset_group_tags[0]").isEqualTo(tag.getId());
  }

  @DisplayName(
      "Given valid AssetGroupInput, should create and get assetGroup with dynamic filter successfully")
  @Test
  @WithMockUser(isAdmin = true)
  void given_validAssetGroupInput_should_createAndGetAssetGroupWithDynamicFilterSuccessfully()
      throws Exception {
    // -- PREPARE --
    Filters.FilterGroup dynamicFilter =
        Filters.FilterGroup.builder()
            .mode(Filters.FilterMode.or)
            .filters(
                List.of(
                    Filters.Filter.builder()
                        .key("endpoint_platform")
                        .mode(Filters.FilterMode.or)
                        .operator(Filters.FilterOperator.eq)
                        .values(List.of("Windows"))
                        .build()))
            .build();
    AssetGroupInput assetGroupInput =
        createAssetGroupWithDynamicFilters("Asset group", dynamicFilter);

    // --EXECUTE--
    String response =
        mvc.perform(
                post(ASSET_GROUP_URI)
                    .content(asJsonString(assetGroupInput))
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .with(csrf()))
            .andExpect(status().is2xxSuccessful())
            .andReturn()
            .getResponse()
            .getContentAsString();

    // --ASSERT--
    assertThatJson(response).node("asset_group_name").isEqualTo(assetGroupInput.getName());
    assertThatJson(response)
        .node("asset_group_description")
        .isEqualTo(assetGroupInput.getDescription());
    // Check default because null in the input
    assertThatJson(response).node("asset_group_dynamic_filter").isEqualTo(dynamicFilter);

    // --EXECUTE--
    String response2 =
        mvc.perform(
                get(ASSET_GROUP_URI + "/" + JsonPath.read(response, "$.asset_group_id"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .with(csrf()))
            .andExpect(status().is2xxSuccessful())
            .andReturn()
            .getResponse()
            .getContentAsString();

    // --ASSERT--
    assertThatJson(response2).node("asset_group_name").isEqualTo(assetGroupInput.getName());
    assertThatJson(response2)
        .node("asset_group_description")
        .isEqualTo(assetGroupInput.getDescription());
    // Check default because null in the input
    assertThatJson(response2).node("asset_group_dynamic_filter").isEqualTo(dynamicFilter);
  }

  @DisplayName(
      "Create one asset group with Java and one with SQL, compare them to check the both asset_group_dynamic_filter are the same")
  @Test
  @WithMockUser(isAdmin = true)
  void should_createOneAssetGroupWithJavaAndOneWithSQLAndCompareThem() throws Exception {
    // -- PREPARE --
    Tag tag = tagRepository.save(TagFixture.getTagNoId());
    AssetGroupInput assetGroupInput = createAssetGroupWithTags("Asset group", List.of(tag.getId()));

    // --EXECUTE--
    String response =
        mvc.perform(
                post(ASSET_GROUP_URI)
                    .content(asJsonString(assetGroupInput))
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .with(csrf()))
            .andExpect(status().is2xxSuccessful())
            .andReturn()
            .getResponse()
            .getContentAsString();

    entityManager
        .createNativeQuery(
            "INSERT INTO asset_groups (asset_group_id, asset_group_name, tenant_id) VALUES ('test_id', 'test_name', '"
                + Tenant.DEFAULT_TENANT_UUID
                + "')")
        .executeUpdate();
    Object resultSql =
        entityManager
            .createNativeQuery(
                "SELECT asset_group_dynamic_filter FROM asset_groups WHERE asset_group_id = 'test_id'")
            .getSingleResult();
    entityManager.flush();
    entityManager.clear();

    // --ASSERT--
    // Check default because null in the input
    assertThatJson(response).node("asset_group_dynamic_filter").isEqualTo(resultSql);
  }

  @DisplayName("Given valid AssetGroupInput, should update assetGroup successfully")
  @Test
  @WithMockUser(isAdmin = true)
  void given_validAssetGroupInput_should_updateAssetGroupSuccessfully() throws Exception {
    // --PREPARE--
    AssetGroup input = createDefaultAssetGroup("Asset group");
    AssetGroup assetGroup = assetGroupRepository.save(input);
    String newName = "Asset group updated";
    input.setName(newName);

    // --EXECUTE--
    String response =
        mvc.perform(
                put(ASSET_GROUP_URI + "/" + assetGroup.getId())
                    .content(asJsonString(input))
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .with(csrf()))
            .andExpect(status().is2xxSuccessful())
            .andReturn()
            .getResponse()
            .getContentAsString();

    // --ASSERT--
    assertEquals(newName, JsonPath.read(response, "$.asset_group_name"));
    assertEquals(input.getDescription(), JsonPath.read(response, "$.asset_group_description"));
  }

  @DisplayName(
      "Given valid AssetGroupInput for a nonexistent assetGroup, should return 404 Not Found")
  @Test
  @WithMockUser(isAdmin = true)
  void given_validAssetGroupInputForNonexistentAssetGroup_should_returnNotFound() throws Exception {
    // --PREPARE--
    AssetGroupInput input = createDefaultAssetGroupInput("Asset group updated");
    String nonexistentAssetGroupId = "nonexistent-id";

    // --EXECUTE & ASSERT--
    mvc.perform(
            put(ASSET_GROUP_URI + "/" + nonexistentAssetGroupId)
                .content(asJsonString(input))
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .with(csrf()))
        .andExpect(status().isNotFound());
  }

  @DisplayName("Given existing assetGroup, should delete assetGroup successfully")
  @Test
  @WithMockUser(isAdmin = true)
  void given_existingAssetGroup_should_deleteAssetGroupSuccessfully() throws Exception {
    // --PREPARE--
    AssetGroup assetGroup = assetGroupRepository.save(createDefaultAssetGroup("Asset group"));

    // --EXECUTE--
    mvc.perform(
            delete(ASSET_GROUP_URI + "/" + assetGroup.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .with(csrf()))
        .andExpect(status().is2xxSuccessful());

    // --ASSERT--
    assertTrue(assetGroupRepository.findById(assetGroup.getId()).isEmpty());
  }

  @DisplayName("Given no existing assetGroup, should throw an exception")
  @Test
  @WithMockUser(isAdmin = true)
  void given_notExistingAssetGroup_should_throwAnException() throws Exception {
    // -- PREPARE --
    String nonexistentAssetGroupId = "nonexistent-id";

    // --EXECUTE--
    mvc.perform(
            delete(ASSET_GROUP_URI + "/" + nonexistentAssetGroupId)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .with(csrf()))
        .andExpect(status().is4xxClientError());
  }

  // Options endpoint tests

  private Inject prepareOptionsEndpointTestData() {
    // Teams
    AssetGroup ag1input = createDefaultAssetGroup(ASSET_GROUP_NAME + "1");
    AssetGroup ag1 = this.assetGroupRepository.save(ag1input);
    AssetGroup ag2input = createDefaultAssetGroup(ASSET_GROUP_NAME + "2");
    AssetGroup ag2 = this.assetGroupRepository.save(ag2input);
    AssetGroup ag3input = createDefaultAssetGroup(ASSET_GROUP_NAME + "3");
    AssetGroup ag3 = this.assetGroupRepository.save(ag3input);
    AssetGroup ag4input = createDefaultAssetGroup(ASSET_GROUP_NAME + "4");
    AssetGroup ag4 = this.assetGroupRepository.save(ag4input);
    Exercise exInput = ExerciseFixture.getExercise();
    Exercise exercise = this.exerciseService.createExercise(exInput);
    // Inject
    Inject inject = getDefaultInject();
    inject.setExercise(exercise);
    inject.setAssetGroups(
        new ArrayList<>() {
          {
            add(ag1);
            add(ag2);
            add(ag3);
            add(ag4);
          }
        });
    return this.injectRepository.save(inject);
  }

  Stream<Arguments> optionsByNameTestParameters() {
    return Stream.of(
        Arguments.of(
            null, false, 0), // Case 1: searchText is null and simulationOrScenarioId is null
        Arguments.of(
            ASSET_GROUP_NAME,
            false,
            0), // Case 2: searchText is valid and simulationOrScenarioId is null
        Arguments.of(
            ASSET_GROUP_NAME + "2",
            false,
            0), // Case 2: searchText is valid and simulationOrScenarioId is null
        Arguments.of(
            null, true, 4), // Case 3: searchText is null and simulationOrScenarioId is valid
        Arguments.of(
            ASSET_GROUP_NAME,
            true,
            4), // Case 4: searchText is valid and simulationOrScenarioId is valid
        Arguments.of(
            ASSET_GROUP_NAME + "2",
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
                get(ASSET_GROUP_URI + "/options")
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
  void optionsByIdTest(Integer numberOfAssetGroupsToProvide, Integer expectedNumberOfResults)
      throws Exception {
    // --PREPARE--
    Inject inject = prepareOptionsEndpointTestData();
    List<AssetGroup> assetGroups = inject.getAssetGroups();

    List<String> idsToSearch = new ArrayList<>();
    for (int i = 0; i < numberOfAssetGroupsToProvide; i++) {
      idsToSearch.add(assetGroups.get(i).getId());
    }

    // --EXECUTE--;
    String response =
        mvc.perform(
                post(ASSET_GROUP_URI + "/options")
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

  @Nested
  @DisplayName("GET /api/asset-groups - assets resolution")
  class GetAssetGroupsAssets {

    private Endpoint windowsX86;
    private Endpoint windowsArm;
    private Endpoint linuxX86;

    @BeforeEach
    void setUp() {
      windowsX86 =
          endpointComposer
              .forEndpoint(
                  EndpointFixture.createEndpoint(
                      "win-x86",
                      Endpoint.PLATFORM_TYPE.Windows,
                      Endpoint.PLATFORM_ARCH.x86_64,
                      "win-host-01",
                      new String[] {"10.0.0.1"}))
              .persist()
              .get();
      windowsArm =
          endpointComposer
              .forEndpoint(
                  EndpointFixture.createEndpoint(
                      "win-arm",
                      Endpoint.PLATFORM_TYPE.Windows,
                      Endpoint.PLATFORM_ARCH.arm64,
                      "win-host-02",
                      new String[] {"10.0.0.2"}))
              .persist()
              .get();
      linuxX86 =
          endpointComposer
              .forEndpoint(
                  EndpointFixture.createEndpoint(
                      "linux-x86",
                      Endpoint.PLATFORM_TYPE.Linux,
                      Endpoint.PLATFORM_ARCH.x86_64,
                      "linux-host-01",
                      new String[] {"10.0.1.1"}))
              .persist()
              .get();
    }

    private List<String> getDynamicAssetIds(String response, String groupName) {
      List<Map<String, Object>> groups =
          JsonPath.read(response, "$[?(@.asset_group_name == '" + groupName + "')]");
      assertEquals(1, groups.size());
      return (List<String>) groups.getFirst().get("asset_group_dynamic_assets");
    }

    @DisplayName(
        "Given a static asset group with endpoints, should return asset_group_assets with correct IDs")
    @Test
    @WithMockUser(isAdmin = true)
    void given_staticAssetGroupWithEndpoints_should_returnCorrectAssetIds() throws Exception {
      // Arrange
      EndpointComposer.Composer winComposer = endpointComposer.forEndpoint(windowsX86);
      EndpointComposer.Composer linuxComposer = endpointComposer.forEndpoint(linuxX86);
      assetGroupComposer
          .forAssetGroup(createDefaultAssetGroup("Static group"))
          .withAsset(winComposer)
          .withAsset(linuxComposer)
          .persist();

      // Act
      String response =
          mvc.perform(get(ASSET_GROUP_URI).accept(MediaType.APPLICATION_JSON))
              .andExpect(status().is2xxSuccessful())
              .andReturn()
              .getResponse()
              .getContentAsString();

      // Assert
      List<Map<String, Object>> matchingGroups =
          JsonPath.read(response, "$[?(@.asset_group_name == 'Static group')]");
      assertEquals(1, matchingGroups.size());

      List<String> assetIds = (List<String>) matchingGroups.getFirst().get("asset_group_assets");
      assertEquals(2, assetIds.size());
      assertTrue(assetIds.contains(windowsX86.getId()));
      assertTrue(assetIds.contains(linuxX86.getId()));

      List<String> dynamicAssets =
          (List<String>) matchingGroups.getFirst().get("asset_group_dynamic_assets");
      assertTrue(dynamicAssets.isEmpty());
    }

    @DisplayName(
        "Given an empty static asset group, should return empty asset_group_assets and empty dynamic_assets")
    @Test
    @WithMockUser(isAdmin = true)
    void given_emptyStaticAssetGroup_should_returnEmptyAssets() throws Exception {
      // Arrange
      assetGroupComposer.forAssetGroup(createDefaultAssetGroup("Empty group")).persist();

      // Act
      String response =
          mvc.perform(get(ASSET_GROUP_URI).accept(MediaType.APPLICATION_JSON))
              .andExpect(status().is2xxSuccessful())
              .andReturn()
              .getResponse()
              .getContentAsString();

      // Assert
      List<Map<String, Object>> matchingGroups =
          JsonPath.read(response, "$[?(@.asset_group_name == 'Empty group')]");
      assertEquals(1, matchingGroups.size());

      List<String> staticAssets =
          (List<String>) matchingGroups.getFirst().get("asset_group_assets");
      assertTrue(staticAssets.isEmpty());

      List<String> dynamicAssets =
          (List<String>) matchingGroups.getFirst().get("asset_group_dynamic_assets");
      assertTrue(dynamicAssets.isEmpty());
    }

    @DisplayName("Dynamic filter should return only matching endpoints")
    @ParameterizedTest(name = "Filter on {0} ({1}) with value {2}")
    @MethodSource("dynamicFilterParameters")
    @WithMockUser(isAdmin = true)
    void given_dynamicFilter_should_returnOnlyMatchingEndpoints(
        String filterKey, String operator, List<String> values, List<String> expectedEndpointNames)
        throws Exception {
      // Arrange
      Filters.FilterGroup dynamicFilter =
          Filters.FilterGroup.builder()
              .filters(
                  List.of(
                      Filters.Filter.builder()
                          .key(filterKey)
                          .operator(Filters.FilterOperator.valueOf(operator))
                          .values(values)
                          .build()))
              .build();

      AssetGroup group = createAssetGroupWithDynamicFilter("Dynamic filter group", dynamicFilter);
      assetGroupComposer.forAssetGroup(group).persist();

      // Act
      String response =
          mvc.perform(get(ASSET_GROUP_URI).accept(MediaType.APPLICATION_JSON))
              .andExpect(status().is2xxSuccessful())
              .andReturn()
              .getResponse()
              .getContentAsString();

      // Assert
      List<String> dynamicAssetIds = getDynamicAssetIds(response, "Dynamic filter group");
      Map<String, Endpoint> endpointsByName =
          Map.of("windowsX86", windowsX86, "windowsArm", windowsArm, "linuxX86", linuxX86);
      List<String> expectedIds =
          expectedEndpointNames.stream().map(name -> endpointsByName.get(name).getId()).toList();
      assertEquals(expectedIds.size(), dynamicAssetIds.size());
      assertTrue(dynamicAssetIds.containsAll(expectedIds));
    }

    static Stream<Arguments> dynamicFilterParameters() {
      return Stream.of(
          Arguments.of(
              "endpoint_platform",
              "eq",
              List.of(Endpoint.PLATFORM_TYPE.Windows.name()),
              List.of("windowsX86", "windowsArm")),
          Arguments.of(
              "endpoint_arch",
              "eq",
              List.of(Endpoint.PLATFORM_ARCH.arm64.name()),
              List.of("windowsArm")),
          Arguments.of(
              "endpoint_hostname",
              "contains",
              List.of("win-host"),
              List.of("windowsX86", "windowsArm")),
          Arguments.of("endpoint_ips", "contains", List.of("10.0.1"), List.of("linuxX86")));
    }
  }

  @Nested
  @DisplayName("Tenant Isolation")
  @WithMockUser
  class TenantIsolation {

    @Test
    @Disabled
    @DisplayName("AssetGroup created in tenant X should NOT be readable from tenant Y")
    void given_assetGroupInTenantX_should_notBeReadableFromTenantY() throws Exception {
      // -------- Arrange --------
      Tenant tenantX =
          tenantIsolationHelper.createTenantWithCapabilities(
              "Tenant X", Set.of(Capability.MANAGE_ASSETS, Capability.ACCESS_ASSETS));
      Tenant tenantY =
          tenantIsolationHelper.createTenantWithCapabilities(
              "Tenant Y", Set.of(Capability.ACCESS_ASSETS));

      AssetGroupInput input = createDefaultAssetGroupInput("RLS Isolation Test AssetGroup");

      String createResponse =
          mvc.perform(
                  post("/api/tenants/" + tenantX.getId() + "/asset_groups")
                      .content(asJsonString(input))
                      .contentType(MediaType.APPLICATION_JSON)
                      .accept(MediaType.APPLICATION_JSON)
                      .with(csrf()))
              .andExpect(status().is2xxSuccessful())
              .andReturn()
              .getResponse()
              .getContentAsString();

      String assetGroupId = JsonPath.read(createResponse, "$.asset_group_id");

      // Evict L1 cache so findById() hits the DB
      entityManager.flush();
      entityManager.clear();

      // -------- Act — read from tenant Y (expect 403) --------
      int responseStatus =
          mvc.perform(
                  get("/api/tenants/" + tenantY.getId() + "/asset_groups/" + assetGroupId)
                      .accept(MediaType.APPLICATION_JSON)
                      .with(csrf()))
              .andReturn()
              .getResponse()
              .getStatus();

      // -------- Assert --------
      assertThat(responseStatus).isEqualTo(HttpStatus.NOT_FOUND.value());
    }

    @Test
    @Disabled
    @DisplayName("AssetGroup created in tenant X should be readable from tenant X")
    void given_assetGroupInTenantX_should_beReadableFromTenantX() throws Exception {
      // -------- Arrange --------
      Tenant tenantX =
          tenantIsolationHelper.createTenantWithCapabilities(
              "Tenant X", Set.of(Capability.MANAGE_ASSETS, Capability.ACCESS_ASSETS));

      AssetGroupInput input = createDefaultAssetGroupInput("Same Tenant AssetGroup");

      String createResponse =
          mvc.perform(
                  post("/api/tenants/" + tenantX.getId() + "/asset_groups")
                      .content(asJsonString(input))
                      .contentType(MediaType.APPLICATION_JSON)
                      .accept(MediaType.APPLICATION_JSON)
                      .with(csrf()))
              .andExpect(status().is2xxSuccessful())
              .andReturn()
              .getResponse()
              .getContentAsString();

      String assetGroupId = JsonPath.read(createResponse, "$.asset_group_id");

      // -------- Act & Assert — read from same tenant should succeed --------
      mvc.perform(
              get("/api/tenants/" + tenantX.getId() + "/asset_groups/" + assetGroupId)
                  .accept(MediaType.APPLICATION_JSON)
                  .with(csrf()))
          .andExpect(status().isOk());
    }

    @Test
    @DisplayName("AssetGroup search in tenant Y should NOT return asset groups from tenant X")
    void given_assetGroupInTenantX_should_notAppearInTenantYSearch() throws Exception {
      // -------- Arrange --------
      Tenant tenantX =
          tenantIsolationHelper.createTenantWithCapabilities(
              "Tenant X", Set.of(Capability.MANAGE_ASSETS, Capability.ACCESS_ASSETS));
      Tenant tenantY =
          tenantIsolationHelper.createTenantWithCapabilities(
              "Tenant Y", Set.of(Capability.ACCESS_ASSETS));

      AssetGroupInput input = createDefaultAssetGroupInput("CrossTenantSearchAssetGroup");

      mvc.perform(
              post("/api/tenants/" + tenantX.getId() + "/asset_groups")
                  .content(asJsonString(input))
                  .contentType(MediaType.APPLICATION_JSON)
                  .accept(MediaType.APPLICATION_JSON)
                  .with(csrf()))
          .andExpect(status().is2xxSuccessful());

      // Evict L1 cache
      entityManager.flush();
      entityManager.clear();

      // -------- Act — search from tenant Y --------
      SearchPaginationInput searchInput =
          PaginationFixture.simpleTextSearch("CrossTenantSearchAssetGroup");

      String searchResponse =
          mvc.perform(
                  post("/api/tenants/" + tenantY.getId() + "/asset_groups/search")
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
    @Disabled
    @DisplayName("AssetGroup created in tenant X should NOT be updatable from tenant Y")
    void given_assetGroupInTenantX_should_notBeUpdatableFromTenantY() throws Exception {
      // -------- Arrange --------
      Tenant tenantX =
          tenantIsolationHelper.createTenantWithCapabilities(
              "Tenant X", Set.of(Capability.MANAGE_ASSETS, Capability.ACCESS_ASSETS));
      Tenant tenantY =
          tenantIsolationHelper.createTenantWithCapabilities(
              "Tenant Y", Set.of(Capability.MANAGE_ASSETS, Capability.ACCESS_ASSETS));

      AssetGroupInput input = createDefaultAssetGroupInput("Update Isolation Test");

      String createResponse =
          mvc.perform(
                  post("/api/tenants/" + tenantX.getId() + "/asset_groups")
                      .content(asJsonString(input))
                      .contentType(MediaType.APPLICATION_JSON)
                      .accept(MediaType.APPLICATION_JSON)
                      .with(csrf()))
              .andExpect(status().is2xxSuccessful())
              .andReturn()
              .getResponse()
              .getContentAsString();

      String assetGroupId = JsonPath.read(createResponse, "$.asset_group_id");

      // Evict L1 cache
      entityManager.flush();
      entityManager.clear();

      // -------- Act — update from tenant Y --------
      AssetGroupInput updateInput = createDefaultAssetGroupInput("Hijacked Name");

      int responseStatus =
          mvc.perform(
                  put("/api/tenants/" + tenantY.getId() + "/asset_groups/" + assetGroupId)
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

    @Test
    @Disabled
    @DisplayName("AssetGroup created in tenant X should NOT be deletable from tenant Y")
    void given_assetGroupInTenantX_should_notBeDeletableFromTenantY() throws Exception {
      // -------- Arrange --------
      Tenant tenantX =
          tenantIsolationHelper.createTenantWithCapabilities(
              "Tenant X", Set.of(Capability.MANAGE_ASSETS, Capability.ACCESS_ASSETS));
      Tenant tenantY =
          tenantIsolationHelper.createTenantWithCapabilities(
              "Tenant Y", Set.of(Capability.DELETE_ASSETS, Capability.ACCESS_ASSETS));

      AssetGroupInput input = createDefaultAssetGroupInput("Delete Isolation Test");

      String createResponse =
          mvc.perform(
                  post("/api/tenants/" + tenantX.getId() + "/asset_groups")
                      .content(asJsonString(input))
                      .contentType(MediaType.APPLICATION_JSON)
                      .accept(MediaType.APPLICATION_JSON)
                      .with(csrf()))
              .andExpect(status().is2xxSuccessful())
              .andReturn()
              .getResponse()
              .getContentAsString();

      String assetGroupId = JsonPath.read(createResponse, "$.asset_group_id");

      // Evict L1 cache
      entityManager.flush();
      entityManager.clear();

      // -------- Act — delete from tenant Y --------
      int responseStatus =
          mvc.perform(
                  delete("/api/tenants/" + tenantY.getId() + "/asset_groups/" + assetGroupId)
                      .with(csrf()))
              .andReturn()
              .getResponse()
              .getStatus();

      // -------- Assert --------
      assertThat(responseStatus).isEqualTo(HttpStatus.NOT_FOUND.value());
    }
  }
}
