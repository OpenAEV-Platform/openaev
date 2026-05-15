package io.openaev.utils.fixtures;

import io.openaev.aop.BypassRls;
import io.openaev.context.TenantContext;
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
      BuiltinIntegrationFactory factory, String injectorType) {
    try {
      factory.registerConnectorForTenant();
    } catch (Exception e) {
      throw new RuntimeException("Failed to initialize injector: " + injectorType, e);
    }

    return injectorRepository
        .findByTypeAndTenantId(injectorType, TenantContext.getCurrentTenant())
        .orElseThrow(
            () ->
                new IllegalStateException(
                    "Injector not found after initialization: " + injectorType));
  }

  private Injector getWellKnownInjector(
      String injectorType, BuiltinIntegrationFactory factory, boolean isPayload) {
    Injector injector =
        injectorRepository
            .findByTypeAndTenantId(injectorType, TenantContext.getCurrentTenant())
            .orElseGet(() -> initializeBuiltInInjector(factory, injectorType));
    // ensure the injector is marked for payloads
    // some tests not running in a transaction may flip this
    injector.setPayloads(isPayload);
    return injectorRepository.save(injector);
  }

  @BypassRls
  @org.springframework.transaction.annotation.Transactional
  public Injector getWellKnownOaevImplantInjector() {
    return getWellKnownInjector(
        OpenAEVImplantContract.TYPE, openaevInjectorIntegrationFactory, true);
  }

  @BypassRls
  @org.springframework.transaction.annotation.Transactional
  public Injector getWellKnownEmailInjector(boolean isPayload) {
    return getWellKnownInjector(EmailContract.TYPE, emailInjectorIntegrationFactory, isPayload);
  }
}
