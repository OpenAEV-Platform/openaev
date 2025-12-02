package io.openaev.rest.connector_instance.service;

import static io.openaev.helper.StreamHelper.fromIterable;

import io.openaev.database.model.CatalogConnector;
import io.openaev.database.model.ConnectorInstance;
import io.openaev.database.repository.ConnectorInstanceRepository;
import jakarta.persistence.EntityNotFoundException;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConnectorInstanceService {

  private final ConnectorInstanceRepository connectorInstanceRepository;

  public List<ConnectorInstance> connectorInstances() {
    return fromIterable(connectorInstanceRepository.findAll());
  }

  public ConnectorInstance connectorInstanceById(String id) {
    return connectorInstanceRepository
        .findById(id)
        .orElseThrow(
            () -> new EntityNotFoundException("ConnectorInstance with id " + id + " not found"));
  }

  public ConnectorInstance save(ConnectorInstance connectorInstance) {
    return connectorInstanceRepository.save(connectorInstance);
  }

  public void deleteById(String id) {
    if (!this.connectorInstanceRepository.existsById(id)) {
      throw new EntityNotFoundException("ConnectorInstance with id " + id + " not found");
    }
    connectorInstanceRepository.deleteById(id);
  }

  public List<ConnectorInstance> findAllByCatalogConnector(CatalogConnector connector) {
    return connectorInstanceRepository.findByCatalogConnectorId(connector.getId());
  }

  public void saveAll(Set<ConnectorInstance> instances) {
    connectorInstanceRepository.saveAll(instances);
  }
}
