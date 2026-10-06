package io.openaev.runner;

import static io.openaev.database.model.Tenant.DEFAULT_TENANT_UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.database.model.Asset;
import io.openaev.database.model.ContractOutputType;
import io.openaev.database.model.Finding;
import io.openaev.database.model.FindingTriage;
import io.openaev.database.model.FindingTriageHistory;
import io.openaev.database.model.FindingTriageStatus;
import io.openaev.database.model.Inject;
import io.openaev.database.model.Injector;
import io.openaev.database.model.InjectorContract;
import io.openaev.database.repository.AssetRepository;
import io.openaev.database.repository.FindingRepository;
import io.openaev.database.repository.FindingTriageHistoryRepository;
import io.openaev.database.repository.FindingTriageRepository;
import io.openaev.database.repository.InjectRepository;
import io.openaev.database.repository.InjectorContractRepository;
import io.openaev.database.repository.InjectorRepository;
import io.openaev.rest.inject.form.InjectExecutionInput;
import io.openaev.rest.inject.service.InjectExecutionService;
import io.openaev.scheduler.TenantScopedJobRunner;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;

@DisplayName("Finding demo seeder")
class FindingDemoSeederTest {

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Nested
  @DisplayName("Activation")
  class Activation {

    private final ApplicationContextRunner contextRunner =
        new ApplicationContextRunner()
            .withUserConfiguration(TestConfiguration.class, FindingDemoSeeder.class);

    @Test
    @DisplayName("Should be disabled when the opt-in property is absent")
    void given_devProfileWithoutProperty_should_notCreateSeeder() {
      contextRunner
          .withPropertyValues("spring.profiles.active=dev")
          .run(context -> assertThat(context).doesNotHaveBean(FindingDemoSeeder.class));
    }

    @Test
    @DisplayName("Should require a supported profile even when the property is enabled")
    void given_propertyWithoutSupportedProfile_should_notCreateSeeder() {
      contextRunner
          .withPropertyValues("openaev.dev.seed-findings=true")
          .run(context -> assertThat(context).doesNotHaveBean(FindingDemoSeeder.class));
    }

    @Test
    @DisplayName("Should be enabled explicitly for feature branch environments")
    void given_featureBranchProfileAndProperty_should_createSeeder() {
      contextRunner
          .withPropertyValues(
              "spring.profiles.active=test-feature-branch", "openaev.dev.seed-findings=true")
          .run(context -> assertThat(context).hasSingleBean(FindingDemoSeeder.class));
    }
  }

  @Nested
  @DisplayName("Seeding")
  class Seeding {

    @Test
    @DisplayName("Should use the callback pipeline and remain idempotent")
    void given_repeatedRuns_should_createEachOccurrenceOnceThroughCallback() throws Exception {
      // Arrange
      AssetRepository assetRepository = mock(AssetRepository.class);
      InjectorRepository injectorRepository = mock(InjectorRepository.class);
      InjectorContractRepository contractRepository = mock(InjectorContractRepository.class);
      InjectRepository injectRepository = mock(InjectRepository.class);
      FindingRepository findingRepository = mock(FindingRepository.class);
      FindingTriageRepository triageRepository = mock(FindingTriageRepository.class);
      FindingTriageHistoryRepository triageHistoryRepository =
          mock(FindingTriageHistoryRepository.class);
      InjectExecutionService executionService = mock(InjectExecutionService.class);
      Finding aliceCredentials = new Finding();
      aliceCredentials.setId("legacy-alice");
      aliceCredentials.setType(ContractOutputType.Credentials);
      aliceCredentials.setValue("alice:DEMO_HASH_ALICE");
      when(findingRepository.findAllByInjectIdAndTenantId(anyString(), eq(DEFAULT_TENANT_UUID)))
          .thenReturn(List.of(aliceCredentials));
      when(triageRepository.findByFinding_Id("legacy-alice"))
          .thenReturn(Optional.empty(), Optional.of(new FindingTriage()));
      TenantScopedJobRunner tenantScopedJobRunner = mock(TenantScopedJobRunner.class);
      doAnswer(
              invocation -> {
                invocation.<Runnable>getArgument(1).run();
                return null;
              })
          .when(tenantScopedJobRunner)
          .runInTenant(eq(DEFAULT_TENANT_UUID), any(Runnable.class));
      when(injectorRepository.findByTypeAndTenantId(anyString(), anyString()))
          .thenReturn(Optional.empty());
      when(assetRepository.findByExternalReferenceAndTenantId(anyString(), eq(DEFAULT_TENANT_UUID)))
          .thenReturn(Optional.empty());
      when(assetRepository.save(any(Asset.class)))
          .thenAnswer(
              invocation -> {
                Asset asset = invocation.getArgument(0);
                asset.setId(java.util.UUID.randomUUID().toString());
                return asset;
              });
      when(injectorRepository.save(any(Injector.class)))
          .thenAnswer(
              invocation -> {
                Injector injector = invocation.getArgument(0);
                injector.setId(java.util.UUID.randomUUID().toString());
                return injector;
              });
      when(contractRepository.findByInjectorsContaining(any(Injector.class)))
          .thenReturn(java.util.List.of());
      when(contractRepository.save(any(InjectorContract.class)))
          .thenAnswer(
              invocation -> {
                InjectorContract contract = invocation.getArgument(0);
                contract.setId(java.util.UUID.randomUUID().toString());
                return contract;
              });
      when(injectRepository.save(any(Inject.class)))
          .thenAnswer(
              invocation -> {
                Inject inject = invocation.getArgument(0);
                inject.setId(java.util.UUID.randomUUID().toString());
                return inject;
              });
      FindingDemoSeeder seeder =
          new FindingDemoSeeder(
              objectMapper,
              assetRepository,
              injectorRepository,
              contractRepository,
              injectRepository,
              findingRepository,
              triageRepository,
              triageHistoryRepository,
              executionService,
              tenantScopedJobRunner);

      // Act
      seeder.run();
      Injector existingInjector = new Injector();
      existingInjector.setId(java.util.UUID.randomUUID().toString());
      existingInjector.setTenantId(DEFAULT_TENANT_UUID);
      InjectorContract existingContract = new InjectorContract();
      existingContract.setId(java.util.UUID.randomUUID().toString());
      existingContract.setTenant(new io.openaev.database.model.Tenant(DEFAULT_TENANT_UUID));
      when(injectorRepository.findByTypeAndTenantId(anyString(), anyString()))
          .thenReturn(Optional.of(existingInjector));
      when(contractRepository.findByInjectorsContaining(existingInjector))
          .thenReturn(java.util.List.of(existingContract));
      when(injectRepository.existsByTitleAndTenantId(anyString(), anyString())).thenReturn(true);
      seeder.run();

      // Assert
      int scans = FindingDemoSeeder.OCCURRENCES_PER_FINDING;
      int exampleBatches = seeder.exampleBatches().size();
      int prowlerCallbacks = FindingDemoSeeder.DEMO_FINDING_COUNT * scans + exampleBatches * scans;
      int expectedInjects = prowlerCallbacks + scans;
      verify(injectorRepository, times(2)).save(any(Injector.class));
      verify(contractRepository, times(4)).save(any(InjectorContract.class));
      verify(injectRepository, times(expectedInjects)).save(any(Inject.class));
      ArgumentCaptor<InjectExecutionInput> callbacks =
          ArgumentCaptor.forClass(InjectExecutionInput.class);
      verify(executionService, times(expectedInjects))
          .handleInjectExecutionCallback(anyString(), isNull(), callbacks.capture());

      List<JsonNode> prowlerOutputs =
          callbacks.getAllValues().stream()
              .filter(this::isProwlerCallback)
              .map(
                  callback ->
                      read(callback.getOutputStructured()).path(FindingDemoSeeder.OUTPUT_KEY))
              .toList();
      assertThat(prowlerOutputs)
          .hasSize(prowlerCallbacks)
          .allSatisfy(
              findings -> {
                assertThat(findings.isArray()).isTrue();
                Set<String> checks = new HashSet<>();
                findings.forEach(
                    finding -> {
                      assertThat(finding.path("status_code").asText()).isEqualTo("FAIL");
                      assertThat(finding.path("metadata").path("product").path("uid").asText())
                          .isEqualTo("prowler");
                      assertThat(finding.path("metadata").path("uid").asText()).isNotBlank();
                      assertThat(finding.path("time_dt").asText()).isNotBlank();
                      // One record per check and callback, so every resource stays a Location.
                      assertThat(checks.add(finding.path("metadata").path("event_code").asText()))
                          .isTrue();
                    });
              });
      int exampleRecords = seeder.exampleBatches().stream().mapToInt(List::size).sum();
      assertThat(prowlerOutputs.stream().mapToInt(JsonNode::size).sum())
          .isEqualTo(FindingDemoSeeder.DEMO_FINDING_COUNT * scans + exampleRecords * scans);

      List<JsonNode> nativeOutputs =
          callbacks.getAllValues().stream()
              .filter(callback -> !isProwlerCallback(callback))
              .map(InjectExecutionInput::getOutputStructured)
              .map(this::read)
              .toList();
      assertThat(nativeOutputs).hasSize(scans);
      nativeOutputs.forEach(
          nativeOutput ->
              FindingDemoSeeder.NATIVE_OUTPUTS.forEach(
                  output -> {
                    JsonNode records = nativeOutput.path(output.field());
                    assertThat(records.size()).as(output.field()).isPositive();
                    records.forEach(
                        record -> {
                          if (record.path("asset_id").isArray()) {
                            assertThat(record.path("asset_id").size())
                                .as(output.field())
                                .isGreaterThanOrEqualTo(2);
                            assertThat(record.path("time_dt").asText()).isNotBlank();
                          }
                        });
                  }));
      assertThat(nativeOutputs.getFirst().path("cves").toString()).doesNotContain("CVE-2024-3400");
      assertThat(nativeOutputs.getLast().path("cves").toString()).contains("CVE-2024-3400");

      ArgumentCaptor<FindingTriage> triages = ArgumentCaptor.forClass(FindingTriage.class);
      verify(triageRepository, times(1)).save(triages.capture());
      assertThat(triages.getValue().getStatus()).isEqualTo(FindingTriageStatus.CONFIRMED);
      verify(triageHistoryRepository, times(1)).save(any(FindingTriageHistory.class));
      verify(injectorRepository, atLeastOnce())
          .linkContract(anyString(), anyString(), eq(DEFAULT_TENANT_UUID));
    }

    private boolean isProwlerCallback(InjectExecutionInput callback) {
      return read(callback.getOutputStructured()).has(FindingDemoSeeder.OUTPUT_KEY);
    }

    private JsonNode read(String content) {
      try {
        return objectMapper.readTree(content);
      } catch (Exception exception) {
        throw new AssertionError(exception);
      }
    }
  }

  @org.springframework.boot.test.context.TestConfiguration(proxyBeanMethods = false)
  static class TestConfiguration {
    @Bean
    ObjectMapper objectMapper() {
      return new ObjectMapper();
    }

    @Bean
    AssetRepository assetRepository() {
      return mock(AssetRepository.class);
    }

    @Bean
    InjectorRepository injectorRepository() {
      return mock(InjectorRepository.class);
    }

    @Bean
    InjectorContractRepository injectorContractRepository() {
      return mock(InjectorContractRepository.class);
    }

    @Bean
    InjectRepository injectRepository() {
      return mock(InjectRepository.class);
    }

    @Bean
    FindingRepository findingRepository() {
      return mock(FindingRepository.class);
    }

    @Bean
    FindingTriageRepository findingTriageRepository() {
      return mock(FindingTriageRepository.class);
    }

    @Bean
    FindingTriageHistoryRepository findingTriageHistoryRepository() {
      return mock(FindingTriageHistoryRepository.class);
    }

    @Bean
    InjectExecutionService injectExecutionService() {
      return mock(InjectExecutionService.class);
    }

    @Bean
    TenantScopedJobRunner tenantScopedJobRunner() {
      return mock(TenantScopedJobRunner.class);
    }
  }
}
