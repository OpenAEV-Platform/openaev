package io.openaev.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.database.model.Execution;
import io.openaev.database.model.ExecutionTrace;
import io.openaev.database.model.ExecutionTraceAction;
import io.openaev.database.model.Inject;
import io.openaev.database.model.Injector;
import io.openaev.database.model.InjectorContract;
import io.openaev.database.model.InjectorContractId;
import io.openaev.database.model.User;
import io.openaev.database.repository.InjectorContractRepository;
import io.openaev.database.repository.UserRepository;
import io.openaev.execution.ExecutionContext;
import io.openaev.execution.ExecutionContextService;
import io.openaev.injectors.email.EmailContract;
import io.openaev.integration.Manager;
import io.openaev.integration.ManagerFactory;
import io.openaev.telemetry.metric_collectors.ResultsMetricCollector;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * The email injector never propagates: {@code Injector.execute()} funnels every failure into
 * execution traces. These tests pin the contract that {@link MailingService} inspects that result
 * instead of discarding it, so a failed delivery cannot masquerade as a success.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MailingServiceTest {

  private static final String TENANT_ID = "tenant-1";
  private static final String INJECTOR_TYPE = "openaev_email";

  @Mock private UserRepository userRepository;
  @Mock private ResultsMetricCollector resultsMetricCollector;
  @Mock private InjectorContractRepository injectorContractRepository;
  @Mock private ExecutionContextService executionContextService;
  @Mock private ManagerFactory managerFactory;

  @InjectMocks private MailingService mailingService;

  private io.openaev.executors.Injector executor;

  @BeforeEach
  void setUp() {
    // MailingService uses constructor injection for its dependencies, so Mockito skips field
    // injection: the @Resource-annotated mapper has to be set explicitly.
    ReflectionTestUtils.setField(mailingService, "mapper", new ObjectMapper());

    Injector injector = new Injector();
    injector.setId("injector-1");
    injector.setType(INJECTOR_TYPE);
    InjectorContract contract = new InjectorContract();
    contract.setId(EmailContract.EMAIL_DEFAULT);
    contract.addInjector(injector);

    when(injectorContractRepository.findById(any(InjectorContractId.class)))
        .thenReturn(Optional.of(contract));
    when(executionContextService.executionContext(
            any(User.class), any(Inject.class), any(String.class)))
        .thenReturn(mock(ExecutionContext.class));

    executor = mock(io.openaev.executors.Injector.class);
    Manager manager = mock(Manager.class);
    when(manager.requestInjectorExecutorByType(INJECTOR_TYPE)).thenReturn(executor);
    when(managerFactory.getManager(TENANT_ID)).thenReturn(manager);
  }

  private static Execution executionWith(ExecutionTrace... traces) {
    Execution execution = new Execution(true);
    for (ExecutionTrace trace : traces) {
      execution.addTrace(trace);
    }
    execution.stop();
    return execution;
  }

  private void sendEmail() {
    User recipient = new User();
    recipient.setId("user-1");
    recipient.setEmail("player@example.com");
    mailingService.sendEmail("subject", "body", List.of(recipient), TENANT_ID);
  }

  @Nested
  @DisplayName("When the injector reports a failed delivery")
  class FailedDelivery {

    @Test
    @DisplayName("given only error traces, should raise with the underlying cause")
    void given_onlyErrorTraces_should_raiseWithUnderlyingCause() {
      // -- PREPARE --
      when(executor.executeInjection(any()))
          .thenReturn(
              executionWith(
                  ExecutionTrace.getNewErrorTrace(
                      "SMTP connection refused", ExecutionTraceAction.COMPLETE)));

      // -- EXECUTE & ASSERT --
      assertThatThrownBy(MailingServiceTest.this::sendEmail)
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("SMTP connection refused")
          .hasMessageContaining(EmailContract.EMAIL_DEFAULT);
    }

    @Test
    @DisplayName("given a partial delivery, should still raise so the failure is not lost")
    void given_partialDelivery_should_stillRaise() {
      // -- PREPARE --
      when(executor.executeInjection(any()))
          .thenReturn(
              executionWith(
                  ExecutionTrace.getNewSuccessTrace("sent to a", ExecutionTraceAction.COMPLETE),
                  ExecutionTrace.getNewErrorTrace(
                      "mailbox unavailable for b", ExecutionTraceAction.COMPLETE)));

      // -- EXECUTE & ASSERT --
      assertThatThrownBy(MailingServiceTest.this::sendEmail)
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("mailbox unavailable for b");
    }
  }

  @Nested
  @DisplayName("When the injector reports a successful delivery")
  class SuccessfulDelivery {

    @Test
    @DisplayName("given success traces, should not raise")
    void given_successTraces_should_notRaise() {
      // -- PREPARE --
      when(executor.executeInjection(any()))
          .thenReturn(
              executionWith(
                  ExecutionTrace.getNewSuccessTrace("sent", ExecutionTraceAction.COMPLETE)));

      // -- EXECUTE & ASSERT --
      assertThatCode(MailingServiceTest.this::sendEmail).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("given no trace at all, should not raise")
    void given_noTrace_should_notRaise() {
      // -- PREPARE --
      when(executor.executeInjection(any())).thenReturn(executionWith());

      // -- EXECUTE --
      assertThatCode(MailingServiceTest.this::sendEmail).doesNotThrowAnyException();

      // -- ASSERT --
      assertThat(executionWith().getTraces()).isEmpty();
    }
  }
}
