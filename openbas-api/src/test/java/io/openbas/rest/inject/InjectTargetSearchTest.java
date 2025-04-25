package io.openbas.rest.inject;

import static io.openbas.rest.inject.InjectApi.INJECT_URI;
import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.openbas.IntegrationTest;
import io.openbas.database.model.*;
import io.openbas.database.model.Tag;
import io.openbas.utils.TargetType;
import io.openbas.utils.fixtures.*;
import io.openbas.utils.fixtures.composers.*;
import io.openbas.utils.mockUser.WithMockAdminUser;
import io.openbas.utils.mockUser.WithMockUnprivilegedUser;
import io.openbas.utils.pagination.SearchPaginationInput;
import jakarta.persistence.EntityManager;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@Transactional
@TestInstance(PER_CLASS)
@DisplayName("Searching inject targets tests")
public class InjectTargetSearchTest extends IntegrationTest {
  @Autowired private InjectComposer injectComposer;
  @Autowired private InjectorContractComposer injectContractComposer;
  @Autowired private PayloadComposer payloadComposer;
  @Autowired private AssetGroupComposer assetGroupComposer;
  @Autowired private ExpectationComposer expectationComposer;
  @Autowired private TeamComposer teamComposer;
  @Autowired private MockMvc mvc;
  @Autowired private ObjectMapper mapper;
  @Autowired private EntityManager entityManager;

  @BeforeEach
  public void beforeEach() {
    injectComposer.reset();
    injectContractComposer.reset();
    payloadComposer.reset();
    assetGroupComposer.reset();
    expectationComposer.reset();
  }

  private InjectComposer.Composer getInjectWrapper() {
    return injectComposer
        .forInject(InjectFixture.getInjectWithoutContract())
        .withInjectorContract(
            injectContractComposer
                .forInjectorContract(InjectorContractFixture.createDefaultInjectorContract())
                .withPayload(payloadComposer.forPayload(PayloadFixture.createDefaultCommand())));
  }

  @Nested
  @DisplayName("With asset groups search")
  public class WithAssetGroupsSearch {
    private final TargetType targetType = TargetType.ASSETS_GROUPS;

    @Nested
    @WithMockAdminUser
    @DisplayName("When inject does not exist")
    public class WhenInjectDoesNotExist {
      @Test
      @DisplayName("Returns not found")
      public void whenInjectDoesNotExist_returnNotFound() throws Exception {
        String id = UUID.randomUUID().toString();
        mvc.perform(
                post(INJECT_URI + "/" + id + "/targets/" + targetType + "/search")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(mapper.writeValueAsString(new SearchPaginationInput())))
            .andExpect(status().isNotFound());
      }
    }

    @Nested
    @WithMockAdminUser
    @DisplayName("When target type does not exist")
    public class WhenTargetTypeDoesNotExist {
      @Test
      @DisplayName("Returns bad request")
      public void whenTargetTypeDoesNotExist_returnBadRequest() throws Exception {
        String id = UUID.randomUUID().toString();
        mvc.perform(
                post(INJECT_URI
                        + "/"
                        + id
                        + "/targets/"
                        + "THIS_TARGET_TYPE_DOES_NOT_EXIST"
                        + "/search")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(mapper.writeValueAsString(new SearchPaginationInput())))
            .andExpect(status().isBadRequest());
      }
    }

    @Nested
    @WithMockUnprivilegedUser
    @DisplayName("Without authorisation")
    public class WhenInjectWithoutAuthorisation {
      @Test
      @DisplayName("Returns not found") // obfuscate lacking privs with NOT_FOUND
      public void withoutAuthorisation_returnNotFound() throws Exception {
        String id = UUID.randomUUID().toString();
        mvc.perform(
                post(INJECT_URI + "/" + id + "/targets/" + targetType + "/search")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(mapper.writeValueAsString(new SearchPaginationInput())))
            .andExpect(status().isNotFound());
      }
    }

    @Nested
    @WithMockAdminUser
    @DisplayName("With existing inject")
    public class WithExistingInject {
      private AssetGroupComposer.Composer getAssetGroupComposerWithName(String assetGroupName) {
        return assetGroupComposer.forAssetGroup(
            AssetGroupFixture.createDefaultAssetGroup(assetGroupName));
      }

      @Test
      @DisplayName("With no asset group targets, return no items in page")
      public void withNoAssetGroupTargets_returnNoItemsInPage() throws Exception {
        Inject inject = getInjectWrapper().persist().get();
        entityManager.flush();
        entityManager.clear();

        SearchPaginationInput search =
            PaginationFixture.simpleFilter(
                "target_name", "target asset group", Filters.FilterOperator.eq);
        String response =
            mvc.perform(
                    post(INJECT_URI
                            + "/"
                            + inject.getId()
                            + "/targets/"
                            + targetType.name()
                            + "/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(search)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThatJson(response).node("content").isEqualTo("[]");
      }

      @Test
      @DisplayName("With some asset group targets, return matching items in page 1")
      public void withSomeAssetGroupTargets_returnMatchingItemsInPage1() throws Exception {
        String searchTerm = "asset group target";
        InjectComposer.Composer injectWrapper = getInjectWrapper();
        for (int i = 0; i < 20; i++) {
          injectWrapper.withAssetGroup(getAssetGroupComposerWithName(searchTerm + " " + i));
        }
        Inject inject = injectWrapper.persist().get();
        entityManager.flush();
        entityManager.clear();

        SearchPaginationInput search =
            PaginationFixture.simpleFilter(
                "target_name", searchTerm, Filters.FilterOperator.contains);
        String response =
            mvc.perform(
                    post(INJECT_URI
                            + "/"
                            + inject.getId()
                            + "/targets/"
                            + targetType.name()
                            + "/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(search)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        List<AssetGroupTarget> expected =
            inject.getAssetGroups().stream()
                .sorted(Comparator.comparing(AssetGroup::getName))
                .limit(search.getSize())
                .map(
                    assetGroup ->
                        new AssetGroupTarget(
                            assetGroup.getId(),
                            assetGroup.getName(),
                            assetGroup.getTags().stream()
                                .map(Tag::getId)
                                .collect(Collectors.toSet())))
                .toList();

        assertThatJson(response).node("content").isEqualTo(mapper.writeValueAsString(expected));
      }

      @Test
      @DisplayName("With some asset group targets, return matching items in page 2")
      public void withSomeAssetGroupTargets_returnMatchingItemsInPage2() throws Exception {
        String searchTerm = "asset group target";
        InjectComposer.Composer injectWrapper = getInjectWrapper();
        for (int i = 0; i < 20; i++) {
          injectWrapper.withAssetGroup(getAssetGroupComposerWithName(searchTerm + " " + i));
        }
        Inject inject = injectWrapper.persist().get();
        entityManager.flush();
        entityManager.clear();

        SearchPaginationInput search =
            PaginationFixture.simpleFilter(
                "target_name", searchTerm, Filters.FilterOperator.contains);
        search.setPage(1); // 0-based
        String response =
            mvc.perform(
                    post(INJECT_URI
                            + "/"
                            + inject.getId()
                            + "/targets/"
                            + targetType.name()
                            + "/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(search)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        List<AssetGroupTarget> expected =
            inject.getAssetGroups().stream()
                .sorted(Comparator.comparing(AssetGroup::getName))
                .skip((long) search.getPage() * search.getSize())
                .limit(search.getSize())
                .map(
                    assetGroup ->
                        new AssetGroupTarget(
                            assetGroup.getId(),
                            assetGroup.getName(),
                            assetGroup.getTags().stream()
                                .map(Tag::getId)
                                .collect(Collectors.toSet())))
                .toList();

        assertThatJson(response).node("content").isEqualTo(mapper.writeValueAsString(expected));
      }

      @Nested
      @DisplayName("With actual results")
      public class WithActualResults {
        private ExpectationComposer.Composer getExpectationWrapperWithResult(
            InjectExpectation.EXPECTATION_TYPE type, InjectExpectation.EXPECTATION_STATUS status) {
          return expectationComposer.forExpectation(
              InjectExpectationFixture.createExpectationWithTypeAndStatus(type, status));
        }

        @Test
        @DisplayName("Given specific scores, expectation type results are of correct status")
        public void givenSpecificScores_expectationTypeResultsAreOfCorrectStatus()
            throws Exception {
          String searchTerm = "asset group target";
          InjectComposer.Composer injectWrapper = getInjectWrapper();
          AssetGroupComposer.Composer assetGroupWrapper = getAssetGroupComposerWithName(searchTerm);
          ExpectationComposer.Composer expectationDetectionWrapper =
              getExpectationWrapperWithResult(
                      InjectExpectation.EXPECTATION_TYPE.DETECTION,
                      InjectExpectation.EXPECTATION_STATUS.SUCCESS)
                  .withAssetGroup(assetGroupWrapper);
          ExpectationComposer.Composer expectationPreventionWrapper =
              getExpectationWrapperWithResult(
                      InjectExpectation.EXPECTATION_TYPE.PREVENTION,
                      InjectExpectation.EXPECTATION_STATUS.FAILED)
                  .withAssetGroup(assetGroupWrapper);
          ExpectationComposer.Composer expectationHumanResponseWrapper =
              getExpectationWrapperWithResult(
                      InjectExpectation.EXPECTATION_TYPE.CHALLENGE,
                      InjectExpectation.EXPECTATION_STATUS.PENDING)
                  .withAssetGroup(assetGroupWrapper);
          injectWrapper.withAssetGroup(assetGroupWrapper);
          injectWrapper.withExpectation(expectationDetectionWrapper);
          injectWrapper.withExpectation(expectationPreventionWrapper);
          injectWrapper.withExpectation(expectationHumanResponseWrapper);
          Inject inject = injectWrapper.persist().get();
          entityManager.flush();
          entityManager.clear();

          SearchPaginationInput search =
              PaginationFixture.simpleFilter(
                  "target_name", searchTerm, Filters.FilterOperator.contains);
          String response =
              mvc.perform(
                      post(INJECT_URI
                              + "/"
                              + inject.getId()
                              + "/targets/"
                              + targetType.name()
                              + "/search")
                          .contentType(MediaType.APPLICATION_JSON)
                          .content(mapper.writeValueAsString(search)))
                  .andExpect(status().isOk())
                  .andReturn()
                  .getResponse()
                  .getContentAsString();

          AssetGroupTarget expectedAssetGroup =
              new AssetGroupTarget(
                  assetGroupWrapper.get().getId(),
                  assetGroupWrapper.get().getName(),
                  assetGroupWrapper.get().getTags().stream()
                      .map(Tag::getId)
                      .collect(Collectors.toSet()));
          expectedAssetGroup.setTargetDetectionStatus(InjectExpectation.EXPECTATION_STATUS.SUCCESS);
          expectedAssetGroup.setTargetPreventionStatus(InjectExpectation.EXPECTATION_STATUS.FAILED);
          expectedAssetGroup.setTargetHumanResponseStatus(
              InjectExpectation.EXPECTATION_STATUS.PENDING);
          List<AssetGroupTarget> expected = List.of(expectedAssetGroup);

          assertThatJson(response).node("content").isEqualTo(mapper.writeValueAsString(expected));
        }
      }
    }
  }

  @Nested
  @WithMockAdminUser
  @DisplayName("With teams search")
  public class WithTeamsSearch {

    private final TargetType targetType = TargetType.TEAMS;

    private TeamComposer.Composer getTeamComposerWithName(String teamName) {
      return teamComposer.forTeam(TeamFixture.createTeamWithName(teamName));
    }

    @Test
    @DisplayName("With no team targets, return no items in page")
    public void withNoTeamTargets_returnNoItemsInPage() throws Exception {
      Inject inject = getInjectWrapper().persist().get();
      entityManager.flush();
      entityManager.clear();

      SearchPaginationInput search =
          PaginationFixture.simpleFilter("target_name", "target team", Filters.FilterOperator.eq);
      String response =
          mvc.perform(
                  post(INJECT_URI
                          + "/"
                          + inject.getId()
                          + "/targets/"
                          + targetType.name()
                          + "/search")
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(mapper.writeValueAsString(search)))
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString();

      assertThatJson(response).node("content").isEqualTo("[]");
    }

    @Test
    @DisplayName("With some team targets, return matching items in page 1")
    public void withSomeTeamTargets_returnMatchingItemsInPage1() throws Exception {
      String searchTerm = "team target";
      InjectComposer.Composer injectWrapper = getInjectWrapper();
      for (int i = 0; i < 20; i++) {
        injectWrapper.withTeam(getTeamComposerWithName(searchTerm + " " + i));
      }
      Inject inject = injectWrapper.persist().get();
      entityManager.flush();
      entityManager.clear();

      SearchPaginationInput search =
          PaginationFixture.simpleFilter(
              "target_name", searchTerm, Filters.FilterOperator.contains);
      String response =
          mvc.perform(
                  post(INJECT_URI
                          + "/"
                          + inject.getId()
                          + "/targets/"
                          + targetType.name()
                          + "/search")
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(mapper.writeValueAsString(search)))
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString();

      List<TeamTarget> expected =
          inject.getTeams().stream()
              .sorted(Comparator.comparing(Team::getName))
              .limit(search.getSize())
              .map(
                  team ->
                      new TeamTarget(
                          team.getId(),
                          team.getName(),
                          team.getTags().stream().map(Tag::getId).collect(Collectors.toSet())))
              .toList();

      assertThatJson(response).node("content").isEqualTo(mapper.writeValueAsString(expected));
    }

    @Test
    @DisplayName("With some team targets, return matching items in page 2")
    public void withSomeTeamTargets_returnMatchingItemsInPage2() throws Exception {
      String searchTerm = "team target";
      InjectComposer.Composer injectWrapper = getInjectWrapper();
      for (int i = 0; i < 20; i++) {
        injectWrapper.withTeam(getTeamComposerWithName(searchTerm + " " + i));
      }
      Inject inject = injectWrapper.persist().get();
      entityManager.flush();
      entityManager.clear();

      SearchPaginationInput search =
          PaginationFixture.simpleFilter(
              "target_name", searchTerm, Filters.FilterOperator.contains);
      search.setPage(1); // 0-based
      String response =
          mvc.perform(
                  post(INJECT_URI
                          + "/"
                          + inject.getId()
                          + "/targets/"
                          + targetType.name()
                          + "/search")
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(mapper.writeValueAsString(search)))
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString();

      List<TeamTarget> expected =
          inject.getTeams().stream()
              .sorted(Comparator.comparing(Team::getName))
              .skip((long) search.getPage() * search.getSize())
              .limit(search.getSize())
              .map(
                  team ->
                      new TeamTarget(
                          team.getId(),
                          team.getName(),
                          team.getTags().stream().map(Tag::getId).collect(Collectors.toSet())))
              .toList();

      assertThatJson(response).node("content").isEqualTo(mapper.writeValueAsString(expected));
    }

    @Nested
    @DisplayName("With actual results")
    public class WithActualResults {
      private ExpectationComposer.Composer getExpectationWrapperWithResult(
          InjectExpectation.EXPECTATION_TYPE type, InjectExpectation.EXPECTATION_STATUS status) {
        return expectationComposer.forExpectation(
            InjectExpectationFixture.createExpectationWithTypeAndStatus(type, status));
      }

      @Test
      @DisplayName("Given specific scores, expectation type results are of correct status")
      public void givenSpecificScores_expectationTypeResultsAreOfCorrectStatus() throws Exception {
        String searchTerm = "team target";
        InjectComposer.Composer injectWrapper = getInjectWrapper();
        TeamComposer.Composer teamWrapper = getTeamComposerWithName(searchTerm);
        ExpectationComposer.Composer expectationHumanResponseWrapper =
            getExpectationWrapperWithResult(
                    InjectExpectation.EXPECTATION_TYPE.CHALLENGE,
                    InjectExpectation.EXPECTATION_STATUS.PENDING)
                .withTeam(teamWrapper);
        injectWrapper.withTeam(teamWrapper);
        injectWrapper.withExpectation(expectationHumanResponseWrapper);
        Inject inject = injectWrapper.persist().get();
        entityManager.flush();
        entityManager.clear();

        SearchPaginationInput search =
            PaginationFixture.simpleFilter(
                "target_name", searchTerm, Filters.FilterOperator.contains);
        String response =
            mvc.perform(
                    post(INJECT_URI
                            + "/"
                            + inject.getId()
                            + "/targets/"
                            + targetType.name()
                            + "/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(search)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        TeamTarget expectedTeam =
            new TeamTarget(
                teamWrapper.get().getId(),
                teamWrapper.get().getName(),
                teamWrapper.get().getTags().stream().map(Tag::getId).collect(Collectors.toSet()));
        expectedTeam.setTargetHumanResponseStatus(InjectExpectation.EXPECTATION_STATUS.PENDING);
        List<TeamTarget> expected = List.of(expectedTeam);

        assertThatJson(response).node("content").isEqualTo(mapper.writeValueAsString(expected));
      }
    }
  }
}
