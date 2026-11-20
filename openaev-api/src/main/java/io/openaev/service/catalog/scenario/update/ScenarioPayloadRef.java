package io.openaev.service.catalog.scenario.update;

import static java.util.stream.Collectors.toMap;
import static java.util.stream.Collectors.toSet;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.openaev.database.model.Payload;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/** A payload version a scenario's content runs, by its catalogue external ID. */
record ScenarioPayloadRef(String externalId, String version) {

  static ScenarioPayloadRef of(JsonNode contract) {
    return new ScenarioPayloadRef(
        contract.path(ScenarioContentReader.PAYLOAD_EXTERNAL_ID).asText(),
        contract.path(ScenarioContentReader.PAYLOAD_VERSION).asText());
  }

  static boolean names(JsonNode contract) {
    return contract.has(ScenarioContentReader.PAYLOAD_EXTERNAL_ID);
  }

  /** The payload versions the injects of a content run. */
  static Set<ScenarioPayloadRef> in(ObjectNode content) {
    return content.properties().stream()
        .filter(element -> element.getKey().startsWith(ScenarioContentReader.INJECT))
        .map(element -> element.getValue().path(ScenarioContentReader.CONTRACT))
        .filter(ScenarioPayloadRef::names)
        .map(ScenarioPayloadRef::of)
        .collect(toSet());
  }

  static Set<String> externalIds(Collection<ScenarioPayloadRef> refs) {
    return refs.stream().map(ScenarioPayloadRef::externalId).collect(toSet());
  }

  static Map<ScenarioPayloadRef, Payload> byRef(Collection<Payload> payloads) {
    return payloads.stream()
        .collect(
            toMap(
                payload -> new ScenarioPayloadRef(payload.getExternalId(), payload.getVersion()),
                Function.identity(),
                (first, second) -> first));
  }
}
