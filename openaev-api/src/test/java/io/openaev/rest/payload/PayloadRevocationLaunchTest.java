package io.openaev.rest.payload;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;

import io.openaev.IntegrationTest;
import io.openaev.context.TenantContext;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Inject;
import io.openaev.database.model.InjectorContract;
import io.openaev.database.model.Payload;
import io.openaev.database.repository.InjectorContractRepository;
import io.openaev.integration.ManagerFactory;
import io.openaev.integration.impl.injectors.openaev.OpenaevInjectorIntegrationFactory;
import io.openaev.rest.exception.BadRequestException;
import io.openaev.rest.inject.service.InjectService;
import io.openaev.rest.payload.form.PayloadCreateInput;
import io.openaev.rest.payload.service.PayloadCreationService;
import io.openaev.service.payload.PayloadRevocationService;
import io.openaev.utils.fixtures.InjectFixture;
import io.openaev.utils.fixtures.PayloadInputFixture;
import io.openaev.utils.mockUser.WithMockUser;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

@TestInstance(PER_CLASS)
@Transactional
@DisplayName("A scenario or simulation running a revoked payload does not launch (F18)")
class PayloadRevocationLaunchTest extends IntegrationTest {

  @Autowired private PayloadCreationService payloadCreationService;
  @Autowired private PayloadRevocationService payloadRevocationService;
  @Autowired private InjectorContractRepository injectorContractRepository;
  @Autowired private InjectService injectService;
  @Autowired private OpenaevInjectorIntegrationFactory openaevInjectorIntegrationFactory;
  @Autowired private ManagerFactory managerFactory;

  @BeforeEach
  void registerPayloadInjector() throws Exception {
    openaevInjectorIntegrationFactory.registerConnectorForTenant(TenantContext.getCurrentTenant());
    managerFactory.getManager(TenantContext.getCurrentTenant()).monitorIntegrations();
  }

  private Inject injectOnRevokedPayload(boolean enabled) {
    PayloadCreateInput input =
        PayloadInputFixture.createDefaultPayloadCreateInputForCommandLine(List.of());
    input.setName("Revoked " + UUID.randomUUID());
    Payload payload =
        payloadCreationService
            .createPayload(TxCtx.forTenant(TenantContext.getCurrentTenant()), input)
            .payload();
    payloadRevocationService.revoke(payload.getId(), "unsafe");
    InjectorContract contract =
        injectorContractRepository.findInjectorContractByPayload(payload).orElseThrow();
    Inject inject = InjectFixture.getDefaultInject();
    inject.setInjectorContract(contract);
    inject.setEnabled(enabled);
    return inject;
  }

  @Test
  @DisplayName("An enabled inject on a revoked payload blocks the launch and is named")
  @WithMockUser(isAdmin = true)
  void given_enabledInjectOnRevokedPayload_should_refuseLaunch() {
    // Arrange
    Inject inject = injectOnRevokedPayload(true);

    // Act & Assert
    assertThatThrownBy(() -> injectService.throwIfAnyPayloadRevoked(List.of(inject)))
        .isInstanceOf(BadRequestException.class)
        .hasMessageContaining(inject.getTitle());
  }

  @Test
  @DisplayName("A disabled inject never runs, so it does not block the launch")
  @WithMockUser(isAdmin = true)
  void given_disabledInjectOnRevokedPayload_should_allowLaunch() {
    // Arrange
    Inject inject = injectOnRevokedPayload(false);

    // Act & Assert
    assertThatCode(() -> injectService.throwIfAnyPayloadRevoked(List.of(inject)))
        .doesNotThrowAnyException();
  }
}
