package io.openaev.config;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Production config keeps domains activated (v2)")
class DomainActivationConfigTest {

  @Test
  @DisplayName("openaev.tenant.active-tables in application.properties contains domains")
  void prodConfigActivatesDomains() throws Exception {
    Set<String> active = ProductionActiveTables.names();
    assertTrue(
        active.contains("domains"),
        "domains must stay in openaev.tenant.active-tables: its v1 @Filter was removed, so"
            + " dropping it would leave the table with no tenant isolation. Found: '"
            + active
            + "'");
  }
}
