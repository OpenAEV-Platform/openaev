package io.openaev.healthcheck;

import static io.openaev.database.model.Tenant.DEFAULT_TENANT_UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import io.openaev.IntegrationTest;
import io.openaev.database.model.Inject;
import io.openaev.database.model.InjectorContract;
import io.openaev.database.model.Payload;
import io.openaev.database.model.Tenant;
import io.openaev.database.model.payload.PayloadRevocation;
import io.openaev.database.repository.PayloadRepository;
import io.openaev.database.repository.PayloadRevocationRepository;
import io.openaev.healthcheck.dto.HealthCheck;
import io.openaev.healthcheck.utils.HealthCheckUtils;
import io.openaev.utils.fixtures.InjectFixture;
import io.openaev.utils.fixtures.PayloadFixture;
import io.openaev.utils.mockUser.WithMockUser;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

@Transactional
@WithMockUser(isAdmin = true, autoJoinDefaultTenant = true)
@DisplayName("A revoked payload version shows in the health checks")
class PayloadRevocationHealthCheckTest extends IntegrationTest {

  @Autowired private HealthCheckUtils healthCheckUtils;
  @Autowired private PayloadRepository payloadRepository;
  @Autowired private PayloadRevocationRepository payloadRevocationRepository;

  private Inject injectRunning(boolean revoked, boolean enabled) {
    Payload payload = PayloadFixture.createDefaultCommand();
    payload.setTenant(new Tenant(DEFAULT_TENANT_UUID));
    payload = payloadRepository.save(payload);
    if (revoked) {
      PayloadRevocation revocation = new PayloadRevocation();
      revocation.setTenant(new Tenant(DEFAULT_TENANT_UUID));
      revocation.setPayload(payload);
      revocation.setReason("Deletes user files");
      payloadRevocationRepository.save(revocation);
    }
    InjectorContract contract = new InjectorContract();
    contract.setPayload(payload);
    Inject inject = InjectFixture.getDefaultInject();
    inject.setInjectorContract(contract);
    inject.setEnabled(enabled);
    return inject;
  }

  @ParameterizedTest(name = "revoked {0}, enabled {1}: {2} check")
  @CsvSource({"true, true, 1", "true, false, 0", "false, true, 0"})
  @DisplayName("only an enabled inject running a revoked version raises a check")
  void given_inject_should_raiseARevokedCheckOnlyWhenItWouldRunARevokedVersion(
      boolean revoked, boolean enabled, int checks) {
    // Arrange
    Inject inject = injectRunning(revoked, enabled);

    // Act
    List<HealthCheck> result = healthCheckUtils.runRevocationChecks(List.of(inject));

    // Assert
    assertThat(result)
        .extracting(HealthCheck::getType, HealthCheck::getDetail, HealthCheck::getStatus)
        .containsExactlyElementsOf(
            Collections.nCopies(
                checks,
                tuple(
                    HealthCheck.Type.PAYLOAD,
                    HealthCheck.Detail.REVOKED,
                    HealthCheck.Status.ERROR)));
  }
}
