package io.openaev.rest.payload;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;

import io.openaev.IntegrationTest;
import io.openaev.database.model.Payload;
import io.openaev.rest.payload.service.PayloadService;
import io.openaev.utils.fixtures.InjectorContractFixture;
import io.openaev.utils.fixtures.PayloadFixture;
import io.openaev.utils.fixtures.composers.InjectorContractComposer;
import io.openaev.utils.fixtures.composers.PayloadComposer;
import io.openaev.utils.helpers.PayloadVersionTestHelper;
import io.openaev.utils.mockUser.WithMockUser;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

@TestInstance(PER_CLASS)
@Transactional
@DisplayName("Payload name uniqueness (#3399)")
class PayloadNameTest extends IntegrationTest {

  @Autowired private PayloadService payloadService;
  @Autowired private PayloadComposer payloadComposer;
  @Autowired private InjectorContractComposer injectorContractComposer;
  @Autowired private PayloadVersionTestHelper payloadVersionTestHelper;

  private Payload persist(String name, String externalId, String version) {
    Payload payload = PayloadFixture.createDefaultCommand();
    payload.setName(name);
    Payload persisted =
        injectorContractComposer
            .forInjectorContract(InjectorContractFixture.createDefaultInjectorContract())
            .withPayload(payloadComposer.forPayload(payload))
            .persist()
            .get()
            .getPayload();
    return version == null
        ? persisted
        : payloadVersionTestHelper.asManagedVersion(persisted, externalId, version);
  }

  @Test
  @DisplayName("A name used by another payload is taken, whatever its case")
  @WithMockUser(isAdmin = true)
  void given_otherPayloadWithName_should_refuseName() {
    // Arrange
    String name = "Dump LSASS " + UUID.randomUUID();
    persist(name, null, null);

    // Act & Assert
    assertFalse(payloadService.isNameAvailable(name.toUpperCase(), null));
  }

  @Test
  @DisplayName("A payload keeps its own name")
  @WithMockUser(isAdmin = true)
  void given_ownName_should_acceptName() {
    // Arrange
    String name = "Dump LSASS " + UUID.randomUUID();
    Payload payload = persist(name, null, null);

    // Act & Assert
    assertTrue(payloadService.isNameAvailable(name, payload.getId()));
  }

  @Test
  @DisplayName("Versions of the same payload share their name")
  @WithMockUser(isAdmin = true)
  void given_otherVersionOfSamePayload_should_acceptName() {
    // Arrange
    String name = "Dump LSASS " + UUID.randomUUID();
    String externalId = UUID.randomUUID().toString();
    persist(name, externalId, "1.0.0");
    Payload v2 = persist(name, externalId, "2.0.0");

    // Act & Assert
    assertTrue(payloadService.isNameAvailable(name, v2.getId()));
  }
}
