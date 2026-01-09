package io.openaev.integration;

import static io.openaev.aop.lock.LockResourceType.MANAGER_FACTORY;

import io.openaev.aop.lock.Lock;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ManagerFactory {
  private final List<IntegrationFactory> factories;
  private Manager manager = null;

  public Manager getManager() {
    Manager local = manager;
    if (local == null) {
      throw new IllegalStateException("Manager not initialized yet");
    }
    return local;
  }

  @Transactional
  @Lock(type = MANAGER_FACTORY, key = "manager-factory")
  public void initializeAndSync() throws Exception {
    if (manager == null) {
      manager = new Manager(factories);
    }
    manager.monitorIntegrations();
  }
}
