package io.openaev.injects.technical_inject;

import static io.openaev.collectors.expectations_vulnerability_manager.ExpectationsVulnerabilityManagerCollector.EXPECTATIONS_VULNERABILITY_COLLECTOR_ID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.IntegrationTest;
import io.openaev.collectors.utils.CollectorsUtils;
import io.openaev.context.TenantContext;
import io.openaev.database.model.*;
import io.openaev.database.repository.InjectExpectationRepository;
import io.openaev.execution.ExecutableInject;
import io.openaev.executors.InjectorContext;
import io.openaev.injectors.openaev.OpenAEVImplantExecutor;
import io.openaev.injectors.openaev.model.OpenAEVImplantInjectContent;
import io.openaev.integration.impl.injectors.openaev.OpenaevInjectorIntegrationFactory;
import io.openaev.model.inject.form.Expectation;
import io.openaev.utils.fixtures.*;
import io.openaev.utils.fixtures.composers.*;
import io.openaev.utilstest.RabbitMQTestListener;
import jakarta.annotation.Resource;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.transaction.annotation.Transactional;

@Transactional
@SpringBootTest
@TestExecutionListeners(
    value = {RabbitMQTestListener.class},
    mergeMode = TestExecutionListeners.MergeMode.MERGE_WITH_DEFAULTS)
public class OpenAEVImplantExecutorTest extends IntegrationTest {
  @Autowired private OpenaevInjectorIntegrationFactory openaevInjectorIntegrationFactory;
  @Autowired private InjectExpectationRepository injectExpectationRepository;
  @Autowired private InjectorContext injectorContext;
  @Autowired private io.openaev.service.InjectExpectationService injectExpectationService;
  @Autowired private io.openaev.rest.inject.service.InjectService injectService;

  @Autowired private InjectComposer injectComposer;
  @Autowired private AssetGroupComposer assetGroupComposer;
  @Autowired private EndpointComposer endpointComposer;
  @Autowired private AgentComposer agentComposer;
  @Autowired private InjectorContractComposer injectorContractComposer;
  @Autowired private CollectorComposer collectorComposer;
  @Autowired private SecurityPlatformComposer securityPlatformComposer;

  @Resource protected ObjectMapper mapper;
  @Autowired private InjectorFixture injectorFixture;

  @BeforeEach
  public void beforeEach() {
    assetGroupComposer.reset();
    endpointComposer.reset();
    agentComposer.reset();
    injectComposer.reset();
    injectorContractComposer.reset();
    collectorComposer.reset();
  }

  // An OAEV implant runs through the agent, so its contract needs an executor: this is what makes
  // ExpectationUtils build the per-agent detection/prevention rows this test asserts on.
  private static InjectorContract oaevImplantContract() {
    InjectorContract contract =
        InjectorContractFixture.createDefaultInjectorContractWithExternalId("external-id");
    contract.setNeedsExecutor(true);
    return contract;
  }

  private Inject createTechnicalInjectHelper(List<Expectation> expectationList) {
    Inject inject = InjectFixture.getDefaultInject();
    OpenAEVImplantInjectContent content = new OpenAEVImplantInjectContent();
    content.setExpectations(expectationList);
    content.setObfuscator("plain-text");
    inject.setContent(this.mapper.valueToTree(content));

    collectorComposer
        .forCollector(CollectorFixture.createDefaultCollector(CollectorsUtils.CROWDSTRIKE))
        .withSecurityPlatform(
            securityPlatformComposer.forSecurityPlatform(
                SecurityPlatformFixture.createDefault(
                    "EDR name", SecurityPlatform.SECURITY_PLATFORM_TYPE.EDR.name())))
        .persist();

    return injectComposer
        .forInject(inject)
        .withAssetGroup(
            assetGroupComposer
                .forAssetGroup(AssetGroupFixture.createDefaultAssetGroup("windows asset group"))
                .withAsset(
                    endpointComposer
                        .forEndpoint(EndpointFixture.createEndpoint())
                        .withAgent(agentComposer.forAgent(AgentFixture.createDefaultAgentService()))
                        .withAgent(
                            agentComposer.forAgent(AgentFixture.createDefaultAgentService()))))
        .withInjectorContract(
            injectorContractComposer
                .forInjectorContract(oaevImplantContract())
                .withInjector(injectorFixture.getWellKnownOaevImplantInjector()))
        .persist()
        .get();
  }

  private static Stream<Arguments> expectationTypeProvider() {
    return Stream.of(
        Arguments.of(
            "detection",
            BaseInjectExpectation.EXPECTATION_TYPE.DETECTION,
            CollectorsUtils.CROWDSTRIKE),
        Arguments.of(
            "vulnerability",
            BaseInjectExpectation.EXPECTATION_TYPE.VULNERABILITY,
            EXPECTATIONS_VULNERABILITY_COLLECTOR_ID));
  }

  @ParameterizedTest(
      name =
          "givenTechnicalInjectWith{0}Expectation_shouldComputeBaseInjectExpectationAndInjectExpectationResult")
  @MethodSource("expectationTypeProvider")
  void givenExpectation_shouldComputeInjectExpectationAndInjectExpectationResult(
      String name, BaseInjectExpectation.EXPECTATION_TYPE type, String expectedSourceId)
      throws Exception {

    // -- PREPARE --
    Expectation expectation = new Expectation();
    expectation.setName(name);
    expectation.setType(type);
    expectation.setScore(100.0);
    expectation.setExpectationGroup(false);

    openaevInjectorIntegrationFactory.registerConnectorForTenant(TenantContext.getCurrentTenant());
    io.openaev.executors.Injector openAEVImplantExecutor =
        new OpenAEVImplantExecutor(injectorContext, injectExpectationService, injectService);

    Inject inject = createTechnicalInjectHelper(List.of(expectation));
    Injection injection = mock(Injection.class);
    when(injection.getInject()).thenReturn(inject);
    ExecutableInject executableInject =
        new ExecutableInject(
            false,
            false,
            injection,
            List.of(),
            inject.getAssets(),
            inject.getAssetGroups(),
            List.of());
    Execution execution = new Execution(executableInject.isRuntime());

    // -- EXECUTE --
    openAEVImplantExecutor.process(execution, executableInject);

    // -- ASSERT --
    // Should have 4 inject expectations - 1 for asset group - 1 for the endpoint - 1 per agent
    List<BaseInjectExpectation> expectationList =
        injectExpectationRepository.findAllByInjectId(inject.getId());
    List<TechnicalInjectExpectation> technicalInjectExpectationList =
        expectationList.stream()
            .filter(e -> e instanceof TechnicalInjectExpectation)
            .map(TechnicalInjectExpectation.class::cast)
            .toList();
    assertEquals(4, expectationList.size());
    List<TechnicalInjectExpectation> assetGroupExpectations =
        technicalInjectExpectationList.stream()
            .filter(
                ie -> ie.getAgent() == null && ie.getAsset() == null && ie.getAssetGroup() != null)
            .toList();
    assertEquals(1, assetGroupExpectations.size());
    List<TechnicalInjectExpectation> endpointExpectations =
        technicalInjectExpectationList.stream()
            .filter(
                ie -> ie.getAgent() == null && ie.getAsset() != null && ie.getAssetGroup() != null)
            .toList();
    assertEquals(1, endpointExpectations.size());
    List<TechnicalInjectExpectation> agentExpectations =
        technicalInjectExpectationList.stream()
            .filter(
                ie -> ie.getAgent() != null && ie.getAsset() != null && ie.getAssetGroup() != null)
            .toList();
    assertEquals(2, agentExpectations.size());

    // BaseInjectExpectation.results.result should be set to null for all existing security
    // platforms at
    // the agent level only.
    assertTrue(assetGroupExpectations.getFirst().getResults().isEmpty());
    assertTrue(endpointExpectations.getFirst().getResults().isEmpty());
    List<InjectExpectationResult> results =
        agentExpectations.stream().flatMap(ie -> ie.getResults().stream()).toList();
    assertTrue(results.stream().allMatch(r -> r.getResult() == null));
    assertTrue(results.stream().allMatch(r -> expectedSourceId.equals(r.getSourceId())));
  }

  @Test
  @DisplayName(
      "Should create prevention expectations for asset group and agents when executing a technical inject")
  void given_technicalInjectWithPreventionExpectation_should_createAssetGroupAndAgentExpectations()
      throws Exception {
    // -- Arrange --
    Expectation expectation = new Expectation();
    expectation.setName("prevention");
    expectation.setType(BaseInjectExpectation.EXPECTATION_TYPE.PREVENTION);
    expectation.setScore(100.0);
    expectation.setExpectationGroup(false);

    openaevInjectorIntegrationFactory.registerConnectorForTenant(TenantContext.getCurrentTenant());
    io.openaev.executors.Injector openAEVImplantExecutor =
        new OpenAEVImplantExecutor(injectorContext, injectExpectationService, injectService);

    Inject inject = createTechnicalInjectHelper(List.of(expectation));
    Injection injection = mock(Injection.class);
    when(injection.getInject()).thenReturn(inject);
    ExecutableInject executableInject =
        new ExecutableInject(
            false,
            false,
            injection,
            List.of(),
            inject.getAssets(),
            inject.getAssetGroups(),
            List.of());
    Execution execution = new Execution(executableInject.isRuntime());

    // -- Act --
    openAEVImplantExecutor.process(execution, executableInject);

    // -- Assert --
    List<BaseInjectExpectation> expectations =
        injectExpectationRepository.findAllByInjectId(inject.getId());
    assertEquals(4, expectations.size());
    List<TechnicalInjectExpectation> technicalInjectExpectationList =
        expectations.stream()
            .filter(e -> e instanceof TechnicalInjectExpectation)
            .map(TechnicalInjectExpectation.class::cast)
            .toList();

    List<TechnicalInjectExpectation> assetGroupExpectations =
        technicalInjectExpectationList.stream()
            .filter(
                ie -> ie.getAgent() == null && ie.getAsset() == null && ie.getAssetGroup() != null)
            .toList();
    assertEquals(1, assetGroupExpectations.size());

    List<TechnicalInjectExpectation> agentExpectations =
        technicalInjectExpectationList.stream()
            .filter(
                ie -> ie.getAgent() != null && ie.getAsset() != null && ie.getAssetGroup() != null)
            .toList();
    assertEquals(2, agentExpectations.size());

    List<InjectExpectationResult> results =
        agentExpectations.stream().flatMap(ie -> ie.getResults().stream()).toList();
    assertFalse(results.isEmpty());
    assertTrue(results.stream().allMatch(r -> r.getResult() == null));
    assertTrue(results.stream().allMatch(r -> CollectorsUtils.CROWDSTRIKE.equals(r.getSourceId())));
  }
}
