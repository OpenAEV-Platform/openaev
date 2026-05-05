package io.openaev.aop;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import io.openaev.rest.exception.ElementNotFoundException;
import io.openaev.rest.settings.PreviewFeature;
import io.openaev.service.PreviewFeatureService;
import io.openaev.service.chaining.QueueChainingService;
import io.openaev.service.chaining.StepService;
import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.Signature;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WorkflowUpdateEventAspectTest {

  @Mock private PreviewFeatureService previewFeatureService;
  @Mock private QueueChainingService queueChainingService;
  @Mock private StepService stepService;

  @Mock private JoinPoint joinPoint;
  @Mock private MethodSignature methodSignature;
  @Mock private WorkflowUpdateEvent annotation;

  @InjectMocks private WorkflowUpdateEventAspect aspect;

  @Captor private ArgumentCaptor<String> stepIdCaptor;

  /* ============================================================
   * Feature flag disabled
   * ============================================================ */
  @Nested
  class FeatureFlagDisabled {

    @Test
    void shouldDoNothing_whenInjectChainingFeatureIsDisabled() {
      // -------- Prepare --------
      when(previewFeatureService.isFeatureEnabled(PreviewFeature.INJECT_CHAINING))
          .thenReturn(false);

      // -------- Act --------
      aspect.afterEventProcessed(joinPoint, annotation);

      // -------- Assert --------
      verifyNoInteractions(queueChainingService, stepService);
    }
  }

  /* ============================================================
   * Invalid annotation configuration
   * ============================================================ */
  @Nested
  class InvalidAnnotationConfiguration {

    @BeforeEach
    void setUp() {
      when(previewFeatureService.isFeatureEnabled(PreviewFeature.INJECT_CHAINING)).thenReturn(true);
    }

    @Test
    void shouldThrow_whenBothInjectIdAndExpectationIdsAreSet() {
      // -------- Prepare --------
      when(annotation.injectId()).thenReturn("#injectId");
      when(annotation.expectationIds()).thenReturn("#expectationIds");

      Signature signature = mock(Signature.class);
      when(joinPoint.getSignature()).thenReturn(signature);
      when(signature.toShortString()).thenReturn("TestMethod()");

      // -------- Act + Assert --------
      IllegalStateException ex =
          assertThrows(
              IllegalStateException.class, () -> aspect.afterEventProcessed(joinPoint, annotation));

      assertTrue(
          ex.getMessage()
              .contains("must set exactly one of injectId, injectIds or expectationIds"));
      verifyNoInteractions(queueChainingService, stepService);
    }

    @Test
    void shouldThrow_whenNeitherInjectIdNorExpectationIdsAreSet() {
      // -------- Prepare --------
      when(annotation.injectId()).thenReturn("");
      when(annotation.expectationIds()).thenReturn("");

      Signature signature = mock(Signature.class);
      when(joinPoint.getSignature()).thenReturn(signature);
      when(signature.toShortString()).thenReturn("TestMethod()");

      // -------- Act + Assert --------
      IllegalStateException ex =
          assertThrows(
              IllegalStateException.class, () -> aspect.afterEventProcessed(joinPoint, annotation));

      assertTrue(
          ex.getMessage()
              .contains("must set exactly one of injectId, injectIds or expectationIds"));
      verifyNoInteractions(queueChainingService, stepService);
    }
  }

  /* ============================================================
   * InjectId path
   * ============================================================ */
  @Nested
  class InjectIdPath {

    @BeforeEach
    void setUp() {
      when(previewFeatureService.isFeatureEnabled(PreviewFeature.INJECT_CHAINING)).thenReturn(true);
      when(annotation.injectId()).thenReturn("#injectId");
      when(annotation.expectationIds()).thenReturn("");
    }

    private void setupJoinPoint(String injectIdValue) {
      when(joinPoint.getSignature()).thenReturn(methodSignature);
      when(methodSignature.getParameterNames()).thenReturn(new String[] {"injectId"});
      when(joinPoint.getArgs()).thenReturn(new Object[] {injectIdValue});
    }

    @ParameterizedTest(name = "{index} => injectIdValue={0}")
    @MethodSource("emptyInjectIdScenarios")
    void shouldDoNothing_whenInjectIdIsEmptyOrNull(String injectIdValue) {
      // -------- Prepare --------
      setupJoinPoint(injectIdValue);

      // -------- Act --------
      aspect.afterEventProcessed(joinPoint, annotation);

      // -------- Assert --------
      verifyNoInteractions(queueChainingService);
      verify(stepService, never()).findStepIdByInjectId(any());
    }

    static Stream<Arguments> emptyInjectIdScenarios() {
      return Stream.of(Arguments.of((String) null), Arguments.of(""));
    }

    @Test
    void shouldDoNothing_whenNoStepFoundForInjectId() {
      // -------- Prepare --------
      String injectId = "inject-123";
      setupJoinPoint(injectId);
      when(stepService.findStepIdByInjectId(injectId))
          .thenThrow(new ElementNotFoundException("Step id not found for inject id : " + injectId));
      // -------- Act --------
      assertThrows(
          ElementNotFoundException.class, () -> aspect.afterEventProcessed(joinPoint, annotation));

      // -------- Assert --------
      verify(stepService).findStepIdByInjectId(injectId);
      verifyNoInteractions(queueChainingService);
    }

    @Test
    void shouldCallUpdateStep_whenStepFoundForInjectId() throws IOException {
      // -------- Prepare --------
      String injectId = "inject-success";
      String stepId = "step-success";
      setupJoinPoint(injectId);

      when(stepService.findStepIdByInjectId(injectId)).thenReturn(stepId);

      // -------- Act --------
      aspect.afterEventProcessed(joinPoint, annotation);

      // -------- Assert --------
      verify(stepService).findStepIdByInjectId(injectId);
      verify(queueChainingService).updateStep(stepIdCaptor.capture());
      assertEquals(stepId, stepIdCaptor.getValue());
    }

    @Test
    void shouldCacheStepId_whenIOExceptionOccurs() throws IOException {
      // -------- Prepare --------
      String injectId = "inject-cache";
      String stepId = "step-cache";
      setupJoinPoint(injectId);

      when(stepService.findStepIdByInjectId(injectId)).thenReturn(stepId);

      IOException ioException = new IOException("Queue error");
      doThrow(ioException).when(queueChainingService).updateStep(stepId);

      // -------- Act --------
      // Should NOT throw - instead caches the stepId for retry
      assertDoesNotThrow(() -> aspect.afterEventProcessed(joinPoint, annotation));

      // -------- Assert --------
      // Called twice: once in handleInjectIdParam (fails), then immediately retried in sendEvents
      verify(queueChainingService, times(2)).updateStep(stepId);
    }
  }

  /* ============================================================
   * InjectIds path
   * ============================================================ */
  @Nested
  class InjectIdsPath {

    @BeforeEach
    void setUp() {
      when(previewFeatureService.isFeatureEnabled(PreviewFeature.INJECT_CHAINING)).thenReturn(true);
      when(annotation.injectId()).thenReturn("");
      when(annotation.injectIds()).thenReturn("#injectIds");
      when(annotation.expectationIds()).thenReturn("");
    }

    private void setupJoinPoint(Object injectIdsValue) {
      when(joinPoint.getSignature()).thenReturn(methodSignature);
      when(methodSignature.getParameterNames()).thenReturn(new String[] {"injectIds"});
      when(joinPoint.getArgs()).thenReturn(new Object[] {injectIdsValue});
    }

    @Test
    void shouldProcessCollection_whenInjectIdsIsCollection() throws IOException {
      // -------- Prepare --------
      List<String> injectIds = List.of("inject-1", "inject-2", "inject-3");
      setupJoinPoint(injectIds);

      Set<String> stepIds = Set.of("step-1", "step-2");
      when(stepService.findStepIdsByInjectIds(Set.of("inject-1", "inject-2", "inject-3")))
          .thenReturn(stepIds);

      // -------- Act --------
      aspect.afterEventProcessed(joinPoint, annotation);

      // -------- Assert --------
      verify(stepService).findStepIdsByInjectIds(Set.of("inject-1", "inject-2", "inject-3"));
      verify(queueChainingService, times(2)).updateStep(stepIdCaptor.capture());
      assertTrue(stepIdCaptor.getAllValues().containsAll(stepIds));
    }

    @Test
    void shouldProcessSingleString_whenInjectIdsIsString() throws IOException {
      // -------- Prepare --------
      String injectId = "inject-single";
      setupJoinPoint(injectId);

      Set<String> stepIds = Set.of("step-1");
      when(stepService.findStepIdsByInjectIds(Set.of(injectId))).thenReturn(stepIds);

      // -------- Act --------
      aspect.afterEventProcessed(joinPoint, annotation);

      // -------- Assert --------
      verify(stepService).findStepIdsByInjectIds(Set.of(injectId));
      verify(queueChainingService).updateStep("step-1");
    }

    @Test
    void shouldThrow_whenInjectIdsIsNeitherCollectionNorString() {
      // -------- Prepare --------
      Integer invalidValue = 12345;
      setupJoinPoint(invalidValue);

      // -------- Act + Assert --------
      IllegalStateException ex =
          assertThrows(
              IllegalStateException.class, () -> aspect.afterEventProcessed(joinPoint, annotation));

      assertTrue(ex.getMessage().contains("must return a Collection or a String"));
      verifyNoInteractions(queueChainingService);
      verify(stepService, never()).findStepIdsByInjectIds(any());
    }

    @Test
    void shouldCacheRemainingStepIds_whenIOExceptionOccurs() throws IOException {
      // -------- Prepare --------
      Set<String> injectIds = new LinkedHashSet<>();
      injectIds.add("inject-1");
      injectIds.add("inject-2");
      setupJoinPoint(injectIds);

      Set<String> stepIds = new LinkedHashSet<>();
      stepIds.add("step-1");
      stepIds.add("step-2");
      stepIds.add("step-3");
      when(stepService.findStepIdsByInjectIds(Set.of("inject-1", "inject-2"))).thenReturn(stepIds);

      doThrow(new IOException("Queue error")).when(queueChainingService).updateStep(any());

      // -------- Act --------
      assertDoesNotThrow(() -> aspect.afterEventProcessed(joinPoint, annotation));

      // -------- Assert --------
      verify(queueChainingService, atLeastOnce()).updateStep(any());
    }
  }

  /* ============================================================
   * ExpectationIds path
   * ============================================================ */
  @Nested
  class ExpectationIdsPath {

    @BeforeEach
    void setUp() {
      when(previewFeatureService.isFeatureEnabled(PreviewFeature.INJECT_CHAINING)).thenReturn(true);
      when(annotation.injectId()).thenReturn("");
      when(annotation.expectationIds()).thenReturn("#expectationIds");
    }

    private void setupJoinPoint(Object expectationIdsValue) {
      when(joinPoint.getSignature()).thenReturn(methodSignature);
      when(methodSignature.getParameterNames()).thenReturn(new String[] {"expectationIds"});
      when(joinPoint.getArgs()).thenReturn(new Object[] {expectationIdsValue});
    }

    @Test
    void shouldProcessCollection_whenExpectationIdsIsCollection() throws IOException {
      // -------- Prepare --------
      List<String> expectationIds = List.of("exp-1", "exp-2", "exp-3");
      setupJoinPoint(expectationIds);

      Set<String> stepIds = Set.of("step-1", "step-2");
      when(stepService.findStepIdsByExpectationIds(Set.of("exp-1", "exp-2", "exp-3")))
          .thenReturn(stepIds);

      // -------- Act --------
      aspect.afterEventProcessed(joinPoint, annotation);

      // -------- Assert --------
      verify(stepService).findStepIdsByExpectationIds(Set.of("exp-1", "exp-2", "exp-3"));
      verify(queueChainingService, times(2)).updateStep(stepIdCaptor.capture());
      assertTrue(stepIdCaptor.getAllValues().containsAll(stepIds));
    }

    @Test
    void shouldProcessSingleString_whenExpectationIdsIsString() throws IOException {
      // -------- Prepare --------
      String expectationId = "exp-single";
      setupJoinPoint(expectationId);

      Set<String> stepIds = Set.of("step-1");
      when(stepService.findStepIdsByExpectationIds(Set.of(expectationId))).thenReturn(stepIds);

      // -------- Act --------
      aspect.afterEventProcessed(joinPoint, annotation);

      // -------- Assert --------
      verify(stepService).findStepIdsByExpectationIds(Set.of(expectationId));
      verify(queueChainingService).updateStep("step-1");
    }

    @Test
    void shouldThrow_whenExpectationIdsIsNeitherCollectionNorString() {
      // -------- Prepare --------
      Integer invalidValue = 12345;
      setupJoinPoint(invalidValue);

      // -------- Act + Assert --------
      IllegalStateException ex =
          assertThrows(
              IllegalStateException.class, () -> aspect.afterEventProcessed(joinPoint, annotation));

      assertTrue(ex.getMessage().contains("must return a Collection or a String"));
      verifyNoInteractions(queueChainingService);
    }

    @Test
    void shouldDoNothing_whenNoStepsFound() {
      // -------- Prepare --------
      String expectationId = "exp-no-steps";
      setupJoinPoint(expectationId);

      when(stepService.findStepIdsByExpectationIds(Set.of(expectationId))).thenReturn(Set.of());

      // -------- Act --------
      aspect.afterEventProcessed(joinPoint, annotation);

      // -------- Assert --------
      verify(stepService).findStepIdsByExpectationIds(Set.of(expectationId));
      verifyNoInteractions(queueChainingService);
    }

    @Test
    void shouldCacheRemainingStepIds_whenIOExceptionOccurs() throws IOException {
      // -------- Prepare --------
      String expectationId = "exp-1";
      setupJoinPoint(expectationId);

      // Use LinkedHashSet to ensure predictable iteration order
      Set<String> stepIds = new LinkedHashSet<>();
      stepIds.add("step-1");
      stepIds.add("step-2");
      stepIds.add("step-3");
      when(stepService.findStepIdsByExpectationIds(Set.of(expectationId))).thenReturn(stepIds);

      // Fail on the first call
      IOException ioException = new IOException("Queue error");
      doThrow(ioException).when(queueChainingService).updateStep(any());

      // -------- Act --------
      // Should NOT throw - instead caches remaining stepIds
      assertDoesNotThrow(() -> aspect.afterEventProcessed(joinPoint, annotation));

      // -------- Assert --------
      // At least one attempt was made
      verify(queueChainingService, atLeastOnce()).updateStep(any());
    }

    @Test
    void shouldCallUpdateStepForEachStep_whenMultipleStepsFound() throws IOException {
      // -------- Prepare --------
      Set<String> expectationIds = Set.of("exp-1", "exp-2");
      setupJoinPoint(expectationIds);

      Set<String> stepIds = Set.of("step-1", "step-2", "step-3");
      when(stepService.findStepIdsByExpectationIds(expectationIds)).thenReturn(stepIds);

      // -------- Act --------
      aspect.afterEventProcessed(joinPoint, annotation);

      // -------- Assert --------
      verify(stepService).findStepIdsByExpectationIds(expectationIds);
      verify(queueChainingService, times(3)).updateStep(stepIdCaptor.capture());
      assertTrue(stepIdCaptor.getAllValues().containsAll(stepIds));
    }
  }

  /* ============================================================
   * Unsent events cache and retry mechanism
   * ============================================================ */
  @Nested
  class UnsentEventsCacheAndRetry {

    @BeforeEach
    void setUp() {
      when(previewFeatureService.isFeatureEnabled(PreviewFeature.INJECT_CHAINING)).thenReturn(true);
    }

    private void setupInjectIdJoinPoint(String injectIdValue) {
      when(annotation.injectId()).thenReturn("#injectId");
      when(annotation.expectationIds()).thenReturn("");
      when(joinPoint.getSignature()).thenReturn(methodSignature);
      when(methodSignature.getParameterNames()).thenReturn(new String[] {"injectId"});
      when(joinPoint.getArgs()).thenReturn(new Object[] {injectIdValue});
    }

    private void setupExpectationIdsJoinPoint(Object expectationIdsValue) {
      when(annotation.injectId()).thenReturn("");
      when(annotation.expectationIds()).thenReturn("#expectationIds");
      when(joinPoint.getSignature()).thenReturn(methodSignature);
      when(methodSignature.getParameterNames()).thenReturn(new String[] {"expectationIds"});
      when(joinPoint.getArgs()).thenReturn(new Object[] {expectationIdsValue});
    }

    @Test
    void shouldRetryCachedEvents_onNextSuccessfulCall() throws IOException {
      // -------- Prepare: First call fails --------
      String injectId1 = "inject-1";
      String stepId1 = "step-1";
      setupInjectIdJoinPoint(injectId1);

      when(stepService.findStepIdByInjectId(injectId1)).thenReturn(stepId1);
      doThrow(new IOException("Queue error")).when(queueChainingService).updateStep(stepId1);

      // First call - should cache stepId1
      aspect.afterEventProcessed(joinPoint, annotation);

      // -------- Prepare: Second call succeeds --------
      reset(queueChainingService);
      String injectId2 = "inject-2";
      String stepId2 = "step-2";
      setupInjectIdJoinPoint(injectId2);

      when(stepService.findStepIdByInjectId(injectId2)).thenReturn(stepId2);
      // Now updateStep succeeds

      // -------- Act --------
      aspect.afterEventProcessed(joinPoint, annotation);

      // -------- Assert --------
      // Both stepId2 (new) and stepId1 (cached) should be sent
      verify(queueChainingService, atLeast(2)).updateStep(stepIdCaptor.capture());
      List<String> capturedStepIds = stepIdCaptor.getAllValues();
      assertTrue(capturedStepIds.contains(stepId1), "Should retry cached stepId1");
      assertTrue(capturedStepIds.contains(stepId2), "Should send new stepId2");
    }

    @Test
    void shouldClearCache_whenAllEventsSentSuccessfully() throws IOException {
      // -------- Prepare: First call fails --------
      String injectId1 = "inject-1";
      String stepId1 = "step-1";
      setupInjectIdJoinPoint(injectId1);

      when(stepService.findStepIdByInjectId(injectId1)).thenReturn(stepId1);
      doThrow(new IOException("Queue error")).when(queueChainingService).updateStep(stepId1);

      // First call - caches stepId1
      aspect.afterEventProcessed(joinPoint, annotation);

      // -------- Prepare: Second call succeeds --------
      reset(queueChainingService);
      String injectId2 = "inject-2";
      String stepId2 = "step-2";
      setupInjectIdJoinPoint(injectId2);

      when(stepService.findStepIdByInjectId(injectId2)).thenReturn(stepId2);

      // Second call - sends both and clears cache
      aspect.afterEventProcessed(joinPoint, annotation);

      // -------- Prepare: Third call with no step found --------
      reset(queueChainingService);
      String injectId3 = "inject-3";
      setupInjectIdJoinPoint(injectId3);

      // -------- Act --------
      when(stepService.findStepIdByInjectId(injectId3))
          .thenThrow(
              new ElementNotFoundException("Step id not found for inject id : " + injectId3));

      assertThrows(
          ElementNotFoundException.class, () -> aspect.afterEventProcessed(joinPoint, annotation));

      // -------- Assert --------
      // Cache should be cleared, so no retry attempts on third call
      verifyNoInteractions(queueChainingService);
    }

    @Test
    void shouldAccumulateCachedEvents_whenMultipleFailuresOccur() throws IOException {
      // -------- First call fails --------
      String injectId1 = "inject-1";
      String stepId1 = "step-1";
      setupInjectIdJoinPoint(injectId1);

      when(stepService.findStepIdByInjectId(injectId1)).thenReturn(stepId1);
      doThrow(new IOException("Queue error")).when(queueChainingService).updateStep(any());

      aspect.afterEventProcessed(joinPoint, annotation);

      // -------- Second call also fails --------
      reset(stepService);
      String injectId2 = "inject-2";
      String stepId2 = "step-2";
      setupInjectIdJoinPoint(injectId2);

      when(stepService.findStepIdByInjectId(injectId2)).thenReturn(stepId2);

      aspect.afterEventProcessed(joinPoint, annotation);

      // -------- Third call succeeds --------
      reset(queueChainingService, stepService);
      String injectId3 = "inject-3";
      String stepId3 = "step-3";
      setupInjectIdJoinPoint(injectId3);

      when(stepService.findStepIdByInjectId(injectId3)).thenReturn(stepId3);

      // -------- Act --------
      aspect.afterEventProcessed(joinPoint, annotation);

      // -------- Assert --------
      // All three stepIds should be attempted (stepId3 new, stepId1 and stepId2 cached)
      verify(queueChainingService, atLeast(2)).updateStep(stepIdCaptor.capture());
      List<String> capturedStepIds = stepIdCaptor.getAllValues();
      assertTrue(capturedStepIds.contains(stepId3), "Should send new stepId3");
    }

    @Test
    void shouldHandlePartialFailure_inSendEvents() throws IOException {
      // -------- Prepare --------
      setupExpectationIdsJoinPoint("exp-1");

      // Use LinkedHashSet for predictable order
      Set<String> stepIds = new LinkedHashSet<>();
      stepIds.add("step-1");
      stepIds.add("step-2");
      stepIds.add("step-3");
      when(stepService.findStepIdsByExpectationIds(Set.of("exp-1"))).thenReturn(stepIds);

      // First call succeeds, second fails
      doNothing()
          .doThrow(new IOException("Queue error"))
          .when(queueChainingService)
          .updateStep(any());

      // -------- Act --------
      aspect.afterEventProcessed(joinPoint, annotation);

      // -------- Assert --------
      // At least 2 calls were made (first succeeded, second failed)
      verify(queueChainingService, atLeast(2)).updateStep(any());
    }
  }

  /* ============================================================
   * SpEL expression evaluation
   * ============================================================ */
  @Nested
  class SpELExpressionEvaluation {

    @BeforeEach
    void setUp() {
      when(previewFeatureService.isFeatureEnabled(PreviewFeature.INJECT_CHAINING)).thenReturn(true);
    }

    @Test
    void shouldEvaluateNestedSpELExpression_forInjectId() throws IOException {
      // -------- Prepare --------
      when(annotation.injectId()).thenReturn("#request.injectId");
      when(annotation.expectationIds()).thenReturn("");

      // Create a simple object to represent the request
      TestRequest request = new TestRequest("nested-inject-123");

      when(joinPoint.getSignature()).thenReturn(methodSignature);
      when(methodSignature.getParameterNames()).thenReturn(new String[] {"request"});
      when(joinPoint.getArgs()).thenReturn(new Object[] {request});

      String stepId = "step-for-nested";
      when(stepService.findStepIdByInjectId("nested-inject-123")).thenReturn(stepId);

      // -------- Act --------
      aspect.afterEventProcessed(joinPoint, annotation);

      // -------- Assert --------
      verify(stepService).findStepIdByInjectId("nested-inject-123");
      verify(queueChainingService).updateStep(stepId);
    }

    @Test
    void shouldEvaluateNestedSpELExpression_forExpectationIds() throws IOException {
      // -------- Prepare --------
      when(annotation.injectId()).thenReturn("");
      when(annotation.expectationIds()).thenReturn("#request.expectationIds");

      TestRequestWithList request = new TestRequestWithList(List.of("exp-a", "exp-b"));

      when(joinPoint.getSignature()).thenReturn(methodSignature);
      when(methodSignature.getParameterNames()).thenReturn(new String[] {"request"});
      when(joinPoint.getArgs()).thenReturn(new Object[] {request});

      when(stepService.findStepIdsByExpectationIds(Set.of("exp-a", "exp-b")))
          .thenReturn(Set.of("step-x"));

      // -------- Act --------
      aspect.afterEventProcessed(joinPoint, annotation);

      // -------- Assert --------
      verify(stepService).findStepIdsByExpectationIds(Set.of("exp-a", "exp-b"));
      verify(queueChainingService).updateStep("step-x");
    }
  }

  /* ============================================================
   * Test helper records
   * ============================================================ */
  record TestRequest(String injectId) {}

  record TestRequestWithList(List<String> expectationIds) {}
}
