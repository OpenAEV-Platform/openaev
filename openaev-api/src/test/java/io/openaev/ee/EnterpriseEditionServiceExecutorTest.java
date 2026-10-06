package io.openaev.ee;

import static io.openaev.integration.impl.executors.crowdstrike.CrowdStrikeExecutorIntegration.CROWDSTRIKE_EXECUTOR_NAME;
import static io.openaev.integration.impl.executors.mde.MdeExecutorIntegration.MDE_EXECUTOR_NAME;
import static io.openaev.integration.impl.executors.paloaltocortex.PaloAltoCortexExecutorIntegration.PALOALTOCORTEX_EXECUTOR_NAME;
import static io.openaev.integration.impl.executors.sentinelone.SentinelOneExecutorIntegration.SENTINELONE_EXECUTOR_NAME;
import static io.openaev.integration.impl.executors.tanium.TaniumExecutorIntegration.TANIUM_EXECUTOR_NAME;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.openaev.database.model.Agent;
import io.openaev.database.model.Executor;
import io.openaev.database.model.InjectStatus;
import io.openaev.rest.exception.LicenseRestrictionException;
import io.openaev.utils.fixtures.InjectStatusFixture;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class EnterpriseEditionServiceExecutorTest {

  private final EnterpriseEditionService enterpriseEditionService = new EnterpriseEditionService();

  private static License license(boolean validated) {
    License license = new License();
    license.setLicenseValidated(validated);
    license.setExpirationDate(Instant.now().plus(30, ChronoUnit.DAYS));
    return license;
  }

  private static Agent agentOf(String executorName) {
    Executor executor = new Executor();
    executor.setName(executorName);
    Agent agent = new Agent();
    agent.setExecutor(executor);
    return agent;
  }

  @ParameterizedTest(name = "{0}")
  @ValueSource(
      strings = {
        CROWDSTRIKE_EXECUTOR_NAME,
        TANIUM_EXECUTOR_NAME,
        SENTINELONE_EXECUTOR_NAME,
        PALOALTOCORTEX_EXECUTOR_NAME,
        MDE_EXECUTOR_NAME
      })
  @DisplayName("given no valid license, an EDR executor dispatch should be refused")
  void given_noValidLicense_should_refuseEdrExecutorDispatch(String executorName) {
    // Arrange
    InjectStatus injectStatus = InjectStatusFixture.createPendingInjectStatus();
    int tracesBefore = injectStatus.getTraces().size();

    // Act & Assert
    assertThatThrownBy(
            () ->
                enterpriseEditionService.throwEEExecutorService(
                    license(false), executorName, injectStatus))
        .isInstanceOf(LicenseRestrictionException.class)
        .hasMessageContaining(executorName);
    assertThat(injectStatus.getTraces()).hasSize(tracesBefore + 1);
  }

  @Test
  @DisplayName("given a valid license, the MDE executor dispatch should be allowed")
  void given_validLicense_should_allowMdeDispatch() {
    // Arrange
    InjectStatus injectStatus = InjectStatusFixture.createPendingInjectStatus();

    // Act & Assert
    assertThatCode(
            () ->
                enterpriseEditionService.throwEEExecutorService(
                    license(true), MDE_EXECUTOR_NAME, injectStatus))
        .doesNotThrowAnyException();
  }

  @Test
  @DisplayName("an agent driven by the MDE executor should be detected as Enterprise Edition")
  void given_mdeAgent_should_beDetectedAsEeExecutor() {
    // Act
    List<String> found =
        enterpriseEditionService.detectEEExecutors(
            List.of(agentOf(MDE_EXECUTOR_NAME), agentOf("OpenAEV Agent")));

    // Assert
    assertThat(found).containsExactly(MDE_EXECUTOR_NAME);
  }
}
