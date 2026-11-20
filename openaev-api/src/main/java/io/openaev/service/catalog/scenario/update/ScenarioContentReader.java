package io.openaev.service.catalog.scenario.update;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.openaev.database.model.Inject;
import io.openaev.database.model.InjectDependency;
import io.openaev.database.model.InjectorContract;
import io.openaev.database.model.Payload;
import io.openaev.database.model.Scenario;
import io.openaev.database.model.Variable;
import io.openaev.rest.exception.BadRequestException;
import io.openaev.service.catalog.scenario.targeting.CatalogScenarioTargets;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.StreamSupport;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Reads a scenario as the merge compares it, from a catalogue package or from the tenant's
 * entities: its fields, its variables, then each inject under the ID its package gives it. The
 * targets a client chose are left out: an update never changes them.
 */
@Component
@RequiredArgsConstructor
public class ScenarioContentReader {

  static final String SCENARIO = "scenario";
  static final String VARIABLES = "variables";
  static final String INJECT = "inject:";
  static final String NAME = "name";
  static final String SEVERITY = "severity";
  static final String CATEGORY = "category";
  static final String TITLE = "title";
  static final String DESCRIPTION = "description";
  static final String CONTRACT = "contract";
  static final String DEPENDS_DURATION = "depends_duration";
  static final String DEPENDS_ON = "depends_on";
  static final String CONTENT = "content.";
  static final String PARENT = "parent";
  static final String CONDITION = "condition";
  static final String PAYLOAD_EXTERNAL_ID = "payload_external_id";
  static final String PAYLOAD_VERSION = "payload_version";
  static final String INJECTOR_CONTRACT_ID = "injector_contract_id";

  private final ObjectMapper mapper;

  // -- PACKAGE --

  /** {@code payloadVersions} is the version its release names of each payload, by external ID. */
  public ObjectNode fromPackage(
      ScenarioPackage scenarioPackage, Map<String, String> payloadVersions) {
    JsonNode root = scenarioPackage.root();
    JsonNode information = root.path("scenario_information");
    ObjectNode content = mapper.createObjectNode();
    content.set(
        SCENARIO,
        scenario(
            text(information, "scenario_name"),
            text(information, "scenario_description"),
            text(information, "scenario_severity"),
            text(information, "scenario_category")));
    ObjectNode variables = content.putObject(VARIABLES);
    root.path("scenario_variables")
        .forEach(
            variable ->
                put(variables, text(variable, "variable_key"), text(variable, "variable_value")));
    root.path("scenario_injects")
        .forEach(
            inject -> {
              String id = text(inject, "inject_id");
              content.set(
                  INJECT + id,
                  inject(
                      text(inject, "inject_title"),
                      text(inject, "inject_description"),
                      packageContract(inject, payloadVersions),
                      inject.path("inject_depends_duration").asLong(0),
                      packageDependencies(inject, id),
                      inject.get("inject_content")));
            });
    return canonical(content);
  }

  private ObjectNode packageContract(JsonNode inject, Map<String, String> payloadVersions) {
    JsonNode contract = inject.path("inject_injector_contract");
    String externalId = text(contract.path("injector_contract_payload"), PAYLOAD_EXTERNAL_ID);
    if (externalId == null) {
      return injectorContract(text(contract, INJECTOR_CONTRACT_ID));
    }
    String version = payloadVersions.get(externalId);
    if (version == null) {
      throw new BadRequestException(
          "The release does not name the payload of " + text(inject, "inject_title"));
    }
    return payload(externalId, version);
  }

  private List<ObjectNode> packageDependencies(JsonNode inject, String id) {
    return StreamSupport.stream(inject.path("inject_depends_on").spliterator(), false)
        .filter(d -> id.equals(text(d.path("dependency_relationship"), "inject_children_id")))
        .map(
            d ->
                dependency(
                    text(d.path("dependency_relationship"), "inject_parent_id"),
                    d.get("dependency_condition")))
        .toList();
  }

  // -- ENTITIES --

  public ObjectNode fromEntities(
      Scenario scenario, Collection<Variable> variables, Collection<Inject> injects) {
    ObjectNode content = mapper.createObjectNode();
    content.set(
        SCENARIO,
        scenario(
            scenario.getName(),
            scenario.getDescription(),
            scenario.getSeverity() == null ? null : scenario.getSeverity().name(),
            scenario.getCategory()));
    ObjectNode values = content.putObject(VARIABLES);
    variables.forEach(variable -> put(values, variable.getKey(), variable.getValue()));
    injects.forEach(
        inject ->
            content.set(
                INJECT + key(inject),
                inject(
                    inject.getTitle(),
                    inject.getDescription(),
                    inject.getInjectorContract().map(this::contract).orElse(null),
                    inject.getDependsDuration() == null ? 0 : inject.getDependsDuration(),
                    inject.getDependsOn().stream().map(this::dependency).toList(),
                    inject.getContent())));
    return canonical(content);
  }

  /** The ID of an inject in its package, or its own when the client added it. */
  static String key(Inject inject) {
    return inject.getCatalogId() != null ? inject.getCatalogId() : inject.getId();
  }

  private ObjectNode contract(InjectorContract contract) {
    Payload payload = contract.getPayload();
    return payload == null || payload.getExternalId() == null
        ? injectorContract(contract.getId())
        : payload(payload.getExternalId(), payload.getVersion());
  }

  private ObjectNode dependency(InjectDependency dependency) {
    return dependency(
        key(dependency.getCompositeId().getInjectParent()),
        mapper.valueToTree(dependency.getInjectDependencyCondition()));
  }

  // -- ELEMENTS --

  private ObjectNode scenario(String name, String description, String severity, String category) {
    ObjectNode scenario = mapper.createObjectNode();
    put(scenario, NAME, name);
    put(scenario, DESCRIPTION, description);
    put(scenario, SEVERITY, severity);
    put(scenario, CATEGORY, category);
    return scenario;
  }

  private ObjectNode inject(
      String title,
      String description,
      ObjectNode contract,
      long dependsDuration,
      List<ObjectNode> dependsOn,
      JsonNode content) {
    ObjectNode inject = mapper.createObjectNode();
    put(inject, TITLE, title);
    put(inject, DESCRIPTION, description);
    if (contract != null) {
      inject.set(CONTRACT, contract);
    }
    inject.put(DEPENDS_DURATION, dependsDuration);
    ArrayNode dependencies = inject.putArray(DEPENDS_ON);
    dependsOn.stream()
        .sorted(Comparator.comparing(dependency -> dependency.path(PARENT).asText()))
        .forEach(dependencies::add);
    if (content != null) {
      content
          .properties()
          .forEach(
              field -> {
                if (!CatalogScenarioTargets.CONTENT_KEYS.contains(field.getKey())
                    && !field.getValue().isNull()) {
                  inject.set(CONTENT + field.getKey(), field.getValue());
                }
              });
    }
    return inject;
  }

  private ObjectNode payload(String externalId, String version) {
    ObjectNode payload = mapper.createObjectNode();
    payload.put(PAYLOAD_EXTERNAL_ID, externalId);
    payload.put(PAYLOAD_VERSION, version);
    return payload;
  }

  private ObjectNode injectorContract(String id) {
    ObjectNode contract = mapper.createObjectNode();
    contract.put(INJECTOR_CONTRACT_ID, id);
    return contract;
  }

  private ObjectNode dependency(String parent, JsonNode condition) {
    ObjectNode dependency = mapper.createObjectNode();
    dependency.put(PARENT, parent);
    if (condition != null && !condition.isNull()) {
      dependency.set(CONDITION, condition);
    }
    return dependency;
  }

  // An absent value and a null one compare the same.
  private static void put(ObjectNode node, String field, String value) {
    if (field != null && value != null) {
      node.put(field, value);
    }
  }

  // The same JSON reads as the same nodes, whichever side its numbers come from.
  private ObjectNode canonical(ObjectNode content) {
    try {
      return (ObjectNode) mapper.readTree(mapper.writeValueAsString(content));
    } catch (JsonProcessingException e) {
      throw new IllegalStateException(e);
    }
  }

  static String text(JsonNode node, String field) {
    JsonNode value = node.get(field);
    return value == null || value.isNull() ? null : value.asText();
  }
}
