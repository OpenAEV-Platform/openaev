package io.openaev.processor.datapack;

import static org.assertj.core.api.Assertions.assertThat;

import io.openaev.IntegrationTest;
import io.openaev.context.TenantContext;
import io.openaev.context.TenantScopedTransaction;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Injector;
import io.openaev.database.model.Tenant;
import io.openaev.database.repository.InjectorRepository;
import io.openaev.processor.MigrationProcessingResult;
import io.openaev.service.DataPackService;
import io.openaev.utils.fixtures.InjectorFixture;
import io.openaev.utils.mockUser.WithMockUser;
import jakarta.persistence.EntityManager;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Deliberately NOT {@code @Transactional}: {@link V20260708_Dynamic_injectors_base_url} no longer
 * opens its own transaction/scope — it runs inside whatever the caller opens (in production, {@code
 * MigrationProcessor}). Every DB interaction here goes through {@link #inScope} to reproduce that
 * same tenant-scoped transaction; writes are committed and cleaned up explicitly.
 */
@WithMockUser(isAdmin = true)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("DataPack V20260708 Dynamic Injectors Base URL Integration Tests")
class V20260708DynamicInjectorsBaseUrlIntegrationTest extends IntegrationTest {

  @Autowired private V20260708_Dynamic_injectors_base_url dataPack;
  @Autowired private InjectorRepository injectorRepository;
  @Autowired private DataPackService dataPackService;
  @Autowired private EntityManager entityManager;
  @Autowired private TenantScopedTransaction tenantTx;

  private String createdInjectorId;

  @BeforeEach
  void setUp() {
    clearDatapackRegistration();
  }

  @AfterEach
  void tearDown() {
    if (createdInjectorId != null) {
      inScope(
          () -> {
            entityManager
                .createNativeQuery("DELETE FROM injectors WHERE injector_id = ?1")
                .setParameter(1, createdInjectorId)
                .executeUpdate();
            return null;
          });
      createdInjectorId = null;
    }
    clearDatapackRegistration();
  }

  /**
   * Runs {@code work} inside the same kind of tenant-scoped transaction MigrationProcessor opens.
   */
  private <T> T inScope(Supplier<T> work) {
    return tenantTx.execute(TxCtx.forTenant(TenantContext.getCurrentTenant()), work);
  }

  @Nested
  @DisplayName("Dynamic placeholder replacement behavior")
  class DynamicPlaceholderReplacementBehavior {

    @Test
    @DisplayName("Should replace server value with baseUrl placeholder")
    void given_serverCommand_should_replaceServerValueWithBaseUrlPlaceholder() {
      // Arrange
      Injector injector =
          persistInjectorWithCommands(
              Map.of("run", "server=\\\"http://192.168.1.13:3001\\\""), Map.of());

      // Act
      MigrationProcessingResult result = processForCurrentTenant();
      Injector updated = findInjector(injector.getId());

      // Assert
      assertThat(result).isEqualTo(MigrationProcessingResult.PROCESSED);
      assertThat(updated.getExecutorCommands().get("run")).isEqualTo("server=\\\"#{baseUrl}\\\"");
    }

    @Test
    @DisplayName("Should replace implant URL prefix with baseUrl placeholder")
    void given_escapedCurlImplantCommand_should_replaceUrlPrefixWithBaseUrlPlaceholder() {
      // Arrange
      Injector injector =
          persistInjectorWithCommands(
              Map.of("run", "curl -s -X GET \\\"URL/api/tenants/#{tenant}/implant/"), Map.of());

      // Act
      MigrationProcessingResult result = processForCurrentTenant();
      Injector updated = findInjector(injector.getId());

      // Assert
      assertThat(result).isEqualTo(MigrationProcessingResult.PROCESSED);
      assertThat(updated.getExecutorCommands().get("run"))
          .contains("\\\"#{baseUrl}/api/tenants/#{tenant}/implant/")
          .doesNotContain("\\\"URL/api/tenants/#{tenant}/implant/");
    }

    @Test
    @DisplayName("Should replace max_size value with maxSize placeholder")
    void given_maxSizeCommand_should_replaceMaxSizeValueWithPlaceholder() {
      // Arrange
      Injector injector =
          persistInjectorWithCommands(Map.of("run", "max_size=\\\"50\\\""), Map.of());

      // Act
      MigrationProcessingResult result = processForCurrentTenant();
      Injector updated = findInjector(injector.getId());

      // Assert
      assertThat(result).isEqualTo(MigrationProcessingResult.PROCESSED);
      assertThat(updated.getExecutorCommands().get("run")).isEqualTo("max_size=\\\"#{maxSize}\\\"");
    }

    @Test
    @DisplayName("Should replace unsecured_certificate value with unsecuredCertificate placeholder")
    void
        given_unsecuredCertificateCommand_should_replaceUnsecuredCertificateValueWithPlaceholder() {
      // Arrange
      Injector injector =
          persistInjectorWithCommands(
              Map.of("run", "unsecured_certificate=\\\"true\\\""), Map.of());

      // Act
      MigrationProcessingResult result = processForCurrentTenant();
      Injector updated = findInjector(injector.getId());

      // Assert
      assertThat(result).isEqualTo(MigrationProcessingResult.PROCESSED);
      assertThat(updated.getExecutorCommands().get("run"))
          .isEqualTo("unsecured_certificate=\\\"#{unsecuredCertificate}\\\"");
    }

    @Test
    @DisplayName("Should replace with_proxy value with withProxy placeholder")
    void given_withProxyCommand_should_replaceWithProxyValueWithPlaceholder() {
      // Arrange
      Injector injector =
          persistInjectorWithCommands(Map.of("run", "with_proxy=\\\"false\\\""), Map.of());

      // Act
      MigrationProcessingResult result = processForCurrentTenant();
      Injector updated = findInjector(injector.getId());

      // Assert
      assertThat(result).isEqualTo(MigrationProcessingResult.PROCESSED);
      assertThat(updated.getExecutorCommands().get("run"))
          .isEqualTo("with_proxy=\\\"#{withProxy}\\\"");
    }

    @Test
    @DisplayName("Should replace all supported dynamic values in a single command")
    void given_commandWithAllSupportedPatterns_should_replaceAllWithPlaceholders() {
      // Arrange
      Injector injector =
          persistInjectorWithCommands(
              Map.of(
                  "run",
                  "server=\\\"http://192.168.1.13:3001\\\" max_size=\\\"50\\\" unsecured_certificate=\\\"true\\\" with_proxy=\\\"false\\\""),
              Map.of());

      // Act
      MigrationProcessingResult result = processForCurrentTenant();
      Injector updated = findInjector(injector.getId());

      // Assert
      assertThat(result).isEqualTo(MigrationProcessingResult.PROCESSED);
      assertThat(updated.getExecutorCommands().get("run"))
          .contains("server=\\\"#{baseUrl}\\\"")
          .contains("max_size=\\\"#{maxSize}\\\"")
          .contains("unsecured_certificate=\\\"#{unsecuredCertificate}\\\"")
          .contains("with_proxy=\\\"#{withProxy}\\\"");
    }

    @Test
    @DisplayName("Should not modify commands when no pattern matches")
    void given_commandWithoutMatchingPattern_should_notModifyInjectorCommands() {
      // Arrange
      Injector injector =
          persistInjectorWithCommands(Map.of("run", "echo untouched"), Map.of("clear", "noop"));

      // Act
      MigrationProcessingResult result = processForCurrentTenant();
      Injector updated = findInjector(injector.getId());

      // Assert
      assertThat(result).isEqualTo(MigrationProcessingResult.PROCESSED);
      assertThat(updated.getExecutorCommands().get("run")).isEqualTo("echo untouched");
      assertThat(updated.getExecutorClearCommands().get("clear")).isEqualTo("noop");
    }

    @Test
    @DisplayName("Should replace clear commands when executor commands map is null")
    void given_nullExecutorCommands_should_replaceOnlyClearCommands() {
      // Arrange
      Injector injector =
          persistInjectorWithCommands(
              null, Map.of("clear", "server=\\\"http://192.168.1.13:3001\\\""));

      // Act
      MigrationProcessingResult result = processForCurrentTenant();
      Injector updated = findInjector(injector.getId());

      // Assert
      assertThat(result).isEqualTo(MigrationProcessingResult.PROCESSED);
      assertThat(updated.getExecutorCommands()).isNull();
      assertThat(updated.getExecutorClearCommands().get("clear"))
          .isEqualTo("server=\\\"#{baseUrl}\\\"");
    }

    @Test
    @DisplayName("Should replace server URL and escaped curl implant URL in the same command")
    void given_commandWithServerAndEscapedCurlImplant_should_replaceBothPatterns() {
      // Arrange
      Injector injector =
          persistInjectorWithCommands(
              Map.of(
                  "run",
                  "server=\\\"http://192.168.1.13:3001\\\" && curl -s -X GET \\\"URL/api/tenants/#{tenant}/implant/"),
              Map.of());

      // Act
      MigrationProcessingResult result = processForCurrentTenant();
      Injector updated = findInjector(injector.getId());

      // Assert
      assertThat(result).isEqualTo(MigrationProcessingResult.PROCESSED);
      assertThat(updated.getExecutorCommands().get("run"))
          .contains("server=\\\"#{baseUrl}\\\"")
          .contains("\\\"#{baseUrl}/api/tenants/#{tenant}/implant/")
          .doesNotContain("server=\\\"http://192.168.1.13:3001\\\"")
          .doesNotContain("\\\"URL/api/tenants/#{tenant}/implant/");
    }

    @Test
    @DisplayName("Should not modify command when command value is null")
    void given_nullCommandValue_should_notModifyInjectorCommands() {
      // Arrange
      Map<String, String> executorCommands = new HashMap<>();
      executorCommands.put("run", null);
      executorCommands.put("other", "echo untouched");

      Injector injector = persistInjectorWithCommands(executorCommands, Map.of());

      // Act
      MigrationProcessingResult result = processForCurrentTenant();
      Injector updated = findInjector(injector.getId());

      // Assert
      assertThat(result).isEqualTo(MigrationProcessingResult.PROCESSED);
      assertThat(updated.getExecutorCommands()).containsEntry("run", null);
      assertThat(updated.getExecutorCommands()).containsEntry("other", "echo untouched");
    }

    @Test
    @DisplayName("Should replace with_proxy when prefixed with dollar sign")
    void given_dollarPrefixedWithProxy_should_replaceWithProxyPlaceholder() {
      // Arrange
      Injector injector =
          persistInjectorWithCommands(Map.of("run", "$with_proxy=\\\"false\\\""), Map.of());

      // Act
      MigrationProcessingResult result = processForCurrentTenant();
      Injector updated = findInjector(injector.getId());

      // Assert
      assertThat(result).isEqualTo(MigrationProcessingResult.PROCESSED);
      assertThat(updated.getExecutorCommands().get("run"))
          .isEqualTo("$with_proxy=\\\"#{withProxy}\\\"");
    }
  }

  @Nested
  @DisplayName("DataPack idempotence")
  class DataPackIdempotence {

    @Test
    @DisplayName("Should skip second execution for the same tenant")
    void given_datapackAlreadyProcessed_should_skipSecondExecution() {
      // Arrange
      Injector injector =
          persistInjectorWithCommands(
              Map.of("run", "server=\\\"http://192.168.1.13:3001\\\""), Map.of());
      dataPackService.registerDataPack(
          dataPack.getPackId(), new Tenant(TenantContext.getCurrentTenant()));

      // Act
      MigrationProcessingResult result = processForCurrentTenant();
      Injector unchanged = findInjector(injector.getId());

      // Assert
      assertThat(result).isEqualTo(MigrationProcessingResult.SKIPPED);
      assertThat(unchanged.getExecutorCommands().get("run"))
          .isEqualTo("server=\\\"http://192.168.1.13:3001\\\"");
    }
  }

  private MigrationProcessingResult processForCurrentTenant() {
    return inScope(() -> dataPack.process(new Tenant(TenantContext.getCurrentTenant())));
  }

  private Injector persistInjectorWithCommands(
      Map<String, String> executorCommands, Map<String, String> executorClearCommands) {
    Injector injector =
        InjectorFixture.createInjector(
            UUID.randomUUID().toString(),
            "injector-" + UUID.randomUUID(),
            "type-" + UUID.randomUUID());
    injector.setExecutorCommands(executorCommands == null ? null : new HashMap<>(executorCommands));
    injector.setExecutorClearCommands(
        executorClearCommands == null ? null : new HashMap<>(executorClearCommands));
    // Write attribution is explicit since injectors went fully v2 (no more TenantIdBaseListener).
    injector.setTenantId(TenantContext.getCurrentTenant());

    Injector saved =
        inScope(
            () -> {
              Injector persisted = injectorRepository.save(injector);
              entityManager.flush();
              entityManager.clear();
              return persisted;
            });
    createdInjectorId = saved.getId();
    return saved;
  }

  private Injector findInjector(String injectorId) {
    return inScope(
        () ->
            injectorRepository
                .findByIdAndTenantId(injectorId, TenantContext.getCurrentTenant())
                .orElseThrow());
  }

  private void clearDatapackRegistration() {
    inScope(
        () -> {
          entityManager
              .createNativeQuery("DELETE FROM datapacks WHERE datapack_id = ?1 AND tenant_id = ?2")
              .setParameter(1, dataPack.getPackId())
              .setParameter(2, TenantContext.getCurrentTenant())
              .executeUpdate();
          entityManager.flush();
          entityManager.clear();
          return null;
        });
  }
}
