package io.openaev.api.secrets_providers;

import io.openaev.api.secrets_providers.form.SecretsProviderOutput;
import io.openaev.context.TransactionalTenantScope;
import io.openaev.database.model.CatalogConnector;
import io.openaev.database.model.ConnectorInstance;
import io.openaev.database.model.ConnectorType;
import io.openaev.database.repository.ConnectorInstanceConfigurationRepository;
import io.openaev.integration.ComponentRequest;
import io.openaev.integration.ManagerFactory;
import io.openaev.rest.catalog_connector.dto.ConnectorIds;
import io.openaev.secrets.provider.SecretsProvider;
import io.openaev.service.catalog_connectors.CatalogConnectorService;
import io.openaev.service.connector_instances.ConnectorInstanceService;
import io.openaev.service.connectors.AbstractConnectorService;
import io.openaev.utils.mapper.CatalogConnectorMapper;
import io.openaev.utils.mapper.SecretsProviderMapper;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class SecretsProviderService
    extends AbstractConnectorService<SecretsProvider, SecretsProviderOutput> {

  private final SecretsProviderMapper secretsProviderMapper;
  private final ManagerFactory managerFactory;
  private final TransactionalTenantScope transactionalTenantScope;

  @Autowired
  public SecretsProviderService(
      ConnectorInstanceConfigurationRepository connectorInstanceConfigurationRepository,
      CatalogConnectorService catalogConnectorService,
      ConnectorInstanceService connectorInstanceService,
      SecretsProviderMapper secretsProviderMapper,
      CatalogConnectorMapper catalogConnectorMapper,
      ManagerFactory managerFactory,
      TransactionalTenantScope transactionalTenantScope) {
    super(
        ConnectorType.SECRETS_PROVIDER,
        connectorInstanceConfigurationRepository,
        catalogConnectorService,
        connectorInstanceService,
        catalogConnectorMapper);
    this.secretsProviderMapper = secretsProviderMapper;
    this.managerFactory = managerFactory;
    this.transactionalTenantScope = transactionalTenantScope;
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

  /**
   * Retrieves IDs of resources associated with a secretProvider.
   *
   * @param secretProviderId secret provider identifier
   * @return connector instance ID and catalog connector ID if available, null values if not found
   */
  public ConnectorIds getSecretsProviderRelationsId(String secretProviderId) {
    return getConnectorRelationsId(secretProviderId);
  }

  @Override
  protected List<SecretsProvider> getAllConnectors() {
    return transactionalTenantScope.currentTenantIds().stream()
        .flatMap(
            tenantId -> {
              try {
                return managerFactory
                    .getManager(tenantId)
                    .requestManyAllStates(
                        new ComponentRequest(SecretsProvider.SERVICE_NAME), SecretsProvider.class)
                    .stream();
              } catch (NoSuchElementException e) {
                log.debug("No secrets provider registered for tenant {}, skipping.", tenantId, e);
                return Stream.empty();
              }
            })
        .toList();
  }

  @Override
  protected SecretsProvider getConnectorById(String id) {
    return getAllConnectors().stream().filter(sp -> id.equals(sp.getId())).findFirst().orElse(null);
  }

  @Override
  protected SecretsProviderOutput mapToOutput(
      SecretsProvider connector,
      String displayName,
      CatalogConnector catalogConnector,
      ConnectorInstance instance,
      boolean existingConnector) {
    return secretsProviderMapper.toSecretsProviderOutput(
        connector, displayName, catalogConnector, instance, existingConnector);
  }

  @Override
  protected SecretsProvider createNewConnector() {
    return new SecretsProvider.Placeholder();
  }
}
