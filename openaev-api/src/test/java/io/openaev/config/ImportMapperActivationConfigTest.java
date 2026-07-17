package io.openaev.config;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.FileInputStream;
import java.io.InputStream;
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
}
