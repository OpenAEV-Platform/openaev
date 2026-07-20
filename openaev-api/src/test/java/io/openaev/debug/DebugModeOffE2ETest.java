package io.openaev.debug;

import static io.openaev.rest.tag.TagApi.TAG_URI;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.openaev.IntegrationTest;
import io.openaev.utils.mockUser.WithMockUser;
import java.util.ArrayList;
import javax.sql.DataSource;
import net.ttddyy.dsproxy.support.ProxyDataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Real-app proof that the default install carries no debug overhead: with the flag off (the
 * default), the running context has none of the debug beans and the datasource is the plain pool,
 * not a proxy.
 *
 * <p>This reuses the default integration-test context (no extra boot).
 */
@DisplayName("Debug mode off in the real app (default)")
class DebugModeOffE2ETest extends IntegrationTest {

  @Autowired private DataSource dataSource;
  @Autowired private ApplicationContext context;
  @Autowired private MockMvc mvc;

  @Test
  @DisplayName("no tracing handler when debug is off, so no span/traceId on any path")
  void noTracingWhenDebugOff() {
    // The Brave bridge is on the classpath for debug mode, but with the flag off the tracing
    // auto-configuration is excluded, so no observation handler turns observations into spans.
    // Without this, every request/message/job would pay for a span and leak a traceId into the logs
    // by default (management.tracing.enabled only gates export, not span creation).
    assertThat(
            context.getBeanNamesForType(
                io.micrometer.tracing.handler.TracingObservationHandler.class))
        .isEmpty();
  }

  @Test
  @DisplayName("datasource is not proxied and no debug beans exist")
  void noDebugFootprintByDefault() {
    assertThat(dataSource)
        .as("no SQL proxy on the query hot path when debug mode is off")
        .isNotInstanceOf(ProxyDataSource.class);

    assertThat(context.getBeanNamesForType(DataSourceProxyBeanPostProcessor.class)).isEmpty();
    assertThat(context.getBeanNamesForType(MaskingSqlLoggingListener.class)).isEmpty();
    assertThat(context.getBeanNamesForType(JfrRecordingManager.class)).isEmpty();
    assertThat(context.getBeanNamesForType(DebugModeManager.class)).isEmpty();
  }

  @Test
  @WithMockUser(isAdmin = true)
  @DisplayName("a real request produces no debug log lines at all (no slowdown by default)")
  void noDebugLogsByDefault() throws Exception {
    Logger debugLogger = (Logger) LoggerFactory.getLogger("io.openaev.debug");
    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.start();
    debugLogger.addAppender(appender);
    try {
      mvc.perform(get(TAG_URI)).andExpect(status().isOk());

      assertThat(new ArrayList<>(appender.list))
          .as("debug mode is off, so nothing under io.openaev.debug should log")
          .isEmpty();
    } finally {
      debugLogger.detachAppender(appender);
    }
  }
}
