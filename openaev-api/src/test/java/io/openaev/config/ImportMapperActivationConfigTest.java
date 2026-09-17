package io.openaev.config;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Properties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Guards that production configuration keeps fully activated v2 tables in the allowlist. */
@DisplayName("Production config keeps fully activated v2 tables in allowlist")
class ImportMapperActivationConfigTest {

  @Test
  @DisplayName("openaev.tenant.active-tables in application.properties contains import_mappers")
  void prodConfigActivatesImportMappers() throws Exception {
    Properties props = new Properties();
    try (InputStream in = new FileInputStream("src/main/resources/application.properties")) {
      props.load(in);
    }
    String active = props.getProperty("openaev.tenant.active-tables", "");
    assertTrue(
        active.contains("import_mappers"),
        "import_mappers must stay in openaev.tenant.active-tables: its v1 @Filter was removed, so"
            + " dropping it would leave the table with no tenant isolation. Found: '"
            + active
            + "'");
  }

  @Test
  @DisplayName("openaev.tenant.active-tables in application.properties contains lessons_templates")
  void prodConfigActivatesLessonsTemplates() throws Exception {
    Properties props = new Properties();
    try (InputStream in = new FileInputStream("src/main/resources/application.properties")) {
      props.load(in);
    }
    String active = props.getProperty("openaev.tenant.active-tables", "");
    assertTrue(
        active.contains("lessons_templates"),
        "lessons_templates must stay in openaev.tenant.active-tables: its v1 @Filter was removed,"
            + " so dropping it would leave the table with no tenant isolation. Found: '"
            + active
            + "'");
  }

  @Test
  @DisplayName("openaev.tenant.active-tables in application.properties contains cwes")
  void prodConfigActivatesCwes() throws Exception {
    Properties props = new Properties();
    try (InputStream in = new FileInputStream("src/main/resources/application.properties")) {
      props.load(in);
    }
    String active = props.getProperty("openaev.tenant.active-tables", "");
    assertTrue(
        active.contains("cwes"),
        "cwes must stay in openaev.tenant.active-tables: its v1 @Filter was removed, so dropping it"
            + " would leave the table with no tenant isolation. Found: '"
            + active
            + "'");
  }

  @Test
  @DisplayName("openaev.tenant.active-tables in application.properties contains collectors")
  void prodConfigActivatesCollectors() throws Exception {
    Properties props = new Properties();
    try (InputStream in = new FileInputStream("src/main/resources/application.properties")) {
      props.load(in);
    }
    String active = props.getProperty("openaev.tenant.active-tables", "");
    assertTrue(
        active.contains("collectors"),
        "collectors must stay in openaev.tenant.active-tables: its v1 @Filter was removed, so"
            + " dropping it would leave the table with no tenant isolation. Found: '"
            + active
            + "'");
  }

  @Test
  @DisplayName("openaev.tenant.active-tables in application.properties contains security_coverages")
  void prodConfigActivatesSecurityCoverages() throws Exception {
    Properties props = new Properties();
    try (InputStream in = new FileInputStream("src/main/resources/application.properties")) {
      props.load(in);
    }
    String active = props.getProperty("openaev.tenant.active-tables", "");
    assertTrue(
        active.contains("security_coverages"),
        "security_coverages must stay in openaev.tenant.active-tables: its v1 @Filter was"
            + " removed, so dropping it would leave the table with no tenant isolation. Found: '"
            + active
            + "'");
  }

  @Test
  @DisplayName(
      "openaev.tenant.active-tables in application.properties contains autonomous_runs,"
          + " autonomous_events and autonomous_directives")
  void prodConfigActivatesAutonomousTables() throws Exception {
    Properties props = new Properties();
    try (InputStream in = new FileInputStream("src/main/resources/application.properties")) {
      props.load(in);
    }
    String active = props.getProperty("openaev.tenant.active-tables", "");
    assertTrue(
        active.contains("autonomous_runs")
            && active.contains("autonomous_events")
            && active.contains("autonomous_directives"),
        "autonomous_runs, autonomous_events and autonomous_directives must stay in"
            + " openaev.tenant.active-tables: they have no v1 @Filter (listener default was"
            + " removed on activation), so dropping them would leave the tables with no tenant"
            + " isolation. Found: '"
            + active
            + "'");
  }

  @Test
  @DisplayName("openaev.tenant.active-tables in application.properties contains kill_chain_phases")
  void prodConfigActivatesKillChainPhases() throws Exception {
    Properties props = new Properties();
    try (InputStream in = new FileInputStream("src/main/resources/application.properties")) {
      props.load(in);
    }
    String active = props.getProperty("openaev.tenant.active-tables", "");
    assertTrue(
        active.contains("kill_chain_phases"),
        "kill_chain_phases must stay in openaev.tenant.active-tables: its v1 @Filter was removed, so"
            + " dropping it would leave the table with no tenant isolation. Found: '"
            + active
            + "'");
  }

  @Test
  @DisplayName("openaev.tenant.active-tables in application.properties contains channels")
  void prodConfigActivatesChannels() throws Exception {
    Properties props = new Properties();
    try (InputStream in = new FileInputStream("src/main/resources/application.properties")) {
      props.load(in);
    }
    String active = props.getProperty("openaev.tenant.active-tables", "");
    assertTrue(
        active.contains("channels"),
        "channels must stay in openaev.tenant.active-tables: its v1 @Filter was removed, so"
            + " dropping it would leave the table with no tenant isolation. Found: '"
            + active
            + "'");
  }

  @Test
  @DisplayName("openaev.tenant.active-tables in application.properties contains notifications")
  void prodConfigActivatesNotifications() throws Exception {
    Properties props = new Properties();
    try (InputStream in = new FileInputStream("src/main/resources/application.properties")) {
      props.load(in);
    }
    String active = props.getProperty("openaev.tenant.active-tables", "");
    assertTrue(
        active.contains("notifications"),
        "notifications must stay in openaev.tenant.active-tables: its v1 @Filter was removed, so"
            + " dropping it would leave the table with no tenant isolation. Found: '"
            + active
            + "'");
  }

  @Test
  @DisplayName("openaev.tenant.active-tables in application.properties contains asset_groups")
  void prodConfigActivatesAssetGroups() throws Exception {
    Properties props = new Properties();
    try (InputStream in = new FileInputStream("src/main/resources/application.properties")) {
      props.load(in);
    }
    String active = props.getProperty("openaev.tenant.active-tables", "");
    assertTrue(
        active.contains("asset_groups"),
        "asset_groups must stay in openaev.tenant.active-tables: its v1 @Filter was removed, so"
            + " dropping it would leave the table with no read isolation at all. Found: '"
            + active
            + "'");
  }

  @Test
  @DisplayName("openaev.tenant.active-tables in application.properties contains custom_dashboards")
  void prodConfigActivatesCustomDashboards() throws Exception {
    Properties props = new Properties();
    try (InputStream in = new FileInputStream("src/main/resources/application.properties")) {
      props.load(in);
    }
    String active = props.getProperty("openaev.tenant.active-tables", "");
    assertTrue(
        active.contains("custom_dashboards"),
        "custom_dashboards must stay in openaev.tenant.active-tables: its v1 @Filter was"
            + " removed, so dropping it would leave the table with no tenant isolation. Found: '"
            + active
            + "'");
  }

  @Test
  @DisplayName("openaev.tenant.active-tables in application.properties contains widgets")
  void prodConfigActivatesWidgets() throws Exception {
    Properties props = new Properties();
    try (InputStream in = new FileInputStream("src/main/resources/application.properties")) {
      props.load(in);
    }
    String active = props.getProperty("openaev.tenant.active-tables", "");
    assertTrue(
        active.contains("widgets"),
        "widgets must stay in openaev.tenant.active-tables: its v1 @Filter was removed, so"
            + " dropping it would leave the table with no tenant isolation. Found: '"
            + active
            + "'");
  }

  @Test
  @DisplayName("openaev.tenant.active-tables in application.properties contains assets")
  void prodConfigActivatesAssets() throws Exception {
    Properties props = new Properties();
    try (InputStream in = new FileInputStream("src/main/resources/application.properties")) {
      props.load(in);
    }
    String active = props.getProperty("openaev.tenant.active-tables", "");
    assertTrue(
        active.contains("assets"),
        "assets must stay in openaev.tenant.active-tables: its v1 @Filter was removed, so dropping"
            + " it would leave endpoints, security platforms and AI targets with no read isolation"
            + " at all. Found: '"
            + active
            + "'");
  }

  @Test
  @DisplayName("openaev.tenant.active-tables in application.properties contains findings")
  void prodConfigActivatesFindings() throws Exception {
    Properties props = new Properties();
    try (InputStream in = new FileInputStream("src/main/resources/application.properties")) {
      props.load(in);
    }
    String active = props.getProperty("openaev.tenant.active-tables", "");
    assertTrue(
        active.contains("findings"),
        "findings must stay in openaev.tenant.active-tables: its v1 @Filter was removed, so dropping"
            + " it would leave every finding readable across tenants. Found: '"
            + active
            + "'");
  }

  @Test
  @DisplayName("openaev.tenant.active-tables in application.properties contains tags")
  void prodConfigActivatesTags() throws Exception {
    Properties props = new Properties();
    try (InputStream in = new FileInputStream("src/main/resources/application.properties")) {
      props.load(in);
    }
    String active = props.getProperty("openaev.tenant.active-tables", "");
    assertTrue(
        active.contains("tags"),
        "tags must stay in openaev.tenant.active-tables: its v1 @Filter was removed, so dropping it"
            + " would leave the table with no tenant isolation. Found: '"
            + active
            + "'");
  }

  @Test
  @DisplayName(
      "openaev.tenant.active-tables in application.properties contains notification_triggers")
  void prodConfigActivatesNotificationTriggers() throws Exception {
    Properties props = new Properties();
    try (InputStream in = new FileInputStream("src/main/resources/application.properties")) {
      props.load(in);
    }
    String active = props.getProperty("openaev.tenant.active-tables", "");
    assertTrue(
        active.contains("notification_triggers"),
        "notification_triggers must stay in openaev.tenant.active-tables: its v1 @Filter and its"
            + " TenantBaseListener were both removed, so dropping it would leave the table with no"
            + " read isolation and no write attribution at all. Found: '"
            + active
            + "'");
  }

  @Test
  @DisplayName(
      "openaev.tenant.active-tables in application.properties contains notification_events")
  void prodConfigActivatesNotificationEvents() throws Exception {
    Properties props = new Properties();
    try (InputStream in = new FileInputStream("src/main/resources/application.properties")) {
      props.load(in);
    }
    String active = props.getProperty("openaev.tenant.active-tables", "");
    assertTrue(
        active.contains("notification_events"),
        "notification_events must stay in openaev.tenant.active-tables: its v1 @Filter was removed,"
            + " so dropping it would let a digest replay another tenant's outbox. Found: '"
            + active
            + "'");
  }

  @Test
  @DisplayName("openaev.tenant.active-tables in application.properties contains tag_rules")
  void prodConfigActivatesTagRules() throws Exception {
    Properties props = new Properties();
    try (InputStream in = new FileInputStream("src/main/resources/application.properties")) {
      props.load(in);
    }
    String active = props.getProperty("openaev.tenant.active-tables", "");
    assertTrue(
        active.contains("tag_rules"),
        "tag_rules must stay in openaev.tenant.active-tables: its v1 @Filter was removed, so"
            + " dropping it would leave the table with no tenant isolation. Found: '"
            + active
            + "'");
  }

  @Test
  @DisplayName(
      "openaev.tenant.active-tables in application.properties contains"
          + " tenant_xtmhub_registrations")
  void prodConfigActivatesTenantXtmHubRegistrations() throws Exception {
    Properties props = new Properties();
    try (InputStream in = new FileInputStream("src/main/resources/application.properties")) {
      props.load(in);
    }
    String active = props.getProperty("openaev.tenant.active-tables", "");
    assertTrue(
        active.contains("tenant_xtmhub_registrations"),
        "tenant_xtmhub_registrations must stay in openaev.tenant.active-tables: its v1 @Filter was"
            + " removed, so dropping it would leave the table with no tenant isolation. Found: '"
            + active
            + "'");
  }

  @Test
  @DisplayName("openaev.tenant.active-tables in application.properties contains notifiers")
  void prodConfigActivatesNotifiers() throws Exception {
    Properties props = new Properties();
    try (InputStream in = new FileInputStream("src/main/resources/application.properties")) {
      props.load(in);
    }
    String active = props.getProperty("openaev.tenant.active-tables", "");
    // Entry match, not contains: contains() on the raw property also passes on any table name that
    // merely embeds this one (e.g. "webhook_notifiers"), so it can report an activation that is
    // not there.
    assertTrue(
        Arrays.stream(active.split(",")).map(String::trim).anyMatch("notifiers"::equals),
        "notifiers must stay in openaev.tenant.active-tables: its v1 @Filter and TenantBaseListener"
            + " were removed, so dropping it would leave the table with no read isolation and no"
            + " write attribution at all. Found: '"
            + active
            + "'");
  }
}
