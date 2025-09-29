package io.openbas.rest.inject;

import static io.openbas.rest.inject.InjectApi.INJECT_URI;
import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.openbas.IntegrationTest;
import io.openbas.database.model.*;
import io.openbas.database.model.Tag;
import io.openbas.utils.FilterUtilsJpa;
import io.openbas.utils.TargetType;
import io.openbas.utils.fixtures.*;
import io.openbas.utils.fixtures.composers.*;
import io.openbas.utils.mockUser.WithMockUser;
import io.openbas.utils.pagination.SearchPaginationInput;
import jakarta.persistence.EntityManager;
import java.util.*;
import java.util.stream.Collectors;
import net.javacrumbs.jsonunit.core.Option;
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
  @Autowired private InjectExpectationComposer injectExpectationComposer;
  @Autowired private EndpointComposer endpointComposer;
  @Autowired private TeamComposer teamComposer;
  @Autowired private UserComposer userComposer;
  @Autowired private ExerciseComposer exerciseComposer;
  @Autowired private MockMvc mvc;
  @Autowired private ObjectMapper mapper;
  @Autowired private EntityManager entityManager;
  @Autowired private AgentComposer agentComposer;
  @Autowired private ExecutorComposer executorComposer;
  @Autowired private TagComposer tagComposer;
  @Autowired private ExecutorFixture executorFixture;

  @BeforeEach
  public void beforeEach() {
    injectComposer.reset();
    injectContractComposer.reset();
    payloadComposer.reset();
    assetGroupComposer.reset();
    injectExpectationComposer.reset();
    endpointComposer.reset();
    agentComposer.reset();
    executorComposer.reset();
    tagComposer.reset();
  }

  private InjectComposer.Composer getInjectWrapper() {
    return injectComposer
        .forInject(InjectFixture.getInjectWithoutContract())
        .withInjectorContract(
            injectContractComposer
                .forInjectorContract(InjectorContractFixture.createDefaultInjectorContract())
                .withPayload(payloadComposer.forPayload(PayloadFixture.createDefaultCommand())));
  }

  private AssetGroupComposer.Composer getAssetGroupWrapperWithFilter(Filters.Filter dynamicFilter) {
    AssetGroup dynamicAssetGroup = AssetGroupFixture.createDefaultAssetGroup("Dynamic Asset Group");
    Filters.FilterGroup filterGroup = new Filters.FilterGroup();
    filterGroup.setFilters(List.of(dynamicFilter));
    filterGroup.setMode(Filters.FilterMode.and);
    dynamicAssetGroup.setDynamicFilter(filterGroup);
    return assetGroupComposer.forAssetGroup(dynamicAssetGroup);
  }

  private InjectComposer.Composer getInjectWithAllTeams() {
    return injectComposer
        .forInject(InjectFixture.getInjectWithAllTeams())
        .withInjectorContract(
            injectContractComposer
                .forInjectorContract(InjectorContractFixture.createDefaultInjectorContract())
                .withPayload(payloadComposer.forPayload(PayloadFixture.createDefaultCommand())));
  }

  private ExerciseComposer.Composer getExerciseComposerWithName() {
    return exerciseComposer.forExercise(ExerciseFixture.createDefaultExercise());
  }

  private UserComposer.Composer getPlayerComposerWithName(
      String firstname, String lastname, String email) {
    return userComposer.forUser(UserFixture.getUser(firstname, lastname, email));
  }

  @Nested
  @WithMockUser(isAdmin = true)
  @DisplayName("When inject does not exist")
  public class WhenInjectDoesNotExist {
    @Test
    @DisplayName("Returns not found")
    public void whenInjectDoesNotExist_returnNotFound() throws Exception {
      String id = UUID.randomUUID().toString();
      mvc.perform(
              post(INJECT_URI + "/" + id + "/targets/" + TargetType.ASSETS_GROUPS + "/search")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(mapper.writeValueAsString(new SearchPaginationInput())))
          .andExpect(status().isNotFound());
    }
  }

  @Nested
  @WithMockUser(isAdmin = true)
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
  @WithMockUser
  @DisplayName("Without authorisation")
  public class WhenInjectWithoutAuthorisation {
    @Test
    @DisplayName("Returns not found") // obfuscate lacking privs with NOT_FOUND
    public void withoutAuthorisation_returnNotFound() throws Exception {
      String id = UUID.randomUUID().toString();
      mvc.perform(
              post(INJECT_URI + "/" + id + "/targets/" + TargetType.ASSETS_GROUPS + "/search")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(mapper.writeValueAsString(new SearchPaginationInput())))
          .andExpect(status().isNotFound());
    }
  }

  @Nested
  @WithMockUser(isAdmin = true)
  @DisplayName("With agent search")
  public class WithAgentSearch {
    private final TargetType targetType = TargetType.AGENT;

    @Nested
    @DisplayName("When getting options for inject targets")
    public class WhenGettingOptionsForInjectTargets {
      @Test
      @DisplayName("/options should return all possible targets")
      public void whenEndpointsAreFilters_returnAllPossibleTargets() throws Exception {
        Inject inject = getInjectWrapper().persist().get();

        entityManager.flush();
        entityManager.clear();

        String response =
            mvc.perform(
                    get(INJECT_URI + "/" + inject.getId() + "/targets/" + targetType + "/options")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        List<FilterUtilsJpa.Option> expected = List.of();

        assertThatJson(response).isEqualTo(mapper.writeValueAsString(expected));
      }

      @Test
      @DisplayName("/options by id should return only options matching ids")
      public void whenEndpointsAreFilters_returnOnlyOptionsMatchingIds() throws Exception {
        InjectComposer.Composer injectWrapper = getInjectWrapper();

        // dynamic group 1
        Filters.Filter filter = new Filters.Filter();
        filter.setKey("endpoint_platform");
        filter.setMode(Filters.FilterMode.and);
        filter.setOperator(Filters.FilterOperator.eq);
        filter.setValues(List.of(Endpoint.PLATFORM_TYPE.Windows.name()));
        AssetGroupComposer.Composer assetGroupWrapper = getAssetGroupWrapperWithFilter(filter);

        AgentComposer.Composer agentWrapper =
            agentComposer
                .forAgent(AgentFixture.createDefaultAgentService())
                .withExecutor(executorComposer.forExecutor(executorFixture.getDefaultExecutor()));
        EndpointComposer.Composer ep1Wrapper =
            endpointComposer
                .forEndpoint(EndpointFixture.createEndpoint())
                .withAgent(agentWrapper)
                .persist();

        injectWrapper.withAssetGroup(assetGroupWrapper).persist();

        entityManager.flush();
        entityManager.clear();

        List<String> ids = List.of(agentWrapper.get().getId());

        String response =
            mvc.perform(
                    post(INJECT_URI + "/targets/" + targetType + "/options")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(ids)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        List<FilterUtilsJpa.Option> expected = List.of();

        assertThatJson(response).isEqualTo(mapper.writeValueAsString(expected));
      }
    }

    @Nested
    @DisplayName("With OR group operator")
    public class WithORGroupOperator {
      @Test
      @DisplayName(
          "OR filter group combination should not return agent that is not a target of inject")
      public void orFilterGroupCombinationShouldNotReturnAgentThatIsNotTargetOfInject()
          throws Exception {
        InjectComposer.Composer injectWrapper = getInjectWrapper();

        List<AssetGroupComposer.Composer> assetGroupWrappers = new ArrayList<>();
        Filters.Filter filter = new Filters.Filter();
        filter.setKey("endpoint_platform");
        filter.setMode(Filters.FilterMode.and);
        filter.setOperator(Filters.FilterOperator.eq);
        filter.setValues(List.of(Endpoint.PLATFORM_TYPE.Windows.name()));
        assetGroupWrappers.add(getAssetGroupWrapperWithFilter(filter));

        // create several endpoints; only the Windows (first) endpoint should be involved in the
        // above
        // groups
        AgentComposer.Composer agent1Wrapper =
            agentComposer
                .forAgent(AgentFixture.createDefaultAgentService())
                .withExecutor(executorComposer.forExecutor(executorFixture.getDefaultExecutor()));
        endpointComposer
            .forEndpoint(EndpointFixture.createEndpoint())
            .withAgent(agent1Wrapper)
            .persist();

        // create a new endpoint that is not part of the above groups but will be targeted
        // explicitly
        AgentComposer.Composer agent2Wrapper =
            agentComposer
                .forAgent(AgentFixture.createDefaultAgentService())
                .withExecutor(executorComposer.forExecutor(executorFixture.getDefaultExecutor()));
        Endpoint ep2 = EndpointFixture.createEndpoint();
        ep2.setPlatform(Endpoint.PLATFORM_TYPE.Linux);
        EndpointComposer.Composer ep2Wrapper =
            endpointComposer.forEndpoint(ep2).withAgent(agent2Wrapper).persist();

        // create a new endpoint that is both part of the above groups and also will be targeted
        // explicitly
        AgentComposer.Composer agent3Wrapper =
            agentComposer
                .forAgent(AgentFixture.createDefaultAgentService())
                .withExecutor(executorComposer.forExecutor(executorFixture.getDefaultExecutor()));
        Endpoint ep3 = EndpointFixture.createEndpoint();
        EndpointComposer.Composer ep3Wrapper =
            endpointComposer.forEndpoint(ep3).withAgent(agent3Wrapper).persist();

        // create a new endpoint that is not part of the above groups
        Endpoint notTarget = EndpointFixture.createEndpoint();
        notTarget.setPlatform(Endpoint.PLATFORM_TYPE.MacOS);
        endpointComposer
            .forEndpoint(notTarget)
            .withAgent(
                agentComposer
                    .forAgent(AgentFixture.createDefaultAgentService())
                    .withExecutor(
                        executorComposer.forExecutor(executorFixture.getDefaultExecutor())))
            .persist();

        assetGroupWrappers.forEach(injectWrapper::withAssetGroup);
        injectWrapper.withEndpoint(ep2Wrapper).withEndpoint(ep3Wrapper);
        Inject inject = injectWrapper.persist().get();

        entityManager.flush();
        entityManager.clear();

        SearchPaginationInput search =
            PaginationFixture.simpleSearchWithOrOperator(
                "target_name", "", Filters.FilterOperator.contains);
        // add another filter that would include notTarget endpoint
        Filters.Filter otherFilter = new Filters.Filter();
        otherFilter.setKey("target_endpoint");
        otherFilter.setMode(Filters.FilterMode.and);
        otherFilter.setOperator(Filters.FilterOperator.contains);
        otherFilter.setValues(List.of(notTarget.getId()));
        search.getFilterGroup().getFilters().add(otherFilter);

        String response =
            mvc.perform(
                    post(INJECT_URI + "/" + inject.getId() + "/targets/" + targetType + "/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(search)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        List<AgentTarget> expected =
            List.of(
                new AgentTarget(
                    agent1Wrapper.get().getId(),
                    agent1Wrapper.get().getExecutedByUser(),
                    Set.of(),
                    agent1Wrapper.get().getAsset().getId(),
                    agent1Wrapper.get().getExecutor().getType()),
                new AgentTarget(
                    agent2Wrapper.get().getId(),
                    agent2Wrapper.get().getExecutedByUser(),
                    Set.of(),
                    agent2Wrapper.get().getAsset().getId(),
                    agent2Wrapper.get().getExecutor().getType()),
                new AgentTarget(
                    agent3Wrapper.get().getId(),
                    agent3Wrapper.get().getExecutedByUser(),
                    Set.of(),
                    agent3Wrapper.get().getAsset().getId(),
                    agent3Wrapper.get().getExecutor().getType()));

        assertThatJson(response).node("content").isEqualTo(mapper.writeValueAsString(expected));
      }
    }

    @Nested
    @DisplayName("With AND filter group operator")
    public class WithAndFilterGroupOperator {
      @Test
      @DisplayName(
          "When agent belongs to dynamic asset in group, they are targets of inject if group is a target")
      public void whenAgentBelongsToDynamicAssetInGroup_thenTargetIsDynamicAssetInGroup()
          throws Exception {
        InjectComposer.Composer injectWrapper = getInjectWrapper();

        // dynamic group 1
        Filters.Filter filter = new Filters.Filter();
        filter.setKey("endpoint_platform");
        filter.setMode(Filters.FilterMode.and);
        filter.setOperator(Filters.FilterOperator.eq);
        filter.setValues(List.of(Endpoint.PLATFORM_TYPE.Windows.name()));
        AssetGroupComposer.Composer assetGroupWrapper = getAssetGroupWrapperWithFilter(filter);

        // 2
        Filters.Filter filter2 = new Filters.Filter();
        filter2.setKey("endpoint_platform");
        filter2.setMode(Filters.FilterMode.and);
        filter2.setOperator(Filters.FilterOperator.eq);
        filter2.setValues(List.of(Endpoint.PLATFORM_TYPE.Linux.name()));
        AssetGroupComposer.Composer assetGroupWrapper2 = getAssetGroupWrapperWithFilter(filter2);

        AgentComposer.Composer agent1Wrapper =
            agentComposer
                .forAgent(AgentFixture.createDefaultAgentService())
                .withExecutor(executorComposer.forExecutor(executorFixture.getDefaultExecutor()));
        endpointComposer
            .forEndpoint(EndpointFixture.createEndpoint())
            .withAgent(agent1Wrapper)
            .persist();

        AgentComposer.Composer agent2Wrapper =
            agentComposer
                .forAgent(AgentFixture.createDefaultAgentService())
                .withExecutor(executorComposer.forExecutor(executorFixture.getDefaultExecutor()));
        Endpoint ep2 = EndpointFixture.createEndpoint();
        ep2.setPlatform(Endpoint.PLATFORM_TYPE.Linux);
        endpointComposer.forEndpoint(ep2).withAgent(agent2Wrapper).persist();

        // create a new endpoint that is not part of the above groups
        AgentComposer.Composer agent3Wrapper =
            agentComposer
                .forAgent(AgentFixture.createDefaultAgentService())
                .withExecutor(executorComposer.forExecutor(executorFixture.getDefaultExecutor()));
        Endpoint ep3 = EndpointFixture.createEndpoint();
        ep3.setPlatform(Endpoint.PLATFORM_TYPE.MacOS);
        endpointComposer.forEndpoint(ep3).withAgent(agent3Wrapper).persist();

        injectWrapper
            .withAssetGroup(assetGroupWrapper)
            .withExpectation(
                injectExpectationComposer
                    .forExpectation(
                        InjectExpectationFixture.createExpectationWithTypeAndStatus(
                            InjectExpectation.EXPECTATION_TYPE.DETECTION,
                            InjectExpectation.EXPECTATION_STATUS.SUCCESS))
                    .withAgent(agent1Wrapper))
            .withExpectation(
                injectExpectationComposer
                    .forExpectation(
                        InjectExpectationFixture.createExpectationWithTypeAndStatus(
                            InjectExpectation.EXPECTATION_TYPE.PREVENTION,
                            InjectExpectation.EXPECTATION_STATUS.PARTIAL))
                    .withAgent(agent1Wrapper))
            .withAssetGroup(assetGroupWrapper2)
            .withExpectation(
                injectExpectationComposer
                    .forExpectation(
                        InjectExpectationFixture.createExpectationWithTypeAndStatus(
                            InjectExpectation.EXPECTATION_TYPE.DETECTION,
                            InjectExpectation.EXPECTATION_STATUS.SUCCESS))
                    .withAgent(agent2Wrapper))
            .withExpectation(
                injectExpectationComposer
                    .forExpectation(
                        InjectExpectationFixture.createExpectationWithTypeAndStatus(
                            InjectExpectation.EXPECTATION_TYPE.PREVENTION,
                            InjectExpectation.EXPECTATION_STATUS.PARTIAL))
                    .withAgent(agent2Wrapper));
        Inject inject = injectWrapper.persist().get();

        entityManager.flush();
        entityManager.clear();

        SearchPaginationInput search = PaginationFixture.getDefault().build();

        String response =
            mvc.perform(
                    post(INJECT_URI + "/" + inject.getId() + "/targets/" + targetType + "/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(search)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        AgentTarget expectedTarget1 =
            new AgentTarget(
                agent1Wrapper.get().getId(),
                agent1Wrapper.get().getExecutedByUser(),
                Set.of(),
                agent1Wrapper.get().getAsset().getId(),
                agent1Wrapper.get().getExecutor().getType());
        expectedTarget1.setTargetDetectionStatus(InjectExpectation.EXPECTATION_STATUS.SUCCESS);
        expectedTarget1.setTargetPreventionStatus(InjectExpectation.EXPECTATION_STATUS.PARTIAL);
        AgentTarget expectedTarget2 =
            new AgentTarget(
                agent2Wrapper.get().getId(),
                agent2Wrapper.get().getExecutedByUser(),
                Set.of(),
                agent2Wrapper.get().getAsset().getId(),
                agent2Wrapper.get().getExecutor().getType());
        expectedTarget2.setTargetDetectionStatus(InjectExpectation.EXPECTATION_STATUS.SUCCESS);
        expectedTarget2.setTargetPreventionStatus(InjectExpectation.EXPECTATION_STATUS.PARTIAL);
        // expect two out of three endpoints in the resultset, i.e. not the extra one
        List<AgentTarget> expected = List.of(expectedTarget1, expectedTarget2);

        assertThatJson(response).node("content").isEqualTo(mapper.writeValueAsString(expected));
      }

      @Test
      @DisplayName("When dynamic groups intersect, common agents are returned only once")
      public void whenDynamicGroupsIntersect_thenReturnCommonAgentsOnce() throws Exception {
        InjectComposer.Composer injectWrapper = getInjectWrapper();

        List<AssetGroupComposer.Composer> assetGroupWrappers = new ArrayList<>();

        // create two identical asset groups
        for (int i = 0; i < 2; i++) {
          Filters.Filter filter = new Filters.Filter();
          filter.setKey("endpoint_platform");
          filter.setMode(Filters.FilterMode.and);
          filter.setOperator(Filters.FilterOperator.eq);
          filter.setValues(List.of(Endpoint.PLATFORM_TYPE.Windows.name()));
          assetGroupWrappers.add(getAssetGroupWrapperWithFilter(filter));
        }

        // create several endpoints; only the Windows (first) endpoint should be involved in the
        // above
        // groups
        AgentComposer.Composer agent1Wrapper =
            agentComposer
                .forAgent(AgentFixture.createDefaultAgentService())
                .withExecutor(executorComposer.forExecutor(executorFixture.getDefaultExecutor()));
        endpointComposer
            .forEndpoint(EndpointFixture.createEndpoint())
            .withAgent(agent1Wrapper)
            .persist();

        // create a new endpoint that is not part of the above groups
        AgentComposer.Composer agent2Wrapper =
            agentComposer
                .forAgent(AgentFixture.createDefaultAgentService())
                .withExecutor(executorComposer.forExecutor(executorFixture.getDefaultExecutor()));
        Endpoint ep2 = EndpointFixture.createEndpoint();
        ep2.setPlatform(Endpoint.PLATFORM_TYPE.Linux);
        endpointComposer.forEndpoint(ep2).withAgent(agent2Wrapper).persist();

        // create a new endpoint that is not part of the above groups
        AgentComposer.Composer agent3Wrapper =
            agentComposer
                .forAgent(AgentFixture.createDefaultAgentService())
                .withExecutor(executorComposer.forExecutor(executorFixture.getDefaultExecutor()));
        Endpoint ep3 = EndpointFixture.createEndpoint();
        ep3.setPlatform(Endpoint.PLATFORM_TYPE.MacOS);
        endpointComposer.forEndpoint(ep3).withAgent(agent3Wrapper).persist();

        assetGroupWrappers.forEach(injectWrapper::withAssetGroup);
        Inject inject = injectWrapper.persist().get();

        entityManager.flush();
        entityManager.clear();

        SearchPaginationInput search =
            PaginationFixture.simpleSearchWithAndOperator(
                "target_name", "", Filters.FilterOperator.contains);

        String response =
            mvc.perform(
                    post(INJECT_URI + "/" + inject.getId() + "/targets/" + targetType + "/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(search)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        AgentTarget expectedTarget =
            new AgentTarget(
                agent1Wrapper.get().getId(),
                agent1Wrapper.get().getExecutedByUser(),
                Set.of(),
                agent1Wrapper.get().getAsset().getId(),
                agent1Wrapper.get().getExecutor().getType());
        List<AgentTarget> expected = List.of(expectedTarget);

        assertThatJson(response).node("content").isEqualTo(mapper.writeValueAsString(expected));
      }

      @Test
      @DisplayName(
          "When no applied AssetGroup filter, at most a full page of targeted agents should be returned")
      public void whenNoAppliedAssetGroupFilter_returnPageOfAllTargetedAgents() throws Exception {
        InjectComposer.Composer injectWrapper = getInjectWrapper();

        List<AssetGroupComposer.Composer> assetGroupWrappers = new ArrayList<>();
        Filters.Filter filter = new Filters.Filter();
        filter.setKey("endpoint_platform");
        filter.setMode(Filters.FilterMode.and);
        filter.setOperator(Filters.FilterOperator.eq);
        filter.setValues(List.of(Endpoint.PLATFORM_TYPE.Windows.name()));
        assetGroupWrappers.add(getAssetGroupWrapperWithFilter(filter));

        // create several endpoints; only the Windows (first) endpoint should be involved in the
        // above
        // groups
        AgentComposer.Composer agent1Wrapper =
            agentComposer
                .forAgent(AgentFixture.createDefaultAgentService())
                .withExecutor(executorComposer.forExecutor(executorFixture.getDefaultExecutor()));
        endpointComposer
            .forEndpoint(EndpointFixture.createEndpoint())
            .withAgent(agent1Wrapper)
            .persist();

        // create a new endpoint that is not part of the above groups but will be targeted
        // explicitly
        AgentComposer.Composer agent2Wrapper =
            agentComposer
                .forAgent(AgentFixture.createDefaultAgentService())
                .withExecutor(executorComposer.forExecutor(executorFixture.getDefaultExecutor()));
        Endpoint ep2 = EndpointFixture.createEndpoint();
        ep2.setPlatform(Endpoint.PLATFORM_TYPE.Linux);
        EndpointComposer.Composer ep2Wrapper =
            endpointComposer.forEndpoint(ep2).withAgent(agent2Wrapper).persist();

        // create a new endpoint that is both part of the above groups and also will be targeted
        // explicitly
        AgentComposer.Composer agent3Wrapper =
            agentComposer
                .forAgent(AgentFixture.createDefaultAgentService())
                .withExecutor(executorComposer.forExecutor(executorFixture.getDefaultExecutor()));
        Endpoint ep3 = EndpointFixture.createEndpoint();
        EndpointComposer.Composer ep3Wrapper =
            endpointComposer.forEndpoint(ep3).withAgent(agent3Wrapper).persist();

        // create a new endpoint that is not part of the above groups
        Endpoint notTarget = EndpointFixture.createEndpoint();
        notTarget.setPlatform(Endpoint.PLATFORM_TYPE.MacOS);
        endpointComposer
            .forEndpoint(notTarget)
            .withAgent(
                agentComposer
                    .forAgent(AgentFixture.createDefaultAgentService())
                    .withExecutor(
                        executorComposer.forExecutor(executorFixture.getDefaultExecutor())))
            .persist();

        assetGroupWrappers.forEach(injectWrapper::withAssetGroup);
        injectWrapper.withEndpoint(ep2Wrapper).withEndpoint(ep3Wrapper);
        Inject inject = injectWrapper.persist().get();

        entityManager.flush();
        entityManager.clear();

        SearchPaginationInput search = PaginationFixture.getDefault().build();

        String response =
            mvc.perform(
                    post(INJECT_URI + "/" + inject.getId() + "/targets/" + targetType + "/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(search)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        List<AgentTarget> expected =
            List.of(
                new AgentTarget(
                    agent1Wrapper.get().getId(),
                    agent1Wrapper.get().getExecutedByUser(),
                    Set.of(),
                    agent1Wrapper.get().getAsset().getId(),
                    agent1Wrapper.get().getExecutor().getType()),
                new AgentTarget(
                    agent2Wrapper.get().getId(),
                    agent2Wrapper.get().getExecutedByUser(),
                    Set.of(),
                    agent2Wrapper.get().getAsset().getId(),
                    agent2Wrapper.get().getExecutor().getType()),
                new AgentTarget(
                    agent3Wrapper.get().getId(),
                    agent3Wrapper.get().getExecutedByUser(),
                    Set.of(),
                    agent3Wrapper.get().getAsset().getId(),
                    agent3Wrapper.get().getExecutor().getType()));

        assertThatJson(response).node("content").isEqualTo(mapper.writeValueAsString(expected));
      }

      @Test
      @DisplayName(
          "When endpoints are explicitly targeted, 'is empty' Asset group filter should only return agents from endpoints that are not in any group'")
      public void whenEndpointsAreExplicitlyTargeted_thenReturnOnlyAgentsFromEndpointsWithNoGroup()
          throws Exception {
        InjectComposer.Composer injectWrapper = getInjectWrapper();

        List<AssetGroupComposer.Composer> assetGroupWrappers = new ArrayList<>();
        Filters.Filter filter = new Filters.Filter();
        filter.setKey("endpoint_platform");
        filter.setMode(Filters.FilterMode.and);
        filter.setOperator(Filters.FilterOperator.eq);
        filter.setValues(List.of(Endpoint.PLATFORM_TYPE.Windows.name()));
        assetGroupWrappers.add(getAssetGroupWrapperWithFilter(filter));

        // create several endpoints; only the Windows (first) endpoint should be involved in the
        // above
        // groups
        endpointComposer
            .forEndpoint(EndpointFixture.createEndpoint())
            .withAgent(
                agentComposer
                    .forAgent(AgentFixture.createDefaultAgentService())
                    .withExecutor(
                        executorComposer.forExecutor(executorFixture.getDefaultExecutor())))
            .persist();

        // create a new endpoint that is not part of the above groups but will be targeted
        // explicitly
        AgentComposer.Composer agent2Wrapper =
            agentComposer
                .forAgent(AgentFixture.createDefaultAgentService())
                .withExecutor(executorComposer.forExecutor(executorFixture.getDefaultExecutor()));
        Endpoint ep2 = EndpointFixture.createEndpoint();
        ep2.setPlatform(Endpoint.PLATFORM_TYPE.Linux);
        EndpointComposer.Composer ep2Wrapper =
            endpointComposer.forEndpoint(ep2).withAgent(agent2Wrapper).persist();

        // create a new endpoint that is both part of the above groups and also will be targeted
        // explicitly
        AgentComposer.Composer agent3Wrapper =
            agentComposer
                .forAgent(AgentFixture.createDefaultAgentService())
                .withExecutor(executorComposer.forExecutor(executorFixture.getDefaultExecutor()));
        Endpoint ep3 = EndpointFixture.createEndpoint();
        EndpointComposer.Composer ep3Wrapper =
            endpointComposer.forEndpoint(ep3).withAgent(agent3Wrapper).persist();

        // create a new endpoint that is not part of the above groups
        Endpoint notTarget = EndpointFixture.createEndpoint();
        notTarget.setPlatform(Endpoint.PLATFORM_TYPE.MacOS);
        endpointComposer
            .forEndpoint(notTarget)
            .withAgent(
                agentComposer
                    .forAgent(AgentFixture.createDefaultAgentService())
                    .withExecutor(
                        executorComposer.forExecutor(executorFixture.getDefaultExecutor())))
            .persist();

        assetGroupWrappers.forEach(injectWrapper::withAssetGroup);
        injectWrapper.withEndpoint(ep2Wrapper).withEndpoint(ep3Wrapper);
        Inject inject = injectWrapper.persist().get();

        entityManager.flush();
        entityManager.clear();

        SearchPaginationInput search =
            PaginationFixture.simpleSearchWithAndOperator(
                "target_asset_groups", null, Filters.FilterOperator.empty);

        String response =
            mvc.perform(
                    post(INJECT_URI + "/" + inject.getId() + "/targets/" + targetType + "/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(search)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        AgentTarget expectedTarget =
            new AgentTarget(
                agent2Wrapper.get().getId(),
                agent2Wrapper.get().getExecutedByUser(),
                Set.of(),
                agent2Wrapper.get().getAsset().getId(),
                agent2Wrapper.get().getExecutor().getType());
        List<AgentTarget> expected = List.of(expectedTarget);

        assertThatJson(response).node("content").isEqualTo(mapper.writeValueAsString(expected));
      }

      @Test
      @DisplayName(
          "When endpoints are explicitly targeted, 'not empty' Asset group filter should only return agents that belong to endpoints in any group'")
      public void whenEndpointsAreExplicitlyTargeted_thenReturnOnlyAgentsFromEndpointsWithGroup()
          throws Exception {
        InjectComposer.Composer injectWrapper = getInjectWrapper();

        List<AssetGroupComposer.Composer> assetGroupWrappers = new ArrayList<>();
        Filters.Filter filter = new Filters.Filter();
        filter.setKey("endpoint_platform");
        filter.setMode(Filters.FilterMode.and);
        filter.setOperator(Filters.FilterOperator.eq);
        filter.setValues(List.of(Endpoint.PLATFORM_TYPE.Windows.name()));
        assetGroupWrappers.add(getAssetGroupWrapperWithFilter(filter));

        // create several endpoints; only the Windows (first) endpoint should be involved in the
        // above
        // groups
        AgentComposer.Composer agent1Wrapper =
            agentComposer
                .forAgent(AgentFixture.createDefaultAgentService())
                .withExecutor(executorComposer.forExecutor(executorFixture.getDefaultExecutor()));
        endpointComposer
            .forEndpoint(EndpointFixture.createEndpoint())
            .withAgent(agent1Wrapper)
            .persist();

        // create a new endpoint that is not part of the above groups but will be targeted
        // explicitly
        AgentComposer.Composer agent2Wrapper =
            agentComposer
                .forAgent(AgentFixture.createDefaultAgentService())
                .withExecutor(executorComposer.forExecutor(executorFixture.getDefaultExecutor()));
        Endpoint ep2 = EndpointFixture.createEndpoint();
        ep2.setPlatform(Endpoint.PLATFORM_TYPE.Linux);
        EndpointComposer.Composer ep2Wrapper =
            endpointComposer.forEndpoint(ep2).withAgent(agent2Wrapper).persist();

        // create a new endpoint that is not part of the above groups
        AgentComposer.Composer agent3Wrapper =
            agentComposer
                .forAgent(AgentFixture.createDefaultAgentService())
                .withExecutor(executorComposer.forExecutor(executorFixture.getDefaultExecutor()));
        Endpoint notTarget = EndpointFixture.createEndpoint();
        notTarget.setPlatform(Endpoint.PLATFORM_TYPE.MacOS);
        endpointComposer.forEndpoint(notTarget).withAgent(agent3Wrapper).persist();

        assetGroupWrappers.forEach(injectWrapper::withAssetGroup);
        injectWrapper.withEndpoint(ep2Wrapper);
        Inject inject = injectWrapper.persist().get();

        entityManager.flush();
        entityManager.clear();

        SearchPaginationInput search =
            PaginationFixture.simpleSearchWithAndOperator(
                "target_asset_groups", null, Filters.FilterOperator.not_empty);

        String response =
            mvc.perform(
                    post(INJECT_URI + "/" + inject.getId() + "/targets/" + targetType + "/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(search)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        AgentTarget expectedTarget =
            new AgentTarget(
                agent1Wrapper.get().getId(),
                agent1Wrapper.get().getExecutedByUser(),
                Set.of(),
                agent1Wrapper.get().getAsset().getId(),
                agent1Wrapper.get().getExecutor().getType());
        List<AgentTarget> expected = List.of(expectedTarget);

        assertThatJson(response).node("content").isEqualTo(mapper.writeValueAsString(expected));
      }

      @Test
      @DisplayName(
          "When are part of a group, 'not contains' operator on this group should exclude agents of member endpoints")
      public void whenArePartOfAGroupAndGroupFilteredOut_thenExcludeAllAgentsFromEndpointsOfGroup()
          throws Exception {
        InjectComposer.Composer injectWrapper = getInjectWrapper();

        List<AssetGroupComposer.Composer> assetGroupWrappers = new ArrayList<>();
        Filters.Filter filter = new Filters.Filter();
        filter.setKey("endpoint_platform");
        filter.setMode(Filters.FilterMode.and);
        filter.setOperator(Filters.FilterOperator.eq);
        filter.setValues(List.of(Endpoint.PLATFORM_TYPE.Windows.name()));
        assetGroupWrappers.add(getAssetGroupWrapperWithFilter(filter));

        // create several endpoints; only the Windows (first) endpoint should be involved in the
        // above
        // groups
        endpointComposer
            .forEndpoint(EndpointFixture.createEndpoint())
            .withAgent(
                agentComposer
                    .forAgent(AgentFixture.createDefaultAgentService())
                    .withExecutor(
                        executorComposer.forExecutor(executorFixture.getDefaultExecutor())))
            .persist();

        // create a new endpoint that is not part of the above groups but will be targeted
        // explicitly
        AgentComposer.Composer agent2Wrapper =
            agentComposer
                .forAgent(AgentFixture.createDefaultAgentService())
                .withExecutor(executorComposer.forExecutor(executorFixture.getDefaultExecutor()));
        Endpoint ep2 = EndpointFixture.createEndpoint();
        ep2.setPlatform(Endpoint.PLATFORM_TYPE.Linux);
        EndpointComposer.Composer ep2Wrapper =
            endpointComposer.forEndpoint(ep2).withAgent(agent2Wrapper).persist();

        // create a new endpoint that is not part of the above groups
        AgentComposer.Composer agent3Wrapper =
            agentComposer
                .forAgent(AgentFixture.createDefaultAgentService())
                .withExecutor(executorComposer.forExecutor(executorFixture.getDefaultExecutor()));
        Endpoint ep3 = EndpointFixture.createEndpoint();
        ep3.setPlatform(Endpoint.PLATFORM_TYPE.MacOS);
        endpointComposer.forEndpoint(ep3).withAgent(agent3Wrapper).persist();

        assetGroupWrappers.forEach(injectWrapper::withAssetGroup);
        Inject inject = injectWrapper.withEndpoint(ep2Wrapper).persist().get();

        entityManager.flush();
        entityManager.clear();

        SearchPaginationInput search =
            PaginationFixture.simpleSearchWithAndOperator(
                "target_asset_groups",
                assetGroupWrappers.getFirst().get().getId(),
                Filters.FilterOperator.not_contains);

        String response =
            mvc.perform(
                    post(INJECT_URI + "/" + inject.getId() + "/targets/" + targetType + "/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(search)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        AgentTarget expectedTarget =
            new AgentTarget(
                agent2Wrapper.get().getId(),
                agent2Wrapper.get().getExecutedByUser(),
                Set.of(),
                agent2Wrapper.get().getAsset().getId(),
                agent2Wrapper.get().getExecutor().getType());
        List<AgentTarget> expected = List.of(expectedTarget);

        assertThatJson(response).node("content").isEqualTo(mapper.writeValueAsString(expected));
      }

      @Test
      @DisplayName(
          "When are part of a group, 'contains' operator on this group should include agents from endpoints of this group and exclude others")
      public void
          whenArePartOfAGroupAndGroupSelectedAsFilter_thenIncludeOnlyAgentsFromEndpointsOfGroup()
              throws Exception {
        InjectComposer.Composer injectWrapper = getInjectWrapper();

        List<AssetGroupComposer.Composer> assetGroupWrappers = new ArrayList<>();
        Filters.Filter filter = new Filters.Filter();
        filter.setKey("endpoint_platform");
        filter.setMode(Filters.FilterMode.and);
        filter.setOperator(Filters.FilterOperator.eq);
        filter.setValues(List.of(Endpoint.PLATFORM_TYPE.Windows.name()));
        assetGroupWrappers.add(getAssetGroupWrapperWithFilter(filter));

        // create several endpoints; only the Windows (first) endpoint should be involved in the
        // above
        // groups
        AgentComposer.Composer agent1Wrapper =
            agentComposer
                .forAgent(AgentFixture.createDefaultAgentService())
                .withExecutor(executorComposer.forExecutor(executorFixture.getDefaultExecutor()));
        EndpointComposer.Composer ep1Wrapper =
            endpointComposer
                .forEndpoint(EndpointFixture.createEndpoint())
                .withAgent(agent1Wrapper)
                .persist();

        // create a new endpoint that is not part of the above groups but will be targeted
        // explicitly
        AgentComposer.Composer agent2Wrapper =
            agentComposer
                .forAgent(AgentFixture.createDefaultAgentService())
                .withExecutor(executorComposer.forExecutor(executorFixture.getDefaultExecutor()));
        Endpoint ep2 = EndpointFixture.createEndpoint();
        ep2.setPlatform(Endpoint.PLATFORM_TYPE.Linux);
        EndpointComposer.Composer ep2Wrapper =
            endpointComposer.forEndpoint(ep2).withAgent(agent2Wrapper).persist();

        // create a new endpoint that is not part of the above groups
        AgentComposer.Composer agent3Wrapper =
            agentComposer
                .forAgent(AgentFixture.createDefaultAgentService())
                .withExecutor(executorComposer.forExecutor(executorFixture.getDefaultExecutor()));
        Endpoint ep3 = EndpointFixture.createEndpoint();
        ep3.setPlatform(Endpoint.PLATFORM_TYPE.MacOS);
        endpointComposer.forEndpoint(ep3).withAgent(agent3Wrapper).persist();

        assetGroupWrappers.forEach(injectWrapper::withAssetGroup);
        // add two endpoints as direct targets, including the endpoint part of the excluded group
        injectWrapper.withEndpoint(ep2Wrapper).withEndpoint(ep1Wrapper);
        Inject inject = injectWrapper.persist().get();

        entityManager.flush();
        entityManager.clear();

        SearchPaginationInput search =
            PaginationFixture.simpleSearchWithAndOperator(
                "target_asset_groups",
                assetGroupWrappers.getFirst().get().getId(),
                Filters.FilterOperator.contains);

        String response =
            mvc.perform(
                    post(INJECT_URI + "/" + inject.getId() + "/targets/" + targetType + "/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(search)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        AgentTarget expectedTarget =
            new AgentTarget(
                agent1Wrapper.get().getId(),
                agent1Wrapper.get().getExecutedByUser(),
                Set.of(),
                agent1Wrapper.get().getAsset().getId(),
                agent1Wrapper.get().getExecutor().getType());
        List<AgentTarget> expected = List.of(expectedTarget);

        assertThatJson(response).node("content").isEqualTo(mapper.writeValueAsString(expected));
      }

      @Nested
      @DisplayName("When filtering with tags")
      public class WhenFilteringWithTags {
        @Test
        @DisplayName(
            "'contains' tag filter should only return agents that belong to endpoints with specified tags'")
        public void
            whenEndpointsAreExplicitlyTargeted_thenReturnOnlyAgentsFromEndpointsWithSpecifiedTags()
                throws Exception {
          InjectComposer.Composer injectWrapper = getInjectWrapper();

          List<AssetGroupComposer.Composer> assetGroupWrappers = new ArrayList<>();
          Filters.Filter filter = new Filters.Filter();
          filter.setKey("endpoint_platform");
          filter.setMode(Filters.FilterMode.and);
          filter.setOperator(Filters.FilterOperator.eq);
          filter.setValues(List.of(Endpoint.PLATFORM_TYPE.Windows.name()));
          assetGroupWrappers.add(getAssetGroupWrapperWithFilter(filter));

          // create several endpoints; only the Windows (first) endpoint should be involved in the
          // above
          // groups
          TagComposer.Composer tagWrapper =
              tagComposer.forTag(TagFixture.getTagWithText("search_this_tag"));
          AgentComposer.Composer agent1Wrapper =
              agentComposer
                  .forAgent(AgentFixture.createDefaultAgentService())
                  .withExecutor(executorComposer.forExecutor(executorFixture.getDefaultExecutor()));
          endpointComposer
              .forEndpoint(EndpointFixture.createEndpoint())
              .withTag(tagWrapper)
              .withAgent(agent1Wrapper)
              .persist();

          // create a new endpoint that is not part of the above groups but will be targeted
          // explicitly
          AgentComposer.Composer agent2Wrapper =
              agentComposer
                  .forAgent(AgentFixture.createDefaultAgentService())
                  .withExecutor(executorComposer.forExecutor(executorFixture.getDefaultExecutor()));
          Endpoint ep2 = EndpointFixture.createEndpoint();
          ep2.setPlatform(Endpoint.PLATFORM_TYPE.Linux);
          EndpointComposer.Composer ep2Wrapper =
              endpointComposer.forEndpoint(ep2).withAgent(agent2Wrapper).persist();

          // create a new endpoint that is not part of the above groups
          AgentComposer.Composer agent3Wrapper =
              agentComposer
                  .forAgent(AgentFixture.createDefaultAgentService())
                  .withExecutor(executorComposer.forExecutor(executorFixture.getDefaultExecutor()));
          Endpoint notTarget = EndpointFixture.createEndpoint();
          notTarget.setPlatform(Endpoint.PLATFORM_TYPE.MacOS);
          endpointComposer.forEndpoint(notTarget).withAgent(agent3Wrapper).persist();

          assetGroupWrappers.forEach(injectWrapper::withAssetGroup);
          injectWrapper.withEndpoint(ep2Wrapper);
          Inject inject = injectWrapper.persist().get();

          entityManager.flush();
          entityManager.clear();

          SearchPaginationInput search =
              PaginationFixture.simpleSearchWithAndOperator(
                  "target_tags", tagWrapper.get().getId(), Filters.FilterOperator.contains);

          String response =
              mvc.perform(
                      post(INJECT_URI + "/" + inject.getId() + "/targets/" + targetType + "/search")
                          .contentType(MediaType.APPLICATION_JSON)
                          .content(mapper.writeValueAsString(search)))
                  .andExpect(status().isOk())
                  .andReturn()
                  .getResponse()
                  .getContentAsString();

          AgentTarget expectedTarget =
              new AgentTarget(
                  agent1Wrapper.get().getId(),
                  agent1Wrapper.get().getExecutedByUser(),
                  Set.of(),
                  agent1Wrapper.get().getAsset().getId(),
                  agent1Wrapper.get().getExecutor().getType());
          List<AgentTarget> expected = List.of(expectedTarget);

          assertThatJson(response).node("content").isEqualTo(mapper.writeValueAsString(expected));
        }

        @Test
        @DisplayName(
            "'not_contains' tag filter should only return agents that don't belong to endpoints with specified tags'")
        public void
            whenEndpointsAreExplicitlyTargeted_thenReturnOnlyAgentsNotFromEndpointsWithSpecifiedTags()
                throws Exception {
          InjectComposer.Composer injectWrapper = getInjectWrapper();

          List<AssetGroupComposer.Composer> assetGroupWrappers = new ArrayList<>();
          Filters.Filter filter = new Filters.Filter();
          filter.setKey("endpoint_platform");
          filter.setMode(Filters.FilterMode.and);
          filter.setOperator(Filters.FilterOperator.eq);
          filter.setValues(List.of(Endpoint.PLATFORM_TYPE.Windows.name()));
          assetGroupWrappers.add(getAssetGroupWrapperWithFilter(filter));

          // create several endpoints; only the Windows (first) endpoint should be involved in the
          // above
          // groups
          TagComposer.Composer tagWrapper =
              tagComposer.forTag(TagFixture.getTagWithText("search_this_tag"));
          AgentComposer.Composer agent1Wrapper =
              agentComposer
                  .forAgent(AgentFixture.createDefaultAgentService())
                  .withExecutor(executorComposer.forExecutor(executorFixture.getDefaultExecutor()));
          endpointComposer
              .forEndpoint(EndpointFixture.createEndpoint())
              .withTag(tagWrapper)
              .withAgent(agent1Wrapper)
              .persist();

          // create a new endpoint that is not part of the above groups but will be targeted
          // explicitly
          AgentComposer.Composer agent2Wrapper =
              agentComposer
                  .forAgent(AgentFixture.createDefaultAgentService())
                  .withExecutor(executorComposer.forExecutor(executorFixture.getDefaultExecutor()));
          Endpoint ep2 = EndpointFixture.createEndpoint();
          ep2.setPlatform(Endpoint.PLATFORM_TYPE.Linux);
          EndpointComposer.Composer ep2Wrapper =
              endpointComposer.forEndpoint(ep2).withAgent(agent2Wrapper).persist();

          // create a new endpoint that is not part of the above groups
          AgentComposer.Composer agent3Wrapper =
              agentComposer
                  .forAgent(AgentFixture.createDefaultAgentService())
                  .withExecutor(executorComposer.forExecutor(executorFixture.getDefaultExecutor()));
          Endpoint notTarget = EndpointFixture.createEndpoint();
          notTarget.setPlatform(Endpoint.PLATFORM_TYPE.MacOS);
          endpointComposer.forEndpoint(notTarget).withAgent(agent3Wrapper).persist();

          assetGroupWrappers.forEach(injectWrapper::withAssetGroup);
          injectWrapper.withEndpoint(ep2Wrapper);
          Inject inject = injectWrapper.persist().get();

          entityManager.flush();
          entityManager.clear();

          SearchPaginationInput search =
              PaginationFixture.simpleSearchWithAndOperator(
                  "target_tags", tagWrapper.get().getId(), Filters.FilterOperator.not_contains);

          String response =
              mvc.perform(
                      post(INJECT_URI + "/" + inject.getId() + "/targets/" + targetType + "/search")
                          .contentType(MediaType.APPLICATION_JSON)
                          .content(mapper.writeValueAsString(search)))
                  .andExpect(status().isOk())
                  .andReturn()
                  .getResponse()
                  .getContentAsString();

          AgentTarget expectedTarget =
              new AgentTarget(
                  agent2Wrapper.get().getId(),
                  agent2Wrapper.get().getExecutedByUser(),
                  Set.of(),
                  agent2Wrapper.get().getAsset().getId(),
                  agent2Wrapper.get().getExecutor().getType());
          List<AgentTarget> expected = List.of(expectedTarget);

          assertThatJson(response).node("content").isEqualTo(mapper.writeValueAsString(expected));
        }

        @Test
        @DisplayName(
            "'empty' tag filter should only return agents that belong to endpoints with no tags'")
        public void whenEndpointsAreExplicitlyTargeted_thenReturnOnlyAgentsFromEndpointsWithNoTags()
            throws Exception {
          InjectComposer.Composer injectWrapper = getInjectWrapper();

          List<AssetGroupComposer.Composer> assetGroupWrappers = new ArrayList<>();
          Filters.Filter filter = new Filters.Filter();
          filter.setKey("endpoint_platform");
          filter.setMode(Filters.FilterMode.and);
          filter.setOperator(Filters.FilterOperator.eq);
          filter.setValues(List.of(Endpoint.PLATFORM_TYPE.Windows.name()));
          assetGroupWrappers.add(getAssetGroupWrapperWithFilter(filter));

          // create several endpoints; only the Windows (first) endpoint should be involved in the
          // above
          // groups
          TagComposer.Composer tagWrapper =
              tagComposer.forTag(TagFixture.getTagWithText("search_this_tag"));
          AgentComposer.Composer agent1Wrapper =
              agentComposer
                  .forAgent(AgentFixture.createDefaultAgentService())
                  .withExecutor(executorComposer.forExecutor(executorFixture.getDefaultExecutor()));
          endpointComposer
              .forEndpoint(EndpointFixture.createEndpoint())
              .withTag(tagWrapper)
              .withAgent(agent1Wrapper)
              .persist();

          // create a new endpoint that is not part of the above groups but will be targeted
          // explicitly
          AgentComposer.Composer agent2Wrapper =
              agentComposer
                  .forAgent(AgentFixture.createDefaultAgentService())
                  .withExecutor(executorComposer.forExecutor(executorFixture.getDefaultExecutor()));
          Endpoint ep2 = EndpointFixture.createEndpoint();
          ep2.setPlatform(Endpoint.PLATFORM_TYPE.Linux);
          EndpointComposer.Composer ep2Wrapper =
              endpointComposer.forEndpoint(ep2).withAgent(agent2Wrapper).persist();

          // create a new endpoint that is not part of the above groups
          AgentComposer.Composer agent3Wrapper =
              agentComposer
                  .forAgent(AgentFixture.createDefaultAgentService())
                  .withExecutor(executorComposer.forExecutor(executorFixture.getDefaultExecutor()));
          Endpoint notTarget = EndpointFixture.createEndpoint();
          notTarget.setPlatform(Endpoint.PLATFORM_TYPE.MacOS);
          endpointComposer.forEndpoint(notTarget).withAgent(agent3Wrapper).persist();

          assetGroupWrappers.forEach(injectWrapper::withAssetGroup);
          injectWrapper.withEndpoint(ep2Wrapper);
          Inject inject = injectWrapper.persist().get();

          entityManager.flush();
          entityManager.clear();

          SearchPaginationInput search =
              PaginationFixture.simpleSearchWithAndOperator(
                  "target_tags", tagWrapper.get().getId(), Filters.FilterOperator.empty);

          String response =
              mvc.perform(
                      post(INJECT_URI + "/" + inject.getId() + "/targets/" + targetType + "/search")
                          .contentType(MediaType.APPLICATION_JSON)
                          .content(mapper.writeValueAsString(search)))
                  .andExpect(status().isOk())
                  .andReturn()
                  .getResponse()
                  .getContentAsString();

          AgentTarget expectedTarget =
              new AgentTarget(
                  agent2Wrapper.get().getId(),
                  agent2Wrapper.get().getExecutedByUser(),
                  Set.of(),
                  agent2Wrapper.get().getAsset().getId(),
                  agent2Wrapper.get().getExecutor().getType());
          List<AgentTarget> expected = List.of(expectedTarget);

          assertThatJson(response).node("content").isEqualTo(mapper.writeValueAsString(expected));
        }

        @Test
        @DisplayName(
            "'not_empty' tag filter should only return agents that belong to endpoints with no tags'")
        public void
            whenEndpointsAreExplicitlyTargeted_thenReturnOnlyAgentsFromEndpointsWithSomeTags()
                throws Exception {
          InjectComposer.Composer injectWrapper = getInjectWrapper();

          List<AssetGroupComposer.Composer> assetGroupWrappers = new ArrayList<>();
          Filters.Filter filter = new Filters.Filter();
          filter.setKey("endpoint_platform");
          filter.setMode(Filters.FilterMode.and);
          filter.setOperator(Filters.FilterOperator.eq);
          filter.setValues(List.of(Endpoint.PLATFORM_TYPE.Windows.name()));
          assetGroupWrappers.add(getAssetGroupWrapperWithFilter(filter));

          // create several endpoints; only the Windows (first) endpoint should be involved in the
          // above
          // groups
          TagComposer.Composer tagWrapper =
              tagComposer.forTag(TagFixture.getTagWithText("search_this_tag"));
          AgentComposer.Composer agent1Wrapper =
              agentComposer
                  .forAgent(AgentFixture.createDefaultAgentService())
                  .withExecutor(executorComposer.forExecutor(executorFixture.getDefaultExecutor()));
          endpointComposer
              .forEndpoint(EndpointFixture.createEndpoint())
              .withTag(tagWrapper)
              .withAgent(agent1Wrapper)
              .persist();

          // create a new endpoint that is not part of the above groups but will be targeted
          // explicitly
          AgentComposer.Composer agent2Wrapper =
              agentComposer
                  .forAgent(AgentFixture.createDefaultAgentService())
                  .withExecutor(executorComposer.forExecutor(executorFixture.getDefaultExecutor()));
          Endpoint ep2 = EndpointFixture.createEndpoint();
          ep2.setPlatform(Endpoint.PLATFORM_TYPE.Linux);
          EndpointComposer.Composer ep2Wrapper =
              endpointComposer.forEndpoint(ep2).withAgent(agent2Wrapper).persist();

          // create a new endpoint that is not part of the above groups
          AgentComposer.Composer agent3Wrapper =
              agentComposer
                  .forAgent(AgentFixture.createDefaultAgentService())
                  .withExecutor(executorComposer.forExecutor(executorFixture.getDefaultExecutor()));
          Endpoint notTarget = EndpointFixture.createEndpoint();
          notTarget.setPlatform(Endpoint.PLATFORM_TYPE.MacOS);
          endpointComposer.forEndpoint(notTarget).withAgent(agent3Wrapper).persist();

          assetGroupWrappers.forEach(injectWrapper::withAssetGroup);
          injectWrapper.withEndpoint(ep2Wrapper);
          Inject inject = injectWrapper.persist().get();

          entityManager.flush();
          entityManager.clear();

          SearchPaginationInput search =
              PaginationFixture.simpleSearchWithAndOperator(
                  "target_tags", tagWrapper.get().getId(), Filters.FilterOperator.not_empty);

          String response =
              mvc.perform(
                      post(INJECT_URI + "/" + inject.getId() + "/targets/" + targetType + "/search")
                          .contentType(MediaType.APPLICATION_JSON)
                          .content(mapper.writeValueAsString(search)))
                  .andExpect(status().isOk())
                  .andReturn()
                  .getResponse()
                  .getContentAsString();

          AgentTarget expectedTarget =
              new AgentTarget(
                  agent1Wrapper.get().getId(),
                  agent1Wrapper.get().getExecutedByUser(),
                  Set.of(),
                  agent1Wrapper.get().getAsset().getId(),
                  agent1Wrapper.get().getExecutor().getType());
          List<AgentTarget> expected = List.of(expectedTarget);

          assertThatJson(response).node("content").isEqualTo(mapper.writeValueAsString(expected));
        }
      }
    }
  }

  @Nested
  @WithMockUser(isAdmin = true)
  @DisplayName("With endpoint search")
  public class WithEndpointSearch {
    private final TargetType targetType = TargetType.ASSETS;

    @Nested
    @DisplayName("When getting options for inject targets")
    public class WhenGettingOptionsForInjectTargets {
      @Test
      @DisplayName("/options should return all possible targets")
      public void whenEndpointsAreFilters_returnAllPossibleTargets() throws Exception {
        InjectComposer.Composer injectWrapper = getInjectWrapper();

        // dynamic group 1
        Filters.Filter filter = new Filters.Filter();
        filter.setKey("endpoint_platform");
        filter.setMode(Filters.FilterMode.and);
        filter.setOperator(Filters.FilterOperator.eq);
        filter.setValues(List.of(Endpoint.PLATFORM_TYPE.Windows.name()));
        AssetGroupComposer.Composer assetGroupWrapper = getAssetGroupWrapperWithFilter(filter);

        // 2
        Filters.Filter filter2 = new Filters.Filter();
        filter2.setKey("endpoint_platform");
        filter2.setMode(Filters.FilterMode.and);
        filter2.setOperator(Filters.FilterOperator.eq);
        filter2.setValues(List.of(Endpoint.PLATFORM_TYPE.Linux.name()));
        AssetGroupComposer.Composer assetGroupWrapper2 = getAssetGroupWrapperWithFilter(filter2);

        EndpointComposer.Composer ep1Wrapper =
            endpointComposer.forEndpoint(EndpointFixture.createEndpoint()).persist();

        Endpoint ep2 = EndpointFixture.createEndpoint();
        ep2.setPlatform(Endpoint.PLATFORM_TYPE.Linux);
        EndpointComposer.Composer ep2Wrapper = endpointComposer.forEndpoint(ep2).persist();

        // create a new endpoint that is not part of the above groups
        Endpoint ep3 = EndpointFixture.createEndpoint();
        ep3.setPlatform(Endpoint.PLATFORM_TYPE.MacOS);
        endpointComposer.forEndpoint(ep3).persist();

        injectWrapper.withAssetGroup(assetGroupWrapper).withAssetGroup(assetGroupWrapper2);
        Inject inject = injectWrapper.persist().get();

        entityManager.flush();
        entityManager.clear();

        String response =
            mvc.perform(
                    get(INJECT_URI + "/" + inject.getId() + "/targets/" + targetType + "/options")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        List<FilterUtilsJpa.Option> expected =
            List.of(
                new FilterUtilsJpa.Option(ep1Wrapper.get().getId(), ep1Wrapper.get().getName()),
                new FilterUtilsJpa.Option(ep2Wrapper.get().getId(), ep2Wrapper.get().getName()));

        assertThatJson(response).isEqualTo(mapper.writeValueAsString(expected));
      }

      @Test
      @DisplayName("/options should return all targets matching search text")
      public void whenEndpointsAreFilters_returnAllTargetsMatchingSearchText() throws Exception {
        InjectComposer.Composer injectWrapper = getInjectWrapper();

        // dynamic group 1
        Filters.Filter filter = new Filters.Filter();
        filter.setKey("endpoint_platform");
        filter.setMode(Filters.FilterMode.and);
        filter.setOperator(Filters.FilterOperator.eq);
        filter.setValues(List.of(Endpoint.PLATFORM_TYPE.Windows.name()));
        AssetGroupComposer.Composer assetGroupWrapper = getAssetGroupWrapperWithFilter(filter);

        // 2
        Filters.Filter filter2 = new Filters.Filter();
        filter2.setKey("endpoint_platform");
        filter2.setMode(Filters.FilterMode.and);
        filter2.setOperator(Filters.FilterOperator.eq);
        filter2.setValues(List.of(Endpoint.PLATFORM_TYPE.Linux.name()));
        AssetGroupComposer.Composer assetGroupWrapper2 = getAssetGroupWrapperWithFilter(filter2);

        EndpointComposer.Composer ep1Wrapper =
            endpointComposer.forEndpoint(EndpointFixture.createEndpoint()).persist();

        Endpoint ep2 = EndpointFixture.createEndpoint();
        ep2.setPlatform(Endpoint.PLATFORM_TYPE.Linux);
        ep2.setName("search this name!");
        EndpointComposer.Composer ep2Wrapper = endpointComposer.forEndpoint(ep2).persist();

        // create a new endpoint that is not part of the above groups
        Endpoint ep3 = EndpointFixture.createEndpoint();
        ep3.setPlatform(Endpoint.PLATFORM_TYPE.MacOS);
        endpointComposer.forEndpoint(ep3).persist();

        injectWrapper.withAssetGroup(assetGroupWrapper).withAssetGroup(assetGroupWrapper2);
        Inject inject = injectWrapper.persist().get();

        entityManager.flush();
        entityManager.clear();

        String response =
            mvc.perform(
                    get(INJECT_URI + "/" + inject.getId() + "/targets/" + targetType + "/options")
                        .queryParam("searchText", "search this")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        List<FilterUtilsJpa.Option> expected =
            List.of(
                new FilterUtilsJpa.Option(ep2Wrapper.get().getId(), ep2Wrapper.get().getName()));

        assertThatJson(response).isEqualTo(mapper.writeValueAsString(expected));
      }

      @Test
      @DisplayName("/options by id should return only options matching ids")
      public void whenEndpointsAreFilters_returnOnlyOptionsMatchingIds() throws Exception {
        InjectComposer.Composer injectWrapper = getInjectWrapper();

        // dynamic group 1
        Filters.Filter filter = new Filters.Filter();
        filter.setKey("endpoint_platform");
        filter.setMode(Filters.FilterMode.and);
        filter.setOperator(Filters.FilterOperator.eq);
        filter.setValues(List.of(Endpoint.PLATFORM_TYPE.Windows.name()));
        AssetGroupComposer.Composer assetGroupWrapper = getAssetGroupWrapperWithFilter(filter);

        // 2
        Filters.Filter filter2 = new Filters.Filter();
        filter2.setKey("endpoint_platform");
        filter2.setMode(Filters.FilterMode.and);
        filter2.setOperator(Filters.FilterOperator.eq);
        filter2.setValues(List.of(Endpoint.PLATFORM_TYPE.Linux.name()));
        AssetGroupComposer.Composer assetGroupWrapper2 = getAssetGroupWrapperWithFilter(filter2);

        EndpointComposer.Composer ep1Wrapper =
            endpointComposer.forEndpoint(EndpointFixture.createEndpoint()).persist();

        Endpoint ep2 = EndpointFixture.createEndpoint();
        ep2.setPlatform(Endpoint.PLATFORM_TYPE.Linux);
        endpointComposer.forEndpoint(ep2).persist();

        // create a new endpoint that is not part of the above groups
        Endpoint ep3 = EndpointFixture.createEndpoint();
        ep3.setPlatform(Endpoint.PLATFORM_TYPE.MacOS);
        endpointComposer.forEndpoint(ep3).persist();

        injectWrapper
            .withAssetGroup(assetGroupWrapper)
            .withAssetGroup(assetGroupWrapper2)
            .persist();

        entityManager.flush();
        entityManager.clear();

        List<String> ids = List.of(ep1Wrapper.get().getId());

        String response =
            mvc.perform(
                    post(INJECT_URI + "/targets/" + targetType + "/options")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(ids)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        List<FilterUtilsJpa.Option> expected =
            List.of(
                new FilterUtilsJpa.Option(ep1Wrapper.get().getId(), ep1Wrapper.get().getName()));

        assertThatJson(response).isEqualTo(mapper.writeValueAsString(expected));
      }
    }

    @Nested
    @DisplayName("With OR group operator")
    public class WithORGroupOperator {
      @Test
      @DisplayName(
          "When endpoints are explicitly targeted, 'is empty' Asset group filter should only return those that are not in any group'")
      public void whenEndpointsAreExplicitlyTargeted_thenReturnOnlyEndpointsWithNoGroup()
          throws Exception {
        InjectComposer.Composer injectWrapper = getInjectWrapper();

        List<AssetGroupComposer.Composer> assetGroupWrappers = new ArrayList<>();
        Filters.Filter filter = new Filters.Filter();
        filter.setKey("endpoint_platform");
        filter.setMode(Filters.FilterMode.and);
        filter.setOperator(Filters.FilterOperator.eq);
        filter.setValues(List.of(Endpoint.PLATFORM_TYPE.Windows.name()));
        assetGroupWrappers.add(getAssetGroupWrapperWithFilter(filter));

        // create several endpoints; only the Windows (first) endpoint should be involved in the
        // above
        // groups
        EndpointComposer.Composer ep1Wrapper =
            endpointComposer.forEndpoint(EndpointFixture.createEndpoint()).persist();

        // create a new endpoint that is not part of the above groups but will be targeted
        // explicitly
        Endpoint ep2 = EndpointFixture.createEndpoint();
        ep2.setPlatform(Endpoint.PLATFORM_TYPE.Linux);
        ep2.setName("target endpoint 2");
        EndpointComposer.Composer ep2Wrapper = endpointComposer.forEndpoint(ep2).persist();

        // create a new endpoint that is both part of the above groups and also will be targeted
        // explicitly
        Endpoint ep3 = EndpointFixture.createEndpoint();
        ep3.setName("target endpoint 3");
        EndpointComposer.Composer ep3Wrapper = endpointComposer.forEndpoint(ep3).persist();

        // create a new endpoint that is not part of the above groups
        Endpoint notTarget = EndpointFixture.createEndpoint();
        notTarget.setPlatform(Endpoint.PLATFORM_TYPE.MacOS);
        notTarget.setName("not target");
        endpointComposer.forEndpoint(notTarget).persist();

        assetGroupWrappers.forEach(injectWrapper::withAssetGroup);
        injectWrapper.withEndpoint(ep2Wrapper).withEndpoint(ep3Wrapper);
        Inject inject = injectWrapper.persist().get();

        entityManager.flush();
        entityManager.clear();

        SearchPaginationInput search =
            PaginationFixture.simpleSearchWithOrOperator(
                "target_asset_groups", null, Filters.FilterOperator.not_empty);
        // add another filter that would include notTarget endpoint
        Filters.Filter otherFilter = new Filters.Filter();
        otherFilter.setKey("target_name");
        otherFilter.setMode(Filters.FilterMode.and);
        otherFilter.setOperator(Filters.FilterOperator.contains);
        otherFilter.setValues(List.of("target"));
        search.getFilterGroup().getFilters().add(otherFilter);

        String response =
            mvc.perform(
                    post(INJECT_URI + "/" + inject.getId() + "/targets/" + targetType + "/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(search)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        List<EndpointTarget> expected =
            List.of(
                new EndpointTarget(
                    ep1Wrapper.get().getId(),
                    ep1Wrapper.get().getName(),
                    ep1Wrapper.get().getTags().stream().map(Tag::getId).collect(Collectors.toSet()),
                    ep1Wrapper.get().getPlatform().name()),
                new EndpointTarget(
                    ep2Wrapper.get().getId(),
                    ep2Wrapper.get().getName(),
                    ep2Wrapper.get().getTags().stream().map(Tag::getId).collect(Collectors.toSet()),
                    ep2Wrapper.get().getPlatform().name()),
                new EndpointTarget(
                    ep3Wrapper.get().getId(),
                    ep3Wrapper.get().getName(),
                    ep3Wrapper.get().getTags().stream().map(Tag::getId).collect(Collectors.toSet()),
                    ep3Wrapper.get().getPlatform().name()));

        assertThatJson(response).node("content").isEqualTo(mapper.writeValueAsString(expected));
      }
    }

    @Nested
    @DisplayName("With AND filter group operator")
    class WithANDFilterGroupOperator {
      @Test
      @DisplayName(
          "When endpoint is dynamic asset in group, they are targets of inject if group is a target")
      public void whenEndpointIsDynamicAssetInGroup_thenTargetIsDynamicAssetInGroup()
          throws Exception {
        InjectComposer.Composer injectWrapper = getInjectWrapper();

        // dynamic group 1
        Filters.Filter filter = new Filters.Filter();
        filter.setKey("endpoint_platform");
        filter.setMode(Filters.FilterMode.and);
        filter.setOperator(Filters.FilterOperator.eq);
        filter.setValues(List.of(Endpoint.PLATFORM_TYPE.Windows.name()));
        AssetGroupComposer.Composer assetGroupWrapper = getAssetGroupWrapperWithFilter(filter);

        // 2
        Filters.Filter filter2 = new Filters.Filter();
        filter2.setKey("endpoint_platform");
        filter2.setMode(Filters.FilterMode.and);
        filter2.setOperator(Filters.FilterOperator.eq);
        filter2.setValues(List.of(Endpoint.PLATFORM_TYPE.Linux.name()));
        AssetGroupComposer.Composer assetGroupWrapper2 = getAssetGroupWrapperWithFilter(filter2);

        EndpointComposer.Composer ep1Wrapper =
            endpointComposer.forEndpoint(EndpointFixture.createEndpoint()).persist();
        Endpoint ep2 = EndpointFixture.createEndpoint();
        ep2.setPlatform(Endpoint.PLATFORM_TYPE.Linux);
        EndpointComposer.Composer ep2Wrapper = endpointComposer.forEndpoint(ep2).persist();

        // create a new endpoint that is not part of the above groups
        Endpoint ep3 = EndpointFixture.createEndpoint();
        ep3.setPlatform(Endpoint.PLATFORM_TYPE.MacOS);
        endpointComposer.forEndpoint(ep3).persist();

        injectWrapper
            .withAssetGroup(assetGroupWrapper)
            .withExpectation(
                injectExpectationComposer
                    .forExpectation(
                        InjectExpectationFixture.createExpectationWithTypeAndStatus(
                            InjectExpectation.EXPECTATION_TYPE.DETECTION,
                            InjectExpectation.EXPECTATION_STATUS.SUCCESS))
                    .withAssetGroup(assetGroupWrapper)
                    .withEndpoint(ep1Wrapper))
            .withExpectation(
                injectExpectationComposer
                    .forExpectation(
                        InjectExpectationFixture.createExpectationWithTypeAndStatus(
                            InjectExpectation.EXPECTATION_TYPE.PREVENTION,
                            InjectExpectation.EXPECTATION_STATUS.PARTIAL))
                    .withAssetGroup(assetGroupWrapper)
                    .withEndpoint(ep1Wrapper))
            .withAssetGroup(assetGroupWrapper2)
            .withExpectation(
                injectExpectationComposer
                    .forExpectation(
                        InjectExpectationFixture.createExpectationWithTypeAndStatus(
                            InjectExpectation.EXPECTATION_TYPE.DETECTION,
                            InjectExpectation.EXPECTATION_STATUS.SUCCESS))
                    .withAssetGroup(assetGroupWrapper2)
                    .withEndpoint(ep2Wrapper))
            .withExpectation(
                injectExpectationComposer
                    .forExpectation(
                        InjectExpectationFixture.createExpectationWithTypeAndStatus(
                            InjectExpectation.EXPECTATION_TYPE.PREVENTION,
                            InjectExpectation.EXPECTATION_STATUS.PARTIAL))
                    .withAssetGroup(assetGroupWrapper2)
                    .withEndpoint(ep2Wrapper));
        Inject inject = injectWrapper.persist().get();

        entityManager.flush();
        entityManager.clear();

        SearchPaginationInput search = PaginationFixture.getDefault().build();

        String response =
            mvc.perform(
                    post(INJECT_URI + "/" + inject.getId() + "/targets/" + targetType + "/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(search)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        EndpointTarget expectedTarget1 =
            new EndpointTarget(
                ep1Wrapper.get().getId(),
                ep1Wrapper.get().getName(),
                ep1Wrapper.get().getTags().stream().map(Tag::getId).collect(Collectors.toSet()),
                ep1Wrapper.get().getPlatform().name());
        expectedTarget1.setTargetDetectionStatus(InjectExpectation.EXPECTATION_STATUS.SUCCESS);
        expectedTarget1.setTargetPreventionStatus(InjectExpectation.EXPECTATION_STATUS.PARTIAL);
        EndpointTarget expectedTarget2 =
            new EndpointTarget(
                ep2Wrapper.get().getId(),
                ep2Wrapper.get().getName(),
                ep2Wrapper.get().getTags().stream().map(Tag::getId).collect(Collectors.toSet()),
                ep2Wrapper.get().getPlatform().name());
        expectedTarget2.setTargetDetectionStatus(InjectExpectation.EXPECTATION_STATUS.SUCCESS);
        expectedTarget2.setTargetPreventionStatus(InjectExpectation.EXPECTATION_STATUS.PARTIAL);
        // expect two out of three endpoints in the resultset, i.e. not the extra one
        List<EndpointTarget> expected = List.of(expectedTarget1, expectedTarget2);

        assertThatJson(response).node("content").isEqualTo(mapper.writeValueAsString(expected));
      }

      @Test
      @DisplayName("When dynamic groups intersect, common endpoints are returned only once")
      public void whenDynamicGroupsIntersect_thenReturnCommonEndpointsOnce() throws Exception {
        InjectComposer.Composer injectWrapper = getInjectWrapper();

        List<AssetGroupComposer.Composer> assetGroupWrappers = new ArrayList<>();

        // create two identical asset groups
        for (int i = 0; i < 2; i++) {
          Filters.Filter filter = new Filters.Filter();
          filter.setKey("endpoint_platform");
          filter.setMode(Filters.FilterMode.and);
          filter.setOperator(Filters.FilterOperator.eq);
          filter.setValues(List.of(Endpoint.PLATFORM_TYPE.Windows.name()));
          assetGroupWrappers.add(getAssetGroupWrapperWithFilter(filter));
        }

        // create several endpoints; only the Windows (first) endpoint should be involved in the
        // above
        // groups
        EndpointComposer.Composer ep1Wrapper =
            endpointComposer.forEndpoint(EndpointFixture.createEndpoint()).persist();

        // create a new endpoint that is not part of the above groups
        Endpoint ep2 = EndpointFixture.createEndpoint();
        ep2.setPlatform(Endpoint.PLATFORM_TYPE.Linux);
        endpointComposer.forEndpoint(ep2).persist();

        // create a new endpoint that is not part of the above groups
        Endpoint ep3 = EndpointFixture.createEndpoint();
        ep3.setPlatform(Endpoint.PLATFORM_TYPE.MacOS);
        endpointComposer.forEndpoint(ep3).persist();

        assetGroupWrappers.forEach(injectWrapper::withAssetGroup);
        Inject inject = injectWrapper.persist().get();

        entityManager.flush();
        entityManager.clear();

        SearchPaginationInput search =
            PaginationFixture.simpleSearchWithAndOperator(
                "target_name", "", Filters.FilterOperator.contains);

        String response =
            mvc.perform(
                    post(INJECT_URI + "/" + inject.getId() + "/targets/" + targetType + "/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(search)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        EndpointTarget expectedTarget =
            new EndpointTarget(
                ep1Wrapper.get().getId(),
                ep1Wrapper.get().getName(),
                ep1Wrapper.get().getTags().stream().map(Tag::getId).collect(Collectors.toSet()),
                ep1Wrapper.get().getPlatform().name());
        List<EndpointTarget> expected = List.of(expectedTarget);

        assertThatJson(response).node("content").isEqualTo(mapper.writeValueAsString(expected));
      }

      @Test
      @DisplayName(
          "When no applied AssetGroup filter, at most a full page of targeted endpoints should be returned")
      public void whenNoAppliedAssetGroupFilter_returnPageOfAllTargetedEndpoints()
          throws Exception {
        InjectComposer.Composer injectWrapper = getInjectWrapper();

        List<AssetGroupComposer.Composer> assetGroupWrappers = new ArrayList<>();
        Filters.Filter filter = new Filters.Filter();
        filter.setKey("endpoint_platform");
        filter.setMode(Filters.FilterMode.and);
        filter.setOperator(Filters.FilterOperator.eq);
        filter.setValues(List.of(Endpoint.PLATFORM_TYPE.Windows.name()));
        assetGroupWrappers.add(getAssetGroupWrapperWithFilter(filter));

        // create several endpoints; only the Windows (first) endpoint should be involved in the
        // above
        // groups
        EndpointComposer.Composer ep1Wrapper =
            endpointComposer.forEndpoint(EndpointFixture.createEndpoint()).persist();

        // create a new endpoint that is not part of the above groups but will be targeted
        // explicitly
        Endpoint ep2 = EndpointFixture.createEndpoint();
        ep2.setPlatform(Endpoint.PLATFORM_TYPE.Linux);
        EndpointComposer.Composer ep2Wrapper = endpointComposer.forEndpoint(ep2).persist();

        // create a new endpoint that is both part of the above groups and also will be targeted
        // explicitly
        Endpoint ep3 = EndpointFixture.createEndpoint();
        EndpointComposer.Composer ep3Wrapper = endpointComposer.forEndpoint(ep3).persist();

        // create a new endpoint that is not part of the above groups
        Endpoint notTarget = EndpointFixture.createEndpoint();
        notTarget.setPlatform(Endpoint.PLATFORM_TYPE.MacOS);
        endpointComposer.forEndpoint(notTarget).persist();

        assetGroupWrappers.forEach(injectWrapper::withAssetGroup);
        injectWrapper.withEndpoint(ep2Wrapper).withEndpoint(ep3Wrapper);
        Inject inject = injectWrapper.persist().get();

        entityManager.flush();
        entityManager.clear();

        SearchPaginationInput search = PaginationFixture.getDefault().build();

        String response =
            mvc.perform(
                    post(INJECT_URI + "/" + inject.getId() + "/targets/" + targetType + "/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(search)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        List<EndpointTarget> expected =
            List.of(
                new EndpointTarget(
                    ep1Wrapper.get().getId(),
                    ep1Wrapper.get().getName(),
                    ep1Wrapper.get().getTags().stream().map(Tag::getId).collect(Collectors.toSet()),
                    ep1Wrapper.get().getPlatform().name()),
                new EndpointTarget(
                    ep2Wrapper.get().getId(),
                    ep2Wrapper.get().getName(),
                    ep2Wrapper.get().getTags().stream().map(Tag::getId).collect(Collectors.toSet()),
                    ep2Wrapper.get().getPlatform().name()),
                new EndpointTarget(
                    ep3Wrapper.get().getId(),
                    ep3Wrapper.get().getName(),
                    ep3Wrapper.get().getTags().stream().map(Tag::getId).collect(Collectors.toSet()),
                    ep3Wrapper.get().getPlatform().name()));

        assertThatJson(response).node("content").isEqualTo(mapper.writeValueAsString(expected));
      }

      @Test
      @DisplayName(
          "When endpoints are explicitly targeted, 'is empty' Asset group filter should only return those that are not in any group'")
      public void whenEndpointsAreExplicitlyTargeted_thenReturnOnlyEndpointsWithNoGroup()
          throws Exception {
        InjectComposer.Composer injectWrapper = getInjectWrapper();

        List<AssetGroupComposer.Composer> assetGroupWrappers = new ArrayList<>();
        Filters.Filter filter = new Filters.Filter();
        filter.setKey("endpoint_platform");
        filter.setMode(Filters.FilterMode.and);
        filter.setOperator(Filters.FilterOperator.eq);
        filter.setValues(List.of(Endpoint.PLATFORM_TYPE.Windows.name()));
        assetGroupWrappers.add(getAssetGroupWrapperWithFilter(filter));

        // create several endpoints; only the Windows (first) endpoint should be involved in the
        // above
        // groups
        endpointComposer.forEndpoint(EndpointFixture.createEndpoint()).persist();

        // create a new endpoint that is not part of the above groups but will be targeted
        // explicitly
        Endpoint ep2 = EndpointFixture.createEndpoint();
        ep2.setPlatform(Endpoint.PLATFORM_TYPE.Linux);
        EndpointComposer.Composer ep2Wrapper = endpointComposer.forEndpoint(ep2).persist();

        // create a new endpoint that is both part of the above groups and also will be targeted
        // explicitly
        Endpoint ep3 = EndpointFixture.createEndpoint();
        EndpointComposer.Composer ep3Wrapper = endpointComposer.forEndpoint(ep3).persist();

        // create a new endpoint that is not part of the above groups
        Endpoint notTarget = EndpointFixture.createEndpoint();
        notTarget.setPlatform(Endpoint.PLATFORM_TYPE.MacOS);
        endpointComposer.forEndpoint(notTarget).persist();

        assetGroupWrappers.forEach(injectWrapper::withAssetGroup);
        injectWrapper.withEndpoint(ep2Wrapper).withEndpoint(ep3Wrapper);
        Inject inject = injectWrapper.persist().get();

        entityManager.flush();
        entityManager.clear();

        SearchPaginationInput search =
            PaginationFixture.simpleSearchWithAndOperator(
                "target_asset_groups", null, Filters.FilterOperator.empty);

        String response =
            mvc.perform(
                    post(INJECT_URI + "/" + inject.getId() + "/targets/" + targetType + "/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(search)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        EndpointTarget expectedTarget =
            new EndpointTarget(
                ep2Wrapper.get().getId(),
                ep2Wrapper.get().getName(),
                ep2Wrapper.get().getTags().stream().map(Tag::getId).collect(Collectors.toSet()),
                ep2Wrapper.get().getPlatform().name());
        List<EndpointTarget> expected = List.of(expectedTarget);

        assertThatJson(response).node("content").isEqualTo(mapper.writeValueAsString(expected));
      }

      @Test
      @DisplayName(
          "When endpoints are explicitly targeted, 'not empty' Asset group filter should only return those that are in any group'")
      public void whenEndpointsAreExplicitlyTargeted_thenReturnOnlyEndpointsWithGroup()
          throws Exception {
        InjectComposer.Composer injectWrapper = getInjectWrapper();

        List<AssetGroupComposer.Composer> assetGroupWrappers = new ArrayList<>();
        Filters.Filter filter = new Filters.Filter();
        filter.setKey("endpoint_platform");
        filter.setMode(Filters.FilterMode.and);
        filter.setOperator(Filters.FilterOperator.eq);
        filter.setValues(List.of(Endpoint.PLATFORM_TYPE.Windows.name()));
        assetGroupWrappers.add(getAssetGroupWrapperWithFilter(filter));

        // create several endpoints; only the Windows (first) endpoint should be involved in the
        // above
        // groups
        EndpointComposer.Composer ep1Wrapper =
            endpointComposer.forEndpoint(EndpointFixture.createEndpoint()).persist();

        // create a new endpoint that is not part of the above groups but will be targeted
        // explicitly
        Endpoint ep2 = EndpointFixture.createEndpoint();
        ep2.setPlatform(Endpoint.PLATFORM_TYPE.Linux);
        EndpointComposer.Composer ep2Wrapper = endpointComposer.forEndpoint(ep2).persist();

        // create a new endpoint that is not part of the above groups
        Endpoint notTarget = EndpointFixture.createEndpoint();
        notTarget.setPlatform(Endpoint.PLATFORM_TYPE.MacOS);
        endpointComposer.forEndpoint(notTarget).persist();

        assetGroupWrappers.forEach(injectWrapper::withAssetGroup);
        injectWrapper.withEndpoint(ep2Wrapper);
        Inject inject = injectWrapper.persist().get();

        entityManager.flush();
        entityManager.clear();

        SearchPaginationInput search =
            PaginationFixture.simpleSearchWithAndOperator(
                "target_asset_groups", null, Filters.FilterOperator.not_empty);

        String response =
            mvc.perform(
                    post(INJECT_URI + "/" + inject.getId() + "/targets/" + targetType + "/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(search)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        EndpointTarget expectedTarget =
            new EndpointTarget(
                ep1Wrapper.get().getId(),
                ep1Wrapper.get().getName(),
                ep1Wrapper.get().getTags().stream().map(Tag::getId).collect(Collectors.toSet()),
                ep1Wrapper.get().getPlatform().name());
        List<EndpointTarget> expected = List.of(expectedTarget);

        assertThatJson(response).node("content").isEqualTo(mapper.writeValueAsString(expected));
      }

      @Test
      @DisplayName(
          "When are part of a group, 'not contains' operator on this group should exclude endpoint")
      public void whenArePartOfAGroupAndGroupFilteredOut_thenExcludeAllEndpointsOfGroup()
          throws Exception {
        InjectComposer.Composer injectWrapper = getInjectWrapper();

        List<AssetGroupComposer.Composer> assetGroupWrappers = new ArrayList<>();
        Filters.Filter filter = new Filters.Filter();
        filter.setKey("endpoint_platform");
        filter.setMode(Filters.FilterMode.and);
        filter.setOperator(Filters.FilterOperator.eq);
        filter.setValues(List.of(Endpoint.PLATFORM_TYPE.Windows.name()));
        assetGroupWrappers.add(getAssetGroupWrapperWithFilter(filter));

        // create several endpoints; only the Windows (first) endpoint should be involved in the
        // above
        // groups
        endpointComposer.forEndpoint(EndpointFixture.createEndpoint()).persist();

        // create a new endpoint that is not part of the above groups but will be targeted
        // explicitly
        Endpoint ep2 = EndpointFixture.createEndpoint();
        ep2.setPlatform(Endpoint.PLATFORM_TYPE.Linux);
        EndpointComposer.Composer ep2Wrapper = endpointComposer.forEndpoint(ep2).persist();

        // create a new endpoint that is not part of the above groups
        Endpoint ep3 = EndpointFixture.createEndpoint();
        ep3.setPlatform(Endpoint.PLATFORM_TYPE.MacOS);
        endpointComposer.forEndpoint(ep3).persist();

        assetGroupWrappers.forEach(injectWrapper::withAssetGroup);
        Inject inject = injectWrapper.withEndpoint(ep2Wrapper).persist().get();

        entityManager.flush();
        entityManager.clear();

        SearchPaginationInput search =
            PaginationFixture.simpleSearchWithAndOperator(
                "target_asset_groups",
                assetGroupWrappers.getFirst().get().getId(),
                Filters.FilterOperator.not_contains);

        String response =
            mvc.perform(
                    post(INJECT_URI + "/" + inject.getId() + "/targets/" + targetType + "/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(search)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        EndpointTarget expectedTarget =
            new EndpointTarget(
                ep2Wrapper.get().getId(),
                ep2Wrapper.get().getName(),
                ep2Wrapper.get().getTags().stream().map(Tag::getId).collect(Collectors.toSet()),
                ep2Wrapper.get().getPlatform().name());
        List<EndpointTarget> expected = List.of(expectedTarget);

        assertThatJson(response).node("content").isEqualTo(mapper.writeValueAsString(expected));
      }

      @Test
      @DisplayName(
          "When are part of a group, 'contains' operator on this group should include endpoints of this group and exclude others")
      public void whenArePartOfAGroupAndGroupSelectedAsFilter_thenIncludeOnlyEndpointsOfGroup()
          throws Exception {
        InjectComposer.Composer injectWrapper = getInjectWrapper();

        List<AssetGroupComposer.Composer> assetGroupWrappers = new ArrayList<>();
        Filters.Filter filter = new Filters.Filter();
        filter.setKey("endpoint_platform");
        filter.setMode(Filters.FilterMode.and);
        filter.setOperator(Filters.FilterOperator.eq);
        filter.setValues(List.of(Endpoint.PLATFORM_TYPE.Windows.name()));
        assetGroupWrappers.add(getAssetGroupWrapperWithFilter(filter));

        // create several endpoints; only the Windows (first) endpoint should be involved in the
        // above
        // groups
        EndpointComposer.Composer ep1Wrapper =
            endpointComposer.forEndpoint(EndpointFixture.createEndpoint()).persist();

        // create a new endpoint that is not part of the above groups but will be targeted
        // explicitly
        Endpoint ep2 = EndpointFixture.createEndpoint();
        ep2.setPlatform(Endpoint.PLATFORM_TYPE.Linux);
        EndpointComposer.Composer ep2Wrapper = endpointComposer.forEndpoint(ep2).persist();

        // create a new endpoint that is not part of the above groups
        Endpoint ep3 = EndpointFixture.createEndpoint();
        ep3.setPlatform(Endpoint.PLATFORM_TYPE.MacOS);
        endpointComposer.forEndpoint(ep3).persist();

        assetGroupWrappers.forEach(injectWrapper::withAssetGroup);
        // add two endpoints as direct targets, including the endpoint part of the excluded group
        injectWrapper.withEndpoint(ep2Wrapper).withEndpoint(ep1Wrapper);
        Inject inject = injectWrapper.persist().get();

        entityManager.flush();
        entityManager.clear();

        SearchPaginationInput search =
            PaginationFixture.simpleSearchWithAndOperator(
                "target_asset_groups",
                assetGroupWrappers.getFirst().get().getId(),
                Filters.FilterOperator.contains);

        String response =
            mvc.perform(
                    post(INJECT_URI + "/" + inject.getId() + "/targets/" + targetType + "/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(search)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        EndpointTarget expectedTarget =
            new EndpointTarget(
                ep1Wrapper.get().getId(),
                ep1Wrapper.get().getName(),
                ep1Wrapper.get().getTags().stream().map(Tag::getId).collect(Collectors.toSet()),
                ep1Wrapper.get().getPlatform().name());
        List<EndpointTarget> expected = List.of(expectedTarget);

        assertThatJson(response).node("content").isEqualTo(mapper.writeValueAsString(expected));
      }
    }
  }

  @Nested
  @WithMockUser(isAdmin = true)
  @DisplayName("With asset groups search")
  public class WithAssetGroupsSearch {
    private final TargetType targetType = TargetType.ASSETS_GROUPS;

    private AssetGroupComposer.Composer getAssetGroupComposerWithName(String assetGroupName) {
      return assetGroupComposer.forAssetGroup(
          AssetGroupFixture.createDefaultAssetGroup(assetGroupName));
    }

    @Nested
    @DisplayName("When getting options for inject targets")
    public class WhenGettingOptionsForInjectTargets {
      @Test
      @DisplayName("When asset groups are targets, options should return all possible targets")
      public void whenAssetGroupsAreTargets_returnAllPossibleTargets() throws Exception {
        InjectComposer.Composer injectWrapper = getInjectWrapper();

        // dynamic group 1
        Filters.Filter filter = new Filters.Filter();
        filter.setKey("endpoint_platform");
        filter.setMode(Filters.FilterMode.and);
        filter.setOperator(Filters.FilterOperator.eq);
        filter.setValues(List.of(Endpoint.PLATFORM_TYPE.Windows.name()));
        AssetGroupComposer.Composer assetGroupWrapper = getAssetGroupWrapperWithFilter(filter);

        // 2
        Filters.Filter filter2 = new Filters.Filter();
        filter2.setKey("endpoint_platform");
        filter2.setMode(Filters.FilterMode.and);
        filter2.setOperator(Filters.FilterOperator.eq);
        filter2.setValues(List.of(Endpoint.PLATFORM_TYPE.Linux.name()));
        AssetGroupComposer.Composer assetGroupWrapper2 = getAssetGroupWrapperWithFilter(filter2);

        injectWrapper.withAssetGroup(assetGroupWrapper).withAssetGroup(assetGroupWrapper2);
        Inject inject = injectWrapper.persist().get();

        entityManager.flush();
        entityManager.clear();

        String response =
            mvc.perform(
                    get(INJECT_URI + "/" + inject.getId() + "/targets/" + targetType + "/options")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        List<FilterUtilsJpa.Option> expected =
            List.of(
                new FilterUtilsJpa.Option(
                    assetGroupWrapper.get().getId(), assetGroupWrapper.get().getName()),
                new FilterUtilsJpa.Option(
                    assetGroupWrapper2.get().getId(), assetGroupWrapper2.get().getName()));

        assertThatJson(response)
            .when(Option.IGNORING_ARRAY_ORDER)
            .isEqualTo(mapper.writeValueAsString(expected));
      }

      @Test
      @DisplayName(
          "When asset groups are targets, options should return all possible targets matching search text")
      public void whenAssetGroupsAreTargets_returnAllPossibleTargetsMatchingSearchText()
          throws Exception {
        InjectComposer.Composer injectWrapper = getInjectWrapper();

        // dynamic group 1
        Filters.Filter filter = new Filters.Filter();
        filter.setKey("endpoint_platform");
        filter.setMode(Filters.FilterMode.and);
        filter.setOperator(Filters.FilterOperator.eq);
        filter.setValues(List.of(Endpoint.PLATFORM_TYPE.Windows.name()));
        AssetGroupComposer.Composer assetGroupWrapper = getAssetGroupWrapperWithFilter(filter);

        // 2
        Filters.Filter filter2 = new Filters.Filter();
        filter2.setKey("endpoint_platform");
        filter2.setMode(Filters.FilterMode.and);
        filter2.setOperator(Filters.FilterOperator.eq);
        filter2.setValues(List.of(Endpoint.PLATFORM_TYPE.Linux.name()));
        AssetGroupComposer.Composer assetGroupWrapper2 = getAssetGroupWrapperWithFilter(filter2);
        // tweak the name of the assetGroup to use as search criteria
        assetGroupWrapper2.get().setName(assetGroupWrapper.get().getName() + "ReturnThis");

        injectWrapper.withAssetGroup(assetGroupWrapper).withAssetGroup(assetGroupWrapper2);
        Inject inject = injectWrapper.persist().get();

        entityManager.flush();
        entityManager.clear();

        String response =
            mvc.perform(
                    get(INJECT_URI + "/" + inject.getId() + "/targets/" + targetType + "/options")
                        // keep lower case for case-insensitive search
                        .queryParam("searchText", "returnthis")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        List<FilterUtilsJpa.Option> expected =
            List.of(
                new FilterUtilsJpa.Option(
                    assetGroupWrapper2.get().getId(), assetGroupWrapper2.get().getName()));

        assertThatJson(response)
            .when(Option.IGNORING_ARRAY_ORDER)
            .isEqualTo(mapper.writeValueAsString(expected));
      }

      @Test
      @DisplayName(
          "When asset groups are targets, options by id should return only options matching ids")
      public void whenAssetGroupsAreTargets_returnOnlyOptionsMatchingIds() throws Exception {
        InjectComposer.Composer injectWrapper = getInjectWrapper();

        // dynamic group 1
        Filters.Filter filter = new Filters.Filter();
        filter.setKey("endpoint_platform");
        filter.setMode(Filters.FilterMode.and);
        filter.setOperator(Filters.FilterOperator.eq);
        filter.setValues(List.of(Endpoint.PLATFORM_TYPE.Windows.name()));
        AssetGroupComposer.Composer assetGroupWrapper = getAssetGroupWrapperWithFilter(filter);

        // 2
        Filters.Filter filter2 = new Filters.Filter();
        filter2.setKey("endpoint_platform");
        filter2.setMode(Filters.FilterMode.and);
        filter2.setOperator(Filters.FilterOperator.eq);
        filter2.setValues(List.of(Endpoint.PLATFORM_TYPE.Linux.name()));
        AssetGroupComposer.Composer assetGroupWrapper2 = getAssetGroupWrapperWithFilter(filter2);

        injectWrapper
            .withAssetGroup(assetGroupWrapper)
            .withAssetGroup(assetGroupWrapper2)
            .persist();

        entityManager.flush();
        entityManager.clear();

        List<String> ids = List.of(assetGroupWrapper.get().getId());

        String response =
            mvc.perform(
                    post(INJECT_URI + "/targets/" + targetType + "/options")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(ids)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        List<FilterUtilsJpa.Option> expected =
            List.of(
                new FilterUtilsJpa.Option(
                    assetGroupWrapper.get().getId(), assetGroupWrapper.get().getName()));

        assertThatJson(response)
            .when(Option.IGNORING_ARRAY_ORDER)
            .isEqualTo(mapper.writeValueAsString(expected));
      }
    }

    @Test
    @DisplayName("With no asset group targets, return no items in page")
    public void withNoAssetGroupTargets_returnNoItemsInPage() throws Exception {
      Inject inject = getInjectWrapper().persist().get();
      entityManager.flush();
      entityManager.clear();

      SearchPaginationInput search =
          PaginationFixture.simpleSearchWithAndOperator(
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
          PaginationFixture.simpleSearchWithAndOperator(
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
          PaginationFixture.simpleSearchWithAndOperator(
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
      private InjectExpectationComposer.Composer getExpectationWrapperWithResult(
          InjectExpectation.EXPECTATION_TYPE type, InjectExpectation.EXPECTATION_STATUS status) {
        return injectExpectationComposer.forExpectation(
            InjectExpectationFixture.createExpectationWithTypeAndStatus(type, status));
      }

      @Test
      @DisplayName("Given specific scores, expectation type results are of correct status")
      public void givenSpecificScores_expectationTypeResultsAreOfCorrectStatus() throws Exception {
        String searchTerm = "asset group target";
        InjectComposer.Composer injectWrapper = getInjectWrapper();
        AssetGroupComposer.Composer assetGroupWrapper = getAssetGroupComposerWithName(searchTerm);
        InjectExpectationComposer.Composer expectationDetectionWrapper =
            getExpectationWrapperWithResult(
                    InjectExpectation.EXPECTATION_TYPE.DETECTION,
                    InjectExpectation.EXPECTATION_STATUS.SUCCESS)
                .withAssetGroup(assetGroupWrapper);
        InjectExpectationComposer.Composer expectationPreventionWrapper =
            getExpectationWrapperWithResult(
                    InjectExpectation.EXPECTATION_TYPE.PREVENTION,
                    InjectExpectation.EXPECTATION_STATUS.FAILED)
                .withAssetGroup(assetGroupWrapper);
        InjectExpectationComposer.Composer expectationHumanResponseWrapper =
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
            PaginationFixture.simpleSearchWithAndOperator(
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

  @Nested
  @WithMockUser(isAdmin = true)
  @DisplayName("With teams search")
  public class WithTeamsSearch {

    private final TargetType targetType = TargetType.TEAMS;

    private TeamComposer.Composer getTeamComposerWithName(String teamName) {
      return teamComposer.forTeam(TeamFixture.createTeamWithName(teamName));
    }

    @Nested
    @DisplayName("When getting options for inject targets")
    public class WhenGettingOptionsForInjectTargets {
      @Test
      @DisplayName("When teams are targets, options should return all possible targets")
      public void whenTeamsAreTargets_returnAllPossibleTargets() throws Exception {
        InjectComposer.Composer injectWrapper = getInjectWrapper();

        TeamComposer.Composer teamWrapper1 = getTeamComposerWithName("test team");
        TeamComposer.Composer teamWrapper2 = getTeamComposerWithName("other test team");

        injectWrapper.withTeam(teamWrapper1).withTeam(teamWrapper2);
        Inject inject = injectWrapper.persist().get();

        entityManager.flush();
        entityManager.clear();

        String response =
            mvc.perform(
                    get(INJECT_URI + "/" + inject.getId() + "/targets/" + targetType + "/options")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        List<FilterUtilsJpa.Option> expected =
            List.of(
                new FilterUtilsJpa.Option(teamWrapper2.get().getId(), teamWrapper2.get().getName()),
                new FilterUtilsJpa.Option(
                    teamWrapper1.get().getId(), teamWrapper1.get().getName()));

        assertThatJson(response)
            .when(Option.IGNORING_ARRAY_ORDER)
            .isEqualTo(mapper.writeValueAsString(expected));
      }

      @Test
      @DisplayName(
          "When teams are targets, options should return all possible targets for exercise all teams")
      public void whenTeamsAreTargets_returnAllPossibleTargetsForExerciseAllTeams()
          throws Exception {
        String searchTerm1 = "team target 1";
        String searchTerm2 = "team target 2";
        TeamComposer.Composer teamWrapper1 = getTeamComposerWithName(searchTerm1);
        UserComposer.Composer userWrapper1 =
            getPlayerComposerWithName(searchTerm1, searchTerm1, searchTerm1 + "@toto.fr");
        TeamComposer.Composer teamWrapper2 = getTeamComposerWithName(searchTerm2);
        UserComposer.Composer userWrapper2 =
            getPlayerComposerWithName(searchTerm2, searchTerm2, searchTerm2 + "@toto.fr");
        InjectComposer.Composer injectWrapper = getInjectWithAllTeams();
        ExerciseComposer.Composer exerciseWrapper =
            getExerciseComposerWithName()
                .withTeam(teamWrapper1.withUser(userWrapper1))
                .withTeam(teamWrapper2.withUser(userWrapper2))
                .withTeamUsers()
                .withInject(injectWrapper);
        Exercise exercise = exerciseWrapper.persist().get();
        entityManager.flush();
        entityManager.clear();

        String response =
            mvc.perform(
                    get(INJECT_URI
                            + "/"
                            + exercise.getInjects().getFirst().getId()
                            + "/targets/"
                            + targetType
                            + "/options")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        List<FilterUtilsJpa.Option> expected =
            List.of(
                new FilterUtilsJpa.Option(teamWrapper2.get().getId(), teamWrapper2.get().getName()),
                new FilterUtilsJpa.Option(
                    teamWrapper1.get().getId(), teamWrapper1.get().getName()));

        assertThatJson(response)
            .when(Option.IGNORING_ARRAY_ORDER)
            .isEqualTo(mapper.writeValueAsString(expected));
      }

      @Test
      @DisplayName(
          "When teams are targets, options should return all possible targets for exercise with inject with 1 team")
      public void whenTeamsAreTargets_returnAllPossibleTargetsForExerciseWithInjectWith1Team()
          throws Exception {
        String searchTerm1 = "player target 1";
        String searchTerm2 = "player target 2";
        TeamComposer.Composer teamWrapper1 = getTeamComposerWithName(searchTerm1);
        UserComposer.Composer userWrapper1 =
            getPlayerComposerWithName(searchTerm1, searchTerm1, searchTerm1 + "@toto.fr");
        TeamComposer.Composer teamWrapper2 = getTeamComposerWithName(searchTerm2);
        UserComposer.Composer userWrapper2 =
            getPlayerComposerWithName(searchTerm2, searchTerm2, searchTerm2 + "@toto.fr");
        InjectComposer.Composer injectWrapper = getInjectWrapper();
        ExerciseComposer.Composer exerciseWrapper =
            getExerciseComposerWithName()
                .withTeam(teamWrapper1.withUser(userWrapper1))
                .withTeam(teamWrapper2.withUser(userWrapper2))
                .withTeamUsers()
                .withInject(injectWrapper.withTeam(teamWrapper1));
        Exercise exercise = exerciseWrapper.persist().get();
        entityManager.flush();
        entityManager.clear();

        String response =
            mvc.perform(
                    get(INJECT_URI
                            + "/"
                            + exercise.getInjects().getFirst().getId()
                            + "/targets/"
                            + targetType
                            + "/options")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        List<FilterUtilsJpa.Option> expected =
            List.of(
                new FilterUtilsJpa.Option(
                    teamWrapper1.get().getId(), teamWrapper1.get().getName()));

        assertThatJson(response)
            .when(Option.IGNORING_ARRAY_ORDER)
            .isEqualTo(mapper.writeValueAsString(expected));
      }

      @Test
      @DisplayName(
          "When teams are targets, options should return all possible targets matching search text")
      public void whenTeamsAreTargets_returnAllPossibleTargetsMatchingSearchText()
          throws Exception {
        InjectComposer.Composer injectWrapper = getInjectWrapper();

        TeamComposer.Composer teamWrapper1 = getTeamComposerWithName("test team");
        TeamComposer.Composer teamWrapper2 = getTeamComposerWithName("other test team");

        injectWrapper.withTeam(teamWrapper1).withTeam(teamWrapper2);
        Inject inject = injectWrapper.persist().get();

        entityManager.flush();
        entityManager.clear();

        String response =
            mvc.perform(
                    get(INJECT_URI + "/" + inject.getId() + "/targets/" + targetType + "/options")
                        .queryParam("searchText", "Other")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        List<FilterUtilsJpa.Option> expected =
            List.of(
                new FilterUtilsJpa.Option(
                    teamWrapper2.get().getId(), teamWrapper2.get().getName()));

        assertThatJson(response)
            .when(Option.IGNORING_ARRAY_ORDER)
            .isEqualTo(mapper.writeValueAsString(expected));
      }

      @Test
      @DisplayName("When teams are targets, options by id should return only options matching ids")
      public void whenTeamsAreTargets_returnOnlyOptionsMatchingIds() throws Exception {
        InjectComposer.Composer injectWrapper = getInjectWrapper();

        TeamComposer.Composer teamWrapper1 = getTeamComposerWithName("test team");
        TeamComposer.Composer teamWrapper2 = getTeamComposerWithName("other test team");

        injectWrapper.withTeam(teamWrapper1).withTeam(teamWrapper2).persist();

        entityManager.flush();
        entityManager.clear();

        List<String> ids = List.of(teamWrapper1.get().getId());

        String response =
            mvc.perform(
                    post(INJECT_URI + "/targets/" + targetType + "/options")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(ids)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        List<FilterUtilsJpa.Option> expected =
            List.of(
                new FilterUtilsJpa.Option(
                    teamWrapper1.get().getId(), teamWrapper1.get().getName()));

        assertThatJson(response)
            .when(Option.IGNORING_ARRAY_ORDER)
            .isEqualTo(mapper.writeValueAsString(expected));
      }
    }

    @Test
    @DisplayName("With no team targets, return no items in page")
    public void withNoTeamTargets_returnNoItemsInPage() throws Exception {
      Inject inject = getInjectWrapper().persist().get();
      entityManager.flush();
      entityManager.clear();

      SearchPaginationInput search =
          PaginationFixture.simpleSearchWithAndOperator(
              "target_name", "target team", Filters.FilterOperator.eq);
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
          PaginationFixture.simpleSearchWithAndOperator(
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
          PaginationFixture.simpleSearchWithAndOperator(
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

    @Test
    @DisplayName(
        "With some teams targets, return matching items for exercises with inject all teams")
    public void withSomeTeamsTargets_returnMatchingItemsForExercisesWithInjectAllTeams()
        throws Exception {
      String searchTerm1 = "team target 1";
      String searchTerm2 = "team target 2";
      TeamComposer.Composer teamWrapper1 = getTeamComposerWithName(searchTerm1);
      UserComposer.Composer userWrapper1 =
          getPlayerComposerWithName(searchTerm1, searchTerm1, searchTerm1 + "@toto.fr");
      TeamComposer.Composer teamWrapper2 = getTeamComposerWithName(searchTerm2);
      UserComposer.Composer userWrapper2 =
          getPlayerComposerWithName(searchTerm2, searchTerm2, searchTerm2 + "@toto.fr");
      InjectComposer.Composer injectWrapper = getInjectWithAllTeams();
      ExerciseComposer.Composer exerciseWrapper =
          getExerciseComposerWithName()
              .withTeam(teamWrapper1.withUser(userWrapper1))
              .withTeam(teamWrapper2.withUser(userWrapper2))
              .withTeamUsers()
              .withInject(injectWrapper);
      Exercise exercise = exerciseWrapper.persist().get();
      entityManager.flush();
      entityManager.clear();

      SearchPaginationInput search =
          PaginationFixture.simpleSearchWithAndOperator(
              "target_name", "team target", Filters.FilterOperator.contains);
      String response =
          mvc.perform(
                  post(INJECT_URI
                          + "/"
                          + exercise.getInjects().getFirst().getId()
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
          exercise.getTeams().stream()
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
      private InjectExpectationComposer.Composer getExpectationWrapperWithResult(
          InjectExpectation.EXPECTATION_TYPE type, InjectExpectation.EXPECTATION_STATUS status) {
        return injectExpectationComposer.forExpectation(
            InjectExpectationFixture.createExpectationWithTypeAndStatus(type, status));
      }

      @Test
      @DisplayName("Given specific scores, expectation type results are of correct status")
      public void givenSpecificScores_expectationTypeResultsAreOfCorrectStatus() throws Exception {
        String searchTerm = "team target";
        InjectComposer.Composer injectWrapper = getInjectWrapper();
        TeamComposer.Composer teamWrapper = getTeamComposerWithName(searchTerm);
        InjectExpectationComposer.Composer expectationHumanResponseWrapper =
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
            PaginationFixture.simpleSearchWithAndOperator(
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

  @Nested
  @WithMockUser(isAdmin = true)
  @DisplayName("With players search")
  public class WithPlayersSearch {

    private final TargetType targetType = TargetType.PLAYERS;

    private TeamComposer.Composer getTeamComposerWithName(String teamName) {
      return teamComposer.forTeam(TeamFixture.createTeamWithName(teamName));
    }

    @Test
    @DisplayName("With no player targets, return no items in page")
    public void withNoPlayerTargets_returnNoItemsInPage() throws Exception {
      Inject inject = getInjectWrapper().persist().get();
      entityManager.flush();
      entityManager.clear();

      SearchPaginationInput search =
          PaginationFixture.simpleSearchWithAndOperator(
              "", "target player", Filters.FilterOperator.eq);
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
    @DisplayName("With some players targets, return matching items in page 1")
    public void withSomePlayersTargets_returnMatchingItemsInPage1() throws Exception {
      String searchTerm = "player target";
      InjectComposer.Composer injectWrapper = getInjectWrapper();
      for (int i = 10; i < 30; i++) {
        injectWrapper.withTeam(
            getTeamComposerWithName(searchTerm + " " + i)
                .withUser(
                    getPlayerComposerWithName(
                        searchTerm + " " + i,
                        searchTerm + " " + i,
                        searchTerm + " " + i + "@toto.fr")));
      }
      Inject inject = injectWrapper.persist().get();
      entityManager.flush();
      entityManager.clear();

      SearchPaginationInput search =
          PaginationFixture.simpleSearchWithAndOperator(
              "", searchTerm, Filters.FilterOperator.contains);
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

      List<PlayerTarget> expected =
          inject.getTeams().stream()
              .sorted(Comparator.comparing(Team::getName))
              .limit(search.getSize())
              .map(
                  team -> {
                    User user = team.getUsers().getFirst();
                    return new PlayerTarget(
                        user.getId(),
                        user.getNameOrEmail(),
                        user.getTags().stream().map(Tag::getId).collect(Collectors.toSet()),
                        Set.of(team.getId()));
                  })
              .toList();

      assertThatJson(response).node("content").isEqualTo(mapper.writeValueAsString(expected));
    }

    @Test
    @DisplayName("With some players targets, return matching items in page 2")
    public void withSomePlayersTargets_returnMatchingItemsInPage2() throws Exception {
      String searchTerm = "team target";
      InjectComposer.Composer injectWrapper = getInjectWrapper();
      for (int i = 10; i < 30; i++) {
        injectWrapper.withTeam(
            getTeamComposerWithName(searchTerm + " " + i)
                .withUser(
                    getPlayerComposerWithName(
                        searchTerm + " " + i,
                        searchTerm + " " + i,
                        searchTerm + " " + i + "@toto.fr")));
      }
      Inject inject = injectWrapper.persist().get();
      entityManager.flush();
      entityManager.clear();

      SearchPaginationInput search =
          PaginationFixture.simpleSearchWithAndOperator(
              "", searchTerm, Filters.FilterOperator.contains);
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

      List<PlayerTarget> expected =
          inject.getTeams().stream()
              .skip((long) search.getPage() * search.getSize())
              .sorted(Comparator.comparing(Team::getName))
              .limit(search.getSize())
              .map(
                  team -> {
                    User user = team.getUsers().getFirst();
                    return new PlayerTarget(
                        user.getId(),
                        user.getNameOrEmail(),
                        user.getTags().stream().map(Tag::getId).collect(Collectors.toSet()),
                        Set.of(team.getId()));
                  })
              .toList();

      assertThatJson(response).node("content").isEqualTo(mapper.writeValueAsString(expected));
    }

    @Test
    @DisplayName(
        "With some players targets, return matching items for exercises with inject all teams")
    public void withSomePlayersTargets_returnMatchingItemsForExercisesWithInjectAllTeams()
        throws Exception {
      String searchTerm1 = "player target 1";
      String searchTerm2 = "player target 2";
      TeamComposer.Composer teamWrapper1 = getTeamComposerWithName(searchTerm1);
      UserComposer.Composer userWrapper1 =
          getPlayerComposerWithName(searchTerm1, searchTerm1, searchTerm1 + "@toto.fr");
      TeamComposer.Composer teamWrapper2 = getTeamComposerWithName(searchTerm2);
      UserComposer.Composer userWrapper2 =
          getPlayerComposerWithName(searchTerm2, searchTerm2, searchTerm2 + "@toto.fr");
      InjectComposer.Composer injectWrapper = getInjectWithAllTeams();
      ExerciseComposer.Composer exerciseWrapper =
          getExerciseComposerWithName()
              .withTeam(teamWrapper1.withUser(userWrapper1))
              .withTeam(teamWrapper2.withUser(userWrapper2))
              .withTeamUsers()
              .withInject(injectWrapper);
      Exercise exercise = exerciseWrapper.persist().get();
      entityManager.flush();
      entityManager.clear();

      SearchPaginationInput search =
          PaginationFixture.simpleSearchWithAndOperator(
              "", "player target", Filters.FilterOperator.contains);
      String response =
          mvc.perform(
                  post(INJECT_URI
                          + "/"
                          + exercise.getInjects().getFirst().getId()
                          + "/targets/"
                          + targetType.name()
                          + "/search")
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(mapper.writeValueAsString(search)))
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString();

      List<PlayerTarget> expected =
          exercise.getTeams().stream()
              .limit(search.getSize())
              .map(
                  team -> {
                    User user = team.getUsers().getFirst();
                    return new PlayerTarget(
                        user.getId(),
                        user.getNameOrEmail(),
                        user.getTags().stream().map(Tag::getId).collect(Collectors.toSet()),
                        Set.of(team.getId()));
                  })
              .toList();

      assertThatJson(response).node("content").isEqualTo(mapper.writeValueAsString(expected));
    }

    @Test
    @DisplayName(
        "With some players targets, return matching items for exercises with inject with 1 team")
    public void withSomePlayersTargets_returnMatchingItemsForExercisesWithInjectWith1Team()
        throws Exception {
      String searchTerm1 = "player target 1";
      String searchTerm2 = "player target 2";
      TeamComposer.Composer teamWrapper1 = getTeamComposerWithName(searchTerm1);
      UserComposer.Composer userWrapper1 =
          getPlayerComposerWithName(searchTerm1, searchTerm1, searchTerm1 + "@toto.fr");
      TeamComposer.Composer teamWrapper2 = getTeamComposerWithName(searchTerm2);
      UserComposer.Composer userWrapper2 =
          getPlayerComposerWithName(searchTerm2, searchTerm2, searchTerm2 + "@toto.fr");
      InjectComposer.Composer injectWrapper = getInjectWrapper();
      ExerciseComposer.Composer exerciseWrapper =
          getExerciseComposerWithName()
              .withTeam(teamWrapper1.withUser(userWrapper1))
              .withTeam(teamWrapper2.withUser(userWrapper2))
              .withTeamUsers()
              .withInject(injectWrapper.withTeam(teamWrapper1));
      Exercise exercise = exerciseWrapper.persist().get();
      entityManager.flush();
      entityManager.clear();

      SearchPaginationInput search =
          PaginationFixture.simpleSearchWithAndOperator(
              "", "player target", Filters.FilterOperator.contains);
      String response =
          mvc.perform(
                  post(INJECT_URI
                          + "/"
                          + exercise.getInjects().getFirst().getId()
                          + "/targets/"
                          + targetType.name()
                          + "/search")
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(mapper.writeValueAsString(search)))
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString();

      List<PlayerTarget> expected =
          exercise.getInjects().getFirst().getTeams().stream()
              .limit(search.getSize())
              .map(
                  team -> {
                    User user = team.getUsers().getFirst();
                    return new PlayerTarget(
                        user.getId(),
                        user.getNameOrEmail(),
                        user.getTags().stream().map(Tag::getId).collect(Collectors.toSet()),
                        Set.of(team.getId()));
                  })
              .toList();

      assertThatJson(response).node("content").isEqualTo(mapper.writeValueAsString(expected));
    }

    @Nested
    @DisplayName("With actual results")
    public class WithActualResults {
      private InjectExpectationComposer.Composer getExpectationWrapperWithResult(
          InjectExpectation.EXPECTATION_TYPE type, InjectExpectation.EXPECTATION_STATUS status) {
        return injectExpectationComposer.forExpectation(
            InjectExpectationFixture.createExpectationWithTypeAndStatus(type, status));
      }

      @Test
      @DisplayName("Given specific scores, expectation type results are of correct status")
      public void givenSpecificScores_expectationTypeResultsAreOfCorrectStatus() throws Exception {
        String searchTerm = "player target";
        InjectComposer.Composer injectWrapper = getInjectWrapper();
        TeamComposer.Composer teamWrapper = getTeamComposerWithName(searchTerm);
        UserComposer.Composer userWrapper =
            getPlayerComposerWithName(searchTerm, searchTerm, searchTerm + "@toto.fr");
        InjectExpectationComposer.Composer expectationHumanResponseWrapper =
            getExpectationWrapperWithResult(
                    InjectExpectation.EXPECTATION_TYPE.CHALLENGE,
                    InjectExpectation.EXPECTATION_STATUS.PENDING)
                .withTeam(teamWrapper)
                .withUser(userWrapper);
        injectWrapper.withTeam(teamWrapper.withUser(userWrapper));
        injectWrapper.withExpectation(expectationHumanResponseWrapper);
        Inject inject = injectWrapper.persist().get();
        entityManager.flush();
        entityManager.clear();

        SearchPaginationInput search =
            PaginationFixture.simpleSearchWithAndOperator(
                "", searchTerm, Filters.FilterOperator.contains);
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

        PlayerTarget expectedPlayer =
            new PlayerTarget(
                userWrapper.get().getId(),
                userWrapper.get().getName(),
                userWrapper.get().getTags().stream().map(Tag::getId).collect(Collectors.toSet()),
                Set.of(teamWrapper.get().getId()));
        expectedPlayer.setTargetHumanResponseStatus(InjectExpectation.EXPECTATION_STATUS.PENDING);
        List<PlayerTarget> expected = List.of(expectedPlayer);

        assertThatJson(response).node("content").isEqualTo(mapper.writeValueAsString(expected));
      }
    }
  }
}
