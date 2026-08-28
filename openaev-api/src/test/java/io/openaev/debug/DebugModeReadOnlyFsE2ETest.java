package io.openaev.debug;

import static io.openaev.rest.tag.TagApi.TAG_URI;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.openaev.IntegrationTest;
import io.openaev.utils.mockUser.WithMockUser;
import javax.sql.DataSource;
import net.ttddyy.dsproxy.support.ProxyDataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Real-app proof that a non-writable JFR output location does not take the platform down. Debug
 * mode is enabled but the JFR output directory points under an existing regular file ({@code
 * pom.xml}), so the directory cannot be created on any filesystem (this reproduces a hardened
 * read-only container with no writable volume, deterministically and without depending on file
 * permissions).
 *
 * <p>The application must still start, the SQL part must still be active, and requests must still
 * be served; only JFR is degraded and it reports {@link JfrRecordingManager.Status#FAILED}.
 */
@TestPropertySource(
    properties = {
      "openaev.debug.enabled=true",
      // Parent is a regular file -> directory creation always fails (incl. as root).
      "openaev.debug.output-dir=pom.xml/debug-readonly-e2e"
    })
@DisplayName("Debug mode end-to-end with a non-writable JFR location")
class DebugModeReadOnlyFsE2ETest extends IntegrationTest {

  @Autowired private MockMvc mvc;
  @Autowired private DataSource dataSource;
  @Autowired private JfrRecordingManager jfrRecordingManager;
  @Autowired private DebugSqlLogFileConfigurer sqlLogFileConfigurer;

  @Test
  @DisplayName("JFR fails loudly but the application started and SQL logging is still active")
  void jfrFailedButAppIsUp() {
    assertThat(jfrRecordingManager.getStatus()).isEqualTo(JfrRecordingManager.Status.FAILED);
    assertThat(jfrRecordingManager.getLastError()).isNotBlank();
    assertThat(dataSource).isInstanceOf(ProxyDataSource.class);
    // The SQL log file could not be created either; it falls back to the console rather than crash.
    assertThat(sqlLogFileConfigurer.isAttached()).isFalse();
  }

  @Test
  @WithMockUser(isAdmin = true)
  @DisplayName("requests are still served while JFR is degraded")
  void requestsStillServed() throws Exception {
    mvc.perform(get(TAG_URI)).andExpect(status().isOk());
  }
}
