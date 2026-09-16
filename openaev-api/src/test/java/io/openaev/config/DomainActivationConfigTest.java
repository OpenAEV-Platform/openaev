package io.openaev.config;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Properties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Production config keeps domains activated (v2)")
class DomainActivationConfigTest {

  @Test
  @DisplayName("openaev.tenant.active-tables in application.properties contains domains")
  void prodConfigActivatesDomains() throws Exception {
    Properties props = new Properties();
    try (InputStream in = new FileInputStream("src/main/resources/application.properties")) {
      props.load(in);
    }
    String active = props.getProperty("openaev.tenant.active-tables", "");
    assertTrue(
        active.contains("domains"),
        "domains must stay in openaev.tenant.active-tables: its v1 @Filter was removed, so"
            + " dropping it would leave the table with no tenant isolation. Found: '"
            + active
            + "'");
  }
}
