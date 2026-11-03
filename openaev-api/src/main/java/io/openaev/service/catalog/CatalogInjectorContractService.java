package io.openaev.service.catalog;

import static io.openaev.helper.StreamHelper.fromIterable;

import io.openaev.api.catalog.CatalogInjectorContractOutput;
import io.openaev.database.model.CatalogConnector;
import io.openaev.database.model.CatalogConnectorInjectorContract;
import io.openaev.database.model.ConnectorType;
import io.openaev.database.model.Injector;
import io.openaev.database.repository.CatalogConnectorRepository;
import io.openaev.service.InjectorService;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CatalogInjectorContractService {

  private final CatalogConnectorRepository catalogConnectorRepository;
  private final InjectorService injectorService;

  /** Contracts of the catalogue injectors that are not registered on the platform. */
  @Transactional(readOnly = true)
  public List<CatalogInjectorContractOutput> listAvailable() {
    Set<String> registered =
        injectorService.findAll().stream().map(Injector::getType).collect(Collectors.toSet());
    return fromIterable(catalogConnectorRepository.findAll()).stream()
        .filter(c -> c.getDeletedAt() == null)
        .filter(c -> c.getContainerType() == ConnectorType.INJECTOR)
        .filter(c -> !registered.contains(c.getSlug()))
        .flatMap(c -> c.getInjectorContracts().stream().map(contract -> toOutput(c, contract)))
        .sorted(
            Comparator.comparing(
                CatalogInjectorContractOutput::label, Comparator.nullsLast(String::compareTo)))
        .toList();
  }

  private static CatalogInjectorContractOutput toOutput(
      CatalogConnector connector, CatalogConnectorInjectorContract contract) {
    Map<String, String> labels = contract.labels() == null ? Map.of() : contract.labels();
    String fallback = labels.values().stream().findFirst().orElse(contract.contractId());
    String label = labels.getOrDefault("en", fallback);
    return new CatalogInjectorContractOutput(
        contract.contractId(),
        label,
        contract.domains() == null ? List.of() : contract.domains(),
        contract.platforms() == null ? List.of() : contract.platforms(),
        connector.getId(),
        connector.getSlug(),
        connector.getTitle(),
        connector.getLogoUrl(),
        connector.isManagerSupported());
  }
}
