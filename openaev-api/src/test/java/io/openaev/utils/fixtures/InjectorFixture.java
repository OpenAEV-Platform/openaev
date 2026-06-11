package io.openaev.utils.fixtures;

import io.openaev.database.model.Injector;
import io.openaev.database.repository.InjectorRepository;
import io.openaev.injectors.email.EmailContract;
import io.openaev.injectors.openaev.OpenAEVImplantContract;
import io.openaev.integration.BuiltinIntegrationFactory;
import io.openaev.integration.impl.injectors.email.EmailInjectorIntegrationFactory;
import io.openaev.integration.impl.injectors.openaev.OpenaevInjectorIntegrationFactory;
import java.time.Instant;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class InjectorFixture {
  @Autowired InjectorRepository injectorRepository;
  @Autowired private OpenaevInjectorIntegrationFactory openaevInjectorIntegrationFactory;
  @Autowired private EmailInjectorIntegrationFactory emailInjectorIntegrationFactory;

  public static Injector createDefaultPayloadInjector() {
    Injector injector =
        createInjector(
            UUID.randomUUID().toString(),
            UUID.randomUUID().toString(),
            UUID.randomUUID().toString());
    injector.setPayloads(true);
    return injector;
  }

  public static Injector createInjector(String id, String name, String type) {
    Injector injector = new Injector();
    injector.setId(id);
    injector.setName(name);
    injector.setType(type);
    injector.setExternal(false);
    injector.setCreatedAt(Instant.now());
    injector.setUpdatedAt(Instant.now());
    return injector;
  }

  public static Injector createDefaultInjector(String injectorName) {
    return createInjector(
        UUID.randomUUID().toString(), injectorName, injectorName.toLowerCase().replace(" ", "-"));
  }

  private Injector initializeBuiltInInjector(
      String tenantId, BuiltinIntegrationFactory factory, String injectorType) {
    try {
      factory.registerConnectorForTenant(tenantId);
    } catch (Exception e) {
      throw new RuntimeException("Failed to initialize injector: " + injectorType, e);
    }

    return injectorRepository
        .findByTypeAndTenantId(injectorType, tenantId)
        .orElseThrow(
            () ->
                new IllegalStateException(
                    "Injector not found after initialization: " + injectorType));
  }

  private Injector getWellKnownInjector(
      String tenantId, String injectorType, BuiltinIntegrationFactory factory, boolean isPayload) {
    Injector injector =
        injectorRepository
            .findByTypeAndTenantId(injectorType, tenantId)
            .orElseGet(() -> initializeBuiltInInjector(tenantId, factory, injectorType));
    // ensure the injector is marked for payloads
    // some tests not running in a transaction may flip this
    injector.setPayloads(isPayload);
    return injectorRepository.save(injector);
  }

  public Injector getWellKnownOaevImplantInjector(String tenantId) {
    return getWellKnownInjector(
        tenantId, OpenAEVImplantContract.TYPE, openaevInjectorIntegrationFactory, true);
  }

  public Injector getWellKnownEmailInjector(String tenantId, boolean isPayload) {
    return getWellKnownInjector(
        tenantId, EmailContract.TYPE, emailInjectorIntegrationFactory, isPayload);
  }
}
