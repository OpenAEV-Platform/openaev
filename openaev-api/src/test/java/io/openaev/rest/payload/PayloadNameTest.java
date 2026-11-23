package io.openaev.rest.payload;

import static io.openaev.rest.payload.service.PayloadService.PAYLOAD_NAME_TAKEN;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.openaev.IntegrationTest;
import io.openaev.context.TenantContext;
import io.openaev.database.model.Payload;
import io.openaev.rest.exception.BadRequestException;
import io.openaev.rest.payload.service.PayloadService;
import io.openaev.utils.fixtures.PayloadFixture;
import io.openaev.utils.fixtures.composers.PayloadComposer;
import io.openaev.utils.mockUser.WithMockUser;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Transactional
@WithMockUser(isAdmin = true)
@DisplayName("A payload name")
class PayloadNameTest extends IntegrationTest {

  @Autowired private PayloadService payloadService;
  @Autowired private PayloadComposer payloadComposer;

  private String tenantId;
  private String name;

  @BeforeEach
  void setup() {
    payloadComposer.reset();
    tenantId = TenantContext.getCurrentTenant();
    name = "Dump LSASS " + UUID.randomUUID();
  }

  private Payload persist(Payload payload) {
    payload.setName(name);
    return payloadComposer.forPayload(payload).persist().get();
  }

  @Test
  @DisplayName("used by another payload is taken, whatever its case and spaces")
  void given_otherPayloadWithName_should_refuseName() {
    // Arrange
    persist(PayloadFixture.createDefaultCommand());

    // Act & Assert
    assertThatThrownBy(
            () -> payloadService.assertNameAvailable(tenantId, " " + name.toUpperCase(), null))
        .isInstanceOf(BadRequestException.class)
        .hasMessage(PAYLOAD_NAME_TAKEN);
  }

  @Test
  @DisplayName("stays available to the payload that has it")
  void given_ownName_should_acceptName() {
    // Arrange
    Payload payload = persist(PayloadFixture.createDefaultCommand());

    // Act & Assert
    assertThatCode(() -> payloadService.assertNameAvailable(tenantId, name, payload.getId()))
        .doesNotThrowAnyException();
  }

  @Test
  @DisplayName("is shared by the versions of one payload")
  void given_otherVersionOfSamePayload_should_acceptName() {
    // Arrange
    Payload first = persist(PayloadFixture.createDefaultCommand());
    Payload second = PayloadFixture.createDefaultCommand();
    second.setIdentity(first.getIdentity());
    second.setVersion("2.0.0");
    persist(second);

    // Act & Assert
    assertThatCode(() -> payloadService.assertNameAvailable(tenantId, name, second.getId()))
        .doesNotThrowAnyException();
  }

  @Test
  @DisplayName("left blank is not checked")
  void given_blankName_should_acceptIt() {
    // Act & Assert
    assertThatCode(() -> payloadService.assertNameAvailable(tenantId, " ", null))
        .doesNotThrowAnyException();
  }
}
