package io.openaev.rest.payload;

import static io.openaev.database.model.BaseInjectExpectation.EXPECTATION_TYPE.DETECTION;
import static io.openaev.database.model.BaseInjectExpectation.EXPECTATION_TYPE.PREVENTION;
import static io.openaev.database.model.SecurityPlatform.SECURITY_PLATFORM_TYPE.EDR;
import static io.openaev.database.model.SecurityPlatform.SECURITY_PLATFORM_TYPE.SIEM;
import static io.openaev.database.model.SecurityPlatform.SECURITY_PLATFORM_TYPE.XDR;
import static org.assertj.core.api.Assertions.assertThat;

import io.openaev.IntegrationTest;
import io.openaev.context.TenantScopedTransaction;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Payload;
import io.openaev.database.model.Tenant;
import io.openaev.database.repository.PayloadRepository;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.PayloadFixture;
import io.openaev.utils.mockUser.WithMockUser;
import jakarta.persistence.EntityManager;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.hibernate.Session;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@WithMockUser(isAdmin = true)
@DisplayName("Payload expected security platforms dirty check")
class PayloadExpectedSecurityPlatformsDirtyCheckTest extends IntegrationTest {

  @Autowired private TenantIsolationTestHelper tenantHelper;
  @Autowired private TenantScopedTransaction tenantTx;
  @Autowired private PayloadRepository payloadRepository;
  @Autowired private EntityManager entityManager;

  private String tenantId;
  private String payloadId;

  @BeforeEach
  void seedPayload() throws Exception {
    tenantId = tenantHelper.createTenantWithCurrentUser("payload-dirty-check").getId();
    tenantTx.execute(
        TxCtx.forTenant(tenantId),
        () -> {
          Payload payload = PayloadFixture.createDefaultCommand();
          payload.setTenant(new Tenant(tenantId));
          payload.setExpectedSecurityPlatforms(
              new HashMap<>(
                  Map.of(DETECTION, List.of(EDR, XDR, SIEM), PREVENTION, List.of(EDR, XDR))));
          payloadId = payloadRepository.save(payload).getId();
        });
  }

  @AfterEach
  void cleanup() {
    tenantTx.execute(TxCtx.forTenant(tenantId), () -> payloadRepository.deleteById(payloadId));
  }

  @Test
  @DisplayName("Loading a payload with expected security platforms does not mark it dirty")
  void given_expectedSecurityPlatforms_should_notBeDirtyAfterLoad() {
    boolean dirty =
        tenantTx.execute(
            TxCtx.forTenant(tenantId),
            () -> {
              entityManager.clear();
              Payload loaded = payloadRepository.findById(payloadId).orElseThrow();
              assertThat(loaded.getExpectedSecurityPlatforms().get(DETECTION))
                  .containsExactly(EDR, XDR, SIEM);
              return entityManager.unwrap(Session.class).isDirty();
            });

    assertThat(dirty).isFalse();
  }
}
