package io.openaev.rest.injector;

import static io.openaev.config.TenantUriUtils.TENANT_BASE_PATH;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import io.openaev.IntegrationTest;
import io.openaev.aop.audit_log.AuditEvent;
import io.openaev.aop.audit_log.AuditLogger;
import io.openaev.config.ProductionActiveTables;
import io.openaev.config.ShutdownService;
import io.openaev.context.TenantContext;
import io.openaev.context.TenantScopedTransaction;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Tenant;
import io.openaev.rest.inject.service.InjectStatusService;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.AgentFixture;
import io.openaev.utils.fixtures.DomainFixture;
import io.openaev.utils.fixtures.EndpointFixture;
import io.openaev.utils.fixtures.InjectFixture;
import io.openaev.utils.fixtures.InjectStatusFixture;
import io.openaev.utils.fixtures.InjectorContractFixture;
import io.openaev.utils.fixtures.InjectorFixture;
import io.openaev.utils.fixtures.PayloadFixture;
import io.openaev.utils.fixtures.composers.AgentComposer;
import io.openaev.utils.fixtures.composers.DomainComposer;
import io.openaev.utils.fixtures.composers.EndpointComposer;
import io.openaev.utils.fixtures.composers.InjectComposer;
import io.openaev.utils.fixtures.composers.InjectStatusComposer;
import io.openaev.utils.fixtures.composers.InjectorContractComposer;
import io.openaev.utils.fixtures.composers.PayloadComposer;
import io.openaev.utils.fixtures.tenants.TenantFixture;
import io.openaev.utilstest.KeepRabbit;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * An implant download for a platform or an architecture the implant does not support answers 400
 * and writes nothing: no execution trace, no inject status change and no audit event, whichever ids
 * the request carries.
 *
 * <p>The tests are not {@code @Transactional}: the rows are committed in their own tenant and read
 * back with plain SQL, so the assertions see every write the request commits or emits, whatever the
 * outcome of the request's own transaction.
 */
@KeepRabbit
@TestPropertySource(properties = {"openaev.audit-logs.transports=console"})
@DisplayName("An implant download for an unsupported platform writes nothing")
class ImplantDownloadUnsupportedPlatformTest extends IntegrationTest {

  private enum Route {
    /** {@code /api/tenants/{tenantId}/implant/openaev/...}: the path names the tenant. */
    TENANT,
    /** {@code /api/implant/openaev/...}: no path tenant. */
    PLAIN
  }

  private enum Caller {
    AGENT_TARGETING_THE_INJECT,
    AGENT_NOT_TARGETING_THE_INJECT
  }

  private static final String UNSUPPORTED_CALLS =
      "io.openaev.rest.injector.ImplantDownloadUnsupportedPlatformTest#unsupportedCalls";

  static Stream<Arguments> unsupportedCalls() {
    return Stream.of(Route.values())
        .flatMap(
            route ->
                Stream.of(Caller.values())
                    .flatMap(
                        caller ->
                            Stream.of(
                                Arguments.of(route, caller, "solaris", "x64"),
                                Arguments.of(route, caller, "linux", "sparc"))));
  }

  private record Seeded(String injectId, String targetingAgentId, String otherAgentId) {}

  private record State(String statusName, List<String> traces) {}

  @DynamicPropertySource
  static void productionActiveTables(DynamicPropertyRegistry registry) {
    registry.add(
        "openaev.tenant.active-tables", () -> String.join(",", ProductionActiveTables.names()));
  }

  @Autowired private MockMvc mvc;
  @Autowired private TenantIsolationTestHelper tenantHelper;
  @Autowired private TenantScopedTransaction tenantTx;
  @Autowired private InjectStatusService injectStatusService;
  @Autowired private InjectComposer injectComposer;
  @Autowired private InjectStatusComposer injectStatusComposer;
  @Autowired private InjectorContractComposer injectorContractComposer;
  @Autowired private EndpointComposer endpointComposer;
  @Autowired private AgentComposer agentComposer;
  @Autowired private DomainComposer domainComposer;
  @Autowired private PayloadComposer payloadComposer;

  @MockitoSpyBean private AuditLogger auditLogger;
  @MockitoSpyBean private ShutdownService shutdownService;

  private String tenant;

  @BeforeEach
  void setUp() {
    tenant = tenantRepository.save(TenantFixture.getTenant("implant-" + UUID.randomUUID())).getId();
    // The events are observed on the spy and not shipped: nothing is sent to a transport.
    doNothing().when(auditLogger).logEvent(any());
    doNothing().when(shutdownService).initiateShutdown();
  }

  @AfterEach
  void tearDown() {
    TenantContext.clearCurrentTenant();
    SecurityContextHolder.clearContext();
    tenantHelper.deleteCommittedTenants(tenant);
  }

  @Nested
  @DisplayName("Unsupported platform or architecture")
  class Unsupported {

    @ParameterizedTest(name = "{0}, {1}, {2}/{3}: answers 400, no trace, no status, no audit event")
    @MethodSource(UNSUPPORTED_CALLS)
    @DisplayName("Answers 400 and writes nothing")
    void given_unsupportedPlatformOrArchitecture_should_answer400AndWriteNothing(
        Route route, Caller caller, String platform, String architecture) throws Exception {
      // Arrange
      Seeded seeded = inTenant(tenant, ImplantDownloadUnsupportedPlatformTest.this::seed);
      String agentId =
          caller == Caller.AGENT_TARGETING_THE_INJECT
              ? seeded.targetingAgentId()
              : seeded.otherAgentId();
      State before = readState(seeded.injectId());
      assertThat(before.statusName()).isEqualTo("PENDING");
      assertThat(before.traces()).isEmpty();
      reset(auditLogger);
      doNothing().when(auditLogger).logEvent(any());

      // Act
      int httpStatus =
          download(route, platform, architecture, seeded.injectId(), agentId).getStatus();

      // Assert
      assertThat(httpStatus).isEqualTo(400);
      assertThat(readState(seeded.injectId())).isEqualTo(before);
      verify(auditLogger, never()).logEvent(any(AuditEvent.class));
    }
  }

  @Nested
  @DisplayName("Observation controls")
  class Controls {

    @Test
    @DisplayName("A committed error trace changes the state and reaches the audit logger")
    void given_committedErrorTrace_should_changeTheStateAndFireTheAuditLogger() {
      // Arrange
      Seeded seeded = inTenant(tenant, ImplantDownloadUnsupportedPlatformTest.this::seed);
      State before = readState(seeded.injectId());
      reset(auditLogger);
      doNothing().when(auditLogger).logEvent(any());

      // Act
      inTenant(
          tenant,
          () -> {
            injectStatusService.setImplantErrorTrace(
                seeded.injectId(), seeded.targetingAgentId(), "control");
            return null;
          });

      // Assert
      State after = readState(seeded.injectId());
      assertThat(after.traces()).hasSize(before.traces().size() + 2);
      assertThat(after.statusName()).isNotEqualTo(before.statusName());
      verify(auditLogger, atLeastOnce()).logEvent(any(AuditEvent.class));
    }
  }

  @Nested
  @DisplayName("Supported platform")
  class Supported {

    @ParameterizedTest(name = "{0}: answers 200 without inject or agent ids")
    @EnumSource(Route.class)
    @DisplayName("Serves the implant")
    void given_supportedPlatformWithoutIds_should_serveTheImplant(Route route) throws Exception {
      // Arrange
      reset(auditLogger);
      doNothing().when(auditLogger).logEvent(any());

      // Act
      MockHttpServletResponse response = download(route, "linux", "x86_64", null, null);

      // Assert
      assertThat(response.getStatus()).isEqualTo(200);
      assertThat(response.getContentAsByteArray()).isNotEmpty();
    }
  }

  // -- helpers --

  private MockHttpServletResponse download(
      Route route, String platform, String architecture, String injectId, String agentId)
      throws Exception {
    // The platform's download command carries no credentials and runs without a tenant on the
    // thread: the call carries no authentication, as in production.
    TenantContext.clearCurrentTenant();
    SecurityContextHolder.clearContext();
    assertThat(TenantContext.getCurrentTenant()).isEqualTo(Tenant.DEFAULT_TENANT_UUID);
    String base =
        route == Route.TENANT
            ? TENANT_BASE_PATH + tenant + "/implant/openaev"
            : "/api/implant/openaev";
    var request = get("%s/%s/%s".formatted(base, platform, architecture));
    if (injectId != null) {
      request.queryParam("injectId", injectId);
    }
    if (agentId != null) {
      request.queryParam("agentId", agentId);
    }
    return mvc.perform(request).andReturn().getResponse();
  }

  @SuppressWarnings("unchecked")
  private State readState(String injectId) {
    return tenantTx.execute(
        TxCtx.forTenant(tenant),
        () -> {
          Object status =
              entityManager
                  .createNativeQuery(
                      "SELECT status_name FROM injects_statuses WHERE status_inject = :id")
                  .setParameter("id", injectId)
                  .getSingleResult();
          List<Object[]> rows =
              entityManager
                  .createNativeQuery(
                      "SELECT t.execution_action, t.execution_status, t.execution_agent_id,"
                          + " t.execution_message FROM execution_traces t JOIN injects_statuses s"
                          + " ON t.execution_inject_status_id = s.status_id"
                          + " WHERE s.status_inject = :id ORDER BY t.execution_time")
                  .setParameter("id", injectId)
                  .getResultList();
          List<String> traces =
              rows.stream().map(r -> r[0] + "/" + r[1] + "/" + r[2] + "/" + r[3]).sorted().toList();
          return new State(String.valueOf(status), traces);
        });
  }

  private Seeded seed() {
    DomainComposer.Composer domain = domainComposer.forDomain(DomainFixture.getRandomDomain());
    domain.get().setTenant(new Tenant(TenantContext.getCurrentTenant()));
    AgentComposer.Composer targetingAgent =
        agentComposer.forAgent(AgentFixture.createDefaultAgentService());
    EndpointComposer.Composer targetedEndpoint =
        endpointComposer.forEndpoint(EndpointFixture.createEndpoint()).withAgent(targetingAgent);
    InjectComposer.Composer inject =
        injectComposer
            .forInject(InjectFixture.getDefaultInject())
            .withInjectStatus(
                injectStatusComposer.forInjectStatus(
                    InjectStatusFixture.createPendingInjectStatus()))
            .withEndpoint(targetedEndpoint)
            .withInjectorContract(
                injectorContractComposer
                    .forInjectorContract(InjectorContractFixture.createDefaultInjectorContract())
                    .withDomain(domain)
                    .withInjector(InjectorFixture.createDefaultPayloadInjector())
                    .withPayload(payloadComposer.forPayload(PayloadFixture.createDefaultCommand())))
            .persist();
    AgentComposer.Composer otherAgent =
        agentComposer.forAgent(AgentFixture.createDefaultAgentService());
    endpointComposer.forEndpoint(EndpointFixture.createEndpoint()).withAgent(otherAgent).persist();
    return new Seeded(inject.get().getId(), targetingAgent.get().getId(), otherAgent.get().getId());
  }

  private <T> T inTenant(String tenantId, Supplier<T> work) {
    TenantContext.setCurrentTenant(tenantId);
    try {
      return tenantTx.execute(TxCtx.forTenant(tenantId), work);
    } finally {
      TenantContext.clearCurrentTenant();
    }
  }
}
