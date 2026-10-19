package io.openaev.service.chaining;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("The workflow update event publisher notifies the steps and keeps refused events")
class WorkflowUpdateEventPublisherTest {

  private static final String TENANT_A = "tenant-a";

  @Mock private QueueChainingService queueChainingService;
  @Mock private StepService stepService;

  @InjectMocks private WorkflowUpdateEventPublisher publisher;

  private static ExternalUpdateEvent eventFor(String stepId, String tenantId) {
    ExternalUpdateEvent event = new ExternalUpdateEvent();
    event.setStepId(stepId);
    event.setTenantId(tenantId);
    return event;
  }

  private static Set<String> ordered(String... ids) {
    return new LinkedHashSet<>(List.of(ids));
  }

  @Nested
  @DisplayName("Publication")
  class Publication {

    @Test
    @DisplayName("given a blank inject id, should look no step up and send nothing")
    void given_blankInjectId_should_lookNoStepUpAndSendNothing() {
      // -- Act --
      publisher.publishForInject(" ");

      // -- Assert --
      verifyNoInteractions(stepService, queueChainingService);
    }

    @Test
    @DisplayName("given an inject with no active step, should send nothing")
    void given_injectWithNoActiveStep_should_sendNothing() {
      // -- Arrange --
      when(stepService.findStepIdActiveByInjectId("inject-1")).thenReturn(Optional.empty());

      // -- Act --
      publisher.publishForInject("inject-1");

      // -- Assert --
      verifyNoInteractions(queueChainingService);
    }

    @Test
    @DisplayName("given injects with active steps, should notify each step")
    void given_injectsWithActiveSteps_should_notifyEachStep() throws IOException {
      // -- Arrange --
      when(stepService.findStepIdsActiveByInjectIds(Set.of("inject-1", "inject-2")))
          .thenReturn(Set.of("step-1", "step-2"));

      // -- Act --
      publisher.publishForInjects(Set.of("inject-1", "inject-2"));

      // -- Assert --
      verify(queueChainingService).updateStep("step-1");
      verify(queueChainingService).updateStep("step-2");
      verify(queueChainingService, never()).republishUpdateEvent(any());
    }

    @Test
    @DisplayName("given expectations with active steps, should notify each step")
    void given_expectationsWithActiveSteps_should_notifyEachStep() throws IOException {
      // -- Arrange --
      when(stepService.findStepIdsActiveByExpectationIds(Set.of("exp-1")))
          .thenReturn(Set.of("step-1"));

      // -- Act --
      publisher.publishForExpectations(Set.of("exp-1"));

      // -- Assert --
      verify(queueChainingService).updateStep("step-1");
    }
  }

  @Nested
  @DisplayName("Unsent events")
  class UnsentEvents {

    @BeforeEach
    void buildEventsWithTheirStepTenant() {
      lenient()
          .when(queueChainingService.newUpdateEvent(any()))
          .thenAnswer(invocation -> eventFor(invocation.getArgument(0), TENANT_A));
    }

    @Test
    @DisplayName(
        "given the queue refusing a send, should keep it and the remaining steps without trying"
            + " them")
    void given_queueRefusingASend_should_keepItAndTheRemainingWithoutTryingThem()
        throws IOException {
      // -- Arrange --
      when(stepService.findStepIdsActiveByInjectIds(Set.of("inject-1")))
          .thenReturn(ordered("step-1", "step-2", "step-3"));
      doNothing()
          .doThrow(new IOException("queue down"))
          .when(queueChainingService)
          .updateStep(any());
      doThrow(new IOException("queue down")).when(queueChainingService).republishUpdateEvent(any());

      // -- Act --
      publisher.publishForInjects(Set.of("inject-1"));

      // -- Assert --
      verify(queueChainingService).updateStep("step-1");
      verify(queueChainingService).updateStep("step-2");
      verify(queueChainingService, never()).updateStep("step-3");
      verify(queueChainingService, never()).newUpdateEvent("step-1");
      verify(queueChainingService).newUpdateEvent("step-2");
      verify(queueChainingService).newUpdateEvent("step-3");
    }

    @Test
    @DisplayName(
        "given a kept event, should resend it as built after a later successful send rather than"
            + " drop it")
    void given_keptEvent_should_resendItAsBuiltAfterALaterSuccessfulSend() throws IOException {
      // -- Arrange --
      when(stepService.findStepIdActiveByInjectId("inject-a")).thenReturn(Optional.of("step-a"));
      when(stepService.findStepIdActiveByInjectId("inject-b")).thenReturn(Optional.of("step-b"));
      doThrow(new IOException("queue down")).when(queueChainingService).updateStep("step-a");
      doThrow(new IOException("queue down"))
          .doNothing()
          .when(queueChainingService)
          .republishUpdateEvent(any());
      publisher.publishForInject("inject-a");

      // -- Act --
      publisher.publishForInject("inject-b");

      // -- Assert --
      InOrder order = inOrder(queueChainingService);
      order.verify(queueChainingService).updateStep("step-b");
      ArgumentCaptor<ExternalUpdateEvent> resent =
          ArgumentCaptor.forClass(ExternalUpdateEvent.class);
      order.verify(queueChainingService).republishUpdateEvent(resent.capture());
      assertThat(resent.getValue().getStepId()).isEqualTo("step-a");
      assertThat(resent.getValue().getTenantId()).isEqualTo(TENANT_A);
      // Built once, when refused: the resend reads nothing in the scope it runs in.
      verify(queueChainingService, times(1)).newUpdateEvent("step-a");
    }

    @Test
    @DisplayName("given a kept event resent once, should not send it again")
    void given_keptEventResent_should_notSendItAgain() throws IOException {
      // -- Arrange --
      when(stepService.findStepIdActiveByInjectId("inject-a")).thenReturn(Optional.of("step-a"));
      doThrow(new IOException("queue down")).when(queueChainingService).updateStep("step-a");
      publisher.publishForInject("inject-a");

      // -- Act --
      publisher.publishForInject(null);

      // -- Assert --
      // Resent right after the refusal, once, and never again.
      verify(queueChainingService, times(1)).republishUpdateEvent(any());
    }

    @Test
    @DisplayName("given a kept step sent again fresh, should drop the kept event")
    void given_keptStepSentAgainFresh_should_dropTheKeptEvent() throws IOException {
      // -- Arrange --
      when(stepService.findStepIdActiveByInjectId("inject-a")).thenReturn(Optional.of("step-a"));
      doThrow(new IOException("queue down"))
          .doNothing()
          .when(queueChainingService)
          .updateStep("step-a");
      doThrow(new IOException("queue down")).when(queueChainingService).republishUpdateEvent(any());
      publisher.publishForInject("inject-a");

      // -- Act --
      publisher.publishForInject("inject-a");

      // -- Assert --
      // One resend, right after the refusal; the fresh send then supersedes the kept event.
      verify(queueChainingService, times(2)).updateStep("step-a");
      verify(queueChainingService, times(1)).republishUpdateEvent(any());
    }
  }
}
