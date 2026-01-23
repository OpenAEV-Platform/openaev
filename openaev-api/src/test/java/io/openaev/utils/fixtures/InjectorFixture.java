package io.openaev.utils.fixtures;

import static io.openaev.integration.impl.injectors.email.EmailInjectorIntegration.EMAIL_INJECTOR_ID;

import io.openaev.database.model.Injector;
import io.openaev.database.repository.InjectorRepository;
import io.openaev.injectors.email.EmailContract;
import io.openaev.injectors.openaev.OpenAEVImplantContract;
import io.openaev.integration.Manager;
import io.openaev.integration.impl.injectors.openaev.OpenaevInjectorIntegrationFactory;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class InjectorFixture {
  @Autowired InjectorRepository injectorRepository;
  @Autowired private OpenaevInjectorIntegrationFactory openaevInjectorIntegrationFactory;

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

  private Injector initializeOAEVImplantInjector() {
    try {
      new Manager(List.of(openaevInjectorIntegrationFactory)).monitorIntegrations();
    } catch (Exception e) {
      throw new RuntimeException("Failed to initialize OAEV Implant Injector", e);
    }

    return injectorRepository
        .findByType(OpenAEVImplantContract.TYPE)
        .orElseThrow(
            () ->
                new IllegalStateException(
                    "Injector not found after initialization: " + OpenAEVImplantContract.TYPE));
  }

  public Injector createOAEVEmailInjector() {
    return createInjector(EMAIL_INJECTOR_ID, "Email", EmailContract.TYPE);
  }

  public Injector getWellKnownOaevImplantInjector() {
    Injector injector =
        injectorRepository
            .findByType(OpenAEVImplantContract.TYPE)
            .orElseGet(this::initializeOAEVImplantInjector);
    // ensure the injector is marked for payloads
    // some tests not running in a transaction may flip this
    injector.setPayloads(true);
    injectorRepository.save(injector);
    return injector;
  }

  public Injector getWellKnownEmailInjector(boolean isPayload) {
    Optional<Injector> injectorOptional = injectorRepository.findByType(EmailContract.TYPE);
    Injector injector =
        injectorOptional.orElseGet(() -> injectorRepository.save(createOAEVEmailInjector()));
    // ensure the injector is marked for payloads
    // some tests not running in a transaction may flip this
    injector.setPayloads(isPayload);
    injectorRepository.save(injector);
    return injector;
  }
}
