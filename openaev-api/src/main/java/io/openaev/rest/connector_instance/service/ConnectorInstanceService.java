package io.openaev.rest.connector_instance.service;

import static io.openaev.helper.StreamHelper.fromIterable;

import io.openaev.database.model.CatalogConnector;
import io.openaev.database.model.ConnectorInstance;
import io.openaev.database.model.ConnectorInstanceInMemory;
import io.openaev.database.model.ConnectorInstancePersisted;
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

  public List<ConnectorInstancePersisted> connectorInstances() {
    return fromIterable(connectorInstanceRepository.findAll());
  }

  public ConnectorInstancePersisted connectorInstanceById(String id) {
    return connectorInstanceRepository
        .findById(id)
        .orElseThrow(
            () -> new EntityNotFoundException("ConnectorInstance with id " + id + " not found"));
  }

  public ConnectorInstance save(ConnectorInstance instance) {
    if (instance instanceof ConnectorInstancePersisted) {
      return connectorInstanceRepository.save((ConnectorInstancePersisted) instance);
    }
    return instance;
  }

  public void deleteById(String id) {
    if (!this.connectorInstanceRepository.existsById(id)) {
      throw new EntityNotFoundException("ConnectorInstance with id " + id + " not found");
    }
    connectorInstanceRepository.deleteById(id);
  }

  public ConnectorInstance refresh(ConnectorInstance instance) {
    if (instance instanceof ConnectorInstancePersisted) {
      return connectorInstanceRepository
          .findById(((ConnectorInstancePersisted) instance).getId())
          .get();
    }
    return instance;
  }

  public ConnectorInstance createAutostartInstance() {
    ConnectorInstanceInMemory instance = new ConnectorInstanceInMemory();
    instance.setRequestedStatus(ConnectorInstancePersisted.REQUESTED_STATUS_TYPE.starting);
    instance.setCurrentStatus(ConnectorInstancePersisted.CURRENT_STATUS_TYPE.stopped);
    return instance;
  }

  public List<ConnectorInstancePersisted> findAllByCatalogConnector(CatalogConnector connector) {
    return connectorInstanceRepository.findByCatalogConnectorId(connector.getId());
  }

  public void saveAll(Set<ConnectorInstancePersisted> instances) {
    connectorInstanceRepository.saveAll(instances);
  }
}
