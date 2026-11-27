package io.openaev.rest.inject.service;

import static io.openaev.utils.fixtures.AssetGroupFixture.createAssetGroupWithDynamicFilter;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;

import io.openaev.IntegrationTest;
import io.openaev.database.model.*;
import io.openaev.database.repository.InjectRepository;
import io.openaev.healthcheck.dto.HealthCheck;
import io.openaev.utils.fixtures.*;
import io.openaev.utils.fixtures.composers.*;
import java.util.List;
import java.util.UUID;
import org.assertj.core.groups.Tuple;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

@TestInstance(PER_CLASS)
@Transactional
@DisplayName("InjectService health checks of several injects")
class InjectServiceHealthChecksTest extends IntegrationTest {

  @Autowired private InjectService injectService;
  @Autowired private InjectRepository injectRepository;
  @Autowired private InjectComposer injectComposer;
  @Autowired private InjectorContractComposer injectorContractComposer;
  @Autowired private AssetGroupComposer assetGroupComposer;
  @Autowired private EndpointComposer endpointComposer;
  @Autowired private AgentComposer agentComposer;
  @Autowired private ExecutorFixture executorFixture;

  private List<String> injectIdsOnAgentGroup;
  private String injectIdOnEmptyGroup;

  @BeforeEach
  void setUp() {
    injectComposer.reset();
    injectorContractComposer.reset();
    assetGroupComposer.reset();
    endpointComposer.reset();
    agentComposer.reset();

    String hostname = "checks-" + UUID.randomUUID();
    Endpoint endpoint = EndpointFixture.createEndpoint();
    endpoint.setHostname(hostname);
    endpointComposer
        .forEndpoint(endpoint)
        .withAgent(
            agentComposer.forAgent(
                AgentFixture.createDefaultAgentSession(executorFixture.getDefaultExecutor())))
        .persist();

    InjectorContract contract = InjectorContractFixture.createDefaultInjectorContract();
    contract.setNeedsExecutor(true);
    InjectorContractComposer.Composer contractWrapper =
        injectorContractComposer
            .forInjectorContract(contract)
            .withInjector(InjectorFixture.createDefaultPayloadInjector());
    AssetGroupComposer.Composer agentGroup =
        assetGroupComposer.forAssetGroup(
            createAssetGroupWithDynamicFilter("agent group", hostnameFilter(hostname)));
    AssetGroupComposer.Composer emptyGroup =
        assetGroupComposer.forAssetGroup(
            createAssetGroupWithDynamicFilter("empty group", hostnameFilter("none-" + hostname)));

    injectIdsOnAgentGroup =
        List.of(
            injectOn(contractWrapper, agentGroup),
            injectOn(contractWrapper, agentGroup),
            injectOn(contractWrapper, agentGroup));
    injectIdOnEmptyGroup = injectOn(contractWrapper, emptyGroup);
    entityManager.flush();
    entityManager.clear();
  }

  private String injectOn(
      InjectorContractComposer.Composer contractWrapper, AssetGroupComposer.Composer group) {
    return injectComposer
        .forInject(InjectFixture.getDefaultInject())
        .withInjectorContract(contractWrapper)
        .withAssetGroup(group)
        .persist()
        .get()
        .getId();
  }

  private static Filters.FilterGroup hostnameFilter(String hostname) {
    return Filters.FilterGroup.builder()
        .filters(
            List.of(
                Filters.Filter.builder()
                    .key("asset_hostname")
                    .operator(Filters.FilterOperator.eq)
                    .values(List.of(hostname))
                    .build()))
        .build();
  }

  private List<Inject> injects(List<String> ids) {
    return injectRepository.findAllById(ids);
  }

  private static List<Tuple> keysOf(List<HealthCheck> healthChecks) {
    return healthChecks.stream()
        .map(
            check -> tuple(check.getType(), check.getDetail(), check.getStatus(), check.getField()))
        .distinct()
        .toList();
  }

  @Test
  @DisplayName(
      "given injects targeting the same dynamic asset groups, should return the checks of each inject")
  void given_injectsTargetingTheSameDynamicGroups_should_returnTheChecksOfEachInject() {
    // Arrange
    List<String> ids =
        List.of(
            injectIdsOnAgentGroup.get(0),
            injectIdsOnAgentGroup.get(1),
            injectIdsOnAgentGroup.get(2),
            injectIdOnEmptyGroup);
    List<Inject> injects = injects(ids);

    // Act
    List<HealthCheck> sharedChecks = injectService.runChecksOfInjects(injects);
    List<HealthCheck> checksOfEachInject =
        injects.stream().flatMap(inject -> injectService.runChecks(inject).stream()).toList();
    List<HealthCheck> agentGroupChecks =
        injectService.runChecksOfInjects(injects(injectIdsOnAgentGroup));

    // Assert
    assertThat(keysOf(sharedChecks))
        .containsExactlyInAnyOrderElementsOf(keysOf(checksOfEachInject))
        .contains(
            tuple(
                HealthCheck.Type.AGENT_OR_EXECUTOR,
                HealthCheck.Detail.EMPTY,
                HealthCheck.Status.ERROR,
                null));
    assertThat(agentGroupChecks)
        .as("the group resolves the endpoint and its agent for every inject targeting it")
        .extracting(HealthCheck::getType)
        .doesNotContain(HealthCheck.Type.AGENT_OR_EXECUTOR);
  }

  @Test
  @DisplayName(
      "given more injects targeting the same dynamic asset group, should not run more queries")
  void given_moreInjectsTargetingTheSameDynamicGroup_should_notRunMoreQueries() {
    // Arrange
    Statistics stats =
        entityManager.getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics();
    // The statistics belong to the session factory shared by every test: put them back as found
    boolean statisticsWereEnabled = stats.isStatisticsEnabled();
    stats.setStatisticsEnabled(true);
    long queriesForOneInject;
    long queriesForThreeInjects;
    try {
      // Act
      List<Inject> oneInject = injects(injectIdsOnAgentGroup.subList(0, 1));
      stats.clear();
      injectService.runChecksOfInjects(oneInject);
      queriesForOneInject = stats.getQueryExecutionCount();
      entityManager.clear();
      List<Inject> threeInjects = injects(injectIdsOnAgentGroup);
      stats.clear();
      injectService.runChecksOfInjects(threeInjects);
      queriesForThreeInjects = stats.getQueryExecutionCount();
    } finally {
      stats.setStatisticsEnabled(statisticsWereEnabled);
    }

    // Assert
    assertThat(queriesForThreeInjects)
        .as("the collectors, the injectors and the group members are queried once for all injects")
        .isEqualTo(queriesForOneInject);
  }
}
