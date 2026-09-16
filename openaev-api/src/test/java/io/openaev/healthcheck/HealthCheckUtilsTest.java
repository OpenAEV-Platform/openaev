package io.openaev.healthcheck;

import static io.openaev.helper.ObjectMapperHelper.openAEVJsonMapper;
import static io.openaev.injector_contract.fields.ContractText.textField;
import static io.openaev.utils.fixtures.InjectorContractFixture.*;
import static io.openaev.utils.fixtures.InjectorContractFixture.addField;
import static io.openaev.utils.fixtures.InjectorFixture.createDefaultPayloadInjector;
import static io.openaev.utils.fixtures.PayloadFixture.createCommand;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.database.model.Command;
import io.openaev.database.model.Injector;
import io.openaev.database.model.InjectorContract;
import io.openaev.database.model.ScopeRuleSelectedMode;
import io.openaev.database.model.ScopeRuleValueType;
import io.openaev.database.model.Step;
import io.openaev.database.model.Workflow;
import io.openaev.database.model.WorkflowScopeRule;
import io.openaev.healthcheck.dto.HealthCheck;
import io.openaev.healthcheck.utils.HealthCheckUtils;
import io.openaev.utils.fixtures.composers.DomainComposer;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
public class HealthCheckUtilsTest {

  private final ObjectMapper mapper = openAEVJsonMapper();

  @Autowired private HealthCheckUtils healthCheckUtils;
  @Autowired private DomainComposer domainComposer;

  private InjectorContract prepareInjectorContract() throws JsonProcessingException {
    Injector injector = createDefaultPayloadInjector();
    Command payloadCommand = createCommand("cmd", "whoami", List.of(), "whoami");
    return createPayloadInjectorContractWithDefaultDomain(injector, payloadCommand);
  }

  @Nested
  class MandatoryAssetTests {

    @Test
    void given_an_injector_contract_with_asset_mandatory_and_an_asset_should_be_ready()
        throws JsonProcessingException {
      // -- PREPARE --
      InjectorContract injectorContract = prepareInjectorContract();
      addField(injectorContract, mapper, buildAssetField(true));
      boolean allTeams = false;
      List<String> teams = new ArrayList<>();
      List<String> assets = List.of("assetId");
      List<String> assetGroups = new ArrayList<>();

      // -- EXECUTE --
      boolean isReady =
          healthCheckUtils
              .runContentChecks(
                  injectorContract,
                  injectorContract.getConvertedContent(),
                  allTeams,
                  teams,
                  assets,
                  assetGroups)
              .isEmpty();

      // -- ASSERT --
      assertTrue(isReady);
    }

    @Test
    void given_an_injector_contract_with_asset_mandatory_and_no_asset_should_not_be_ready()
        throws JsonProcessingException {
      // -- PREPARE --
      InjectorContract injectorContract = prepareInjectorContract();
      addField(injectorContract, mapper, buildAssetField(true));
      boolean allTeams = false;
      List<String> teams = new ArrayList<>();
      List<String> assets = new ArrayList<>();
      List<String> assetGroups = new ArrayList<>();

      // -- EXECUTE --
      boolean isReady =
          healthCheckUtils
              .runContentChecks(
                  injectorContract,
                  injectorContract.getConvertedContent(),
                  allTeams,
                  teams,
                  assets,
                  assetGroups)
              .isEmpty();

      // -- ASSERT --
      assertFalse(isReady);
    }

    @Test
    void given_an_injector_contract_with_asset_optional_and_an_asset_should_be_ready()
        throws JsonProcessingException {
      // -- PREPARE --
      InjectorContract injectorContract = prepareInjectorContract();
      addField(injectorContract, mapper, buildAssetField(true));
      boolean allTeams = false;
      List<String> teams = new ArrayList<>();
      List<String> assets = List.of("assetId");
      List<String> assetGroups = new ArrayList<>();

      // -- EXECUTE --
      boolean isReady =
          healthCheckUtils
              .runContentChecks(
                  injectorContract,
                  injectorContract.getConvertedContent(),
                  allTeams,
                  teams,
                  assets,
                  assetGroups)
              .isEmpty();

      // -- ASSERT --
      assertTrue(isReady);
    }

    @Test
    void given_an_injector_contract_with_asset_optional_and_not_asset_should_be_ready()
        throws JsonProcessingException {
      // -- PREPARE --
      InjectorContract injectorContract = prepareInjectorContract();
      addField(injectorContract, mapper, buildAssetField(false));
      boolean allTeams = false;
      List<String> teams = new ArrayList<>();
      List<String> assets = new ArrayList<>();
      List<String> assetGroups = new ArrayList<>();

      // -- EXECUTE --
      boolean isReady =
          healthCheckUtils
              .runContentChecks(
                  injectorContract,
                  injectorContract.getConvertedContent(),
                  allTeams,
                  teams,
                  assets,
                  assetGroups)
              .isEmpty();

      // -- ASSERT --
      assertTrue(isReady);
    }
  }

  @Nested
  class MandatoryGroupTests {

    @Test
    void given_an_injector_contract_with_mandatory_groups_and_an_element_should_be_ready()
        throws JsonProcessingException {
      // -- PREPARE --
      InjectorContract injectorContract = prepareInjectorContract();
      addField(injectorContract, mapper, buildMandatoryGroup());
      boolean allTeams = false;
      List<String> teams = new ArrayList<>();
      List<String> assets = List.of("assetId");
      List<String> assetGroups = new ArrayList<>();

      // -- EXECUTE --
      boolean isReady =
          healthCheckUtils
              .runContentChecks(
                  injectorContract,
                  injectorContract.getConvertedContent(),
                  allTeams,
                  teams,
                  assets,
                  assetGroups)
              .isEmpty();

      // -- ASSERT --
      assertTrue(isReady);
    }

    @Test
    void given_an_injector_contract_with_mandatory_groups_and_full_elements_should_be_ready()
        throws JsonProcessingException {
      // -- PREPARE --
      InjectorContract injectorContract = prepareInjectorContract();
      addField(injectorContract, mapper, buildMandatoryGroup());
      boolean allTeams = false;
      List<String> teams = new ArrayList<>();
      List<String> assets = List.of("assetId");
      List<String> assetGroups = List.of("assetGroupId");

      // -- EXECUTE --
      boolean isReady =
          healthCheckUtils
              .runContentChecks(
                  injectorContract,
                  injectorContract.getConvertedContent(),
                  allTeams,
                  teams,
                  assets,
                  assetGroups)
              .isEmpty();

      // -- ASSERT --
      assertTrue(isReady);
    }

    @Test
    void given_an_injector_contract_with_mandatory_groups_and_no_element_should_not_be_ready()
        throws JsonProcessingException {
      // -- PREPARE --
      InjectorContract injectorContract = prepareInjectorContract();
      addField(injectorContract, mapper, buildMandatoryGroup());
      boolean allTeams = false;
      List<String> teams = new ArrayList<>();
      List<String> assets = new ArrayList<>();
      List<String> assetGroups = new ArrayList<>();

      // -- EXECUTE --
      boolean isReady =
          healthCheckUtils
              .runContentChecks(
                  injectorContract,
                  injectorContract.getConvertedContent(),
                  allTeams,
                  teams,
                  assets,
                  assetGroups)
              .isEmpty();

      // -- ASSERT --
      assertFalse(isReady);
    }

    @Nested
    class MandatoryOnConditionTests {

      @Test
      void
          given_an_injector_contract_with_mandatory_on_condition_and_no_element_should_not_be_ready()
              throws JsonProcessingException {
        // -- PREPARE --
        InjectorContract injectorContract = prepareInjectorContract();
        addField(injectorContract, mapper, buildMandatoryOnCondition());
        boolean allTeams = false;
        List<String> teams = new ArrayList<>();
        List<String> assets = List.of();
        List<String> assetGroups = List.of();

        // -- EXECUTE --
        boolean isReady =
            healthCheckUtils
                .runContentChecks(
                    injectorContract,
                    injectorContract.getConvertedContent(),
                    allTeams,
                    teams,
                    assets,
                    assetGroups)
                .isEmpty();

        // -- ASSERT --
        assertFalse(isReady);
      }

      @Test
      void given_an_injector_contract_with_mandatory_on_condition_and_element_should_be_ready()
          throws JsonProcessingException {
        // -- PREPARE --
        InjectorContract injectorContract = prepareInjectorContract();
        addField(injectorContract, mapper, buildMandatoryOnCondition());
        boolean allTeams = false;
        List<String> teams = new ArrayList<>();
        List<String> assets = List.of("assetId");
        List<String> assetGroups = List.of();

        // -- EXECUTE --
        boolean isReady =
            healthCheckUtils
                .runContentChecks(
                    injectorContract,
                    injectorContract.getConvertedContent(),
                    allTeams,
                    teams,
                    assets,
                    assetGroups)
                .isEmpty();

        // -- ASSERT --
        assertTrue(isReady);
      }

      @Test
      void
          given_an_injector_contract_with_mandatory_on_condition_and_condition_element_should_not_be_ready()
              throws JsonProcessingException {
        // -- PREPARE --
        InjectorContract injectorContract = prepareInjectorContract();
        addField(injectorContract, mapper, buildMandatoryOnCondition());
        boolean allTeams = false;
        List<String> teams = new ArrayList<>();
        List<String> assets = List.of();
        List<String> assetGroups = List.of("assetGroupId");

        // -- EXECUTE --
        boolean isReady =
            healthCheckUtils
                .runContentChecks(
                    injectorContract,
                    injectorContract.getConvertedContent(),
                    allTeams,
                    teams,
                    assets,
                    assetGroups)
                .isEmpty();

        // -- ASSERT --
        assertFalse(isReady);
      }

      @Test
      void given_an_injector_contract_with_mandatory_on_condition_and_all_elements_should_be_ready()
          throws JsonProcessingException {
        // -- PREPARE --
        InjectorContract injectorContract = prepareInjectorContract();
        addField(injectorContract, mapper, buildMandatoryOnCondition());
        boolean allTeams = false;
        List<String> teams = new ArrayList<>();
        List<String> assets = List.of("assetId");
        List<String> assetGroups = List.of("assetGroupId");

        // -- EXECUTE --
        boolean isReady =
            healthCheckUtils
                .runContentChecks(
                    injectorContract,
                    injectorContract.getConvertedContent(),
                    allTeams,
                    teams,
                    assets,
                    assetGroups)
                .isEmpty();

        // -- ASSERT --
        assertTrue(isReady);
      }
    }
  }

  @Nested
  class MandatoryOnConditionValueTests {

    @Test
    void given_mandatory_on_condition_with_specific_value_when_condition_matches_should_be_ready()
        throws JsonProcessingException {
      // -- PREPARE --
      InjectorContract injectorContract = prepareInjectorContract();
      addField(injectorContract, mapper, buildMandatoryOnConditionValue("assetGroupId"));
      boolean allTeams = false;
      List<String> teams = new ArrayList<>();
      List<String> assets = List.of("assetId");
      List<String> assetGroups = List.of("assetGroupId");

      // -- EXECUTE --
      boolean isReady =
          healthCheckUtils
              .runContentChecks(
                  injectorContract,
                  injectorContract.getConvertedContent(),
                  allTeams,
                  teams,
                  assets,
                  assetGroups)
              .isEmpty();

      // -- ASSERT --
      assertTrue(isReady);
    }

    @Test
    void given_mandatory_on_condition_with_specific_values_when_condition_matches_should_be_ready()
        throws JsonProcessingException {
      // -- PREPARE --
      InjectorContract injectorContract = prepareInjectorContract();
      addField(
          injectorContract,
          mapper,
          buildMandatoryOnConditionValue(List.of("assetGroupId", "assetGroupId2")));
      boolean allTeams = false;
      List<String> teams = new ArrayList<>();
      List<String> assets = List.of("assetId");
      List<String> assetGroups = List.of("assetGroupId2");

      // -- EXECUTE --
      boolean isReady =
          healthCheckUtils
              .runContentChecks(
                  injectorContract,
                  injectorContract.getConvertedContent(),
                  allTeams,
                  teams,
                  assets,
                  assetGroups)
              .isEmpty();

      // -- ASSERT --
      assertTrue(isReady);
    }

    @Test
    void
        given_mandatory_on_condition_with_specific_value_when_condition_not_matches_should_not_be_ready()
            throws JsonProcessingException {
      // -- PREPARE --
      InjectorContract injectorContract = prepareInjectorContract();
      addField(injectorContract, mapper, buildMandatoryOnConditionValue("assetGroupId"));
      boolean allTeams = false;
      List<String> teams = new ArrayList<>();
      List<String> assets = List.of();
      List<String> assetGroups = List.of("assetGroupId");

      // -- EXECUTE --
      boolean isReady =
          healthCheckUtils
              .runContentChecks(
                  injectorContract,
                  injectorContract.getConvertedContent(),
                  allTeams,
                  teams,
                  assets,
                  assetGroups)
              .isEmpty();

      // -- ASSERT --
      assertFalse(isReady);
    }

    @Test
    void
        given_mandatory_on_condition_with_not_specific_value_when_condition_not_matches_should_be_ready()
            throws JsonProcessingException {
      // -- PREPARE --
      InjectorContract injectorContract = prepareInjectorContract();
      addField(injectorContract, mapper, buildMandatoryOnConditionValue("assetGroupId"));
      boolean allTeams = false;
      List<String> teams = new ArrayList<>();
      List<String> assets = List.of();
      List<String> assetGroups = List.of("assetGroupId2");

      // -- EXECUTE --
      boolean isReady =
          healthCheckUtils
              .runContentChecks(
                  injectorContract,
                  injectorContract.getConvertedContent(),
                  allTeams,
                  teams,
                  assets,
                  assetGroups)
              .isEmpty();

      // -- ASSERT --
      assertTrue(isReady);
    }

    @Test
    void
        given_mandatory_on_condition_with_not_specific_values_when_condition_not_matches_should_be_ready()
            throws JsonProcessingException {
      // -- PREPARE --
      InjectorContract injectorContract = prepareInjectorContract();
      addField(
          injectorContract,
          mapper,
          buildMandatoryOnConditionValue(List.of("assetGroupId", "assetGroupId3")));
      boolean allTeams = false;
      List<String> teams = new ArrayList<>();
      List<String> assets = List.of();
      List<String> assetGroups = List.of("assetGroupId2");

      // -- EXECUTE --
      boolean isReady =
          healthCheckUtils
              .runContentChecks(
                  injectorContract,
                  injectorContract.getConvertedContent(),
                  allTeams,
                  teams,
                  assets,
                  assetGroups)
              .isEmpty();

      // -- ASSERT --
      assertTrue(isReady);
    }
  }

  @Nested
  class DefaultValueTests {

    @Test
    void given_text_field_with_default_value_and_no_content_should_be_ready()
        throws JsonProcessingException {
      // -- PREPARE --
      InjectorContract injectorContract = prepareInjectorContract();
      addField(injectorContract, mapper, List.of(textField("title", "title", "Default title")));

      boolean allTeams = false;
      List<String> teams = new ArrayList<>();
      List<String> assets = new ArrayList<>();
      List<String> assetGroups = new ArrayList<>();

      // -- EXECUTE --
      boolean isReady =
          healthCheckUtils
              .runContentChecks(
                  injectorContract,
                  injectorContract.getConvertedContent(),
                  allTeams,
                  teams,
                  assets,
                  assetGroups)
              .isEmpty();

      // -- ASSERT --
      assertTrue(isReady);
    }

    @Test
    void given_text_field_without_default_value_and_no_content_should_not_be_ready()
        throws JsonProcessingException {
      // -- PREPARE --
      InjectorContract injectorContract = prepareInjectorContract();
      addField(injectorContract, mapper, List.of(textField("title", "title")));

      boolean allTeams = false;
      List<String> teams = new ArrayList<>();
      List<String> assets = new ArrayList<>();
      List<String> assetGroups = new ArrayList<>();

      // -- EXECUTE --
      boolean isReady =
          healthCheckUtils
              .runContentChecks(
                  injectorContract,
                  injectorContract.getConvertedContent(),
                  allTeams,
                  teams,
                  assets,
                  assetGroups)
              .isEmpty();

      // -- ASSERT --
      assertFalse(isReady);
    }
  }

  @Nested
  class ScopeDefinitionChecksTests {

    @Test
    void given_no_scope_rules_should_return_scope_definition_empty_warning() {
      // -- PREPARE --
      Workflow workflow = new Workflow();

      // -- EXECUTE --
      List<HealthCheck> checks = healthCheckUtils.runScopeDefinitionChecks(workflow);

      // -- ASSERT --
      assertTrue(
          checks.stream()
              .anyMatch(
                  check ->
                      HealthCheck.Type.SCOPE_DEFINITION.equals(check.getType())
                          && HealthCheck.Detail.EMPTY.equals(check.getDetail())));
    }

    @Test
    void given_only_denylist_scope_rules_should_return_scope_definition_empty_warning() {
      // -- PREPARE --
      Workflow workflow = new Workflow();
      workflow.setWorkflowScopeRules(
          List.of(buildScopeRule(ScopeRuleSelectedMode.DENYLIST, "asset-1")));

      // -- EXECUTE --
      List<HealthCheck> checks = healthCheckUtils.runScopeDefinitionChecks(workflow);

      // -- ASSERT --
      assertTrue(
          checks.stream()
              .anyMatch(
                  check ->
                      HealthCheck.Type.SCOPE_DEFINITION.equals(check.getType())
                          && HealthCheck.Detail.EMPTY.equals(check.getDetail())));
    }

    @Test
    void given_allowlist_scope_rule_with_value_should_not_return_scope_definition_empty_warning() {
      // -- PREPARE --
      Workflow workflow = new Workflow();
      workflow.setWorkflowScopeRules(
          List.of(buildScopeRule(ScopeRuleSelectedMode.ALLOWLIST, "asset-1")));

      // -- EXECUTE --
      List<HealthCheck> checks = healthCheckUtils.runScopeDefinitionChecks(workflow);

      // -- ASSERT --
      assertFalse(
          checks.stream()
              .anyMatch(
                  check ->
                      HealthCheck.Type.SCOPE_DEFINITION.equals(check.getType())
                          && HealthCheck.Detail.EMPTY.equals(check.getDetail())));
    }

    // -- Payload-type-aware checks (technical vs audience/tabletop steps) --

    @Test
    void given_audience_step_and_technical_only_scope_should_return_missing_audience_error() {
      // -- PREPARE --
      Workflow workflow = new Workflow();
      workflow.setWorkflowScopeRules(
          List.of(
              buildScopeRule(
                  ScopeRuleSelectedMode.ALLOWLIST, ScopeRuleValueType.ASSET_ID, "asset-1")));
      workflow.setSteps(List.of(buildStep(EMAIL_STEP_DATA)));

      // -- EXECUTE --
      List<HealthCheck> checks = healthCheckUtils.runScopeDefinitionChecks(workflow);

      // -- ASSERT --
      assertTrue(hasDetail(checks, HealthCheck.Detail.MISSING_AUDIENCE_TARGETS));
      assertTrue(
          checks.stream()
              .anyMatch(
                  check ->
                      HealthCheck.Detail.MISSING_AUDIENCE_TARGETS.equals(check.getDetail())
                          && HealthCheck.Status.ERROR.equals(check.getStatus())));
      // The asset entry is consumed by no step: flagged as ineffective, warning only.
      assertTrue(hasDetail(checks, HealthCheck.Detail.INEFFECTIVE_TECHNICAL_TARGETS));
      assertFalse(hasDetail(checks, HealthCheck.Detail.EMPTY));
    }

    @Test
    void given_technical_step_and_audience_only_scope_should_return_missing_technical_error() {
      // -- PREPARE --
      Workflow workflow = new Workflow();
      workflow.setWorkflowScopeRules(
          List.of(
              buildScopeRule(
                  ScopeRuleSelectedMode.ALLOWLIST, ScopeRuleValueType.TEAM_ID, "team-1")));
      workflow.setSteps(List.of(buildStep(PAYLOAD_STEP_DATA)));

      // -- EXECUTE --
      List<HealthCheck> checks = healthCheckUtils.runScopeDefinitionChecks(workflow);

      // -- ASSERT --
      assertTrue(hasDetail(checks, HealthCheck.Detail.MISSING_TECHNICAL_TARGETS));
      assertTrue(hasDetail(checks, HealthCheck.Detail.INEFFECTIVE_AUDIENCE_TARGETS));
      assertFalse(hasDetail(checks, HealthCheck.Detail.MISSING_AUDIENCE_TARGETS));
    }

    @Test
    void given_audience_step_and_scope_with_player_entry_should_return_no_check() {
      // -- PREPARE --
      Workflow workflow = new Workflow();
      workflow.setWorkflowScopeRules(
          List.of(
              buildScopeRule(
                  ScopeRuleSelectedMode.ALLOWLIST, ScopeRuleValueType.PLAYER_ID, "player-1")));
      workflow.setSteps(List.of(buildStep(EMAIL_STEP_DATA)));

      // -- EXECUTE --
      List<HealthCheck> checks = healthCheckUtils.runScopeDefinitionChecks(workflow);

      // -- ASSERT --
      assertTrue(checks.isEmpty());
    }

    @Test
    void given_audience_step_with_explicit_teams_should_not_require_audience_entries() {
      // -- PREPARE --
      Workflow workflow = new Workflow();
      workflow.setWorkflowScopeRules(
          List.of(
              buildScopeRule(
                  ScopeRuleSelectedMode.ALLOWLIST, ScopeRuleValueType.ASSET_ID, "asset-1")));
      workflow.setSteps(List.of(buildStep(EMAIL_STEP_WITH_EXPLICIT_TEAMS_DATA)));

      // -- EXECUTE --
      List<HealthCheck> checks = healthCheckUtils.runScopeDefinitionChecks(workflow);

      // -- ASSERT --
      assertFalse(hasDetail(checks, HealthCheck.Detail.MISSING_AUDIENCE_TARGETS));
    }

    @Test
    void
        given_audience_scope_entries_and_only_explicit_audience_steps_should_return_ineffective_audience_warning() {
      // -- PREPARE -- every audience step carries an explicit drawer audience, so the scope's
      // team entry is never consumed by the fallback and must be flagged as ineffective.
      Workflow workflow = new Workflow();
      workflow.setWorkflowScopeRules(
          List.of(
              buildScopeRule(
                  ScopeRuleSelectedMode.ALLOWLIST, ScopeRuleValueType.TEAM_ID, "team-1")));
      workflow.setSteps(List.of(buildStep(EMAIL_STEP_WITH_EXPLICIT_TEAMS_DATA)));

      // -- EXECUTE --
      List<HealthCheck> checks = healthCheckUtils.runScopeDefinitionChecks(workflow);

      // -- ASSERT --
      assertTrue(hasDetail(checks, HealthCheck.Detail.INEFFECTIVE_AUDIENCE_TARGETS));
      assertFalse(hasDetail(checks, HealthCheck.Detail.MISSING_AUDIENCE_TARGETS));
    }

    @Test
    void given_audience_scope_entries_and_scope_relying_audience_step_should_not_warn() {
      // -- PREPARE -- the audience step has no drawer audience: it consumes the scope entry.
      Workflow workflow = new Workflow();
      workflow.setWorkflowScopeRules(
          List.of(
              buildScopeRule(
                  ScopeRuleSelectedMode.ALLOWLIST, ScopeRuleValueType.TEAM_ID, "team-1")));
      workflow.setSteps(List.of(buildStep(EMAIL_STEP_DATA)));

      // -- EXECUTE --
      List<HealthCheck> checks = healthCheckUtils.runScopeDefinitionChecks(workflow);

      // -- ASSERT --
      assertFalse(hasDetail(checks, HealthCheck.Detail.INEFFECTIVE_AUDIENCE_TARGETS));
      assertFalse(hasDetail(checks, HealthCheck.Detail.MISSING_AUDIENCE_TARGETS));
    }

    @Test
    void given_empty_scope_with_steps_should_only_return_empty_warning() {
      // -- PREPARE --
      Workflow workflow = new Workflow();
      workflow.setSteps(List.of(buildStep(EMAIL_STEP_DATA), buildStep(PAYLOAD_STEP_DATA)));

      // -- EXECUTE --
      List<HealthCheck> checks = healthCheckUtils.runScopeDefinitionChecks(workflow);

      // -- ASSERT --
      assertTrue(hasDetail(checks, HealthCheck.Detail.EMPTY));
      assertFalse(hasDetail(checks, HealthCheck.Detail.MISSING_AUDIENCE_TARGETS));
      assertFalse(hasDetail(checks, HealthCheck.Detail.MISSING_TECHNICAL_TARGETS));
    }

    @Test
    void given_scope_entries_but_no_steps_should_not_return_ineffective_warnings() {
      // -- PREPARE --
      Workflow workflow = new Workflow();
      workflow.setWorkflowScopeRules(
          List.of(
              buildScopeRule(ScopeRuleSelectedMode.ALLOWLIST, ScopeRuleValueType.TEAM_ID, "team-1"),
              buildScopeRule(
                  ScopeRuleSelectedMode.ALLOWLIST, ScopeRuleValueType.ASSET_ID, "asset-1")));

      // -- EXECUTE --
      List<HealthCheck> checks = healthCheckUtils.runScopeDefinitionChecks(workflow);

      // -- ASSERT --
      assertTrue(checks.isEmpty());
    }

    /** A tabletop (email) step: contract snapshot with team fields only, nothing configured. */
    private static final String EMAIL_STEP_DATA =
        """
        {"inject_injector_contract":{"injector_contract_id":"contract-email",
        "injector_contract_content":
        "{\\"fields\\":[{\\"key\\":\\"teams\\",\\"type\\":\\"team\\"},{\\"key\\":\\"subject\\",\\"type\\":\\"text\\"}]}"},
        "inject_all_teams":false,"inject_teams":[]}
        """;

    /** The same tabletop step with teams explicitly picked in the Configure-action drawer. */
    private static final String EMAIL_STEP_WITH_EXPLICIT_TEAMS_DATA =
        """
        {"inject_injector_contract":{"injector_contract_id":"contract-email",
        "injector_contract_content":
        "{\\"fields\\":[{\\"key\\":\\"teams\\",\\"type\\":\\"team\\"}]}"},
        "inject_all_teams":false,"inject_teams":["team-9"]}
        """;

    /** A technical step: payload inject running on an endpoint. */
    private static final String PAYLOAD_STEP_DATA =
        """
        {"inject_injector_contract":{"injector_contract_id":"contract-payload",
        "injector_contract_payload":{"payload_type":"Command"}}}
        """;

    private Step buildStep(String data) {
      return Step.builder().data(data).build();
    }

    private boolean hasDetail(List<HealthCheck> checks, HealthCheck.Detail detail) {
      return checks.stream()
          .anyMatch(
              check ->
                  HealthCheck.Type.SCOPE_DEFINITION.equals(check.getType())
                      && detail.equals(check.getDetail()));
    }

    private WorkflowScopeRule buildScopeRule(ScopeRuleSelectedMode mode, String value) {
      WorkflowScopeRule rule = new WorkflowScopeRule();
      rule.setSelectedMode(mode);
      rule.setRuleValue(value);
      return rule;
    }

    private WorkflowScopeRule buildScopeRule(
        ScopeRuleSelectedMode mode, ScopeRuleValueType valueType, String value) {
      WorkflowScopeRule rule = buildScopeRule(mode, value);
      rule.setValueType(valueType);
      return rule;
    }
  }
}
