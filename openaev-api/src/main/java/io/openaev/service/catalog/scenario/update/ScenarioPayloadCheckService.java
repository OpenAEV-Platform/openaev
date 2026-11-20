package io.openaev.service.catalog.scenario.update;

import static java.util.stream.Collectors.toMap;
import static java.util.stream.Collectors.toSet;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.openaev.database.model.Payload;
import io.openaev.database.model.catalog.CatalogPayload;
import io.openaev.rest.payload.service.PayloadService;
import io.openaev.service.catalog.CatalogListService;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Checks the payload versions a scenario content runs against those its tenant installed. */
@Service
@RequiredArgsConstructor
public class ScenarioPayloadCheckService {

  private record Ref(String externalId, String version) {}

  private final PayloadService payloadService;
  private final CatalogListService catalogListService;

  @Transactional(readOnly = true)
  public ScenarioPayloadCheck check(String tenantId, ObjectNode content) {
    Set<Ref> runs = payloadRefs(content);
    if (runs.isEmpty()) {
      return new ScenarioPayloadCheck(List.of(), List.of());
    }
    Set<String> externalIds = runs.stream().map(Ref::externalId).collect(toSet());
    Map<Ref, Payload> installed =
        payloadService.versions(tenantId, externalIds).stream()
            .collect(
                toMap(
                    payload -> new Ref(payload.getExternalId(), payload.getVersion()),
                    Function.identity(),
                    (first, second) -> first));
    return new ScenarioPayloadCheck(
        toInstall(runs, installed, externalIds), revoked(runs, installed));
  }

  private List<ScenarioPayloadCheck.Version> toInstall(
      Set<Ref> runs, Map<Ref, Payload> installed, Set<String> externalIds) {
    if (installed.keySet().containsAll(runs)) {
      return List.of();
    }
    Map<Ref, String> names =
        catalogListService.versions(CatalogPayload.class, externalIds).stream()
            .collect(
                toMap(
                    item -> new Ref(item.getExternalId(), item.getVersion()),
                    CatalogPayload::getName,
                    (first, second) -> first));
    return runs.stream()
        .filter(ref -> !installed.containsKey(ref))
        .map(ref -> new ScenarioPayloadCheck.Version(names.get(ref), ref.version()))
        .toList();
  }

  private static List<ScenarioPayloadCheck.Version> revoked(
      Set<Ref> runs, Map<Ref, Payload> installed) {
    return runs.stream()
        .map(installed::get)
        .filter(payload -> payload != null && payload.isRevoked())
        .map(payload -> new ScenarioPayloadCheck.Version(payload.getName(), payload.getVersion()))
        .toList();
  }

  private static Set<Ref> payloadRefs(ObjectNode content) {
    return content.properties().stream()
        .filter(element -> element.getKey().startsWith(ScenarioContentReader.INJECT))
        .map(element -> element.getValue().path(ScenarioContentReader.CONTRACT))
        .filter(contract -> contract.has(ScenarioContentReader.PAYLOAD_EXTERNAL_ID))
        .map(ScenarioPayloadCheckService::ref)
        .collect(toSet());
  }

  private static Ref ref(JsonNode contract) {
    return new Ref(
        contract.path(ScenarioContentReader.PAYLOAD_EXTERNAL_ID).asText(),
        contract.path(ScenarioContentReader.PAYLOAD_VERSION).asText());
  }
}
