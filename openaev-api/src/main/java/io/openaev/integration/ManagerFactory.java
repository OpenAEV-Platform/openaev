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
  private volatile Manager manager = null;

  @Transactional
  @Lock(type = MANAGER_FACTORY, key = "manager-factory")
  public Manager getManager() throws Exception {
    if (manager == null) {
      this.manager = new Manager(factories);
    }
    this.manager.monitorIntegrations();
    return this.manager;
  }
}
