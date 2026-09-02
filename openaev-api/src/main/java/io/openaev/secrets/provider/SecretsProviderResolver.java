package io.openaev.secrets.provider;

import io.openaev.database.model.ConnectorInstanceInMemory;
import io.openaev.integration.ComponentRequest;
import io.openaev.integration.ManagerFactory;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Single place resolving which {@link SecretsProvider} backs a secret reference.
 *
 * <p>Always returns the {@link SecretsProvider} contract, never a concrete implementation: callers
 * must go through the abstraction so a second backend can be added without touching them.
 *
 * <p>Resolution is in-memory once a tenant's Manager exists, but creating that Manager on first
 * access writes to the database. Callers running a background job must therefore resolve inside
 * their transactional phase, never during a network phase.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SecretsProviderResolver {

  private final ManagerFactory managerFactory;

  /**
   * Resolves the provider backing a given connector instance.
   *
   * @param tenantId the owning tenant
   * @param connectorInstanceId the connector instance the reference points at
   * @return the provider
   * @throws IllegalStateException if no provider is available for that instance
   */
  public SecretsProvider resolveByConnectorInstanceId(String tenantId, String connectorInstanceId) {
    return findByConnectorInstanceId(tenantId, connectorInstanceId)
        .orElseThrow(
            () ->
                new IllegalStateException(
                    "No secrets provider is available for connector instance "
                        + connectorInstanceId));
  }

  /**
   * Non-throwing variant, for the background validation run: an unresolvable provider must degrade
   * to an outcome for that one credential, not abort the whole tenant batch.
   *
   * @param tenantId the owning tenant
   * @param connectorInstanceId the connector instance the reference points at
   * @return the provider, or empty when it cannot be resolved
   */
  public Optional<SecretsProvider> findByConnectorInstanceId(
      String tenantId, String connectorInstanceId) {
    if (connectorInstanceId == null || connectorInstanceId.isBlank()) {
      return Optional.empty();
    }
    try {
      ConnectorInstanceInMemory instance = new ConnectorInstanceInMemory();
      instance.setId(connectorInstanceId);
      return Optional.ofNullable(
          managerFactory.getManager(tenantId).requestForInstance(instance, SecretsProvider.class));
    } catch (Exception e) {
      log.warn("No secrets provider available for connector instance {}", connectorInstanceId);
      return Optional.empty();
    }
  }

  /**
   * Resolves the tenant's local provider.
   *
   * <p>Targets {@link SecretsProviderType#LOCAL} explicitly, and that is deliberate: it is the
   * default backend used when creating a credential, for as long as there is no mechanism letting
   * an operator choose one. When provider selection lands, this becomes a lookup on the chosen
   * provider — it is not an oversight.
   *
   * @param tenantId the owning tenant
   * @return the local provider
   * @throws IllegalStateException if the tenant has no local provider
   */
  public SecretsProvider resolveLocalProvider(String tenantId) {
    try {
      return managerFactory
          .getManager(tenantId)
          .requestManyAllStates(
              new ComponentRequest(SecretsProvider.SERVICE_NAME), SecretsProvider.class)
          .stream()
          .filter(provider -> Objects.equals(provider.getType(), SecretsProviderType.LOCAL.type))
          .findFirst()
          .orElseThrow(
              () ->
                  new IllegalStateException(
                      "No secrets provider found for type " + SecretsProviderType.LOCAL));
    } catch (Exception e) {
      throw new IllegalStateException(
          "No secrets provider is available for type "
              + SecretsProviderType.LOCAL
              + " in current tenant",
          e);
    }
  }
}
