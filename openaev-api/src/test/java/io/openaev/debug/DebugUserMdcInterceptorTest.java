package io.openaev.debug;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

@DisplayName("DebugUserMdcInterceptor")
class DebugUserMdcInterceptorTest {

  private final DebugUserMdcInterceptor interceptor =
      new DebugUserMdcInterceptor(
          new DebugUserSource() {
            @Override
            public String currentUser() {
              return "u-9";
            }
          });

  @AfterEach
  void clear() {
    MDC.clear();
  }

  @Test
  @DisplayName("tags the request with user= on preHandle and removes it on afterCompletion")
  void putsAndRemovesUser() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    MockHttpServletResponse response = new MockHttpServletResponse();

    interceptor.preHandle(request, response, new Object());
    assertThat(MDC.get("user")).isEqualTo("u-9");

    interceptor.afterCompletion(request, response, new Object(), null);
    assertThat(MDC.get("user")).isNull();
  }

  @Test
  @DisplayName("removes user= when the request goes async (afterConcurrentHandlingStarted)")
  void removesUserOnAsyncDispatch() {
    // Async requests (e.g. StreamingResponseBody) do not call afterCompletion on the initial
    // dispatch: the servlet thread must not go back to the pool with the caller still in MDC.
    MockHttpServletRequest request = new MockHttpServletRequest();
    MockHttpServletResponse response = new MockHttpServletResponse();

    interceptor.preHandle(request, response, new Object());
    assertThat(MDC.get("user")).isEqualTo("u-9");

    interceptor.afterConcurrentHandlingStarted(request, response, new Object());
    assertThat(MDC.get("user")).isNull();
  }
}
