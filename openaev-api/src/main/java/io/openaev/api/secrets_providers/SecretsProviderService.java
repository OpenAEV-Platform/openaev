package io.openaev.api.secrets_providers;

import io.openaev.api.secrets_providers.form.SecretsProviderOutput;
import io.openaev.database.model.CatalogConnector;
import io.openaev.database.model.ConnectorInstance;
import io.openaev.database.model.ConnectorType;
import io.openaev.database.repository.ConnectorInstanceConfigurationRepository;
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

  @Autowired
  public SecretsProviderService(
      ConnectorInstanceConfigurationRepository connectorInstanceConfigurationRepository,
      CatalogConnectorService catalogConnectorService,
      ConnectorInstanceService connectorInstanceService,
      SecretsProviderMapper secretsProviderMapper,
      SecretService secretService,
      CatalogConnectorMapper catalogConnectorMapper) {
    super(
        ConnectorType.SECRETS_PROVIDER,
        connectorInstanceConfigurationRepository,
        catalogConnectorService,
        connectorInstanceService,
        catalogConnectorMapper);
    this.secretsProviderMapper = secretsProviderMapper;
    this.secretService = secretService;
  }

  /**
   * Retrieve all executors.
   *
   * @param isIncludeNext Include pending executors.
   * @return List of executor output
   */
  public Iterable<SecretsProviderOutput> secretsProviderOutput(boolean isIncludeNext) {
    return getConnectorsOutput(isIncludeNext);
  }

  @Override
  protected List<SecretsProvider> getAllConnectors() {
    return secretService.getAllProviders();
  }

  @Override
  protected SecretsProvider getConnectorById(String id) {
    return secretService.getAllProviders().stream()
        .filter(sp -> id.equals(sp.getId()))
        .findFirst()
        .orElse(null);
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
