package io.openaev.integration;

import java.util.List;
import lombok.Getter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class ManagerFactory {
  @Getter private final Manager manager;

  public ManagerFactory(List<IntegrationFactory> factories) {
    manager = spawnIntegrationsManager(factories);
  }

  private Manager spawnIntegrationsManager(List<IntegrationFactory> factories) {
    return new Manager(factories);
  }
}
