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
 * Guards that the production configuration keeps {@code security_coverage_hunt_validations}
 * activated in v2. The table was born on the new mechanism: it has no v1 {@code @Filter} and no
 * {@code TenantBaseListener}, so if it ever dropped out of {@code openaev.tenant.active-tables} it
 * would have <b>no</b> tenant isolation at all. This reads the production {@code
 * application.properties} directly, because the test classpath deliberately shadows it with the
 * test config (which keeps the allowlist empty so the rest of the suite controls activation per
 * test via {@code @TestPropertySource}).
 */
@DisplayName("Production config keeps security_coverage_hunt_validations activated (v2)")
class SecurityCoverageHuntValidationActivationConfigTest {

  @Test
  @DisplayName(
      "openaev.tenant.active-tables in application.properties contains"
          + " security_coverage_hunt_validations")
  void prodConfigActivatesSecurityCoverageHuntValidations() throws Exception {
    Properties props = new Properties();
    try (InputStream in = new FileInputStream("src/main/resources/application.properties")) {
      props.load(in);
    }
    String active = props.getProperty("openaev.tenant.active-tables", "");
    // Exact token comparison, not substring: "security_coverages" or any other prefix must not
    // satisfy this guard once the real table has been deactivated.
    List<String> tables = Arrays.stream(active.split(",")).map(String::trim).toList();
    assertTrue(
        tables.contains("security_coverage_hunt_validations"),
        "security_coverage_hunt_validations must stay in openaev.tenant.active-tables: it has no v1"
            + " @Filter, so dropping it would leave the table with no tenant isolation. Found: '"
            + active
            + "'");
  }
}
