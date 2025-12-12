package io.openaev.rest.search;

import static io.openaev.search.FullTextSearchApi.GLOBAL_SEARCH_URI;
import static io.openaev.service.UserService.buildAuthenticationToken;
import static io.openaev.utils.JsonUtils.asJsonString;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.IntegrationTest;
import io.openaev.database.model.*;
import io.openaev.database.repository.AssetRepository;
import io.openaev.database.repository.ScenarioRepository;
import io.openaev.search.FullTextSearchApi;
import io.openaev.utils.fixtures.*;
import io.openaev.utils.fixtures.composers.GrantComposer;
import io.openaev.utils.fixtures.composers.GroupComposer;
import io.openaev.utils.fixtures.composers.RoleComposer;
import io.openaev.utils.fixtures.composers.UserComposer;
import io.openaev.utils.mockUser.WithMockUser;
import io.openaev.utils.pagination.SearchPaginationInput;
import java.util.*;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

@Transactional
@TestInstance(PER_CLASS)
public class FullTextSearchTest extends IntegrationTest {

  @Autowired private MockMvc mvc;

  @Autowired private ScenarioRepository scenarioRepository;
  @Autowired private AssetRepository assetRepository;

  @Autowired private UserComposer userComposer;
  @Autowired private GroupComposer groupComposer;
  @Autowired private RoleComposer roleComposer;
  @Autowired private GrantComposer grantComposer;

  private static final List<String> SCENARIO_IDS = new ArrayList<>();
  private static Scenario testScenarioCrisis;
  private static Scenario testScenarioIncident;
  private static Asset assetForTest;

  private User testUser;

  @BeforeAll
  void beforeAll() {
    Scenario scenario1 = ScenarioFixture.createDefaultCrisisScenario();
    testScenarioCrisis = this.scenarioRepository.save(scenario1);
    SCENARIO_IDS.add(testScenarioCrisis.getId());

    Scenario scenario2 = ScenarioFixture.createDefaultIncidentResponseScenario();
    testScenarioIncident = this.scenarioRepository.save(scenario2);
    SCENARIO_IDS.add(testScenarioIncident.getId());

    Asset asset = AssetFixture.createDefaultAsset("Asset for full text search test");
    assetForTest = this.assetRepository.save(asset);
  }

  @AfterAll
  void afterAll() {
    this.scenarioRepository.deleteAllById(SCENARIO_IDS);
    this.assetRepository.delete(assetForTest);
  }

  @AfterEach
  void afterEach() {
    userComposer.reset();
    groupComposer.reset();
    roleComposer.reset();
    grantComposer.reset();
  }

  private static Stream<Arguments> countScenarioTestCases() {
    return Stream.of(
        Arguments.of(
            "scenario", 2, "Full text search 'scenario' returns all scenarios - Admin user"),
        Arguments.of("Crisis", 1, "Full text search 'crisis' returns 1 scenario - Admin user"),
        Arguments.of("test", 0, "Full text search 'test' returns no results - Admin user"));
  }

  @ParameterizedTest(name = "{2}")
  @MethodSource("countScenarioTestCases")
  @WithMockUser(isAdmin = true)
  void given_user_is_admin_search_input_should_return_count_for_all_scenarios(
      String searchTerm, int expectedCount, String testDisplayName) throws Exception {
    // -- PREPARE --
    FullTextSearchApi.SearchTerm term = new FullTextSearchApi.SearchTerm();
    term.setSearchTerm(searchTerm);

    // -- EXECUTE --
    mvc.perform(
            post(GLOBAL_SEARCH_URI)
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJsonString(term)))
        .andExpect(status().is2xxSuccessful())
        .andExpect(jsonPath("$['" + Scenario.class.getName() + "'].count").value(expectedCount));
  }

  private static Stream<Arguments> searchScenarioTestCases() {
    return Stream.of(
        Arguments.of(
            "scenario",
            2,
            List.of(testScenarioCrisis.getId(), testScenarioIncident.getId()),
            "Full text search 'scenario' returns all scenarios - Admin user"),
        Arguments.of(
            "Crisis",
            1,
            List.of(testScenarioCrisis.getId()),
            "Full text search 'crisis' returns 1 scenario - Admin user"),
        Arguments.of(
            "test",
            0,
            List.<String>of(),
            "Full text search 'test' returns no results - Admin user"));
  }

  @ParameterizedTest(name = "{3}")
  @MethodSource("searchScenarioTestCases")
  @WithMockUser(isAdmin = true)
  void given_user_is_admin_search_input_should_return_all_scenarios(
      String searchTerm, int expectedCount, List<String> expectedIds, String testDisplayName)
      throws Exception {
    // -- PREPARE --
    SearchPaginationInput searchPaginationInput =
        PaginationFixture.getDefault().textSearch(searchTerm).build();

    // -- EXECUTE --
    mvc.perform(
            post(GLOBAL_SEARCH_URI + "/" + Scenario.class.getName())
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJsonString(searchPaginationInput)))
        .andExpect(status().is2xxSuccessful())
        .andExpect(jsonPath("$.content.size()").value(expectedCount))
        .andExpect(jsonPath("$.content[*].id", containsInAnyOrder(expectedIds.toArray())));
  }

  private static Stream<Arguments> searchScenarioGrantsTestCases() {
    return Stream.of(
        Arguments.of(
            "scenario",
            2,
            List.of(testScenarioCrisis.getId(), testScenarioIncident.getId()),
            List.of(
                GrantFixture.getGrantForScenario(testScenarioCrisis),
                GrantFixture.getGrantForScenario(testScenarioIncident)),
            "Full text search 'scenario' returns all scenarios - Granted user"),
        Arguments.of(
            "scenario",
            1,
            List.of(testScenarioIncident.getId()),
            List.of(GrantFixture.getGrantForScenario(testScenarioIncident)),
            "Full text search 'scenario' returns 1 scenarios - Partially granted user"),
        Arguments.of(
            "Crisis",
            1,
            List.of(testScenarioCrisis.getId()),
            List.of(
                GrantFixture.getGrantForScenario(testScenarioCrisis),
                GrantFixture.getGrantForScenario(testScenarioIncident)),
            "Full text search 'crisis' returns 1 scenario - Granted user"),
        Arguments.of(
            "Crisis",
            0,
            List.<String>of(),
            List.of(GrantFixture.getGrantForScenario(testScenarioIncident)),
            "Full text search 'crisis' returns 0 scenario - Ungranted user"),
        Arguments.of(
            "Crisis",
            0,
            List.<String>of(),
            List.<Grant>of(),
            "Full text search 'crisis' returns 0 scenario - NO grants"),
        Arguments.of(
            "test",
            0,
            List.<String>of(),
            List.of(
                GrantFixture.getGrantForScenario(testScenarioCrisis),
                GrantFixture.getGrantForScenario(testScenarioIncident)),
            "Full text search 'test' returns no results - Granted user"));
  }

  @ParameterizedTest(name = "{4}")
  @MethodSource("searchScenarioGrantsTestCases")
  @WithMockUser
  void given_user_with_grants_search_input_should_match_grants(
      String searchTerm,
      int expectedCount,
      List<String> expectedIds,
      List<Grant> grants,
      String testDisplayName)
      throws Exception {
    // -- PREPARE --
    SearchPaginationInput searchPaginationInput =
        PaginationFixture.getDefault().textSearch(searchTerm).build();

    grants.forEach(
        grant ->
            addGrantToCurrentUser(
                grant.getGrantResourceType(), grant.getName(), grant.getResourceId()));

    // -- EXECUTE --
    mvc.perform(
            post(GLOBAL_SEARCH_URI + "/" + Scenario.class.getName())
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJsonString(searchPaginationInput)))
        .andExpect(status().is2xxSuccessful())
        .andExpect(jsonPath("$.content.size()").value(expectedCount))
        .andExpect(jsonPath("$.content[*].id", containsInAnyOrder(expectedIds.toArray())));
  }

  private static Stream<Arguments> searchAssetTestCases() {
    return Stream.of(
        Arguments.of(
            "Asset",
            Capability.ACCESS_ASSETS,
            1,
            List.of(assetForTest.getId()),
            "Full text search 'Asset' returns all assets - user with capabilities"),
        Arguments.of(
            "Asset",
            Capability.BYPASS,
            1,
            List.of(assetForTest.getId()),
            "Full text search 'Asset' returns all assets - user with bypass capabilities"),
        Arguments.of(
            "DoesNotExist",
            Capability.ACCESS_ASSETS,
            0,
            List.<String>of(),
            "Full text search 'DoesNotExist' returns 0 asset - user with capabilities"),
        Arguments.of(
            "Asset",
            Capability.ACCESS_ASSESSMENT,
            0,
            List.<String>of(),
            "Full text search 'Asset' returns 0 asset - user with wrong capabilities"),
        Arguments.of(
            "Asset",
            null,
            0,
            List.<String>of(),
            "Full text search 'Asset' returns 0 asset - user with no capabilities"));
  }

  @ParameterizedTest(name = "{4}")
  @MethodSource("searchAssetTestCases")
  void given_user_with_specific_capability_and_search_input_should_return_expected_result(
      String searchTerm,
      Capability capability,
      int expectedCount,
      List<String> expectedIds,
      String testDisplayName)
      throws Exception {
    // -- PREPARE --
    SearchPaginationInput searchPaginationInput =
        PaginationFixture.getDefault().textSearch(searchTerm).build();

    GroupComposer.Composer groupComposed =
        groupComposer
            .forGroup(GroupFixture.createGroup())
            .withRole(
                roleComposer.forRole(
                    RoleFixture.getRole(
                        capability == null ? new HashSet<>() : new HashSet<>(Set.of(capability)))));

    this.testUser =
        userComposer
            .forUser(
                UserFixture.getUser(
                    "Firstname", "Lastname", UUID.randomUUID() + "@unittests.invalid"))
            .withGroup(groupComposed)
            .persist()
            .get();

    Authentication auth = buildAuthenticationToken(this.testUser);

    // -- EXECUTE --
    mvc.perform(
            post(GLOBAL_SEARCH_URI + "/" + Asset.class.getName())
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJsonString(searchPaginationInput))
                .with(authentication(auth)))
        .andExpect(status().is2xxSuccessful())
        .andExpect(jsonPath("$.content.size()").value(expectedCount))
        .andExpect(jsonPath("$.content[*].id", containsInAnyOrder(expectedIds.toArray())));
  }

  @ParameterizedTest(name = "{4}")
  @MethodSource("searchAssetTestCases")
  void given_user_with_specific_capability_and_search_input_should_return_expected_count(
      String searchTerm,
      Capability capability,
      int expectedCount,
      List<String> expectedIds,
      String testDisplayName)
      throws Exception {
    // -- PREPARE --
    FullTextSearchApi.SearchTerm term = new FullTextSearchApi.SearchTerm();
    term.setSearchTerm(searchTerm);

    GroupComposer.Composer groupComposed =
        groupComposer
            .forGroup(GroupFixture.createGroup())
            .withRole(
                roleComposer.forRole(
                    RoleFixture.getRole(
                        capability == null ? new HashSet<>() : new HashSet<>(Set.of(capability)))));

    this.testUser =
        userComposer
            .forUser(
                UserFixture.getUser(
                    "Firstname", "Lastname", UUID.randomUUID() + "@unittests.invalid"))
            .withGroup(groupComposed)
            .persist()
            .get();

    Authentication auth = buildAuthenticationToken(this.testUser);

    // -- EXECUTE --
    ResultActions result =
        mvc.perform(
                post(GLOBAL_SEARCH_URI)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(asJsonString(term))
                    .with(authentication(auth)))
            .andExpect(status().is2xxSuccessful());

    if (expectedCount == 0) {
      // either the key does not exist or the count must be 0
      String responseContent = result.andReturn().getResponse().getContentAsString();
      JsonNode root = new ObjectMapper().readTree(responseContent);
      if (root.has(Asset.class.getName())) {
        result.andExpect(jsonPath("$['" + Asset.class.getName() + "'].count").value(0));
      }
    } else {
      result.andExpect(jsonPath("$['" + Asset.class.getName() + "'].count").value(expectedCount));
    }
  }
}
