package io.openaev.integration;

import io.openaev.database.model.CatalogConnector;
import io.openaev.database.model.ConnectorInstance;
import io.openaev.service.CatalogConnectorService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

@SpringBootTest
@Transactional
public class ManagerTest {
  @Autowired private ManagerFactory managerFactory;
  @Autowired private CatalogConnectorService catalogConnectorService;
  @Test
  public void test() throws ClassNotFoundException {
    Manager manager = managerFactory.getManager();

    Optional<CatalogConnector> connector = catalogConnectorService.findByFactoryClassName("io.openaev.integration.CrowdStrikeIntegrationFactory");
    ConnectorInstance instance = new ConnectorInstance();
    instance.setCatalogConnector(connector.get());

    manager.activate(instance);

    assertThat(manager.getSpawnedIntegrations().getFirst()).isInstanceOf(CrowdStrikeIntegration.class);
  }
}
