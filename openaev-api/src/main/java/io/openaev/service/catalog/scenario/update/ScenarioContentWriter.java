package io.openaev.service.catalog.scenario.update;

import static io.openaev.service.catalog.scenario.update.ScenarioContentReader.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.openaev.database.model.Inject;
import io.openaev.database.model.InjectDependency;
import io.openaev.database.model.InjectDependencyConditions.InjectDependencyCondition;
import io.openaev.database.model.InjectorContract;
import io.openaev.database.model.Payload;
import io.openaev.database.model.Scenario;
import io.openaev.database.model.Variable;
import io.openaev.rest.exception.BadRequestException;
import io.openaev.rest.inject.service.InjectService;
import io.openaev.rest.injector_contract.InjectorContractService;
import io.openaev.rest.payload.service.PayloadService;
import io.openaev.service.VariableService;
import io.openaev.service.catalog.scenario.targeting.CatalogScenarioTargets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Writes merged content back to a scenario: its fields, its variables and its injects, created,
 * changed or deleted. The targets of an inject stay as the client chose them.
 */
@Component
@RequiredArgsConstructor
public class ScenarioContentWriter {

  private final InjectService injectService;
  private final VariableService variableService;
  private final InjectorContractService injectorContractService;
  private final PayloadService payloadService;
  private final ObjectMapper mapper;

  public void write(Scenario scenario, Collection<Inject> injects, ObjectNode content) {
    writeScenario(scenario, content.path(SCENARIO));
    writeVariables(scenario, content.path(VARIABLES));
    writeInjects(scenario, injects, content);
  }

  // -- SCENARIO --

  private static void writeScenario(Scenario scenario, JsonNode fields) {
    scenario.setName(text(fields, NAME));
    scenario.setDescription(text(fields, DESCRIPTION));
    scenario.setCategory(text(fields, CATEGORY));
    String severity = text(fields, SEVERITY);
    scenario.setSeverity(severity == null ? null : Scenario.SEVERITY.valueOf(severity));
  }

  private void writeVariables(Scenario scenario, JsonNode values) {
    Map<String, Variable> current =
        variableService.variablesFromScenario(scenario.getId()).stream()
            .collect(Collectors.toMap(Variable::getKey, Function.identity()));
    current.forEach(
        (key, variable) -> {
          if (!values.has(key)) {
            variableService.deleteVariable(variable.getId());
          }
        });
    values
        .properties()
        .forEach(
            value -> {
              Variable variable = current.get(value.getKey());
              if (variable == null) {
                variable = new Variable();
                variable.setKey(value.getKey());
                variable.setScenario(scenario);
                variable.setValue(value.getValue().asText());
                variableService.createVariable(variable);
              } else if (!value.getValue().asText().equals(variable.getValue())) {
                variable.setValue(value.getValue().asText());
                variableService.updateVariable(variable);
              }
            });
  }

  // -- INJECTS --

  private void writeInjects(Scenario scenario, Collection<Inject> injects, ObjectNode content) {
    Map<String, Inject> current =
        injects.stream().collect(Collectors.toMap(ScenarioContentReader::key, Function.identity()));
    Map<String, JsonNode> wanted = new LinkedHashMap<>();
    content
        .properties()
        .forEach(
            element -> {
              if (element.getKey().startsWith(INJECT)) {
                wanted.put(element.getKey().substring(INJECT.length()), element.getValue());
              }
            });
    String tenantId = scenario.getTenant().getId();
    Map<ScenarioPayloadRef, InjectorContract> payloadContracts =
        payloadContracts(tenantId, content);
    Map<String, InjectorContract> otherContracts = new HashMap<>();
    List<Inject> written = new ArrayList<>();
    wanted.forEach(
        (key, element) -> {
          Inject inject = current.containsKey(key) ? current.get(key) : newInject(scenario, key);
          inject.setTitle(text(element, TITLE));
          inject.setDescription(text(element, DESCRIPTION));
          inject.setDependsDuration(element.path(DEPENDS_DURATION).asLong(0));
          inject.setInjectorContract(
              element.has(CONTRACT)
                  ? contract(tenantId, element.get(CONTRACT), payloadContracts, otherContracts)
                  : null);
          inject.setContent(content(inject.getContent(), element));
          written.add(inject);
        });
    Map<String, Inject> saved =
        injectService.saveAll(written).stream()
            .collect(Collectors.toMap(ScenarioContentReader::key, Function.identity()));
    saved.forEach((key, inject) -> writeDependencies(inject, wanted.get(key), saved));
    injectService.saveAll(List.copyOf(saved.values()));
    injectService.deleteAll(
        injects.stream().filter(inject -> !wanted.containsKey(key(inject))).toList());
  }

  private static Inject newInject(Scenario scenario, String key) {
    Inject inject = new Inject();
    inject.setScenario(scenario);
    inject.setTenant(scenario.getTenant());
    inject.setCatalogId(key);
    return inject;
  }

  private InjectorContract contract(
      String tenantId,
      JsonNode ref,
      Map<ScenarioPayloadRef, InjectorContract> payloadContracts,
      Map<String, InjectorContract> otherContracts) {
    if (!ScenarioPayloadRef.names(ref)) {
      return otherContracts.computeIfAbsent(
          ref.path(INJECTOR_CONTRACT_ID).asText(),
          id -> injectorContractService.injectorContractInTenant(id, tenantId));
    }
    ScenarioPayloadRef payload = ScenarioPayloadRef.of(ref);
    InjectorContract contract = payloadContracts.get(payload);
    if (contract == null) {
      throw new BadRequestException("Payload version not installed: " + payload.version());
    }
    return contract;
  }

  // The action of every payload version the content runs, read in two queries.
  private Map<ScenarioPayloadRef, InjectorContract> payloadContracts(
      String tenantId, ObjectNode content) {
    Set<ScenarioPayloadRef> refs = ScenarioPayloadRef.in(content);
    if (refs.isEmpty()) {
      return Map.of();
    }
    Map<ScenarioPayloadRef, Payload> installed =
        new HashMap<>(
            ScenarioPayloadRef.byRef(
                payloadService.versions(tenantId, ScenarioPayloadRef.externalIds(refs))));
    installed.keySet().retainAll(refs);
    Map<String, InjectorContract> byPayload =
        injectorContractService.injectorContractsOfPayloads(
            tenantId, installed.values().stream().map(Payload::getId).toList());
    Map<ScenarioPayloadRef, InjectorContract> contracts = new HashMap<>();
    installed.forEach(
        (ref, payload) -> {
          InjectorContract contract = byPayload.get(payload.getId());
          if (contract != null) {
            contracts.put(ref, contract);
          }
        });
    return contracts;
  }

  // The fields of the merged content, and the targets the client chose.
  private ObjectNode content(ObjectNode current, JsonNode element) {
    ObjectNode content = mapper.createObjectNode();
    element
        .properties()
        .forEach(
            field -> {
              if (field.getKey().startsWith(CONTENT)) {
                content.set(field.getKey().substring(CONTENT.length()), field.getValue());
              }
            });
    if (current != null) {
      CatalogScenarioTargets.CONTENT_KEYS.stream()
          .filter(current::has)
          .forEach(key -> content.set(key, current.get(key)));
    }
    return content;
  }

  // A dependency on an inject no longer there is dropped.
  private void writeDependencies(Inject inject, JsonNode element, Map<String, Inject> saved) {
    Map<String, JsonNode> wanted = new HashMap<>();
    element
        .path(DEPENDS_ON)
        .forEach(
            dependency -> {
              String parent = dependency.path(PARENT).asText();
              if (saved.containsKey(parent)) {
                wanted.put(parent, dependency.get(CONDITION));
              }
            });
    inject.getDependsOn().removeIf(dependency -> !wanted.containsKey(parentKey(dependency)));
    wanted.forEach(
        (parent, condition) -> {
          InjectDependency dependency =
              inject.getDependsOn().stream()
                  .filter(existing -> parent.equals(parentKey(existing)))
                  .findFirst()
                  .orElseGet(() -> newDependency(inject, saved.get(parent)));
          dependency.setInjectDependencyCondition(
              condition == null
                  ? null
                  : mapper.convertValue(condition, InjectDependencyCondition.class));
        });
  }

  private static InjectDependency newDependency(Inject child, Inject parent) {
    InjectDependency dependency = new InjectDependency();
    dependency.getCompositeId().setInjectParent(parent);
    dependency.getCompositeId().setInjectChildren(child);
    child.getDependsOn().add(dependency);
    return dependency;
  }

  private static String parentKey(InjectDependency dependency) {
    return key(dependency.getCompositeId().getInjectParent());
  }
}
