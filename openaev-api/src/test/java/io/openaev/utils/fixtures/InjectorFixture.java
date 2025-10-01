package io.openaev.utils.fixtures;

import io.openaev.database.model.Injector;
import io.openaev.database.repository.InjectorRepository;
import java.time.Instant;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

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

  public Injector getWellKnownOaevImplantInjector() {
    Injector injector = injectorRepository.findByType("openaev_implant").orElseThrow();
    // ensure the injector is marked for payloads
    // some tests not running in a transaction may flip this
    injector.setPayloads(true);
    injectorRepository.save(injector);
    return injector;
  }

  public Injector getWellKnownEmailInjector() {
    Injector injector = injectorRepository.findByType("openaev_email").orElseThrow();
    // ensure the injector is marked for payloads
    // some tests not running in a transaction may flip this
    injector.setPayloads(true);
    injectorRepository.save(injector);
    return injector;
  }
}
