package io.openaev.config;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Guards that the production configuration keeps {@code security_coverages} activated in v2. The
 * table is fully on the new mechanism: its v1 {@code @Filter} and {@code TenantBaseListener} have
 * been removed, so if it ever dropped out of {@code openaev.tenant.active-tables} it would have
 * <b>no</b> tenant isolation at all (neither v1 nor v2). This reads the production {@code
 * application.properties} directly, because the test classpath deliberately shadows it with the
 * test config (which keeps the allowlist empty so the rest of the suite controls activation per
 * test via {@code @TestPropertySource}).
 */
@DisplayName("Production config keeps security_coverages activated (v2)")
class SecurityCoverageActivationConfigTest {

  @Test
  @DisplayName("openaev.tenant.active-tables in application.properties contains security_coverages")
  void prodConfigActivatesSecurityCoverages() throws Exception {
    Properties props = new Properties();
    try (InputStream in = new FileInputStream("src/main/resources/application.properties")) {
      props.load(in);
    }
    String active = props.getProperty("openaev.tenant.active-tables", "");
    // Exact token comparison, not substring: a value like "security_coverages_archive" must not
    // satisfy this guard once the real table has been deactivated.
    List<String> tables = Arrays.stream(active.split(",")).map(String::trim).toList();
    assertTrue(
        tables.contains("security_coverages"),
        "security_coverages must stay in openaev.tenant.active-tables: its v1 @Filter was"
            + " removed, so dropping it would leave the table with no tenant isolation. Found: '"
            + active
            + "'");
  }
}
