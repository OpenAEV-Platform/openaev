package io.openaev.telemetry.metric_collectors;

import static io.openaev.helper.CryptoHelper.hashWithSHA256;
import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.openaev.context.TenantScopedTransaction;
import io.openaev.context.TxCtx;
import io.openaev.database.repository.CredentialSecretReferenceRepository;
import io.openaev.rest.exception.CredentialResolutionException.ErrorCode;
import io.opentelemetry.api.common.Attributes;
import java.util.Map;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@ExtendWith(MockitoExtension.class)
@DisplayName("CredentialMetricCollector Tests")
class CredentialMetricCollectorTest {

  @Mock private MetricRegistry metricRegistry;
  @Mock private TenantScopedTransaction tenantTx;
  @Mock private CredentialSecretReferenceRepository credentialSecretReferenceRepository;

  @InjectMocks private CredentialMetricCollector credentialMetricCollector;

  @Captor private ArgumentCaptor<Supplier<Long>> supplierCaptor;
  @Captor private ArgumentCaptor<Supplier<Map<Attributes, Long>>> multiGaugeCaptor;

  private static Attributes usageAttributes(
      String userId, String credentialId, String credentialType, String injectorType) {
    return Attributes.of(
        stringKey("user_hash"),
        hashWithSHA256(userId),
        stringKey("credential_hash"),
        hashWithSHA256(credentialId),
        stringKey("credential_type"),
        credentialType,
        stringKey("injector_type"),
        injectorType);
  }

  private static Attributes errorCodeAttributes(ErrorCode errorCode) {
    return Attributes.of(stringKey("error_code"), errorCode.name());
  }

  @Nested
  @DisplayName("Credential count gauge")
  class CredentialCount {

    @Test
    @DisplayName("given credentials, should report their count across every tenant")
    void given_credentials_should_reportTheirCountAcrossEveryTenant() {
      // Arrange
      when(tenantTx.execute(any(TxCtx.class), ArgumentMatchers.<Supplier<Long>>any()))
          .thenAnswer(invocation -> invocation.<Supplier<Long>>getArgument(1).get());
      when(credentialSecretReferenceRepository.globalCount()).thenReturn(2L);

      // Act
      credentialMetricCollector.init();

      // Assert
      verify(metricRegistry)
          .registerGauge(
              eq("total_credentials_count"), eq("Number of credentials"), supplierCaptor.capture());
      assertThat(supplierCaptor.getValue().get()).isEqualTo(2L);
      verify(tenantTx).execute(eq(TxCtx.allTenants()), ArgumentMatchers.<Supplier<Long>>any());
    }
  }

  @Nested
  @DisplayName("Credential usage gauge")
  class CredentialUsages {

    @AfterEach
    void clearTransactionSynchronization() {
      if (TransactionSynchronizationManager.isSynchronizationActive()) {
        TransactionSynchronizationManager.clearSynchronization();
      }
    }

    @Test
    @DisplayName("given an active transaction, should count the usage only once it commits")
    void given_activeTransaction_should_countUsageOnlyOnceItCommits() {
      // Arrange
      TransactionSynchronizationManager.initSynchronization();

      // Act
      credentialMetricCollector.addCredentialUsage(
          "user-1", "credential-1", "CLOUD_AWS", "openaev_aws");

      // Assert
      assertThat(credentialMetricCollector.collectCredentialUsages()).isEmpty();
      TransactionSynchronizationManager.getSynchronizations().forEach(sync -> sync.afterCommit());
      assertThat(credentialMetricCollector.collectCredentialUsages())
          .containsExactlyEntriesOf(
              Map.of(usageAttributes("user-1", "credential-1", "CLOUD_AWS", "openaev_aws"), 1L));
    }

    @Test
    @DisplayName("given a transaction that rolls back, should never count the usage")
    void given_rolledBackTransaction_should_neverCountUsage() {
      // Arrange
      TransactionSynchronizationManager.initSynchronization();

      // Act: Spring never calls afterCommit on a rollback
      credentialMetricCollector.addCredentialUsage(
          "user-1", "credential-1", "CLOUD_AWS", "openaev_aws");
      TransactionSynchronizationManager.clearSynchronization();

      // Assert
      assertThat(credentialMetricCollector.collectCredentialUsages()).isEmpty();
    }

    @Test
    @DisplayName(
        "given credentials attached by users, should count them by hashed user and credential,"
            + " type and injector")
    void given_attachedCredentials_should_countThemByHashedUserCredentialTypeAndInjector() {
      // Arrange
      credentialMetricCollector.addCredentialUsage(
          "user-1", "credential-1", "CLOUD_AWS", "openaev_aws");
      credentialMetricCollector.addCredentialUsage(
          "user-1", "credential-1", "CLOUD_AWS", "openaev_aws");
      credentialMetricCollector.addCredentialUsage(
          "user-2", "credential-1", "CLOUD_AWS", "openaev_aws");
      credentialMetricCollector.addCredentialUsage(
          "user-2", "credential-2", "CLOUD_AWS", "openaev_aws");

      // Act
      credentialMetricCollector.init();

      // Assert
      verify(metricRegistry)
          .registerMultiGauge(
              eq("credential_usages_count"), anyString(), multiGaugeCaptor.capture());
      assertThat(multiGaugeCaptor.getValue().get())
          .containsExactlyInAnyOrderEntriesOf(
              Map.of(
                  usageAttributes("user-1", "credential-1", "CLOUD_AWS", "openaev_aws"), 2L,
                  usageAttributes("user-2", "credential-1", "CLOUD_AWS", "openaev_aws"), 1L,
                  usageAttributes("user-2", "credential-2", "CLOUD_AWS", "openaev_aws"), 1L));
    }

    @Test
    @DisplayName("given a user and a credential, should export their hashes and never their ids")
    void given_userAndCredential_should_exportHashesNotIds() {
      // Act
      credentialMetricCollector.addCredentialUsage(
          "user-1", "credential-1", "IDENTITY", "openaev_nmap");

      // Assert
      assertThat(credentialMetricCollector.collectCredentialUsages().keySet())
          .singleElement()
          .satisfies(
              attributes -> {
                assertThat(attributes.get(stringKey("user_hash")))
                    .isEqualTo(hashWithSHA256("user-1"));
                assertThat(attributes.get(stringKey("credential_hash")))
                    .isEqualTo(hashWithSHA256("credential-1"));
                assertThat(attributes.asMap().values()).doesNotContain("user-1", "credential-1");
              });
    }

    @Test
    @DisplayName("given an inject without injector, should export its injector type as unknown")
    void given_injectWithoutInjector_should_exportUnknownInjectorType() {
      // Act
      credentialMetricCollector.addCredentialUsage("user-1", "credential-1", "CLOUD_AZURE", null);

      // Assert
      assertThat(credentialMetricCollector.collectCredentialUsages())
          .containsExactlyEntriesOf(
              Map.of(usageAttributes("user-1", "credential-1", "CLOUD_AZURE", "unknown"), 1L));
    }

    @Test
    @DisplayName("given collected usages, should reset them for the next export")
    void given_collectedUsages_should_resetForNextExport() {
      // Arrange
      credentialMetricCollector.addCredentialUsage(
          "user-1", "credential-1", "CLOUD_GCP", "openaev_gcp");
      credentialMetricCollector.collectCredentialUsages();

      // Act
      Map<Attributes, Long> nextExport = credentialMetricCollector.collectCredentialUsages();

      // Assert
      assertThat(nextExport).isEmpty();
    }
  }

  @Nested
  @DisplayName("Credential resolution successes gauge")
  class ResolutionSuccesses {

    @Test
    @DisplayName("given successful resolutions, should count them and reset for the next export")
    void given_successfulResolutions_should_countThemAndReset() {
      // Arrange
      credentialMetricCollector.addResolutionSuccess();
      credentialMetricCollector.addResolutionSuccess();

      // Act
      long firstExport = credentialMetricCollector.collectResolutionSuccesses();
      long nextExport = credentialMetricCollector.collectResolutionSuccesses();

      // Assert
      assertThat(firstExport).isEqualTo(2L);
      assertThat(nextExport).isZero();
    }

    @Test
    @DisplayName("given the collector is initialised, should register the successes gauge")
    void given_init_should_registerSuccessesGauge() {
      // Arrange
      credentialMetricCollector.addResolutionSuccess();

      // Act
      credentialMetricCollector.init();

      // Assert
      verify(metricRegistry)
          .registerGauge(
              eq("credential_resolution_successes_count"), anyString(), supplierCaptor.capture());
      assertThat(supplierCaptor.getValue().get()).isEqualTo(1L);
    }
  }

  @Nested
  @DisplayName("Credential resolution failures gauge")
  class ResolutionFailures {

    @ParameterizedTest
    @EnumSource(ErrorCode.class)
    @DisplayName("given a refused resolution, should count it under its error code")
    void given_refusedResolution_should_countItsErrorCode(ErrorCode errorCode) {
      // Act
      credentialMetricCollector.addResolutionFailure(errorCode);
      credentialMetricCollector.addResolutionFailure(errorCode);

      // Assert
      assertThat(credentialMetricCollector.collectResolutionFailures())
          .containsExactlyEntriesOf(Map.of(errorCodeAttributes(errorCode), 2L));
    }

    @Test
    @DisplayName("given collected failures, should reset them for the next export")
    void given_collectedFailures_should_resetForNextExport() {
      // Arrange
      credentialMetricCollector.addResolutionFailure(ErrorCode.CREDENTIAL_INACTIVE);
      credentialMetricCollector.collectResolutionFailures();

      // Act
      Map<Attributes, Long> nextExport = credentialMetricCollector.collectResolutionFailures();

      // Assert
      assertThat(nextExport).isEmpty();
    }

    @Test
    @DisplayName("given the collector is initialised, should register the failures gauge")
    void given_init_should_registerFailuresGauge() {
      // Arrange
      credentialMetricCollector.addResolutionFailure(ErrorCode.CREDENTIAL_ACCESS_DENIED);

      // Act
      credentialMetricCollector.init();

      // Assert
      verify(metricRegistry)
          .registerMultiGauge(
              eq("credential_resolution_failures_count"), anyString(), multiGaugeCaptor.capture());
      assertThat(multiGaugeCaptor.getValue().get())
          .containsExactlyEntriesOf(
              Map.of(errorCodeAttributes(ErrorCode.CREDENTIAL_ACCESS_DENIED), 1L));
    }
  }
}
