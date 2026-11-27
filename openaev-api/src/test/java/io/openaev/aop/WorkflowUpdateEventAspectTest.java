package io.openaev.aop;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import io.openaev.service.chaining.WorkflowUpdateEventPublisher;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.Signature;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("The workflow update event aspect resolves its ids and hands them to the publisher")
class WorkflowUpdateEventAspectTest {

  @Mock private WorkflowUpdateEventPublisher workflowUpdateEventPublisher;

  @Mock private JoinPoint joinPoint;
  @Mock private MethodSignature methodSignature;
  @Mock private WorkflowUpdateEvent annotation;

  @InjectMocks private WorkflowUpdateEventAspect aspect;

  private void setupJoinPoint(String parameterName, Object value) {
    when(joinPoint.getSignature()).thenReturn(methodSignature);
    when(methodSignature.getParameterNames()).thenReturn(new String[] {parameterName});
    when(joinPoint.getArgs()).thenReturn(new Object[] {value});
  }

  @Nested
  @DisplayName("Invalid annotation configuration")
  class InvalidAnnotationConfiguration {

    @BeforeEach
    void setUp() {
      Signature signature = mock(Signature.class);
      when(joinPoint.getSignature()).thenReturn(signature);
      when(signature.toShortString()).thenReturn("TestMethod()");
    }

    @Test
    @DisplayName("given both injectId and expectationIds, should throw and publish nothing")
    void given_injectIdAndExpectationIds_should_throwAndPublishNothing() {
      // -- Arrange --
      when(annotation.injectId()).thenReturn("#injectId");
      when(annotation.expectationIds()).thenReturn("#expectationIds");

      // -- Act --
      IllegalStateException ex =
          assertThrows(
              IllegalStateException.class, () -> aspect.afterEventProcessed(joinPoint, annotation));

      // -- Assert --
      assertTrue(
          ex.getMessage()
              .contains("must set exactly one of injectId, injectIds or expectationIds"));
      verifyNoInteractions(workflowUpdateEventPublisher);
    }

    @Test
    @DisplayName("given no field set, should throw and publish nothing")
    void given_noFieldSet_should_throwAndPublishNothing() {
      // -- Arrange --
      when(annotation.injectId()).thenReturn("");
      when(annotation.injectIds()).thenReturn("");
      when(annotation.expectationIds()).thenReturn("");

      // -- Act --
      IllegalStateException ex =
          assertThrows(
              IllegalStateException.class, () -> aspect.afterEventProcessed(joinPoint, annotation));

      // -- Assert --
      assertTrue(
          ex.getMessage()
              .contains("must set exactly one of injectId, injectIds or expectationIds"));
      verifyNoInteractions(workflowUpdateEventPublisher);
    }
  }

  @Nested
  @DisplayName("injectId")
  class InjectIdPath {

    @BeforeEach
    void setUp() {
      when(annotation.injectId()).thenReturn("#injectId");
      when(annotation.injectIds()).thenReturn("");
      when(annotation.expectationIds()).thenReturn("");
    }

    @ParameterizedTest(name = "{index} => injectIdValue={0}")
    @MethodSource("emptyInjectIds")
    @DisplayName(
        "given an empty inject id, should hand it to the publisher, which skips the lookup")
    void given_emptyInjectId_should_handItToThePublisher(String injectIdValue) {
      // -- Arrange --
      setupJoinPoint("injectId", injectIdValue);

      // -- Act --
      aspect.afterEventProcessed(joinPoint, annotation);

      // -- Assert --
      verify(workflowUpdateEventPublisher).publishForInject(injectIdValue);
    }

    static Stream<Arguments> emptyInjectIds() {
      return Stream.of(Arguments.of((String) null), Arguments.of(""));
    }

    @Test
    @DisplayName("given an inject id, should publish for it")
    void given_injectId_should_publishForIt() {
      // -- Arrange --
      setupJoinPoint("injectId", "inject-123");

      // -- Act --
      aspect.afterEventProcessed(joinPoint, annotation);

      // -- Assert --
      verify(workflowUpdateEventPublisher).publishForInject("inject-123");
    }

    @Test
    @DisplayName("given a nested SpEL expression, should publish for the inject it resolves")
    void given_nestedSpEL_should_publishForTheResolvedInject() {
      // -- Arrange --
      when(annotation.injectId()).thenReturn("#request.injectId");
      setupJoinPoint("request", new TestRequest("nested-inject-123"));

      // -- Act --
      aspect.afterEventProcessed(joinPoint, annotation);

      // -- Assert --
      verify(workflowUpdateEventPublisher).publishForInject("nested-inject-123");
    }
  }

  @Nested
  @DisplayName("injectIds")
  class InjectIdsPath {

    @BeforeEach
    void setUp() {
      when(annotation.injectId()).thenReturn("");
      when(annotation.injectIds()).thenReturn("#injectIds");
      when(annotation.expectationIds()).thenReturn("");
    }

    @Test
    @DisplayName("given a collection, should publish for its distinct inject ids")
    void given_collection_should_publishForItsDistinctIds() {
      // -- Arrange --
      setupJoinPoint("injectIds", List.of("inject-1", "inject-2", "inject-1"));

      // -- Act --
      aspect.afterEventProcessed(joinPoint, annotation);

      // -- Assert --
      verify(workflowUpdateEventPublisher).publishForInjects(Set.of("inject-1", "inject-2"));
    }

    @Test
    @DisplayName("given a single string, should publish for that inject")
    void given_singleString_should_publishForThatInject() {
      // -- Arrange --
      setupJoinPoint("injectIds", "inject-single");

      // -- Act --
      aspect.afterEventProcessed(joinPoint, annotation);

      // -- Assert --
      verify(workflowUpdateEventPublisher).publishForInjects(Set.of("inject-single"));
    }

    @Test
    @DisplayName("given neither a collection nor a string, should throw and publish nothing")
    void given_invalidValue_should_throwAndPublishNothing() {
      // -- Arrange --
      setupJoinPoint("injectIds", 12345);

      // -- Act --
      IllegalStateException ex =
          assertThrows(
              IllegalStateException.class, () -> aspect.afterEventProcessed(joinPoint, annotation));

      // -- Assert --
      assertTrue(ex.getMessage().contains("must return a Collection or a String"));
      verifyNoInteractions(workflowUpdateEventPublisher);
    }
  }

  @Nested
  @DisplayName("expectationIds")
  class ExpectationIdsPath {

    @BeforeEach
    void setUp() {
      when(annotation.injectId()).thenReturn("");
      when(annotation.injectIds()).thenReturn("");
      when(annotation.expectationIds()).thenReturn("#expectationIds");
    }

    @Test
    @DisplayName("given a collection, should publish for its expectation ids")
    void given_collection_should_publishForItsExpectationIds() {
      // -- Arrange --
      setupJoinPoint("expectationIds", List.of("exp-1", "exp-2", "exp-3"));

      // -- Act --
      aspect.afterEventProcessed(joinPoint, annotation);

      // -- Assert --
      verify(workflowUpdateEventPublisher)
          .publishForExpectations(Set.of("exp-1", "exp-2", "exp-3"));
    }

    @Test
    @DisplayName("given a single string, should publish for that expectation")
    void given_singleString_should_publishForThatExpectation() {
      // -- Arrange --
      setupJoinPoint("expectationIds", "exp-single");

      // -- Act --
      aspect.afterEventProcessed(joinPoint, annotation);

      // -- Assert --
      verify(workflowUpdateEventPublisher).publishForExpectations(Set.of("exp-single"));
    }

    @Test
    @DisplayName("given a nested SpEL expression, should publish for the expectations it resolves")
    void given_nestedSpEL_should_publishForTheResolvedExpectations() {
      // -- Arrange --
      when(annotation.expectationIds()).thenReturn("#request.expectationIds");
      setupJoinPoint("request", new TestRequestWithList(List.of("exp-a", "exp-b")));

      // -- Act --
      aspect.afterEventProcessed(joinPoint, annotation);

      // -- Assert --
      verify(workflowUpdateEventPublisher).publishForExpectations(Set.of("exp-a", "exp-b"));
    }

    @Test
    @DisplayName("given neither a collection nor a string, should throw and publish nothing")
    void given_invalidValue_should_throwAndPublishNothing() {
      // -- Arrange --
      setupJoinPoint("expectationIds", 12345);

      // -- Act --
      IllegalStateException ex =
          assertThrows(
              IllegalStateException.class, () -> aspect.afterEventProcessed(joinPoint, annotation));

      // -- Assert --
      assertTrue(ex.getMessage().contains("must return a Collection or a String"));
      verifyNoInteractions(workflowUpdateEventPublisher);
    }
  }

  record TestRequest(String injectId) {}

  record TestRequestWithList(List<String> expectationIds) {}
}
