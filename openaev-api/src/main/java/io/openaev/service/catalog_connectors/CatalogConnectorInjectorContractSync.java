package io.openaev.service.catalog_connectors;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.database.model.CatalogConnector;
import io.openaev.database.model.CatalogConnectorInjectorContract;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Keeps the injector contracts of a catalogue connector in line with its manifest. */
@Component
@RequiredArgsConstructor
public class CatalogConnectorInjectorContractSync {

  private final ObjectMapper objectMapper;

  /** Updates, adds and removes the connector's injector contracts to match its manifest. */
  public void syncFromManifest(CatalogConnector connector, JsonNode manifest) {
    Map<String, CatalogConnectorInjectorContract> stored =
        connector.getInjectorContracts().stream()
            .collect(Collectors.toMap(CatalogConnectorInjectorContract::getExternalId, c -> c));
    List<CatalogConnectorInjectorContract> contracts =
        manifestContracts(manifest).stream()
            .map(
                fromManifest ->
                    copy(
                        fromManifest,
                        stored.getOrDefault(
                            fromManifest.id(), new CatalogConnectorInjectorContract()),
                        connector))
            .toList();
    connector.getInjectorContracts().clear();
    connector.getInjectorContracts().addAll(contracts);
  }

  // Contracts without an id are skipped, and a repeated id keeps its first declaration.
  private List<ManifestInjectorContract> manifestContracts(JsonNode manifest) {
    JsonNode contracts = manifest.path("injector_contracts");
    if (!contracts.isArray()) {
      return List.of();
    }
    return objectMapper
        .convertValue(contracts, new TypeReference<List<ManifestInjectorContract>>() {})
        .stream()
        .filter(contract -> contract.id() != null && !contract.id().isBlank())
        .collect(
            Collectors.toMap(
                ManifestInjectorContract::id,
                c -> c,
                (first, repeated) -> first,
                LinkedHashMap::new))
        .values()
        .stream()
        .toList();
  }

  // Reuses the stored row: Hibernate inserts new rows before it deletes orphans.
  private static CatalogConnectorInjectorContract copy(
      ManifestInjectorContract fromManifest,
      CatalogConnectorInjectorContract contract,
      CatalogConnector connector) {
    contract.setCatalogConnector(connector);
    contract.setExternalId(fromManifest.id());
    contract.setLabels(new HashMap<>(fromManifest.labels()));
    contract.setDomains(new ArrayList<>(fromManifest.domainNames()));
    contract.setPlatforms(new ArrayList<>(fromManifest.platforms()));
    contract.setAttackPatterns(new ArrayList<>(fromManifest.attackPatterns()));
    return contract;
  }
}
