package io.openbas.rest.asset_group;

import static io.openbas.rest.asset_group.AssetGroupApi.ASSET_GROUP_URI;
import static io.openbas.utils.JsonUtils.asJsonString;
import static io.openbas.utils.fixtures.AssetGroupFixture.createAssetGroupWithTags;
import static io.openbas.utils.fixtures.AssetGroupFixture.createDefaultAssetGroup;
import static io.openbas.utils.fixtures.InjectFixture.getDefaultInject;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.openbas.IntegrationTest;
import io.openbas.database.model.*;
import io.openbas.database.repository.AssetGroupRepository;
import io.openbas.database.repository.InjectRepository;
import io.openbas.database.repository.TagRepository;
import io.openbas.rest.asset_group.form.AssetGroupInput;
import io.openbas.rest.exercise.service.ExerciseService;
import io.openbas.utils.fixtures.ExerciseFixture;
import io.openbas.utils.fixtures.TagFixture;
import io.openbas.utils.mockUser.WithMockAdminUser;
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

  @DisplayName("Given valid AssetGroupInput, should create assetGroup successfully")
  @Test
  @WithMockAdminUser
  void given_validAssetGroupInput_should_createAssetGroupSuccessfully() throws Exception {
    // -- PREPARE --
    Tag tag = tagRepository.save(TagFixture.getTag());
    AssetGroupInput assetGroupInput = createAssetGroupWithTags("Asset group", List.of(tag.getId()));

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
    assertEquals(assetGroupInput.getName(), JsonPath.read(response, "$.asset_group_name"));
    assertEquals(
        assetGroupInput.getDescription(), JsonPath.read(response, "$.asset_group_description"));
    assertEquals(tag.getId(), JsonPath.read(response, "$.asset_group_tags[0]"));
  }

  @DisplayName("Given valid AssetGroupInput, should update assetGroup successfully")
  @Test
  @WithMockAdminUser
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
  @WithMockAdminUser
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
  @WithMockAdminUser
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
  @WithMockAdminUser
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
  @WithMockAdminUser
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
  @WithMockAdminUser
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
