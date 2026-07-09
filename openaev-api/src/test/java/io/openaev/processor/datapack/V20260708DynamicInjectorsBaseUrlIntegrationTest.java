package io.openaev.processor.datapack;

import static org.assertj.core.api.Assertions.assertThat;

import io.openaev.IntegrationTest;
import io.openaev.context.TenantContext;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

@Transactional
@WithMockUser(isAdmin = true)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("DataPack V20260708 Dynamic Injectors Base URL Integration Tests")
class V20260708DynamicInjectorsBaseUrlIntegrationTest extends IntegrationTest {

  @Autowired private V20260708_Dynamic_injectors_base_url dataPack;
  @Autowired private InjectorRepository injectorRepository;
  @Autowired private DataPackService dataPackService;
  @Autowired private EntityManager entityManager;

  @BeforeEach
  void setUp() {
    ReflectionTestUtils.setField(dataPack, "baseUrl", null);
    clearDatapackRegistration();
  }

  @Nested
  @DisplayName("Base URL replacement behavior")
  class BaseUrlReplacementBehavior {

    @Test
    @DisplayName("Should replace configured base URL in injector commands")
    void given_configuredBaseUrl_should_replaceMatchingValuesInInjectorCommandMaps() {
      // Arrange
      String configuredBaseUrl = "https://openaev.example.com/";
      ReflectionTestUtils.setField(dataPack, "baseUrl", configuredBaseUrl);

      Injector injector =
          persistInjectorWithCommands(
              Map.of(
                  "run", "curl https://openaev.example.com/api/run",
                  "keep", "echo untouched"),
              Map.of("clear", "wget https://openaev.example.com/cleanup"));

      // Act
      MigrationProcessingResult result = processForCurrentTenant();
      Injector updated = findInjector(injector.getId());

      // Assert
      assertThat(result).isEqualTo(MigrationProcessingResult.PROCESSED);
      assertThat(updated.getExecutorCommands().get("run")).isEqualTo("curl #{baseUrl}/api/run");
      assertThat(updated.getExecutorCommands().get("keep")).isEqualTo("echo untouched");
      assertThat(updated.getExecutorClearCommands().get("clear"))
          .isEqualTo("wget #{baseUrl}/cleanup");
    }

    @Test
    @DisplayName("Should not modify commands when configured base URL is blank")
    void given_blankBaseUrl_should_notModifyInjectorCommands() {
      // Arrange
      ReflectionTestUtils.setField(dataPack, "baseUrl", "   ");
      Injector injector =
          persistInjectorWithCommands(
              Map.of("run", "curl https://openaev.example.com/api/run"),
              Map.of("clear", "wget https://openaev.example.com/cleanup"));

      // Act
      MigrationProcessingResult result = processForCurrentTenant();
      Injector updated = findInjector(injector.getId());

      // Assert
      assertThat(result).isEqualTo(MigrationProcessingResult.PROCESSED);
      assertThat(updated.getExecutorCommands().get("run"))
          .isEqualTo("curl https://openaev.example.com/api/run");
      assertThat(updated.getExecutorClearCommands().get("clear"))
          .isEqualTo("wget https://openaev.example.com/cleanup");
    }

    @Test
    @DisplayName("Should not modify commands when configured base URL is absent")
    void given_baseUrlAbsentInCommands_should_notModifyInjectorCommands() {
      // Arrange
      ReflectionTestUtils.setField(dataPack, "baseUrl", "https://openaev.example.com/");
      Injector injector =
          persistInjectorWithCommands(
              Map.of("run", "curl https://another.example.com/api/run"),
              Map.of("clear", "wget https://another.example.com/cleanup"));

      // Act
      MigrationProcessingResult result = processForCurrentTenant();
      Injector updated = findInjector(injector.getId());

      // Assert
      assertThat(result).isEqualTo(MigrationProcessingResult.PROCESSED);
      assertThat(updated.getExecutorCommands().get("run"))
          .isEqualTo("curl https://another.example.com/api/run");
      assertThat(updated.getExecutorClearCommands().get("clear"))
          .isEqualTo("wget https://another.example.com/cleanup");
    }
  }

  @Nested
  @DisplayName("DataPack idempotence")
  class DataPackIdempotence {

    @Test
    @DisplayName("Should skip second execution for the same tenant")
    void given_datapackAlreadyProcessed_should_skipSecondExecution() {
      // Arrange
      ReflectionTestUtils.setField(dataPack, "baseUrl", "https://openaev.example.com/");
      Injector injector =
          persistInjectorWithCommands(
              Map.of("run", "curl https://openaev.example.com/api/run"), Map.of());
      dataPackService.registerDataPack(
          dataPack.getPackId(), new Tenant(TenantContext.getCurrentTenant()));

      // Act
      MigrationProcessingResult result = processForCurrentTenant();
      Injector unchanged = findInjector(injector.getId());

      // Assert
      assertThat(result).isEqualTo(MigrationProcessingResult.SKIPPED);
      assertThat(unchanged.getExecutorCommands().get("run"))
          .isEqualTo("curl https://openaev.example.com/api/run");
    }
  }

  private MigrationProcessingResult processForCurrentTenant() {
    return dataPack.process(new Tenant(TenantContext.getCurrentTenant()));
  }

  private Injector persistInjectorWithCommands(
      Map<String, String> executorCommands, Map<String, String> executorClearCommands) {
    Injector injector =
        InjectorFixture.createInjector(
            UUID.randomUUID().toString(),
            "injector-" + UUID.randomUUID(),
            "type-" + UUID.randomUUID());
    injector.setExecutorCommands(new HashMap<>(executorCommands));
    injector.setExecutorClearCommands(new HashMap<>(executorClearCommands));

    Injector saved = injectorRepository.save(injector);
    entityManager.flush();
    entityManager.clear();
    return saved;
  }

  private Injector findInjector(String injectorId) {
    return injectorRepository.findById(injectorId).orElseThrow();
  }

  private void clearDatapackRegistration() {
    entityManager
        .createNativeQuery("DELETE FROM datapacks WHERE datapack_id = ?1 AND tenant_id = ?2")
        .setParameter(1, dataPack.getPackId())
        .setParameter(2, TenantContext.getCurrentTenant())
        .executeUpdate();
    entityManager.flush();
    entityManager.clear();
  }
}
