package io.openaev.integration;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import io.openaev.database.model.CatalogConnector;
import io.openaev.database.model.ConnectorInstance;
import io.openaev.database.model.ConnectorInstanceConfiguration;
import io.openaev.executors.ExecutorContextService;
import io.openaev.executors.crowdstrike.config.CrowdStrikeExecutorConfig;
import io.openaev.executors.crowdstrike.service.CrowdStrikeExecutorContextService;
import io.openaev.integration.impl.executors.crowdstrike.CrowdStrikeExecutorIntegration;
import io.openaev.rest.connector_instance.service.ConnectorInstanceService;
import io.openaev.service.CatalogConnectorService;
import jakarta.persistence.EntityManager;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
public class ManagerTest {
  @Autowired private ManagerFactory managerFactory;
  @Autowired private CatalogConnectorService catalogConnectorService;
  @Autowired private ConnectorInstanceService connectorInstanceService;
  @Autowired private EntityManager entityManager;

  @Test
  public void test() throws Exception {
    Manager manager = managerFactory.getManager();

    Optional<CatalogConnector> connector =
        catalogConnectorService.findByFactoryClassName(
            "io.openaev.integration.impl.crowdstrike.CrowdStrikeIntegrationFactory");
    ConnectorInstance instance = new ConnectorInstance();
    instance.setCatalogConnector(connector.get());

    manager.activate(instance);

    assertThat(manager.getSpawnedIntegrations().getFirst())
        .isInstanceOf(CrowdStrikeExecutorIntegration.class);
  }

  @Test
  public void test2() throws Exception {
    String className = "io.openaev.integration.impl.crowdstrike.CrowdStrikeIntegrationFactory";
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

    Optional<CatalogConnector> connector =
        catalogConnectorService.findByFactoryClassName(className);
    ConnectorInstance instance = new ConnectorInstance();
    instance.setCatalogConnector(connector.get());

    manager.activate(instance);

    assertThat(manager.getSpawnedIntegrations().getFirst())
        .isInstanceOf(CrowdStrikeExecutorIntegration.class);
  }

  @Test
  public void test3() throws Exception {
    String className = "io.openaev.integration.impl.crowdstrike.CrowdStrikeIntegrationFactory";
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

    Optional<CatalogConnector> connector =
        catalogConnectorService.findByFactoryClassName(className);
    ConnectorInstance instance = new ConnectorInstance();
    instance.setCatalogConnector(connector.get());

    manager.activate(instance);

    ExecutorContextService executorContextService =
        manager.request(
            new ComponentRequest(CrowdStrikeExecutorContextService.SERVICE_NAME),
            ExecutorContextService.class);

    assertThat(executorContextService).isInstanceOf(ExecutorContextService.class);
  }

  @Test
  public void test4() throws JsonProcessingException {
    CrowdStrikeExecutorConfig csConfig = new CrowdStrikeExecutorConfig();
    csConfig.setApiUrl("HTTP_URL");
    csConfig.setClientSecret("CLIENT SECRET");
    Set<ConnectorInstanceConfiguration> configs = csConfig.toInstanceConfigurationSet(new ConnectorInstance());
    assertThat(configs.size()).isGreaterThan(0);
  }
}
