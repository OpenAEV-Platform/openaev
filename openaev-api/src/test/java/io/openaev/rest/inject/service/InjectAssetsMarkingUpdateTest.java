package io.openaev.rest.inject.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.IntegrationTest;
import io.openaev.context.TenantContext;
import io.openaev.database.model.Agent;
import io.openaev.database.model.AssetGroup;
import io.openaev.database.model.Endpoint;
import io.openaev.database.model.ExecutionTrace;
import io.openaev.database.model.Inject;
import io.openaev.database.model.InjectStatus;
import io.openaev.database.model.InjectorContract;
import io.openaev.database.repository.AgentRepository;
import io.openaev.database.repository.AssetGroupRepository;
import io.openaev.database.repository.EndpointRepository;
import io.openaev.database.repository.ExecutionTraceRepository;
import io.openaev.database.repository.InjectRepository;
import io.openaev.database.repository.InjectStatusRepository;
import io.openaev.rest.atomic_testing.form.AtomicTestingInput;
import io.openaev.rest.inject.form.InjectInput;
import io.openaev.service.AssetGroupService;
import io.openaev.service.AtomicTestingService;
import io.openaev.utils.fixtures.AgentFixture;
import io.openaev.utils.fixtures.AssetGroupFixture;
import io.openaev.utils.fixtures.EndpointFixture;
import io.openaev.utils.fixtures.ExecutionTraceFixture;
import io.openaev.utils.fixtures.InjectFixture;
import io.openaev.utils.fixtures.InjectStatusFixture;
import io.openaev.utils.fixtures.InjectorContractFixture;
import io.openaev.utils.mockUser.WithMockUser;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * What a user cleared for GREEN only does to, and sees of, what belongs to a RED asset: editing the
 * targets of an inject or the members of a static group keeps the RED one they cannot see, and the
 * RED agent's execution traces are hidden from them.
 *
 * <p>The clearance is written straight into the transaction ({@code app.current_markings}), the way
 * the transaction aspect does it on an HTTP request, so the test isolates the ORM behaviour from
 * the group/marking resolution. Rows are read back with raw JDBC, which the statement inspector
 * does not rewrite, to see what is really stored.
 */
@Transactional
@WithMockUser
@DisplayName("Derived tables seen and edited with a lower clearance")
class InjectAssetsMarkingUpdateTest extends IntegrationTest {

  private static final String GREEN = "test-tlp-green";
  private static final String RED = "test-tlp-red";

  @Autowired private InjectService injectService;
  @Autowired private AtomicTestingService atomicTestingService;
  @Autowired private InjectRepository injectRepository;
  @Autowired private EndpointRepository endpointRepository;
  @Autowired private InjectorContractFixture injectorContractFixture;
  @Autowired private AssetGroupService assetGroupService;
  @Autowired private AssetGroupRepository assetGroupRepository;
  @Autowired private AgentRepository agentRepository;
  @Autowired private InjectStatusRepository injectStatusRepository;
  @Autowired private ExecutionTraceRepository executionTraceRepository;
  @Autowired private DataSource dataSource;

  private JdbcTemplate jdbc;
  private InjectorContract contract;
  private Endpoint green;
  private Endpoint red;
  private Endpoint added;

  @BeforeEach
  void seed() {
    jdbc = new JdbcTemplate(dataSource);
    contract = injectorContractFixture.getWellKnownSingleEmailContract();
    green = endpointRepository.save(EndpointFixture.createEndpoint());
    red = endpointRepository.save(EndpointFixture.createEndpoint());
    added = endpointRepository.save(EndpointFixture.createEndpoint());
  }

  private Inject injectTargetingGreenAndRed() {
    Inject inject = InjectFixture.getInjectForEmailContract(contract);
    inject.setAssets(new ArrayList<>(List.of(green, red)));
    inject = injectRepository.save(inject);
    markAndForget();
    return inject;
  }

  /**
   * Marks the assets out of band (an ORM write of a marking is itself guarded by the rewrite), then
   * forgets the persistence context, so the code under test reloads through the rewritten SQL.
   */
  private void markAndForget() {
    entityManager.flush();
    jdbc.update(
        "UPDATE assets SET marking_ids = ? WHERE asset_id = ?",
        new String[] {GREEN},
        green.getId());
    jdbc.update(
        "UPDATE assets SET marking_ids = ? WHERE asset_id = ?", new String[] {RED}, red.getId());
    entityManager.clear();
  }

  private void actAsGreenUser() {
    entityManager
        .createNativeQuery("SELECT set_config('app.current_tenants', :tenants, true)")
        .setParameter("tenants", TenantContext.getCurrentTenant())
        .getSingleResult();
    entityManager
        .createNativeQuery("SELECT set_config('app.current_markings', :markings, true)")
        .setParameter("markings", GREEN)
        .getSingleResult();
  }

  private List<String> storedTargets(String injectId) {
    return jdbc.queryForList(
        "SELECT asset_id FROM injects_assets WHERE inject_id = ?", String.class, injectId);
  }

  @Nested
  @DisplayName("scenario / simulation inject (InjectService.updateInject)")
  class ScenarioInject {

    @Test
    @DisplayName("given a hidden RED target, should keep it when a GREEN user adds an asset")
    void given_hiddenTarget_should_keepIt_when_addingAnAsset() {
      // -- ARRANGE --
      Inject inject = injectTargetingGreenAndRed();
      actAsGreenUser();
      InjectInput input = new InjectInput();
      input.setTitle(inject.getTitle());
      input.setContent(new ObjectMapper().createObjectNode());
      input.setDependsDuration(0L);
      // What the GREEN user's form sends: the targets they can see, plus the new one.
      input.setAssets(List.of(green.getId(), added.getId()));

      // -- ACT --
      injectService.updateInject(inject.getId(), input);
      entityManager.flush();

      // -- ASSERT --
      assertThat(storedTargets(inject.getId()))
          .containsExactlyInAnyOrder(green.getId(), red.getId(), added.getId());
    }
  }

  @Nested
  @DisplayName("atomic testing (AtomicTestingService.createOrUpdate)")
  class AtomicTesting {

    @Test
    @DisplayName("given a hidden RED target, should keep it when a GREEN user adds an asset")
    void given_hiddenTarget_should_keepIt_when_addingAnAsset() {
      // -- ARRANGE --
      Inject inject = injectTargetingGreenAndRed();
      actAsGreenUser();
      AtomicTestingInput input = InjectFixture.createAtomicTesting(inject.getTitle(), null);
      input.setInjectorContract(contract.getId());
      input.setAssets(List.of(green.getId(), added.getId()));

      // -- ACT --
      atomicTestingService.createOrUpdate(input, inject.getId());
      entityManager.flush();

      // -- ASSERT --
      assertThat(storedTargets(inject.getId()))
          .containsExactlyInAnyOrder(green.getId(), red.getId(), added.getId());
    }
  }

  @Nested
  @DisplayName("static asset group (AssetGroupService.updateAssetsOnAssetGroup)")
  class StaticAssetGroup {

    @Test
    @DisplayName("given a hidden RED member, should keep it when a GREEN user adds an asset")
    void given_hiddenMember_should_keepIt_when_addingAnAsset() {
      // -- ARRANGE --
      AssetGroup group = AssetGroupFixture.createDefaultAssetGroup("marking-group");
      group.setAssets(new ArrayList<>(List.of(green, red)));
      String groupId = assetGroupRepository.save(group).getId();
      markAndForget();
      actAsGreenUser();
      AssetGroup loaded = assetGroupRepository.findById(groupId).orElseThrow();

      // -- ACT --
      assetGroupService.updateAssetsOnAssetGroup(loaded, List.of(green.getId(), added.getId()));
      entityManager.flush();

      // -- ASSERT --
      assertThat(
              jdbc.queryForList(
                  "SELECT asset_id FROM asset_groups_assets WHERE asset_group_id = ?",
                  String.class,
                  groupId))
          .containsExactlyInAnyOrder(green.getId(), red.getId(), added.getId());
    }
  }

  @Nested
  @DisplayName("execution traces")
  class ExecutionTraces {

    @Test
    @DisplayName("given traces of a GREEN and a RED agent, should show the GREEN user only GREEN's")
    void given_tracesOfARedAgent_should_hideThem() {
      // -- ARRANGE --
      Inject inject = injectRepository.save(InjectFixture.getInjectForEmailContract(contract));
      InjectStatus status = InjectStatusFixture.createPendingInjectStatus();
      status.setInject(inject);
      status = injectStatusRepository.save(status);
      Agent greenAgent = agentRepository.save(AgentFixture.createAgent(green, "green-agent"));
      Agent redAgent = agentRepository.save(AgentFixture.createAgent(red, "red-agent"));
      for (Agent agent : List.of(greenAgent, redAgent)) {
        ExecutionTrace trace = ExecutionTraceFixture.createDefaultExecutionTraceComplete();
        trace.setInjectStatus(status);
        trace.setAgent(agent);
        executionTraceRepository.save(trace);
      }
      markAndForget();
      actAsGreenUser();

      // -- ACT --
      List<ExecutionTrace> greenTraces =
          executionTraceRepository.findByInjectIdAndAgentId(inject.getId(), greenAgent.getId());
      List<ExecutionTrace> redTraces =
          executionTraceRepository.findByInjectIdAndAgentId(inject.getId(), redAgent.getId());

      // -- ASSERT --
      assertThat(greenTraces).hasSize(1);
      assertThat(redTraces).isEmpty();
      assertThat(agentRepository.findById(redAgent.getId())).isEmpty();
    }
  }
}
