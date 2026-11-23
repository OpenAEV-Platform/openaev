package io.openaev.xtmone;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import io.openaev.context.TenantScopedTransaction;
import io.openaev.context.TxCtx;
import io.openaev.database.model.AgentStatus;
import io.openaev.database.model.Collector;
import io.openaev.database.model.Executor;
import io.openaev.database.model.Injector;
import io.openaev.database.repository.AgentRepository;
import io.openaev.database.repository.CollectorRepository;
import io.openaev.database.repository.ExecutorRepository;
import io.openaev.database.repository.InjectorRepository;
import io.openaev.injectors.email.service.ImapService;
import io.openaev.injectors.email.service.SmtpService;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@DisplayName("XTM One health snapshot")
class XtmOneHealthSnapshotTest {

  @Mock private TenantScopedTransaction tenantTx;
  @Mock private InjectorRepository injectorRepository;
  @Mock private CollectorRepository collectorRepository;
  @Mock private ExecutorRepository executorRepository;
  @Mock private AgentRepository agentRepository;
  @Mock private SmtpService smtpService;
  @Mock private ImapService imapService;

  @InjectMocks private XtmOneHealthSnapshot snapshot;

  @BeforeEach
  void inlineTransaction() {
    lenient()
        .when(tenantTx.execute(any(TxCtx.class), any(Supplier.class)))
        .thenAnswer(invocation -> ((Supplier<?>) invocation.getArgument(1)).get());
    ReflectionTestUtils.setField(snapshot, "smtpEnabled", true);
    ReflectionTestUtils.setField(snapshot, "imapEnabled", true);
  }

  private static Injector injector(String id, boolean external, Instant updatedAt) {
    Injector injector = new Injector();
    injector.setId(id);
    injector.setName("Injector " + id);
    injector.setExternal(external);
    injector.setUpdatedAt(updatedAt);
    return injector;
  }

  @Test
  @DisplayName("Given unavailable mail services should report SMTP as an error and IMAP a warning")
  void given_unavailableMailServices_should_reportThem() {
    // -- ARRANGE --
    when(smtpService.isServiceAvailable()).thenReturn(false);
    when(imapService.isServiceAvailable()).thenReturn(false);

    // -- ACT --
    List<Map<String, Object>> checks = snapshot.healthchecks();

    // -- ASSERT --
    assertThat(checks)
        .containsExactly(
            Map.of("type", "smtp", "status", "ERROR", "count", 1),
            Map.of("type", "imap", "status", "WARNING", "count", 1));
  }

  @Test
  @DisplayName("Given IMAP disabled should not report it, whatever its state")
  void given_imapDisabled_should_notReportIt() {
    // -- ARRANGE --
    ReflectionTestUtils.setField(snapshot, "imapEnabled", false);
    when(smtpService.isServiceAvailable()).thenReturn(true);

    // -- ACT --
    List<Map<String, Object>> checks = snapshot.healthchecks();

    // -- ASSERT --
    assertThat(checks).isEmpty();
  }

  @Test
  @DisplayName("Given built-in and external integrations should send the external ones only")
  void given_integrations_should_sendTheExternalOnes() {
    // -- ARRANGE --
    Instant older = Instant.parse("2026-11-20T04:00:00Z");
    Instant newer = Instant.parse("2026-11-20T05:00:00Z");
    when(injectorRepository.findAll())
        .thenReturn(
            List.of(
                injector("builtin", false, newer),
                injector("nuclei", true, older),
                // Same injector registered in a second tenant: the newest heartbeat wins.
                injector("nuclei", true, newer)));
    Collector collector = new Collector();
    collector.setId("crowdstrike");
    collector.setName("CrowdStrike");
    collector.setExternal(true);
    collector.setUpdatedAt(older);
    when(collectorRepository.findAll()).thenReturn(List.of(collector));
    Executor executor = new Executor();
    executor.setId("openaev_agent");
    executor.setExternal(false);
    when(executorRepository.findAll()).thenReturn(List.of(executor));

    // -- ACT --
    List<Map<String, Object>> integrations = snapshot.integrations();

    // -- ASSERT --
    assertThat(integrations)
        .containsExactly(
            Map.of(
                "id",
                "nuclei",
                "name",
                "Injector nuclei",
                "kind",
                "injector",
                "updated_at",
                newer.toString()),
            Map.of(
                "id",
                "crowdstrike",
                "name",
                "CrowdStrike",
                "kind",
                "collector",
                "updated_at",
                older.toString()));
  }

  @Test
  @DisplayName("Given agents should send their total and offline counts")
  void given_agents_should_sendTheirCounts() {
    // -- ARRANGE --
    when(smtpService.isServiceAvailable()).thenReturn(true);
    when(imapService.isServiceAvailable()).thenReturn(true);
    when(agentRepository.count()).thenReturn(10L);
    when(agentRepository.countByStatus(AgentStatus.INACTIVE)).thenReturn(4L);

    // -- ACT --
    Map<String, Object> built = snapshot.build();

    // -- ASSERT --
    assertThat(built).containsEntry("schema_version", 1);
    assertThat(built).containsEntry("agents", Map.of("total", 10L, "offline", 4L));
    assertThat(built).containsEntry("healthchecks", List.of());
    assertThat(built).containsEntry("integrations", List.of());
  }

  @Test
  @DisplayName("Given a read that fails should send no snapshot rather than block the registration")
  void given_failingRead_should_sendNoSnapshot() {
    // -- ARRANGE --
    when(smtpService.isServiceAvailable()).thenReturn(true);
    when(imapService.isServiceAvailable()).thenReturn(true);
    when(injectorRepository.findAll()).thenThrow(new IllegalStateException("database down"));

    // -- ACT --
    Map<String, Object> built = snapshot.build();

    // -- ASSERT --
    assertThat(built).isNull();
  }
}
