package io.openaev.api.secrets_providers;

import io.openaev.api.secrets_providers.form.SecretsProviderOutput;
import io.openaev.context.TenantContext;
import io.openaev.database.model.CatalogConnector;
import io.openaev.database.model.ConnectorInstance;
import io.openaev.database.model.ConnectorType;
import io.openaev.database.repository.ConnectorInstanceConfigurationRepository;
import io.openaev.integration.ComponentRequest;
import io.openaev.integration.ManagerFactory;
import io.openaev.rest.catalog_connector.dto.ConnectorIds;
import io.openaev.secrets.provider.SecretsProvider;
import io.openaev.secrets.service.SecretService;
import io.openaev.service.catalog_connectors.CatalogConnectorService;
import io.openaev.service.connector_instances.ConnectorInstanceService;
import io.openaev.service.connectors.AbstractConnectorService;
import io.openaev.utils.mapper.CatalogConnectorMapper;
import io.openaev.utils.mapper.SecretsProviderMapper;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class SecretsProviderService
    extends AbstractConnectorService<SecretsProvider, SecretsProviderOutput> {
  private final SecretsProviderMapper secretsProviderMapper;
  private final SecretService secretService;
  private final ManagerFactory managerFactory;

  @Autowired
  public SecretsProviderService(
      ConnectorInstanceConfigurationRepository connectorInstanceConfigurationRepository,
      CatalogConnectorService catalogConnectorService,
      ConnectorInstanceService connectorInstanceService,
      SecretsProviderMapper secretsProviderMapper,
      SecretService secretService,
      CatalogConnectorMapper catalogConnectorMapper,
      ManagerFactory managerFactory) {
    super(
        ConnectorType.SECRETS_PROVIDER,
        connectorInstanceConfigurationRepository,
        catalogConnectorService,
        connectorInstanceService,
        catalogConnectorMapper);
    this.secretsProviderMapper = secretsProviderMapper;
    this.secretService = secretService;
    this.managerFactory = managerFactory;
  }

  /**
   * Retrieve all secrets' provider.
   *
   * @param isIncludeNext Include pending executors.
   * @return List of executor output
   */
  public Iterable<SecretsProviderOutput> secretsProviderOutput(boolean isIncludeNext) {
    return getConnectorsOutput(isIncludeNext);
  }

  public ConnectorIds getSecretsProviderRelationsId(String executorId) {
    return getConnectorRelationsId(executorId);
  }

  @Override
  protected List<SecretsProvider> getAllConnectors() {
    return managerFactory
        .getManager(TenantContext.getCurrentTenant())
        .requestManyAllStates(new ComponentRequest("secrets-provider"), SecretsProvider.class)
        .stream()
        .toList();
  }

  @Override
  protected SecretsProvider getConnectorById(String id) {
    return getAllConnectors().stream().filter(sp -> id.equals(sp.getId())).findFirst().orElse(null);
  }

  @Override
  protected SecretsProviderOutput mapToOutput(
      SecretsProvider connector,
      CatalogConnector catalogConnector,
      ConnectorInstance instance,
      boolean existingConnector) {
    return secretsProviderMapper.toSecretsProviderOutput(connector, catalogConnector, instance);
  }

  @Override
  protected SecretsProvider createNewConnector() {
    return new SecretsProvider.Placeholder();
  }
}
