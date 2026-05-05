package io.openaev.aop;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import io.openaev.context.TenantContext;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.Signature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("BypassRlsAspect")
class BypassRlsAspectTest {

  @InjectMocks private BypassRlsAspect aspect;

  @AfterEach
  void tearDown() {
    TenantContext.clearRlsBypass();
  }

  @Nested
  @DisplayName("RLS bypass for scheduled jobs")
  class RlsBypassForScheduledJobs {

    @Test
    @DisplayName(
        "given_bypassRls_annotation_when_method_executes_should_enable_rls_bypass_on_thread")
    void given_bypassRls_annotation_when_method_executes_should_enable_rls_bypass_on_thread()
        throws Throwable {
      // -- Arrange --
      ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
      Signature signature = mock(Signature.class);
      when(joinPoint.getSignature()).thenReturn(signature);
      when(signature.toShortString()).thenReturn("InjectsExecutionJob.execute(..)");

      // Capture the bypass state during execution
      when(joinPoint.proceed())
          .thenAnswer(
              invocation -> {
                // -- Assert (during execution) --
                assertThat(TenantContext.isRlsBypassed())
                    .as("RLS should be bypassed while annotated method is running")
                    .isTrue();
                return null;
              });

      // -- Act --
      aspect.aroundBypassRls(joinPoint);

      // -- Assert (after execution) --
      assertThat(TenantContext.isRlsBypassed())
          .as("RLS bypass should be cleared after method completes")
          .isFalse();
    }

    @Test
    @DisplayName("given_bypassRls_annotation_when_method_throws_should_still_clear_rls_bypass")
    void given_bypassRls_annotation_when_method_throws_should_still_clear_rls_bypass()
        throws Throwable {
      // -- Arrange --
      ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
      Signature signature = mock(Signature.class);
      when(joinPoint.getSignature()).thenReturn(signature);
      when(signature.toShortString()).thenReturn("InjectsExecutionJob.execute(..)");
      when(joinPoint.proceed()).thenThrow(new RuntimeException("Job failed"));

      // -- Act --
      try {
        aspect.aroundBypassRls(joinPoint);
      } catch (RuntimeException ignored) {
        // expected
      }

      // -- Assert --
      assertThat(TenantContext.isRlsBypassed())
          .as("RLS bypass should be cleared even when method throws")
          .isFalse();
    }

    @Test
    @DisplayName("given_no_bypass_when_checking_rls_state_should_be_false_by_default")
    void given_no_bypass_when_checking_rls_state_should_be_false_by_default() {
      // -- Assert --
      assertThat(TenantContext.isRlsBypassed())
          .as("RLS bypass should be false by default")
          .isFalse();
    }
  }
}
