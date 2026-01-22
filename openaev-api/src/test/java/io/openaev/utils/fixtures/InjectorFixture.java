package io.openaev.utils.fixtures;

import io.openaev.database.model.Injector;
import io.openaev.database.repository.InjectorRepository;
import java.time.Instant;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import static io.openaev.integration.impl.injectors.email.EmailInjectorIntegration.EMAIL_INJECTOR_ID;
import static io.openaev.integration.impl.injectors.openaev.OpenaevInjectorIntegration.OPENAEV_INJECTOR_ID;

@Component
public class InjectorFixture {
  @Autowired InjectorRepository injectorRepository;

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

  public Injector createOAEVImplantInjector() {
    return createInjector(OPENAEV_INJECTOR_ID, "OpenAEV Implant", "openaev_implant");
  }

  public Injector createOAEVEmailInjector() {
    return createInjector(EMAIL_INJECTOR_ID, "Email", "openaev_email");
  }

  public Injector getWellKnownOaevImplantInjector() {
    Injector injector =
        injectorRepository.findByType("openaev_implant").orElse(createOAEVImplantInjector());
    // ensure the injector is marked for payloads
    // some tests not running in a transaction may flip this
    injector.setPayloads(true);
    injectorRepository.save(injector);
    return injector;
  }

  public Injector getWellKnownEmailInjector(boolean isPayload) {
    Injector injector =
        injectorRepository.findByType("openaev_email").orElse(createOAEVEmailInjector());
    // ensure the injector is marked for payloads
    // some tests not running in a transaction may flip this
    injector.setPayloads(isPayload);
    injectorRepository.save(injector);
    return injector;
  }
}
