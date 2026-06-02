package io.openaev.integration;

import static io.openaev.aop.lock.LockResourceType.MANAGER_FACTORY;

import io.openaev.aop.lock.Lock;
import io.openaev.database.model.Tenant;
import jakarta.annotation.PostConstruct;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ManagerFactory {
  private final List<IntegrationFactory> factories;

  private final Map<String, Manager> managers = new ConcurrentHashMap<>();

  @PostConstruct
  public void init() throws Exception {
    for (IntegrationFactory factory : factories) {
      factory.initialise();
    }
  }

  @Transactional
  @Lock(type = MANAGER_FACTORY, key = Tenant.DEFAULT_TENANT_UUID)
  public Manager getManager() {
    return getManagerForTenant(Tenant.DEFAULT_TENANT_UUID);
  }

  @Transactional
  @Lock(type = MANAGER_FACTORY, key = "#tenantId")
  public Manager getManager(String tenantId) {
    return getManagerForTenant(tenantId);
  }

  private Manager getManagerForTenant(String tenantId) {
    if (!this.managers.containsKey(tenantId)) {
      try {
        Manager manager = new Manager(factories, tenantId);
        manager.monitorIntegrations();
        this.managers.put(tenantId, manager);
      } catch (Exception e) {
        throw new RuntimeException("Failed to initialize Manager", e);
      }
    }
    return this.managers.get(tenantId);
  }
}
