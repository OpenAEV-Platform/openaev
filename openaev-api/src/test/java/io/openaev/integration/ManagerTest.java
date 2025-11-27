package io.openaev.integration;

import io.openaev.database.model.CatalogConnector;
import io.openaev.database.model.ConnectorInstance;
import io.openaev.rest.connector_instance.service.ConnectorInstanceService;
import io.openaev.service.CatalogConnectorService;
import jakarta.persistence.EntityManager;
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
  @Autowired private ConnectorInstanceService connectorInstanceService;
  @Autowired private EntityManager entityManager;

  @Test
  public void test() throws ClassNotFoundException {
    Manager manager = managerFactory.getManager();

    Optional<CatalogConnector> connector = catalogConnectorService.findByFactoryClassName("io.openaev.integration.CrowdStrikeIntegrationFactory");
    ConnectorInstance instance = new ConnectorInstance();
    instance.setCatalogConnector(connector.get());

    manager.activate(instance);

    assertThat(manager.getSpawnedIntegrations().getFirst()).isInstanceOf(CrowdStrikeIntegration.class);
  }

  @Test
  public void test2() throws ClassNotFoundException {
    String className = "io.openaev.integration.CrowdStrikeIntegrationFactory";
    CatalogConnector cc = catalogConnectorService.createBuiltIn(className);
    ConnectorInstance alreadyCreated = new ConnectorInstance();
    alreadyCreated.setCatalogConnector(cc);
    alreadyCreated.setRequestedStatus(ConnectorInstance.REQUESTED_STATUS_TYPE.stopping);
    alreadyCreated.setCurrentStatus(ConnectorInstance.CURRENT_STATUS_TYPE.stopped);
    alreadyCreated.setSource(ConnectorInstance.SOURCE.PROPERTIES_MIGRATION);
    connectorInstanceService.save(alreadyCreated);
    entityManager.flush();
    entityManager.clear();

    Manager manager = managerFactory.getManager();

    Optional<CatalogConnector> connector = catalogConnectorService.findByFactoryClassName(className);
    ConnectorInstance instance = new ConnectorInstance();
    instance.setCatalogConnector(connector.get());

    manager.activate(instance);

    assertThat(manager.getSpawnedIntegrations().getFirst()).isInstanceOf(CrowdStrikeIntegration.class);
  }
}
