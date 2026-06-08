package io.openaev.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;
import java.util.Objects;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.core.task.TaskDecorator;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

@DisplayName("ThreadPoolTaskLoggerConfig unit tests")
class ThreadPoolTaskLoggerConfigTest {

  private final ThreadPoolTaskLoggerConfig config = new ThreadPoolTaskLoggerConfig();

  @AfterEach
  void tearDown() {
    MDC.clear();
    LocaleContextHolder.resetLocaleContext();
    SecurityContextHolder.clearContext();
    org.springframework.web.context.request.RequestContextHolder.resetRequestAttributes();
    ThreadPoolTaskLoggerConfig.ThreadRequestContextHolder.clear();
  }

  @Nested
  @DisplayName("buildThreadRequestContextHolder")
  class BuildThreadRequestContextHolderTests {

    @Test
    void given_requestAndAuthentication_should_buildRequestContextData() {
      // Arrange
      MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/test");
      request.addHeader("X-Trace-Id", "trace-123");
      request.setRemoteAddr("127.0.0.1");
      MockHttpSession session = new MockHttpSession();
      request.setSession(session);
      Authentication authentication = new TestingAuthenticationToken("john.doe", "pwd");

      // Act
      ThreadPoolTaskLoggerConfig.ThreadRequestContextHolder.RequestContextData data =
          ThreadPoolTaskLoggerConfig.buildThreadRequestContextHolder(request, authentication);

      // Assert
      assertThat(data.headers()).containsEntry("X-Trace-Id", "trace-123");
      assertThat(data.remoteAddress()).isEqualTo("127.0.0.1");
      assertThat(data.method()).isEqualTo("POST");
      assertThat(data.url()).isEqualTo("http://localhost/api/test");
      assertThat(data.sessionId()).isEqualTo(session.getId());
      assertThat(data.authentication()).isSameAs(authentication);
    }

    @Test
    void given_nullRequest_should_buildEmptyRequestContextData() {
      // Arrange
      Authentication authentication = new TestingAuthenticationToken("john.doe", "pwd");

      // Act
      ThreadPoolTaskLoggerConfig.ThreadRequestContextHolder.RequestContextData data =
          ThreadPoolTaskLoggerConfig.buildThreadRequestContextHolder(null, authentication);

      // Assert
      assertThat(data.headers()).isNull();
      assertThat(data.remoteAddress()).isNull();
      assertThat(data.method()).isNull();
      assertThat(data.url()).isNull();
      assertThat(data.sessionId()).isNull();
      assertThat(data.authentication()).isSameAs(authentication);
    }
  }

  @Nested
  @DisplayName("ThreadRequestContextHolder")
  class ThreadRequestContextHolderTests {

    @Test
    void given_valuesAreSet_should_getValuesAndTypedData() {
      // Arrange
      ThreadPoolTaskLoggerConfig.ThreadRequestContextHolder.set("key", "value");
      ThreadPoolTaskLoggerConfig.ThreadRequestContextHolder.RequestContextData data =
          new ThreadPoolTaskLoggerConfig.ThreadRequestContextHolder.RequestContextData(
              Map.of("X-Test", "v"), "10.0.0.1", "GET", "http://localhost/api", "session-1", null);

      // Act
      ThreadPoolTaskLoggerConfig.ThreadRequestContextHolder.setRequestContextData(data);
      Object value = ThreadPoolTaskLoggerConfig.ThreadRequestContextHolder.get("key");
      ThreadPoolTaskLoggerConfig.ThreadRequestContextHolder.RequestContextData readData =
          ThreadPoolTaskLoggerConfig.ThreadRequestContextHolder.getRequestContextData();

      // Assert
      assertThat(value).isEqualTo("value");
      assertThat(readData).isEqualTo(data);
    }

    @Test
    void given_contextIsCleared_should_removeStoredValues() {
      // Arrange
      ThreadPoolTaskLoggerConfig.ThreadRequestContextHolder.set("key", "value");

      // Act
      ThreadPoolTaskLoggerConfig.ThreadRequestContextHolder.clear();

      // Assert
      assertThat(ThreadPoolTaskLoggerConfig.ThreadRequestContextHolder.get("key")).isNull();
      assertThat(ThreadPoolTaskLoggerConfig.ThreadRequestContextHolder.getRequestContextData())
          .isNull();
    }
  }

  @Nested
  @DisplayName("contextAwareExecutor")
  class ContextAwareExecutorTests {

    @Test
    void given_contextsExist_should_propagateThemToDecoratedRunnableAndCleanup() {
      // Arrange
      MDC.put("traceId", "trace-parent");
      LocaleContextHolder.setLocale(Locale.FRANCE);
      Authentication authentication = new TestingAuthenticationToken("jane.doe", "pwd");
      SecurityContextHolder.getContext().setAuthentication(authentication);

      MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/audit");
      request.addHeader("X-Test", "header-value");
      request.setRemoteAddr("192.168.1.10");
      MockHttpSession session = new MockHttpSession();
      request.setSession(session);
      org.springframework.web.context.request.RequestContextHolder.setRequestAttributes(
          new org.springframework.web.context.request.ServletRequestAttributes(request));

      ThreadPoolTaskLoggerConfig.ThreadRequestContextHolder.RequestContextData expectedData =
          ThreadPoolTaskLoggerConfig.buildThreadRequestContextHolder(request, authentication);

      Executor rawExecutor = config.contextAwareExecutor();
      ThreadPoolTaskExecutor executor = (ThreadPoolTaskExecutor) rawExecutor;
      TaskDecorator taskDecorator =
          Objects.requireNonNull(
              (TaskDecorator) ReflectionTestUtils.getField(executor, "taskDecorator"),
              "taskDecorator should be set");

      AtomicReference<String> mdcTrace = new AtomicReference<>();
      AtomicReference<Locale> locale = new AtomicReference<>();
      AtomicReference<Authentication> auth = new AtomicReference<>();
      AtomicReference<ThreadPoolTaskLoggerConfig.ThreadRequestContextHolder.RequestContextData>
          requestData = new AtomicReference<>();

      Runnable decoratedRunnable =
          taskDecorator.decorate(
              () -> {
                mdcTrace.set(MDC.get("traceId"));
                locale.set(LocaleContextHolder.getLocale());
                auth.set(SecurityContextHolder.getContext().getAuthentication());
                requestData.set(
                    ThreadPoolTaskLoggerConfig.ThreadRequestContextHolder.getRequestContextData());
              });

      // Act
      decoratedRunnable.run();

      // Assert
      assertThat(mdcTrace.get()).isEqualTo("trace-parent");
      assertThat(locale.get()).isEqualTo(Locale.FRANCE);
      assertThat(auth.get()).isSameAs(authentication);
      assertThat(requestData.get()).isEqualTo(expectedData);

      assertThat(MDC.getCopyOfContextMap()).isNull();
      assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
      assertThat(ThreadPoolTaskLoggerConfig.ThreadRequestContextHolder.getRequestContextData())
          .isNull();
    }

    @Test
    void given_noContexts_should_executeWithoutErrors() {
      // Arrange
      MDC.clear();
      LocaleContextHolder.resetLocaleContext();
      SecurityContextHolder.clearContext();
      org.springframework.web.context.request.RequestContextHolder.resetRequestAttributes();

      Executor rawExecutor = config.contextAwareExecutor();
      ThreadPoolTaskExecutor executor = (ThreadPoolTaskExecutor) rawExecutor;
      TaskDecorator taskDecorator =
          Objects.requireNonNull(
              (TaskDecorator) ReflectionTestUtils.getField(executor, "taskDecorator"),
              "taskDecorator should be set");

      AtomicReference<Map<String, String>> mdcMap = new AtomicReference<>();
      AtomicReference<Authentication> authentication = new AtomicReference<>();
      AtomicReference<ThreadPoolTaskLoggerConfig.ThreadRequestContextHolder.RequestContextData>
          requestData = new AtomicReference<>();

      Runnable decoratedRunnable =
          taskDecorator.decorate(
              () -> {
                mdcMap.set(MDC.getCopyOfContextMap());
                authentication.set(SecurityContextHolder.getContext().getAuthentication());
                requestData.set(
                    ThreadPoolTaskLoggerConfig.ThreadRequestContextHolder.getRequestContextData());
              });

      // Act
      decoratedRunnable.run();

      // Assert
      assertThat(mdcMap.get()).isNull();
      assertThat(authentication.get()).isNull();
      assertThat(requestData.get()).isNotNull();
      assertThat(requestData.get().headers()).isNull();
      assertThat(requestData.get().remoteAddress()).isNull();
      assertThat(requestData.get().method()).isNull();
      assertThat(requestData.get().url()).isNull();
      assertThat(requestData.get().sessionId()).isNull();
      assertThat(requestData.get().authentication()).isNull();
    }
  }
}
