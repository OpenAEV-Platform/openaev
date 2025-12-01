package io.openaev.rest.connector_instance.service;

import static io.openaev.helper.StreamHelper.fromIterable;

import io.openaev.database.model.ConnectorInstance;
import io.openaev.database.repository.ConnectorInstanceRepository;
import jakarta.persistence.EntityNotFoundException;
import java.util.List;
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
}
