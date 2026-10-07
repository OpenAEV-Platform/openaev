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
 * Guards that the production configuration keeps {@code ioc_validations} activated in v2. The table
 * is v2-native: it never had a v1 {@code @Filter} nor a {@code TenantBaseListener}, so if it ever
 * dropped out of {@code openaev.tenant.active-tables} it would have <b>no</b> tenant isolation at
 * all. This reads the production {@code application.properties} directly, because the test
 * classpath deliberately shadows it with the test config (which keeps the allowlist empty so the
 * rest of the suite controls activation per test via {@code @TestPropertySource}).
 */
@DisplayName("Production config keeps ioc_validations activated (v2)")
class IocValidationActivationConfigTest {

  @Test
  @DisplayName("openaev.tenant.active-tables in application.properties contains ioc_validations")
  void prodConfigActivatesIocValidations() throws Exception {
    Properties props = new Properties();
    try (InputStream in = new FileInputStream("src/main/resources/application.properties")) {
      props.load(in);
    }
    String active = props.getProperty("openaev.tenant.active-tables", "");
    // Exact token comparison, not substring: another table whose name starts with ioc_validations
    // must not satisfy this guard once the real table has been deactivated.
    List<String> tables = Arrays.stream(active.split(",")).map(String::trim).toList();
    assertTrue(
        tables.contains("ioc_validations"),
        "ioc_validations must stay in openaev.tenant.active-tables: the table has no v1 @Filter,"
            + " so dropping it would leave it with no tenant isolation. Found: '"
            + active
            + "'");
  }
}
