package io.openaev.helper;

import static org.junit.jupiter.api.Assertions.*;

import freemarker.template.TemplateException;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("TemplateHelper")
class TemplateHelperTest {

  private static Map<String, Object> templateContext() {
    Map<String, Object> context = new HashMap<>();
    context.put("firstname", "John");
    context.put("lastname", "Doe");
    context.put("greeting", "Welcome");
    return context;
  }

  private static final List<String> RESTRICTED_CLASSES =
      List.of(
          String.join(".", "freemarker", "template", "utility", "Execute"),
          String.join(".", "freemarker", "template", "utility", "ObjectConstructor"),
          String.join(".", "freemarker", "template", "utility", "JndiObjectFactory"));

  private static String restrictedExpression(String className) {
    return "${\"" + className + "\"?" + "new()(\"test\")}";
  }

  @Nested
  @DisplayName("SAFER_RESOLVER — restricted built-in classes")
  class SaferResolver {

    @Test
    @DisplayName("given restricted built-in classes should all produce fallback output")
    void given_restricted_builtin_classes_should_all_produce_fallback_output()
        throws IOException, TemplateException {
      Map<String, Object> context = templateContext();

      for (String restricted : RESTRICTED_CLASSES) {
        // -- Arrange --
        String expression = restrictedExpression(restricted);

        // -- Act --
        String rendered = TemplateHelper.buildContentWithDataMap(expression, context);

        // -- Assert --
        String shortName = restricted.substring(restricted.lastIndexOf('.') + 1);
        assertFalse(rendered.isBlank(), shortName + ": should produce fallback output");
        assertTrue(
            rendered.contains("${"),
            shortName + ": expression must be preserved as literal text — SAFER_RESOLVER may be missing");
      }
    }

    @Test
    @DisplayName("given mixed content with restricted class should resolve variables and preserve restricted expression")
    void given_mixed_content_should_resolve_variables_and_preserve_restricted_expression()
        throws IOException, TemplateException {
      // -- Arrange --
      String content =
          "${greeting} ${firstname}, "
              + restrictedExpression(RESTRICTED_CLASSES.get(0))
              + " have a nice day";
      Map<String, Object> context = templateContext();

      // -- Act --
      String rendered = TemplateHelper.buildContentWithDataMap(content, context);

      // -- Assert --
      assertTrue(rendered.contains("Welcome John"));
      assertTrue(rendered.contains("have a nice day"));
      assertTrue(
          rendered.contains("${"),
          "Restricted expression must be preserved as literal text — SAFER_RESOLVER may be missing");
    }
  }

  @Nested
  @DisplayName("Normal template substitution")
  class NormalSubstitution {

    @Test
    @DisplayName("given content with variables should resolve them")
    void given_content_with_variables_should_resolve_them()
        throws IOException, TemplateException {
      // -- Arrange --
      String content = "${greeting} ${firstname} ${lastname}";

      // -- Act --
      String rendered = TemplateHelper.buildContentWithDataMap(content, templateContext());

      // -- Assert --
      assertEquals("Welcome John Doe", rendered);
    }

    @Test
    @DisplayName("given null content should return empty string")
    void given_null_content_should_return_empty_string() throws IOException, TemplateException {
      // -- Act --
      String rendered = TemplateHelper.buildContentWithDataMap(null, templateContext());

      // -- Assert --
      assertEquals("", rendered);
    }
  }
}
