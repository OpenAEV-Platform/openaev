package io.openaev.rest.asset_group;

import static io.openaev.rest.asset_group.AssetGroupApi.ASSET_GROUP_URI;
import static io.openaev.utils.JsonUtils.asJsonString;
import static io.openaev.utils.fixtures.AssetGroupFixture.*;
import static io.openaev.utils.fixtures.InjectFixture.getDefaultInject;
import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.openaev.IntegrationTest;
import io.openaev.database.model.*;
import io.openaev.database.repository.AssetGroupRepository;
import io.openaev.database.repository.InjectRepository;
import io.openaev.database.repository.TagRepository;
import io.openaev.rest.asset_group.form.AssetGroupInput;
import io.openaev.rest.exercise.service.ExerciseService;
import io.openaev.utils.fixtures.ExerciseFixture;
import io.openaev.utils.fixtures.TagFixture;
import io.openaev.utils.mockUser.WithMockUser;
import jakarta.persistence.EntityManager;
import jakarta.servlet.ServletException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.json.JSONArray;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
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

  @DisplayName(
      "Given valid AssetGroupInput, should create and get assetGroup without dynamic filter successfully")
  @Test
  @WithMockUser(isAdmin = true)
  void given_validAssetGroupInput_should_createAndGetAssetGroupWithoutDynamicFilterSuccessfully()
      throws Exception {
    // -- PREPARE --
    Tag tag = tagRepository.save(TagFixture.getTag());
    AssetGroupInput assetGroupInput = createAssetGroupWithTags("Asset group", List.of(tag.getId()));
    Filters.FilterGroup filterGroupExpected = Filters.FilterGroup.defaultFilterGroup();

    // --EXECUTE--
    String response =
        mvc.perform(
                post(ASSET_GROUP_URI)
                    .content(asJsonString(assetGroupInput))
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON))
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
                    .accept(MediaType.APPLICATION_JSON))
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
    Filters.FilterGroup dynamicFilter = new Filters.FilterGroup();
    dynamicFilter.setMode(Filters.FilterMode.or);
    Filters.Filter filter = new Filters.Filter();
    filter.setKey("endpoint_platform");
    filter.setMode(Filters.FilterMode.or);
    filter.setOperator(Filters.FilterOperator.eq);
    filter.setValues(List.of("Windows"));
    dynamicFilter.setFilters(List.of(filter));
    AssetGroupInput assetGroupInput =
        createAssetGroupWithDynamicFilters("Asset group", dynamicFilter);

    // --EXECUTE--
    String response =
        mvc.perform(
                post(ASSET_GROUP_URI)
                    .content(asJsonString(assetGroupInput))
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON))
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
                    .accept(MediaType.APPLICATION_JSON))
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
    Tag tag = tagRepository.save(TagFixture.getTag());
    AssetGroupInput assetGroupInput = createAssetGroupWithTags("Asset group", List.of(tag.getId()));
    Filters.FilterGroup filterGroupExpected = Filters.FilterGroup.defaultFilterGroup();

    // --EXECUTE--
    String response =
        mvc.perform(
                post(ASSET_GROUP_URI)
                    .content(asJsonString(assetGroupInput))
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().is2xxSuccessful())
            .andReturn()
            .getResponse()
            .getContentAsString();

    entityManager
        .createNativeQuery(
            "INSERT INTO asset_groups (asset_group_id, asset_group_name) VALUES ('test_id', 'test_name')")
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
                    .accept(MediaType.APPLICATION_JSON))
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
  void given_validAssetGroupInputForNonexistentAssetGroup_should_returnNotFound() {
    // --PREPARE--
    AssetGroup input = createDefaultAssetGroup("Asset group");
    String nonexistentAssetGroupId = "nonexistent-id";
    input.setName("Asset group updated");

    // --EXECUTE--
    assertThrows(
        ServletException.class,
        () ->
            mvc.perform(
                put(ASSET_GROUP_URI + "/" + nonexistentAssetGroupId)
                    .content(asJsonString(input))
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)));
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
                .accept(MediaType.APPLICATION_JSON))
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
                .accept(MediaType.APPLICATION_JSON))
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
                    .queryParam(
                        "simulationOrScenarioId", simulationOrScenarioId ? exercise.getId() : null)
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
                    .accept(MediaType.APPLICATION_JSON))
            .andReturn()
            .getResponse()
            .getContentAsString();

    JSONArray jsonArray = new JSONArray(response);

    // --ASSERT--
    assertEquals(expectedNumberOfResults, jsonArray.length());
  }
}
