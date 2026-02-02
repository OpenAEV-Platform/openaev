package io.openaev.helper;

import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import io.openaev.execution.ExecutionContext;
import java.io.IOException;
import java.io.StringReader;
import java.util.Map;
import org.springframework.ui.freemarker.FreeMarkerTemplateUtils;

/**
 * Helper class for FreeMarker template processing.
 *
 * <p>Provides methods for processing template strings with dynamic data substitution using the
 * FreeMarker template engine. Used for generating dynamic content in emails, messages, and other
 * inject outputs.
 *
 * <p>This is a utility class and cannot be instantiated.
 */
public final class TemplateHelper {

  private TemplateHelper() {}

  /** Cached FreeMarker configuration - thread-safe and designed for reuse. */
  private static final Configuration FREEMARKER_CONFIG;

  static {
    FREEMARKER_CONFIG = new Configuration(Configuration.VERSION_2_3_31);
    FREEMARKER_CONFIG.setTemplateExceptionHandler(new TemplateExceptionManager());
    FREEMARKER_CONFIG.setLogTemplateExceptions(false);
  }

  /**
   * Processes a template string with execution context data.
   *
   * <p>Substitutes template variables in the content with values from the execution context, which
   * includes user information, exercise data, and other contextual variables.
   *
   * @param content the template string containing FreeMarker expressions
   * @param context the execution context providing variable values
   * @return the processed string with all variables substituted
   * @throws IOException if template reading fails
   * @throws TemplateException if template processing fails
   */
  public static String buildContextualContent(String content, ExecutionContext context)
      throws IOException, TemplateException {
    return buildContentWithDataMap(content, context);
  }

  /**
   * Processes a template string with a custom data map.
   *
   * <p>Substitutes template variables in the content with values from the provided data map. More
   * flexible than {@link #buildContextualContent} for cases with custom variable sets.
   *
   * @param content the template string containing FreeMarker expressions
   * @param dataMap the map of variable names to values
   * @return the processed string with all variables substituted, or empty string if content is null
   * @throws IOException if template reading fails
   * @throws TemplateException if template processing fails
   */
  public static String buildContentWithDataMap(String content, Map<String, Object> dataMap)
      throws IOException, TemplateException {
    if (content == null) return "";
    Template template = new Template("template", new StringReader(content), FREEMARKER_CONFIG);
    return FreeMarkerTemplateUtils.processTemplateIntoString(template, dataMap);
  }
}
