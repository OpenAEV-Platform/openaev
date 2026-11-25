package io.openaev.service.catalog.scenario.update;

import static java.util.stream.Collectors.toMap;

import com.fasterxml.jackson.databind.node.ObjectNode;
import io.openaev.database.model.Payload;
import io.openaev.database.model.catalog.CatalogPayload;
import io.openaev.rest.payload.service.PayloadService;
import io.openaev.service.catalog.CatalogListService;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Checks the payload versions a scenario content runs against those its tenant installed. */
@Service
@RequiredArgsConstructor
public class ScenarioPayloadCheckService {

  private final PayloadService payloadService;
  private final CatalogListService catalogListService;

  @Transactional(readOnly = true)
  public ScenarioPayloadCheck check(String tenantId, ObjectNode content) {
    Set<ScenarioPayloadRef> runs = ScenarioPayloadRef.in(content);
    if (runs.isEmpty()) {
      return new ScenarioPayloadCheck(List.of(), List.of());
    }
    Set<String> externalIds = ScenarioPayloadRef.externalIds(runs);
    Map<ScenarioPayloadRef, Payload> installed =
        ScenarioPayloadRef.byRef(payloadService.versions(tenantId, externalIds));
    return new ScenarioPayloadCheck(
        toInstall(runs, installed, externalIds), revoked(runs, installed));
  }

  private List<ScenarioPayloadCheck.Version> toInstall(
      Set<ScenarioPayloadRef> runs,
      Map<ScenarioPayloadRef, Payload> installed,
      Set<String> externalIds) {
    if (installed.keySet().containsAll(runs)) {
      return List.of();
    }
    Map<ScenarioPayloadRef, String> names =
        catalogListService.versions(CatalogPayload.class, externalIds).stream()
            .collect(
                toMap(
                    item -> new ScenarioPayloadRef(item.getExternalId(), item.getVersion()),
                    CatalogPayload::getName,
                    (first, second) -> first));
    return runs.stream()
        .filter(ref -> !installed.containsKey(ref))
        .map(ref -> new ScenarioPayloadCheck.Version(names.get(ref), ref.version()))
        .toList();
  }

  private static List<ScenarioPayloadCheck.Version> revoked(
      Set<ScenarioPayloadRef> runs, Map<ScenarioPayloadRef, Payload> installed) {
    return runs.stream()
        .map(installed::get)
        .filter(payload -> payload != null && payload.isRevoked())
        .map(payload -> new ScenarioPayloadCheck.Version(payload.getName(), payload.getVersion()))
        .toList();
  }
}
