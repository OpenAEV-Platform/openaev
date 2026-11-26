package io.openaev.aop.audit_log;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.aop.AccessControl;
import io.openaev.database.model.Action;
import io.openaev.database.model.EventStatus;
import io.openaev.database.model.ResourceType;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import java.lang.reflect.Method;
import java.util.concurrent.CompletableFuture;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * The flush the aspect runs before logging executes the request's pending writes: a failure there
 * belongs to the request. Swallowed, it left the transaction rollback-only and the commit failed
 * with an {@code UnexpectedRollbackException} hiding it (deleting a marking definition held by one
 * of the caller's groups). Outside a transaction ({@code Propagation.SUPPORTS} endpoints, whose
 * writes have committed), there is nothing to flush and the flush is skipped.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AccessControlAuditLogAspect — flush before logging")
class AccessControlAuditLogAspectFlushTest {

  @Mock private AuditLogger auditLogger;
  @Mock private EntityManager entityManager;
  @Mock private ProceedingJoinPoint joinPoint;
  @Mock private MethodSignature methodSignature;

  private AccessControlAuditLogAspect aspect;

  /** Controller-like fixture carrying the annotation the aspect reads. */
  @SuppressWarnings("unused")
  static class SampleApi {

    @AccessControl(actionPerformed = Action.DELETE, resourceType = ResourceType.MARKING_DEFINITION)
    void delete() {}
  }

  @BeforeEach
  void setup() throws NoSuchMethodException {
    ObjectMapper objectMapper = new ObjectMapper();
    aspect =
        new AccessControlAuditLogAspect(
            auditLogger, objectMapper, new AuditObjectMapper(objectMapper));
    ReflectionTestUtils.setField(aspect, "entityManager", entityManager);

    Method method = SampleApi.class.getDeclaredMethod("delete");
    when(joinPoint.getSignature()).thenReturn(methodSignature);
    when(methodSignature.getMethod()).thenReturn(method);
    lenient().when(methodSignature.getParameterNames()).thenReturn(new String[] {});
    lenient().when(joinPoint.getArgs()).thenReturn(new Object[] {});
    when(auditLogger.isAuditLoggingEnabled()).thenReturn(true);
    when(auditLogger.isAuditLoggingValid(any())).thenReturn(true);
    lenient()
        .when(
            auditLogger.logAccessControlEvent(
                any(), any(), any(), anyString(), any(), any(), any()))
        .thenReturn(CompletableFuture.completedFuture(true));
  }

  @Test
  @DisplayName("given_flushFailsAfterSuccessfulCall_should_propagateFailureAndLogNothing")
  void given_flushFailsAfterSuccessfulCall_should_propagateFailureAndLogNothing() throws Throwable {
    // Arrange
    PersistenceException flushFailure = new PersistenceException("flush failed");
    when(joinPoint.proceed()).thenReturn(null);
    when(entityManager.isJoinedToTransaction()).thenReturn(true);
    doThrow(flushFailure).when(entityManager).flush();

    // Act & Assert
    assertThatThrownBy(() -> aspect.auditAround(joinPoint)).isSameAs(flushFailure);
    verify(auditLogger, never())
        .logAccessControlEvent(any(), any(), any(), anyString(), any(), any(), any());
  }

  @Test
  @DisplayName("given_callAndFlushFail_should_rethrowCallFailure")
  void given_callAndFlushFail_should_rethrowCallFailure() throws Throwable {
    // Arrange
    IllegalArgumentException callFailure = new IllegalArgumentException("call failed");
    when(joinPoint.proceed()).thenThrow(callFailure);
    when(entityManager.isJoinedToTransaction()).thenReturn(true);
    doThrow(new PersistenceException("flush failed")).when(entityManager).flush();

    // Act & Assert
    assertThatThrownBy(() -> aspect.auditAround(joinPoint)).isSameAs(callFailure);
  }

  @Test
  @DisplayName("given_noTransactionAfterSuccessfulCall_should_skipFlushAndLogSuccess")
  void given_noTransactionAfterSuccessfulCall_should_skipFlushAndLogSuccess() throws Throwable {
    // Arrange
    String result = "deleted";
    when(joinPoint.proceed()).thenReturn(result);
    when(entityManager.isJoinedToTransaction()).thenReturn(false);

    // Act
    Object returned = aspect.auditAround(joinPoint);

    // Assert
    assertThat(returned).isSameAs(result);
    verify(entityManager, never()).flush();
    verify(auditLogger)
        .logAccessControlEvent(
            any(), eq(EventStatus.SUCCESS), any(), anyString(), any(), any(), any());
  }
}
