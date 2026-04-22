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

  private static Map<String, Object> templateContext() {
    Map<String, Object> context = new HashMap<>();
    context.put("firstname", "John");
    context.put("lastname", "Doe");
    context.put("greeting", "Welcome");
    return context;
  }

  @Nested
  @DisplayName("Normal template substitution")
  class NormalSubstitution {

    @Test
    @DisplayName("given content with variables should resolve them")
    void given_content_with_variables_should_resolve_them() throws IOException, TemplateException {
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
