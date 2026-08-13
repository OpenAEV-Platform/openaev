package io.openaev.utils.fixtures;

import io.openaev.context.TenantContext;
import io.openaev.database.model.Injector;
import io.openaev.database.repository.InjectorRepository;
import io.openaev.injectors.email.EmailContract;
import io.openaev.injectors.openaev.OpenAEVImplantContract;
import io.openaev.integration.BuiltinIntegrationFactory;
import io.openaev.integration.impl.injectors.email.EmailInjectorIntegrationFactory;
import io.openaev.integration.impl.injectors.openaev.OpenaevInjectorIntegrationFactory;
import io.openaev.rest.injector.form.InjectorCreateInput;
import io.openaev.rest.injector_contract.form.InjectorContractInput;
import java.time.Instant;
import java.util.List;
import java.util.Map;
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

  public static InjectorCreateInput createDefaultInjectorCreateInput(
      String id, String name, String type, String contractId) {
    InjectorCreateInput input = new InjectorCreateInput();
    input.setId(id);
    input.setName(name);
    input.setType(type);
    input.setCategory("attack");
    input.setContracts(List.of(createDefaultInjectorContractInput(contractId)));
    return input;
  }

  public static InjectorContractInput createDefaultInjectorContractInput(String contractId) {
    InjectorContractInput contract = new InjectorContractInput();
    contract.setId(contractId);
    contract.setLabels(Map.of("en", "Test Contract"));
    contract.setContent("{\"fields\":[]}");
    return contract;
  }

  private Injector initializeBuiltInInjector(
      BuiltinIntegrationFactory factory, String injectorType) {
    try {
      factory.registerConnectorForTenant(TenantContext.getCurrentTenant());
    } catch (Exception e) {
      throw new RuntimeException("Failed to initialize injector: " + injectorType, e);
    }

    return injectorRepository.findAll().stream()
        .filter(i -> injectorType.equals(i.getType()))
        .filter(i -> TenantContext.getCurrentTenant().equals(i.getTenantId()))
        .findFirst()
        .orElseThrow(
            () ->
                new IllegalStateException(
                    "Injector not found after initialization: " + injectorType));
  }

  private Injector getWellKnownInjector(
      String injectorType, BuiltinIntegrationFactory factory, boolean isPayload) {
    Injector injector =
        injectorRepository.findAll().stream()
            .filter(i -> injectorType.equals(i.getType()))
            .filter(i -> TenantContext.getCurrentTenant().equals(i.getTenantId()))
            .findFirst()
            .orElseGet(() -> initializeBuiltInInjector(factory, injectorType));
    // ensure the injector is marked for payloads
    // some tests not running in a transaction may flip this
    injector.setPayloads(isPayload);
    return injectorRepository.save(injector);
  }

  @org.springframework.transaction.annotation.Transactional
  public Injector getWellKnownOaevImplantInjector() {
    return getWellKnownInjector(
        OpenAEVImplantContract.TYPE, openaevInjectorIntegrationFactory, true);
  }

  @org.springframework.transaction.annotation.Transactional
  public Injector getWellKnownEmailInjector(boolean isPayload) {
    return getWellKnownInjector(EmailContract.TYPE, emailInjectorIntegrationFactory, isPayload);
  }
}
