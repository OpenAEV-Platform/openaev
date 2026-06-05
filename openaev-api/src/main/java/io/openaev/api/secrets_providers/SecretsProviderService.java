package io.openaev.api.secrets_providers;

import static io.openaev.utils.TxCtxScopeUtils.tenantIdsFromHTTPCtx;

import io.openaev.api.secrets_providers.form.SecretsProviderOutput;
import io.openaev.context.TxCtx;
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
import java.util.Set;
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

  @Autowired
  public SecretsProviderService(
      ConnectorInstanceConfigurationRepository connectorInstanceConfigurationRepository,
      CatalogConnectorService catalogConnectorService,
      ConnectorInstanceService connectorInstanceService,
      SecretsProviderMapper secretsProviderMapper,
      CatalogConnectorMapper catalogConnectorMapper,
      ManagerFactory managerFactory) {
    super(
        ConnectorType.SECRETS_PROVIDER,
        connectorInstanceConfigurationRepository,
        catalogConnectorService,
        connectorInstanceService,
        catalogConnectorMapper);
    this.secretsProviderMapper = secretsProviderMapper;
    this.managerFactory = managerFactory;
  }

  /**
   * Retrieve all secrets providers, scoped to the authorized {@link TxCtx} handed down by the
   * controller. The {@code ctx} was produced by {@code TxCtxArgumentResolver}, i.e. it already went
   * through the {@code users_tenants} membership check, so the tenant list here is guaranteed
   * authorized. Secrets providers are in-memory {@code Manager} state with no repository-backed
   * tenant filter, so the tenant scope must be passed in explicitly rather than read back from the
   * ambient transaction scope; {@link TxCtx.AllTenants} is actively rejected on this HTTP path.
   *
   * @param ctx the authorized tenant scope for this request
   * @param isIncludeNext Include pending providers.
   * @return List of secrets provider output
   */
  public Iterable<SecretsProviderOutput> secretsProviderOutput(TxCtx ctx, boolean isIncludeNext) {
    List<SecretsProvider> connectors = getConnectorsForTenants(tenantIdsFromHTTPCtx(ctx));
    return buildConnectorsOutput(connectors, isIncludeNext);
  }

  /**
   * Single-resource lookup scoped to the authorized {@link TxCtx}.
   *
   * @param ctx the authorized tenant scope for this request
   * @param id the secrets provider id
   * @return the secrets provider if visible in the authorized scope, otherwise {@code null}
   */
  public SecretsProvider getConnectorById(TxCtx ctx, String id) {
    return getConnectorsForTenants(tenantIdsFromHTTPCtx(ctx)).stream()
        .filter(sp -> id.equals(sp.getId()))
        .findFirst()
        .orElse(null);
  }

  /**
   * Retrieves IDs of resources associated with a secrets provider, scoped to the authorized {@link
   * TxCtx}.
   *
   * @param ctx the authorized tenant scope for this request
   * @param secretProviderId secret provider identifier
   * @return connector instance ID and catalog connector ID if available, null values if not found
   */
  public ConnectorIds getSecretsProviderRelationsId(TxCtx ctx, String secretProviderId) {
    return getConnectorRelationsId(getConnectorById(ctx, secretProviderId), secretProviderId);
  }

  private List<SecretsProvider> getConnectorsForTenants(Set<String> tenantIds) {
    return tenantIds.stream()
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

  /**
   * Secrets providers are in-memory {@code Manager} state, not repository-backed rows: they cannot
   * be enumerated without an explicit, authorized tenant scope. Callers must use the {@link TxCtx}
   * overloads ({@link #secretsProviderOutput(TxCtx, boolean)}, {@link #getConnectorById(TxCtx,
   * String)}, {@link #getSecretsProviderRelationsId(TxCtx, String)}), which fail closed. This
   * no-arg path (used by the repository-backed connector types via {@link
   * AbstractConnectorService}) is intentionally unsupported here so no code can read providers from
   * the ambient scope.
   */
  @Override
  protected List<SecretsProvider> getAllConnectors() {
    throw new UnsupportedOperationException(
        "Secrets providers require an explicit authorized tenant scope; use the TxCtx overloads.");
  }

  @Override
  protected SecretsProvider getConnectorById(String id) {
    throw new UnsupportedOperationException(
        "Secrets providers require an explicit authorized tenant scope; use getConnectorById(TxCtx, String).");
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
      throw new UnsupportedOperationException("Cannot create abstract secrets provider.");
  }
}
