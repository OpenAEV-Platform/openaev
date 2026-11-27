package io.openaev.service.catalog.scenario.update;

import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.openaev.database.model.Command;
import io.openaev.database.model.Inject;
import io.openaev.database.model.InjectDependency;
import io.openaev.database.model.InjectDependencyConditions;
import io.openaev.database.model.InjectorContract;
import io.openaev.database.model.Scenario;
import io.openaev.database.model.Variable;
import io.openaev.rest.exception.BadRequestException;
import io.openaev.service.catalog.merge.MergeStatus;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@DisplayName("Scenario content reader")
class ScenarioContentReaderTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private final ScenarioContentReader reader = new ScenarioContentReader(MAPPER);

  @Nested
  @DisplayName("From a package")
  class FromPackage {

    @Test
    @DisplayName("reads the fields, the variables and each inject with its contract and links")
    void given_fullPackage_should_readEveryElement() throws Exception {
      // Arrange
      ScenarioPackage scenarioPackage =
          new ScenarioPackage(
              MAPPER.readTree(
                  """
                  {
                    "scenario_information": {
                      "scenario_name": "Recon",
                      "scenario_severity": "high",
                      "scenario_category": "Discovery"
                    },
                    "scenario_variables": [{"variable_key": "target", "variable_value": "10.0.0.1"}],
                    "scenario_injects": [
                      {
                        "inject_id": "scan",
                        "inject_title": "Scan",
                        "inject_depends_duration": 60,
                        "inject_content": {"arguments": "-sV", "targets": ["a"], "empty": null},
                        "inject_injector_contract": {
                          "injector_contract_payload": {"payload_external_id": "nmap"}
                        },
                        "inject_depends_on": [{
                          "dependency_relationship": {
                            "inject_parent_id": "warn", "inject_children_id": "scan"
                          },
                          "dependency_condition": {"mode": "and"}
                        }]
                      },
                      {
                        "inject_id": "warn",
                        "inject_title": "Warn",
                        "inject_injector_contract": {"injector_contract_id": "email"}
                      }
                    ]
                  }
                  """));

      // Act
      ObjectNode content = reader.fromPackage(scenarioPackage, Map.of("nmap", "1.2.0"));

      // Assert
      assertThatJson(content)
          .isEqualTo(
              """
              {
                "scenario": {"name": "Recon", "severity": "high", "category": "Discovery"},
                "variables": {"target": "10.0.0.1"},
                "inject:scan": {
                  "title": "Scan",
                  "contract": {"payload_external_id": "nmap", "payload_version": "1.2.0"},
                  "depends_duration": 60,
                  "depends_on": [{"parent": "warn", "condition": {"mode": "and"}}],
                  "content.arguments": "-sV"
                },
                "inject:warn": {
                  "title": "Warn",
                  "contract": {"injector_contract_id": "email"},
                  "depends_duration": 0,
                  "depends_on": []
                }
              }
              """);
    }

    @Test
    @DisplayName("refuses an inject running a payload its release does not name")
    void given_payloadMissingFromTheRelease_should_refuse() throws Exception {
      // Arrange
      ScenarioPackage scenarioPackage =
          new ScenarioPackage(
              MAPPER.readTree(
                  """
                  {
                    "scenario_information": {"scenario_name": "Recon"},
                    "scenario_injects": [{
                      "inject_id": "scan",
                      "inject_title": "Scan",
                      "inject_injector_contract": {
                        "injector_contract_payload": {"payload_external_id": "nmap"}
                      }
                    }]
                  }
                  """));

      // Act & Assert
      assertThatThrownBy(() -> reader.fromPackage(scenarioPackage, Map.of()))
          .isInstanceOf(BadRequestException.class)
          .hasMessageEndingWith("Scan");
    }
  }

  @Nested
  @DisplayName("From the entities")
  class FromEntities {

    @Test
    @DisplayName("reads an inject the client added under its own ID, with only the fields it has")
    void given_injectTheClientAdded_should_readItUnderItsOwnId() {
      // Arrange
      Scenario scenario = new Scenario();
      scenario.setName("Recon");
      scenario.setSeverity(Scenario.SEVERITY.high);
      Inject inject = new Inject();
      inject.setId("my-check");
      inject.setTitle("My check");

      // Act
      ObjectNode content = reader.fromEntities(scenario, List.of(), List.of(inject));

      // Assert
      assertThatJson(content)
          .isEqualTo(
              """
              {
                "scenario": {"name": "Recon", "severity": "high"},
                "variables": {},
                "inject:my-check": {"title": "My check", "depends_duration": 0, "depends_on": []}
              }
              """);
    }

    @Test
    @DisplayName(
        "reads the variables and an installed inject with its payload, links and arguments")
    void given_installedInjects_should_readEveryElementButTheTargets() throws Exception {
      // Arrange
      Scenario scenario = new Scenario();
      scenario.setName("Recon");
      scenario.setDescription("Find the hosts");
      scenario.setCategory("Discovery");
      Variable variable = new Variable();
      variable.setKey("target");
      variable.setValue("10.0.0.1");
      Inject warn = installedInject("warn", "Warn", contract("email", null));
      Command nmap = new Command();
      nmap.setExternalId("nmap");
      nmap.setVersion("1.2.0");
      Inject scan = installedInject("scan", "Scan", contract("nmap-contract", nmap));
      scan.setDescription("Scan the hosts");
      scan.setDependsDuration(60L);
      scan.setContent(
          (ObjectNode)
              MAPPER.readTree(
                  """
                  {"arguments": "-sV", "targets": ["a"], "target_selector": "manual", "empty": null}
                  """));
      InjectDependency dependency = new InjectDependency();
      dependency.getCompositeId().setInjectParent(warn);
      dependency.getCompositeId().setInjectChildren(scan);
      InjectDependencyConditions.InjectDependencyCondition condition =
          new InjectDependencyConditions.InjectDependencyCondition();
      condition.setMode(InjectDependencyConditions.DependencyMode.and);
      dependency.setInjectDependencyCondition(condition);
      scan.getDependsOn().add(dependency);

      // Act
      ObjectNode content = reader.fromEntities(scenario, List.of(variable), List.of(scan, warn));

      // Assert
      assertThatJson(content)
          .isEqualTo(
              """
              {
                "scenario": {"name": "Recon", "description": "Find the hosts", "category": "Discovery"},
                "variables": {"target": "10.0.0.1"},
                "inject:scan": {
                  "title": "Scan",
                  "description": "Scan the hosts",
                  "contract": {"payload_external_id": "nmap", "payload_version": "1.2.0"},
                  "depends_duration": 60,
                  "depends_on": [{"parent": "warn", "condition": {"mode": "and", "conditions": null}}],
                  "content.arguments": "-sV"
                },
                "inject:warn": {
                  "title": "Warn",
                  "contract": {"injector_contract_id": "email"},
                  "depends_duration": 0,
                  "depends_on": []
                }
              }
              """);
    }

    private static Inject installedInject(
        String catalogId, String title, InjectorContract contract) {
      Inject inject = new Inject();
      inject.setId(catalogId + "-local");
      inject.setCatalogId(catalogId);
      inject.setTitle(title);
      inject.setInjectorContract(contract);
      return inject;
    }

    private static InjectorContract contract(String id, Command payload) {
      InjectorContract contract = new InjectorContract();
      contract.setId(id);
      contract.setPayload(payload);
      return contract;
    }
  }

  @Nested
  @TestInstance(TestInstance.Lifecycle.PER_CLASS)
  @DisplayName("Kinds of a change")
  class Kinds {

    static Stream<Arguments> fields() {
      return Stream.of(
          Arguments.of(
              "scenario", "name", ScenarioElementKind.SCENARIO, ScenarioFieldKind.NAME, null),
          Arguments.of(
              "variables",
              "target",
              ScenarioElementKind.VARIABLES,
              ScenarioFieldKind.VARIABLE,
              "target"),
          Arguments.of(
              "inject:scan",
              "content.arguments",
              ScenarioElementKind.INJECT,
              ScenarioFieldKind.ARGUMENT,
              "arguments"),
          Arguments.of(
              "inject:scan",
              "depends_on",
              ScenarioElementKind.INJECT,
              ScenarioFieldKind.DEPENDS_ON,
              null));
    }

    @ParameterizedTest
    @MethodSource("fields")
    @DisplayName("names what a changed field is, and the key of a variable or an argument")
    void given_changedField_should_nameItsKinds(
        String element,
        String field,
        ScenarioElementKind elementKind,
        ScenarioFieldKind fieldKind,
        String key) {
      // Arrange
      ScenarioMerge.Change change =
          new ScenarioMerge.Change(element, field, null, MergeStatus.CONFLICT, null, null, null);

      // Act & Assert
      assertThat(change.elementKind()).isEqualTo(elementKind);
      assertThat(change.fieldKind()).isEqualTo(fieldKind);
      assertThat(change.fieldKey()).isEqualTo(key);
    }

    @Test
    @DisplayName("gives no field kind to a whole element")
    void given_wholeElement_should_haveNoFieldKind() {
      // Arrange
      ScenarioMerge.Change change =
          new ScenarioMerge.Change(
              "inject:scan", null, "Scan", MergeStatus.CONFLICT, null, null, null);

      // Act & Assert
      assertThat(change.elementKind()).isEqualTo(ScenarioElementKind.INJECT);
      assertThat(change.fieldKind()).isNull();
      assertThat(change.fieldKey()).isNull();
    }
  }
}
