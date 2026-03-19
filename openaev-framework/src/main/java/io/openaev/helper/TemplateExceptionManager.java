package io.openaev.helper;

import freemarker.core.Environment;
import freemarker.template.TemplateException;
import freemarker.template.TemplateExceptionHandler;
import java.io.IOException;
import java.io.Writer;

/**
 * Custom FreeMarker template exception handler that preserves unresolved variable expressions.
 *
 * <p>Instead of throwing an error or rendering nothing when a template variable cannot be resolved,
 * this handler outputs the original expression syntax (e.g., {@code ${variableName}}). This is
 * useful for:
 *
 * <ul>
 *   <li>Graceful degradation when optional variables are missing
 *   <li>Debugging templates by making missing variables visible
 *   <li>Supporting partial template rendering where some variables may be resolved later
 * </ul>
 *
 * <p>Example: If a template contains {@code ${user.middleName}} but the user has no middle name,
 * the output will contain the literal text {@code ${user.middleName}} instead of causing an error.
 *
 * <p>This handler is typically configured when creating the FreeMarker {@code Configuration}:
 *
 * <pre>{@code
 * Configuration cfg = new Configuration(Configuration.VERSION_2_3_32);
 * cfg.setTemplateExceptionHandler(new TemplateExceptionManager());
 * }</pre>
 *
 * @see TemplateExceptionHandler
 */
public class TemplateExceptionManager implements TemplateExceptionHandler {

  /**
   * Handles a template exception by outputting the original expression that caused the error.
   *
   * @param templateException the exception that occurred during template processing
   * @param environment the FreeMarker environment where the exception occurred
   * @param out the writer to output the fallback expression
   * @throws TemplateException if writing the fallback expression fails
   */
  @Override
  public void handleTemplateException(
      TemplateException templateException, Environment environment, Writer out)
      throws TemplateException {
    String blamedExpression = templateException.getBlamedExpressionString();
    if (blamedExpression == null || blamedExpression.isBlank()) {
      blamedExpression = "unknown";
    }

    try {
      out.write("${" + blamedExpression + "}");
    } catch (IOException e) {
      throw new TemplateException(
          "Failed to write fallback expression for unresolved variable '"
              + blamedExpression
              + "'. Cause: "
              + e.getMessage(),
          environment);
    }
  }
}
