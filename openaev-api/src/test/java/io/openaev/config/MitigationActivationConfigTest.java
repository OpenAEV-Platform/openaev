package io.openaev.config;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Properties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Guards that the production configuration keeps {@code mitigations} activated in v2. The table is
 * fully on the new mechanism: its v1 {@code @Filter} has been removed, so if it ever dropped out of
 * {@code openaev.tenant.active-tables} it would have <b>no</b> tenant isolation at all (neither v1
 * nor v2). This reads the production {@code application.properties} directly, because the test
 * classpath deliberately shadows it with the test config (which keeps the allowlist empty so the
 * rest of the suite controls activation per test via {@code @TestPropertySource}).
 */
@DisplayName("Production config keeps mitigations activated (v2)")
class MitigationActivationConfigTest {

  @Test
  @DisplayName("openaev.tenant.active-tables in application.properties contains mitigations")
  void prodConfigActivatesMitigations() throws Exception {
    Properties props = new Properties();
    try (InputStream in = new FileInputStream("src/main/resources/application.properties")) {
      props.load(in);
    }
    String active = props.getProperty("openaev.tenant.active-tables", "");
    assertTrue(
        active.contains("mitigations"),
        "mitigations must stay in openaev.tenant.active-tables: its v1 @Filter was removed, so"
            + " dropping it would leave the table with no tenant isolation. Found: '"
            + active
            + "'");
  }
}
