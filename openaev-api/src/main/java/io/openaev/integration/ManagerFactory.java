package io.openaev.integration;

import static io.openaev.aop.lock.LockResourceType.MANAGER_FACTORY;

import io.openaev.aop.lock.Lock;
import io.openaev.integration.impl.executors.paloaltocortex.PaloAltoCortexExecutorIntegrationFactory;
import io.openaev.integration.impl.executors.sentinelone.SentinelOneExecutorIntegrationFactory;
import io.openaev.rest.settings.PreviewFeature;
import io.openaev.service.PreviewFeatureService;
import jakarta.annotation.PostConstruct;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ManagerFactory {
  private final List<IntegrationFactory> factories;
  private final PreviewFeatureService previewFeatureService;

  @PostConstruct
  public void disableFlaggedExecutors() {
    if (!previewFeatureService.isFeatureEnabled(PreviewFeature.SENTINEL_ONE_EXECUTOR)) {
      factories.removeIf(
          factory ->
              SentinelOneExecutorIntegrationFactory.class
                  .getCanonicalName()
                  .equals(factory.getClassName()));
    }
    if (!previewFeatureService.isFeatureEnabled(PreviewFeature.PALO_ALTO_CORTEX_EXECUTOR)) {
      factories.removeIf(
          factory ->
              PaloAltoCortexExecutorIntegrationFactory.class
                  .getCanonicalName()
                  .equals(factory.getClassName()));
    }
  }

  private volatile Manager manager = null;

  @Transactional
  @Lock(type = MANAGER_FACTORY, key = "manager-factory")
  public Manager getManager() {
    if (manager == null) {
      try {
        this.manager = new Manager(factories);
        this.manager.monitorIntegrations();
      } catch (Exception e) {
        throw new RuntimeException("Failed to initialize Manager", e);
      }
    }
    return this.manager;
  }
}
