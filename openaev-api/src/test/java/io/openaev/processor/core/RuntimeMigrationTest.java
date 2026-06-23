package io.openaev.processor.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import io.openaev.config.OpenAEVConfig;
import io.openaev.config.QueueConfig;
import io.openaev.context.TenantContext;
import io.openaev.database.model.DataPack;
import io.openaev.database.model.Injector;
import io.openaev.database.model.Tenant;
import io.openaev.database.repository.InjectorRepository;
import io.openaev.processor.MigrationProcessingResult;
import io.openaev.service.DataPackService;
import io.openaev.service.RabbitmqService;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Tests for all Java migrations declared in {@code io.openaev.processor.core}.
 *
 * <p>Each migration gets its own {@link Nested} class. Add new migrations here following the same
 * pattern.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Java migrations")
class RuntimeMigrationTest {

  // ── V20260420_Migrate_rabbitmq_queues ──────────────────────────────────────

  @Nested
  @DisplayName("V20260420 — Migrate RabbitMQ queues")
  class MigrateRabbitmqQueues {

    private static final String CURRENT_PREFIX = "openaev";
    private static final String TENANT_ID = Tenant.DEFAULT_TENANT_UUID;
    private static final String TENANT_PREFIX = CURRENT_PREFIX + "-" + TENANT_ID;
    private static final String INJECTOR_UUID = "54ae2cb3-604e-4dda-a042-7dd48c4df412";
    private static final String INJECTOR_TYPE = "openaev_nmap";

    @Mock private DataPackService dataPackService;
    @Mock private RabbitmqService rabbitmqService;
    @Mock private InjectorRepository injectorRepository;

    private V20260420_Migrate_rabbitmq_queues migration;

    private static QueueConfig queueConfig(String name) {
      QueueConfig config = new QueueConfig();
      config.setQueueName(name);
      return config;
    }

    private static Injector injector(String id, String type) {
      Injector injector = new Injector();
      injector.setId(id);
      injector.setType(type);
      return injector;
    }

    @BeforeEach
    void setUp() {
      OpenAEVConfig config = mock(OpenAEVConfig.class);
      lenient()
          .when(config.getQueueConfig())
          .thenReturn(Map.of("inject-trace", queueConfig("inject-trace")));

      migration =
          new V20260420_Migrate_rabbitmq_queues(
              dataPackService, rabbitmqService, injectorRepository, config);
    }

    /** Stubs that are only needed when the migration actually runs (default tenant). */
    private void stubDefaultTenantDependencies() {
      when(rabbitmqService.getPrefix()).thenReturn(CURRENT_PREFIX);
      when(injectorRepository.findAll())
          .thenReturn(List.of(injector(INJECTOR_UUID, INJECTOR_TYPE)));
    }

    /** Stubs the four management-API list calls so they return empty lists. */
    private void stubEmptyBrokerState() {
      when(rabbitmqService.listQueueNamesWithPrefix("openbas_")).thenReturn(List.of());
      when(rabbitmqService.listExchangeNamesWithPrefix("openbas_")).thenReturn(List.of());
      when(rabbitmqService.listQueueNamesWithPrefix("openaev_")).thenReturn(List.of());
      when(rabbitmqService.listExchangeNamesWithPrefix("openaev_")).thenReturn(List.of());
    }

    // -- Non-default tenant --

    @Nested
    @DisplayName("Non-default tenant")
    class NonDefaultTenant {

      @Test
      @DisplayName("given non-default tenant should skip migration")
      void given_nonDefaultTenant_should_skipMigration() {
        // Arrange
        try (MockedStatic<TenantContext> ctx = mockStatic(TenantContext.class)) {
          ctx.when(TenantContext::getCurrentTenant).thenReturn("other-tenant-id");

          // Act
          boolean result = migration.doMigrate();

          // Assert
          assertThat(result).isTrue();
          verifyNoInteractions(rabbitmqService);
        }
      }
    }

    // -- Legacy openbas queues --

    @Nested
    @DisplayName("Legacy openbas queues")
    class LegacyOpenbasQueues {

      private MockedStatic<TenantContext> ctx;

      @BeforeEach
      void setUp() {
        ctx = mockStatic(TenantContext.class);
        ctx.when(TenantContext::getCurrentTenant).thenReturn(TENANT_ID);
        stubDefaultTenantDependencies();
      }

      @AfterEach
      void tearDown() {
        ctx.close();
      }

      @Test
      @DisplayName("given empty legacy queue should delete without transfer")
      void given_emptyLegacyQueue_should_deleteWithoutTransfer() throws Exception {
        // Arrange
        stubEmptyBrokerState();
        when(rabbitmqService.listQueueNamesWithPrefix("openbas_"))
            .thenReturn(List.of("openbas_injector_openaev_nmap"));
        when(rabbitmqService.drainQueue("openbas_injector_openaev_nmap")).thenReturn(List.of());

        // Act
        boolean result = migration.doMigrate();

        // Assert
        assertThat(result).isTrue();
        verify(rabbitmqService).safeDeleteQueue("openbas_injector_openaev_nmap");
        verify(rabbitmqService, never()).publishBatch(any(), any(), any());
      }

      @Test
      @DisplayName(
          "given legacy injector queue with messages should transfer to new queue and delete")
      void given_legacyInjectorQueueWithMessages_should_transferAndDelete() throws Exception {
        // Arrange
        byte[] msg = "test-message".getBytes();
        stubEmptyBrokerState();
        when(rabbitmqService.listQueueNamesWithPrefix("openbas_"))
            .thenReturn(List.of("openbas_injector_openaev_nmap"));
        when(rabbitmqService.drainQueue("openbas_injector_openaev_nmap")).thenReturn(List.of(msg));

        // Act
        boolean result = migration.doMigrate();

        // Assert
        assertThat(result).isTrue();
        verify(rabbitmqService)
            .publishBatch(
                eq(CURRENT_PREFIX + RabbitmqService.EXCHANGE_KEY),
                eq(CURRENT_PREFIX + RabbitmqService.ROUTING_KEY + INJECTOR_UUID),
                eq(List.of(msg)));
        verify(rabbitmqService).safeDeleteQueue("openbas_injector_openaev_nmap");
      }

      @Test
      @DisplayName(
          "given legacy execution queue with messages should transfer to tenant-scoped queue")
      void given_legacyExecutionQueueWithMessages_should_transferToTenantScoped() throws Exception {
        // Arrange
        byte[] msg = "trace-data".getBytes();
        stubEmptyBrokerState();
        when(rabbitmqService.listQueueNamesWithPrefix("openbas_"))
            .thenReturn(List.of("openbas_execution_inject-trace"));
        when(rabbitmqService.drainQueue("openbas_execution_inject-trace")).thenReturn(List.of(msg));

        // Act
        boolean result = migration.doMigrate();

        // Assert
        assertThat(result).isTrue();
        verify(rabbitmqService)
            .publishBatch(
                eq(TENANT_PREFIX + "_amqp.inject-trace.exchange"),
                eq(TENANT_PREFIX + RabbitmqService.ROUTING_KEY + "inject-trace"),
                eq(List.of(msg)));
        verify(rabbitmqService).safeDeleteQueue("openbas_execution_inject-trace");
      }

      @Test
      @DisplayName("given legacy queue with unknown suffix should not delete and warn")
      void given_legacyQueueWithUnknownSuffix_should_notDeleteAndWarn() throws Exception {
        // Arrange
        stubEmptyBrokerState();
        when(rabbitmqService.listQueueNamesWithPrefix("openbas_"))
            .thenReturn(List.of("openbas_something_unknown"));
        // Target resolution fails for unknown suffix → drainQueue is never called

        // Act
        boolean result = migration.doMigrate();

        // Assert
        assertThat(result).isTrue();
        verify(rabbitmqService, never()).drainQueue(any());
        verify(rabbitmqService, never()).publishBatch(any(), any(), any());
        verify(rabbitmqService, never()).safeDeleteQueue("openbas_something_unknown");
      }
    }

    // -- Non-conforming openaev queues --

    @Nested
    @DisplayName("Non-conforming openaev queues")
    class NonConformingQueues {

      private MockedStatic<TenantContext> ctx;

      @BeforeEach
      void setUp() {
        ctx = mockStatic(TenantContext.class);
        ctx.when(TenantContext::getCurrentTenant).thenReturn(TENANT_ID);
        stubDefaultTenantDependencies();
      }

      @AfterEach
      void tearDown() {
        ctx.close();
      }

      @Test
      @DisplayName(
          "given old-format injector queue with type as suffix should transfer to UUID-based queue")
      void given_oldFormatInjectorQueue_should_transferToUuidBasedQueue() throws Exception {
        // Arrange
        byte[] msg = "payload".getBytes();
        stubEmptyBrokerState();
        when(rabbitmqService.listQueueNamesWithPrefix("openaev_"))
            .thenReturn(List.of("openaev_injector_openaev_nmap"));
        when(rabbitmqService.drainQueue("openaev_injector_openaev_nmap")).thenReturn(List.of(msg));

        // Act
        boolean result = migration.doMigrate();

        // Assert
        assertThat(result).isTrue();
        verify(rabbitmqService)
            .publishBatch(
                eq(CURRENT_PREFIX + RabbitmqService.EXCHANGE_KEY),
                eq(CURRENT_PREFIX + RabbitmqService.ROUTING_KEY + INJECTOR_UUID),
                eq(List.of(msg)));
        verify(rabbitmqService).safeDeleteQueue("openaev_injector_openaev_nmap");
      }

      @Test
      @DisplayName("given expected queue name should not process it")
      void given_expectedQueueName_should_notProcessIt() throws Exception {
        // Arrange
        String expectedQueue = CURRENT_PREFIX + "_injector_" + INJECTOR_UUID;
        stubEmptyBrokerState();
        when(rabbitmqService.listQueueNamesWithPrefix("openaev_"))
            .thenReturn(List.of(expectedQueue));

        // Act
        boolean result = migration.doMigrate();

        // Assert
        assertThat(result).isTrue();
        verify(rabbitmqService, never()).drainQueue(any());
        verify(rabbitmqService, never()).safeDeleteQueue(any());
      }
    }

    // -- Exchange cleanup --

    @Nested
    @DisplayName("Exchange cleanup")
    class ExchangeCleanup {

      private MockedStatic<TenantContext> ctx;

      @BeforeEach
      void setUp() {
        ctx = mockStatic(TenantContext.class);
        ctx.when(TenantContext::getCurrentTenant).thenReturn(TENANT_ID);
        stubDefaultTenantDependencies();
      }

      @AfterEach
      void tearDown() {
        ctx.close();
      }

      @Test
      @DisplayName("given legacy exchange should delete it")
      void given_legacyExchange_should_deleteIt() {
        // Arrange
        stubEmptyBrokerState();
        when(rabbitmqService.listExchangeNamesWithPrefix("openbas_"))
            .thenReturn(List.of("openbas_amqp.connector.exchange"));

        // Act
        boolean result = migration.doMigrate();

        // Assert
        assertThat(result).isTrue();
        verify(rabbitmqService).safeDeleteExchange("openbas_amqp.connector.exchange");
      }

      @Test
      @DisplayName("given non-conforming openaev exchange should delete it")
      void given_nonConformingExchange_should_deleteIt() {
        // Arrange
        stubEmptyBrokerState();
        when(rabbitmqService.listExchangeNamesWithPrefix("openaev_"))
            .thenReturn(List.of("openaev_amqp.old-stuff.exchange"));

        // Act
        boolean result = migration.doMigrate();

        // Assert
        assertThat(result).isTrue();
        verify(rabbitmqService).safeDeleteExchange("openaev_amqp.old-stuff.exchange");
      }

      @Test
      @DisplayName("given expected exchange should not delete it")
      void given_expectedExchange_should_notDeleteIt() {
        // Arrange
        String expectedExchange = CURRENT_PREFIX + RabbitmqService.EXCHANGE_KEY;
        stubEmptyBrokerState();
        when(rabbitmqService.listExchangeNamesWithPrefix("openaev_"))
            .thenReturn(List.of(expectedExchange));

        // Act
        boolean result = migration.doMigrate();

        // Assert
        assertThat(result).isTrue();
        verify(rabbitmqService, never()).safeDeleteExchange(expectedExchange);
      }
    }

    // -- Error handling --

    @Nested
    @DisplayName("Error handling")
    class ErrorHandling {

      private MockedStatic<TenantContext> ctx;

      @BeforeEach
      void setUp() {
        ctx = mockStatic(TenantContext.class);
        ctx.when(TenantContext::getCurrentTenant).thenReturn(TENANT_ID);
        stubDefaultTenantDependencies();
      }

      @AfterEach
      void tearDown() {
        ctx.close();
      }

      @Test
      @DisplayName("given drain throws exception should not delete queue and continue")
      void given_drainThrowsException_should_notDeleteAndContinue() throws Exception {
        // Arrange
        stubEmptyBrokerState();
        // Use a valid injector type so target resolution succeeds and drainQueue is called
        when(rabbitmqService.listQueueNamesWithPrefix("openbas_"))
            .thenReturn(List.of("openbas_injector_openaev_nmap"));
        when(rabbitmqService.drainQueue("openbas_injector_openaev_nmap"))
            .thenThrow(new RuntimeException("connection refused"));

        // Act
        boolean result = migration.doMigrate();

        // Assert
        assertThat(result).isTrue();
        verify(rabbitmqService, never()).safeDeleteQueue("openbas_injector_openaev_nmap");
        verify(rabbitmqService, never()).publishBatch(any(), any(), any());
      }
    }
  }

  // ── JavaMigration base class ───────────────────────────────────────────────

  @Nested
  @DisplayName("JavaMigration — base process() logic")
  class RuntimeMigrationBase {

    @Mock private DataPackService dataPackService;

    private boolean doMigrateResult;
    private RuntimeMigration migration;
    private Tenant tenant;

    @BeforeEach
    void setUp() {
      doMigrateResult = true;
      migration =
          new RuntimeMigration(dataPackService) {
            @Override
            protected boolean doMigrate() {
              return doMigrateResult;
            }
          };
      tenant = new Tenant();
      tenant.setId(Tenant.DEFAULT_TENANT_UUID);
    }

    @Test
    @DisplayName("given already processed migration should return SKIPPED")
    void given_alreadyProcessedMigration_should_returnSkipped() {
      // Arrange
      when(dataPackService.findByIdAndTenant(eq(migration.getMigrationId()), eq(tenant)))
          .thenReturn(Optional.of(mock(DataPack.class)));

      // Act
      MigrationProcessingResult result = migration.process(tenant);

      // Assert
      assertThat(result).isEqualTo(MigrationProcessingResult.SKIPPED);
      verify(dataPackService, never()).registerDataPack(any(), any());
    }

    @Test
    @DisplayName("given first run with successful doMigrate should return PROCESSED and register")
    void given_firstRunSuccess_should_returnProcessedAndRegister() {
      // Arrange
      when(dataPackService.findByIdAndTenant(eq(migration.getMigrationId()), eq(tenant)))
          .thenReturn(Optional.empty());

      // Act
      MigrationProcessingResult result = migration.process(tenant);

      // Assert
      assertThat(result).isEqualTo(MigrationProcessingResult.PROCESSED);
      verify(dataPackService).registerDataPack(eq(migration.getMigrationId()), eq(tenant));
    }

    @Test
    @DisplayName("given first run with failed doMigrate should return PROCESSED but not register")
    void given_firstRunFailure_should_returnProcessedButNotRegister() {
      // Arrange
      doMigrateResult = false;
      when(dataPackService.findByIdAndTenant(eq(migration.getMigrationId()), eq(tenant)))
          .thenReturn(Optional.empty());

      // Act
      MigrationProcessingResult result = migration.process(tenant);

      // Assert
      assertThat(result).isEqualTo(MigrationProcessingResult.PROCESSED);
      verify(dataPackService, never()).registerDataPack(any(), any());
    }

    @Test
    @DisplayName("migrationId should be the canonical class name for a named class")
    void given_namedMigration_should_haveMigrationIdAsCanonicalName() {
      // Arrange — use a real named subclass instead of an anonymous one
      // (anonymous classes return null for getCanonicalName())
      V20260420_Migrate_rabbitmq_queues namedMigration =
          new V20260420_Migrate_rabbitmq_queues(
              dataPackService,
              mock(RabbitmqService.class),
              mock(InjectorRepository.class),
              mock(OpenAEVConfig.class));

      // Assert
      assertThat(namedMigration.getMigrationId())
          .isEqualTo(V20260420_Migrate_rabbitmq_queues.class.getCanonicalName());
    }
  }
}
